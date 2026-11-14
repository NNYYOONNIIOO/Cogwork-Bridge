package com.cogwork.cogworkbridge.content.logistics.packagebox;

import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import javax.annotation.Nullable;
import java.util.List;
import java.util.regex.Pattern;

/** Nine-slot addressed parcel shared by packagers and the logistics network. */
public final class PackageItem extends Item {
    public static final int SLOTS = 9;
    private static final String DATA = "CogworkBridgePackage";
    private final int width, height;
    private final boolean rare;
    public PackageItem(int width, int height, boolean rare) { this.width = width; this.height = height; this.rare = rare; setMaxStackSize(1); setCreativeTab(ModCreativeTab.INSTANCE); }
    public int packageWidth() { return width; }
    public int packageHeight() { return height; }
    public static boolean isPackage(ItemStack stack) { return !stack.isEmpty() && stack.getItem() instanceof PackageItem; }
    @Override public boolean onEntityItemUpdate(EntityItem original) {
        if (original instanceof PackageEntity || original.world.isRemote || original.isDead) return false;
        // Convert after the original spawn has succeeded. Forge's immediate
        // custom-entity hook cancels that spawn, which can make transfer
        // callers restore an item that was already spawned.
        PackageEntity parcel = new PackageEntity(original.world, original.posX, original.posY, original.posZ, original.getItem().copy());
        parcel.motionX = original.motionX; parcel.motionY = original.motionY; parcel.motionZ = original.motionZ;
        parcel.rotationYaw = original.rotationYaw;
        if (original.world.spawnEntity(parcel)) { original.setDead(); return true; }
        return false;
    }
    private static NBTTagCompound data(ItemStack stack) {
        NBTTagCompound root = stack.getTagCompound();
        return root == null ? new NBTTagCompound() : root.getCompoundTag(DATA);
    }
    public static ItemStackHandler contents(ItemStack stack) {
        ItemStackHandler handler = new ItemStackHandler(SLOTS);
        NBTTagCompound tag = data(stack);
        if (tag.hasKey("Contents", 10)) handler.deserializeNBT(tag.getCompoundTag("Contents"));
        return handler;
    }
    public static void setContents(ItemStack box, ItemStackHandler handler) {
        if (!isPackage(box)) return;
        NBTTagCompound root = box.hasTagCompound() ? box.getTagCompound() : new NBTTagCompound();
        NBTTagCompound tag = data(box); tag.setTag("Contents", handler.serializeNBT()); root.setTag(DATA, tag); box.setTagCompound(root);
    }
    public static String address(ItemStack box) { return data(box).getString("Address"); }
    public static void setFragment(ItemStack box, java.util.UUID order, int index, boolean last) {
        NBTTagCompound root = box.hasTagCompound() ? box.getTagCompound() : new NBTTagCompound();
        NBTTagCompound tag = data(box), fragment = new NBTTagCompound();
        fragment.setUniqueId("Order", order); fragment.setInteger("Index", index); fragment.setBoolean("Last", last);
        tag.setTag("Fragment", fragment); root.setTag(DATA, tag); box.setTagCompound(root);
    }
    @Nullable public static java.util.UUID fragmentOrder(ItemStack box) {
        NBTTagCompound fragment = data(box).getCompoundTag("Fragment");
        return fragment.hasUniqueId("Order") ? fragment.getUniqueId("Order") : null;
    }
    public static int fragmentIndex(ItemStack box) { return data(box).getCompoundTag("Fragment").getInteger("Index"); }
    public static boolean lastFragment(ItemStack box) { return data(box).getCompoundTag("Fragment").getBoolean("Last"); }
    public static NBTTagCompound orderContext(ItemStack box) { return data(box).getCompoundTag("OrderContext").copy(); }
    public static void setOrderContext(ItemStack box, NBTTagCompound context) {
        if (!isPackage(box)) return;
        NBTTagCompound root = box.hasTagCompound() ? box.getTagCompound() : new NBTTagCompound();
        NBTTagCompound tag = data(box);
        if (context == null || context.isEmpty()) tag.removeTag("OrderContext");
        else tag.setTag("OrderContext", context.copy());
        root.setTag(DATA, tag); box.setTagCompound(root);
    }
    public static void setAddress(ItemStack box, String address) {
        NBTTagCompound root = box.hasTagCompound() ? box.getTagCompound() : new NBTTagCompound();
        NBTTagCompound tag = data(box); tag.setString("Address", address == null ? "" : address.substring(0, Math.min(25, address.length())));
        root.setTag(DATA, tag); box.setTagCompound(root);
    }
    public static boolean matchAddress(String boxAddress, String filter) {
        if (filter.trim().isEmpty()) return boxAddress.trim().isEmpty();
        if (filter.equals("*") || boxAddress.equals("*") || filter.equals(boxAddress)) return true;
        return glob(filter, boxAddress) || glob(boxAddress, filter);
    }
    private static boolean glob(String pattern, String value) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') regex.append(".*"); else if (c == '?') regex.append('.'); else regex.append(Pattern.quote(String.valueOf(c)));
        }
        return value.matches(regex.append('$').toString());
    }
    public static ItemStack create(ItemStackHandler contents, String address, java.util.Random random) {
        PackageItem[] pool = random.nextInt(7500) == 0 ? AllItems.RARE_PACKAGES : AllItems.CARDBOARD_PACKAGES;
        ItemStack result = new ItemStack(pool[random.nextInt(pool.length)]);
        setContents(result, contents); if (address != null && !address.isEmpty()) setAddress(result, address);
        return result;
    }
    private static EnumActionResult open(World world, EntityPlayer player, EnumHand hand) {
        ItemStack box = player.getHeldItem(hand);
        if (!isPackage(box)) return EnumActionResult.PASS;
        if (!world.isRemote) {
            ItemStackHandler items = contents(box); box.shrink(1);
            for (int slot = 0; slot < items.getSlots(); slot++) {
                ItemStack item = items.getStackInSlot(slot);
                if (!item.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, item.copy());
            }
            player.inventory.markDirty();
        }
        return EnumActionResult.SUCCESS;
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (player.isSneaking()) return new ActionResult<>(open(world, player, hand), player.getHeldItem(hand));
        player.setActiveHand(hand); return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }
    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing face, float x, float y, float z) {
        if (player.isSneaking()) return open(world, player, hand);
        if (!world.isRemote) {
            ItemStack held = player.getHeldItem(hand); ItemStack box = held.copy(); box.setCount(1);
            EntityItem entity = new PackageEntity(world, pos.getX() + x + face.getXOffset() * .4, pos.getY() + y + Math.max(0, face.getYOffset()) * .1, pos.getZ() + z + face.getZOffset() * .4, box);
            entity.motionX = entity.motionY = entity.motionZ = 0; entity.setPickupDelay(20);
            if (world.spawnEntity(entity) && !player.capabilities.isCreativeMode) held.shrink(1);
        }
        return EnumActionResult.SUCCESS;
    }
    @Override public EnumAction getItemUseAction(ItemStack stack) { return EnumAction.BOW; }
    @Override public int getMaxItemUseDuration(ItemStack stack) { return 72000; }
    @Override public void onPlayerStoppedUsing(ItemStack stack, World world, EntityLivingBase entity, int timeLeft) {
        if (world.isRemote || !(entity instanceof EntityPlayer player)) return;
        float f = (getMaxItemUseDuration(stack) - timeLeft) / 20F; f = Math.min(1F, (f * f + f * 2F) / 3F);
        if (f < .1F) return;
        var look = player.getLookVec(); ItemStack box = stack.copy(); box.setCount(1);
        EntityItem dropped = new PackageEntity(world, player.posX + look.x, player.posY + player.height * .5 + look.y, player.posZ + look.z, box);
        dropped.motionX = look.x * f * 2; dropped.motionY = look.y * f * 2; dropped.motionZ = look.z * f * 2;
        dropped.setPickupDelay(30); if (world.spawnEntity(dropped) && !player.capabilities.isCreativeMode) stack.shrink(1);
    }
    @Override public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag advanced) {
        String address = address(stack); if (!address.isEmpty()) tooltip.add("§6→ " + address);
        ItemStackHandler items = contents(stack); int names = 0, rest = 0;
        for (int i = 0; i < items.getSlots(); i++) if (!items.getStackInSlot(i).isEmpty()) {
            ItemStack item = items.getStackInSlot(i); if (names++ < 3) tooltip.add("§7" + item.getDisplayName() + " x" + item.getCount()); else rest++;
        }
        if (rest > 0) tooltip.add("§7+" + rest);
    }
}
