package com.cogwork.cogworkbridge.content.trains.track;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.decoration.SeatEntity;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public final class TrainScheduleItem extends Item {
    public TrainScheduleItem(){setCreativeTab(ModCreativeTab.INSTANCE);}
    @Override public ActionResult<ItemStack> onItemRightClick(World world,EntityPlayer player,EnumHand hand) {
        if(hand!=EnumHand.MAIN_HAND||player.isSneaking())return super.onItemRightClick(world,player,hand);
        if(world.isRemote)open();
        return new ActionResult<>(EnumActionResult.SUCCESS,player.getHeldItem(hand));
    }
    @Override public EnumActionResult onItemUse(EntityPlayer player,World world,BlockPos pos,EnumHand hand,
            EnumFacing face,float x,float y,float z) {
        return onItemRightClick(world,player,hand).getType();
    }
    @SideOnly(Side.CLIENT) private static void open() {
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new com.cogwork.cogworkbridge.client.TrainScheduleScreen());
    }
    @Override public boolean itemInteractionForEntity(ItemStack stack,EntityPlayer player,EntityLivingBase target,EnumHand hand) {
        if(player.isSpectator()||player.getCooldownTracker().hasCooldown(this)||!(target.getRidingEntity() instanceof SeatEntity seat))return false;
        BearingContraptionEntity body=seat.getBoundContraption();
        if(body==null||!(body.getMountedCart() instanceof RailwayCarriageEntity carriage))return false;
        if(player.world.isRemote)return true;
        if(!carriage.validConductorSeat(seat)) {
            if(!player.world.isRemote)player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("cogworkbridge.schedule.non_controlling_seat"),true);
            return true;
        }
        if(!player.world.isRemote&&carriage.installSchedule(stack)) {
            if(!player.capabilities.isCreativeMode)stack.shrink(1);
            player.getCooldownTracker().setCooldown(this,5);
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("cogworkbridge.schedule.assigned"),true);
        } else if(carriage.trainController()!=null) {
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(carriage.trainController().hasSchedule()?"cogworkbridge.schedule.remove_with_empty_hand":"cogworkbridge.schedule.no_stops"),true);
        }
        return true;
    }
}
