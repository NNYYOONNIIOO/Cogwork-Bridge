package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server-authoritative extended attack and entity interaction. */
public final class ExtendoGripEntityPacket implements IMessage {
    private int target;
    private boolean attack;

    public ExtendoGripEntityPacket() { }
    public ExtendoGripEntityPacket(int target, boolean attack) { this.target = target; this.attack = attack; }
    @Override public void fromBytes(ByteBuf buf) { target = buf.readInt(); attack = buf.readBoolean(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(target); buf.writeBoolean(attack); }

    public static final class Handler implements IMessageHandler<ExtendoGripEntityPacket, IMessage> {
        @Override public IMessage onMessage(ExtendoGripEntityPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> interact(player, message));
            return null;
        }

        private void interact(EntityPlayerMP player, ExtendoGripEntityPacket message) {
            if (!ExtendoGripItem.isHolding(player) || player.isSpectator()) return;
            Entity target = player.world.getEntityByID(message.target);
            if (target == null || target == player || !target.isEntityAlive()
                    || target instanceof EntityPlayer other && other.isSpectator()
                    || target instanceof BearingContraptionEntity || !target.canBeCollidedWith()) return;
            if (message.attack && (target instanceof net.minecraft.entity.item.EntityItem
                    || target instanceof net.minecraft.entity.item.EntityXPOrb
                    || target instanceof net.minecraft.entity.projectile.EntityArrow)) return;
            var attribute = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE);
            if (attribute == null) return;
            double reach = attribute.getAttributeValue();
            Vec3d eyes = player.getPositionEyes(1F), end = eyes.add(player.getLook(1F).scale(reach));
            AxisAlignedBB box = target.getEntityBoundingBox().grow(target.getCollisionBorderSize());
            RayTraceResult entityHit = box.calculateIntercept(eyes, end);
            Vec3d hit = box.contains(eyes) ? eyes : entityHit == null ? null : entityHit.hitVec;
            if (hit == null) return;
            RayTraceResult blockHit = player.world.rayTraceBlocks(eyes, hit, false, true, false);
            if (blockHit != null && blockHit.typeOfHit == RayTraceResult.Type.BLOCK
                    && eyes.squareDistanceTo(blockHit.hitVec) + 1.0E-6D < eyes.squareDistanceTo(hit)) return;
            for (BearingContraptionEntity structure : player.world.getEntities(BearingContraptionEntity.class,
                    entity -> entity != null && !entity.isDead && entity.hasContraptionData())) {
                BearingContraptionEntity.ContraptionHit obstacle = structure.rayTrace(eyes, hit);
                if (obstacle != null && eyes.squareDistanceTo(obstacle.worldHit()) + 1.0E-6D < eyes.squareDistanceTo(hit)) return;
            }
            if (message.attack) {
                player.attackTargetEntityWithCurrentItem(target);
                player.swingArm(EnumHand.MAIN_HAND);
                return;
            }
            Vec3d local = hit.subtract(target.posX, target.posY, target.posZ);
            for (EnumHand hand : EnumHand.values()) {
                EnumActionResult result = target.applyPlayerInteraction(player, local, hand);
                if (result != EnumActionResult.SUCCESS) result = player.interactOn(target, hand);
                if (result == EnumActionResult.SUCCESS) { player.swingArm(hand); break; }
            }
        }
    }
}
