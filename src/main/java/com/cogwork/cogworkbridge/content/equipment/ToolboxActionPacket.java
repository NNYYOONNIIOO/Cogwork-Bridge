package com.cogwork.cogworkbridge.content.equipment;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class ToolboxActionPacket implements IMessage {
    private int action, hotbar, compartment;
    private long pos;
    public ToolboxActionPacket() { }
    public ToolboxActionPacket(int action, BlockPos pos, int compartment, int hotbar) {
        this.action = action; this.pos = pos.toLong(); this.compartment = compartment; this.hotbar = hotbar;
    }
    @Override public void toBytes(ByteBuf buf) { buf.writeByte(action); buf.writeLong(pos); buf.writeByte(compartment); buf.writeByte(hotbar); }
    @Override public void fromBytes(ByteBuf buf) { action = buf.readByte(); pos = buf.readLong(); compartment = buf.readByte(); hotbar = buf.readByte(); }
    public static final class Handler implements IMessageHandler<ToolboxActionPacket, IMessage> {
        @Override public IMessage onMessage(ToolboxActionPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.hotbar < 0 || message.hotbar >= 9) return;
                if (message.action == 1) { ToolboxLinks.detach(player, message.hotbar, true); return; }
                BlockPos pos = BlockPos.fromLong(message.pos);
                if (!player.world.isBlockLoaded(pos) || player.getDistanceSq(pos) >= 100D
                        || !(player.world.getTileEntity(pos) instanceof ToolboxTileEntity box)) return;
                if (message.action == 0) ToolboxLinks.equip(player, box, message.compartment, message.hotbar);
                if (message.action == 2) ToolboxLinks.deposit(player, box);
            });
            return null;
        }
    }
}
