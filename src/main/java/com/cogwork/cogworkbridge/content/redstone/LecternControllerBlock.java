package com.cogwork.cogworkbridge.content.redstone;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import java.util.Random;

/** An FFD lectern containing a wireless controller; the original lectern is restored on removal. */
public final class LecternControllerBlock extends BlockContainer {
    public static final PropertyDirection FACING=PropertyDirection.create("facing",EnumFacing.Plane.HORIZONTAL);
    public LecternControllerBlock(){super(Material.WOOD);setHardness(2F);setDefaultState(getDefaultState().withProperty(FACING,EnumFacing.NORTH));}
    public static Block lectern(){return Block.REGISTRY.getObject(new ResourceLocation("farmers_future_delight","lectern"));}
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING);}
    @Override public IBlockState getStateFromMeta(int m){return getDefaultState().withProperty(FACING,EnumFacing.byHorizontalIndex(m));}
    @Override public int getMetaFromState(IBlockState s){return s.getValue(FACING).getHorizontalIndex();}
    @Override public TileEntity createNewTileEntity(World w,int m){return new LecternControllerTileEntity();}
    @Override public EnumBlockRenderType getRenderType(IBlockState s){return EnumBlockRenderType.MODEL;}
    @Override public boolean isOpaqueCube(IBlockState s){return false;} @Override public boolean isFullCube(IBlockState s){return false;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){return new AxisAlignedBB(0,0,0,1,1,1);}
    @Override public boolean hasComparatorInputOverride(IBlockState s){return true;}
    @Override public int getComparatorInputOverride(IBlockState s,World w,BlockPos p){return 15;}
    @Override public Item getItemDropped(IBlockState s,Random r,int fortune){Block b=lectern();return b==null?net.minecraft.init.Items.AIR:Item.getItemFromBlock(b);}
    @Override public ItemStack getItem(World w,BlockPos p,IBlockState s){Block b=lectern();return b==null?ItemStack.EMPTY:new ItemStack(b);}
    @Override public boolean onBlockActivated(World w,BlockPos p,IBlockState s,EntityPlayer pl,EnumHand hand,EnumFacing side,float x,float y,float z){
        if(!(w.getTileEntity(p) instanceof LecternControllerTileEntity t))return false;
        if(!w.isRemote){if(pl.isSneaking())t.restoreLectern();else t.start(pl);}return true;
    }
    @Override public void breakBlock(World w,BlockPos p,IBlockState s){if(!w.isRemote && w.getTileEntity(p) instanceof LecternControllerTileEntity t)t.dropController();super.breakBlock(w,p,s);}
    public static boolean isEmptyLectern(IBlockState s){
        if(s.getBlock()!=lectern())return false;
        for(var e:s.getProperties().entrySet())if(e.getKey().getName().equals("has_book") && Boolean.TRUE.equals(e.getValue()))return false;
        return true;
    }
    public static EnumFacing facing(IBlockState s){for(var e:s.getProperties().entrySet())if(e.getKey().getName().equals("facing") && e.getValue() instanceof EnumFacing f)return f;return EnumFacing.NORTH;}
}
