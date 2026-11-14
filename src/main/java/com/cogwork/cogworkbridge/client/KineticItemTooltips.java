package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ClockworkBearingTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.MechanicalBearingTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.WindmillBearingTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.crank.HandCrankTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.motor.CreativeMotorTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.steam.PoweredShaftTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.steam.SteamEngineBlock;
import com.cogwork.cogworkbridge.content.kinetics.waterwheel.LargeWaterWheelTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.waterwheel.WaterWheelTileEntity;
import com.cogwork.cogworkbridge.content.processing.mixer.MechanicalMixerTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Item descriptions use the same stress coefficients as the kinetic network. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class KineticItemTooltips {
    private static final Map<Block, Stats> STATS = new HashMap<>();

    private KineticItemTooltips() { }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof ItemBlock item)) return;
        Block block = item.getBlock();
        if (block.getRegistryName() == null || !Reference.MOD_ID.equals(block.getRegistryName().getNamespace())) return;

        EntityPlayer player = event.getEntityPlayer();
        boolean goggles = player != null && player.getItemStackFromSlot(EntityEquipmentSlot.HEAD).getItem() == AllItems.GOGGLES;
        List<String> tooltip = event.getToolTip();
        if (block instanceof SteamEngineBlock) {
            tooltip.add("");
            tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.cogworkbridge.kinetics.capacity"));
            tooltip.add(TextFormatting.GOLD + " " + I18n.format("tooltip.cogworkbridge.kinetics.boiler"));
            if (goggles) {
                float maximum = PoweredShaftTileEntity.BASE_SPEED * PoweredShaftTileEntity.CAPACITY_SCALE;
                tooltip.add(TextFormatting.DARK_GRAY + "  " + I18n.format("tooltip.cogworkbridge.kinetics.maximum", number(maximum)));
            }
            return;
        }
        if (!(block instanceof KineticBlock kineticBlock)) return;
        Stats stats = STATS.computeIfAbsent(block, unused -> readStats(kineticBlock));
        if (stats.impact <= 0 && stats.capacity <= 0 && stats.minimumSpeed <= 0) return;

        tooltip.add("");
        if (stats.minimumSpeed > 0) {
            tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.cogworkbridge.kinetics.minimum_speed"));
            tooltip.add(TextFormatting.GOLD + " \u2588\u2588\u2592 "
                    + I18n.format("tooltip.cogworkbridge.kinetics.speed", number(stats.minimumSpeed)));
        }
        if (stats.impact > 0) {
            tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.cogworkbridge.kinetics.impact"));
            appendRating(tooltip, stats.impact, false, goggles);
            if (stats.assembledOnly)
                tooltip.add(TextFormatting.DARK_GRAY + "  " + I18n.format("tooltip.cogworkbridge.kinetics.assembled"));
        }
        if (stats.capacity > 0) {
            tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.cogworkbridge.kinetics.capacity"));
            appendRating(tooltip, stats.capacity, true, goggles);
            if (goggles && stats.maximumSpeed > 0) {
                tooltip.add(TextFormatting.DARK_GRAY + "  " + I18n.format("tooltip.cogworkbridge.kinetics.maximum",
                        number(stats.capacity * stats.maximumSpeed)));
            }
            if (stats.windmill)
                tooltip.add(TextFormatting.DARK_GRAY + "  " + I18n.format("tooltip.cogworkbridge.kinetics.windmill"));
        }
    }

    private static Stats readStats(KineticBlock block) {
        TileEntity tile = block.createNewTileEntity(null, 0);
        if (!(tile instanceof KineticTileEntity kinetic)) return new Stats(0, 0, 0, 0, false, false);
        boolean windmill = kinetic instanceof WindmillBearingTileEntity;
        boolean bearing = !windmill && kinetic instanceof MechanicalBearingTileEntity;
        boolean clock = kinetic instanceof ClockworkBearingTileEntity;
        float impact = bearing ? MechanicalBearingTileEntity.ASSEMBLED_STRESS_IMPACT
                : clock ? ClockworkBearingTileEntity.ASSEMBLED_STRESS_IMPACT : kinetic.getStressImpact();
        float maximumSpeed = kinetic instanceof HandCrankTileEntity ? HandCrankTileEntity.GENERATED_SPEED
                : kinetic instanceof WaterWheelTileEntity ? WaterWheelTileEntity.GENERATED_SPEED
                : kinetic instanceof LargeWaterWheelTileEntity ? LargeWaterWheelTileEntity.GENERATED_SPEED
                : windmill ? WindmillBearingTileEntity.MAX_GENERATED_SPEED
                : kinetic instanceof CreativeMotorTileEntity ? CreativeMotorTileEntity.MAX_SPEED : 0;
        float minimumSpeed = kinetic instanceof MechanicalMixerTileEntity ? MechanicalMixerTileEntity.MINIMUM_PROCESSING_SPEED : 0;
        return new Stats(impact, kinetic.getStressCapacity(), maximumSpeed, minimumSpeed, bearing || clock, windmill);
    }

    private static void appendRating(List<String> tooltip, float value, boolean capacity, boolean goggles) {
        int level = capacity ? value >= 1024 ? 2 : value >= 256 ? 1 : 0
                : value >= 8 ? 2 : value >= 4 ? 1 : 0;
        TextFormatting color = capacity ? new TextFormatting[]{TextFormatting.GOLD, TextFormatting.YELLOW, TextFormatting.GREEN}[level]
                : new TextFormatting[]{TextFormatting.GREEN, TextFormatting.YELLOW, TextFormatting.GOLD}[level];
        String bar = level == 0 ? "\u2588\u2592\u2592" : level == 1 ? "\u2588\u2588\u2592" : "\u2588\u2588\u2588";
        String detail = goggles ? I18n.format("tooltip.cogworkbridge.kinetics.coefficient", number(value))
                : I18n.format("tooltip.cogworkbridge.kinetics." + (capacity ? "capacity_" : "impact_")
                        + new String[]{"low", "medium", "high"}[level]);
        tooltip.add(color + " " + bar + " " + TextFormatting.WHITE + detail);
    }

    private static String number(float value) {
        return value == Math.round(value) ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.2f", value);
    }

    private record Stats(float impact, float capacity, float maximumSpeed, float minimumSpeed,
                         boolean assembledOnly, boolean windmill) { }
}
