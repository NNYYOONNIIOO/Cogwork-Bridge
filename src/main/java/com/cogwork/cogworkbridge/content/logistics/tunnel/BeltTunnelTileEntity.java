package com.cogwork.cogworkbridge.content.logistics.tunnel;

import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.fan.FanProcessingHolder;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

public class BeltTunnelTileEntity extends CogworkTileEntity implements ITickable, FanProcessingHolder {
    private boolean closed;
    private final int[] flapSerials = new int[6];
    private final float[] flaps = new float[6], previousFlaps = new float[6];
    public float flapValue(EnumFacing side, float partial) {
        int i = side.getIndex(); return previousFlaps[i] + (flaps[i] - previousFlaps[i]) * partial;
    }
    public void flap(EnumFacing side) {
        if (world == null || world.isRemote || side.getAxis().isVertical()) return;
        flapSerials[side.getIndex()]++; markDirty();
        world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
    }
    public void onTransportedItemsReceived(int amount) {
        if (world == null || world.isRemote || amount <= 0) return;
        for (EnumFacing side : EnumFacing.VALUES) {
            BlockPos neighbour = pos.offset(side);
            if (!world.isBlockLoaded(neighbour)) continue;
            TileEntity tile = world.getTileEntity(neighbour);
            if (tile instanceof com.cogwork.cogworkbridge.content.display.DisplayLinkTileEntity link
                    && link.getSourcePosition().equals(pos)) link.onTunnelItems(amount);
        }
    }
    @Override public void update() {
        if (world == null || !world.isRemote) return;
        for (int i = 0; i < flaps.length; i++) { previousFlaps[i] = flaps[i]; flaps[i] = Math.max(0, flaps[i] - 0.125F); }
    }
    public boolean windowClosed() { return closed; }
    public void toggleWindow() {
        closed = !closed; markDirty();
        if (world != null && !world.isRemote) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }

    @Override public void applyFanProcessing(EncasedFanTileEntity.Medium medium) {
        if (world != null && world.getTileEntity(pos.down()) instanceof BeltTileEntity belt)
            belt.applyFanProcessing(medium);
    }

    /** Return true while a side output is blocked; otherwise let the remainder proceed. */
    public boolean transferToSide(BeltTileEntity source, EnumFacing forward) {
        if (world == null || world.isRemote || source == null || source.getCarried().isEmpty()) return false;
        if (source.getCarried().getCount() <= 1) return false;
        EnumFacing.Axis beltAxis = source.step().getX() != 0 ? EnumFacing.Axis.X : EnumFacing.Axis.Z;
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            if (side.getAxis() == beltAxis || side == forward.getOpposite()) continue;
            if (source.getCarried().getCount() <= 1) break;
            if (source.alreadySplitTo(side)) continue;
            if (!BeltTunnelBlock.hasExit(world, pos.down(), side)) continue;
            BlockPos outputPos = pos.down().offset(side);
            if (world.getTileEntity(outputPos) instanceof BeltTileEntity belt && !belt.canReceiveFrom(side)) continue;
            IItemHandler target = ItemTransfer.at(world, outputPos, side.getOpposite());
            if (target == null) continue;
            ItemStack carried = source.getCarried();
            ItemStack one = carried.copy(); one.setCount(1);
            if (target instanceof BeltTileEntity belt) {
                // Enter at the actual input end rather than the capability's centre.
                if (!belt.insertFrom(side, one, true).isEmpty()) return true;
                ItemStack extracted = source.extractItem(0, 1, false);
                ItemStack remaining = belt.insertFrom(side, extracted, false);
                if (!remaining.isEmpty()) {
                    ItemStack restored = source.restoreSplitItem(remaining);
                    if (!restored.isEmpty()) ItemTransfer.drop(world, source.getPos(), restored);
                    return true;
                }
                source.markSplitTo(side);
                flap(side);
                continue;
            }
            if (!ItemHandlerHelper.insertItemStacked(target, one, true).isEmpty()) return true;
            int moved = ItemTransfer.move(source, target, 1, stack -> true,
                    stack -> ItemTransfer.drop(world, source.getPos(), stack));
            if (moved > 0) {
                source.markSplitTo(side);
                flap(side);
            } else return true;
        }
        return false;
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setBoolean("WindowClosed", closed); tag.setIntArray("FlapSerials", flapSerials); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); closed = tag.getBoolean("WindowClosed");
        int[] serials = tag.getIntArray("FlapSerials");
        if (serials.length == 0 && tag.hasKey("FlapSide")) {
            serials = new int[flapSerials.length];
            int side = tag.getInteger("FlapSide");
            if (side >= 0 && side < serials.length) serials[side] = tag.getInteger("FlapSerial");
        }
        for (int i = 0; i < flapSerials.length; i++) {
            int serial = i < serials.length ? serials[i] : 0;
            if (world != null && world.isRemote && serial != flapSerials[i]) flaps[i] = previousFlaps[i] = 1;
            flapSerials[i] = serial;
        }
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound()); if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
