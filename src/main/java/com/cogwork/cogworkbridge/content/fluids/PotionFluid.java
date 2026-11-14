package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

/** A virtual fluid: potion contents and bottle type travel in the fluid stack's NBT. */
public final class PotionFluid extends Fluid {
    private static final String NAME = "cogworkbridge_potion";
    private static final String BOTTLE_TYPE = "BottleType";
    private static final String[] POTION_DATA = {"Potion", "CustomPotionEffects", "CustomPotionColor"};
    private static Fluid registered;

    private PotionFluid() {
        super(NAME, new ResourceLocation("create", "fluid/potion_still"),
                new ResourceLocation("create", "fluid/potion_flow"));
    }

    public static void register() {
        PotionFluid fluid = new PotionFluid();
        FluidRegistry.registerFluid(fluid);
        registered = FluidRegistry.getFluid(NAME);
    }

    public static boolean isPotionItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemPotion;
    }

    public static FluidStack fromPotion(ItemStack stack, int amount) {
        int bottleType = stack.getItem() == Items.SPLASH_POTION ? 1
                : stack.getItem() == Items.LINGERING_POTION ? 2 : 0;
        NBTTagCompound itemTag = stack.getTagCompound();
        if (bottleType == 0 && PotionUtils.getPotionFromItem(stack) == PotionTypes.WATER
                && PotionUtils.getEffectsFromStack(stack).isEmpty()
                && (itemTag == null || !itemTag.hasKey("CustomPotionColor", 99))) {
            return new FluidStack(FluidRegistry.WATER, amount);
        }
        FluidStack result = new FluidStack(registered, amount);
        result.tag = new NBTTagCompound();
        result.tag.setByte(BOTTLE_TYPE, (byte) bottleType);
        if (itemTag != null) {
            for (String key : POTION_DATA) {
                if (itemTag.hasKey(key)) result.tag.setTag(key, itemTag.getTag(key).copy());
            }
        }
        return result;
    }

    static boolean canBottle(@Nullable FluidStack stack) {
        return stack != null && (stack.getFluid() == FluidRegistry.WATER || stack.getFluid() == registered);
    }

    public static boolean isPotionFluid(@Nullable FluidStack stack) {
        return stack != null && registered != null && stack.getFluid() == registered;
    }

    public static ItemStack toBottle(FluidStack fluid) {
        if (fluid.getFluid() == FluidRegistry.WATER) {
            return PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.WATER);
        }
        NBTTagCompound data = fluid.tag;
        int type = data == null ? 0 : data.getByte(BOTTLE_TYPE);
        Item item = type == 1 ? Items.SPLASH_POTION : type == 2 ? Items.LINGERING_POTION : Items.POTIONITEM;
        ItemStack result = new ItemStack(item);
        if (data != null) {
            NBTTagCompound itemTag = new NBTTagCompound();
            for (String key : POTION_DATA) {
                if (data.hasKey(key)) itemTag.setTag(key, data.getTag(key).copy());
            }
            if (!itemTag.isEmpty()) result.setTagCompound(itemTag);
        }
        return result;
    }

    @Override
    public int getColor(FluidStack stack) {
        return 0xFF000000 | PotionUtils.getColor(toBottle(stack));
    }

    @Override
    public String getLocalizedName(FluidStack stack) {
        return toBottle(stack).getDisplayName();
    }
}
