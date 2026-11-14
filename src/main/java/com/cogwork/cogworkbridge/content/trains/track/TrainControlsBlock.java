package com.cogwork.cogworkbridge.content.trains.track;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

public final class TrainControlsBlock extends Block {
    public static final PropertyDirection FACING=PropertyDirection.create("facing",EnumFacing.Plane.HORIZONTAL);
    /** Derived from the render world; never serialized into metadata or the chunk palette. */
    public static final IUnlistedProperty<Boolean> OPEN=new IUnlistedProperty<Boolean>() {
        @Override public String getName(){return "open";}
        @Override public boolean isValid(Boolean value){return value!=null;}
        @Override public Class<Boolean> getType(){return Boolean.class;}
        @Override public String valueToString(Boolean value){return value.toString();}
    };
    public TrainControlsBlock(){super(Material.IRON);setHardness(2F);setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH));}
    @Override protected BlockStateContainer createBlockState(){
        return new ExtendedBlockState(this,new IProperty<?>[]{FACING},new IUnlistedProperty<?>[]{OPEN});
    }
    @Override public IBlockState getExtendedState(IBlockState state,IBlockAccess world,BlockPos pos){
        return ((IExtendedBlockState)state).withProperty(OPEN,world instanceof FakeWorld
                || com.cogwork.cogworkbridge.client.TutorialRenderState.isOpen(world,pos));
    }
    @Override public int getMetaFromState(IBlockState state){return state.getValue(FACING).getHorizontalIndex();}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,EnumFacing.byHorizontalIndex(meta));}
    @Override public IBlockState withRotation(IBlockState state,Rotation rotation){return state.withProperty(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer){
        return getDefaultState().withProperty(FACING,placer.getHorizontalFacing().getOpposite());
    }
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public BlockRenderLayer getRenderLayer(){return BlockRenderLayer.CUTOUT_MIPPED;}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,
            EnumHand hand,EnumFacing side,float x,float y,float z) {
        if(!(world instanceof FakeWorld fake)
                || !(fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity body)
                || !(body.getMountedCart() instanceof RailwayCarriageEntity carriage))return false;
        net.minecraft.item.ItemStack held=player.getHeldItem(hand);
        if (carriage.isCrashed()) {
            if (!world.isRemote) {
                boolean recovered = held.getItem() == com.cogwork.cogworkbridge.registry.AllItems.WRENCH && carriage.recoverFromCrash();
                player.sendStatusMessage(new TextComponentTranslation(recovered ? "cogworkbridge.train.recovered"
                        : held.getItem() == com.cogwork.cogworkbridge.registry.AllItems.WRENCH
                        ? "cogworkbridge.train.recovery_blocked" : "cogworkbridge.train.crashed"), true);
            }
            return true;
        }
        if(held.getItem()==com.cogwork.cogworkbridge.registry.AllItems.SCHEDULE) {
            if(!world.isRemote&&carriage.installSchedule(held)) {
                if(!player.capabilities.isCreativeMode)held.shrink(1);
                player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schedule.assigned"),true);
            }
            return true;
        }
        if(player.isSneaking()) {
            if(held.isEmpty()&&!world.isRemote) {
                net.minecraft.item.ItemStack schedule=carriage.removeSchedule();
                if(!schedule.isEmpty())net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(player,schedule);
            }
            return true;
        }
        if(!world.isRemote && player instanceof EntityPlayerMP serverPlayer) {
            if(carriage.startDriving(serverPlayer,pos))
                ModNetwork.CHANNEL.sendTo(new TrainControlClientPacket(carriage.getEntityId(),pos),serverPlayer);
            else player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.train.controls_busy"),true);
        }
        return true;
    }
}
