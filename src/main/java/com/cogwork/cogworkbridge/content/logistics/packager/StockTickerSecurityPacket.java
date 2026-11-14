package com.cogwork.cogworkbridge.content.logistics.packager;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class StockTickerSecurityPacket implements IMessage {
    private int window;
    public StockTickerSecurityPacket() { }
    public StockTickerSecurityPacket(int window) { this.window = window; }
    @Override public void fromBytes(ByteBuf buf) { window = buf.readUnsignedByte(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeByte(window); }
    public static final class Handler implements IMessageHandler<StockTickerSecurityPacket, IMessage> {
        @Override public IMessage onMessage(StockTickerSecurityPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof StockTickerContainer container) || container.windowId != message.window
                        || !container.canInteractWith(player) || !(player.world.getTileEntity(container.position()) instanceof StockTickerTileEntity ticker)) return;
                if (LogisticsNetworkSecurity.toggle(player.world, ticker.getNetwork(), player)) {
                    var state = player.world.getBlockState(container.position());
                    player.world.notifyBlockUpdate(container.position(), state, state, 2);
                }
            });
            return null;
        }
    }
}
