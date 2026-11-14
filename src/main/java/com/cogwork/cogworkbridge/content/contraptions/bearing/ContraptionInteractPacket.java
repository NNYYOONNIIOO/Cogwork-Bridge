package com.cogwork.cogworkbridge.content.contraptions.bearing;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client request to use one block carried by a contraption. */
public final class ContraptionInteractPacket implements IMessage {

    private int entityId;
    private BlockPos localPos = BlockPos.ORIGIN;
    private EnumFacing side = EnumFacing.UP;
    private EnumHand hand = EnumHand.MAIN_HAND;
    private boolean secondaryUse;
    private float interactionAngle;
    private float hitWorldX;
    private float hitWorldY;
    private float hitWorldZ;
    private float traceStartX;
    private float traceStartY;
    private float traceStartZ;
    private float traceEndX;
    private float traceEndY;
    private float traceEndZ;
    private float hitX = 0.5F;
    private float hitY = 0.5F;
    private float hitZ = 0.5F;

    public ContraptionInteractPacket() {
    }

    public ContraptionInteractPacket(int entityId, BlockPos localPos,
                                     EnumFacing side, EnumHand hand,
                                     boolean secondaryUse,
                                     float interactionAngle,
                                     net.minecraft.util.math.Vec3d worldHit,
                                     net.minecraft.util.math.Vec3d traceStart,
                                     net.minecraft.util.math.Vec3d traceEnd,
                                     float hitX, float hitY, float hitZ) {
        this.entityId = entityId;
        this.localPos = localPos;
        this.side = side == null ? EnumFacing.UP : side;
        this.hand = hand == null ? EnumHand.MAIN_HAND : hand;
        this.secondaryUse = secondaryUse;
        this.interactionAngle = interactionAngle;
        if (worldHit != null) {
            this.hitWorldX = (float) worldHit.x;
            this.hitWorldY = (float) worldHit.y;
            this.hitWorldZ = (float) worldHit.z;
        }
        if (traceStart != null) {
            this.traceStartX = (float) traceStart.x;
            this.traceStartY = (float) traceStart.y;
            this.traceStartZ = (float) traceStart.z;
        }
        if (traceEnd != null) {
            this.traceEndX = (float) traceEnd.x;
            this.traceEndY = (float) traceEnd.y;
            this.traceEndZ = (float) traceEnd.z;
        }
        this.hitX = clampHit(hitX);
        this.hitY = clampHit(hitY);
        this.hitZ = clampHit(hitZ);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        entityId = buffer.readInt();
        localPos = BlockPos.fromLong(buffer.readLong());
        side = EnumFacing.byIndex(buffer.readByte());
        if (side == null) {
            side = EnumFacing.UP;
        }
        hand = buffer.readBoolean() ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
        secondaryUse = buffer.readBoolean();
        interactionAngle = buffer.readFloat();
        hitWorldX = buffer.readFloat();
        hitWorldY = buffer.readFloat();
        hitWorldZ = buffer.readFloat();
        traceStartX = buffer.readFloat();
        traceStartY = buffer.readFloat();
        traceStartZ = buffer.readFloat();
        traceEndX = buffer.readFloat();
        traceEndY = buffer.readFloat();
        traceEndZ = buffer.readFloat();
        hitX = clampHit(buffer.readFloat());
        hitY = clampHit(buffer.readFloat());
        hitZ = clampHit(buffer.readFloat());
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeLong(localPos.toLong());
        buffer.writeByte(side.getIndex());
        buffer.writeBoolean(hand == EnumHand.OFF_HAND);
        buffer.writeBoolean(secondaryUse);
        buffer.writeFloat(interactionAngle);
        buffer.writeFloat(hitWorldX);
        buffer.writeFloat(hitWorldY);
        buffer.writeFloat(hitWorldZ);
        buffer.writeFloat(traceStartX);
        buffer.writeFloat(traceStartY);
        buffer.writeFloat(traceStartZ);
        buffer.writeFloat(traceEndX);
        buffer.writeFloat(traceEndY);
        buffer.writeFloat(traceEndZ);
        buffer.writeFloat(hitX);
        buffer.writeFloat(hitY);
        buffer.writeFloat(hitZ);
    }

    private static float clampHit(float value) {
        return Float.isFinite(value)
                ? Math.max(0.0F, Math.min(1.0F, value)) : 0.5F;
    }

    private net.minecraft.util.math.Vec3d worldHit() {
        return vec(hitWorldX, hitWorldY, hitWorldZ);
    }

    private net.minecraft.util.math.Vec3d traceEnd() {
        return vec(traceEndX, traceEndY, traceEndZ);
    }

    private net.minecraft.util.math.Vec3d traceStart() {
        return vec(traceStartX, traceStartY, traceStartZ);
    }

    private static net.minecraft.util.math.Vec3d vec(
            float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(z)
                || (x == 0.0F && y == 0.0F && z == 0.0F)) {
            return null;
        }
        return new net.minecraft.util.math.Vec3d(x, y, z);
    }

    public static final class Handler
            implements IMessageHandler<ContraptionInteractPacket, IMessage> {
        @Override
        public IMessage onMessage(ContraptionInteractPacket message,
                                  MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() ->
                    BearingContraptionInteractionHandler.handleInteract(
                            player, message.entityId, message.localPos,
                            message.side, message.hand,
                            message.interactionAngle, message.worldHit(),
                            message.traceStart(), message.traceEnd(),
                            message.hitX, message.hitY, message.hitZ,
                            message.secondaryUse));
            return null;
        }
    }
}
