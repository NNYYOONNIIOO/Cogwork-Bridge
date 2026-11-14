package com.cogwork.cogworkbridge.content.logistics.packager;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class StockTickerRequestPacket implements IMessage {
    private int window, amount;
    private ItemStack item;
    private String address;
    public StockTickerRequestPacket() { }
    public StockTickerRequestPacket(int window, ItemStack item, int amount, String address) {
        this.window = window; this.item = item.copy(); this.item.setCount(1); this.amount = amount; this.address = address;
    }
    @Override public void fromBytes(ByteBuf buf) { window = buf.readUnsignedByte(); amount = buf.readInt(); item = ByteBufUtils.readItemStack(buf); address = ByteBufUtils.readUTF8String(buf); }
    @Override public void toBytes(ByteBuf buf) { buf.writeByte(window); buf.writeInt(amount); ByteBufUtils.writeItemStack(buf, item); ByteBufUtils.writeUTF8String(buf, address); }
    public static final class Handler implements IMessageHandler<StockTickerRequestPacket, IMessage> {
        @Override public IMessage onMessage(StockTickerRequestPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof StockTickerContainer container) || container.windowId != message.window
                        || !container.canInteractWith(player) || message.item.isEmpty() || message.amount <= 0 || message.amount > 4096
                        || message.address.length() > 25) return;
                net.minecraft.tileentity.TileEntity target = player.world.getTileEntity(container.position());
                if (target instanceof RedstoneRequesterTileEntity requester) {
                    requester.configure(message.item, message.amount, message.address);
                    return;
                }
                if (!(target instanceof StockTickerTileEntity ticker)) return;
                ItemStack held = player.getHeldItemMainhand();
                if (held.getItem() instanceof RedstoneRequesterItem) {
                    net.minecraft.nbt.NBTTagCompound tag = held.hasTagCompound() ? held.getTagCompound() : new net.minecraft.nbt.NBTTagCompound();
                    tag.setUniqueId("LogisticsFrequency", ticker.getNetwork());
                    net.minecraft.nbt.NBTTagCompound order = new net.minecraft.nbt.NBTTagCompound();
                    order.setTag("Item", message.item.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
                    order.setInteger("Amount", message.amount); order.setString("Address", message.address);
                    tag.setTag("AutoRequest", order); held.setTagCompound(tag); player.inventory.markDirty();
                    return;
                }
                int moved = StockLinkNetwork.request(player.getServer(), ticker.getNetwork(), message.item, message.amount, message.address);
                player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("cogworkbridge.stock_ticker.requested", moved), true);
            });
            return null;
        }
    }
}
