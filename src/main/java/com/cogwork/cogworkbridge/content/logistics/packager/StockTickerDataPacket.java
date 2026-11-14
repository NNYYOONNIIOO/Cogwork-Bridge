package com.cogwork.cogworkbridge.content.logistics.packager;

import com.cogwork.cogworkbridge.CogworkBridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

public final class StockTickerDataPacket implements IMessage {
    public static final int PAGE_SIZE = 32;
    private int window;
    private boolean first, last;
    private final List<ItemStack> items = new ArrayList<>();
    private final List<Integer> counts = new ArrayList<>();
    public StockTickerDataPacket() { }
    public StockTickerDataPacket(int window, List<StockLinkNetwork.StockEntry> stock, int offset) {
        this.window = window; first = offset == 0; last = offset + PAGE_SIZE >= stock.size();
        for (int i = offset; i < Math.min(stock.size(), offset + PAGE_SIZE); i++) {
            StockLinkNetwork.StockEntry entry = stock.get(i);
            items.add(entry.item.copy()); counts.add(entry.count);
        }
    }
    @Override public void fromBytes(ByteBuf buf) {
        window = buf.readUnsignedByte(); first = buf.readBoolean(); last = buf.readBoolean(); int count = buf.readUnsignedByte();
        if (count > PAGE_SIZE) throw new IllegalArgumentException("Invalid stock page length");
        for (int i = 0; i < count; i++) { items.add(ByteBufUtils.readItemStack(buf)); counts.add(buf.readInt()); }
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeByte(window); buf.writeBoolean(first); buf.writeBoolean(last); buf.writeByte(items.size());
        for (int i = 0; i < items.size(); i++) { ByteBufUtils.writeItemStack(buf, items.get(i)); buf.writeInt(counts.get(i)); }
    }
    public static final class Handler implements IMessageHandler<StockTickerDataPacket, IMessage> {
        @Override public IMessage onMessage(StockTickerDataPacket message, MessageContext context) {
            CogworkBridge.proxy.updateStockTicker(message.window, message.first, message.last, message.items, message.counts);
            return null;
        }
    }
}
