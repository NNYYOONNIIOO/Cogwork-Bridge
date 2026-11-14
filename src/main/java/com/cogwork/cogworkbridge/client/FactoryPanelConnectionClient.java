package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelBlock;
import com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelConnectionPacket;
import com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelTileEntity;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class FactoryPanelConnectionClient {
    private static World world;
    private static BlockPos destination;
    private static int slot, amount;
    private FactoryPanelConnectionClient() { }

    public static void begin(BlockPos pos, int panelSlot, int inputAmount) {
        Minecraft mc = Minecraft.getMinecraft();
        world = mc.world; destination = pos.toImmutable(); slot = panelSlot; amount = inputAmount;
        if (mc.player != null) mc.player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.factory.select_input"), true);
    }

    @SubscribeEvent public static void click(PlayerInteractEvent.RightClickBlock event) {
        if (destination == null || event.getHand() != EnumHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world != world || mc.player == null || event.getEntityPlayer() != mc.player) { destination = null; return; }
        BlockPos source = event.getPos(); var state = world.getBlockState(source);
        if (state.getBlock() instanceof FactoryPanelBlock && event.getHitVec() != null) {
            Vec3d local = event.getHitVec().subtract(source.getX(), source.getY(), source.getZ());
            int sourceSlot = FactoryPanelBlock.slot(state.getValue(FactoryPanelBlock.FACING), (float) local.x, (float) local.y, (float) local.z);
            ModNetwork.CHANNEL.sendToServer(new FactoryPanelConnectionPacket(destination, slot, source, sourceSlot, amount));
        } else if (state.getBlock() instanceof com.cogwork.cogworkbridge.content.display.DisplayLinkBlock) {
            // The same panel-selection interaction can bind a gauge to a display link.
            // Sneaking while selecting the link removes that particular binding.
            ModNetwork.CHANNEL.sendToServer(new FactoryPanelConnectionPacket(destination, slot, source,
                    mc.player.isSneaking() ? 1 : 0, amount));
        }
        destination = null; event.setCancellationResult(EnumActionResult.SUCCESS); event.setCanceled(true);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || destination == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world != world || mc.player == null || mc.player.getDistanceSq(destination) > 256
                || !(world.getTileEntity(destination) instanceof FactoryPanelTileEntity panel) || !panel.active(slot)) destination = null;
    }
}
