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

public final class LargeWaterWheelTileEntity extends KineticTileEntity {
    public static final float GENERATED_SPEED = 4F;
    private int flowScore;
    private int wood = 1;
    public int getWood() { return wood; }
    public void setWood(int value) {
        int next = Math.max(0, Math.min(7, value));
        if (wood != next) { wood = next; markDirty(); sync(); }
    }
    @Override public boolean isSource() { return true; }
    @Override protected Rotation computeSourceRotation() { return Rotation.of(getAxis(), Integer.signum(flowScore) * GENERATED_SPEED); }
    @Override public float getStressCapacity() { return 128F; }
    @Override public void update() {
        if (world != null && !world.isRemote && world.getTotalWorldTime() % 20 == 0) updateFlow();
        super.update();
    }
    private void updateFlow() {
        int score = 0;
        EnumFacing.Axis axis = getAxis();
        for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) {
            if (Math.max(Math.abs(a), Math.abs(b)) != 2 || Math.abs(a) == 2 && Math.abs(b) == 2) continue;
            BlockPos offset = LargeWaterWheelBlock.offset(axis, a, b), target = pos.add(offset);
            if (!world.isBlockLoaded(target)) continue;
            IBlockState fluid = world.getBlockState(target);
            Vec3d flow;
            if (fluid.getBlock() instanceof BlockLiquid) flow = fluid.getBlock().modifyAcceleration(world, target, null, Vec3d.ZERO);
            else if (fluid.getBlock() instanceof net.minecraftforge.fluids.BlockFluidBase forgeFluid) flow = forgeFluid.getFlowVector(world, target);
            else continue;
            Vec3d radial = new Vec3d(offset).normalize();
            Vec3d tangent = switch (axis) {
                case X -> new Vec3d(0, -radial.z, radial.y);
                case Y -> new Vec3d(radial.z, 0, -radial.x);
                case Z -> new Vec3d(-radial.y, radial.x, 0);
            };
            Vec3d plane = switch (axis) { case X -> new Vec3d(0, flow.y, flow.z); case Y -> new Vec3d(flow.x, 0, flow.z); case Z -> new Vec3d(flow.x, flow.y, 0); };
            double dot = plane.normalize().dotProduct(tangent);
            if (Math.abs(dot) > .5D) score += (int) Math.signum(dot);
        }
        if (score != flowScore) { flowScore = score; KineticNetworkManager.discard(world); markDirty(); sync(); }
    }
    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos).grow(2); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) { super.writeToNBT(tag); tag.setInteger("FlowScore", flowScore); tag.setInteger("Wood", wood); return tag; }
    @Override public void readFromNBT(NBTTagCompound tag) { super.readFromNBT(tag); flowScore = tag.getInteger("FlowScore"); wood = tag.hasKey("Wood") ? Math.max(0, Math.min(7, tag.getInteger("Wood"))) : 1; }
    @Override public NBTTagCompound getUpdateTag() { NBTTagCompound tag = super.getUpdateTag(); tag.setInteger("Wood", wood); return tag; }
    @Override public void handleUpdateTag(NBTTagCompound tag) { super.handleUpdateTag(tag); wood = tag.hasKey("Wood") ? Math.max(0, Math.min(7, tag.getInteger("Wood"))) : 1; }
}
