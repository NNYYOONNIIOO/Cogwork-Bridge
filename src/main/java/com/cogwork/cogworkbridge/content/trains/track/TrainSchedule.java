package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import java.util.ArrayList;
import java.util.List;

/** A bounded, persistent list of destination filters and station waiting times. */
public final class TrainSchedule {
    public static final int MAX_ENTRIES=32, MAX_NAME=64, MAX_WAIT=20*60*60, MAX_GROUPS=8, MAX_CONDITIONS=8;
    public enum Instruction { DESTINATION, RENAME, THROTTLE, FETCH_PACKAGES, DELIVER_PACKAGES }
    public enum Condition { DELAY, REDSTONE, PLAYER, CARGO_EMPTY, CARGO_FULL, TIME_OF_DAY,
        WIRELESS_REDSTONE, ITEM_THRESHOLD, FLUID_THRESHOLD, PLAYER_COUNT, IDLE_CARGO, STATION_UNLOADED }
    public enum Operator { LESS, EQUAL, GREATER;
        public boolean test(long actual,long target) { return this==LESS?actual<target:this==GREATER?actual>target:actual==target; }
    }
    public static final class Wait {
        public Condition type=Condition.DELAY;
        public int value=100, rotation, measure;
        public Operator operator=Operator.GREATER;
        public ItemStack filter=ItemStack.EMPTY, second=ItemStack.EMPTY;
        public Wait() { }
        public Wait(Condition type,int value) { this.type=type;this.value=value; }
        public NBTTagCompound write() {
            NBTTagCompound t=new NBTTagCompound();t.setString("Type",type.name());t.setInteger("Value",value);
            t.setInteger("Rotation",rotation);t.setInteger("Measure",measure);t.setString("Operator",operator.name());
            if(!filter.isEmpty())t.setTag("Filter",filter.serializeNBT());if(!second.isEmpty())t.setTag("Second",second.serializeNBT());return t;
        }
        public static Wait read(NBTTagCompound t) {
            Wait w=new Wait();w.type=parse(Condition.class,t.getString("Type"),Condition.DELAY);
            w.value=Math.max(0,Math.min(1000000,t.getInteger("Value")));w.rotation=Math.max(0,Math.min(9,t.getInteger("Rotation")));
            w.measure=Math.max(0,Math.min(1,t.getInteger("Measure")));w.operator=parse(Operator.class,t.getString("Operator"),Operator.GREATER);
            w.filter=t.hasKey("Filter",10)?new ItemStack(t.getCompoundTag("Filter")):ItemStack.EMPTY;
            w.second=t.hasKey("Second",10)?new ItemStack(t.getCompoundTag("Second")):ItemStack.EMPTY;
            if(!w.filter.isEmpty())w.filter.setCount(1);if(!w.second.isEmpty())w.second.setCount(1);return w;
        }
    }
    public static final class Entry {
        public Instruction instruction=Instruction.DESTINATION;
        public String text="";
        public int value=100;
        public final List<List<Wait>> groups=new ArrayList<>();
        public Entry(String destination,int wait,Condition condition) {
            text=destination==null?"":destination.substring(0,Math.min(MAX_NAME,destination.length()));
            groups.add(new ArrayList<>(List.of(new Wait(condition,Math.max(0,Math.min(MAX_WAIT,wait))))));
        }
        public String destination() { return text; }
        public int waitTicks() { return first().type==Condition.DELAY?first().value:0; }
        public Condition condition() { return first().type; }
        public Wait first() { return groups.isEmpty()||groups.get(0).isEmpty()?new Wait(Condition.DELAY,0):groups.get(0).get(0); }
        public Entry copy() { return read(write()); }
        public NBTTagCompound write() {
            NBTTagCompound t=new NBTTagCompound();t.setString("Instruction",instruction.name());t.setString("Text",text);t.setInteger("Value",value);
            NBTTagList outer=new NBTTagList();
            for(List<Wait> group:groups) { NBTTagCompound g=new NBTTagCompound();NBTTagList inner=new NBTTagList();for(Wait w:group)inner.appendTag(w.write());g.setTag("Conditions",inner);outer.appendTag(g); }
            t.setTag("Groups",outer);return t;
        }
        public static Entry read(NBTTagCompound t) {
            int legacy=Math.max(0,Math.min(Condition.values().length-1,t.getInteger("Condition")));
            Entry e=new Entry(t.hasKey("Text")?t.getString("Text"):t.getString("Destination"),t.getInteger("Wait"),Condition.values()[legacy]);
            e.instruction=parse(Instruction.class,t.getString("Instruction"),Instruction.DESTINATION);e.value=Math.max(0,Math.min(100,t.hasKey("Value")?t.getInteger("Value"):100));
            if(t.hasKey("Groups",9)) {
                e.groups.clear();NBTTagList outer=t.getTagList("Groups",10);
                for(int i=0;i<Math.min(MAX_GROUPS,outer.tagCount());i++) {
                    NBTTagList inner=outer.getCompoundTagAt(i).getTagList("Conditions",10);List<Wait> group=new ArrayList<>();
                    for(int j=0;j<Math.min(MAX_CONDITIONS,inner.tagCount());j++)group.add(Wait.read(inner.getCompoundTagAt(j)));
                    if(!group.isEmpty())e.groups.add(group);
                }
            }return e;
        }
    }
    private static <E extends Enum<E>> E parse(Class<E> type,String value,E fallback) {
        try { return Enum.valueOf(type,value); } catch(IllegalArgumentException ignored) { return fallback; }
    }
    public final List<Entry> entries=new ArrayList<>();
    public boolean cyclic=true;
    public int savedProgress;

    public static TrainSchedule fromItem(ItemStack item) {
        return item.hasTagCompound()?read(item.getTagCompound().getCompoundTag("Schedule")):new TrainSchedule();
    }
    public static TrainSchedule read(NBTTagCompound tag) {
        TrainSchedule schedule=new TrainSchedule();
        schedule.cyclic=!tag.hasKey("Cyclic")||tag.getBoolean("Cyclic");
        NBTTagList list=tag.getTagList("Entries",10);
        for(int i=0;i<list.tagCount()&&i<MAX_ENTRIES;i++) {
            schedule.entries.add(Entry.read(list.getCompoundTagAt(i)));
        }
        schedule.savedProgress=Math.max(0,Math.min(Math.max(0,schedule.entries.size()-1),tag.getInteger("Progress")));
        return schedule;
    }
    public void add(String name,int wait) {
        add(name,wait,Condition.DELAY);
    }
    public void add(String name,int wait,Condition condition) {
        if(entries.size()>=MAX_ENTRIES||name==null)return;
        name=name.trim();if(name.length()>MAX_NAME)name=name.substring(0,MAX_NAME);
        if(!name.isEmpty())entries.add(new Entry(name,Math.max(0,Math.min(MAX_WAIT,wait)),condition==null?Condition.DELAY:condition));
    }
    public NBTTagCompound write() {
        NBTTagCompound tag=new NBTTagCompound();tag.setBoolean("Cyclic",cyclic);NBTTagList list=new NBTTagList();
        for(Entry entry:entries) {
            list.appendTag(entry.write());
        }
        tag.setTag("Entries",list);tag.setInteger("Progress",savedProgress);return tag;
    }
    public void save(ItemStack stack) {
        NBTTagCompound root=stack.hasTagCompound()?stack.getTagCompound():new NBTTagCompound();
        root.setTag("Schedule",write());stack.setTagCompound(root);
    }
    public static boolean matches(String pattern,String name) {
        StringBuilder regex=new StringBuilder("(?i)^");
        for(int i=0;i<pattern.length();i++) {
            char c=pattern.charAt(i);
            if(c=='*')regex.append(".*");else regex.append(java.util.regex.Pattern.quote(String.valueOf(c)));
        }
        return name.matches(regex.append('$').toString());
    }
}
