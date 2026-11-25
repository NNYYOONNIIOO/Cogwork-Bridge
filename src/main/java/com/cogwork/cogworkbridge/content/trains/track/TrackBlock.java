package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import javax.annotation.Nullable;

public final class TrackBlock extends BlockContainer {
    public static final PropertyEnum<TrackShape> SHAPE=PropertyEnum.create("shape",TrackShape.class);
    /** Render-only connection direction; the track axis remains in metadata. */
    public static final net.minecraftforge.common.property.IUnlistedProperty<EnumFacing> PORTAL_FACE=new net.minecraftforge.common.property.IUnlistedProperty<EnumFacing>() {
        public String getName(){return "portal_face";}public boolean isValid(EnumFacing value){return value!=null;}
        public Class<EnumFacing> getType(){return EnumFacing.class;}public String valueToString(EnumFacing value){return value.getName();}
    };
    private static final AxisAlignedBB OUTLINE=new AxisAlignedBB(0,0,0,1,.25,1);
    private static final AxisAlignedBB TRACK_COLLISION=new AxisAlignedBB(0,0,0,1,.125D,1);
    public TrackBlock() {
        super(Material.IRON); setHardness(.8F); setLightOpacity(0); setSoundType(net.minecraft.block.SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(SHAPE,TrackShape.ZO));
    }
    @Override protected BlockStateContainer createBlockState(){return new net.minecraftforge.common.property.ExtendedBlockState(this,new net.minecraft.block.properties.IProperty<?>[]{SHAPE},new net.minecraftforge.common.property.IUnlistedProperty<?>[]{PORTAL_FACE});}
    @Override public IBlockState getExtendedState(IBlockState state,IBlockAccess world,BlockPos pos) {
        EnumFacing face=EnumFacing.UP;
        if(world.getTileEntity(pos) instanceof TrackTileEntity tile&&tile.portalLinked()) {
            var shape=state.getValue(SHAPE);
            for(EnumFacing side:EnumFacing.HORIZONTALS)if((shape==TrackShape.XO&&side.getAxis()==EnumFacing.Axis.X||shape==TrackShape.ZO&&side.getAxis()==EnumFacing.Axis.Z)
                    &&world.getBlockState(pos.offset(side)).getBlock()==net.minecraft.init.Blocks.PORTAL){face=side;break;}
        }
        return ((net.minecraftforge.common.property.IExtendedBlockState)state).withProperty(PORTAL_FACE,face);
    }
    @Override public int getMetaFromState(IBlockState state){return state.getValue(SHAPE).ordinal();}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(SHAPE,TrackShape.byMeta(meta));}
    @Override public TileEntity createNewTileEntity(World world,int meta){return new TrackTileEntity();}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public EnumBlockRenderType getRenderType(IBlockState state){return EnumBlockRenderType.MODEL;}
    @Override public BlockRenderLayer getRenderLayer(){return BlockRenderLayer.CUTOUT_MIPPED;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return OUTLINE;}
    @Nullable @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){
        return state.getValue(SHAPE).slope()?NULL_AABB:TRACK_COLLISION;
    }
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,
            java.util.List<AxisAlignedBB> boxes,@Nullable net.minecraft.entity.Entity entity,boolean actual) {
        // Extended railway surfaces are supplied by TrackCollisionEvents, which
        // also handles geometry crossing more than one neighboring block.
    }
    @Override public EnumPushReaction getPushReaction(IBlockState state){return EnumPushReaction.BLOCK;}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,
            EnumHand hand,EnumFacing side,float x,float y,float z) {
        if(player.isSneaking())return false;
        for(TileEntity tile:new java.util.ArrayList<>(world.loadedTileEntityList))
            if(tile instanceof RailwayTargetTile.Station station && station.tryBogey(player,hand,pos))return true;
        return false;
    }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face,
            float hitX,float hitY,float hitZ,int meta,EntityLivingBase placer) {
        Vec3d look=placer.getLookVec();
        double angle=Math.toDegrees(Math.atan2(look.z,look.x));
        int octant=Math.floorMod((int)Math.round(angle/45D),4);
        TrackShape shape=switch(octant){case 0 -> TrackShape.XO; case 1 -> TrackShape.PD;
            case 2 -> TrackShape.ZO; default -> TrackShape.ND;};
        return getDefaultState().withProperty(SHAPE,shape);
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        TrackShape shape=state.getValue(SHAPE);
        for(int i=0;i<rotation.ordinal();i++) shape=rotate(shape);
        return state.withProperty(SHAPE,shape);
    }
    private static TrackShape rotate(TrackShape s) {
        return switch(s) {
            case XO -> TrackShape.ZO; case ZO -> TrackShape.XO; case PD -> TrackShape.ND; case ND -> TrackShape.PD;
            case AE -> TrackShape.AS; case AS -> TrackShape.AW; case AW -> TrackShape.AN; case AN -> TrackShape.AE;
            case CR_PDX -> TrackShape.CR_NDZ; case CR_NDZ -> TrackShape.CR_PDX;
            case CR_NDX -> TrackShape.CR_PDZ; case CR_PDZ -> TrackShape.CR_NDX; default -> s;
        };
    }
    @Override public boolean removedByPlayer(IBlockState state,World world,BlockPos pos,EntityPlayer player,boolean willHarvest) {
        if(!world.isRemote && player.capabilities.isCreativeMode && world.getTileEntity(pos) instanceof TrackTileEntity track)
            track.suppressDrops();
        return super.removedByPlayer(state,world,pos,player,willHarvest);
    }
    @Override public void onBlockAdded(World world,BlockPos pos,IBlockState state) {
        super.onBlockAdded(world,pos,state);
        if(!world.isRemote) {
            RailwayNetworkData.get(world).update(world,pos);
            RailwayPortals.discover(world,pos,true);
        }
    }
    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,net.minecraft.block.Block block,BlockPos fromPos) {
        super.neighborChanged(state,world,pos,block,fromPos);
        if(!world.isRemote&&fromPos.getY()==pos.getY()&&pos.distanceSq(fromPos)==1
                &&(block==Blocks.PORTAL||world.isBlockLoaded(fromPos)&&world.getBlockState(fromPos).getBlock()==Blocks.PORTAL))
            RailwayPortals.discover(world,pos,true);
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state) {
        TileEntity te=world.getTileEntity(pos);
        if(!world.isRemote && te instanceof TrackTileEntity track) {
            for(TrackCurve curve:new java.util.ArrayList<>(track.connections())) {
                TrackCurveSupport.remove(world,curve,!track.dropsSuppressed(),pos);
            }
        }
        if(!world.isRemote)RailwayNetworkData.get(world).remove(pos);
        if(!world.isRemote)RailwayPortals.remove(world,pos);
        super.breakBlock(world,pos,state);
    }
}
