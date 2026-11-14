package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.redstone.PulseDiodeBlock;
import com.cogwork.cogworkbridge.content.redstone.PulseDiodeConfigPacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class PulseDiodeConfigClient {
    private static BlockPos held;
    private static int ticks;
    private PulseDiodeConfigClient() { }
    private static boolean eligible(Minecraft mc) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null || mc.player.isSneaking()
                || !mc.player.getHeldItemMainhand().isEmpty() || mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK || mc.objectMouseOver.sideHit != EnumFacing.UP
                || !(mc.world.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock() instanceof PulseDiodeBlock)) return false;
        var local = mc.objectMouseOver.hitVec.subtract(mc.objectMouseOver.getBlockPos().getX(), mc.objectMouseOver.getBlockPos().getY(), mc.objectMouseOver.getBlockPos().getZ());
        return Math.abs(local.x - .5D) <= .24D && Math.abs(local.z - .5D) <= .24D;
    }
    @SubscribeEvent public static void mouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getButton() < 0 || event.getButton() - 100 != mc.gameSettings.keyBindUseItem.getKeyCode()) return;
        if (event.isButtonstate() && !event.isCanceled() && eligible(mc)) {
            held = mc.objectMouseOver.getBlockPos().toImmutable(); ticks = 0; event.setCanceled(true);
        } else if (!event.isButtonstate() && held != null) {
            if (eligible(mc) && held.equals(mc.objectMouseOver.getBlockPos())) ModNetwork.CHANNEL.sendToServer(new PulseDiodeConfigPacket(held, 0));
            held = null; event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || held == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!eligible(mc) || !held.equals(mc.objectMouseOver.getBlockPos())) { held = null; return; }
        if (++ticks >= 6) { BlockPos pos = held; held = null; mc.displayGuiScreen(new PulseDiodeConfigScreen(pos)); }
    }
}
