package com.cogwork.cogworkbridge.content.logistics.port;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class PackagePortConfigPacket implements IMessage {
    private int window;
    private String address;
    private boolean accepts;
    public PackagePortConfigPacket() { }
    public PackagePortConfigPacket(int window, String address, boolean accepts) { this.window = window; this.address = address; this.accepts = accepts; }
    @Override public void fromBytes(ByteBuf buf) { window = buf.readUnsignedByte(); address = ByteBufUtils.readUTF8String(buf); accepts = buf.readBoolean(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeByte(window); ByteBufUtils.writeUTF8String(buf, address); buf.writeBoolean(accepts); }
    public static final class Handler implements IMessageHandler<PackagePortConfigPacket, IMessage> {
        @Override public IMessage onMessage(PackagePortConfigPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.address.length() <= 25 && player.openContainer instanceof PackagePortContainer container
                        && container.windowId == message.window && container.canInteractWith(player))
                    container.port().configure(message.address, message.accepts);
            });
            return null;
        }
    }
}
