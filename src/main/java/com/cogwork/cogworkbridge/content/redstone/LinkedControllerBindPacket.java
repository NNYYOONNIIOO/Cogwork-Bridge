package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.registry.AllItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Copies the terminal's two frequency items into one controller key. */
public final class LinkedControllerBindPacket implements IMessage {
    private BlockPos pos = BlockPos.ORIGIN;
    private int key;
    private boolean offhand;

    public LinkedControllerBindPacket() { }
    public LinkedControllerBindPacket(BlockPos pos, int key, EnumHand hand) {
        this.pos = pos;
        this.key = key;
        offhand = hand == EnumHand.OFF_HAND;
    }
    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        key = buf.readUnsignedByte();
        offhand = buf.readBoolean();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeByte(key);
        buf.writeBoolean(offhand);
    }
    public static final class Handler implements IMessageHandler<LinkedControllerBindPacket, IMessage> {
        @Override public IMessage onMessage(LinkedControllerBindPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.key >= 6 || player.isSpectator() || !player.world.isBlockLoaded(message.pos)
                        || player.getDistanceSqToCenter(message.pos) > 64D
                        || !player.world.isBlockModifiable(player, message.pos)) return;
                EnumHand hand = message.offhand ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
                ItemStack controller = player.getHeldItem(hand);
                if (controller.getItem() != AllItems.LINKED_CONTROLLER
                        || !player.canPlayerEdit(message.pos, EnumFacing.UP, controller)
                        || !(player.world.getTileEntity(message.pos) instanceof RedstoneLinkTileEntity link)) return;
                LinkedControllerItem.setFrequency(controller, message.key * 2, link.getFrequency(0));
                LinkedControllerItem.setFrequency(controller, message.key * 2 + 1, link.getFrequency(1));
                player.inventory.markDirty();
            });
            return null;
        }
    }
}
