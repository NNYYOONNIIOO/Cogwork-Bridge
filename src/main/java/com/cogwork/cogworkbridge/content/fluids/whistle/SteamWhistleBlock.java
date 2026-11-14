package com.cogwork.cogworkbridge.content.fluids.whistle;

import com.cogwork.cogworkbridge.content.fluids.FluidTankBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class SteamWhistleBlock extends BlockContainer {
    /** Both mounting orientation and horizontal direction are stored in metadata. */
    public static final PropertyDirection FACING=PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyBool WALL=PropertyBool.create("wall");
    public SteamWhistleBlock() { super(Material.IRON);setHardness(2F);setDefaultState(getDefaultState().withProperty(FACING,EnumFacing.NORTH).withProperty(WALL,false)); }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this,FACING,WALL); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING,EnumFacing.byHorizontalIndex(meta&3)).withProperty(WALL,(meta&4)!=0); }
    @Override public int getMetaFromState(IBlockState s) { return s.getValue(FACING).getHorizontalIndex()|(s.getValue(WALL)?4:0); }
    public static EnumFacing attachedDirection(IBlockState s) { return s.getValue(WALL)?s.getValue(FACING):EnumFacing.DOWN; }
    @Override public IBlockState getStateForPlacement(World w,BlockPos p,EnumFacing face,float x,float y,float z,int meta,EntityLivingBase placer) { boolean wall=face.getAxis().isHorizontal();return getDefaultState().withProperty(FACING,wall?face.getOpposite():placer.getHorizontalFacing()).withProperty(WALL,wall); }
    @Override public boolean canPlaceBlockOnSide(World w,BlockPos p,EnumFacing face) { EnumFacing toward=face.getAxis()==EnumFacing.Axis.Y?EnumFacing.DOWN:face.getOpposite();return super.canPlaceBlockAt(w,p) && w.getBlockState(p.offset(toward)).getBlock() instanceof FluidTankBlock; }
    @Override public void neighborChanged(IBlockState s,World w,BlockPos p,Block b,BlockPos from) { if(!w.isRemote && !(w.getBlockState(p.offset(attachedDirection(s))).getBlock() instanceof FluidTankBlock)) w.destroyBlock(p,true); }
    @Override public boolean onBlockActivated(World w,BlockPos p,IBlockState s,EntityPlayer player,EnumHand hand,EnumFacing face,float x,float y,float z) {
        if(player.getHeldItem(hand).getItem()!=Item.getItemFromBlock(AllBlocks.STEAM_WHISTLE)) return false;
        if(!w.isRemote && w.getTileEntity(p) instanceof SteamWhistleTileEntity t) t.extend();return true;
    }
    @Override public TileEntity createNewTileEntity(World w,int meta) { return new SteamWhistleTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState s) { return EnumBlockRenderType.ENTITYBLOCK_ANIMATED; }
    @Override public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override public boolean isFullCube(IBlockState s) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState s,IBlockAccess w,BlockPos p) { return new AxisAlignedBB(1/16D,0,1/16D,15/16D,1,15/16D); }
}
