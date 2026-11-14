package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nullable;

public final class FluidContainerHelper {
    private static final int BOTTLE_AMOUNT = 250;
    private static final int BUCKET_AMOUNT = 1000;

    private FluidContainerHelper() { }

    public static boolean canInteractWithPotionBottle(ItemStack stack, IFluidHandler handler) {
        if (PotionFluid.isPotionItem(stack)) {
            FluidStack contents = contents(stack);
            return contents != null && handler.fill(contents, false) == contents.amount;
        }
        return !stack.isEmpty() && stack.getItem() == Items.GLASS_BOTTLE && canFill(stack, handler, BOTTLE_AMOUNT);
    }

    @Nullable
    public static ItemStack interactWithPotionBottle(ItemStack stack, IFluidHandler handler) {
        if (!canInteractWithPotionBottle(stack, handler)) return null;
        FluidStack contents = contents(stack);
        return contents != null ? empty(stack, handler, contents) : fill(stack, handler, BOTTLE_AMOUNT);
    }

    @Nullable
    static FluidStack contents(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() == com.cogwork.cogworkbridge.registry.AllItems.BUILDERS_TEA
                && FluidRegistry.getFluid("tea") != null) return new FluidStack(FluidRegistry.getFluid("tea"), BOTTLE_AMOUNT);
        if (!stack.isEmpty() && stack.getItem().getRegistryName() != null
                && stack.getItem().getRegistryName().toString().equals("farmers_future_delight:honey_bottle")
                && FluidRegistry.getFluid("honey") != null) return new FluidStack(FluidRegistry.getFluid("honey"), BOTTLE_AMOUNT);
        if (PotionFluid.isPotionItem(stack)) return PotionFluid.fromPotion(stack, BOTTLE_AMOUNT);
        if (stack.isEmpty()) return null;
        if (stack.getItem() == Items.MILK_BUCKET) return MilkFluid.bucket();
        IFluidHandlerItem handler = FluidUtil.getFluidHandler(single(stack));
        if (handler == null) return null;
        FluidStack fluid = handler.drain(BUCKET_AMOUNT, false);
        return fluid == null || fluid.amount <= 0 ? null : fluid;
    }

    @Nullable
    static ItemStack empty(ItemStack stack, IFluidHandler target, FluidStack fluid) {
        if (!stack.isEmpty() && (stack.getItem() == com.cogwork.cogworkbridge.registry.AllItems.BUILDERS_TEA
                || stack.getItem().getRegistryName() != null && stack.getItem().getRegistryName().toString().equals("farmers_future_delight:honey_bottle"))) {
            FluidStack contents = contents(stack);
            if (contents == null || !contents.isFluidEqual(fluid) || target.fill(contents, false) != contents.amount) return null;
            return target.fill(contents, true) == contents.amount ? new ItemStack(Items.GLASS_BOTTLE) : null;
        }
        if (!stack.isEmpty() && stack.getItem() == Items.MILK_BUCKET) {
            if (!MilkFluid.isMilk(fluid) || fluid.amount != BUCKET_AMOUNT
                    || target.fill(fluid, false) != BUCKET_AMOUNT) return null;
            return target.fill(fluid, true) == BUCKET_AMOUNT ? new ItemStack(Items.BUCKET) : null;
        }
        if (PotionFluid.isPotionItem(stack)) {
            if (!PotionFluid.fromPotion(stack, BOTTLE_AMOUNT).isFluidEqual(fluid)) return null;
            if (target.fill(fluid, false) != BOTTLE_AMOUNT) return null;
            return target.fill(fluid, true) == BOTTLE_AMOUNT ? new ItemStack(Items.GLASS_BOTTLE) : null;
        }
        FluidActionResult result = FluidUtil.tryEmptyContainer(single(stack), target, fluid.amount, null, true);
        return result.isSuccess() ? result.getResult() : null;
    }

    static boolean canFill(ItemStack stack, IFluidHandler source, int capacity) {
        if (!stack.isEmpty() && stack.getItem() == Items.BUCKET && capacity >= BUCKET_AMOUNT) {
            FluidStack available = source.drain(BUCKET_AMOUNT, false);
            if (available != null && available.amount >= BUCKET_AMOUNT
                    && (MilkFluid.isMilk(available) || ProcessingFluids.bucketFor(available) != null)) return true;
        }
        if (stack.getItem() == Items.GLASS_BOTTLE) {
            FluidStack available = source.drain(BOTTLE_AMOUNT, false);
            return PotionFluid.canBottle(available) && available.amount >= BOTTLE_AMOUNT;
        }
        if (FluidUtil.getFluidHandler(stack) == null) return false;
        return FluidUtil.tryFillContainer(single(stack), source, capacity, null, false).isSuccess();
    }

    static boolean canPossiblyFill(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() == Items.GLASS_BOTTLE || stack.getItem() == Items.BUCKET) return true;
        IFluidHandlerItem handler = FluidUtil.getFluidHandler(single(stack));
        if (handler == null) return false;
        for (IFluidTankProperties property : handler.getTankProperties()) {
            FluidStack contents = property.getContents();
            if (property.canFill() && (contents == null || contents.amount < property.getCapacity())) return true;
        }
        return false;
    }

    @Nullable
    static ItemStack fill(ItemStack stack, IFluidHandler source, int capacity) {
        if (!stack.isEmpty() && stack.getItem() == Items.BUCKET && capacity >= BUCKET_AMOUNT) {
            FluidStack available = source.drain(BUCKET_AMOUNT, false);
            if (available != null && available.amount >= BUCKET_AMOUNT) {
                net.minecraft.item.Item bucket = MilkFluid.isMilk(available) ? Items.MILK_BUCKET : ProcessingFluids.bucketFor(available);
                if (bucket != null) {
                    available.amount = BUCKET_AMOUNT;
                    FluidStack drained = source.drain(available, true);
                    return drained == null || drained.amount != BUCKET_AMOUNT ? null : new ItemStack(bucket);
                }
            }
        }
        if (stack.getItem() == Items.GLASS_BOTTLE) {
            FluidStack available = source.drain(BOTTLE_AMOUNT, false);
            if (!PotionFluid.canBottle(available) || available.amount < BOTTLE_AMOUNT) return null;
            available.amount = BOTTLE_AMOUNT;
            FluidStack drained = source.drain(available, true);
            return drained == null || drained.amount != BOTTLE_AMOUNT ? null : PotionFluid.toBottle(drained);
        }
        FluidActionResult result = FluidUtil.tryFillContainer(single(stack), source, capacity, null, true);
        return result.isSuccess() ? result.getResult() : null;
    }

    private static ItemStack single(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
