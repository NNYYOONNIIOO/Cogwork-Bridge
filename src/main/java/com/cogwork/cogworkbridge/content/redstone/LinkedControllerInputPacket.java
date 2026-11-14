package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.registry.AllItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class LinkedControllerInputPacket implements IMessage {
    private int mask;
    private boolean offhand, lectern, stop;
    private net.minecraft.util.math.BlockPos pos;
    public LinkedControllerInputPacket() { }
    public LinkedControllerInputPacket(EnumHand hand, int mask) { offhand = hand == EnumHand.OFF_HAND; this.mask = mask & 63; }
    public LinkedControllerInputPacket(net.minecraft.util.math.BlockPos pos,int mask,boolean stop) { this.lectern=true;this.pos=pos;this.mask=mask&63;this.stop=stop; }
    @Override public void fromBytes(ByteBuf buf) { offhand=buf.readBoolean();mask=buf.readUnsignedByte()&63;lectern=buf.readBoolean();if(lectern){pos=net.minecraft.util.math.BlockPos.fromLong(buf.readLong());stop=buf.readBoolean();} }
    @Override public void toBytes(ByteBuf buf) { buf.writeBoolean(offhand);buf.writeByte(mask);buf.writeBoolean(lectern);if(lectern){buf.writeLong(pos.toLong());buf.writeBoolean(stop);} }
    public static final class Handler implements IMessageHandler<LinkedControllerInputPacket, IMessage> {
        @Override public IMessage onMessage(LinkedControllerInputPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                ItemStack held;
                if (message.lectern) {
                    if (!player.world.isBlockLoaded(message.pos) || !(player.world.getTileEntity(message.pos) instanceof LecternControllerTileEntity tile) || !tile.usedBy(player) || !tile.inRange(player)) return;
                    if (message.stop) { tile.stop(player); return; }
                    held=tile.controller();
                } else held=player.getHeldItem(message.offhand ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND);
                if (message.mask != 0 && (held.getItem() != AllItems.LINKED_CONTROLLER || player.isSpectator())) return;
                RedstoneLinkNetwork.Key[] keys = new RedstoneLinkNetwork.Key[6];
                for (int i = 0; i < keys.length; i++) keys[i] = RedstoneLinkNetwork.key(
                        LinkedControllerItem.frequency(held, i * 2), LinkedControllerItem.frequency(held, i * 2 + 1));
                RedstoneLinkNetwork.updateRemote(player, keys, message.mask);
            });
            return null;
        }
    }
}
