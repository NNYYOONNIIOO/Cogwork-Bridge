package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorInteraction;
import com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorInteractionPacket;
import com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ChainConveyorClient {
    private static net.minecraft.util.math.BlockPos firstWheel;
    private static net.minecraft.world.World firstWorld;
    private ChainConveyorClient() { }
    private static boolean eligible(ItemStack held, boolean sneaking) {
        return held.isEmpty() || PackageItem.isPackage(held) || held.getItem() instanceof ItemBlock item && item.getBlock() == AllBlocks.PACKAGE_FROGPORT
                || held.getItem() == AllItems.WRENCH;
    }
    private static boolean use(EnumHand hand) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.currentScreen != null || !eligible(mc.player.getHeldItem(hand), mc.player.isSneaking())) return false;
        if (mc.player.getHeldItem(hand).getItem() == AllItems.WRENCH && !mc.player.isSneaking()) {
            var chain = ChainConveyorInteraction.trace(mc.player, mc.playerController.getBlockReachDistance());
            if (chain != null) { ChainConveyorRideClient.start(chain.wheel(), chain.end(), chain.progress()); mc.player.swingArm(hand); return true; }
            var hit = mc.objectMouseOver;
            if (hit != null && hit.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK
                    && mc.world.getTileEntity(hit.getBlockPos()) instanceof com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorTileEntity wheel) {
                float angle = com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorTileEntity.angle(hit.hitVec.x - wheel.getPos().getX() - .5, hit.hitVec.z - wheel.getPos().getZ() - .5);
                ChainConveyorRideClient.start(wheel, null, angle); mc.player.swingArm(hand); return true;
            }
            return false;
        }
        if (mc.player.getHeldItem(hand).isEmpty()
                ? ChainConveyorInteraction.traceParcel(mc.player, mc.playerController.getBlockReachDistance()) == null
                : ChainConveyorInteraction.trace(mc.player, mc.playerController.getBlockReachDistance()) == null) return false;
        ModNetwork.CHANNEL.sendToServer(new ChainConveyorInteractionPacket(hand)); mc.player.swingArm(hand); return true;
    }
    private static boolean selectWheel(PlayerInteractEvent event) {
        if (!event.getWorld().isRemote || !com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorBlock.isChain(event.getItemStack())) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return false;
        if (mc.player.isSneaking()) { firstWheel = null; return true; }
        var wheel = ChainConveyorInteraction.traceWheel(mc.player, mc.playerController.getBlockReachDistance());
        if (wheel == null) { firstWheel = null; return false; }
        if (firstWheel == null || firstWorld != mc.world) {
            firstWheel = wheel.getPos().toImmutable(); firstWorld = mc.world;
            mc.player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("cogworkbridge.chain.select_second"), true);
        } else if (!firstWheel.equals(wheel.getPos())) {
            ModNetwork.CHANNEL.sendToServer(new com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorConnectionPacket(firstWheel, wheel.getPos(), event.getHand()));
            firstWheel = null;
        }
        mc.player.swingArm(event.getHand());
        return true;
    }
    @SubscribeEvent public static void clickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getWorld().isRemote) return;
        if (selectWheel(event) || use(event.getHand())) { event.setCancellationResult(EnumActionResult.SUCCESS); event.setCanceled(true); }
    }
    @SubscribeEvent public static void clickAir(PlayerInteractEvent.RightClickItem event) {
        if (!event.getWorld().isRemote) return;
        if (selectWheel(event) || use(event.getHand())) { event.setCancellationResult(EnumActionResult.SUCCESS); event.setCanceled(true); }
    }
    @SubscribeEvent public static void clickEmpty(PlayerInteractEvent.RightClickEmpty event) { if (event.getWorld().isRemote) use(event.getHand()); }
    @SubscribeEvent public static void outline(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        drawSelection(mc, event.getPartialTicks());
        if (mc.player == null || mc.world == null || mc.currentScreen != null
                || !eligible(mc.player.getHeldItemMainhand(), mc.player.isSneaking()) && !eligible(mc.player.getHeldItemOffhand(), mc.player.isSneaking())) return;
        if (mc.player.getHeldItemMainhand().isEmpty() && mc.player.getHeldItemOffhand().isEmpty()) return;
        var hit = ChainConveyorInteraction.trace(mc.player, mc.playerController.getBlockReachDistance());
        if (hit == null) return;
        float p = event.getPartialTicks();
        double x = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * p;
        double y = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * p;
        double z = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * p;
        var v = hit.point(); GlStateManager.pushMatrix(); GlStateManager.enableBlend(); GlStateManager.disableTexture2D();
        GlStateManager.glLineWidth(3F); RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(v.x-.1-x,v.y-.1-y,v.z-.1-z,v.x+.1-x,v.y+.1-y,v.z+.1-z), .8F, 1F, .7F, .9F);
        GlStateManager.glLineWidth(1F); GlStateManager.enableTexture2D(); GlStateManager.disableBlend(); GlStateManager.popMatrix();
    }
    private static void drawSelection(Minecraft mc, float partial) {
        if (firstWheel == null || mc.world != firstWorld || mc.player == null || mc.currentScreen != null) return;
        if (!mc.world.isBlockLoaded(firstWheel) || !(mc.world.getTileEntity(firstWheel) instanceof com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorTileEntity)) { firstWheel=null; return; }
        if (!com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorBlock.isChain(mc.player.getHeldItemMainhand())
                && !com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorBlock.isChain(mc.player.getHeldItemOffhand())) return;
        double x=mc.player.lastTickPosX+(mc.player.posX-mc.player.lastTickPosX)*partial;
        double y=mc.player.lastTickPosY+(mc.player.posY-mc.player.lastTickPosY)*partial;
        double z=mc.player.lastTickPosZ+(mc.player.posZ-mc.player.lastTickPosZ)*partial;
        GlStateManager.pushMatrix(); GlStateManager.enableBlend(); GlStateManager.disableTexture2D(); GlStateManager.glLineWidth(3F);
        RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(firstWheel).grow(.05).offset(-x,-y,-z), .5F,1F,.4F,.9F);
        var hit=mc.objectMouseOver;
        if(hit!=null && hit.typeOfHit==net.minecraft.util.math.RayTraceResult.Type.BLOCK
                && mc.world.getTileEntity(hit.getBlockPos()) instanceof com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorTileEntity
                && !hit.getBlockPos().equals(firstWheel)) {
            var end=hit.getBlockPos(); var tess=net.minecraft.client.renderer.Tessellator.getInstance(); var b=tess.getBuffer();
            b.begin(org.lwjgl.opengl.GL11.GL_LINES, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
            b.pos(firstWheel.getX()+.5-x,firstWheel.getY()+.375-y,firstWheel.getZ()+.5-z).color(.5F,1F,.4F,1F).endVertex();
            b.pos(end.getX()+.5-x,end.getY()+.375-y,end.getZ()+.5-z).color(.5F,1F,.4F,1F).endVertex(); tess.draw();
        }
        GlStateManager.glLineWidth(1F); GlStateManager.enableTexture2D(); GlStateManager.disableBlend(); GlStateManager.popMatrix();
    }
}
