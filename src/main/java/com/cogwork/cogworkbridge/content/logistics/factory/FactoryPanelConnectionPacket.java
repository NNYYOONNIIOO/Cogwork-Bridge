package com.cogwork.cogworkbridge.content.logistics.factory;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class FactoryPanelConnectionPacket implements IMessage {
    private BlockPos destination, source;
    private int destinationSlot, sourceSlot, amount;

    public FactoryPanelConnectionPacket() { }
    public FactoryPanelConnectionPacket(BlockPos destination, int destinationSlot, BlockPos source, int sourceSlot, int amount) {
        this.destination = destination.toImmutable(); this.destinationSlot = destinationSlot;
        this.source = source.toImmutable(); this.sourceSlot = sourceSlot; this.amount = amount;
    }
    @Override public void fromBytes(ByteBuf buf) {
        destination = BlockPos.fromLong(buf.readLong()); destinationSlot = buf.readUnsignedByte();
        source = BlockPos.fromLong(buf.readLong()); sourceSlot = buf.readUnsignedByte(); amount = buf.readUnsignedByte();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(destination.toLong()); buf.writeByte(destinationSlot);
        buf.writeLong(source.toLong()); buf.writeByte(sourceSlot); buf.writeByte(amount);
    }
    public static final class Handler implements IMessageHandler<FactoryPanelConnectionPacket, IMessage> {
        @Override public IMessage onMessage(FactoryPanelConnectionPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.destinationSlot > 3 || message.sourceSlot > 3 || message.amount > 64
                        || !player.world.isBlockLoaded(message.destination) || player.getDistanceSq(message.destination) > 256
                        || !player.canPlayerEdit(message.destination, EnumFacing.UP, player.getHeldItemMainhand())
                        || !(player.world.getTileEntity(message.destination) instanceof FactoryPanelTileEntity panel)) return;
                if (panel.network(message.destinationSlot) == null || !com.cogwork.cogworkbridge.content.logistics.packager.LogisticsNetworkSecurity.mayInteract(player.world, panel.network(message.destinationSlot), player)) return;
                if (message.amount == 0) { panel.clearConnections(message.destinationSlot); return; }
                if (!player.world.isBlockLoaded(message.source) || player.getDistanceSq(message.source) > 64
                        || !player.canPlayerEdit(message.source, EnumFacing.UP, player.getHeldItemMainhand())) return;
                if (player.world.getTileEntity(message.source) instanceof com.cogwork.cogworkbridge.content.display.DisplayLinkTileEntity link) {
                    link.linkFactoryPanel(message.destination, message.destinationSlot, message.sourceSlot == 1);
                    return;
                }
                if (!(player.world.getTileEntity(message.source) instanceof FactoryPanelTileEntity sourcePanel)
                        || sourcePanel.network(message.sourceSlot) == null || !com.cogwork.cogworkbridge.content.logistics.packager.LogisticsNetworkSecurity.mayInteract(player.world, sourcePanel.network(message.sourceSlot), player)) return;
                panel.connect(message.destinationSlot, message.source, message.sourceSlot, message.amount);
            });
            return null;
        }
    }
}
