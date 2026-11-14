package com.cogwork.cogworkbridge.core;

import com.google.common.collect.Multimap;
import net.minecraftforge.fml.relauncher.CoreModManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;

/**
 * Keeps the original 1.21.1 jar out of FML and Mixin discovery while leaving
 * the archive available to the resource and recipe readers.
 */
final class TargetIsolation {
    private static final Logger LOGGER = LogManager.getLogger("CogworkBridge/TargetIsolation");
    private static final String EXTERNAL_MANIFEST =
            "Manifest-Version: 1.0\r\nModType: JAR\r\n\r\n";

    private TargetIsolation() {}

    static void isolate(List<TargetJar> targets, boolean discoveryAlreadyStarted) throws Exception {
        IOException metadataFailure = null;
        for (TargetJar target : targets) {
            try {
                ensureExternalManifest(target.getFile());
            } catch (IOException failure) {
                if (metadataFailure == null) metadataFailure = failure;
                else metadataFailure.addSuppressed(failure);
            }
            List<String> ignored = CoreModManager.getIgnoredMods();
            if (!ignored.contains(target.getFile().getName())) ignored.add(target.getFile().getName());
            CoreModManager.getReparseableCoremods().remove(target.getFile().getName());
        }

        // Cleanroom scans every manifest before it constructs discovered coremods.
        // Changing the file alone is too late on the first launch.
        if (discoveryAlreadyStarted && !targets.isEmpty()) replaceCachedManifests(targets);
        if (metadataFailure != null) throw metadataFailure;
    }

    static void ensureExternalManifest(File jar) throws IOException {
        Path meta = Path.of(jar.getAbsolutePath() + ".meta");
        byte[] safe = EXTERNAL_MANIFEST.getBytes(StandardCharsets.ISO_8859_1);
        if (Files.isRegularFile(meta) && java.util.Arrays.equals(Files.readAllBytes(meta), safe)) return;

        Path parent = meta.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "cogworkbridge-meta-", ".tmp");
        try {
            Files.write(temporary, safe);
            try {
                Files.move(temporary, meta, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, meta, StandardCopyOption.REPLACE_EXISTING);
            }
            LOGGER.info("Wrote safe external manifest for {}", jar.getName());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @SuppressWarnings("unchecked")
    private static void replaceCachedManifests(List<TargetJar> targets) throws Exception {
        Class<?> discovererClass = Class.forName("com.cleanroommc.discovery.CleanroomModDiscoverer");
        Object discoverer = discovererClass.getMethod("instance").invoke(null);
        if (discoverer == null) throw new IllegalStateException("Cleanroom discovery is not ready");

        Field recordsField = discovererClass.getDeclaredField("discoveredFiles");
        recordsField.setAccessible(true);
        Map<File, Object> records = (Map<File, Object>) recordsField.get(discoverer);
        Field byFileField = discovererClass.getDeclaredField("fileToModIds");
        Field byIdField = discovererClass.getDeclaredField("modIdToFiles");
        byFileField.setAccessible(true);
        byIdField.setAccessible(true);
        Multimap<File, String> byFile = (Multimap<File, String>) byFileField.get(discoverer);
        Multimap<String, File> byId = (Multimap<String, File>) byIdField.get(discoverer);

        Class<?> recordClass = Class.forName("com.cleanroommc.discovery.DiscoveredMod");
        Constructor<?> constructor = recordClass.getDeclaredConstructor(File.class, Attributes.class,
                List.class, String.class, boolean.class, boolean.class, boolean.class,
                String.class, String.class);
        constructor.setAccessible(true);
        Set<File> remaining = new HashSet<>();
        for (TargetJar target : targets) remaining.add(target.getFile().getCanonicalFile());

        // Replacing a value, rather than removing a map entry, is safe while
        // Cleanroom is iterating its LinkedHashMap in discoverCoreMods().
        for (Map.Entry<File, Object> entry : records.entrySet()) {
            File file = entry.getKey();
            if (!remaining.remove(file.getCanonicalFile())) continue;
            Attributes safe = new Attributes();
            safe.putValue("Manifest-Version", "1.0");
            safe.putValue("ModType", "JAR");
            entry.setValue(constructor.newInstance(file, safe, Collections.emptyList(), "JAR",
                    false, false, false, null, null));
            for (String id : new ArrayList<>(byFile.removeAll(file))) byId.remove(id, file);
            LOGGER.info("Removed cached mod and mixin discovery data for {}", file.getName());
        }
        if (!remaining.isEmpty()) {
            throw new IllegalStateException("Cleanroom did not expose discovery records for " + remaining);
        }
    }
}
