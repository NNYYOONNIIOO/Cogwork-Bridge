package com.cogwork.cogworkbridge.content.redstone;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class PulseDiodeConfigPacket implements IMessage {
    private BlockPos pos;
    private int value;
    public PulseDiodeConfigPacket() { }
    public PulseDiodeConfigPacket(BlockPos pos, int value) { this.pos = pos.toImmutable(); this.value = value; }
    @Override public void fromBytes(ByteBuf buffer) { pos = BlockPos.fromLong(buffer.readLong()); value = buffer.readInt(); }
    @Override public void toBytes(ByteBuf buffer) { buffer.writeLong(pos.toLong()); buffer.writeInt(value); }
    public static final class Handler implements IMessageHandler<PulseDiodeConfigPacket, IMessage> {
        @Override public IMessage onMessage(PulseDiodeConfigPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!player.world.isBlockLoaded(message.pos) || player.getDistanceSq(message.pos) > 64
                        || !player.canPlayerEdit(message.pos, EnumFacing.UP, player.getHeldItemMainhand())
                        || !(player.world.getTileEntity(message.pos) instanceof PulseDiodeTileEntity diode)) return;
                if (message.value == 0) diode.toggleInverted();
                else diode.setDelay(message.value);
            });
            return null;
        }
    }
}
