package com.cogwork.cogworkbridge.content.processing.press;

import com.cogwork.cogworkbridge.content.processing.basin.BasinTileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Finds ordinary uniform-grid crafting recipes without adding recipe data. */
final class CompactingCraft {
    record Plan(int slot, ItemStack input, int count, ResourceLocation recipe, List<ItemStack> results) { }

    private CompactingCraft() { }

    @Nullable
    static Plan find(World world, BasinTileEntity basin) {
        int slot = -1;
        for (int i = 0; i < basin.getInputItems().getSlots(); i++) {
            if (!basin.getInputItems().getStackInSlot(i).isEmpty()) {
                if (slot != -1) return null;
                slot = i;
            }
        }
        if (slot < 0) return null;
        ItemStack input = basin.getInputItems().getStackInSlot(slot);
        Plan plan = findForSize(world, basin, slot, input, 3);
        return plan != null ? plan : findForSize(world, basin, slot, input, 2);
    }

    @Nullable
    private static Plan findForSize(World world, BasinTileEntity basin, int slot, ItemStack input, int size) {
        int count = size * size;
        if (input.getCount() < count) return null;
        Container container = new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return false; }
        };
        InventoryCrafting grid = new InventoryCrafting(container, 3, 3);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                ItemStack one = input.copy();
                one.setCount(1);
                grid.setInventorySlotContents(y * 3 + x, one);
            }
        }
        IRecipe recipe = CraftingManager.findMatchingRecipe(grid, world);
        if (recipe == null || recipe.getRegistryName() == null) return null;
        ItemStack result = recipe.getCraftingResult(grid);
        if (result.isEmpty()) return null;
        List<ItemStack> results = new ArrayList<>();
        results.add(result.copy());
        for (ItemStack remainder : recipe.getRemainingItems(grid)) {
            if (!remainder.isEmpty()) results.add(remainder.copy());
        }
        if (!basin.processItems(slot, input, count, results, true)) return null;
        ItemStack expected = input.copy();
        expected.setCount(1);
        return new Plan(slot, expected, count, recipe.getRegistryName(), results);
    }
}
