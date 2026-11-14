package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BacktankItem extends DivingGearItem {
    public static final int MAX_AIR = 900;
    public BacktankItem(boolean netherite) {
        super(EntityEquipmentSlot.CHEST, netherite);
        setMaxDamage(0);
    }
    public static final class Netherite extends BacktankItem implements xy177.farmersfuturedelight.common.item.INetheriteItem {
        public Netherite() { super(true); }
    }
    public static int getAir(ItemStack stack) {
        return stack.hasTagCompound() ? Math.max(0, Math.min(maxAir(stack), stack.getTagCompound().getInteger("Air"))) : 0;
    }
    public static List<ItemStack> getEquippedWithAir(EntityLivingBase entity) {
        List<ItemStack> tanks = new ArrayList<>();
        if (entity == null) return tanks;
        for (ItemStack stack : entity.getArmorInventoryList()) {
            if (stack.getItem() instanceof BacktankItem && getAir(stack) > 0) tanks.add(stack);
        }
        tanks.sort(Comparator.comparingInt(BacktankItem::getAir));
        return tanks;
    }
    public static int maxAir(ItemStack stack) {
        return MAX_AIR + 300 * EnchantmentHelper.getEnchantmentLevel(com.cogwork.cogworkbridge.registry.AllEnchantments.CAPACITY, stack);
    }
    public static void setAir(ItemStack stack, int air) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger("Air", Math.max(0, Math.min(maxAir(stack), air)));
    }
    public static void consumeAir(net.minecraft.entity.EntityLivingBase entity, ItemStack stack, int amount) {
        int previous = getAir(stack);
        int remaining = Math.max(0, previous - Math.max(0, amount));
        setAir(stack, remaining);
        if (entity.world.isRemote || !(entity instanceof net.minecraft.entity.player.EntityPlayerMP player)) return;
        boolean low = previous > maxAir(stack) / 10 && remaining <= maxAir(stack) / 10;
        boolean depleted = previous > 1 && remaining <= 1;
        if (low || depleted) {
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                    depleted ? "gui.cogworkbridge.backtank.depleted" : "gui.cogworkbridge.backtank.low"), true);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    net.minecraft.init.SoundEvents.BLOCK_NOTE_PLING, net.minecraft.util.SoundCategory.PLAYERS, .5F, 1.25F);
        }
    }
    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public void addInformation(ItemStack stack, @javax.annotation.Nullable World world, java.util.List<String> tooltip,
                               net.minecraft.client.util.ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        tooltip.add(net.minecraft.client.resources.I18n.format("gui.cogworkbridge.backtank.air", getAir(stack), maxAir(stack)));
    }
    @Override public boolean showDurabilityBar(ItemStack stack) { return true; }
    @Override public boolean isEnchantable(ItemStack stack) { return true; }
    @Override public double getDurabilityForDisplay(ItemStack stack) { return 1D - getAir(stack) / (double) maxAir(stack); }
    @Override public int getRGBDurabilityForDisplay(ItemStack stack) { return 0xEFEFEF; }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public net.minecraft.client.model.ModelBiped getArmorModel(net.minecraft.entity.EntityLivingBase entity, ItemStack stack,
                                                              EntityEquipmentSlot slot, net.minecraft.client.model.ModelBiped original) {
        net.minecraft.client.model.ModelBiped hidden = new net.minecraft.client.model.ModelBiped();
        hidden.setVisible(false);
        return hidden;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        BlockPos target = world.getBlockState(pos).getBlock().isReplaceable(world, pos) ? pos : pos.offset(facing);
        Block block = netherite ? AllBlocks.NETHERITE_BACKTANK : AllBlocks.COPPER_BACKTANK;
        if (!player.canPlayerEdit(target, facing, stack) || !world.mayPlace(block, target, false, facing, player)) return EnumActionResult.FAIL;
        if (world.isRemote) return EnumActionResult.SUCCESS;
        IBlockState state = block.getStateForPlacement(world, target, facing, hitX, hitY, hitZ, 0, player);
        if (!world.setBlockState(target, state, 3)) return EnumActionResult.FAIL;
        if (world.getTileEntity(target) instanceof BacktankTileEntity tank) tank.setFromItem(stack);
        if (!player.capabilities.isCreativeMode) stack.shrink(1);
        return EnumActionResult.SUCCESS;
    }
}
