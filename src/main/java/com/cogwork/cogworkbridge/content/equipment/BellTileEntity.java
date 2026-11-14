package com.cogwork.cogworkbridge.content.equipment;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

public final class BellTileEntity extends TileEntity implements ITickable {
    private int ticks = 74;
    private int sequence;
    private boolean powered;
    private EnumFacing direction = EnumFacing.NORTH;
    private BlockPos previousMovingPosition;
    private int movementRecharge;
    public int ticks() { return ticks; }
    public EnumFacing direction() { return direction; }
    public void updatePower(boolean value) { if (value && !powered) ring(EnumFacing.NORTH); powered = value; markDirty(); }
    public void ring(EnumFacing face) {
        if (world == null || world.isRemote || !(getBlockType() instanceof BellBlock block)) return;
        if (block.isHaunted() && ticks < 65) return;
        direction = face.getAxis().isHorizontal() ? face : EnumFacing.NORTH;
        ticks = 0;
        sequence++;
        markDirty();
        world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
        world.playSound(null, pos, block.isHaunted() ? com.cogwork.cogworkbridge.registry.AllSounds.HAUNTED_BELL : com.cogwork.cogworkbridge.registry.AllSounds.PECULIAR_BELL,
                SoundCategory.BLOCKS, block.isHaunted() ? 4F : 2F, block.isHaunted() ? 1F : .94F);
    }
    @Override public void update() {
        if (world == null) return;
        boolean haunted = getBlockType() instanceof BellBlock block && block.isHaunted();
        if (haunted && world instanceof com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld carried) {
            BlockPos actual = carried.worldPosition(pos);
            if (movementRecharge > 0) movementRecharge--;
            if (previousMovingPosition != null && !actual.equals(previousMovingPosition) && movementRecharge == 0) {
                movementRecharge = 65;
                if (world.isRemote) com.cogwork.cogworkbridge.CogworkBridge.proxy.spawnSoulPulse(actual, 3);
                else world.playSound(null, pos, com.cogwork.cogworkbridge.registry.AllSounds.HAUNTED_BELL,
                        SoundCategory.BLOCKS, 4F, 1F);
            }
            previousMovingPosition = actual;
        } else previousMovingPosition = null;
        if (world.isRemote && ticks == 0 && haunted) {
            BlockPos actual = world instanceof com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld carried
                    ? carried.worldPosition(pos) : pos;
            com.cogwork.cogworkbridge.CogworkBridge.proxy.spawnSoulPulse(actual, 10);
        }
        if (ticks < 74) ticks++;
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setInteger("Ticks", ticks); tag.setInteger("Sequence", sequence);
        tag.setInteger("Direction", direction.getIndex()); tag.setBoolean("Powered", powered); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); ticks = Math.max(0, Math.min(74, tag.getInteger("Ticks")));
        sequence = tag.getInteger("Sequence"); direction = EnumFacing.byIndex(tag.getInteger("Direction")); powered = tag.getBoolean("Powered");
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager network, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
