package com.cogwork.cogworkbridge.content.decoration.palette;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

public class CopycatTile extends TileEntity {
    private IBlockState material = Blocks.AIR.getDefaultState();
    private ItemStack consumed = ItemStack.EMPTY;
    public IBlockState material() { return material; }
    public ItemStack consumed() { return consumed; }
    @Override public void mirror(net.minecraft.util.Mirror mirror) { material = material.withMirror(mirror); }
    @Override public void rotate(net.minecraft.util.Rotation rotation) { material = material.withRotation(rotation); }
    public void setMaterial(IBlockState state, ItemStack stack) {
        material = state; consumed = stack.copy(); if (!consumed.isEmpty()) consumed.setCount(1);
        markDirty();
        if (world != null) { IBlockState old = world.getBlockState(pos); world.notifyBlockUpdate(pos, old, old, 3); world.checkLight(pos); }
    }
    public ItemStack takeMaterial() { ItemStack result = consumed; setMaterial(Blocks.AIR.getDefaultState(), ItemStack.EMPTY); return result; }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setTag("Material", NBTUtil.writeBlockState(new NBTTagCompound(), material));
        if (!consumed.isEmpty()) tag.setTag("Consumed", consumed.writeToNBT(new NBTTagCompound()));
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); material = tag.hasKey("Material", 10) ? NBTUtil.readBlockState(tag.getCompoundTag("Material")) : Blocks.AIR.getDefaultState();
        consumed = tag.hasKey("Consumed", 10) ? new ItemStack(tag.getCompoundTag("Consumed")) : ItemStack.EMPTY;
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager network, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound()); if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
