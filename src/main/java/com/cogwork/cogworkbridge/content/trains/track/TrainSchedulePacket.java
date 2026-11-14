package com.cogwork.cogworkbridge.content.trains.track;

import com.cogwork.cogworkbridge.registry.AllItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class TrainSchedulePacket implements IMessage {
    private int slot;
    private TrainSchedule schedule=new TrainSchedule();
    public TrainSchedulePacket() { }
    public TrainSchedulePacket(int slot,TrainSchedule schedule){this.slot=slot;this.schedule=schedule;}
    @Override public void toBytes(ByteBuf buf) {
        buf.writeByte(slot);ByteBufUtils.writeTag(buf,schedule.write());
    }
    @Override public void fromBytes(ByteBuf buf) {
        slot=buf.readUnsignedByte();var tag=ByteBufUtils.readTag(buf);schedule=TrainSchedule.read(tag==null?new net.minecraft.nbt.NBTTagCompound():tag);
    }
    public static final class Handler implements IMessageHandler<TrainSchedulePacket,IMessage> {
        @Override public IMessage onMessage(TrainSchedulePacket msg,MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(msg.slot<0||msg.slot>=9||player.inventory.currentItem!=msg.slot)return;
                ItemStack held=player.inventory.getStackInSlot(msg.slot);
                if(held.getItem()!=AllItems.SCHEDULE)return;
                msg.schedule.save(held);player.inventory.markDirty();
            });return null;
        }
    }
}
