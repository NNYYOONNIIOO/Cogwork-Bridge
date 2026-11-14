package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import java.util.*;

public final class RailwayDisplayData extends WorldSavedData {
    private static final String ID="cogworkbridge_train_display";
    private final Map<UUID,NBTTagCompound> trains=new LinkedHashMap<>();
    public RailwayDisplayData(){this(ID);}
    public RailwayDisplayData(String name){super(name);}
    private static RailwayDisplayData get(World world) {
        var server=world.getMinecraftServer();World root=server==null?world:server.getWorld(0);
        var storage=root.getPerWorldStorage();var data=(RailwayDisplayData)storage.getOrLoadData(RailwayDisplayData.class,ID);
        if(data==null){data=new RailwayDisplayData();storage.setData(ID,data);}return data;
    }
    static void capture(RailwayCarriageEntity train) {
        var data=get(train.world);var predictions=train.displayPredictions();
        if(predictions.isEmpty()){if(data.trains.remove(train.trainId())!=null)data.markDirty();return;}
        NBTTagCompound record=new NBTTagCompound();record.setTag("Predictions",predictions);
        record.setLong("Updated",train.world.getMinecraftServer().getWorld(0).getTotalWorldTime());
        data.trains.put(train.trainId(),record);data.markDirty();
    }
    static void remove(RailwayCarriageEntity train){var data=get(train.world);if(data.trains.remove(train.trainId())!=null)data.markDirty();}
    public static List<String> read(TileEntity source,int mode,String filter,int columns,int namePercent,int platformChars) {
        if(source instanceof RailwayTargetTile.Observer observer)return List.of(observer.observedTrainName());
        if(!(source instanceof RailwayTargetTile.Station station)||source.getWorld().isRemote)return List.of("");
        if(mode==21) {var train=station.stoppedTrain();return List.of(train==null?"":train.waitingStatus());}
        record Prediction(int ticks,String name,String title,String platform) { }
        List<Prediction> predictions=new ArrayList<>();var world=source.getWorld();
        String pattern=filter.isBlank()?station.name():filter;
        long now=world.getMinecraftServer().getWorld(0).getTotalWorldTime();
        for(var record:get(world).trains.values()) {
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
    @Override public void readFromNBT(NBTTagCompound tag){trains.clear();var list=tag.getTagList("Trains",10);for(int i=0;i<list.tagCount();i++){var t=list.getCompoundTagAt(i);if(t.hasUniqueId("Id"))trains.put(t.getUniqueId("Id"),t.copy());}}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){NBTTagList list=new NBTTagList();for(var e:trains.entrySet()){var t=e.getValue().copy();t.setUniqueId("Id",e.getKey());list.appendTag(t);}tag.setTag("Trains",list);return tag;}
}
