package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.kinetics.turntable.TurntableBlock;
import com.cogwork.cogworkbridge.content.kinetics.turntable.TurntableTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Rotates the local player's view smoothly while standing on a powered turntable. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class TurntableClientEvents {
    private static long lastFrameNanos;

    private TurntableClientEvents() { }

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        float elapsedTicks = lastFrameNanos == 0 ? 0F : Math.min((now - lastFrameNanos) / 50_000_000F, 1F);
        lastFrameNanos = now;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.playerController == null || mc.isGamePaused() || !mc.player.onGround) return;
        BlockPos pos = new BlockPos(mc.player.posX, mc.player.posY - .2D, mc.player.posZ);
        if (!(mc.world.getBlockState(pos).getBlock() instanceof TurntableBlock)
                || !(mc.world.getTileEntity(pos) instanceof TurntableTileEntity table)) return;

        float speed = table.getSpeed() * (2F / 3F) * elapsedTicks;
        if (speed == 0F) return;
        double dx = mc.player.posX - pos.getX() - .5D;
        double dy = mc.player.posY - pos.getY() - .5D;
        double dz = mc.player.posZ - pos.getZ() - .5D;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > .25D) speed *= (float) MathHelper.clamp((.5D - distance) * 2D, 0D, 1D);

        float angle = speed * event.renderTickTime;
        mc.player.rotationYaw -= angle;
        mc.player.prevRotationYaw -= angle;
        mc.player.renderYawOffset -= angle;
        mc.player.prevRenderYawOffset -= angle;
    }
}
