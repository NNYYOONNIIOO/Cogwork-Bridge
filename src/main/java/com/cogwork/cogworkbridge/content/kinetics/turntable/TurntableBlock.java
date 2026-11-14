package com.cogwork.cogworkbridge.content.kinetics.turntable;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public final class TurntableBlock extends KineticBlock {
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(1/16D, 0, 1/16D, 15/16D, .5, 15/16D);
    private static final AxisAlignedBB TABLE = new AxisAlignedBB(1/16D, 4/16D, 1/16D, 15/16D, 8/16D, 15/16D);
    private static final AxisAlignedBB SHAFT = new AxisAlignedBB(5/16D, 0, 5/16D, 11/16D, 4/16D, 11/16D);
    public TurntableBlock() { super(Material.WOOD); setHardness(1F); }
    @Override public boolean hasAxis() { return false; }
    @Override public EnumFacing.Axis getAxis(IBlockState state) { return EnumFacing.Axis.Y; }
    @Override public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) { return side == EnumFacing.DOWN; }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new TurntableTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.ENTITYBLOCK_ANIMATED; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return SHAPE; }
    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB mask,
                                                 List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        addCollisionBoxToList(pos, mask, boxes, TABLE);
        addCollisionBoxToList(pos, mask, boxes, SHAFT);
    }
    @Override @Nullable public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos, Vec3d start, Vec3d end) {
        RayTraceResult tableHit = rayTrace(pos, start, end, TABLE);
        RayTraceResult shaftHit = rayTrace(pos, start, end, SHAFT);
        return shaftHit != null && (tableHit == null || shaftHit.hitVec.squareDistanceTo(start) < tableHit.hitVec.squareDistanceTo(start))
                ? shaftHit : tableHit;
    }
    @Override public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (!entity.onGround || entity.motionY > 0 || entity.posY < pos.getY() + .5 || !(world.getTileEntity(pos) instanceof TurntableTileEntity tile)) return;
        float degrees = tile.getSpeed() * .3F;
        if (degrees == 0) return;
        if (entity instanceof EntityPlayer) {
            if (!world.isRemote || world.getBlockState(entity.getPosition()) == state) return;
            double a = Math.toRadians(Math.max(-16F, Math.min(16F, degrees)));
            double dx = entity.posX - pos.getX() - .5, dz = entity.posZ - pos.getZ() - .5;
            entity.motionX += dx * Math.cos(a) + dz * Math.sin(a) - dx;
            entity.motionZ += dz * Math.cos(a) - dx * Math.sin(a) - dz;
            entity.velocityChanged = true;
            return;
        }
        if (world.isRemote) return;
        entity.rotationYaw -= degrees;
        if (entity instanceof EntityLivingBase living) {
            float headYaw = living.rotationYawHead - degrees;
            living.rotationYawHead = headYaw; living.renderYawOffset = headYaw;
            living.onGround = false;
        }
        entity.velocityChanged = true;
    }
}
