package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/** Read the synchronized data and capabilities of the machine under the crosshair. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class GogglesOverlay {
    private GogglesOverlay() { }

    @SubscribeEvent public static void render(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || mc.player == null || mc.world == null
                || mc.currentScreen != null || mc.gameSettings.hideGUI || mc.player.isSpectator()
                || mc.player.getItemStackFromSlot(EntityEquipmentSlot.HEAD).getItem() != AllItems.GOGGLES) return;
        TileEntity tile;
        EnumFacing side;
        World hoverWorld;
        BlockPos hoverPos;
        ContraptionInteractionClientEvents.ContraptionHit dynamic = mc.playerController == null
                ? null : ContraptionInteractionClientEvents.currentHit();
        if (dynamic != null) {
            hoverWorld = dynamic.entity().getInteractionWorld();
            hoverPos = dynamic.hit().localPos();
            tile = hoverWorld.getTileEntity(hoverPos);
            side = dynamic.hit().side();
        } else {
            if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK) return;
            if (!InteractionHoverOverlay.targetHint().isEmpty()) return;
            hoverWorld = mc.world;
            hoverPos = mc.objectMouseOver.getBlockPos();
            tile = hoverWorld.getTileEntity(hoverPos);
            side = mc.objectMouseOver.sideHit;
            if (KineticMachineConfigClientEvents.hasTargetHint(mc.world.getBlockState(mc.objectMouseOver.getBlockPos()),
                    tile, mc.objectMouseOver.getBlockPos(), mc.objectMouseOver)) return;
        }
        List<String> lines = new ArrayList<>();
        if (tile == null) {
            addPistonPoleInfo(lines, hoverWorld, hoverPos);
            if (!lines.isEmpty()) CreateOverlayTooltip.draw(lines, event.getResolution(),
                    event.getResolution().getScaledWidth() / 2 + 16,
                    event.getResolution().getScaledHeight() / 2 + 12, new ItemStack(AllItems.GOGGLES));
            return;
        }
        com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleTileEntity whistle = null;
        if (tile instanceof com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleTileEntity base) {
            whistle = base;
        } else if (tile instanceof com.cogwork.cogworkbridge.content.fluids.whistle.WhistleExtensionTileEntity) {
            BlockPos root = com.cogwork.cogworkbridge.content.fluids.whistle.WhistleExtensionBlock.root(hoverWorld, hoverPos);
            if (hoverWorld.isBlockLoaded(root) && hoverWorld.getTileEntity(root) instanceof com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleTileEntity base)
                whistle = base;
        }
        if (whistle != null) {
            String[] notes = I18n.format("gui.cogworkbridge.goggles.whistle_notes").split(";");
            lines.add("\u00a77" + I18n.format("gui.cogworkbridge.goggles.whistle_pitch", notes[Math.floorMod(whistle.pitch(), notes.length)]));
        }
        if (tile instanceof com.cogwork.cogworkbridge.content.kinetics.deployer.DeployerTileEntity deployer) {
            lines.add("\u00a77" + I18n.format("gui.cogworkbridge.goggles.deployer"));
            lines.add(" \u00a7f" + I18n.format("gui.cogworkbridge.goggles.deployer." + deployer.getMode().name().toLowerCase(java.util.Locale.ROOT)));
            if (!deployer.getHeldItem().isEmpty())
                lines.add(" \u00a77" + I18n.format("gui.cogworkbridge.goggles.deployer_item", deployer.getHeldItem().getDisplayName()));
        }
        if (tile instanceof com.cogwork.cogworkbridge.content.redstone.AnalogLeverTileEntity lever)
            lines.add(I18n.format("gui.cogworkbridge.goggles.analog_strength", lever.value()));
        if (tile instanceof com.cogwork.cogworkbridge.content.logistics.chute.ChuteTileEntity chute) {
            lines.add("\u00a77" + I18n.format("gui.cogworkbridge.goggles.chute.header"));
            if (chute.getPullForce() == 0 && chute.getPushForce() == 0)
                lines.add(" \u00a77" + I18n.format("gui.cogworkbridge.goggles.chute.no_fans"));
            if (chute.getPullForce() != 0)
                lines.add(" \u00a77" + I18n.format("gui.cogworkbridge.goggles.chute." + (chute.getPullForce() > 0 ? "pull_up" : "push_down")));
            if (chute.getPushForce() != 0)
                lines.add(" \u00a77" + I18n.format("gui.cogworkbridge.goggles.chute." + (chute.getPushForce() > 0 ? "push_up" : "pull_down")));
            lines.add(" \u00a7e" + I18n.format("gui.cogworkbridge.goggles.chute." + (chute.getItemMotion() < 0 ? "move_down" : "move_up")));
            ItemStack carried = chute.getItem();
            if (!carried.isEmpty()) lines.add(" \u00a7a" + I18n.format("gui.cogworkbridge.goggles.chute.contains", carried.getDisplayName(), carried.getCount()));
        }
        KineticTileEntity kinetic = tile instanceof KineticTileEntity value ? value : null;
        if (tile instanceof com.cogwork.cogworkbridge.content.kinetics.steam.SteamEngineTileEntity engine
                && engine.getWorld() != null && engine.getWorld().isBlockLoaded(engine.shaftPos())
                && engine.getWorld().getTileEntity(engine.shaftPos()) instanceof KineticTileEntity shaft) kinetic = shaft;
        if (kinetic != null) {
            lines.add("\u00a77" + I18n.format("gui.cogworkbridge.goggles.kinetic_header"));
            lines.add(" \u00a7b" + I18n.format("gui.cogworkbridge.goggles.speed", Math.abs(kinetic.getSpeed())));
            if (kinetic.getStressImpact() > 0)
                lines.add(" \u00a7b" + I18n.format("gui.cogworkbridge.goggles.impact", kinetic.stressForNetwork()));
            if (kinetic.isSource())
                lines.add(" \u00a7b" + I18n.format("gui.cogworkbridge.goggles.capacity", kinetic.stressCapacity() * Math.abs(kinetic.generatedSpeed())));
            lines.add(" \u00a77" + I18n.format("gui.cogworkbridge.goggles.stress", kinetic.getNetworkStress(), kinetic.getNetworkCapacity()));
            if (kinetic.isOverstressed()) lines.add("\u00a7c" + I18n.format("gui.cogworkbridge.goggles.overstressed"));
            if (tile instanceof com.cogwork.cogworkbridge.content.kinetics.gauge.GaugeTileEntity
                    && tile.getWorld().getBlockState(tile.getPos()).getBlock() instanceof com.cogwork.cogworkbridge.content.kinetics.gauge.GaugeBlock gauge
                    && gauge.isStressGauge())
                lines.add(" \u00a7b" + I18n.format("gui.cogworkbridge.goggles.remaining", Math.max(0F, kinetic.getNetworkCapacity() - kinetic.getNetworkStress())));
        }
        boolean isBoiler = tile instanceof com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity tank && tank.isBoiler();
        IFluidHandler fluid = isBoiler ? null : tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
        if (fluid == null && !isBoiler) fluid = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
        if (fluid != null) {
            for (IFluidTankProperties tank : fluid.getTankProperties()) {
                FluidStack contents = tank.getContents();
                if (contents != null && contents.amount > 0)
                    lines.add("\u00a7b" + I18n.format("gui.cogworkbridge.goggles.fluid", contents.getLocalizedName(), contents.amount, tank.getCapacity()));
                else if (tank.getCapacity() > 0)
                    lines.add("\u00a77" + I18n.format("gui.cogworkbridge.goggles.empty_tank", tank.getCapacity()));
            }
        }
        IItemHandler items = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (items == null) items = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
        if (items != null && items.getSlots() > 0) {
            int occupied = 0;
            int count = 0;
            for (int i = 0; i < items.getSlots(); i++) {
                if (!items.getStackInSlot(i).isEmpty()) occupied++;
                count += items.getStackInSlot(i).getCount();
            }
            lines.add(I18n.format("gui.cogworkbridge.goggles.items", occupied, items.getSlots(), count));
            if (tile instanceof com.cogwork.cogworkbridge.content.processing.basin.BasinTileEntity) {
                for (int i = 0; i < items.getSlots(); i++) {
                    ItemStack stack = items.getStackInSlot(i);
                    if (!stack.isEmpty()) lines.add(" \u00a77" + stack.getDisplayName() + " \u00a7ax" + stack.getCount());
                }
            }
        }
        if (isBoiler && tile instanceof com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity tank) {
            com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity root = tank.getControllerTile();
            if (root != null) {
                String efficiency = String.format(java.util.Locale.ROOT, "%.0f%%", root.getBoilerEfficiency() * 100F);
                String water = String.format(java.util.Locale.ROOT, "%.1f", root.getBoilerWaterRate());
                String heat = root.hasPassiveBoilerHeat() ? I18n.format("gui.cogworkbridge.goggles.boiler.passive") : Integer.toString(root.getBoilerHeat());
                lines.add("\u00a77" + I18n.format("gui.cogworkbridge.goggles.boiler.status", efficiency));
                lines.add(" \u00a77" + I18n.format("gui.cogworkbridge.goggles.boiler.size", root.getWidth(), root.getWidth(), root.getHeight()));
                lines.add(" \u00a77" + I18n.format("gui.cogworkbridge.goggles.boiler.water", water));
                lines.add(" \u00a77" + I18n.format("gui.cogworkbridge.goggles.boiler.heat", heat));
            }
        }
        if (tile instanceof com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity detector) {
            lines.add(I18n.format("gui.cogworkbridge.goggles.observed", detector.getAmount(), detector.getMaximum()));
            if (!detector.getFilter().isEmpty()) lines.add(I18n.format("gui.cogworkbridge.goggles.filter", detector.getFilter().getDisplayName()));
        }
        if (tile instanceof com.cogwork.cogworkbridge.content.logistics.funnel.FunnelTileEntity funnel) {
            lines.add(I18n.format(funnel.isExtracting() ? "gui.cogworkbridge.goggles.extracting" : "gui.cogworkbridge.goggles.inserting"));
            if (!funnel.getFilter().isEmpty()) lines.add(I18n.format("gui.cogworkbridge.goggles.filter", funnel.getFilter().getDisplayName()));
        }
        if (tile instanceof com.cogwork.cogworkbridge.content.logistics.port.PackagePortTileEntity port && !port.address().isEmpty())
            lines.add(I18n.format("gui.cogworkbridge.goggles.address", port.address()));
        if (lines.isEmpty()) return;
        CreateOverlayTooltip.draw(lines, event.getResolution(), event.getResolution().getScaledWidth() / 2 + 16,
                event.getResolution().getScaledHeight() / 2 + 12, new ItemStack(AllItems.GOGGLES));
    }

    private static void addPistonPoleInfo(List<String> lines, World world, BlockPos pos) {
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof com.cogwork.cogworkbridge.content.contraptions.piston.PistonPoleBlock pole)
                || pole.isHead()) return;
        EnumFacing.Axis axis = state.getValue(com.cogwork.cogworkbridge.content.contraptions.piston.PistonPoleBlock.FACING).getAxis();
        EnumFacing forward = EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, axis);
        int length = 1;
        boolean pistonFound = false;
        for (EnumFacing direction : new EnumFacing[]{forward, forward.getOpposite()}) {
            for (int distance = 1; distance <= 64; distance++) {
                BlockPos next = pos.offset(direction, distance);
                if (!world.isBlockLoaded(next)) break;
                net.minecraft.block.state.IBlockState neighbour = world.getBlockState(next);
                if (neighbour.getBlock() instanceof com.cogwork.cogworkbridge.content.contraptions.piston.PistonPoleBlock nextPole
                        && !nextPole.isHead()
                        && neighbour.getValue(com.cogwork.cogworkbridge.content.contraptions.piston.PistonPoleBlock.FACING).getAxis() == axis) {
                    length++;
                    continue;
                }
                if (neighbour.getBlock() instanceof com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock
                        && neighbour.getValue(com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock.FACING).getAxis() == axis)
                    pistonFound = true;
                break;
            }
        }
        if (pistonFound) lines.add("\u00a77" + I18n.format("gui.cogworkbridge.goggles.pole_length", length));
    }
}
