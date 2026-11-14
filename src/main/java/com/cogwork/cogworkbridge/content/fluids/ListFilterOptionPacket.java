package com.cogwork.cogworkbridge.content.fluids;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class ListFilterOptionPacket implements IMessage {
    private int windowId;
    private int option;

    public ListFilterOptionPacket() { }
    public ListFilterOptionPacket(int windowId, int option) { this.windowId = windowId; this.option = option; }

    @Override public void fromBytes(ByteBuf buffer) {
        windowId = buffer.readUnsignedByte();
        option = buffer.readUnsignedByte();
    }

    @Override public void toBytes(ByteBuf buffer) { buffer.writeByte(windowId); buffer.writeByte(option); }

    public static final class Handler implements IMessageHandler<ListFilterOptionPacket, IMessage> {
        @Override
        public IMessage onMessage(ListFilterOptionPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.option <= 4 && player.openContainer instanceof ListFilterContainer filter
                        && filter.windowId == message.windowId && filter.canInteractWith(player)) {
                    filter.applyOption(message.option);
                }
            });
            return null;
        }
    }
}
