package com.cogwork.cogworkbridge.content.logistics.packager;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import java.util.ArrayList;
import java.util.List;

public final class StockTickerOrderPacket implements IMessage {
    private int window;
    private String address = "";
    private List<StockOrder.Line> order = new ArrayList<>();
    private List<StockOrder.Craft> crafts = new ArrayList<>();
    public StockTickerOrderPacket() { }
    public StockTickerOrderPacket(int window, List<StockOrder.Line> order, String address) {
        this.window = window; this.order = StockOrder.normalize(order); this.address = address;
    }
    public StockTickerOrderPacket(int window, List<StockOrder.Line> order, String address, List<StockOrder.Craft> crafts) {
        this(window, order, address); this.crafts = StockOrder.readCrafts(StockOrder.writeCrafts(crafts));
    }
    @Override public void fromBytes(ByteBuf buf) {
        window = buf.readUnsignedByte(); int count = buf.readUnsignedByte();
        if (count > 45) return;
        for (int i = 0; i < count; i++) order.add(new StockOrder.Line(ByteBufUtils.readItemStack(buf), buf.readInt()));
        address = ByteBufUtils.readUTF8String(buf);
        NBTTagCompound data = ByteBufUtils.readTag(buf);
        if (data != null) crafts = StockOrder.readCrafts(data.getTagList("Crafts", 10));
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeByte(window); buf.writeByte(order.size());
        for (StockOrder.Line line : order) { ByteBufUtils.writeItemStack(buf, line.item()); buf.writeInt(line.count()); }
        ByteBufUtils.writeUTF8String(buf, address);
        NBTTagCompound data = new NBTTagCompound(); data.setTag("Crafts", StockOrder.writeCrafts(crafts)); ByteBufUtils.writeTag(buf, data);
    }
    public static final class Handler implements IMessageHandler<StockTickerOrderPacket, IMessage> {
        @Override public IMessage onMessage(StockTickerOrderPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof StockTickerContainer container) || container.windowId != message.window
                        || !container.canInteractWith(player) || message.address.length() > 25 || message.order.isEmpty()) return;
                for (StockOrder.Line line : message.order)
                    if (line.item().isEmpty() || line.count() <= 0 || line.count() > 4096) return;
                List<StockOrder.Line> lines = StockOrder.normalize(message.order);
                TileEntity target = player.world.getTileEntity(container.position());
                ItemStack held = player.getHeldItemMainhand();
                if (target instanceof RedstoneRequesterTileEntity || target instanceof StockTickerTileEntity && held.getItem() instanceof RedstoneRequesterItem) {
                    int neededSlots = 0;
                    for (StockOrder.Line line : lines) neededSlots += (line.count() + 255) / 256;
                    if (neededSlots > 9) {
                        player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.requester.too_many_items"), true);
                        return;
                    }
                }
                if (!StockOrder.matchesCrafts(lines, message.crafts)) message.crafts.clear();
                if (target instanceof RedstoneRequesterTileEntity requester) { requester.configure(lines, message.address); requester.setCrafts(message.crafts); return; }
                if (!(target instanceof StockTickerTileEntity ticker)) return;
                ticker.setPreviousAddress(message.address);
                if (held.getItem() instanceof RedstoneRequesterItem
                        || net.minecraft.block.Block.getBlockFromItem(held.getItem()) instanceof com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock) {
                    NBTTagCompound tag = held.hasTagCompound() ? held.getTagCompound() : new NBTTagCompound();
                    tag.setUniqueId("LogisticsFrequency", ticker.getNetwork());
                    NBTTagCompound request = new NBTTagCompound(); request.setTag("Items", StockOrder.write(lines));
                    request.setString("Address", message.address); request.setLong("Ticker", ticker.getPos().toLong());
                    request.setTag("Crafts", StockOrder.writeCrafts(message.crafts));
                    request.setUniqueId("Network", ticker.getNetwork());
                    request.setInteger("Dimension", player.world.provider.getDimension()); tag.setTag("AutoRequest", request);
                    held.setTagCompound(tag); player.inventory.markDirty(); return;
                }
                int moved = StockLinkNetwork.requestEncodedOrder(player.getServer(), ticker.getNetwork(), lines, message.address, false, message.crafts);
                if (!lines.isEmpty()) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.award(player, "stock_ticker");
                player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.stock_ticker.requested", moved), true);
            });
            return null;
        }
    }
}
