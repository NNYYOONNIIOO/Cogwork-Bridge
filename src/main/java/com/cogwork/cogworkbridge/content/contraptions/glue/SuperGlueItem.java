package com.cogwork.cogworkbridge.content.contraptions.glue;

import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Selection token for super glue. Server-side area creation happens in the
 * selection packet so the client can show the full two-point selection first.
 */
public class SuperGlueItem extends Item {

    public static final int USES = 99;

    public SuperGlueItem() {
        setMaxStackSize(1);
        setMaxDamage(USES);
        setCreativeTab(ModCreativeTab.INSTANCE);
    }

    @Override
    public boolean canDestroyBlockInCreative(World world, BlockPos pos, ItemStack stack, EntityPlayer player) {
        return false;
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, net.minecraft.world.IBlockAccess world,
                                     BlockPos pos, EntityPlayer player) {
        return false;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        return EnumActionResult.SUCCESS;
    }
}
