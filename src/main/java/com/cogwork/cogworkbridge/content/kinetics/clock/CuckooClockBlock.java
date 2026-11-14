package com.cogwork.cogworkbridge.content.kinetics.clock;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class CuckooClockBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    private final boolean mysterious;
    public CuckooClockBlock(boolean mysterious) { super(Material.WOOD); this.mysterious=mysterious; setHardness(2F); setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH)); }
    public boolean isMysterious() { return mysterious; }
    @Override public boolean hasAxis() { return false; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this,FACING); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta)); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateForPlacement(World w, BlockPos p, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        EnumFacing preferred = placer.isSneaking() ? null : getPreferredShaftFacing(w,p);
        return getDefaultState().withProperty(FACING, preferred != null && preferred.getAxis().isHorizontal() ? preferred.getOpposite() : placer.getHorizontalFacing().getOpposite());
    }
    @Override public EnumFacing.Axis getAxis(IBlockState s) { return s.getValue(FACING).getAxis(); }
    @Override public boolean hasShaftTowards(World w, BlockPos p, IBlockState s, EnumFacing face) { return face == s.getValue(FACING).getOpposite(); }
    @Override public TileEntity createNewTileEntity(World w, int meta) { return new CuckooClockTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override public boolean isFullCube(IBlockState s) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState s,IBlockAccess w,BlockPos p) { return new AxisAlignedBB(1/16D,0,1/16D,15/16D,1,15/16D); }
    @Override public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> items) { if (!mysterious) super.getSubBlocks(tab,items); }
}
