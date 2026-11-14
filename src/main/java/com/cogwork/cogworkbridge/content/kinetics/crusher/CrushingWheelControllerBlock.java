package com.cogwork.cogworkbridge.content.kinetics.crusher;

import com.cogwork.cogworkbridge.content.kinetics.crusher.CrushingWheelShapes;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public final class CrushingWheelControllerBlock extends Block implements ITileEntityProvider {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public CrushingWheelControllerBlock() {
        super(Material.ROCK);
        setTranslationKey("cogworkbridge.crushing_wheel_controller");
        setHardness(3.0F);
        setResistance(10.0F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.DOWN));
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta & 7));
    }
    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new CrushingWheelControllerTileEntity(); }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return CrushingWheelShapes.bounds(state.getValue(FACING));
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB mask,
                                     List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        TileEntity tile = world.getTileEntity(pos);
        if (entity != null && tile instanceof CrushingWheelControllerTileEntity
                && ((CrushingWheelControllerTileEntity) tile).isProcessing(entity)) return;
        addCollisionBoxToList(pos, mask, boxes, CrushingWheelShapes.bounds(state.getValue(FACING)));
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (world.isRemote) return;
        if (entity instanceof EntityPlayer && ((EntityPlayer) entity).capabilities.isCreativeMode) return;
        if (world.getDifficulty() == net.minecraft.world.EnumDifficulty.PEACEFUL
                && entity instanceof EntityPlayer) return;
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof CrushingWheelControllerTileEntity controller
                && controller.isActive()) {
            ((CrushingWheelControllerTileEntity) tile).startCrushing(entity);
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!player.getHeldItem(hand).isEmpty()) return false;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof CrushingWheelControllerTileEntity controller)) return false;
        if (!world.isRemote) controller.giveItemsTo(player);
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof CrushingWheelControllerTileEntity) {
            ((CrushingWheelControllerTileEntity) tile).dropItems(world, pos);
        }
        super.breakBlock(world, pos, state);
    }
}
