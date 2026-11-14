package com.cogwork.cogworkbridge.content.contraptions.bearing;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client confirmation that PlayerControllerMP completed one block. */
public final class ContraptionBreakPacket implements IMessage {

    private int entityId;
    private BlockPos localPos = BlockPos.ORIGIN;
    private EnumFacing side = EnumFacing.UP;
    private float interactionAngle;
    private float clientHitX;
    private float clientHitY;
    private float clientHitZ;

    public ContraptionBreakPacket() {
    }

    public ContraptionBreakPacket(int entityId, BlockPos localPos,
                                  EnumFacing side) {
        this.entityId = entityId;
        this.localPos = localPos;
        this.side = side == null ? EnumFacing.UP : side;
        this.interactionAngle = 0.0F;
        this.clientHitX = 0.0F;
        this.clientHitY = 0.0F;
        this.clientHitZ = 0.0F;
    }

    public ContraptionBreakPacket(int entityId, BlockPos localPos,
                                  EnumFacing side,
                                  float interactionAngle, Vec3d clientHit) {
        this(entityId, localPos, side);
        this.interactionAngle = interactionAngle;
        if (clientHit != null) {
            this.clientHitX = (float) clientHit.x;
            this.clientHitY = (float) clientHit.y;
            this.clientHitZ = (float) clientHit.z;
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        entityId = buffer.readInt();
        localPos = BlockPos.fromLong(buffer.readLong());
        side = EnumFacing.byIndex(buffer.readByte());
        if (side == null) {
            side = EnumFacing.UP;
        }
        interactionAngle = buffer.readFloat();
        clientHitX = buffer.readFloat();
        clientHitY = buffer.readFloat();
        clientHitZ = buffer.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeLong(localPos.toLong());
        buffer.writeByte(side.getIndex());
        buffer.writeFloat(interactionAngle);
        buffer.writeFloat(clientHitX);
        buffer.writeFloat(clientHitY);
        buffer.writeFloat(clientHitZ);
    }

    private Vec3d clientHit() {
        if (!Float.isFinite(clientHitX) || !Float.isFinite(clientHitY)
                || !Float.isFinite(clientHitZ)
                || (clientHitX == 0.0F && clientHitY == 0.0F
                && clientHitZ == 0.0F)) {
            return null;
        }
        return new Vec3d(clientHitX, clientHitY, clientHitZ);
    }

    public static final class Handler implements IMessageHandler<ContraptionBreakPacket, IMessage> {
        @Override
        public IMessage onMessage(ContraptionBreakPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.capabilities.isCreativeMode) {
                    BearingContraptionInteractionHandler.handleBreak(
                            player, message.entityId, message.localPos, message.side,
                            message.interactionAngle, message.clientHit());
                }
            });
            return null;
        }
    }
}
