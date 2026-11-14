package com.cogwork.cogworkbridge.content.decoration.palette;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class BracketEvents {
    private BracketEvents() { }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || event.getWorld().isRemote) return;
        TileEntity tile = event.getWorld().getTileEntity(event.getPos());
        BracketAttachment bracket = BracketAttachment.of(tile);
        if (bracket == null || !bracket.present()) return;
        EntityPlayer player = event.getPlayer();
        ItemStack item = bracket.item();
        BracketAttachment.remove(tile, null);
        if (player == null || !player.capabilities.isCreativeMode) Block.spawnAsEntity(event.getWorld(), event.getPos(), item);
    }
}
