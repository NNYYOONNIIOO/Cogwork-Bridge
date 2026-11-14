package com.cogwork.cogworkbridge.content.fluids.whistle;

import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
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
import java.util.Random;

public final class WhistleExtensionBlock extends BlockContainer {
    public WhistleExtensionBlock(){super(Material.IRON);setHardness(2F);}
    @Override public TileEntity createNewTileEntity(World w,int m){return new WhistleExtensionTileEntity();}
    @Override public EnumBlockRenderType getRenderType(IBlockState s){return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;}
    @Override public boolean isOpaqueCube(IBlockState s){return false;} @Override public boolean isFullCube(IBlockState s){return false;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){return new AxisAlignedBB(.1875,0,.1875,.8125,1,.8125);}
    @Override public Item getItemDropped(IBlockState s,Random rand,int fortune){return net.minecraft.init.Items.AIR;}
    public static BlockPos root(World w,BlockPos p){for(int i=0;i<7 && w.getBlockState(p).getBlock() instanceof WhistleExtensionBlock;i++)p=p.down();return p;}
    @Override public boolean onBlockActivated(World w,BlockPos p,IBlockState s,EntityPlayer pl,EnumHand hand,EnumFacing f,float x,float y,float z){
        if(pl.getHeldItem(hand).getItem()!=Item.getItemFromBlock(AllBlocks.STEAM_WHISTLE))return false;
        if(!w.isRemote && w.getTileEntity(root(w,p)) instanceof SteamWhistleTileEntity t)t.extend();return true;
    }
    @Override public void neighborChanged(IBlockState s,World w,BlockPos p,Block b,BlockPos from){if(!w.isRemote && !(w.getBlockState(p.down()).getBlock() instanceof SteamWhistleBlock || w.getBlockState(p.down()).getBlock() instanceof WhistleExtensionBlock))w.setBlockToAir(p);}
}
