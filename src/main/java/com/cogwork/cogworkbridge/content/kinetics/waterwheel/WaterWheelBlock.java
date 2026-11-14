package com.cogwork.cogworkbridge.content.kinetics.waterwheel;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

/** The single-block water wheel. Its axis is stored in metadata, like other kinetic blocks. */
public final class WaterWheelBlock extends KineticBlock {
    public WaterWheelBlock() {
        super(Material.WOOD);
        setHardness(2F);
        setDefaultState(getDefaultState().withProperty(AXIS, EnumFacing.Axis.X));
    }

    @Override public boolean hasAxis() { return true; }

    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y,
                                                       float z, int meta, EntityLivingBase placer) {
        EnumFacing preferred = placer.isSneaking() ? null : getPreferredShaftFacing(world, pos);
        return getDefaultState().withProperty(AXIS, preferred == null ? getPlacementAxis(placer) : preferred.getAxis());
    }

    @Override public TileEntity createNewTileEntity(World world, int meta) { return new WaterWheelTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }

    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                               EnumHand hand, EnumFacing side, float x, float y, float z) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.getItem() != Item.getItemFromBlock(Blocks.PLANKS)) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof WaterWheelTileEntity wheel)
            wheel.setWood(stack.getMetadata());
        return true;
    }

    @Override public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        if (!world.isRemote) world.scheduleUpdate(pos, this, 1);
    }

    @Override public void updateTick(World world, BlockPos pos, IBlockState state, Random random) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof WaterWheelTileEntity wheel)
            wheel.updateFlow();
    }

    static BlockPos offset(EnumFacing.Axis axis, int a, int b) {
        return switch (axis) {
            case X -> new BlockPos(0, a, b);
            case Y -> new BlockPos(a, 0, b);
            case Z -> new BlockPos(a, b, 0);
        };
    }
}
