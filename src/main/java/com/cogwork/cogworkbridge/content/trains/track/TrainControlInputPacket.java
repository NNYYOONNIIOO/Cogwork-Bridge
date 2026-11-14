package com.cogwork.cogworkbridge.content.trains.track;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class TrainControlInputPacket implements IMessage {
    private int carriageId,mask;
    private boolean stop;
    public TrainControlInputPacket() { }
    public TrainControlInputPacket(int id,int mask,boolean stop){carriageId=id;this.mask=mask;this.stop=stop;}
    @Override public void fromBytes(ByteBuf buf){carriageId=buf.readInt();mask=buf.readUnsignedByte();stop=buf.readBoolean();}
    @Override public void toBytes(ByteBuf buf){buf.writeInt(carriageId);buf.writeByte(mask);buf.writeBoolean(stop);}
    public static final class Handler implements IMessageHandler<TrainControlInputPacket,IMessage> {
        @Override public IMessage onMessage(TrainControlInputPacket msg,MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                Entity entity=player.world.getEntityByID(msg.carriageId);
                if(!(entity instanceof RailwayCarriageEntity carriage))return;
                if(msg.stop)carriage.stopDriving(player);else carriage.input(player,msg.mask);
            });return null;
        }
    }
}
