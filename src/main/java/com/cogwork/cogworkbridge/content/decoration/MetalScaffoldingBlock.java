package com.cogwork.cogworkbridge.content.decoration;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public final class MetalScaffoldingBlock extends Block {
    public static final PropertyBool BOTTOM = PropertyBool.create("bottom");
    private static final AxisAlignedBB TOP = new AxisAlignedBB(0, .5D, 0, 1, 1, 1);
    private static final AxisAlignedBB WALKABLE_TOP = new AxisAlignedBB(0, .875D, 0, 1, 1, 1);
    private static final AxisAlignedBB[] LEGS = {
            new AxisAlignedBB(0, 0, 0, .125D, 1, .125D),
            new AxisAlignedBB(.875D, 0, 0, 1, 1, .125D),
            new AxisAlignedBB(0, 0, .875D, .125D, 1, 1),
            new AxisAlignedBB(.875D, 0, .875D, 1, 1, 1)
    };

    public MetalScaffoldingBlock() {
        super(Material.IRON);
        setHardness(2F);
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState().withProperty(BOTTOM, false));
    }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, BOTTOM); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState(); }
    @Override public int getMetaFromState(IBlockState state) { return 0; }
    @Override public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        IBlockState below = world.getBlockState(pos.down());
        return state.withProperty(BOTTOM, below.getBlock() != this
                && !below.isSideSolid(world, pos.down(), net.minecraft.util.EnumFacing.UP));
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean isLadder(IBlockState state, IBlockAccess world, BlockPos pos, EntityLivingBase entity) {
        // Vanilla 1.12 stops a sneaking player on ladders; scaffolding instead lets them descend.
        return !(entity instanceof EntityPlayer && entity.isSneaking());
    }
    @Override public boolean isPassable(IBlockAccess world, BlockPos pos) {
        return !getActualState(world.getBlockState(pos), world, pos).getValue(BOTTOM);
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return getActualState(state, world, pos).getValue(BOTTOM) ? TOP : FULL_BLOCK_AABB;
    }
    @Nullable @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return getActualState(state, world, pos).getValue(BOTTOM) ? TOP : NULL_AABB;
    }
    @Nullable @Override public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos,
                                                                Vec3d start, Vec3d end) {
        if (getActualState(state, world, pos).getValue(BOTTOM)) return rayTrace(pos, start, end, TOP);
        if (world.isRemote && com.cogwork.cogworkbridge.client.ScaffoldingClientEvents.isHolding(this))
            return rayTrace(pos, start, end, FULL_BLOCK_AABB);
        RayTraceResult nearest = rayTrace(pos, start, end, TOP);
        double distance = nearest == null ? Double.POSITIVE_INFINITY : nearest.hitVec.squareDistanceTo(start);
        for (AxisAlignedBB leg : LEGS) {
            RayTraceResult hit = rayTrace(pos, start, end, leg);
            if (hit != null && hit.hitVec.squareDistanceTo(start) < distance) {
                nearest = hit;
                distance = hit.hitVec.squareDistanceTo(start);
            }
        }
        return nearest;
    }
    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        boolean bottom = getActualState(state, world, pos).getValue(BOTTOM);
        if (bottom) {
            addCollisionBoxToList(pos, entityBox, boxes, TOP);
        } else if (entity == null || (!entity.isSneaking() && entity.getEntityBoundingBox().minY >= pos.getY() + 1D - 1.0E-5D)) {
            addCollisionBoxToList(pos, entityBox, boxes, WALKABLE_TOP);
        }
    }
    @Override public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (!(entity instanceof EntityLivingBase) || entity instanceof EntityPlayer player
                && (player.isSpectator() || player.capabilities.isFlying)) return;
        if (entity.getEntityBoundingBox().minY >= pos.getY() && entity.getEntityBoundingBox().minY < pos.getY() + 1D) {
            entity.fallDistance = 0;
            if (entity instanceof EntityPlayer && entity.isSneaking()) entity.motionY = Math.max(entity.motionY, -.15D);
        }
    }
}
