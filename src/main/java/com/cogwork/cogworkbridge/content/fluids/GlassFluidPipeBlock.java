package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

public final class GlassFluidPipeBlock extends FluidPipeBlock {
    public static final PropertyEnum<EnumFacing.Axis> AXIS = PropertyEnum.create("axis", EnumFacing.Axis.class);

    public GlassFluidPipeBlock() {
        setDefaultState(getDefaultState().withProperty(AXIS, EnumFacing.Axis.Y));
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, AXIS); }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(AXIS, EnumFacing.Axis.values()[Math.floorMod(meta, 3)]);
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(AXIS).ordinal(); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        EnumFacing facing = EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, state.getValue(AXIS));
        return state.withProperty(AXIS, rotation.rotate(facing).getAxis());
    }
    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) { return state; }
    @Override public Item getItemDropped(IBlockState state, Random random, int fortune) {
        return Item.getItemFromBlock(AllBlocks.FLUID_PIPE);
    }
    @Override public net.minecraft.item.ItemStack getPickBlock(IBlockState state,
            net.minecraft.util.math.RayTraceResult target, net.minecraft.world.World world,
            BlockPos pos, net.minecraft.entity.player.EntityPlayer player) {
        return new net.minecraft.item.ItemStack(AllBlocks.FLUID_PIPE);
    }
}
