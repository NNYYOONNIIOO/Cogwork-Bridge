package com.cogwork.cogworkbridge.content.trains.track;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class TrainControlClientPacket implements IMessage {
    private int carriageId;
    private BlockPos controlsPos;
    public TrainControlClientPacket() { }
    public TrainControlClientPacket(int id){carriageId=id;}
    public TrainControlClientPacket(int id,BlockPos pos){carriageId=id;controlsPos=pos.toImmutable();}
    @Override public void fromBytes(ByteBuf buf){carriageId=buf.readInt();controlsPos=buf.readBoolean()?BlockPos.fromLong(buf.readLong()):null;}
    @Override public void toBytes(ByteBuf buf){buf.writeInt(carriageId);buf.writeBoolean(controlsPos!=null);if(controlsPos!=null)buf.writeLong(controlsPos.toLong());}
    public static final class Handler implements IMessageHandler<TrainControlClientPacket,IMessage> {
        @Override public IMessage onMessage(TrainControlClientPacket msg,MessageContext ctx){
            Minecraft.getMinecraft().addScheduledTask(()->com.cogwork.cogworkbridge.client.TrainControlClient.start(msg.carriageId,msg.controlsPos));
            return null;
        }
    }
}
