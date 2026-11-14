package com.cogwork.cogworkbridge.content.trains.track;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.decoration.SeatEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid=Reference.MOD_ID)
public final class TrainScheduleEvents {
    @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract event) {
        EntityPlayer player=event.getEntityPlayer();Entity target=event.getTarget();
        if(player.isSpectator()||event.getHand()!=net.minecraft.util.EnumHand.MAIN_HAND||player.getCooldownTracker().hasCooldown(com.cogwork.cogworkbridge.registry.AllItems.SCHEDULE)
                ||!(target.getRidingEntity() instanceof SeatEntity seat))return;
        BearingContraptionEntity body=seat.getBoundContraption();
        if(body==null||!(body.getMountedCart() instanceof RailwayCarriageEntity carriage))return;
        if(player.world.isRemote){event.setCancellationResult(EnumActionResult.SUCCESS);event.setCanceled(true);return;}
        if(!carriage.validConductorSeat(seat))return;
        var leader=carriage.trainController();if(leader==null)return;
        if(!leader.hasSchedule()||player.getHeldItem(event.getHand()).getItem()==com.cogwork.cogworkbridge.registry.AllItems.SCHEDULE)return;
        if(leader.resumeSchedule()) {
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("cogworkbridge.schedule.continued"),true);
            player.getCooldownTracker().setCooldown(com.cogwork.cogworkbridge.registry.AllItems.SCHEDULE,5);
            event.setCancellationResult(EnumActionResult.SUCCESS);event.setCanceled(true);return;
        }
        if(!player.getHeldItem(event.getHand()).isEmpty()) {
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("cogworkbridge.schedule.remove_with_empty_hand"),true);
            event.setCancellationResult(EnumActionResult.SUCCESS);event.setCanceled(true);return;
        }
        ItemStack schedule=carriage.removeSchedule();if(schedule.isEmpty())return;
        net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(player,schedule);
        player.getCooldownTracker().setCooldown(com.cogwork.cogworkbridge.registry.AllItems.SCHEDULE,5);
        event.setCancellationResult(EnumActionResult.SUCCESS);event.setCanceled(true);
    }
}
