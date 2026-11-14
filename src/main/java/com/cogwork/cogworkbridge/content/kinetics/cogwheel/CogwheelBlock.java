package com.cogwork.cogworkbridge.content.kinetics.cogwheel;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.content.kinetics.placement.KineticPlacementContext;
import com.cogwork.cogworkbridge.content.kinetics.placement.KineticPlacementRules;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A cogwheel meshes with neighbours perpendicular to its axis.
 *
 * <p>Small and large cogwheels are <em>separate registry names</em>; the size is
 * never encoded in metadata. Only the axis is.
 */
public class CogwheelBlock extends KineticBlock {

    private final boolean large;

    public CogwheelBlock(boolean large) {
        super(Material.WOOD, large ? "cogworkbridge.large_cogwheel" : "cogworkbridge.cogwheel");
        this.large = large;
        setHardness(0.5F);
        setResistance(4.0F);
        setDefaultState(getDefaultState().withProperty(AXIS, EnumFacing.Axis.Y));
    }

    public boolean isLarge() {
        return large;
    }

    @Override
    public boolean hasAxis() {
        return true;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            net.minecraft.entity.EntityLivingBase placer) {
        EnumFacing.Axis forced = KineticPlacementContext.getForcedAxis();
        return getDefaultState().withProperty(AXIS, forced != null ? forced
                : KineticPlacementRules.placementAxis(world, pos, facing, placer, this));
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new CogwheelTileEntity();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing face, float hitX, float hitY, float hitZ) {
        return com.cogwork.cogworkbridge.content.kinetics.encased.Encasing
                .tryApply(world, pos, state, player, hand);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return shape(state).bounds();
    }
    private static final java.util.Map<EnumFacing, com.cogwork.cogworkbridge.util.BlockShape> LARGE_SHAPE = com.cogwork.cogworkbridge.util.BlockShape.pixels(0,6,0,16,10,16, 5,0,5,11,16,11).fromUp();
    private static final java.util.Map<EnumFacing, com.cogwork.cogworkbridge.util.BlockShape> SMALL_SHAPE = com.cogwork.cogworkbridge.util.BlockShape.pixels(2,6,2,14,10,14, 5,0,5,11,16,11).fromUp();
    private com.cogwork.cogworkbridge.util.BlockShape shape(IBlockState state) {
        return (large ? LARGE_SHAPE : SMALL_SHAPE).get(EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, state.getValue(AXIS)));
    }
    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
            java.util.List<AxisAlignedBB> boxes, @javax.annotation.Nullable net.minecraft.entity.Entity entity, boolean actual) {
        shape(state).addCollisionBoxes(pos, query, boxes);
    }
    @Override @javax.annotation.Nullable public net.minecraft.util.math.RayTraceResult collisionRayTrace(IBlockState state, World world,
            BlockPos pos, net.minecraft.util.math.Vec3d start, net.minecraft.util.math.Vec3d end) { return shape(state).rayTrace(pos, start, end); }
}
