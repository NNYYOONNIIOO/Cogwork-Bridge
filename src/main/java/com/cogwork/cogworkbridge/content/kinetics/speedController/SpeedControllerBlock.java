package com.cogwork.cogworkbridge.content.kinetics.speedController;

import com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelBlock;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/** Horizontal-axis drive controller, coupled to a large cog directly above. */
public final class SpeedControllerBlock extends KineticBlock {
    private static final AxisAlignedBB OUTLINE = new AxisAlignedBB(0, 0, 0, 1, 14.0D / 16.0D, 1);
    private static final AxisAlignedBB BASE = new AxisAlignedBB(0, 0, 0, 1, 4.0D / 16.0D, 1);
    private static final AxisAlignedBB CENTER = new AxisAlignedBB(1.0D / 16.0D, 1.0D / 16.0D, 1.0D / 16.0D,
            15.0D / 16.0D, 13.0D / 16.0D, 15.0D / 16.0D);
    private static final AxisAlignedBB TOP = new AxisAlignedBB(0, 8.0D / 16.0D, 0, 1, 14.0D / 16.0D, 1);

    public SpeedControllerBlock() {
        super(Material.WOOD);
        setHardness(2.0F);
        setResistance(6.0F);
        setDefaultState(getDefaultState().withProperty(AXIS, EnumFacing.Axis.X));
    }

    @Override
    public boolean hasAxis() {
        return true;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new SpeedControllerTileEntity();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        IBlockState above = world.getBlockState(pos.up());
        if (above.getBlock() instanceof CogwheelBlock cog && cog.isLarge()) {
            EnumFacing.Axis cogAxis = cog.getAxis(above);
            if (cogAxis != EnumFacing.Axis.Y) {
                return getDefaultState().withProperty(AXIS, perpendicular(cogAxis));
            }
        }

        EnumFacing.Axis preferred = null;
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            BlockPos neighbourPos = pos.offset(side);
            IBlockState neighbour = world.getBlockState(neighbourPos);
            if (!(neighbour.getBlock() instanceof KineticBlock kinetic)
                    || !kinetic.canConnectShaftTowards(world, neighbourPos, neighbour, side.getOpposite())) {
                continue;
            }
            if (preferred != null && preferred != side.getAxis()) {
                preferred = null;
                break;
            }
            preferred = side.getAxis();
        }
        if (preferred == null) {
            preferred = placer.getHorizontalFacing().rotateY().getAxis();
        }
        return getDefaultState().withProperty(AXIS, preferred);
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return state.withProperty(AXIS, perpendicular(getAxis(state)));
        }
        return state;
    }

    @Override
    public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        return side.getAxis().isHorizontal() && side.getAxis() == getAxis(state);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return OUTLINE;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
                                      AxisAlignedBB mask, List<AxisAlignedBB> boxes,
                                      @Nullable Entity entity, boolean actualState) {
        addCollisionBoxToList(pos, mask, boxes, BASE);
        addCollisionBoxToList(pos, mask, boxes, CENTER);
        addCollisionBoxToList(pos, mask, boxes, TOP);
    }

    private static EnumFacing.Axis perpendicular(EnumFacing.Axis axis) {
        return axis == EnumFacing.Axis.X ? EnumFacing.Axis.Z : EnumFacing.Axis.X;
    }
}
