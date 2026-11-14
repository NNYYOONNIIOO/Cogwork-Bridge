package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.decoration.MetalScaffoldingBlock;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Supplies modern scaffolding controls absent from the 1.12 player movement code. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ScaffoldingClientEvents {
    private ScaffoldingClientEvents() { }

    public static boolean isHolding(Block block) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return false;
        Item item = Item.getItemFromBlock(block);
        ItemStack main = player.getHeldItemMainhand(), off = player.getHeldItemOffhand();
        return main.getItem() == item || off.getItem() == item;
    }

    @SubscribeEvent public static void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.player;
        if (player == null || event.getEntityLiving() != player || player.movementInput == null || minecraft.currentScreen != null
                || player.isSpectator() || player.capabilities.isFlying || player.isElytraFlying()
                || player.isRiding() || player.isInWater() || player.isInLava()) return;

        BlockPos feet = new BlockPos(player.posX, player.getEntityBoundingBox().minY + 1.0E-5D, player.posZ);
        IBlockState state = player.world.getBlockState(feet);
        boolean inside = state.getBlock() instanceof MetalScaffoldingBlock scaffold
                && !scaffold.getActualState(state, player.world, feet).getValue(MetalScaffoldingBlock.BOTTOM);
        boolean descending = player.movementInput.sneak;
        if (!inside && descending && player.onGround) {
            BlockPos below = feet.down();
            IBlockState support = player.world.getBlockState(below);
            inside = support.getBlock() instanceof MetalScaffoldingBlock scaffold
                    && !scaffold.getActualState(support, player.world, below).getValue(MetalScaffoldingBlock.BOTTOM)
                    && Math.abs(player.getEntityBoundingBox().minY - (below.getY() + 1D)) < .05D;
        }
        if (!inside) return;
        player.fallDistance = 0;
        if (descending) player.motionY = -.15D;
        else if (player.movementInput.jump) player.motionY = .2D;
    }
}
