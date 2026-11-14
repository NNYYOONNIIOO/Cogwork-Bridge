package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import java.util.List;
import javax.annotation.Nullable;

/** Invisible, removable support for the visible Bezier track segments. */
public final class FakeTrackBlock extends BlockContainer {
    private static final AxisAlignedBB SHAPE=new AxisAlignedBB(0,0,0,1,.125D,1);
    public FakeTrackBlock(){super(Material.IRON);setHardness(.8F);setLightOpacity(0);}
    @Override public TileEntity createNewTileEntity(World world,int meta){return new FakeTrackTile();}
    @Override public EnumBlockRenderType getRenderType(IBlockState state){return EnumBlockRenderType.INVISIBLE;}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return SHAPE;}
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return SHAPE;}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,
            List<AxisAlignedBB> boxes,@Nullable Entity entity,boolean actual) {
        // TrackCollisionEvents collects extended curve surfaces for both sides.
    }
    @Override public boolean removedByPlayer(IBlockState state,World world,BlockPos pos,EntityPlayer player,boolean willHarvest) {
        if(!world.isRemote&&player.capabilities.isCreativeMode&&world.getTileEntity(pos) instanceof FakeTrackTile tile)
            tile.suppressDropsForCreative();
        return super.removedByPlayer(state,world,pos,player,willHarvest);
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state) {
        if(!world.isRemote&&world.getTileEntity(pos) instanceof FakeTrackTile tile&&!tile.suppressBreak()) {
            RailwayNetworkData graph=RailwayNetworkData.get(world);
            for(FakeTrackTile.Link link:tile.links()) {
                TrackCurve curve=graph.findCurve(link.first(),link.second());
                if(curve!=null)TrackCurveSupport.remove(world,curve,!tile.suppressDrops(),pos);
            }
        }
        super.breakBlock(world,pos,state);
    }
}
