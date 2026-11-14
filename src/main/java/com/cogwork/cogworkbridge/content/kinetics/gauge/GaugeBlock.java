package com.cogwork.cogworkbridge.content.kinetics.gauge;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The direction of the dial and the shaft axis are both orientation metadata. */
public final class GaugeBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final PropertyBool AXIS_ALONG_FIRST = PropertyBool.create("axis_along_first");
    private final boolean stress;

    public GaugeBlock(boolean stress) {
        super(Material.WOOD);
        this.stress = stress;
        setHardness(2F);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.UP).withProperty(AXIS_ALONG_FIRST, false));
    }

    public boolean isStressGauge() { return stress; }
    @Override public boolean hasAxis() { return true; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, AXIS_ALONG_FIRST); }
    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex() + (state.getValue(AXIS_ALONG_FIRST) ? 6 : 0);
    }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(Math.floorMod(meta, 6)))
                .withProperty(AXIS_ALONG_FIRST, meta >= 6);
    }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y,
                                                       float z, int meta, EntityLivingBase placer) {
        EnumFacing preferred = placer.isSneaking() ? null : getPreferredShaftFacing(world, pos);
        EnumFacing.Axis axis = preferred == null ? getPlacementAxis(placer) : preferred.getAxis();
        EnumFacing face = side;
        if (face.getAxis() == axis) {
            face = placer.getHorizontalFacing().getOpposite();
            if (face.getAxis() == axis) face = face.rotateY();
        }
        return getDefaultState().withProperty(FACING, face).withProperty(AXIS_ALONG_FIRST, firstAxis(face) == axis);
    }
    private static EnumFacing.Axis firstAxis(EnumFacing facing) {
        return facing.getAxis() == EnumFacing.Axis.X ? EnumFacing.Axis.Y : EnumFacing.Axis.X;
    }
    @Override public EnumFacing.Axis getAxis(IBlockState state) {
        EnumFacing.Axis face = state.getValue(FACING).getAxis();
        if (state.getValue(AXIS_ALONG_FIRST)) return firstAxis(state.getValue(FACING));
        return face == EnumFacing.Axis.Z ? EnumFacing.Axis.Y : EnumFacing.Axis.Z;
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        EnumFacing.Axis axis = rotation.rotate(EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, getAxis(state))).getAxis();
        EnumFacing face = rotation.rotate(state.getValue(FACING));
        return state.withProperty(FACING, face).withProperty(AXIS_ALONG_FIRST, firstAxis(face) == axis);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new GaugeTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof GaugeTileEntity gauge ? gauge.getComparatorValue() : 0;
    }
    private static final com.cogwork.cogworkbridge.util.BlockShape GAUGE_SHAPE = com.cogwork.cogworkbridge.util.BlockShape.pixels(1,0,0,15,2,16, 2,2,1,14,14,15);
    private static final java.util.Map<net.minecraft.util.EnumFacing, com.cogwork.cogworkbridge.util.BlockShape> COLLISION = GAUGE_SHAPE.fromUp();
    private static final java.util.Map<net.minecraft.util.EnumFacing, com.cogwork.cogworkbridge.util.BlockShape> COLLISION_FIRST = GAUGE_SHAPE.rotate(net.minecraft.util.EnumFacing.Axis.Y, 1).fromUp();

    private static com.cogwork.cogworkbridge.util.BlockShape collisionShape(net.minecraft.block.state.IBlockState state) {
        boolean first = state.getValue(AXIS_ALONG_FIRST) ^ (state.getValue(FACING).getAxis() == EnumFacing.Axis.X);
        return (first ? COLLISION_FIRST : COLLISION).get(state.getValue(FACING));
    }
    @Override public void addCollisionBoxToList(net.minecraft.block.state.IBlockState state, net.minecraft.world.World world,
            net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.AxisAlignedBB query, java.util.List<net.minecraft.util.math.AxisAlignedBB> boxes,
            @javax.annotation.Nullable net.minecraft.entity.Entity entity, boolean actual) { collisionShape(state).addCollisionBoxes(pos, query, boxes); }

    @Override public net.minecraft.util.math.AxisAlignedBB getBoundingBox(net.minecraft.block.state.IBlockState state,
            net.minecraft.world.IBlockAccess world, net.minecraft.util.math.BlockPos pos) { return collisionShape(state).bounds(); }
    @Override @javax.annotation.Nullable public net.minecraft.util.math.RayTraceResult collisionRayTrace(net.minecraft.block.state.IBlockState state,
            net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.Vec3d start,
            net.minecraft.util.math.Vec3d end) { return collisionShape(state).rayTrace(pos, start, end); }
}
