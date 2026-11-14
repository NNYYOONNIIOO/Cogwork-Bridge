package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nullable;

public final class CreativeFluidTankTileEntity extends FluidTankTileEntity {
    @Nullable private FluidStack selectedFluid;
    private final IFluidHandler creativeHandler = new IFluidHandler() {
        @Override
        public IFluidTankProperties[] getTankProperties() {
            CreativeFluidTankTileEntity root = controller();
            if (root == null) return new IFluidTankProperties[0];
            return new IFluidTankProperties[]{new FluidTankProperties(
                    root.selectedFluid == null ? null : new FluidStack(root.selectedFluid, root.tank.getCapacity()),
                    root.tank.getCapacity(), true, true)};
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (controller() == null || resource == null || resource.amount <= 0) return 0;
            return resource.amount;
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            CreativeFluidTankTileEntity root = controller();
            if (root == null || root.selectedFluid == null || resource == null
                    || !root.selectedFluid.isFluidEqual(resource)) return null;
            return drain(resource.amount, doDrain);
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            CreativeFluidTankTileEntity root = controller();
            if (root == null || root.selectedFluid == null || maxDrain <= 0) return null;
            return new FluidStack(root.selectedFluid, Math.min(maxDrain, root.tank.getCapacity()));
        }
    };

    @Override public IFluidHandler getFluidHandler() { return isBoiler() ? super.getFluidHandler() : creativeHandler; }
    public void setSelectedFluid(@Nullable FluidStack fluid) {
        CreativeFluidTankTileEntity root = controller();
        if (root == null) return;
        root.setRawSelected(fluid);
        root.contentsChanged();
    }

    @Nullable private CreativeFluidTankTileEntity controller() {
        FluidTankTileEntity root = getControllerTile();
        return root instanceof CreativeFluidTankTileEntity creative ? creative : null;
    }

    @Nullable FluidStack getRawSelected() { return selectedFluid; }

    void setRawSelected(@Nullable FluidStack fluid) {
        selectedFluid = fluid == null || fluid.amount <= 0 ? null : fluid.copy();
        if (selectedFluid != null) selectedFluid.amount = 1;
    }

    public boolean hasSelectedFluid() { return getFluid() != null; }
    @Override public FluidStack getFluid() {
        CreativeFluidTankTileEntity root = controller();
        return root == null || root.selectedFluid == null ? null
                : new FluidStack(root.selectedFluid, root.tank.getCapacity());
    }

    @Override
    public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> capability,
                                 net.minecraft.util.EnumFacing facing) {
        return capability == net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
                || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability,
                               net.minecraft.util.EnumFacing facing) {
        return capability == net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
                ? net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(getFluidHandler())
                : super.getCapability(capability, facing);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (selectedFluid != null) tag.setTag("CreativeFluid", selectedFluid.writeToNBT(new NBTTagCompound()));
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        selectedFluid = tag.hasKey("CreativeFluid", 10)
                ? FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("CreativeFluid")) : null;
    }
}
