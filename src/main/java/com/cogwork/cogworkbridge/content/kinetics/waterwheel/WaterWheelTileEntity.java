package com.cogwork.cogworkbridge.content.kinetics.waterwheel;

import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.kinetic.Rotation;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Flow sampling and material state for the standard one-block water wheel. */
public final class WaterWheelTileEntity extends KineticTileEntity {
    public static final float GENERATED_SPEED = 8F;
    private int flowScore;
    private int wood = 1;

    public int getWood() { return wood; }
    public void setWood(int value) {
        int next = Math.max(0, Math.min(7, value));
        if (wood != next) { wood = next; markDirty(); sync(); }
    }

    @Override public boolean isSource() { return true; }
    @Override protected Rotation computeSourceRotation() { return Rotation.of(getAxis(), Integer.signum(flowScore) * GENERATED_SPEED); }
    @Override public float getStressCapacity() { return 32F; }

    @Override public void update() {
        if (world != null && !world.isRemote && world.getTotalWorldTime() % 20L == 0L) updateFlow();
        super.update();
    }

    public void updateFlow() {
        if (world == null) return;
        int score = 0;
        EnumFacing.Axis axis = getAxis();
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            if (Math.abs(a) + Math.abs(b) != 1) continue;
            BlockPos offset = WaterWheelBlock.offset(axis, a, b);
            BlockPos target = pos.add(offset);
            if (!world.isBlockLoaded(target)) continue;
            IBlockState fluid = world.getBlockState(target);
            Vec3d flow;
            if (fluid.getBlock() instanceof BlockLiquid liquid)
                flow = liquid.modifyAcceleration(world, target, null, Vec3d.ZERO);
            else if (fluid.getBlock() instanceof net.minecraftforge.fluids.BlockFluidBase forgeFluid)
                flow = forgeFluid.getFlowVector(world, target);
            else continue;
            if (flow.lengthSquared() < 1.0E-8D) continue;
            Vec3d radial = new Vec3d(offset).normalize();
            Vec3d tangent = switch (axis) {
                case X -> new Vec3d(0, -radial.z, radial.y);
                case Y -> new Vec3d(radial.z, 0, -radial.x);
                case Z -> new Vec3d(-radial.y, radial.x, 0);
            };
            Vec3d plane = switch (axis) {
                case X -> new Vec3d(0, flow.y, flow.z);
                case Y -> new Vec3d(flow.x, 0, flow.z);
                case Z -> new Vec3d(flow.x, flow.y, 0);
            };
            if (plane.lengthSquared() > 1.0E-8D && plane.normalize().dotProduct(tangent) > .5D) score++;
            else if (plane.lengthSquared() > 1.0E-8D && plane.normalize().dotProduct(tangent) < -.5D) score--;
        }
        if (score != flowScore) {
            flowScore = score;
            KineticNetworkManager.discard(world);
            markDirty();
            sync();
        }
    }

    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos).grow(1); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setInteger("FlowScore", flowScore); tag.setInteger("Wood", wood); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); flowScore = tag.getInteger("FlowScore"); wood = Math.max(0, Math.min(7, tag.hasKey("Wood") ? tag.getInteger("Wood") : 1));
    }
    @Override public NBTTagCompound getUpdateTag() { NBTTagCompound tag = super.getUpdateTag(); tag.setInteger("Wood", wood); return tag; }
    @Override public void handleUpdateTag(NBTTagCompound tag) { super.handleUpdateTag(tag); wood = Math.max(0, Math.min(7, tag.hasKey("Wood") ? tag.getInteger("Wood") : 1)); }
}
