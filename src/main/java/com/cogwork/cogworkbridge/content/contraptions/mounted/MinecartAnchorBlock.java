package com.cogwork.cogworkbridge.content.contraptions.mounted;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Visual anchor captured with a minecart structure; it is never placed in the world. */
public final class MinecartAnchorBlock extends Block {
    public static final PropertyEnum<EnumFacing.Axis> AXIS = PropertyEnum.create("axis", EnumFacing.Axis.class, axis -> axis.isHorizontal());
    public MinecartAnchorBlock() {
        super(Material.IRON);
        setDefaultState(blockState.getBaseState().withProperty(AXIS, EnumFacing.Axis.Z));
    }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, AXIS); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(AXIS) == EnumFacing.Axis.X ? 1 : 0; }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(AXIS, (meta & 1) == 0 ? EnumFacing.Axis.Z : EnumFacing.Axis.X);
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return new AxisAlignedBB(.5D, 0D, .5D, .5D, 0D, .5D);
    }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return NULL_AABB; }
}
