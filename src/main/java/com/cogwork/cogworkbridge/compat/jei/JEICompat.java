package com.cogwork.cogworkbridge.compat.jei;

import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.client.ListFilterScreen;
import com.cogwork.cogworkbridge.client.ClipboardScreen;
import com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardData;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.gui.IGhostIngredientHandler;
import mezz.jei.api.ingredients.VanillaTypes;
import net.minecraft.item.ItemStack;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/** JEI descriptions for Cogwork Bridge components that have no recipe category yet. */
@JEIPlugin
public final class JEICompat implements IModPlugin {

    @Override
    public void registerCategories(mezz.jei.api.recipe.IRecipeCategoryRegistration registration) {
        ProcessingJEI.categories(registration);
        AssemblyLineJEI.categories(registration);
        MechanicalCraftingJEI.categories(registration);
    }

    @Override
    public void register(IModRegistry registry) {
        ProcessingJEI.register(registry);
        AssemblyLineJEI.register(registry);
        MechanicalCraftingJEI.register(registry);
        describe(registry, AllBlocks.MECHANICAL_PLOUGH, "jei.cogworkbridge.mechanical_plough");
        describe(registry, AllBlocks.MECHANICAL_HARVESTER, "jei.cogworkbridge.mechanical_harvester");
        describe(registry, AllBlocks.MECHANICAL_ROLLER, "jei.cogworkbridge.mechanical_roller");
        describe(registry, AllBlocks.MECHANICAL_DRILL, "jei.cogworkbridge.mechanical_drill");
        describe(registry, AllBlocks.MECHANICAL_SAW, "jei.cogworkbridge.mechanical_saw");
        describe(registry, AllBlocks.MECHANICAL_BEARING, "jei.cogworkbridge.mechanical_bearing");
        describe(registry, AllBlocks.COGWHEEL, "jei.cogworkbridge.cogwheel");
        describe(registry, AllBlocks.LARGE_COGWHEEL, "jei.cogworkbridge.large_cogwheel");
        describe(registry, AllBlocks.GEARBOX, "jei.cogworkbridge.gearbox");
        describe(registry, AllBlocks.VERTICAL_GEARBOX, "jei.cogworkbridge.vertical_gearbox");
        describe(registry, AllBlocks.CLUTCH, "jei.cogworkbridge.clutch");
        describe(registry, AllBlocks.GEARSHIFT, "jei.cogworkbridge.gearshift");
        describe(registry, AllBlocks.ENCASED_CHAIN_DRIVE, "jei.cogworkbridge.encased_chain_drive");
        describe(registry, AllBlocks.ADJUSTABLE_CHAIN_GEARSHIFT, "jei.cogworkbridge.adjustable_chain_gearshift");
        describe(registry, AllBlocks.ROTATION_SPEED_CONTROLLER, "jei.cogworkbridge.rotation_speed_controller");
        describe(registry, AllBlocks.ENCASED_FAN, "jei.cogworkbridge.encased_fan");
        describe(registry, AllBlocks.NOZZLE, "jei.cogworkbridge.nozzle");
        describe(registry, AllBlocks.BASIN, "jei.cogworkbridge.basin");
        describe(registry, AllBlocks.MECHANICAL_PRESS, "jei.cogworkbridge.mechanical_press");
        describe(registry, AllBlocks.MECHANICAL_MIXER, "jei.cogworkbridge.mechanical_mixer");
        registry.addGhostIngredientHandler(ListFilterScreen.class, new ListFilterGhostHandler());
        registry.addGhostIngredientHandler(ClipboardScreen.class, new ClipboardGhostHandler());
        registry.addGhostIngredientHandler(com.cogwork.cogworkbridge.client.BlueprintScreen.class, new BlueprintGhostHandler());
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(new BlueprintTransferHandler(), mezz.jei.api.recipe.VanillaRecipeCategoryUid.CRAFTING);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(new StockOrderTransferHandler(), mezz.jei.api.recipe.VanillaRecipeCategoryUid.CRAFTING);
    }

    private static final class StockOrderTransferHandler implements mezz.jei.api.recipe.transfer.IRecipeTransferHandler<com.cogwork.cogworkbridge.content.logistics.packager.StockTickerContainer> {
        @Override public Class<com.cogwork.cogworkbridge.content.logistics.packager.StockTickerContainer> getContainerClass() {
            return com.cogwork.cogworkbridge.content.logistics.packager.StockTickerContainer.class;
        }
        @Override public mezz.jei.api.recipe.transfer.IRecipeTransferError transferRecipe(com.cogwork.cogworkbridge.content.logistics.packager.StockTickerContainer container,
                mezz.jei.api.gui.IRecipeLayout layout, net.minecraft.entity.player.EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
            if (!doTransfer) return null;
            java.util.List<ItemStack> pattern = new ArrayList<>();
            var ingredients = layout.getItemStacks().getGuiIngredients();
            for (int index = 1; index <= 9; index++) {
                var ingredient = ingredients.get(index);
                ItemStack chosen = ItemStack.EMPTY;
                if (ingredient != null && ingredient.isInput()) {
                    for (ItemStack alternative : ingredient.getAllIngredients()) {
                        if (alternative.isEmpty()) continue;
                        if (chosen.isEmpty()) chosen = alternative.copy();
                        boolean inStock = false;
                        for (int slot = 0; slot < container.display().getSlots(); slot++)
                            if (net.minecraftforge.oredict.OreDictionary.itemMatches(alternative, container.display().getStackInSlot(slot), false)) { inStock = true; break; }
                        if (inStock) { chosen = alternative.copy(); break; }
                    }
                }
                if (!chosen.isEmpty()) chosen.setCount(1);
                pattern.add(chosen);
            }
            container.addCraftingOrder(pattern, maxTransfer ? 64 : 1);
            return null;
        }
    }

    private static final class BlueprintTransferHandler implements mezz.jei.api.recipe.transfer.IRecipeTransferHandler<com.cogwork.cogworkbridge.content.equipment.BlueprintContainer> {
        @Override public Class<com.cogwork.cogworkbridge.content.equipment.BlueprintContainer> getContainerClass() {
            return com.cogwork.cogworkbridge.content.equipment.BlueprintContainer.class;
        }
        @Override public mezz.jei.api.recipe.transfer.IRecipeTransferError transferRecipe(com.cogwork.cogworkbridge.content.equipment.BlueprintContainer container,
                mezz.jei.api.gui.IRecipeLayout layout, net.minecraft.entity.player.EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
            if (!doTransfer) return null;
            var ingredients = layout.getItemStacks().getGuiIngredients();
            com.cogwork.cogworkbridge.network.ModNetwork.CHANNEL.sendToServer(new com.cogwork.cogworkbridge.content.equipment.BlueprintEditPacket(container.windowId, -1, ItemStack.EMPTY));
            for (int index = 1; index <= 9; index++) {
                var ingredient = ingredients.get(index);
                if (ingredient == null || !ingredient.isInput()) continue;
                java.util.List<ItemStack> alternatives = new ArrayList<>();
                for (ItemStack stack : ingredient.getAllIngredients()) if (!stack.isEmpty()) alternatives.add(stack.copy());
                ItemStack filter = com.cogwork.cogworkbridge.content.fluids.ListFilterItem.fromAlternatives(alternatives);
                if (!filter.isEmpty()) com.cogwork.cogworkbridge.network.ModNetwork.CHANNEL.sendToServer(new com.cogwork.cogworkbridge.content.equipment.BlueprintEditPacket(container.windowId, index - 1, filter));
            }
            var output = ingredients.get(0);
            if (output != null && output.getDisplayedIngredient() != null && !output.getDisplayedIngredient().isEmpty())
                com.cogwork.cogworkbridge.network.ModNetwork.CHANNEL.sendToServer(new com.cogwork.cogworkbridge.content.equipment.BlueprintEditPacket(container.windowId, 9, output.getDisplayedIngredient()));
            return null;
        }
    }

    private static final class BlueprintGhostHandler implements IGhostIngredientHandler<com.cogwork.cogworkbridge.client.BlueprintScreen> {
        @Override public <I> List<Target<I>> getTargets(com.cogwork.cogworkbridge.client.BlueprintScreen gui, I ingredient, boolean doStart) {
            List<Target<I>> targets = new ArrayList<>();
            if (!(ingredient instanceof ItemStack)) return targets;
            for (int slot = 0; slot < 11; slot++) {
                final int targetSlot = slot;
                targets.add(new Target<I>() {
                    @Override public Rectangle getArea() { return gui.getJeiGhostArea(targetSlot); }
                    @Override public void accept(I value) { if (value instanceof ItemStack stack) gui.acceptJeiItem(targetSlot, stack); }
                });
            }
            return targets;
        }
        @Override public void onComplete() { }
        @Override public boolean shouldHighlightTargets() { return true; }
    }

    private static final class ClipboardGhostHandler implements IGhostIngredientHandler<ClipboardScreen> {
        @Override public <I> List<Target<I>> getTargets(ClipboardScreen gui, I ingredient, boolean doStart) {
            List<Target<I>> targets = new ArrayList<>();
            if (!(ingredient instanceof ItemStack)) return targets;
            for (int row = 0; row < ClipboardData.ROWS; row++) {
                final int targetRow = row;
                targets.add(new Target<I>() {
                    @Override public Rectangle getArea() { return gui.getJeiGhostArea(targetRow); }
                    @Override public void accept(I value) {
                        if (value instanceof ItemStack stack) gui.acceptJeiItem(targetRow, stack);
                    }
                });
            }
            return targets;
        }
        @Override public void onComplete() { }
        @Override public boolean shouldHighlightTargets() { return true; }
    }

    private static final class ListFilterGhostHandler implements IGhostIngredientHandler<ListFilterScreen> {
        @Override
        public <I> List<Target<I>> getTargets(ListFilterScreen gui, I ingredient, boolean doStart) {
            List<Target<I>> targets = new ArrayList<>();
            if (!(ingredient instanceof ItemStack)) return targets;
            for (int slot = 0; slot < 18; slot++) {
                final int targetSlot = slot;
                targets.add(new Target<I>() {
                    @Override
                    public Rectangle getArea() {
                        return gui.getJeiGhostArea(targetSlot);
                    }

                    @Override
                    public void accept(I value) {
                        if (value instanceof ItemStack stack)
                            gui.acceptJeiItem(targetSlot, stack);
                    }
                });
            }
            return targets;
        }

        @Override
        public void onComplete() {
        }

        @Override
        public boolean shouldHighlightTargets() {
            return true;
        }
    }

    private static void describe(IModRegistry registry, net.minecraft.block.Block block,
                                 String descriptionKey) {
        registry.addIngredientInfo(new ItemStack(block), VanillaTypes.ITEM, descriptionKey);
    }
}
