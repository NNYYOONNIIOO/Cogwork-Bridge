package com.cogwork.cogworkbridge.content.contraptions.bearing;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** A held mining target, or cancellation of that target. */
public final class ContraptionMiningPacket implements IMessage {

    private int sequence;
    private int entityId;
    private BlockPos localPos = BlockPos.ORIGIN;
    private EnumFacing side = EnumFacing.UP;
    private float angle;
    private double hitX;
    private double hitY;
    private double hitZ;
    private boolean cancel;

    public ContraptionMiningPacket() {
    }

    public ContraptionMiningPacket(int sequence, int entityId, BlockPos localPos,
                                   EnumFacing side, float angle, Vec3d hit,
                                   boolean cancel) {
        this.sequence = sequence;
        this.entityId = entityId;
        this.localPos = localPos.toImmutable();
        this.side = side;
        this.angle = angle;
        this.hitX = hit.x;
        this.hitY = hit.y;
        this.hitZ = hit.z;
        this.cancel = cancel;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        sequence = buffer.readInt();
        entityId = buffer.readInt();
        localPos = BlockPos.fromLong(buffer.readLong());
        side = EnumFacing.byIndex(buffer.readByte());
        angle = buffer.readFloat();
        hitX = buffer.readDouble();
        hitY = buffer.readDouble();
        hitZ = buffer.readDouble();
        cancel = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(sequence);
        buffer.writeInt(entityId);
        buffer.writeLong(localPos.toLong());
        buffer.writeByte(side.getIndex());
        buffer.writeFloat(angle);
        buffer.writeDouble(hitX);
        buffer.writeDouble(hitY);
        buffer.writeDouble(hitZ);
        buffer.writeBoolean(cancel);
    }

    public static final class Handler
            implements IMessageHandler<ContraptionMiningPacket, IMessage> {
        @Override
        public IMessage onMessage(ContraptionMiningPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() ->
                    ContraptionMiningServer.handle(player, message.sequence,
                            message.entityId, message.localPos, message.side,
                            message.angle, new Vec3d(message.hitX, message.hitY, message.hitZ),
                            message.cancel));
            return null;
        }
    }
}
