package com.cogwork.cogworkbridge.content.kinetics.crank;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** A hand-operated source mounted against the face behind its handle. */
public class HandCrankBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    public HandCrankBlock() {
        super(Material.IRON);
        setHardness(1.0F);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.UP));
    }
    @Override public boolean hasAxis() { return false; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta)); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public EnumFacing.Axis getAxis(IBlockState state) { return state.getValue(FACING).getAxis(); }
    @Override public EnumFacing getShaftFacing(World world, BlockPos pos, IBlockState state) { return state.getValue(FACING).getOpposite(); }
    @Override public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) { return side == getShaftFacing(world, pos, state); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float x, float y, float z, int meta, EntityLivingBase placer) {
        EnumFacing preferred = placer.isSneaking() ? null : getPreferredShaftFacing(world, pos);
        return getDefaultState().withProperty(FACING, preferred == null ? side : preferred.getOpposite());
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new HandCrankTileEntity(); }
    @Override public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        BlockPos support = pos.offset(side.getOpposite());
        return super.canPlaceBlockAt(world, pos) && world.getBlockState(support).getCollisionBoundingBox(world, support) != null;
    }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.ENTITYBLOCK_ANIMATED; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return new AxisAlignedBB(.125, .125, .125, .875, .875, .875);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.isSpectator()) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof HandCrankTileEntity crank) {
            crank.turn(player.isSneaking());
            if (!(crank instanceof ValveHandleTileEntity)) player.addExhaustion(.32F);
        }
        return true;
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, net.minecraft.block.Block block, BlockPos from) {
        if (world.isRemote || !pos.offset(state.getValue(FACING).getOpposite()).equals(from)) return;
        BlockPos support = pos.offset(state.getValue(FACING).getOpposite());
        IBlockState other = world.getBlockState(support);
        if (other.getCollisionBoundingBox(world, support) == null) world.destroyBlock(pos, true);
    }
}
