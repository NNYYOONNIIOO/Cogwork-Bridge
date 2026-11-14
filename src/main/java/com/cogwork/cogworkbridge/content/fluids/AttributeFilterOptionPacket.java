package com.cogwork.cogworkbridge.content.fluids;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class AttributeFilterOptionPacket implements IMessage {
    private int window;
    private int option;
    private int index;
    public AttributeFilterOptionPacket() { }
    public AttributeFilterOptionPacket(int window, int option, int index) {
        this.window = window; this.option = option; this.index = index;
    }
    @Override public void fromBytes(ByteBuf buffer) {
        window = buffer.readUnsignedByte(); option = buffer.readUnsignedByte(); index = buffer.readUnsignedShort();
    }
    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeByte(window); buffer.writeByte(option); buffer.writeShort(index);
    }
    public static final class Handler implements IMessageHandler<AttributeFilterOptionPacket, IMessage> {
        @Override public IMessage onMessage(AttributeFilterOptionPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.option <= 5 && player.openContainer instanceof AttributeFilterContainer filter
                        && filter.windowId == message.window && filter.canInteractWith(player))
                    filter.applyOption(message.option, message.index);
            });
            return null;
        }
    }
}
