package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeBlock;
import com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.deployer.DeployerBlock;
import com.cogwork.cogworkbridge.content.kinetics.deployer.DeployerTileEntity;
import com.cogwork.cogworkbridge.content.logistics.funnel.FunnelBlock;
import com.cogwork.cogworkbridge.content.logistics.funnel.FunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BrassTunnelTileEntity;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.ArrayList;
import java.util.List;

/** Shows a value-box hint only while the crosshair is on the actual control. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class InteractionHoverOverlay {
    private InteractionHoverOverlay() { }

    public static List<String> targetHint() {
        List<String> lines = new ArrayList<>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK
                || mc.player.getHeldItemMainhand().getItem() == AllItems.WRENCH
                || mc.player.getHeldItemOffhand().getItem() == AllItems.WRENCH) return lines;
        RayTraceResult hit = mc.objectMouseOver;
        if (hit.sideHit == null || hit.hitVec == null) return lines;
        BlockPos pos = hit.getBlockPos();
        IBlockState state = mc.world.getBlockState(pos);
        TileEntity tile = mc.world.getTileEntity(pos);
        Vec3d local = hit.hitVec.subtract(new Vec3d(pos));
        ItemStack filter = ItemStack.EMPTY;
        String header = "gui.cogworkbridge.hover.filter";
        boolean selected = false, configurable = false;
        if (tile instanceof com.cogwork.cogworkbridge.content.logistics.table.TableClothTileEntity cloth
                && com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock.targetsPrice(mc.world,pos,cloth,local)) {
            selected=true; configurable=cloth.maySetPrice(mc.player); filter=cloth.price();
            header="gui.cogworkbridge.table_cloth.price";
            lines.add("\u00a7f"+cloth.priceCount());
        } else if (tile instanceof com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity detector
                && state.getBlock() instanceof com.cogwork.cogworkbridge.content.redstone.DetectorBlock
                && com.cogwork.cogworkbridge.content.redstone.DetectorBlock.isFilterSide(state, hit.sideHit)) {
            Vec3d delta = local.subtract(com.cogwork.cogworkbridge.content.redstone.DetectorBlock.filterCenter(hit.sideHit));
            double distance = (hit.sideHit.getAxis() == EnumFacing.Axis.X ? 0 : delta.x * delta.x)
                    + (hit.sideHit.getAxis() == EnumFacing.Axis.Y ? 0 : delta.y * delta.y)
                    + (hit.sideHit.getAxis() == EnumFacing.Axis.Z ? 0 : delta.z * delta.z);
            if (distance <= .2D * .2D) { selected = true; filter = detector.getFilter(); }
        } else if (tile instanceof com.cogwork.cogworkbridge.content.redstone.PulseDiodeTileEntity diode
                && hit.sideHit == EnumFacing.UP && Math.abs(local.x - .5D) <= .24D && Math.abs(local.z - .5D) <= .24D) {
            int ticks = diode.getDelay();
            lines.add("\u00a77" + I18n.format("gui.cogworkbridge.pulse.interval"));
            lines.add("\u00a7f" + (ticks < 60 ? ticks + "t" : ticks < 1200 ? ticks / 20 + "s" : ticks / 1200 + "m"));
            configurable = true;
        } else if (tile instanceof com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawTileEntity saw
                && state.getBlock() instanceof com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock
                && com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock.isFilterPanelHit(mc.world, pos,
                        state, hit.sideHit, (float) local.x, (float) local.y, (float) local.z)) {
            selected = true; filter = saw.getFilter();
        } else if (tile instanceof DeployerTileEntity deployer && state.getBlock() instanceof DeployerBlock
                && DeployerBlock.isFilterPanelHit(state, hit.sideHit, (float) local.x, (float) local.y, (float) local.z)) {
            selected = true; filter = deployer.getFilter();
        } else if (tile instanceof FunnelTileEntity funnel && state.getBlock() instanceof FunnelBlock block && block.isBrass()
                && FunnelBlock.isFilterSlot(mc.world, pos, state, hit.sideHit, (float) local.x, (float) local.y, (float) local.z)) {
            selected = true; filter = funnel.getFilter(); configurable = true;
        } else if (tile instanceof BrassTunnelTileEntity tunnel) {
            if (hit.sideHit.getAxis().isHorizontal()) {
                double lateral = hit.sideHit.getAxis() == EnumFacing.Axis.X ? local.z : local.x;
                if (Math.abs(lateral - .5D) <= .18D && Math.abs(local.y - .8125D) <= .18D) {
                    selected = true; configurable = true; filter = tunnel.filters()[hit.sideHit.getIndex()];
                }
            } else if (hit.sideHit == EnumFacing.UP && Math.abs(local.x - .5D) <= .2D && Math.abs(local.z - .5D) <= .2D) {
                lines.add("\u00a77" + I18n.format("gui.cogworkbridge.tunnel.mode_title"));
                lines.add("\u00a7f" + I18n.format("cogworkbridge.tunnel.mode." + tunnel.getMode()));
                configurable = true;
            }
        } else if (tile instanceof SmartFluidPipeTileEntity pipe && state.getBlock() instanceof SmartFluidPipeBlock
                && local.squareDistanceTo(SmartFluidPipeBlock.filterCenter(state)) <= .22D * .22D) {
            selected = true; filter = pipe.getFilterItem();
        } else if (tile instanceof com.cogwork.cogworkbridge.content.logistics.chute.ChuteTileEntity chute
                && state.getBlock() instanceof com.cogwork.cogworkbridge.content.logistics.chute.ChuteBlock block
                && block.isSmart() && hit.sideHit.getAxis().isHorizontal() && local.y > .45D && local.y < .9D
                && Math.abs((hit.sideHit.getAxis() == EnumFacing.Axis.X ? local.z : local.x) - .5D) < .2D) {
            selected = true; filter = chute.getFilter();
        } else if (tile instanceof com.cogwork.cogworkbridge.content.logistics.crate.CreativeCrateTileEntity crate
                && hit.sideHit == EnumFacing.UP && local.x >= .2D && local.x <= .8D && local.z >= .2D && local.z <= .8D) {
            selected = true; filter = crate.getSelection();
        } else if (tile instanceof com.cogwork.cogworkbridge.content.redstone.RedstoneLinkTileEntity link
                && state.getBlock() instanceof com.cogwork.cogworkbridge.content.redstone.RedstoneLinkBlock
                && mc.player.getHeldItemMainhand().getItem() != AllItems.LINKED_CONTROLLER
                && mc.player.getHeldItemOffhand().getItem() != AllItems.LINKED_CONTROLLER) {
            EnumFacing facing = state.getValue(com.cogwork.cogworkbridge.content.redstone.RedstoneLinkBlock.FACING);
            for (int slot = 0; slot < 2; slot++) {
                Vec3d delta = local.subtract(com.cogwork.cogworkbridge.content.redstone.RedstoneLinkBlock.frequencySlotCenter(facing, slot));
                double distance = (facing.getAxis() == EnumFacing.Axis.X ? 0 : delta.x * delta.x)
                        + (facing.getAxis() == EnumFacing.Axis.Y ? 0 : delta.y * delta.y)
                        + (facing.getAxis() == EnumFacing.Axis.Z ? 0 : delta.z * delta.z);
                if (distance <= .14D * .14D) {
                    selected = true; filter = link.getFrequency(slot); header = "gui.cogworkbridge.hover.frequency"; break;
                }
            }
        }
        if (selected) {
            lines.add("\u00a77" + I18n.format(header));
            lines.add("\u00a7f" + (filter.isEmpty() ? I18n.format("gui.cogworkbridge.hover.any") : filter.getDisplayName()));
            lines.add("\u00a78" + I18n.format("gui.cogworkbridge.hover.mark", mc.gameSettings.keyBindUseItem.getDisplayName()));
        }
        if (configurable && mc.player.getHeldItemMainhand().isEmpty() && mc.player.getHeldItemOffhand().isEmpty())
            lines.add("\u00a78" + I18n.format("gui.cogworkbridge.kinetic.hold_to_configure", mc.gameSettings.keyBindUseItem.getDisplayName()));
        return lines;
    }

    @SubscribeEvent public static void render(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || mc.currentScreen != null || mc.gameSettings.hideGUI) return;
        List<String> lines = targetHint();
        if (!lines.isEmpty()) CreateOverlayTooltip.draw(lines, event.getResolution(), event.getResolution().getScaledWidth() / 2 + 16,
                event.getResolution().getScaledHeight() / 2 + 12, ItemStack.EMPTY);
    }
}
