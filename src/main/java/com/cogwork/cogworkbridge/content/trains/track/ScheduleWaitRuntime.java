package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import java.util.ArrayList;
import java.util.List;

/** Each OR branch advances through its AND column independently and retains completed conditions. */
final class ScheduleWaitRuntime {
    private final List<NBTTagCompound> branches=new ArrayList<>();
    void reset() { branches.clear(); }
    String status(TrainSchedule.Entry entry) {
        for(int i=0;i<entry.groups.size();i++) {
            int index=i<branches.size()?branches.get(i).getInteger("Index"):0;
            if(index>=entry.groups.get(i).size())continue;
            var w=entry.groups.get(i).get(index);int elapsed=i<branches.size()?branches.get(i).getInteger("Elapsed"):0;
            String key="gui.cogworkbridge.schedule.condition."+w.type.name().toLowerCase(java.util.Locale.ROOT);
            String text=net.minecraft.util.text.translation.I18n.translateToLocal(key);
            return text+(w.type==TrainSchedule.Condition.DELAY?" "+Math.max(0,(w.value-elapsed+19)/20)+"s":"");
        }return "";
    }
    boolean tick(RailwayCarriageEntity train,TrainSchedule.Entry entry) {
        if(entry.groups.isEmpty())return true;
        while(branches.size()<entry.groups.size())branches.add(new NBTTagCompound());
        for(int branch=0;branch<entry.groups.size();branch++) {
            var group=entry.groups.get(branch);var c=branches.get(branch);int index=c.getInteger("Index");
            if(index>=group.size())return true;
            if(test(train,group.get(index),c)) {
                c=new NBTTagCompound();c.setInteger("Index",index+1);branches.set(branch,c);
                if(index+1>=group.size())return true;
            }
        }return false;
    }
    private boolean test(RailwayCarriageEntity train,TrainSchedule.Wait w,NBTTagCompound c) {
        var world=train.world;int elapsed=c.getInteger("Elapsed");c.setInteger("Elapsed",Math.min(TrainSchedule.MAX_WAIT,elapsed+1));
        return switch(w.type) {
            case DELAY -> elapsed>=Math.min(TrainSchedule.MAX_WAIT,w.value);
            case REDSTONE -> {
                var stationWorld=train.stationWorld();var station=train.stationPosition();
                yield station!=null&&stationWorld!=null&&stationWorld.isBlockLoaded(station)&&stationWorld.isBlockPowered(station);
            }
            case PLAYER -> TrainCargo.players(train)>0;
            case CARGO_EMPTY -> TrainCargo.countItems(train,net.minecraft.item.ItemStack.EMPTY,false)==0;
            case CARGO_FULL -> train.legacyCargoFull();
            case ITEM_THRESHOLD -> w.operator.test(TrainCargo.countItems(train,w.filter,w.measure==1),w.value);
            case FLUID_THRESHOLD -> w.operator.test(TrainCargo.countFluids(train,w.filter),(long)w.value*1000);
            case PLAYER_COUNT -> w.measure==0?TrainCargo.players(train)==w.value:TrainCargo.players(train)>=w.value;
            case TIME_OF_DAY -> {
                int[] rotations={24000,12000,6000,4000,3000,2000,1000,750,500,250};int rotation=rotations[w.rotation];
                int target=Math.floorMod(((w.value/60+18)%24)*1000+(int)Math.ceil((w.value%60)/60F*1000),rotation);
                long diff=Math.floorMod(world.getWorldTime(),rotation)-target;yield diff>=0&&diff<=40;
            }
            case WIRELESS_REDSTONE -> com.cogwork.cogworkbridge.content.redstone.RedstoneLinkNetwork.hasAnyLoadedPower(w.filter,w.second)!=(w.measure==1);
            case STATION_UNLOADED -> {
                var station=train.stationPosition();var stationWorld=train.stationWorld();
                yield station!=null&&(stationWorld==null||!stationWorld.isBlockLoaded(station));
            }
            case IDLE_CARGO -> {
                long hash=TrainCargo.fingerprint(train);int idle=c.getInteger("Idle");
                if(!c.hasKey("Cargo")||c.getLong("Cargo")!=hash)idle=0;else idle++;
                c.setLong("Cargo",hash);c.setInteger("Idle",idle);yield idle>Math.min(TrainSchedule.MAX_WAIT,w.value);
            }
        };
    }
    NBTTagList write() { NBTTagList list=new NBTTagList();for(var c:branches)list.appendTag(c.copy());return list; }
    void read(NBTTagList list) { branches.clear();for(int i=0;i<Math.min(TrainSchedule.MAX_GROUPS,list.tagCount());i++)branches.add(list.getCompoundTagAt(i).copy()); }
}
