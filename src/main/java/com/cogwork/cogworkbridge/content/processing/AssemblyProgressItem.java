package com.cogwork.cogworkbridge.content.processing;

import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/** Displays the progress stored by the existing assembly machines. */
public final class AssemblyProgressItem extends Item {
    public AssemblyProgressItem() { setCreativeTab(ModCreativeTab.INSTANCE); }

    @Override @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        if (ScriptedAssemblyRecipes.inProgress(stack)) return;
        AssemblyLineRecipes.Progress progress = AssemblyLineRecipes.progress(stack);
        if (progress == null) return;
        tooltip.add("");
        tooltip.add("\u00a77" + I18n.format("tooltip.cogworkbridge.assembly.title"));
        tooltip.add("\u00a78" + I18n.format("tooltip.cogworkbridge.assembly.progress", progress.completed(), progress.total()));
        List<AssemblyLineRecipes.Step> steps = AssemblyLineRecipes.steps(progress.kind());
        int remaining = progress.total() - progress.completed();
        for (int i = 0; i < Math.min(steps.size(), remaining); i++) {
            AssemblyLineRecipes.Step step = steps.get((progress.completed() + i) % steps.size());
            String description = switch (step.operation()) {
                case DEPLOY -> I18n.format("tooltip.cogworkbridge.assembly.deploy", step.items().get(0).getDisplayName());
                case FILL -> I18n.format("tooltip.cogworkbridge.assembly.fill", step.fluid().getLocalizedName());
                case PRESS -> I18n.format("tooltip.cogworkbridge.assembly.press");
            };
            tooltip.add(i == 0 ? "\u00a7b" + I18n.format("tooltip.cogworkbridge.assembly.next", description)
                    : "\u00a73-> " + description);
        }
    }

    @Override public boolean showDurabilityBar(ItemStack stack) { return AssemblyLineRecipes.progress(stack) != null; }

    @Override public double getDurabilityForDisplay(ItemStack stack) {
        AssemblyLineRecipes.Progress progress = AssemblyLineRecipes.progress(stack);
        return progress == null ? 0D : 1D - progress.completed() / (double) progress.total();
    }

    @Override public int getRGBDurabilityForDisplay(ItemStack stack) {
        AssemblyLineRecipes.Progress progress = AssemblyLineRecipes.progress(stack);
        float ratio = progress == null ? 0F : progress.completed() / (float) progress.total();
        int red = Math.round(255 + (70 - 255) * ratio);
        int green = Math.round(192 + (255 - 192) * ratio);
        int blue = Math.round(116 + (224 - 116) * ratio);
        return red << 16 | green << 8 | blue;
    }
}
