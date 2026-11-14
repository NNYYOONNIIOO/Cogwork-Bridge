package com.cogwork.cogworkbridge.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds upstream Create 1.21.1 JARs purely for their assets.
 *
 * <p>Detection is content based, not name based: any jar that contains
 * the expected asset paths and declares Create with a Minecraft 1.21.1
 * dependency is accepted. This tolerates renamed builds and forks.
 */
public final class TargetJarLocator {

    private static final String PROBE_TEXTURE = "assets/create/textures/block/andesite_casing.png";
    private static final String PROBE_BLOCKSTATE = "assets/create/blockstates/shaft.json";
    private static final Pattern CREATE_MOD = Pattern.compile("(?m)^\\s*modId\\s*=\\s*[\"']create[\"']\\s*$");
    private static final Pattern DEPENDENCY = Pattern.compile(
            "(?ms)^\\s*\\[\\[dependencies\\.(?:\"create\"|create)\\]\\][^\\r\\n]*\\R(.*?)(?=^\\s*\\[\\[|\\z)");
    private static final Pattern MINECRAFT_MOD = Pattern.compile("(?m)^\\s*modId\\s*=\\s*[\"']minecraft[\"']\\s*$");
    private static final Pattern VERSION_RANGE = Pattern.compile("(?m)^\\s*versionRange\\s*=\\s*[\"']([^\"']+)[\"']");

    private TargetJarLocator() {
    }

    public static List<TargetJar> scan(File gameDirectory, File selfJar) {
        List<TargetJar> found = new ArrayList<>();
        collect(new File(gameDirectory, "mods"), selfJar, found);
        collect(new File(new File(gameDirectory, "mods"), "1.12.2"), selfJar, found);
        return Collections.unmodifiableList(found);
    }

    private static void collect(File directory, File selfJar, List<TargetJar> out) {
        File[] files = directory.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (sameFile(file, selfJar)) {
                continue;
            }
            TargetJar target = inspect(file);
            if (target != null) {
                out.add(target);
            }
        }
    }

    static TargetJar inspect(File file) {
        try (JarFile jar = new JarFile(file)) {
            if (jar.getEntry(PROBE_TEXTURE) == null && jar.getEntry(PROBE_BLOCKSTATE) == null) {
                return null;
            }
            if (!declaresCompatibleCreate(jar)) {
                return null;
            }
            return new TargetJar(file.getCanonicalFile(), readVersion(jar));
        } catch (IOException ignored) {
            return null;
        }
    }

    private static boolean declaresCompatibleCreate(JarFile jar) throws IOException {
        for (String meta : new String[]{"META-INF/neoforge.mods.toml", "META-INF/mods.toml"}) {
            if (jar.getEntry(meta) == null) {
                continue;
            }
            try (InputStream in = jar.getInputStream(jar.getEntry(meta))) {
                String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (!CREATE_MOD.matcher(text).find()) {
                    continue;
                }
                Matcher dependencies = DEPENDENCY.matcher(text);
                while (dependencies.find()) {
                    String section = dependencies.group(1);
                    Matcher range = VERSION_RANGE.matcher(section);
                    if (MINECRAFT_MOD.matcher(section).find() && range.find()
                            && range.group(1).contains("1.21.1")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String readVersion(JarFile jar) throws IOException {
        Manifest manifest = jar.getManifest();
        if (manifest == null) {
            return "unknown";
        }
        String version = manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        return version == null ? "unknown" : version;
    }

    private static boolean sameFile(File a, File b) {
        if (b == null) {
            return false;
        }
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException ignored) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }
}
