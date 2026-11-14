package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class RoseQuartzLampTileEntity extends TileEntity {
    private boolean powered, lit;

    public boolean isLit() { return lit; }

    public void refreshInput() {
        if (world == null || world.isRemote) return;
        boolean input = world.isBlockPowered(pos);
        if (powered == input) return;
        powered = input;
        markDirty();
        if (!input) return;
        forEachConnected(other -> { if (other != this) other.setLit(false); });
        setLit(true);
    }

    public void wrenchToggle() {
        if (world == null || world.isRemote) return;
        setLit(!lit);
        forEachConnected(other -> other.world.notifyNeighborsOfStateChange(other.pos, other.getBlockType(), false));
    }

    private void forEachConnected(java.util.function.Consumer<RoseQuartzLampTileEntity> action) {
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        pending.add(pos.toImmutable());
        visited.add(pos.toImmutable());
        while (!pending.isEmpty()) {
            BlockPos current = pending.removeFirst();
            if (world.getTileEntity(current) instanceof RoseQuartzLampTileEntity lamp) action.accept(lamp);
            for (EnumFacing side : EnumFacing.VALUES) {
                BlockPos next = current.offset(side);
                if (Math.abs(next.getX() - pos.getX()) + Math.abs(next.getY() - pos.getY())
                        + Math.abs(next.getZ() - pos.getZ()) > 16 || !visited.add(next) || !world.isBlockLoaded(next)) continue;
                if (world.getBlockState(next).getBlock() instanceof RoseQuartzLampBlock) pending.add(next);
            }
        }
    }

    private void setLit(boolean value) {
        if (lit == value) return;
        lit = value;
        markDirty();
        if (world instanceof FakeWorld fake) fake.getMobileChunk().markTileDirty(pos);
        else {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
            world.checkLight(pos);
        }
        world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("Powered", powered);
        tag.setBoolean("Lit", lit);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        powered = tag.getBoolean("Powered");
        lit = tag.getBoolean("Lit");
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
