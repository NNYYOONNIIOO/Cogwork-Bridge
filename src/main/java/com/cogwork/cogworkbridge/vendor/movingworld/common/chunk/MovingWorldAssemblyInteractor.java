/*
 * Derived from MovingWorld (https://github.com/darkevilmac/MovingWorld).
 * Upstream: src/main/java/com/elytradev/movingworld/common/chunk/MovingWorldAssemblyInteractor.java
 * Revision: 400225200ea4f913047f67dbfb44e882fcf997d8.
 * Modified by Cogwork Bridge contributors: relocated packages and adapted
 * for integration with Cogwork Bridge contraptions and their lifecycle.
 * Licensed under the Apache License, Version 2.0; see
 * META-INF/licenses/MovingWorld-LICENSE.txt and THIRD-PARTY-NOTICES.txt.
 */
package com.cogwork.cogworkbridge.vendor.movingworld.common.chunk;

import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

/**
 * Used for storing information given by and taken by the (Dis)Assembler <p/> Mostly for use in
 * GUIs, the ship pretty much immediately forgets this thing.
 */
public class MovingWorldAssemblyInteractor {

    public MovingWorldAssemblyInteractor fromByteBuf(byte resultCode, ByteBuf byteBuf) {
        return new MovingWorldAssemblyInteractor();
    }

    public MovingWorldAssemblyInteractor fromNBT(NBTTagCompound tag, World world) {
        return new MovingWorldAssemblyInteractor();
    }

    public boolean doDiagonalAssembly() {
        return false;
    }

    public void toByteBuf(ByteBuf byteBuf) {
    }

    /**
     * Called when a block is assembled to your moving world.
     */
    public void blockAssembled(LocatedBlock locatedBlock) {
    }

    /**
     * Called when a block is disassembled from your moving world.
     */
    public void blockDisassembled(LocatedBlock locatedBlock) {
        if (locatedBlock.tileEntity != null && locatedBlock.tileEntity.getWorld() != null
                && locatedBlock.tileEntity.getWorld().getTileEntity(locatedBlock.blockPos) != null)
            locatedBlock.tileEntity.getWorld().getTileEntity(locatedBlock.blockPos).markDirty();
    }

    /**
     * @return returns if it is an over writable block in the config.
     */
    public boolean canOverwriteState(IBlockState state) {
        return false;
    }

    /**
     * Called when a block is overwritten when a moving world is disassembled.
     */
    public void blockOverwritten(Block block) {
    }

    /**
     * Called when a block is rotated during disassembling.
     */
    public IBlockState blockRotated(IBlockState blockState, int deltarot) {
        return blockState;
    }

    /**
     * Called when a chunk assembly has finished.
     */
    public void chunkAssembled(Object assembleResult) {
    }

    /**
     * Called when a chunk disassembly has finished.
     */
    public void chunkDissasembled(Object assembleResult) {
    }

    public boolean isBlockMovingWorldMarker(Block block) {
        return false;
    }

    public boolean isTileMovingWorldMarker(TileEntity tile) {
        return false;
    }

    public EnumFacing getFrontDirection(LocatedBlock marker) {
        return EnumFacing.NORTH;
    }

    /**
     * Recommended to call writeNBTMetadata first, then write the rest of your data.
     */
    public void writeNBTFully(NBTTagCompound tag) {
    }


    /**
     * Write metadata to NBT.
     */
    public void writeNBTMetadata(NBTTagCompound tag) {
    }

}
