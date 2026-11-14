package com.cogwork.cogworkbridge.content.logistics.funnel;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class BrassFunnelConfigPacket implements IMessage {
    private BlockPos pos;
    private int amount;
    private boolean exact;
    public BrassFunnelConfigPacket() { }
    public BrassFunnelConfigPacket(BlockPos pos, int amount, boolean exact) {
        this.pos = pos.toImmutable(); this.amount = amount; this.exact = exact;
    }
    @Override public void fromBytes(ByteBuf buffer) {
        pos = BlockPos.fromLong(buffer.readLong()); amount = buffer.readUnsignedByte(); exact = buffer.readBoolean();
    }
    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeLong(pos.toLong()); buffer.writeByte(amount); buffer.writeBoolean(exact);
    }
    public static final class Handler implements IMessageHandler<BrassFunnelConfigPacket, IMessage> {
        @Override public IMessage onMessage(BrassFunnelConfigPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.getDistanceSq(message.pos) > 64 || !player.world.isBlockLoaded(message.pos)
                        || !(player.world.getTileEntity(message.pos) instanceof BrassFunnelTileEntity funnel)) return;
                funnel.setAmount(message.amount, message.exact);
            });
            return null;
        }
    }
}
