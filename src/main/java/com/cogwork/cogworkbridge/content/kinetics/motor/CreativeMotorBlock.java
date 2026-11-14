package com.cogwork.cogworkbridge.content.kinetics.motor;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Creative motor: an infinite rotation source for testing and for the first
 * playable build. Speed is fixed at 32 RPM and can be flipped with a wrench later.
 */
public class CreativeMotorBlock extends KineticBlock {

    /** Create's motor is directional: the shaft exits through the facing side. */
    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    public CreativeMotorBlock() {
        super(Material.ROCK, "cogworkbridge.creative_motor");
        setHardness(0.8F);
        setResistance(10.0F);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public boolean hasAxis() {
        return false;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing[] values = EnumFacing.values();
        return getDefaultState().withProperty(FACING, values[Math.floorMod(meta, values.length)]);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).ordinal();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        EnumFacing preferred = placer.isSneaking() ? null : getPreferredShaftFacing(world, pos);
        return getDefaultState().withProperty(FACING, preferred != null
                ? preferred : getPlacementFacing(placer, placer.isSneaking()));
    }

    @Override
    public EnumFacing.Axis getAxis(IBlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        return side == state.getValue(FACING);
    }

    @Override
    public EnumFacing getShaftFacing(World world, BlockPos pos, IBlockState state) {
        return state.getValue(FACING);
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new CreativeMotorTileEntity();
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }
    private static final java.util.Map<net.minecraft.util.EnumFacing, com.cogwork.cogworkbridge.util.BlockShape> COLLISION = com.cogwork.cogworkbridge.util.BlockShape.pixels(3,0,3,13,14,13).fromUp();

    private static com.cogwork.cogworkbridge.util.BlockShape collisionShape(net.minecraft.block.state.IBlockState state) { return COLLISION.get(state.getValue(FACING)); }
    @Override public void addCollisionBoxToList(net.minecraft.block.state.IBlockState state, net.minecraft.world.World world,
            net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.AxisAlignedBB query, java.util.List<net.minecraft.util.math.AxisAlignedBB> boxes,
            @javax.annotation.Nullable net.minecraft.entity.Entity entity, boolean actual) { collisionShape(state).addCollisionBoxes(pos, query, boxes); }

    @Override public net.minecraft.util.math.AxisAlignedBB getBoundingBox(net.minecraft.block.state.IBlockState state,
            net.minecraft.world.IBlockAccess world, net.minecraft.util.math.BlockPos pos) { return collisionShape(state).bounds(); }
    @Override @javax.annotation.Nullable public net.minecraft.util.math.RayTraceResult collisionRayTrace(net.minecraft.block.state.IBlockState state,
            net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.Vec3d start,
            net.minecraft.util.math.Vec3d end) { return collisionShape(state).rayTrace(pos, start, end); }
}
