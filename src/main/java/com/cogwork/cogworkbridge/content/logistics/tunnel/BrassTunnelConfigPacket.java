package com.cogwork.cogworkbridge.content.logistics.tunnel;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class BrassTunnelConfigPacket implements IMessage {
    private BlockPos pos;
    private int mode;
    public BrassTunnelConfigPacket() { }
    public BrassTunnelConfigPacket(BlockPos pos, int mode) { this.pos = pos.toImmutable(); this.mode = mode; }
    @Override public void fromBytes(ByteBuf buffer) { pos = BlockPos.fromLong(buffer.readLong()); mode = buffer.readUnsignedByte(); }
    @Override public void toBytes(ByteBuf buffer) { buffer.writeLong(pos.toLong()); buffer.writeByte(mode); }
    public static final class Handler implements IMessageHandler<BrassTunnelConfigPacket, IMessage> {
        @Override public IMessage onMessage(BrassTunnelConfigPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.mode > 6 || player.getDistanceSq(message.pos) > 64 || !player.world.isBlockLoaded(message.pos)
                        || !(player.world.getTileEntity(message.pos) instanceof BrassTunnelTileEntity tunnel)) return;
                tunnel.setMode(message.mode);
            });
            return null;
        }
    }
}
