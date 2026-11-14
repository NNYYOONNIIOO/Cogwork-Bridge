package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;
import java.util.UUID;

/** Applies Forge's reach attribute on both sides, so the server accepts the same reach as the client. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class ExtendoGripItem extends Item {
    private static final UUID REACH_ID = UUID.fromString("d50ff755-d7aa-4e82-a35c-710ec0f1d00e");
    private static final int BACKTANK_ACTIONS = 1000;
    private record PendingKnockback(long tick, float bonus) { }
    private static final java.util.Map<net.minecraft.entity.EntityLivingBase, PendingKnockback> KNOCKBACK = new java.util.WeakHashMap<>();

    public ExtendoGripItem() {
        setCreativeTab(ModCreativeTab.INSTANCE);
        setMaxStackSize(1);
        setMaxDamage(200);
    }

    @SubscribeEvent
    public static void updateReach(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        EntityPlayer player = event.player;
        IAttributeInstance reach = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE);
        if (reach == null) return;
        int count = (player.getHeldItemMainhand().getItem() == AllItems.EXTENDO_GRIP ? 1 : 0)
                + (player.getHeldItemOffhand().getItem() == AllItems.EXTENDO_GRIP ? 1 : 0);
        double amount = count == 2 ? 5D : count == 1 ? 3D : 0D;
        AttributeModifier current = reach.getModifier(REACH_ID);
        if (current != null && current.getAmount() == amount) return;
        if (current != null) reach.removeModifier(current);
        if (amount != 0D) {
            AttributeModifier modifier = new AttributeModifier(REACH_ID, "Create extendo grip", amount, 0);
            modifier.setSaved(false);
            reach.applyModifier(modifier);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled()) consume(event.getPlayer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.PlaceEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof EntityPlayer player) consume(player);
    }

    public static boolean isHolding(EntityPlayer player) {
        return player != null && (player.getHeldItemMainhand().getItem() == AllItems.EXTENDO_GRIP
                || player.getHeldItemOffhand().getItem() == AllItems.EXTENDO_GRIP);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rememberAttack(LivingAttackEvent event) {
        if (event.getEntityLiving().world.isRemote) return;
        KNOCKBACK.remove(event.getEntityLiving());
        if (!event.isCanceled() && event.getSource().getTrueSource() instanceof EntityPlayer attacker) consume(attacker);
        if (!event.isCanceled() && event.getSource().getImmediateSource() instanceof EntityPlayer player
                && isHolding(player)) {
            int grips = (player.getHeldItemMainhand().getItem() == AllItems.EXTENDO_GRIP ? 1 : 0)
                    + (player.getHeldItemOffhand().getItem() == AllItems.EXTENDO_GRIP ? 1 : 0);
            KNOCKBACK.put(event.getEntityLiving(), new PendingKnockback(player.world.getTotalWorldTime(), grips * 2F));
        }
    }

    @SubscribeEvent
    public static void extendKnockback(LivingKnockBackEvent event) {
        if (event.getEntityLiving().world.isRemote) return;
        PendingKnockback pending = KNOCKBACK.remove(event.getEntityLiving());
        if (pending != null && pending.tick() == event.getEntityLiving().world.getTotalWorldTime())
            event.setStrength(event.getStrength() + pending.bonus());
    }

    private static void consume(EntityPlayer player) {
        if (player == null || player.world.isRemote || player.capabilities.isCreativeMode) return;
        ItemStack grip = player.getHeldItemMainhand();
        if (grip.getItem() != AllItems.EXTENDO_GRIP) grip = player.getHeldItemOffhand();
        if (grip.getItem() != AllItems.EXTENDO_GRIP) return;
        List<ItemStack> tanks = BacktankItem.getEquippedWithAir(player);
        if (!tanks.isEmpty()) {
            BacktankItem.consumeAir(player, tanks.get(0), Math.max(BacktankItem.MAX_AIR / BACKTANK_ACTIONS, 1));
        } else {
            grip.damageItem(1, player);
        }
    }

    @SideOnly(Side.CLIENT)
    private static List<ItemStack> displayedBacktanks() {
        return BacktankItem.getEquippedWithAir(net.minecraft.client.Minecraft.getMinecraft().player);
    }

    @Override @SideOnly(Side.CLIENT)
    public boolean showDurabilityBar(ItemStack stack) {
        return !displayedBacktanks().isEmpty() || super.showDurabilityBar(stack);
    }

    @Override @SideOnly(Side.CLIENT)
    public double getDurabilityForDisplay(ItemStack stack) {
        List<ItemStack> tanks = displayedBacktanks();
        if (tanks.isEmpty()) return super.getDurabilityForDisplay(stack);
        int totalWidth = 0;
        for (ItemStack tank : tanks) {
            totalWidth += Math.round(13F * BacktankItem.getAir(tank) / BacktankItem.maxAir(tank));
        }
        return 1D - Math.round((float) totalWidth / tanks.size()) / 13D;
    }

    @Override @SideOnly(Side.CLIENT)
    public int getRGBDurabilityForDisplay(ItemStack stack) {
        List<ItemStack> tanks = displayedBacktanks();
        return tanks.isEmpty() ? super.getRGBDurabilityForDisplay(stack)
                : tanks.get(0).getItem().getRGBDurabilityForDisplay(tanks.get(0));
    }
}
