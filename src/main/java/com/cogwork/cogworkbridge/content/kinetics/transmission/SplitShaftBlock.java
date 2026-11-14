package com.cogwork.cogworkbridge.content.kinetics.transmission;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

/** A straight shaft with redstone-controlled clutch or reversing behavior. */
public final class SplitShaftBlock extends KineticBlock {

    public static final IUnlistedProperty<Boolean> POWERED = new IUnlistedProperty<Boolean>() {
        @Override public String getName() { return "powered"; }
        @Override public boolean isValid(Boolean value) { return value != null; }
        @Override public Class<Boolean> getType() { return Boolean.class; }
        @Override public String valueToString(Boolean value) { return value.toString(); }
    };

    private final boolean clutch;

    public SplitShaftBlock(boolean clutch) {
        super(Material.WOOD);
        this.clutch = clutch;
        setHardness(2.0F);
        setResistance(6.0F);
        setDefaultState(getDefaultState().withProperty(AXIS, EnumFacing.Axis.Y));
    }

    public boolean isClutch() {
        return clutch;
    }

    @Override
    public boolean hasAxis() {
        return true;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{AXIS},
                new IUnlistedProperty<?>[]{POWERED});
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        boolean powered = tile instanceof SplitShaftTileEntity split && split.isPowered();
        return ((IExtendedBlockState) state).withProperty(POWERED, powered);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new SplitShaftTileEntity();
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        EnumFacing preferred = placer.isSneaking() ? null : getPreferredShaftFacing(world, pos);
        return getDefaultState().withProperty(AXIS,
                preferred != null ? preferred.getAxis() : getPlacementAxis(placer));
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        updatePower(world, pos);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block neighbour,
                                BlockPos neighbourPos) {
        updatePower(world, pos);
    }

    private static void updatePower(World world, BlockPos pos) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof SplitShaftTileEntity tile) {
            tile.updateRedstone();
        }
    }

    @Override
    public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        if (side.getAxis() != getAxis(state)) {
            return false;
        }
        TileEntity tile = world.getTileEntity(pos);
        return !clutch || !(tile instanceof SplitShaftTileEntity split) || !split.isPowered();
    }

    @Override
    public double shaftPortModifier(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        TileEntity tile = world.getTileEntity(pos);
        boolean reverse = !clutch && tile instanceof SplitShaftTileEntity split && split.isPowered();
        return reverse && side.getAxisDirection() == EnumFacing.AxisDirection.NEGATIVE ? -1.0D : 1.0D;
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
}
