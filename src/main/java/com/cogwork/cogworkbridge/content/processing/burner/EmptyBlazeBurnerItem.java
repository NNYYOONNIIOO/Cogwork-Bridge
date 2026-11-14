package com.cogwork.cogworkbridge.content.processing.burner;

import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;

public final class EmptyBlazeBurnerItem extends ItemBlock {
    public EmptyBlazeBurnerItem(Block block) { super(block); }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer player, EntityLivingBase target, EnumHand hand) {
        if (!(target instanceof EntityBlaze) || !target.isEntityAlive()) return false;
        if (!player.world.isRemote) {
            target.setDead();
            capture(player, hand);
            player.world.playSound(null, target.getPosition(), SoundEvents.ENTITY_BLAZE_AMBIENT,
                    SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
        return true;
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntityMobSpawner spawner)) return EnumActionResult.PASS;
        NBTTagCompound data = spawner.getSpawnerBaseLogic().writeToNBT(new NBTTagCompound());
        String entity = data.getCompoundTag("SpawnData").getString("id");
        if (!"minecraft:blaze".equals(entity) && !"Blaze".equals(entity)) return EnumActionResult.PASS;
        if (!player.canPlayerEdit(pos, side, player.getHeldItem(hand))) return EnumActionResult.FAIL;
        if (!world.isRemote) {
            capture(player, hand);
            world.playSound(null, pos, SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
        return EnumActionResult.SUCCESS;
    }

    private static void capture(EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        ItemStack filled = new ItemStack(AllBlocks.BLAZE_BURNER);
        if (!player.capabilities.isCreativeMode) {
            held.shrink(1);
            if (held.isEmpty()) {
                player.setHeldItem(hand, filled);
                return;
            }
        }
        ItemHandlerHelper.giveItemToPlayer(player, filled);
    }
}
