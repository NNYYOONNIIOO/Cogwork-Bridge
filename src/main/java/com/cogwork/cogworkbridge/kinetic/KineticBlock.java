package com.cogwork.cogworkbridge.kinetic;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Base class for every block that participates in a kinetic network.
 *
 * <p>Orientation is the <em>only</em> thing stored in metadata. A kinetic block's
 * axis (or facing) is the sole property; everything else lives in its tile entity.
 */
public abstract class KineticBlock extends Block implements ITileEntityProvider {

    public static final PropertyEnum<EnumFacing.Axis> AXIS = PropertyEnum.create("axis", EnumFacing.Axis.class);

    public KineticBlock(Material material) {
        super(material);
    }

    public KineticBlock(Material material, String translationKey) {
        super(material);
        setTranslationKey(translationKey);
    }

    /**
     * True when this block should rotate around the given axis.
     */
    public abstract boolean hasAxis();

    /**
     * Kinetic blocks use the ordinary model path by default. Blocks whose complete
     * shape rotates override this with {@code ENTITYBLOCK_ANIMATED}; blocks with a
     * static housing can keep MODEL and let their TESR draw only a moving sub-part.
     */
    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return hasAxis()
                ? new BlockStateContainer(this, AXIS)
                : new BlockStateContainer(this);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        if (!hasAxis()) {
            return getDefaultState();
        }
        EnumFacing.Axis[] values = EnumFacing.Axis.values();
        int index = meta % values.length;
        return getDefaultState().withProperty(AXIS, values[index]);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        if (!hasAxis() || !state.getPropertyKeys().contains(AXIS)) {
            return 0;
        }
        return state.getValue(AXIS).ordinal();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        return getDefaultState();
    }

    /**
     * Uses the player's view for orientation while retaining the ability to face
     * upward or downward. Horizontal device axes are relative to the player's facing,
     * not the clicked face.
     */
    public static EnumFacing getPlacementFacing(EntityLivingBase placer) {
        Vec3d look = placer.getLookVec();
        return EnumFacing.getFacingFromVector((float) look.x, (float) look.y, (float) look.z);
    }

    public static EnumFacing getPlacementFacing(EntityLivingBase placer, boolean sneaking) {
        EnumFacing facing = getPlacementFacing(placer);
        return sneaking ? facing.getOpposite() : facing;
    }

    public static EnumFacing.Axis getPlacementAxis(EntityLivingBase placer) {
        return getPlacementFacing(placer).getAxis();
    }

    public static EnumFacing.Axis getPlacementAxis(EntityLivingBase placer, boolean sneaking) {
        return getPlacementFacing(placer, sneaking).getAxis();
    }

    public static EnumFacing getPreferredShaftFacing(World world, BlockPos pos) {
        EnumFacing preferred = null;
        for (EnumFacing side : EnumFacing.VALUES) {
            BlockPos neighbourPos = pos.offset(side);
            if (!world.isBlockLoaded(neighbourPos)) {
                continue;
            }
            IBlockState neighbour = world.getBlockState(neighbourPos);
            if (!(neighbour.getBlock() instanceof KineticBlock kinetic)
                    || !kinetic.canConnectShaftTowards(world, neighbourPos,
                            neighbour, side.getOpposite())) {
                continue;
            }
            if (preferred != null && preferred.getAxis() != side.getAxis()) {
                return null;
            }
            preferred = side;
        }
        return preferred;
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        if (!hasAxis() || !state.getPropertyKeys().contains(AXIS)) {
            return state;
        }
        EnumFacing.Axis axis = state.getValue(AXIS);
        switch (rot) {
            case CLOCKWISE_90:
            case CLOCKWISE_180:
            case COUNTERCLOCKWISE_90:
                if (axis == EnumFacing.Axis.X) {
                    return state.withProperty(AXIS, EnumFacing.Axis.Z);
                }
                if (axis == EnumFacing.Axis.Z) {
                    return state.withProperty(AXIS, EnumFacing.Axis.X);
                }
                return state;
            default:
                return state;
        }
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return null;
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state;
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof KineticTileEntity) {
                ((KineticTileEntity) tile).refreshKinetics();
            }
        }
    }

    /** Utility: the axis encoded in this block's state, defaulting to Y. */
    public EnumFacing.Axis getAxis(IBlockState state) {
        if (hasAxis() && state.getPropertyKeys().contains(AXIS)) {
            return state.getValue(AXIS);
        }
        return EnumFacing.Axis.Y;
    }

    /** The faces where this block exposes an axial power socket. */
    public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        return hasAxis() && side.getAxis() == getAxis(state);
    }

    /** Converts the tile's reference speed to the speed at a shaft port. */
    public double shaftPortModifier(IBlockState state, EnumFacing side) {
        return 1.0D;
    }

    public double shaftPortModifier(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        return shaftPortModifier(state, side);
    }

    /**
     * Returns the exact signed face of this block's axial port, or null when the
     * port is bidirectional. Comparing only {@link EnumFacing.Axis} loses the
     * positive/negative direction and incorrectly lets a directional source
     * drive through its back face.
     */
    public EnumFacing getShaftFacing(World world, BlockPos pos, IBlockState state) {
        return null;
    }

    /**
     * Tests an axial port with its sign. Directional blocks override the exact
     * face; bidirectional shafts remain valid on both ends of their axis.
     */
    public boolean canConnectShaftTowards(World world, BlockPos pos, IBlockState state,
                                          EnumFacing side) {
        EnumFacing facing = getShaftFacing(world, pos, state);
        return facing != null
                ? side == facing
                : hasShaftTowards(world, pos, state, side);
    }
}




