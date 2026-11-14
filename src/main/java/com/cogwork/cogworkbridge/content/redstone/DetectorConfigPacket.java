package com.cogwork.cogworkbridge.content.redstone;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class DetectorConfigPacket implements IMessage {
    private BlockPos pos; private int on, off; private boolean inverted, stacks;
    public DetectorConfigPacket() { }
    public DetectorConfigPacket(BlockPos pos, int on, int off, boolean inverted, boolean stacks) { this.pos = pos.toImmutable(); this.on = on; this.off = off; this.inverted = inverted; this.stacks = stacks; }
    @Override public void fromBytes(ByteBuf b) { pos = BlockPos.fromLong(b.readLong()); on = b.readInt(); off = b.readInt(); inverted = b.readBoolean(); stacks = b.readBoolean(); }
    @Override public void toBytes(ByteBuf b) { b.writeLong(pos.toLong()); b.writeInt(on); b.writeInt(off); b.writeBoolean(inverted); b.writeBoolean(stacks); }
    public static final class Handler implements IMessageHandler<DetectorConfigPacket, IMessage> {
        @Override public IMessage onMessage(DetectorConfigPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!player.world.isBlockLoaded(message.pos) || player.getDistanceSq(message.pos) > 64 || !player.canPlayerEdit(message.pos, EnumFacing.UP, player.getHeldItemMainhand())
                        || !(player.world.getBlockState(message.pos).getBlock() instanceof DetectorBlock block) || !block.isThreshold()
                        || !(player.world.getTileEntity(message.pos) instanceof DetectorTileEntity tile)) return;
                tile.configure(message.on, message.off, message.inverted, message.stacks);
            }); return null;
        }
    }
}
