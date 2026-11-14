package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

/** The reusable 18-slot list filter used by fluid and item devices. */
public final class ListFilterItem extends Item {
    public static final int SLOT_COUNT = 18;
    private static final int MAX_NESTING = 8;
    private static final String DATA = "CogworkBridgeListFilter";

    public ListFilterItem() { setCreativeTab(ModCreativeTab.INSTANCE); }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        return new ActionResult<>(open(world, player, hand), held);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        return open(world, player, hand);
    }

    private static EnumActionResult open(World world, EntityPlayer player, EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND || player.isSneaking()) return EnumActionResult.PASS;
        if (!world.isRemote) {
            player.openGui(CogworkBridge.instance, ListFilterGuiHandler.FILTER_GUI, world,
                    player.inventory.currentItem, 0, 0);
        }
        return EnumActionResult.SUCCESS;
    }

    public static NonNullList<ItemStack> entries(ItemStack filter) {
        NonNullList<ItemStack> entries = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        NBTTagCompound data = data(filter);
        NBTTagList list = data.getTagList("Items", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int slot = entry.getInteger("Slot");
            if (slot < 0 || slot >= SLOT_COUNT) continue;
            ItemStack item = new ItemStack(entry);
            if (!item.isEmpty()) {
                item.setCount(1);
                entries.set(slot, item);
            }
        }
        return entries;
    }

    public static boolean blacklist(ItemStack filter) { return data(filter).getBoolean("Blacklist"); }
    public static boolean respectData(ItemStack filter) { return data(filter).getBoolean("RespectData"); }
    public static boolean isFilterItem(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof ListFilterItem || stack.getItem() instanceof AttributeFilterItem || stack.getItem() instanceof com.cogwork.cogworkbridge.content.logistics.packagebox.PackageFilterItem);
    }

    static void write(ItemStack filter, NonNullList<ItemStack> entries, boolean blacklist, boolean respectData) {
        if (filter.isEmpty() || !(filter.getItem() instanceof ListFilterItem)) return;
        NBTTagCompound root = filter.hasTagCompound() ? filter.getTagCompound() : new NBTTagCompound();
        NBTTagCompound data = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < Math.min(SLOT_COUNT, entries.size()); i++) {
            ItemStack item = entries.get(i);
            if (item.isEmpty()) continue;
            ItemStack copy = item.copy();
            copy.setCount(1);
            NBTTagCompound entry = copy.writeToNBT(new NBTTagCompound());
            entry.setInteger("Slot", i);
            list.appendTag(entry);
        }
        if (list.tagCount() > 0) data.setTag("Items", list);
        data.setBoolean("Blacklist", blacklist);
        data.setBoolean("RespectData", respectData);
        root.setTag(DATA, data);
        filter.setTagCompound(root);
    }

    public static ItemStack fromAlternatives(java.util.List<ItemStack> alternatives) {
        if (alternatives.isEmpty()) return ItemStack.EMPTY;
        if (alternatives.size() == 1) return alternatives.get(0).copy();
        if (alternatives.size() > SLOT_COUNT) {
            for (int oreId : net.minecraftforge.oredict.OreDictionary.getOreIDs(alternatives.get(0))) {
                boolean allMatch = true;
                for (ItemStack candidate : alternatives) {
                    boolean found = false;
                    for (int id : net.minecraftforge.oredict.OreDictionary.getOreIDs(candidate)) if (id == oreId) { found = true; break; }
                    if (!found) { allMatch = false; break; }
                }
                if (allMatch) {
                    ItemStack filter = new ItemStack(com.cogwork.cogworkbridge.registry.AllItems.ATTRIBUTE_FILTER);
                    AttributeFilterItem.write(filter, 0, java.util.List.of(new AttributeFilterItem.Attribute("ore", net.minecraftforge.oredict.OreDictionary.getOreName(oreId), false)));
                    return filter;
                }
            }
        }
        ItemStack filter = new ItemStack(com.cogwork.cogworkbridge.registry.AllItems.FILTER);
        NonNullList<ItemStack> entries = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        for (int i = 0; i < Math.min(SLOT_COUNT, alternatives.size()); i++) entries.set(i, alternatives.get(i).copy());
        write(filter, entries, false, true);
        return filter;
    }

    private static NBTTagCompound data(ItemStack stack) {
        NBTTagCompound root = stack.getTagCompound();
        return root == null ? new NBTTagCompound() : root.getCompoundTag(DATA);
    }

    /** Match an item using the same recursive allow/deny rules as a Create list filter. */
    public static boolean matchesItem(ItemStack filter, ItemStack stack) {
        return matchesItem(filter, stack, false, 0);
    }

    private static boolean matchesItem(ItemStack filter, ItemStack stack, boolean exactData, int depth) {
        if (filter.isEmpty()) return true;
        if (stack.isEmpty() || depth >= MAX_NESTING) return false;
        if (filter.getItem() instanceof com.cogwork.cogworkbridge.content.logistics.packagebox.PackageFilterItem)
            return com.cogwork.cogworkbridge.content.logistics.packagebox.PackageFilterItem.matches(filter, stack);
        if (filter.getItem() instanceof AttributeFilterItem) return AttributeFilterItem.matches(filter, stack);
        if (filter.getItem() instanceof ListFilterItem) {
            NBTTagCompound settings = data(filter);
            if (!settings.hasKey("Items", 9)) return settings.getBoolean("Blacklist");
            boolean inverse = settings.getBoolean("Blacklist");
            boolean exact = settings.getBoolean("RespectData");
            for (ItemStack entry : entries(filter)) {
                if (!entry.isEmpty() && matchesItem(entry, stack, exact, depth + 1)) return !inverse;
            }
            return inverse;
        }
        if (filter.getItem() != stack.getItem()) return false;
        boolean sameVariant = filter.getMetadata() == stack.getMetadata();
        if (!sameVariant && (exactData || !filter.isItemStackDamageable())) return false;
        return !exactData || ItemStack.areItemStackTagsEqual(filter, stack);
    }

    public static boolean matchesFluid(ItemStack filter, FluidStack fluid) {
        return matchesFluid(filter, fluid, true, 0);
    }

    private static boolean matchesFluid(ItemStack filter, FluidStack fluid, boolean respectData, int depth) {
        if (filter.isEmpty()) return true;
        if (fluid == null || fluid.amount <= 0 || depth >= MAX_NESTING) return false;
        if (filter.getItem() instanceof ListFilterItem) {
            NBTTagCompound data = data(filter);
            // An empty allow list matches nothing; an empty deny list matches everything.
            if (!data.hasKey("Items", 9)) return data.getBoolean("Blacklist");
            boolean inverse = data.getBoolean("Blacklist");
            boolean exact = data.getBoolean("RespectData");
            for (ItemStack item : entries(filter)) {
                if (!item.isEmpty() && matchesFluid(item, fluid, exact, depth + 1)) return !inverse;
            }
            return inverse;
        }
        FluidStack candidate = FluidContainerHelper.contents(filter);
        return candidate != null && (respectData ? candidate.isFluidEqual(fluid)
                : candidate.getFluid() == fluid.getFluid());
    }
}
