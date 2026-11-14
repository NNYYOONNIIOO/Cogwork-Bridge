package com.cogwork.cogworkbridge.content.logistics.factory;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class FactoryPanelConfigPacket implements IMessage {
    private BlockPos pos; private int slot, count, output; private String address; private boolean crafting, clearPromises;
    public FactoryPanelConfigPacket() { }
    public FactoryPanelConfigPacket(BlockPos pos, int slot, int count, String address, int output, boolean crafting, boolean clearPromises) { this.pos = pos.toImmutable(); this.slot = slot; this.count = count; this.address = address; this.output = output; this.crafting = crafting; this.clearPromises = clearPromises; }
    @Override public void fromBytes(ByteBuf buf) { pos = BlockPos.fromLong(buf.readLong()); slot = buf.readUnsignedByte(); count = buf.readInt(); output = buf.readUnsignedByte(); address = ByteBufUtils.readUTF8String(buf); crafting = buf.readBoolean(); clearPromises = buf.readBoolean(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeLong(pos.toLong()); buf.writeByte(slot); buf.writeInt(count); buf.writeByte(output); ByteBufUtils.writeUTF8String(buf, address); buf.writeBoolean(crafting); buf.writeBoolean(clearPromises); }
    public static final class Handler implements IMessageHandler<FactoryPanelConfigPacket, IMessage> {
        @Override public IMessage onMessage(FactoryPanelConfigPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.slot > 3 || message.count < 0 || message.count > 4096 || message.output < 1 || message.output > 64 || message.address.length() > 25
                        || !player.world.isBlockLoaded(message.pos) || player.getDistanceSq(message.pos) > 64
                        || !player.canPlayerEdit(message.pos, EnumFacing.UP, player.getHeldItemMainhand())
                        || !(player.world.getTileEntity(message.pos) instanceof FactoryPanelTileEntity panel)) return;
                if (panel.network(message.slot) == null || !com.cogwork.cogworkbridge.content.logistics.packager.LogisticsNetworkSecurity.mayInteract(player.world, panel.network(message.slot), player)) return;
                panel.configure(message.slot, message.count, message.address, message.output, message.crafting);
                if (message.clearPromises) panel.clearPromises(message.slot);
            }); return null;
        }
    }
}
