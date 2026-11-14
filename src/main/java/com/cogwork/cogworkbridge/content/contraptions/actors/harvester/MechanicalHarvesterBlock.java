package com.cogwork.cogworkbridge.content.contraptions.actors.harvester;

import com.cogwork.cogworkbridge.content.contraptions.actors.AttachedActorBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public final class MechanicalHarvesterBlock extends AttachedActorBlock {
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new MechanicalHarvesterTileEntity();
    }
}
