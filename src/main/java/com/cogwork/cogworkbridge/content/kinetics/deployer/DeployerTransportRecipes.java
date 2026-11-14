package com.cogwork.cogworkbridge.content.kinetics.deployer;

import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Available upstream deploying operations with ingredients present in this port. */
public final class DeployerTransportRecipes {
    private DeployerTransportRecipes() { }

    public static ItemStack result(ItemStack input, ItemStack held) {
        if (input.isEmpty() || held.isEmpty()) return ItemStack.EMPTY;
        if (com.cogwork.cogworkbridge.content.trains.track.TrackAssemblySequence.isDeployStep(input, held))
            return com.cogwork.cogworkbridge.content.trains.track.TrackAssemblySequence.deploy(input);
        if (held.getItem() instanceof com.cogwork.cogworkbridge.content.equipment.SandPaperItem) {
            ItemStack polished = com.cogwork.cogworkbridge.content.equipment.SandPaperItem.polish(input);
            if (!polished.isEmpty()) return polished;
        }
        boolean initial = hasOre(input, "plateGold");
        if (isSequenceStep(input, held)) {
            int stage = initial ? 0 : Math.max(0, Math.min(14, input.hasTagCompound() ? input.getTagCompound().getInteger(com.cogwork.cogworkbridge.content.processing.AssemblyLineRecipes.PRECISION_STEP) : 0));
            if (stage == 14) return new ItemStack(AllItems.PRECISION_MECHANISM);
            ItemStack partial = new ItemStack(AllItems.INCOMPLETE_PRECISION_MECHANISM);
            net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
            tag.setInteger(com.cogwork.cogworkbridge.content.processing.AssemblyLineRecipes.PRECISION_STEP, stage + 1); partial.setTagCompound(tag); return partial;
        }
        if (input.getItem() == AllItems.INCOMPLETE_PRECISION_MECHANISM) return ItemStack.EMPTY;
        var upstream = findUpstream(input, held);
        if (upstream != null && !upstream.outputs().isEmpty())
            return upstream.outputs().get(0).stack().copy();
        if (com.cogwork.cogworkbridge.content.processing.casing.CasingApplication.isStripped(input)) {
            net.minecraft.block.Block casing = com.cogwork.cogworkbridge.content.processing.casing.CasingApplication.casing(held);
            if (casing != null) return new ItemStack(casing);
        }
        if (!isPlank(held)) return ItemStack.EMPTY;
        if (input.getItem() == Item.getItemFromBlock(AllBlocks.SHAFT))
            return new ItemStack(AllBlocks.COGWHEEL);
        if (input.getItem() == Item.getItemFromBlock(AllBlocks.COGWHEEL))
            return new ItemStack(AllBlocks.LARGE_COGWHEEL);
        return ItemStack.EMPTY;
    }

    static boolean consumesHeld(ItemStack input, ItemStack held) {
        if (isPolishing(input, held)) return false;
        if (com.cogwork.cogworkbridge.content.trains.track.TrackAssemblySequence.isDeployStep(input, held)) return true;
        if (isSequenceStep(input, held)) return true;
        var recipe = findUpstream(input, held);
        return recipe == null || !recipe.keepHeld();
    }

    static boolean isPolishing(ItemStack input, ItemStack held) {
        return held.getItem() instanceof com.cogwork.cogworkbridge.content.equipment.SandPaperItem
                && !com.cogwork.cogworkbridge.content.equipment.SandPaperItem.polish(input).isEmpty();
    }

    private static com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.Recipe findUpstream(ItemStack input, ItemStack held) {
        var recipe = com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.find("deploying", input, held);
        return recipe != null ? recipe : com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.find("item_application", input, held);
    }

    private static boolean isSequenceStep(ItemStack input, ItemStack held) {
        boolean initial = hasOre(input, "plateGold");
        if (!initial && input.getItem() != AllItems.INCOMPLETE_PRECISION_MECHANISM) return false;
        int stage = initial ? 0 : Math.max(0, Math.min(14, input.hasTagCompound() ? input.getTagCompound().getInteger(com.cogwork.cogworkbridge.content.processing.AssemblyLineRecipes.PRECISION_STEP) : 0));
        return stage % 3 == 0 ? held.getItem() == Item.getItemFromBlock(AllBlocks.COGWHEEL)
                : stage % 3 == 1 ? held.getItem() == Item.getItemFromBlock(AllBlocks.LARGE_COGWHEEL) : hasOre(held, "nuggetIron");
    }

    static java.util.List<ItemStack> finish(ItemStack input, ItemStack held, java.util.Random random) {
        ItemStack result = result(input, held);
        if (result.isEmpty()) return java.util.Collections.emptyList();
        if (com.cogwork.cogworkbridge.content.trains.track.TrackAssemblySequence.isDeployStep(input, held))
            return java.util.Collections.singletonList(result);
        var upstream = findUpstream(input, held);
        if (upstream != null && !isSequenceStep(input, held) && !isPolishing(input, held)) return upstream.roll(random);
        if (!isSequenceStep(input, held) || result.getItem() != AllItems.PRECISION_MECHANISM) return java.util.Collections.singletonList(result);
        int roll = random.nextInt(150);
        if (roll < 120) return java.util.Collections.singletonList(result);
        if (roll < 128) result = new ItemStack(AllItems.GOLDEN_SHEET);
        else if (roll < 136) result = new ItemStack(AllItems.ANDESITE_ALLOY);
        else if (roll < 141) result = new ItemStack(AllBlocks.COGWHEEL);
        else if (roll < 144) result = new ItemStack(net.minecraft.init.Items.GOLD_NUGGET);
        else if (roll < 146) result = new ItemStack(AllBlocks.SHAFT);
        else if (roll < 148) result = new ItemStack(AllItems.CRUSHED_RAW_GOLD);
        else result = new ItemStack(roll == 148 ? net.minecraft.init.Items.IRON_INGOT : net.minecraft.init.Items.CLOCK);
        return java.util.Collections.singletonList(result);
    }
    private static boolean hasOre(ItemStack stack, String name) {
        for (int id : OreDictionary.getOreIDs(stack)) if (name.equals(OreDictionary.getOreName(id))) return true;
        return false;
    }
    private static boolean isPlank(ItemStack stack) {
        if (stack.getItem() == Item.getItemFromBlock(Blocks.PLANKS)) return true;
        for (int id : OreDictionary.getOreIDs(stack))
            if ("plankWood".equals(OreDictionary.getOreName(id))) return true;
        return false;
    }
}
