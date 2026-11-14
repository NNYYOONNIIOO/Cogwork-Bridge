package com.cogwork.cogworkbridge.content.logistics.table;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class TableClothPricePacket implements IMessage {
    private BlockPos pos; private int count;
    public TableClothPricePacket() { }
    public TableClothPricePacket(BlockPos pos, int count) { this.pos=pos; this.count=count; }
    @Override public void fromBytes(ByteBuf b) { pos=BlockPos.fromLong(b.readLong()); count=b.readUnsignedByte(); }
    @Override public void toBytes(ByteBuf b) { b.writeLong(pos.toLong()); b.writeByte(count); }
    public static final class Handler implements IMessageHandler<TableClothPricePacket,IMessage> {
        @Override public IMessage onMessage(TableClothPricePacket m, MessageContext c) {
            var p=c.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                if (m.count<1 || m.count>100 || p.getDistanceSq(m.pos)>64 || !p.world.isBlockLoaded(m.pos)
                        || !p.canPlayerEdit(m.pos,net.minecraft.util.EnumFacing.UP,p.getHeldItemMainhand())) return;
                if (p.world.getTileEntity(m.pos) instanceof TableClothTileEntity cloth) cloth.setPriceCount(p,m.count);
            }); return null;
        }
    }
}
