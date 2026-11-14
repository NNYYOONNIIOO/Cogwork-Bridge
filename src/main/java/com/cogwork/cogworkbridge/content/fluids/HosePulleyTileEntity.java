package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;

public final class HosePulleyTileEntity extends KineticTileEntity implements ITickable {
    private final FluidTank tank = new FluidTank(1500) {
        @Override protected void onContentsChanged() { markDirty(); sync(); }
    };
    private float cableOffset;
    private float previousOffset;
    private float previousSpeed;
    private boolean moving;
    private final HoseFluidSearch search = new HoseFluidSearch(this);
    private final IFluidHandler handler = new IFluidHandler() {
        @Override public net.minecraftforge.fluids.capability.IFluidTankProperties[] getTankProperties() {
            FluidStack fluid = tank.getFluid();
            if (fluid == null && !moving && world != null && !world.isRemote) fluid = search.findSource(endpoint());
            return new net.minecraftforge.fluids.capability.IFluidTankProperties[]{
                    new FluidTankProperties(fluid == null ? null : fluid.copy(), tank.getCapacity(), true, true)};
        }
        @Override public int fill(FluidStack resource, boolean doFill) { return fillFromPipe(resource, doFill); }
        @Override public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || resource.amount <= 0) return null;
            FluidStack available = availableForDrain();
            return available == null || !available.isFluidEqual(resource) ? null : drainToPipe(resource.amount, doDrain);
        }
        @Override public FluidStack drain(int maxDrain, boolean doDrain) { return drainToPipe(maxDrain, doDrain); }
    };

    public float getCableOffset() { return cableOffset; }
    public float getCableOffset(float partialTicks) { return previousOffset + (cableOffset - previousOffset) * partialTicks; }
    public FluidStack getFluid() { return tank.getFluid(); }
    public int getCapacity() { return tank.getCapacity(); }
    public IFluidHandler getFluidHandler() { return handler; }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).expand(0.0D, -Math.ceil(cableOffset) - 1.0D, 0.0D);
    }

    @Override
    public void update() {
        super.update();
        if (world == null) return;
        float speed = getSpeed();
        boolean speedChanged = Float.compare(speed, previousSpeed) != 0;
        previousSpeed = speed;
        previousOffset = cableOffset;
        boolean wasMoving = moving;
        if (speed == 0.0F && speedChanged) cableOffset = Math.round(cableOffset);
        float next = cableOffset + speed / 512.0F;
        next = MathHelper.clamp(next, 0.0F, Math.max(0, pos.getY()));
        int depth = Math.max(1, MathHelper.ceil(next));
        net.minecraft.util.math.BlockPos below = pos.down(depth);
        if (speed > 0 && world.isBlockLoaded(below)) {
            IBlockState obstruction = world.getBlockState(below);
            if (!obstruction.getBlock().isReplaceable(world, below)
                    && obstruction.getMaterial() != net.minecraft.block.material.Material.WATER
                    && obstruction.getMaterial() != net.minecraft.block.material.Material.LAVA
                    && !(obstruction.getBlock() instanceof net.minecraftforge.fluids.IFluidBlock)) {
                next = Math.max(0, depth - 1);
            }
        }
        moving = Float.compare(next, cableOffset) != 0;
        cableOffset = next;
        if (world.isRemote) return;
        if (moving && !wasMoving) search.reset();
        if (!moving) search.tick(endpoint());
        if (speedChanged || moving != wasMoving || moving && world.getTotalWorldTime() % 20 == 0) {
            markDirty();
            sync();
        }
    }

    private net.minecraft.util.math.BlockPos endpoint() {
        return pos.down(MathHelper.ceil(cableOffset));
    }

    @Nullable private FluidStack availableForDrain() {
        FluidStack buffered = tank.getFluid();
        if (buffered != null && buffered.amount > 0) return buffered;
        return world == null || world.isRemote || moving ? null : search.findSource(endpoint());
    }

    @Nullable private FluidStack drainToPipe(int maxDrain, boolean execute) {
        if (maxDrain <= 0) return null;
        FluidStack buffered = tank.getFluid();
        int bufferedAmount = tank.getFluidAmount();
        if (bufferedAmount >= maxDrain || bufferedAmount >= 1000
                || world == null || world.isRemote || moving) {
            return tank.drain(maxDrain, execute);
        }
        FluidStack source = search.findSource(endpoint());
        if (source == null || source.amount != 1000
                || buffered != null && !buffered.isFluidEqual(source)) {
            return tank.drain(maxDrain, execute);
        }

        int amount = Math.min(maxDrain, bufferedAmount + 1000);
        // Do not remove a whole world source unless its remainder fits in the buffer.
        if (bufferedAmount + 1000 - amount > tank.getCapacity()) return tank.drain(maxDrain, execute);
        FluidStack result = source.copy();
        result.amount = amount;
        if (!execute) return result;

        FluidStack pulled = search.pull(endpoint());
        if (pulled == null || pulled.amount != 1000 || !pulled.isFluidEqual(source)) {
            return tank.drain(maxDrain, true);
        }
        int fromBuffer = Math.min(bufferedAmount, amount);
        if (fromBuffer > 0) tank.drain(fromBuffer, true);
        int remainder = 1000 - (amount - fromBuffer);
        if (remainder > 0) {
            pulled.amount = remainder;
            tank.fill(pulled, true);
        }
        return result;
    }

    private int fillFromPipe(FluidStack resource, boolean execute) {
        if (resource == null || resource.amount <= 0 || resource.getFluid().getBlock() == null) return 0;
        FluidStack existing = tank.getFluid();
        if (existing != null && !existing.isFluidEqual(resource)) return 0;
        int buffered = tank.getFluidAmount();
        if (world == null || world.isRemote || moving
                || buffered + resource.amount < 1000 || search.findFillTarget(endpoint(), resource.getFluid()) == null) {
            return tank.fill(resource, execute);
        }
        int fromBuffer = Math.min(buffered, 1000);
        int fromResource = 1000 - fromBuffer;
        int remaining = resource.amount - fromResource;
        int stored = Math.min(remaining, tank.getCapacity() - (buffered - fromBuffer));
        if (!execute) return fromResource + stored;
        if (!search.deposit(endpoint(), resource.getFluid())) return tank.fill(resource, true);
        if (fromBuffer > 0) tank.drain(fromBuffer, true);
        if (stored > 0) {
            FluidStack rest = resource.copy();
            rest.amount = stored;
            stored = tank.fill(rest, true);
        }
        return fromResource + stored;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            EnumFacing pipeSide = world != null && world.getBlockState(pos).getBlock() instanceof HosePulleyBlock
                    ? world.getBlockState(pos).getValue(HosePulleyBlock.FACING).rotateYCCW() : null;
            return facing == null || facing == pipeSide;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        return hasCapability(capability, facing) && capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
                ? CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(handler) : super.getCapability(capability, facing);
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setFloat("CableOffset", cableOffset);
        tag.setFloat("PreviousSpeed", previousSpeed);
        tag.setBoolean("Moving", moving);
        tag.setTag("Tank", tank.writeToNBT(new NBTTagCompound()));
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        cableOffset = MathHelper.clamp(tag.getFloat("CableOffset"), 0.0F, 256.0F);
        previousOffset = cableOffset;
        previousSpeed = tag.getFloat("PreviousSpeed");
        moving = tag.getBoolean("Moving");
        search.reset();
        if (tag.hasKey("Tank", 10)) tank.readFromNBT(tag.getCompoundTag("Tank"));
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
