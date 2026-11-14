package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.client.ClientContraptionMiningProgress;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server progress for one player mining one carried block. */
public final class ContraptionMiningProgressPacket implements IMessage {

    public static final int CANCELLED = -1;
    public static final int COMPLETED = -2;

    private int playerId;
    private int sequence;
    private int entityId;
    private BlockPos localPos = BlockPos.ORIGIN;
    private int stage;

    public ContraptionMiningProgressPacket() {
    }

    public ContraptionMiningProgressPacket(int playerId, int sequence, int entityId,
                                           BlockPos localPos, int stage) {
        this.playerId = playerId;
        this.sequence = sequence;
        this.entityId = entityId;
        this.localPos = localPos.toImmutable();
        this.stage = stage;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        playerId = buffer.readInt();
        sequence = buffer.readInt();
        entityId = buffer.readInt();
        localPos = BlockPos.fromLong(buffer.readLong());
        stage = buffer.readByte();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(playerId);
        buffer.writeInt(sequence);
        buffer.writeInt(entityId);
        buffer.writeLong(localPos.toLong());
        buffer.writeByte(stage);
    }

    public static final class Handler
            implements IMessageHandler<ContraptionMiningProgressPacket, IMessage> {
        @Override
        public IMessage onMessage(ContraptionMiningProgressPacket message, MessageContext context) {
            Minecraft.getMinecraft().addScheduledTask(() ->
                    ClientContraptionMiningProgress.update(message.playerId, message.sequence,
                            message.entityId, message.localPos, message.stage));
            return null;
        }
    }
}
