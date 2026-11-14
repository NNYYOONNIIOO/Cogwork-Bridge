package com.cogwork.cogworkbridge.content.kinetics.chainconveyor;

import com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.AllItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class ChainConveyorInteractionPacket implements IMessage {
    private boolean offhand;
    public ChainConveyorInteractionPacket() { }
    public ChainConveyorInteractionPacket(EnumHand hand) { offhand = hand == EnumHand.OFF_HAND; }
    @Override public void fromBytes(ByteBuf buf) { offhand = buf.readBoolean(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeBoolean(offhand); }
    public static final class Handler implements IMessageHandler<ChainConveyorInteractionPacket, IMessage> {
        @Override public IMessage onMessage(ChainConveyorInteractionPacket packet, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.isSpectator()) return;
                EnumHand hand = packet.offhand ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
                ItemStack held = player.getHeldItem(hand);
                double reach = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue();
                if (held.isEmpty()) {
                    var parcel = ChainConveyorInteraction.traceParcel(player, Math.max(0, Math.min(32, reach)));
                    if (parcel != null) parcel.wheel().takeParcel(player, parcel.parcel());
                    return;
                }
                var hit = ChainConveyorInteraction.trace(player, Math.max(0, Math.min(32, reach)));
                if (hit == null || !player.world.isBlockModifiable(player, hit.wheel().getPos())) return;
                if (held.getItem() == AllItems.WRENCH && player.isSneaking()) {
                    hit.wheel().disconnect(hit.end(), player); return;
                }
                if (held.getItem() instanceof ItemBlock item && item.getBlock() == AllBlocks.PACKAGE_FROGPORT) {
                    NBTTagCompound selection = new NBTTagCompound(); selection.setLong("Pos", hit.wheel().getPos().toLong());
                    selection.setLong("End", hit.end().toLong()); selection.setFloat("Angle", hit.progress());
                    selection.setInteger("Dimension", player.world.provider.getDimension());
                    player.getEntityData().setTag(ChainConveyorBlock.PORT_SELECTION, selection);
                    player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.chain.frog_selected"), true); return;
                }
                if (PackageItem.isPackage(held) && hit.wheel().addTravelPackage(held, hit.end(), hit.progress()) && !player.capabilities.isCreativeMode)
                    held.shrink(1);
            });
            return null;
        }
    }
}
