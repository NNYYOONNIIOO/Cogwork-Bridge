package com.cogwork.cogworkbridge.content.processing;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ScriptedAssemblyTooltip {
    private ScriptedAssemblyTooltip() { }
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        var input = event.getItemStack();
        if (!ScriptedAssemblyRecipes.inProgress(input)) return;
        var progress = input.getTagCompound().getCompoundTag(ScriptedAssemblyRecipes.TAG);
        for (var line : ScriptedAssemblyRecipes.all()) {
            if (!line.id().equals(progress.getString("Recipe"))) continue;
            int total = line.steps().size() * line.loops();
            int done = Math.max(0, Math.min(total - 1, progress.getInteger("Step")));
            var text = event.getToolTip(); text.add("");
            text.add("\u00a77" + I18n.format("tooltip.cogworkbridge.assembly.title"));
            text.add("\u00a78" + I18n.format("tooltip.cogworkbridge.assembly.progress", done, total));
            for (int i = 0; i < Math.min(line.steps().size(), total - done); i++) {
                var step = line.steps().get((done + i) % line.steps().size());
                String description = switch (step.operation()) {
                    case PRESS -> I18n.format("tooltip.cogworkbridge.assembly.press");
                    case DEPLOY -> I18n.format("tooltip.cogworkbridge.assembly.deploy", step.held().get(0).getDisplayName());
                    case FILL -> I18n.format("tooltip.cogworkbridge.assembly.fill", step.fluid().getLocalizedName());
                };
                text.add(i == 0 ? "\u00a7b" + I18n.format("tooltip.cogworkbridge.assembly.next", description) : "\u00a73-> " + description);
            }
            return;
        }
    }
}
