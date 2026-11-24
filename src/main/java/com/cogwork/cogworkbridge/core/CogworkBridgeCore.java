package com.cogwork.cogworkbridge.core;

import net.minecraftforge.fml.relauncher.FMLInjectionData;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Cogwork Bridge's core plugin.
 *
 * <p>The constructor isolates the required upstream jar before Cleanroom submits
 * Mixin containers. The archive remains available to resource and recipe readers.
 */
@IFMLLoadingPlugin.Name("CogworkBridgeCore")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(Integer.MIN_VALUE)
@IFMLLoadingPlugin.TransformerExclusions("com.cogwork.cogworkbridge.core")
public final class CogworkBridgeCore implements IFMLLoadingPlugin {

    private static final Logger LOGGER = LogManager.getLogger("CogworkBridgeCore");

    private static volatile List<TargetJar> targets = Collections.emptyList();
    private static volatile File gameDirectory;
    private static volatile File coremodSource;
    private static volatile Throwable isolationFailure;
    private static volatile boolean isolationAttempted;

    public CogworkBridgeCore() {
        try {
            // FML sets this before discovering coremods on both sides.
            // Launch.minecraftHome is not populated by the dedicated server.
            gameDirectory = (File) FMLInjectionData.data()[6];
            if (gameDirectory == null) {
                throw new IllegalStateException("FML game directory is unavailable during early startup");
            }
            targets = TargetJarLocator.scan(gameDirectory, null);
            boolean discoveryAlreadyStarted = false;
            for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                if (frame.getClassName().equals("com.cleanroommc.discovery.CleanroomModDiscoverer")
                        && frame.getMethodName().equals("discoverCoreMod")) {
                    discoveryAlreadyStarted = true;
                    break;
                }
            }
            TargetIsolation.isolate(targets, discoveryAlreadyStarted);
            for (TargetJar target : targets) {
                LOGGER.info("Isolated upstream Create 1.21.1 jar {} (version {})",
                        target.getFile().getName(), target.getImplementationVersion());
            }
        } catch (Exception | LinkageError failure) {
            isolationFailure = failure;
            LOGGER.error("Unable to isolate the required Create 1.21.1 jar", failure);
        } finally {
            isolationAttempted = true;
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{"com.cogwork.cogworkbridge.core.ContraptionLightTransformer",
                "com.cogwork.cogworkbridge.core.ContraptionGuiTransformer",
                "com.cogwork.cogworkbridge.core.CartAssemblerRailTransformer",
                "com.cogwork.cogworkbridge.core.PackageAttackTransformer",
                "com.cogwork.cogworkbridge.core.GlassFurnaceTransformer",
                "com.cogwork.cogworkbridge.core.DivingEquipmentTransformer",
                "com.cogwork.cogworkbridge.core.DivingBootsTravelTransformer",
                "com.cogwork.cogworkbridge.core.EntityTrackerSafetyTransformer"};
    }

    @Override
    public String getModContainerClass() {
        // Forge needs an active dependency provider to show its standard
        // missing-mod screen before constructing Cogwork Bridge. This marker
        // is present only when the separately supplied archive was detected.
        return targets.isEmpty() ? null : CreateDependencyContainer.class.getName();
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // Isolation must already have happened in the constructor: injectData
        // can run after Cleanroom has submitted Mixin containers.
        Object location = data.get("coremodLocation");
        if (location instanceof File) coremodSource = (File) location;
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    public static List<TargetJar> getTargets() {
        return targets;
    }

    public static File getGameDirectory() {
        return gameDirectory;
    }

    static File getCoremodSource() {
        if (coremodSource != null) return coremodSource;
        try {
            return new File(CogworkBridgeCore.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot locate the Cogwork Bridge container", failure);
        }
    }

    /** Called on both sides before the mod mounts resources or registers content. */
    public static void requireIsolatedUpstream() {
        if (!isolationAttempted || isolationFailure != null) {
            throw new IllegalStateException(
                    "Cogwork Bridge could not safely isolate the Create 1.21.1 jar. "
                    + "Check the startup log and ensure the mods directory is writable. "
                    + "齿轮桥接器无法安全隔离 Create 1.21.1 原版文件，请检查启动日志和 mods 目录写入权限。",
                    isolationFailure);
        }
        if (targets.isEmpty()) {
            throw new IllegalStateException(
                    "Cogwork Bridge requires the original Create for Minecraft 1.21.1 jar in mods/ "
                    + "or mods/1.12.2/. No compatible jar was found. "
                    + "齿轮桥接器需要将适用于 Minecraft 1.21.1 的 Create 原版 JAR 放入 mods 文件夹；"
                    + "未找到兼容文件。");
        }
    }
}
