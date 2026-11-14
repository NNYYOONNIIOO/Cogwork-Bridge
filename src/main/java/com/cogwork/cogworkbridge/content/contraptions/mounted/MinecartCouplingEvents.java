package com.cogwork.cogworkbridge.content.contraptions.mounted;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class MinecartCouplingEvents {
    private static final Map<EntityPlayer, UUID> SELECTIONS = new WeakHashMap<>();
    private MinecartCouplingEvents() { }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interact(PlayerInteractEvent.EntityInteract event) { interact(event, event.getTarget()); }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interactAt(PlayerInteractEvent.EntityInteractSpecific event) { interact(event, event.getTarget()); }
    private static void interact(PlayerInteractEvent event, Entity target) {
        EntityPlayer player = event.getEntityPlayer();
        if (!(target instanceof EntityMinecart cart) || cart.isDead
                || target instanceof com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity) return;
        ItemStack held = player.getHeldItem(event.getHand());
        if (held.getItem() != AllItems.MINECART_COUPLING && held.getItem() != AllItems.WRENCH) return;
        if (player.world.isRemote) return;
        WorldServer world = (WorldServer) player.world;
        MinecartCouplingData data = MinecartCouplingData.get(world);
        if (held.getItem() == AllItems.WRENCH) {
            if (data.degree(cart.getUniqueID()) == 0) return;
            int removed = data.remove(world, cart.getUniqueID());
            if (!player.capabilities.isCreativeMode && removed > 0) {
                ItemStack returned = new ItemStack(AllItems.MINECART_COUPLING, removed);
                if (!player.inventory.addItemStackToInventory(returned)) player.dropItem(returned, false);
            }
            status(player, "removed");
        } else {
            if (player.isSneaking()) { SELECTIONS.remove(player); status(player, "cleared"); }
            else {
                UUID chosen = SELECTIONS.get(player);
                if (chosen == null || chosen.equals(cart.getUniqueID())) {
                    if (data.degree(cart.getUniqueID()) >= 2) status(player, "two_couplings_max");
                    else { SELECTIONS.put(player, cart.getUniqueID()); status(player, "select_second"); }
                } else {
                    SELECTIONS.remove(player);
                    Entity other = world.getEntityFromUuid(chosen);
                    if (!(other instanceof EntityMinecart first) || first.isDead) status(player, "unloaded");
                    else {
                        int length = Math.max(2, (int) first.getDistance(cart));
                        if (length > 32) status(player, "too_far");
                        else if (data.degree(first.getUniqueID()) >= 2 || data.degree(cart.getUniqueID()) >= 2)
                            status(player, "two_couplings_max");
                        else if (data.connected(first.getUniqueID(), cart.getUniqueID())) status(player, "no_loops");
                        else if (data.add(world, first, cart, length)) {
                            if (!player.capabilities.isCreativeMode) held.shrink(1);
                            status(player, "connected");
                        } else status(player, "unavailable");
                    }
                }
            }
        }
        event.setCancellationResult(EnumActionResult.SUCCESS);
        event.setCanceled(true);
    }
    private static void status(EntityPlayer player, String key) {
        player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.minecart_coupling." + key), true);
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntityPlayer() instanceof EntityPlayerMP player && event.getTarget() instanceof EntityMinecart cart)
            MinecartCouplingData.get(player.world).sync(player, cart.getUniqueID());
    }
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.world instanceof WorldServer world)
            MinecartCouplingData.get(world).tick(world);
    }
}
