package com.cogwork.cogworkbridge.content.logistics.packagebox;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import com.cogwork.cogworkbridge.content.fluids.ListFilterGuiHandler;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import javax.annotation.Nullable;
import java.util.List;

public final class PackageFilterItem extends Item {
    public PackageFilterItem() { setCreativeTab(ModCreativeTab.INSTANCE); }
    public static String address(ItemStack stack) { return stack.hasTagCompound() ? stack.getTagCompound().getString("PackageAddress") : ""; }
    public static void setAddress(ItemStack stack, String value) {
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        tag.setString("PackageAddress", value.substring(0, Math.min(25, value.length()))); stack.setTagCompound(tag);
    }
    public static boolean matches(ItemStack filter, ItemStack candidate) {
        return PackageItem.isPackage(candidate) && PackageItem.matchAddress(PackageItem.address(candidate), address(filter));
    }
    private EnumActionResult open(World world, EntityPlayer player, EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND || player.isSneaking()) return EnumActionResult.PASS;
        if (!world.isRemote) player.openGui(CogworkBridge.instance, ListFilterGuiHandler.PACKAGE_GUI, world, player.inventory.currentItem, 0, 0);
        return EnumActionResult.SUCCESS;
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) { return new ActionResult<>(open(world, player, hand), player.getHeldItem(hand)); }
    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side, float x, float y, float z) { return open(world, player, hand); }
    @Override public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) { String address = address(stack); if (!address.isEmpty()) tooltip.add("§7→ §6" + address); }
}
