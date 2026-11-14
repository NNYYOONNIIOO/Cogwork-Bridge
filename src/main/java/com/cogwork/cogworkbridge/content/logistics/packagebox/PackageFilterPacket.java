package com.cogwork.cogworkbridge.content.logistics.packagebox;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class PackageFilterPacket implements IMessage {
    private int window; private String address;
    public PackageFilterPacket() { }
    public PackageFilterPacket(int window, String address) { this.window = window; this.address = address; }
    @Override public void fromBytes(ByteBuf buf) { window = buf.readUnsignedByte(); address = ByteBufUtils.readUTF8String(buf); }
    @Override public void toBytes(ByteBuf buf) { buf.writeByte(window); ByteBufUtils.writeUTF8String(buf, address); }
    public static final class Handler implements IMessageHandler<PackageFilterPacket, IMessage> {
        @Override public IMessage onMessage(PackageFilterPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.address.length() > 25 || !(player.openContainer instanceof PackageFilterContainer filter)
                        || filter.windowId != message.window || !filter.canInteractWith(player)) return;
                filter.setAddress(message.address);
            });
            return null;
        }
    }
}
