package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import java.util.*;

/** Prediction snapshots survive unloading, while live updates run only for active displays. */
public final class RailwayDisplayData extends WorldSavedData {
    private static final String ID="cogworkbridge_train_display";
    private final Map<UUID,NBTTagCompound> trains=new LinkedHashMap<>();
    private long lastRefresh=Long.MIN_VALUE;
    public RailwayDisplayData(){this(ID);}
    public RailwayDisplayData(String name){super(name);}
    private static World root(World world) {
        var server=world.getMinecraftServer();return server==null?world:server.getWorld(0);
    }
    private static RailwayDisplayData get(World world) {
        var storage=root(world).getPerWorldStorage();var data=(RailwayDisplayData)storage.getOrLoadData(RailwayDisplayData.class,ID);
        if(data==null){data=new RailwayDisplayData();storage.setData(ID,data);}return data;
    }
    private void refresh(World world,long now) {
        if(lastRefresh!=Long.MIN_VALUE&&now>=lastRefresh&&now-lastRefresh<20)return;
        lastRefresh=now;
        var server=world.getMinecraftServer();if(server==null)return;
        for(WorldServer level:server.worlds)for(RailwayCarriageEntity train:TrainDimensionTransfer.loaded(level)) {
            if(train.isPortalProxy()||train.awaitingPortalTransfer()||!train.trainId().equals(train.getUniqueID())||train.isCrashed())continue;
            NBTTagList predictions=train.displayPredictions();
            if(predictions.isEmpty()) {if(trains.remove(train.trainId())!=null)markDirty();continue;}
            NBTTagCompound record=new NBTTagCompound();record.setTag("Predictions",predictions);record.setLong("Updated",now);
            NBTTagCompound previous=trains.put(train.trainId(),record);
            // Arrival times are live cache state; persist new/changed schedules, not every countdown tick.
            if(previous==null||!sameSchedule(previous.getTagList("Predictions",10),predictions))markDirty();
        }
    }
    private static boolean sameSchedule(NBTTagList previous,NBTTagList current) {
        if(previous.tagCount()!=current.tagCount())return false;
        for(int i=0;i<current.tagCount();i++) {
            NBTTagCompound before=previous.getCompoundTagAt(i),after=current.getCompoundTagAt(i);
            for(String key:List.of("Destination","Name","Title"))if(!before.getString(key).equals(after.getString(key)))return false;
        }
        return true;
    }
    static void remove(RailwayCarriageEntity train){var data=get(train.world);if(data.trains.remove(train.trainId())!=null)data.markDirty();}
    public static List<String> read(TileEntity source,int mode,String filter,int columns,int namePercent,int platformChars) {
        if(source instanceof RailwayTargetTile.Observer observer)return List.of(observer.observedTrainName());
        if(!(source instanceof RailwayTargetTile.Station station)||source.getWorld().isRemote)return List.of("");
        if(mode==21) {var train=station.stoppedTrain();return List.of(train==null?"":train.waitingStatus());}
        if(mode!=22)return List.of("");
        record Prediction(int ticks,String name,String title,String platform) { }
        List<Prediction> predictions=new ArrayList<>();var world=source.getWorld();
        String pattern=filter.isBlank()?station.name():filter;
        long now=root(world).getTotalWorldTime();var data=get(world);data.refresh(world,now);
        for(var record:data.trains.values()) {
            boolean live=now-record.getLong("Updated")<40;var list=record.getTagList("Predictions",10);
            for(int i=0;i<list.tagCount();i++) {
                var p=list.getCompoundTagAt(i);String destination=p.getString("Destination");
                if(!TrainSchedule.matches(pattern,destination)&&!(TrainSchedule.matches(pattern,station.name())&&TrainSchedule.matches(destination,station.name())))continue;
                predictions.add(new Prediction(live?p.getInteger("Ticks"):-1,p.getString("Name"),p.getString("Title"),destination));
            }
        }
        predictions.sort(Comparator.comparingInt((Prediction p)->p.ticks<0?Integer.MAX_VALUE:p.ticks<200?0:p.ticks).thenComparing(Prediction::name));
        List<String> lines=new ArrayList<>();boolean hasPlatform=pattern.contains("*");
        int platformWidth=hasPlatform?Math.min(platformChars,Math.max(0,columns-9)):0;
        int available=Math.max(0,columns-7-platformWidth-(platformWidth>0?1:0));
        int nameWidth=available*namePercent/100,titleWidth=available-nameWidth;
        for(var p:predictions) {
            int roundedSeconds=p.ticks<0?0:((p.ticks/20+14)/15)*15;
            String time=p.ticks<0||p.ticks>=11700?" ~ ":p.ticks<200?tr("arrival_now")
                    :roundedSeconds>=60?tr("arrival_minutes",roundedSeconds/60):tr("arrival_seconds",roundedSeconds);
            String platform=p.platform;for(String literal:pattern.split("\\*"))if(!literal.isEmpty())platform=platform.replace(literal,"");
            lines.add(fit(time,6,false)+" "+fit(p.name,nameWidth,false)+fit(p.title,titleWidth,false)
                    +(platformWidth>0?" "+fit(platform.trim().replace('*','?'),platformWidth,true):""));
            if(lines.size()==64)break;
        }return lines;
    }
    private static String tr(String key,Object... args){return net.minecraft.util.text.translation.I18n.translateToLocalFormatted("gui.cogworkbridge.display."+key,args);}
    private static String fit(String value,int width,boolean right) {
        if(width<=0)return "";int length=value.codePointCount(0,value.length());
        if(length>width)value=value.substring(0,value.offsetByCodePoints(0,width));
        String spaces=" ".repeat(Math.max(0,width-Math.min(width,length)));return right?spaces+value:value+spaces;
    }
    @Override public void readFromNBT(NBTTagCompound tag){trains.clear();lastRefresh=Long.MIN_VALUE;var list=tag.getTagList("Trains",10);for(int i=0;i<list.tagCount();i++){var t=list.getCompoundTagAt(i);if(t.hasUniqueId("Id"))trains.put(t.getUniqueId("Id"),t.copy());}}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){NBTTagList list=new NBTTagList();for(var e:trains.entrySet()){var t=e.getValue().copy();t.setUniqueId("Id",e.getKey());list.appendTag(t);}tag.setTag("Trains",list);return tag;}
}
