package com.cogwork.cogworkbridge.content.fluids;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server-authoritative synchronization for a list filter item dropped from JEI. */
public final class ListFilterJeiPacket implements IMessage {
    private int windowId;
    private int slot;
    private NBTTagCompound stackTag;

    public ListFilterJeiPacket() {
    }

    public ListFilterJeiPacket(int windowId, int slot, ItemStack stack) {
        this.windowId = windowId;
        this.slot = slot;
        this.stackTag = stack == null || stack.isEmpty()
                ? new NBTTagCompound()
                : stack.writeToNBT(new NBTTagCompound());
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        windowId = buffer.readUnsignedByte();
        slot = buffer.readUnsignedByte();
        stackTag = ByteBufUtils.readTag(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(windowId);
        buffer.writeByte(slot);
        ByteBufUtils.writeTag(buffer, stackTag == null ? new NBTTagCompound() : stackTag);
    }

    public static final class Handler implements IMessageHandler<ListFilterJeiPacket, IMessage> {
        @Override
        public IMessage onMessage(ListFilterJeiPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof ListFilterContainer filter)
                        || filter.windowId != message.windowId
                        || !filter.canInteractWith(player)
                        || message.slot < 0
                        || message.slot >= ListFilterItem.SLOT_COUNT) {
                    return;
                }
                ItemStack stack = message.stackTag == null || message.stackTag.isEmpty()
                        ? ItemStack.EMPTY
                        : new ItemStack(message.stackTag);
                if (!stack.isEmpty()) stack.setCount(1);
                filter.applyGhostEntry(message.slot, stack);
            });
            return null;
        }
    }
}
