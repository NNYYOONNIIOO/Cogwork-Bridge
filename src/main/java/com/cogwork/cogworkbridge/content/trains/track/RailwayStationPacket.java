package com.cogwork.cogworkbridge.content.trains.track;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class RailwayStationPacket implements IMessage {
    private BlockPos pos=BlockPos.ORIGIN;
    private String name="";
    private boolean assembling;
    private boolean assemble;
    public RailwayStationPacket() { }
    public RailwayStationPacket(BlockPos pos,String name,boolean assembling){this.pos=pos;this.name=name;this.assembling=assembling;}
    public RailwayStationPacket(BlockPos pos,String name,boolean assembling,boolean assemble){this(pos,name,assembling);this.assemble=assemble;}
    @Override public void fromBytes(ByteBuf buf){pos=BlockPos.fromLong(buf.readLong());name=ByteBufUtils.readUTF8String(buf);assembling=buf.readBoolean();assemble=buf.readBoolean();}
    @Override public void toBytes(ByteBuf buf){buf.writeLong(pos.toLong());ByteBufUtils.writeUTF8String(buf,name);buf.writeBoolean(assembling);buf.writeBoolean(assemble);}
    public static final class Handler implements IMessageHandler<RailwayStationPacket,IMessage> {
        @Override public IMessage onMessage(RailwayStationPacket msg,MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(!player.world.isBlockLoaded(msg.pos)||player.getDistanceSqToCenter(msg.pos)>64D
                        || !player.canPlayerEdit(msg.pos,EnumFacing.UP,player.getHeldItemMainhand()))return;
                TileEntity tile=player.world.getTileEntity(msg.pos);
                if(tile instanceof RailwayTargetTile.Station station) {
                    if(msg.assemble) {
                        // A stale GUI cannot silently re-enter assembly or disassemble a train.
                        station.assemble(player);return;
                    }
                    if(msg.assembling&&!station.assembling()&&station.trainPresent()&&!station.disassemble(player))return;
                    station.configure(msg.name,msg.assembling);
                    if(msg.assembling&&!station.assembling())
                        player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("cogworkbridge.train.invalid_station"),true);
                }
            });return null;
        }
    }
}
