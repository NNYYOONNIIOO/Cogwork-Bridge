package com.cogwork.cogworkbridge.content.kinetics.fan;

import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

public final class NozzleTileEntity extends TileEntity implements ITickable {
    @Override
    public void update() {
        if (world == null || world instanceof FakeWorld) {
            return;
        }
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof NozzleBlock)) {
            return;
        }
        EnumFacing facing = state.getValue(NozzleBlock.FACING);
        TileEntity source = world.getTileEntity(pos.offset(facing.getOpposite()));
        if (!(source instanceof EncasedFanTileEntity fan) || fan.getSpeed() == 0) {
            return;
        }
        float range = AirFlow.range(fan.getSpeed());
        boolean pushing = AirFlow.pushing(fan.getSpeed(), facing);
        Vec3d center = new Vec3d(pos).add(0.5D, 0.5D, 0.5D);
        if (world.isRemote) {
            if (world.rand.nextInt(8) == 0) {
                Vec3d offset = new Vec3d(world.rand.nextDouble() - 0.5D,
                        world.rand.nextDouble() - 0.5D, world.rand.nextDouble() - 0.5D);
                Vec3d velocity = offset.normalize().scale(pushing ? 0.08D : -0.08D);
                world.spawnParticle(EnumParticleTypes.CLOUD, center.x + offset.x, center.y + offset.y,
                        center.z + offset.z, velocity.x, velocity.y, velocity.z);
            }
            return;
        }
        double radius = range / 2.0D;
        AxisAlignedBB box = new AxisAlignedBB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        for (Entity entity : world.getEntitiesWithinAABBExcludingEntity(null, box)) {
            if (!AirFlow.affected(entity) || entity.isSneaking()) {
                continue;
            }
            Vec3d delta = entity.getPositionVector().subtract(center);
            double distance = delta.length();
            if (distance <= 1.0E-5D || distance > range || !pushing && distance < 1.5D) {
                continue;
            }
            RayTraceResult hit = world.rayTraceBlocks(entity.getPositionVector(), center, false, true, false);
            if (hit != null && !pos.equals(hit.getBlockPos())) {
                continue;
            }
            double factor = (range - distance) * (pushing ? 1 : -1)
                    / (entity instanceof EntityItem ? 128.0D : 32.0D);
            Vec3d force = delta.scale(factor / distance);
            AirFlow.applyVelocity(entity, force.x, force.y, force.z);
            entity.fallDistance = 0;
        }
    }
}
