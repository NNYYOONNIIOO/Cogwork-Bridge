package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.Reference;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Forge 1.12 equivalents of the upstream server fluid settings. */
@Config(modid = Reference.MOD_ID, name = Reference.MOD_ID + "/fluids")
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class FluidConfig {
    @Config.RangeInt(min = 1, max = 932)
    @Config.RequiresMcRestart
    @Config.Comment("Capacity of each fluid tank block, in buckets.")
    public static int fluidTankCapacity = 8;

    @Config.RangeInt(min = 1, max = 256)
    @Config.RequiresMcRestart
    @Config.Comment("Maximum height of a fluid tank, in blocks.")
    public static int fluidTankMaxHeight = 32;

    @Config.RangeInt(min = 1, max = 4096)
    @Config.Comment("Maximum pressure propagation distance on each side of a mechanical pump.")
    public static int mechanicalPumpRange = 16;

    @Config.RangeInt(min = 1, max = 4096)
    @Config.Comment("Maximum radius searched by a hose pulley.")
    public static int hosePulleyRange = 128;

    @Config.RangeInt(min = -1, max = 1000000)
    @Config.Comment("Fluid block threshold for a bottomless body; -1 disables the threshold.")
    public static int hosePulleyBlockThreshold = 10000;

    @Config.Comment("Whether a hose pulley continues placing sources beyond the threshold.")
    public static boolean fillInfinite = false;
    public static boolean fluidFillPlaceFluidSourceBlocks = true;
    public static boolean pipesPlaceFluidSourceBlocks = true;

    @Config.Comment("1.12 uses registered fluid names instead of 1.21 fluid tags.")
    public static BottomlessMode bottomlessFluidMode = BottomlessMode.ALLOW_BY_LIST;
    public static String[] bottomlessFluids = {"water", "lava"};

    private FluidConfig() { }

    public enum BottomlessMode { ALLOW_ALL, DENY_ALL, ALLOW_BY_LIST, DENY_BY_LIST }

    static boolean canDrainInfinitely(Fluid fluid) {
        if (fluid == null || hosePulleyBlockThreshold < 0) return false;
        boolean listed = false;
        for (String name : bottomlessFluids) {
            if (fluid.getName().equals(name) || ("minecraft:" + fluid.getName()).equals(name)) {
                listed = true;
                break;
            }
        }
        return switch (bottomlessFluidMode) {
            case ALLOW_ALL -> true;
            case DENY_ALL -> false;
            case ALLOW_BY_LIST -> listed;
            case DENY_BY_LIST -> !listed;
        };
    }

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (Reference.MOD_ID.equals(event.getModID())) ConfigManager.sync(Reference.MOD_ID, Config.Type.INSTANCE);
    }
}
