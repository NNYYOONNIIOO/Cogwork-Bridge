package com.cogwork.cogworkbridge.content.schematics;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import javax.annotation.Nullable;
import java.util.List;

public final class SchematicItem extends Item {
    public SchematicItem() { setMaxStackSize(1); setCreativeTab(ModCreativeTab.INSTANCE); }
    @Override public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
            EnumFacing face, float x, float y, float z, EnumHand hand) {
        net.minecraft.block.Block target = world.getBlockState(pos).getBlock();
        if (target == com.cogwork.cogworkbridge.registry.AllBlocks.SCHEMATICANNON
                || target == com.cogwork.cogworkbridge.registry.AllBlocks.DEPLOYER) return EnumActionResult.PASS;
        return onItemUse(player, world, pos, hand, face, x, y, z);
    }
    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing face, float x, float y, float z) {
        if (hand != EnumHand.MAIN_HAND) return EnumActionResult.PASS;
        ItemStack stack = player.getHeldItem(hand);
        if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey("FileId")) return EnumActionResult.PASS;
        if (player.isSneaking()) {
            if (world.isRemote) CogworkBridge.proxy.openSchematicEdit();
        } else if (!world.isRemote) {
            NBTTagCompound data = stack.getTagCompound();
            int[] bounds = SchematicData.horizontalBounds(data.getInteger("Width"), data.getInteger("Depth"),
                    SchematicData.mirror(stack), SchematicData.rotation(stack));
            BlockPos target = pos.offset(face);
            data.setLong("Anchor", target.add(-Math.floorDiv(bounds[0] + bounds[1], 2), 0,
                    -Math.floorDiv(bounds[2] + bounds[3], 2)).toLong());
            data.setBoolean("Deployed", true);
            player.inventory.markDirty();
        }
        return EnumActionResult.SUCCESS;
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (hand != EnumHand.MAIN_HAND || !stack.hasTagCompound() || !player.isSneaking())
            return new ActionResult<>(EnumActionResult.PASS, stack);
        if (world.isRemote) CogworkBridge.proxy.openSchematicEdit();
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
    @SideOnly(Side.CLIENT)
    @Override public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag advanced) {
        if (stack.hasTagCompound()) tooltip.add(net.minecraft.util.text.TextFormatting.GOLD + stack.getTagCompound().getString("Name"));
    }
}
