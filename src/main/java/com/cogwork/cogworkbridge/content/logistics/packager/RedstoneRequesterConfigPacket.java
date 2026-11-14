package com.cogwork.cogworkbridge.content.logistics.packager;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class RedstoneRequesterConfigPacket implements IMessage {
    private int window;
    private String address = "";
    private boolean partial;
    private final int[] amounts = new int[9];
    public RedstoneRequesterConfigPacket() { }
    public RedstoneRequesterConfigPacket(int window, String address, boolean partial, RedstoneRequesterContainer container) {
        this.window = window; this.address = address; this.partial = partial;
        for (int i = 0; i < 9; i++) amounts[i] = container.amount(i);
    }
    @Override public void fromBytes(ByteBuf buf) {
        window = buf.readUnsignedByte(); address = ByteBufUtils.readUTF8String(buf); partial = buf.readBoolean();
        for (int i = 0; i < 9; i++) amounts[i] = buf.readUnsignedShort();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeByte(window); ByteBufUtils.writeUTF8String(buf, address); buf.writeBoolean(partial);
        for (int amount : amounts) buf.writeShort(amount);
    }
    public static final class Handler implements IMessageHandler<RedstoneRequesterConfigPacket, IMessage> {
        @Override public IMessage onMessage(RedstoneRequesterConfigPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.address.length() > 25 || !(player.openContainer instanceof RedstoneRequesterContainer container)
                        || container.windowId != message.window || !container.canInteractWith(player)) return;
                for (int amount : message.amounts) if (amount < 1 || amount > 256) return;
                container.applyConfig(message.address, message.partial, message.amounts);
            });
            return null;
        }
    }
}
