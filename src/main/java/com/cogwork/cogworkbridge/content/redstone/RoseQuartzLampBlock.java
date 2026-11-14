package com.cogwork.cogworkbridge.content.redstone;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

/** The illuminated lamp in a connected panel stores its state in its tile. */
public final class RoseQuartzLampBlock extends BlockContainer {
    public static final IUnlistedProperty<Boolean> LIT = new IUnlistedProperty<Boolean>() {
        @Override public String getName() { return "lit"; }
        @Override public boolean isValid(Boolean value) { return value != null; }
        @Override public Class<Boolean> getType() { return Boolean.class; }
        @Override public String valueToString(Boolean value) { return value.toString(); }
    };

    public RoseQuartzLampBlock() { super(Material.GLASS); setHardness(.3F); }
    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[0], new IUnlistedProperty<?>[]{LIT});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return ((IExtendedBlockState) state).withProperty(LIT,
                world.getTileEntity(pos) instanceof RoseQuartzLampTileEntity lamp && lamp.isLit());
    }
    @Override public int getMetaFromState(IBlockState state) { return 0; }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState(); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new RoseQuartzLampTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof RoseQuartzLampTileEntity lamp && lamp.isLit() ? 15 : 0;
    }
    @Override public boolean canProvidePower(IBlockState state) { return true; }
    @Override public boolean shouldCheckWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return false; }
    @Override public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        if (side == null) return 0;
        IBlockState receiver = world.getBlockState(pos.offset(side.getOpposite()));
        if (receiver.getBlock() == this) return 0;
        if (receiver.getBlock() instanceof net.minecraft.block.BlockRedstoneComparator) {
            for (int power = 15; power > 0; power--) {
                BlockPos current = pos.offset(side, 15 - power);
                if (world.getBlockState(current).getBlock() != this) return 0;
                if (world.getTileEntity(current) instanceof RoseQuartzLampTileEntity lamp && lamp.isLit()) return power;
            }
            return 0;
        }
        return world.getTileEntity(pos) instanceof RoseQuartzLampTileEntity lamp && lamp.isLit() ? 15 : 0;
    }
    @Override public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return 0; }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, net.minecraft.block.Block block, BlockPos from) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof RoseQuartzLampTileEntity lamp) lamp.refreshInput();
    }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, net.minecraft.item.ItemStack stack) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof RoseQuartzLampTileEntity lamp) lamp.refreshInput();
    }
}
