package com.cogwork.cogworkbridge.content.processing.mixer;

import com.cogwork.cogworkbridge.content.processing.basin.BasinBlock;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

public final class MechanicalMixerBlock extends KineticBlock {
    private static final AxisAlignedBB SELECTION = FULL_BLOCK_AABB;

    public MechanicalMixerBlock() {
        super(Material.ROCK, "cogworkbridge.mechanical_mixer");
        setHardness(2.0F);
        setResistance(6.0F);
        setHarvestLevel("pickaxe", 0);
    }

    @Override public boolean hasAxis() { return false; }

    @Override
    public EnumFacing.Axis getAxis(IBlockState state) {
        return EnumFacing.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        return false;
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos)
                && !(world.getBlockState(pos.down()).getBlock() instanceof BasinBlock);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new MechanicalMixerTileEntity();
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return SELECTION;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
                                      AxisAlignedBB entityBox, List<AxisAlignedBB> boxes,
                                      Entity entity, boolean isActualState) {
        if (entity instanceof EntityPlayer) {
            addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(0, 2 / 16D, 0, 1, 1, 1));
            return;
        }
        addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(0, 0, 0, 4 / 16D, 1, 1));
        addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(12 / 16D, 0, 0, 1, 1, 1));
        addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(4 / 16D, 0, 0, 12 / 16D, 1, 4 / 16D));
        addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(4 / 16D, 0, 12 / 16D, 12 / 16D, 1, 1));
    }

    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
}
