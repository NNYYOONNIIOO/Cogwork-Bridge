package com.cogwork.cogworkbridge.content.kinetics.flywheel;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class FlywheelBlock extends KineticBlock {
    public FlywheelBlock() { super(Material.IRON); setHardness(2F); setDefaultState(getDefaultState().withProperty(AXIS, EnumFacing.Axis.Y)); }
    @Override public boolean hasAxis() { return true; }
    @Override public IBlockState getStateForPlacement(World w, BlockPos p, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        EnumFacing preferred = placer.isSneaking() ? null : getPreferredShaftFacing(w, p);
        return getDefaultState().withProperty(AXIS, preferred == null ? getPlacementAxis(placer) : preferred.getAxis());
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new FlywheelTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.ENTITYBLOCK_ANIMATED; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return shape(state).bounds();
    }
    private static final java.util.Map<EnumFacing, com.cogwork.cogworkbridge.util.BlockShape> LARGE_SHAPE = com.cogwork.cogworkbridge.util.BlockShape.pixels(0,6,0,16,10,16, 5,0,5,11,16,11).fromUp();
    private com.cogwork.cogworkbridge.util.BlockShape shape(IBlockState state) {
        return LARGE_SHAPE.get(EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, state.getValue(AXIS)));
    }
    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
            java.util.List<AxisAlignedBB> boxes, @javax.annotation.Nullable net.minecraft.entity.Entity entity, boolean actual) {
        shape(state).addCollisionBoxes(pos, query, boxes);
    }
    @Override @javax.annotation.Nullable public net.minecraft.util.math.RayTraceResult collisionRayTrace(IBlockState state, World world,
            BlockPos pos, net.minecraft.util.math.Vec3d start, net.minecraft.util.math.Vec3d end) { return shape(state).rayTrace(pos, start, end); }
}
