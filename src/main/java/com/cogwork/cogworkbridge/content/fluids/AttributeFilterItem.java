package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.block.Block;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.entity.EntityLiving;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.List;

/** Attribute filter backed by 1.12 item, ore dictionary, enchantment and fluid APIs. */
public final class AttributeFilterItem extends Item {
    private static final String DATA = "CogworkBridgeAttributeFilter";
    public static final int MAX_ATTRIBUTES = 64;
    private static final String[] SIMPLE = {"placeable", "consumable", "fluid_container", "enchanted",
            "max_enchanted", "renamed", "damaged", "badly_damaged", "not_stackable", "equipable",
            "furnace_fuel", "washable", "hauntable", "crushable", "smeltable", "smokable", "blastable", "compostable"};

    public AttributeFilterItem() { setCreativeTab(ModCreativeTab.INSTANCE); }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        return new ActionResult<>(open(world, player, hand), player.getHeldItem(hand));
    }
    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                                 EnumFacing face, float x, float y, float z) {
        return open(world, player, hand);
    }
    private static EnumActionResult open(World world, EntityPlayer player, EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND || player.isSneaking()) return EnumActionResult.PASS;
        if (!world.isRemote) player.openGui(CogworkBridge.instance, ListFilterGuiHandler.ATTRIBUTE_GUI, world,
                player.inventory.currentItem, 0, 0);
        return EnumActionResult.SUCCESS;
    }
    private static NBTTagCompound data(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound().getCompoundTag(DATA) : new NBTTagCompound();
    }
    public static int mode(ItemStack filter) { return Math.max(0, Math.min(2, data(filter).getInteger("Mode"))); }
    public static List<Attribute> selected(ItemStack filter) {
        NBTTagList tags = data(filter).getTagList("Attributes", 10);
        List<Attribute> result = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_ATTRIBUTES, tags.tagCount()); i++) {
            NBTTagCompound entry = tags.getCompoundTagAt(i);
            String type = entry.getString("Type");
            String value = entry.getString("Value");
            if (!type.isEmpty() && type.length() <= 64 && value.length() <= 256)
                result.add(new Attribute(type, value, entry.getBoolean("Inverted")));
        }
        return result;
    }
    public static void write(ItemStack filter, int mode, List<Attribute> attributes) {
        if (filter.isEmpty() || !(filter.getItem() instanceof AttributeFilterItem)) return;
        NBTTagCompound root = filter.hasTagCompound() ? filter.getTagCompound() : new NBTTagCompound();
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("Mode", Math.max(0, Math.min(2, mode)));
        NBTTagList tags = new NBTTagList();
        for (int i = 0; i < Math.min(MAX_ATTRIBUTES, attributes.size()); i++) {
            Attribute attribute = attributes.get(i);
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Type", attribute.type());
            entry.setString("Value", attribute.value());
            entry.setBoolean("Inverted", attribute.inverted());
            tags.appendTag(entry);
        }
        data.setTag("Attributes", tags);
        root.setTag(DATA, data);
        filter.setTagCompound(root);
    }
    /** Modes: any selected condition, all selected conditions, or deny matching conditions. */
    public static boolean matches(ItemStack filter, ItemStack stack) {
        if (stack.isEmpty()) return false;
        int mode = mode(filter);
        for (Attribute attribute : selected(filter)) {
            boolean matches = attribute.applies(stack) != attribute.inverted();
            if (matches && mode == 0) return true;
            if (!matches && mode == 1) return false;
            if (matches && mode == 2) return false;
        }
        return mode != 0;
    }
    public static List<Attribute> attributesFor(ItemStack stack) {
        List<Attribute> result = new ArrayList<>();
        if (stack.isEmpty()) return result;
        for (String type : SIMPLE) {
            Attribute attribute = new Attribute(type, "", false);
            if (attribute.applies(stack)) result.add(attribute);
        }
        ResourceLocation id = stack.getItem().getRegistryName();
        if (id != null) result.add(new Attribute("added_by", id.getNamespace(), false));
        if (stack.getItem().getCreativeTab() != null)
            result.add(new Attribute("item_group", stack.getItem().getCreativeTab().getTabLabel(), false));
        List<String> ores = new ArrayList<>();
        for (int oreId : OreDictionary.getOreIDs(stack)) ores.add(OreDictionary.getOreName(oreId));
        ores.sort(String::compareTo);
        for (String ore : ores) result.add(new Attribute("ore", ore, false));
        EnchantmentHelper.getEnchantments(stack).keySet().stream().map(Enchantment::getRegistryName)
                .filter(java.util.Objects::nonNull).map(ResourceLocation::toString).sorted()
                .forEach(name -> result.add(new Attribute("enchantment", name, false)));
        FluidStack fluid = FluidContainerHelper.contents(stack);
        if (fluid != null) result.add(new Attribute("fluid", fluid.getFluid().getName(), false));
        if (stack.hasDisplayName()) result.add(new Attribute("name", stack.getDisplayName(), false));
        String color = color(stack);
        if (!color.isEmpty()) result.add(new Attribute("color", color, false));
        String shulker = shulkerLevel(stack);
        if (!shulker.isEmpty()) result.add(new Attribute("shulker_level", shulker, false));
        if (stack.getItem() == Items.WRITTEN_BOOK && stack.hasTagCompound()) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag.hasKey("author")) result.add(new Attribute("author", tag.getString("author"), false));
            result.add(new Attribute("book_generation", Integer.toString(tag.getInteger("generation")), false));
        }
        return result;
    }
    private static String shulkerLevel(ItemStack stack) {
        if (!(Block.getBlockFromItem(stack.getItem()) instanceof BlockShulkerBox)) return "";
        NBTTagCompound root = stack.getTagCompound();
        if (root == null || !root.hasKey("BlockEntityTag", 10)) return "empty";
        NBTTagCompound block = root.getCompoundTag("BlockEntityTag");
        if (block.hasKey("LootTable")) return "";
        NBTTagList contents = block.getTagList("Items", 10);
        if (contents.tagCount() == 0) return "empty";
        boolean[] full = new boolean[27];
        for (int i = 0; i < contents.tagCount(); i++) {
            NBTTagCompound entry = contents.getCompoundTagAt(i); int slot = entry.getByte("Slot") & 255;
            ItemStack item = new ItemStack(entry);
            if (slot < full.length && !item.isEmpty() && item.getCount() >= item.getMaxStackSize()) full[slot] = true;
        }
        for (boolean slot : full) if (!slot) return "partial";
        return "full";
    }
    private static String color(ItemStack stack) {
        if (stack.getItem() == Items.DYE) return EnumDyeColor.byDyeDamage(stack.getMetadata()).getName();
        Block block = Block.getBlockFromItem(stack.getItem());
        if (block == Blocks.WOOL || block == Blocks.STAINED_GLASS || block == Blocks.STAINED_GLASS_PANE
                || block == Blocks.STAINED_HARDENED_CLAY || block == Blocks.CARPET)
            return EnumDyeColor.byMetadata(stack.getMetadata()).getName();
        if (stack.getItem() instanceof ItemArmor armor && armor.hasColor(stack))
            return Integer.toHexString(armor.getColor(stack));
        return "";
    }
    public record Attribute(String type, String value, boolean inverted) {
        public Attribute invert() { return new Attribute(type, value, true); }
        public boolean applies(ItemStack stack) {
            if (stack.isEmpty()) return false;
            return switch (type) {
                case "placeable" -> stack.getItem() instanceof ItemBlock;
                case "consumable" -> stack.getItem() instanceof ItemFood;
                case "fluid_container" -> FluidUtil.getFluidHandler(stack) != null;
                case "enchanted" -> stack.isItemEnchanted();
                case "max_enchanted" -> EnchantmentHelper.getEnchantments(stack).entrySet().stream()
                        .anyMatch(entry -> entry.getValue() >= entry.getKey().getMaxLevel());
                case "renamed" -> stack.hasDisplayName();
                case "damaged" -> stack.isItemDamaged();
                case "badly_damaged" -> stack.isItemDamaged() && stack.getMaxDamage() > 0
                        && (float) stack.getItemDamage() / stack.getMaxDamage() > 0.75F;
                case "not_stackable" -> !stack.isStackable();
                case "equipable" -> EntityLiving.getSlotForItemStack(stack).getSlotType() == EntityEquipmentSlot.Type.ARMOR;
                case "furnace_fuel" -> TileEntityFurnace.isItemFuel(stack);
                case "smeltable" -> !FurnaceRecipes.instance().getSmeltingResult(stack).isEmpty();
                case "washable" -> com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.find("splashing", stack) != null;
                case "hauntable" -> com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.find("haunting", stack) != null;
                case "crushable" -> com.cogwork.cogworkbridge.content.kinetics.crusher.CrushingRecipes.accepts(stack)
                        || com.cogwork.cogworkbridge.content.processing.millstone.MillstoneRecipes.find(stack) != null;
                case "smokable" -> xy177.farmersfuturedelight.api.WorkstationRecipes.find(
                        xy177.farmersfuturedelight.api.WorkstationRecipes.Type.SMOKING, java.util.Collections.singletonList(stack)) != null;
                case "blastable" -> xy177.farmersfuturedelight.api.WorkstationRecipes.find(
                        xy177.farmersfuturedelight.api.WorkstationRecipes.Type.BLASTING, java.util.Collections.singletonList(stack)) != null;
                case "compostable" -> xy177.farmersfuturedelight.api.CompostingRules.find(stack) != null;
                case "added_by" -> stack.getItem().getRegistryName() != null
                        && value.equals(stack.getItem().getRegistryName().getNamespace());
                case "item_group" -> stack.getItem().getCreativeTab() != null
                        && value.equals(stack.getItem().getCreativeTab().getTabLabel());
                case "ore" -> {
                    boolean found = false;
                    for (int id : OreDictionary.getOreIDs(stack)) if (value.equals(OreDictionary.getOreName(id))) found = true;
                    yield found;
                }
                case "enchantment" -> EnchantmentHelper.getEnchantments(stack).keySet().stream()
                        .anyMatch(enchantment -> enchantment.getRegistryName() != null
                                && value.equals(enchantment.getRegistryName().toString()));
                case "fluid" -> {
                    FluidStack fluid = FluidContainerHelper.contents(stack);
                    yield fluid != null && value.equals(fluid.getFluid().getName());
                }
                case "name" -> stack.hasDisplayName() && value.equals(stack.getDisplayName());
                case "color" -> value.equals(color(stack));
                case "shulker_level" -> value.equals(shulkerLevel(stack));
                case "author" -> stack.getItem() == Items.WRITTEN_BOOK && stack.hasTagCompound()
                        && value.equals(stack.getTagCompound().getString("author"));
                case "book_generation" -> stack.getItem() == Items.WRITTEN_BOOK && stack.hasTagCompound()
                        && value.equals(Integer.toString(stack.getTagCompound().getInteger("generation")));
                default -> false;
            };
        }
    }
}
