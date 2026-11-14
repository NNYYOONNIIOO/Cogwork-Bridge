package com.cogwork.cogworkbridge.registry;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

/**
 * The Cogwork Bridge creative tab.
 *
 * <p>Constructing a {@link CreativeTabs} registers it into the vanilla tab array,
 * which is why this class must be class-loaded during pre-init — see
 * {@code CommonProxy.preInit}. The icon is resolved lazily on first draw so we do
 * not depend on block field initialisation order.
 */
public final class ModCreativeTab extends CreativeTabs {

    public static final ModCreativeTab INSTANCE = new ModCreativeTab();

    private ItemStack cachedIcon;

    private ModCreativeTab() {
        super(Reference.MOD_ID);
        setBackgroundImageName("item_search.png");
    }

    @Override
    public boolean hasSearchBar() {
        return true;
    }

    @Override
    public ItemStack createIcon() {
        if (cachedIcon == null) {
            cachedIcon = new ItemStack(AllBlocks.SHAFT);
        }
        return cachedIcon;
    }
}
