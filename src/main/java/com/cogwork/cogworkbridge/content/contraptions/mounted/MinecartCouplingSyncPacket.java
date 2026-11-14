package com.cogwork.cogworkbridge.content.contraptions.mounted;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.util.UUID;

/** A single persistent link is mirrored when created, removed or newly tracked. */
public final class MinecartCouplingSyncPacket implements IMessage {
    private UUID first = new UUID(0, 0), second = new UUID(0, 0);
    private boolean present;
    private int dimension;
    public MinecartCouplingSyncPacket() { }
    public MinecartCouplingSyncPacket(UUID first, UUID second, boolean present, int dimension) {
        this.first = first; this.second = second; this.present = present; this.dimension = dimension;
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(first.getMostSignificantBits()); buf.writeLong(first.getLeastSignificantBits());
        buf.writeLong(second.getMostSignificantBits()); buf.writeLong(second.getLeastSignificantBits());
        buf.writeBoolean(present); buf.writeInt(dimension);
    }
    @Override public void fromBytes(ByteBuf buf) {
        first = new UUID(buf.readLong(), buf.readLong()); second = new UUID(buf.readLong(), buf.readLong());
        present = buf.readBoolean(); dimension = buf.readInt();
    }
    public static final class Handler implements IMessageHandler<MinecartCouplingSyncPacket, IMessage> {
        @Override public IMessage onMessage(MinecartCouplingSyncPacket msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.world != null && mc.world.provider.getDimension() == msg.dimension)
                    com.cogwork.cogworkbridge.client.MinecartCouplingClient.update(msg.first, msg.second, msg.present);
            });
            return null;
        }
    }
}
