package com.cogwork.cogworkbridge.content.kinetics.chain;

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

/** A shaft coupled to a single line of adjacent chain drives. */
public final class ChainDriveBlock extends KineticBlock {
    public static final IUnlistedProperty<Integer> MODEL = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "chain_model"; }
        @Override public boolean isValid(Integer value) { return value != null && value >= 0 && value < 32; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };

    private final boolean adjustable;

    public ChainDriveBlock(boolean adjustable) {
        super(Material.WOOD);
        this.adjustable = adjustable;
        setHardness(2.0F);
        setResistance(6.0F);
        setDefaultState(getDefaultState().withProperty(AXIS, EnumFacing.Axis.Y));
    }

    public boolean isAdjustable() {
        return adjustable;
    }

    @Override public boolean hasAxis() { return true; }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{AXIS}, new IUnlistedProperty<?>[]{MODEL});
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        int model = tile instanceof ChainDriveTileEntity chain ? chain.getModelState() : 0;
        return ((IExtendedBlockState) state).withProperty(MODEL, model);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face,
                                           float hitX, float hitY, float hitZ, int meta,
                                           EntityLivingBase placer) {
        EnumFacing.Axis preferred = null;
        if (!placer.isSneaking()) {
            for (EnumFacing side : EnumFacing.VALUES) {
                BlockPos otherPos = pos.offset(side);
                IBlockState other = world.getBlockState(otherPos);
                if (!(other.getBlock() instanceof KineticBlock block)
                        || !block.canConnectShaftTowards(world, otherPos, other, side.getOpposite())) {
                    continue;
                }
                if (preferred != null && preferred != side.getAxis()) {
                    preferred = null;
                    break;
                }
                preferred = side.getAxis();
            }
        }
        return getDefaultState().withProperty(AXIS,
                preferred == null ? getPlacementAxis(placer) : preferred);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new ChainDriveTileEntity();
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block neighbour, BlockPos fromPos) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof ChainDriveTileEntity tile) {
            tile.refreshConnections();
        }
    }

    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
}
