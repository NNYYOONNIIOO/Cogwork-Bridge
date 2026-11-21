package com.cogwork.cogworkbridge.compat.jecharacters;

import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import java.util.Locale;
import me.towdium.jecharacters.util.Match;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

/** Optional JEC integration for Forge's per-tab creative search. */
public final class CreativeSearchCompat {
    private CreativeSearchCompat() { }

    public static boolean updateSearch(GuiContainerCreative gui) {
        if (!Loader.isModLoaded("jecharacters") || gui.getSelectedTabIndex() != ModCreativeTab.INSTANCE.getIndex()) return false;
        updatePinyinSearch(gui);
        return true;
    }

    @Optional.Method(modid = "jecharacters")
    private static void updatePinyinSearch(GuiContainerCreative gui) {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiContainerCreative.ContainerCreative container = (GuiContainerCreative.ContainerCreative) gui.inventorySlots;
        container.itemList.clear();
        ModCreativeTab.INSTANCE.displayAllRelevantItems(container.itemList);
        String query = gui.searchField.getText().toLowerCase(Locale.ROOT);
        if (!query.isEmpty()) {
            ITooltipFlag flag = minecraft.gameSettings.advancedItemTooltips
                    ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL;
            container.itemList.removeIf(stack -> !matches(stack, minecraft, flag, query));
        }
        gui.currentScroll = 0F;
        container.scrollTo(0F);
    }

    @Optional.Method(modid = "jecharacters")
    private static boolean matches(ItemStack stack, Minecraft minecraft, ITooltipFlag flag, String query) {
        for (String line : stack.getTooltip(minecraft.player, flag)) {
            String text = TextFormatting.getTextWithoutFormattingCodes(line).toLowerCase(Locale.ROOT);
            if (text.contains(query) || Match.contains(text, query)) return true;
        }
        return false;
    }
}
