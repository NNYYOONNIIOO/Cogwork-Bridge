package com.cogwork.cogworkbridge.content.equipment;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class BlueprintEditPacket implements IMessage {
    private int window, slot;
    private ItemStack stack = ItemStack.EMPTY;
    public BlueprintEditPacket() { }
    public BlueprintEditPacket(int window, int slot, ItemStack stack) { this.window = window; this.slot = slot; this.stack = stack.copy(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(window); buf.writeByte(slot); ByteBufUtils.writeItemStack(buf, stack); }
    @Override public void fromBytes(ByteBuf buf) { window = buf.readInt(); slot = buf.readByte(); stack = ByteBufUtils.readItemStack(buf); }
    public static final class Handler implements IMessageHandler<BlueprintEditPacket, IMessage> {
        @Override public IMessage onMessage(BlueprintEditPacket packet, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof BlueprintContainer container) || container.windowId != packet.window || !container.canInteractWith(player)) return;
                if (packet.slot == -1) container.clearGhost();
                else if (packet.slot >= 0 && packet.slot < 11) container.setGhost(packet.slot, packet.stack);
                container.detectAndSendChanges();
            });
            return null;
        }
    }
}
