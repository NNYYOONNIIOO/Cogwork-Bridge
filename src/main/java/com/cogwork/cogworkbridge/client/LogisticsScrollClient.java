package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.chute.SmartChuteTileEntity;
import com.cogwork.cogworkbridge.content.logistics.ejector.EjectorTileEntity;
import com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.chassis.ChassisTileEntity;
import com.cogwork.cogworkbridge.content.logistics.transport.LogisticsAmountPacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class LogisticsScrollClient {
    private LogisticsScrollClient() { }
    @SubscribeEvent public static void mouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getDwheel() == 0 || mc.currentScreen != null || mc.player == null || mc.world == null
                || mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK) return;
        TileEntity tile = mc.world.getTileEntity(mc.objectMouseOver.getBlockPos());
        if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.bearing.ClockworkBearingTileEntity
                && !mc.player.isSneaking()) {
            net.minecraft.block.state.IBlockState state = mc.world.getBlockState(mc.objectMouseOver.getBlockPos());
            if (state.getBlock() instanceof com.cogwork.cogworkbridge.kinetic.KineticBlock kinetic
                    && mc.objectMouseOver.sideHit.getAxis() != kinetic.getAxis(state)) {
                ModNetwork.CHANNEL.sendToServer(new LogisticsAmountPacket(mc.objectMouseOver.getBlockPos(),
                        event.getDwheel() > 0 ? 1 : -1, false));
                event.setCanceled(true);
                return;
            }
        }
        if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.pulley.RopePulleyTileEntity
                && mc.objectMouseOver.sideHit == net.minecraft.util.EnumFacing.UP && !mc.player.isSneaking()) {
            ModNetwork.CHANNEL.sendToServer(new LogisticsAmountPacket(mc.objectMouseOver.getBlockPos(),
                    event.getDwheel() > 0 ? 1 : -1, false));
            event.setCanceled(true);
            return;
        }
        boolean wrench = mc.player.getHeldItemMainhand().getItem() == AllItems.WRENCH
                || mc.player.getHeldItemOffhand().getItem() == AllItems.WRENCH;
        if (tile instanceof EjectorTileEntity || tile instanceof ChassisTileEntity) {
            if (!wrench) return;
        } else {
            if (!(tile instanceof SmartChuteTileEntity
                    || tile instanceof MechanicalArmTileEntity) || !mc.player.isSneaking()
                    || !mc.player.getHeldItemMainhand().isEmpty() || !mc.player.getHeldItemOffhand().isEmpty()) return;
        }
        boolean bulk = tile instanceof ChassisTileEntity
                && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL));
        ModNetwork.CHANNEL.sendToServer(new LogisticsAmountPacket(mc.objectMouseOver.getBlockPos(),
                event.getDwheel() > 0 ? 1 : -1, bulk));
        event.setCanceled(true);
    }
}
