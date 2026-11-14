package com.cogwork.cogworkbridge.content.logistics.port;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.trains.track.*;
import com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.items.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import java.util.*;

/** Persistent station mailboxes. Unloaded mailboxes exchange through saved inventories, then reconcile on load. */
@Mod.EventBusSubscriber(modid=Reference.MOD_ID)
public final class MailboxNetwork extends WorldSavedData {
    private static final String NAME="cogworkbridge_station_mail", SELECTION="CogworkBridgeMailboxStation";
    private final Map<BlockPos,NBTTagCompound> ports=new LinkedHashMap<>();
    private final List<NBTTagCompound> recovery=new ArrayList<>();
    public MailboxNetwork() { this(NAME); }
    public MailboxNetwork(String name) { super(name); }
    public static MailboxNetwork get(World world) {
        var storage=world.getPerWorldStorage();var data=(MailboxNetwork)storage.getOrLoadData(MailboxNetwork.class,NAME);
        if(data==null){data=new MailboxNetwork();storage.setData(NAME,data);}return data;
    }
    public void publish(PackagePortTileEntity port) {
        BlockPos station=port.mailStation();if(station==null){if(ports.remove(port.getPos())!=null)markDirty();return;}
        NBTTagCompound old=ports.get(port.getPos());long revision=Math.max(port.mailRevision(),old==null?0:old.getLong("Revision"))+1;
        NBTTagCompound t=new NBTTagCompound();t.setLong("Station",station.toLong());t.setString("Address",port.address());t.setBoolean("Accepts",port.accepts());
        t.setTag("Inventory",port.inventory().serializeNBT());t.setLong("Revision",revision);
        ports.put(port.getPos().toImmutable(),t);port.setMailRevision(revision);markDirty();
    }
    public void load(PackagePortTileEntity port) {
        NBTTagCompound stored=ports.get(port.getPos());
        if(stored!=null&&port.mailStation()!=null&&stored.getLong("Station")==port.mailStation().toLong()&&stored.getLong("Revision")>port.mailRevision())
            port.applyMailInventory(stored.getCompoundTag("Inventory"),stored.getLong("Revision"));
        else publish(port);
    }
    public void remove(BlockPos pos) { if(ports.remove(pos)!=null)markDirty(); }
    public List<BlockPos> atStation(BlockPos station) {
        List<BlockPos> result=new ArrayList<>();for(var entry:ports.entrySet())if(entry.getValue().getLong("Station")==station.toLong())result.add(entry.getKey());return result;
    }
    public boolean offers(BlockPos station,String address) {
        for(BlockPos pos:atStation(station)) { var data=ports.get(pos);var inv=new ItemStackHandler(18);inv.deserializeNBT(data.getCompoundTag("Inventory"));
            for(int i=0;i<inv.getSlots();i++) {var stack=inv.getStackInSlot(i);if(outgoing(data,stack)&&TrainSchedule.matches(address.isBlank()?"*":address,PackageItem.address(stack)))return true;}
        }return false;
    }
    public boolean accepts(BlockPos station,RailwayCarriageEntity train) {
        for(BlockPos pos:atStation(station)) { var data=ports.get(pos);if(!data.getBoolean("Accepts"))continue;
            for(var inv:TrainCargo.items(train))for(int i=0;i<inv.getSlots();i++) {
                var s=inv.getStackInSlot(i);if(PackageItem.isPackage(s)&&PackageItem.matchAddress(PackageItem.address(s),data.getString("Address")))return true;
            }
        }return false;
    }
    private static boolean outgoing(NBTTagCompound data,ItemStack stack) {
        return PackageItem.isPackage(stack)&&(!data.getBoolean("Accepts")||!PackageItem.matchAddress(PackageItem.address(stack),data.getString("Address")));
    }
    public void exchange(World world,BlockPos station,RailwayCarriageEntity train) {
        if(world.isRemote||Math.abs(train.speed())>.005)return;
        List<IItemHandler> cargo=TrainCargo.items(train);
        for(BlockPos pos:atStation(station)) {
            NBTTagCompound data=ports.get(pos);if(data==null)continue;
            PackagePortTileEntity live=world.isBlockLoaded(pos)&&world.getTileEntity(pos) instanceof PackagePortTileEntity p?p:null;
            if(live!=null&&world.isBlockLoaded(station)&&world.getTileEntity(station) instanceof RailwayTargetTile.Station stop)live.refreshStationName(stop.name());
            ItemStackHandler inventory=live==null?new ItemStackHandler(18):live.inventory();
            if(live==null)inventory.deserializeNBT(data.getCompoundTag("Inventory"));
            boolean changed=false,sent=false;
            for(IItemHandler inv:cargo) {
                for(int i=0;i<inv.getSlots();i++) {
                    ItemStack s=inv.getStackInSlot(i);
                    if(!data.getBoolean("Accepts")||!PackageItem.isPackage(s)||!PackageItem.matchAddress(PackageItem.address(s),data.getString("Address")))continue;
                    changed|=move(inv,i,inventory,pos);
                }
                for(int i=0;i<inventory.getSlots();i++)if(outgoing(data,inventory.getStackInSlot(i))) {
                    boolean moved=move(inventory,i,inv,pos);changed|=moved;sent|=moved;
                }
            }
            if(changed) {
                if(live!=null){live.animateMailTransfer(sent);publish(live);}
                else { data.setTag("Inventory",inventory.serializeNBT());data.setLong("Revision",data.getLong("Revision")+1);markDirty(); }
            }
        }
    }
    private boolean move(IItemHandler from,int slot,IItemHandler to,BlockPos recoveryAt) {
        ItemStack sample=from.extractItem(slot,1,true);if(sample.isEmpty()||!ItemHandlerHelper.insertItemStacked(to,sample,true).isEmpty())return false;
        ItemStack extracted=from.extractItem(slot,1,false);if(extracted.isEmpty())return false;
        ItemStack left=ItemHandlerHelper.insertItemStacked(to,extracted,false);
        if(!left.isEmpty()) {
            ItemStack restore=from.insertItem(slot,left,false);
            if(!restore.isEmpty())restore=ItemHandlerHelper.insertItemStacked(from,restore,false);
            if(!restore.isEmpty()) {
                NBTTagCompound saved=new NBTTagCompound();saved.setLong("Pos",recoveryAt.toLong());saved.setTag("Item",restore.serializeNBT());
                recovery.add(saved);markDirty();
            }
            return !restore.isEmpty();
        }return true;
    }
    @SubscribeEvent public static void recover(net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent e) {
        if(e.phase!=net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END||e.world.isRemote)return;
        MailboxNetwork network=get(e.world);
        for(var it=network.recovery.iterator();it.hasNext();) {
            NBTTagCompound saved=it.next();BlockPos pos=BlockPos.fromLong(saved.getLong("Pos"));if(!e.world.isBlockLoaded(pos))continue;
            ItemStack stack=new ItemStack(saved.getCompoundTag("Item"));
            if(stack.isEmpty()||e.world.spawnEntity(new net.minecraft.entity.item.EntityItem(e.world,pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,stack))) {
                it.remove();network.markDirty();
            }
        }
    }
    @SubscribeEvent public static void select(PlayerInteractEvent.RightClickBlock e) {
        ItemStack held=e.getEntityPlayer().getHeldItem(e.getHand());
        if(!(Block.getBlockFromItem(held.getItem()) instanceof PackagePortBlock block)||block.isFrog()
                ||!(e.getWorld().getTileEntity(e.getPos()) instanceof RailwayTargetTile.Station station))return;
        if(!e.getWorld().isRemote) {
            NBTTagCompound tag=held.hasTagCompound()?held.getTagCompound():new NBTTagCompound(),selection=new NBTTagCompound();
            selection.setLong("Pos",e.getPos().toLong());selection.setInteger("Dimension",e.getWorld().provider.getDimension());selection.setString("Name",station.name());
            tag.setTag(SELECTION,selection);held.setTagCompound(tag);e.getEntityPlayer().inventory.markDirty();
            e.getEntityPlayer().sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("cogworkbridge.mailbox.selected",station.name()),true);
        }
        e.setCanceled(true);e.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
    }
    public static void bindPlaced(World world,BlockPos pos,ItemStack stack) {
        if(world.isRemote||!stack.hasTagCompound()||!stack.getTagCompound().hasKey(SELECTION,10)
                ||!(world.getTileEntity(pos) instanceof PackagePortTileEntity port))return;
        var selection=stack.getTagCompound().getCompoundTag(SELECTION);BlockPos station=BlockPos.fromLong(selection.getLong("Pos"));
        if(selection.getInteger("Dimension")==world.provider.getDimension()&&pos.distanceSq(station)<=32D*32D
                &&world.isBlockLoaded(station)&&world.getTileEntity(station) instanceof RailwayTargetTile.Station)
            port.bindStation(station,selection.getString("Name"));
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        recovery.clear();var pending=tag.getTagList("Recovery",10);for(int i=0;i<pending.tagCount();i++)recovery.add(pending.getCompoundTagAt(i).copy());
        ports.clear();NBTTagList list=tag.getTagList("Ports",10);for(int i=0;i<Math.min(100000,list.tagCount());i++) {
            NBTTagCompound t=list.getCompoundTagAt(i);ports.put(BlockPos.fromLong(t.getLong("Pos")),t.copy());
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList pending=new NBTTagList();for(var saved:recovery)pending.appendTag(saved.copy());tag.setTag("Recovery",pending);
        NBTTagList list=new NBTTagList();for(var entry:ports.entrySet()) {var t=entry.getValue().copy();t.setLong("Pos",entry.getKey().toLong());list.appendTag(t);}tag.setTag("Ports",list);return tag;
    }
}
