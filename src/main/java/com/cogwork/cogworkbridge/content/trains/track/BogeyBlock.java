package com.cogwork.cogworkbridge.content.trains.track;

import com.cogwork.cogworkbridge.registry.BlockFamily;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import java.util.Random;

/** Bogeys are installed on assembly tracks from a railway casing. */
public final class BogeyBlock extends BlockContainer {
    public static final PropertyEnum<EnumFacing.Axis> AXIS=PropertyEnum.create("axis",EnumFacing.Axis.class,
            axis->axis==EnumFacing.Axis.X||axis==EnumFacing.Axis.Z);
    private final boolean large;
    public BogeyBlock(boolean large) {
        super(Material.IRON);this.large=large;setHardness(2F);setSoundType(net.minecraft.block.SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(AXIS,EnumFacing.Axis.Z));
    }
    public boolean isLarge(){return large;}
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,AXIS);}
    @Override public int getMetaFromState(IBlockState state){return state.getValue(AXIS)==EnumFacing.Axis.X?1:0;}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(AXIS,(meta&1)!=0?EnumFacing.Axis.X:EnumFacing.Axis.Z);}
    @Override public IBlockState withRotation(IBlockState state,Rotation rotation){
        if(rotation==Rotation.CLOCKWISE_90||rotation==Rotation.COUNTERCLOCKWISE_90)
            return state.withProperty(AXIS,state.getValue(AXIS)==EnumFacing.Axis.X?EnumFacing.Axis.Z:EnumFacing.Axis.X);
        return state;
    }
    @Override public TileEntity createNewTileEntity(World world,int meta){return new BogeyTile();}
    @Override public EnumBlockRenderType getRenderType(IBlockState state){return EnumBlockRenderType.MODEL;}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public BlockRenderLayer getRenderLayer(){return BlockRenderLayer.CUTOUT_MIPPED;}
    @Override public Item getItemDropped(IBlockState state,Random random,int fortune){return Item.getItemFromBlock(BlockFamily.get("railway_casing"));}
    @Override public ItemStack getPickBlock(IBlockState state,RayTraceResult target,World world,BlockPos pos,EntityPlayer player){
        return new ItemStack(BlockFamily.get("railway_casing"));
    }
}
