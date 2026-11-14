package com.cogwork.cogworkbridge.content.equipment;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class SymmetryWandConfigPacket implements IMessage {
    private int hand, slot, mode, alignment;
    public SymmetryWandConfigPacket() { }
    public SymmetryWandConfigPacket(EnumHand hand, int slot, int mode, int alignment) {
        this.hand = hand.ordinal(); this.slot = slot; this.mode = mode; this.alignment = alignment;
    }
    @Override public void fromBytes(ByteBuf buf) { hand = buf.readByte(); slot = buf.readByte(); mode = buf.readByte(); alignment = buf.readByte(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeByte(hand); buf.writeByte(slot); buf.writeByte(mode); buf.writeByte(alignment); }
    public static final class Handler implements IMessageHandler<SymmetryWandConfigPacket, IMessage> {
        @Override public IMessage onMessage(SymmetryWandConfigPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.hand < 0 || message.hand >= EnumHand.values().length) return;
                if (message.mode < 0 || message.mode > 2 || message.alignment < 0 || message.alignment > 1) return;
                if (message.hand == EnumHand.MAIN_HAND.ordinal() && message.slot != player.inventory.currentItem) return;
                ItemStack stack = player.getHeldItem(EnumHand.values()[message.hand]);
                if (!(stack.getItem() instanceof SymmetryWandItem)) return;
                SymmetryWandItem.configure(stack, message.mode, message.alignment);
                player.inventory.markDirty();
            });
            return null;
        }
    }
}
