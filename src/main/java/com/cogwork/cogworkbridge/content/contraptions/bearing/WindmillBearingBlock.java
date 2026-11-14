package com.cogwork.cogworkbridge.content.contraptions.bearing;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class WindmillBearingBlock extends MechanicalBearingBlock {
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new WindmillBearingTileEntity(); }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.isSneaking() || !player.getHeldItem(hand).isEmpty()) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof WindmillBearingTileEntity windmill) {
            if (windmill.isAssembled()) windmill.requestDisassemble(); else windmill.requestAssemble();
        }
        return true;
    }
}
