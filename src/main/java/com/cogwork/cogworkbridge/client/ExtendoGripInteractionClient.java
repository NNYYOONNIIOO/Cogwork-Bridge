package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.equipment.ExtendoGripEntityPacket;
import com.cogwork.cogworkbridge.content.equipment.ExtendoGripItem;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ExtendoGripInteractionClient {
    private ExtendoGripInteractionClient() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (!event.isButtonstate() || event.isCanceled() || player == null || mc.world == null
                || mc.playerController == null || mc.currentScreen != null || !ExtendoGripItem.isHolding(player)) return;
        int key = event.getButton() - 100;
        boolean attack = key == mc.gameSettings.keyBindAttack.getKeyCode();
        if (!attack && key != mc.gameSettings.keyBindUseItem.getKeyCode()) return;
        double reach = mc.playerController.getBlockReachDistance();
        if (reach <= 3D) return;
        Vec3d eyes = player.getPositionEyes(1F), look = player.getLook(1F), end = eyes.add(look.scale(reach));
        RayTraceResult block = mc.world.rayTraceBlocks(eyes, end, false, true, false);
        double closest = block != null && block.typeOfHit == RayTraceResult.Type.BLOCK
                ? eyes.squareDistanceTo(block.hitVec) : reach * reach;
        for (BearingContraptionEntity structure : mc.world.getEntities(BearingContraptionEntity.class,
                entity -> entity != null && !entity.isDead && entity.hasContraptionData())) {
            BearingContraptionEntity.ContraptionHit hit = structure.rayTrace(eyes, end, mc.getRenderPartialTicks());
            if (hit != null) closest = Math.min(closest, eyes.squareDistanceTo(hit.worldHit()));
        }
        Entity nearest = null;
        AxisAlignedBB area = player.getEntityBoundingBox().expand(look.x * reach, look.y * reach, look.z * reach).grow(1D);
        for (Entity entity : mc.world.getEntitiesWithinAABBExcludingEntity(player, area)) {
            if (entity instanceof BearingContraptionEntity
                    || entity instanceof net.minecraft.entity.player.EntityPlayer other && other.isSpectator()
                    || !entity.canBeCollidedWith()) continue;
            AxisAlignedBB box = entity.getEntityBoundingBox().grow(entity.getCollisionBorderSize());
            RayTraceResult hit = box.calculateIntercept(eyes, end);
            Vec3d point = box.contains(eyes) ? eyes : hit == null ? null : hit.hitVec;
            if (point == null) continue;
            double distance = eyes.squareDistanceTo(point);
            if (distance < closest) { closest = distance; nearest = entity; }
        }
        // Vanilla already handles nearby entities. The extra packet avoids its
        // built-in distance limit for entities reached by the grip.
        if (nearest == null || closest <= 9D) return;
        ModNetwork.CHANNEL.sendToServer(new ExtendoGripEntityPacket(nearest.getEntityId(), attack));
        player.swingArm(EnumHand.MAIN_HAND);
        event.setCanceled(true);
    }
}
