package com.cogwork.cogworkbridge.content.logistics.packager;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.items.ItemHandlerHelper;

public final class StockTickerCategoryPacket implements IMessage {
    private int window, index;
    public StockTickerCategoryPacket() { }
    public StockTickerCategoryPacket(int window, int index) { this.window = window; this.index = index; }
    @Override public void fromBytes(ByteBuf buf) { window = buf.readUnsignedByte(); index = buf.readByte(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeByte(window); buf.writeByte(index); }
    public static final class Handler implements IMessageHandler<StockTickerCategoryPacket, IMessage> {
        @Override public IMessage onMessage(StockTickerCategoryPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof StockTickerContainer container) || container.windowId != message.window
                        || !container.canInteractWith(player) || !(player.world.getTileEntity(container.position()) instanceof StockTickerTileEntity ticker)) return;
                if (message.index < -1) { ticker.toggleCategory(player.getUniqueID(), -2 - message.index); return; }
                if (!LogisticsNetworkSecurity.mayAdminister(player.world, ticker.getNetwork(), player)) return;
                if (message.index == -1) {
                    ItemStack held = player.getHeldItemMainhand();
                    if (ticker.addCategory(held) && !player.capabilities.isCreativeMode) held.shrink(1);
                } else {
                    ItemStack removed = ticker.removeCategory(message.index);
                    if (!removed.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, removed);
                }
                player.inventory.markDirty();
            });
            return null;
        }
    }
}
