package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class ToolboxLinks {
    private static final String ROOT = "CogworkBridgeToolboxLinks";
    private static NBTTagCompound links(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        NBTTagCompound persistent = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persistent.hasKey(ROOT, 10)) persistent.setTag(ROOT, new NBTTagCompound());
        return persistent.getCompoundTag(ROOT);
    }
    public static void equip(EntityPlayer player, ToolboxTileEntity box, int compartment, int hotbar) {
        if (hotbar < 0 || hotbar >= 9 || compartment < 0 || compartment >= 8) return;
        NBTTagCompound previous = links(player).getCompoundTag(Integer.toString(hotbar));
        if (previous.hasUniqueId("Id") && previous.getUniqueId("Id").equals(box.identity())
                && previous.getInteger("Compartment") == compartment) {
            refill(player, hotbar, previous);
            return;
        }
        toggle(player, box, compartment, hotbar);
    }
    public static void toggle(EntityPlayer player, ToolboxTileEntity box, int compartment, int hotbar) {
        if (hotbar < 0 || hotbar >= 9 || compartment < 0 || compartment >= 8) return;
        NBTTagCompound links = links(player); String key = Integer.toString(hotbar);
        NBTTagCompound previous = links.getCompoundTag(key);
        if (previous.hasUniqueId("Id") && previous.getUniqueId("Id").equals(box.identity()) && previous.getInteger("Compartment") == compartment) {
            detach(player, hotbar, true);
            return;
        }
        ItemStack sample = box.filter(compartment).copy();
        if (sample.isEmpty() || box.count(compartment) == 0) return;
        detach(player, hotbar, false);
        ItemStack held = player.inventory.getStackInSlot(hotbar);
        if (!held.isEmpty() && (!ItemStack.areItemsEqual(held, sample) || !ItemStack.areItemStackTagsEqual(held, sample))) {
            player.inventory.setInventorySlotContents(hotbar, ItemStack.EMPTY);
            ItemStack remaining = box.deposit(held);
            if (!remaining.isEmpty()) returnToInventory(player, remaining, hotbar);
            if (!remaining.isEmpty()) player.inventory.setInventorySlotContents(hotbar, remaining);
        }
        sample.setCount(1);
        NBTTagCompound entry = new NBTTagCompound();
        entry.setUniqueId("Id", box.identity()); entry.setLong("Pos", box.getPos().toLong());
        entry.setInteger("Dimension", player.dimension); entry.setInteger("Compartment", compartment);
        entry.setTag("Item", sample.writeToNBT(new NBTTagCompound())); links.setTag(key, entry);
        player.sendStatusMessage(new TextComponentTranslation("gui.cogworkbridge.toolbox.connected", hotbar + 1), true);
        refill(player, hotbar, entry);
        sync(player);
    }
    private static void returnToInventory(EntityPlayer player, ItemStack remaining, int excludedSlot) {
        for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
            for (int slot = 0; slot < player.inventory.mainInventory.size() && !remaining.isEmpty(); slot++) {
                if (slot == excludedSlot) continue;
                ItemStack target = player.inventory.getStackInSlot(slot);
                if (pass == 0 && (target.isEmpty() || !ItemStack.areItemsEqual(target, remaining)
                        || !ItemStack.areItemStackTagsEqual(target, remaining))) continue;
                if (pass == 1 && !target.isEmpty()) continue;
                int available = target.isEmpty() ? remaining.getMaxStackSize() : target.getMaxStackSize() - target.getCount();
                int amount = Math.min(remaining.getCount(), available);
                if (amount <= 0) continue;
                if (target.isEmpty()) { ItemStack copy = remaining.copy(); copy.setCount(amount); player.inventory.setInventorySlotContents(slot, copy); }
                else target.grow(amount);
                remaining.shrink(amount);
            }
        }
    }
    public static void detach(EntityPlayer player, int hotbar, boolean notify) {
        if (hotbar < 0 || hotbar >= 9) return;
        NBTTagCompound data = links(player), previous = data.getCompoundTag(Integer.toString(hotbar));
        if (!previous.hasUniqueId("Id")) return;
        BlockPos pos = BlockPos.fromLong(previous.getLong("Pos"));
        if (previous.getInteger("Dimension") == player.dimension && player.world.isBlockLoaded(pos)
                && player.getDistanceSq(pos) < 100D && player.world.getTileEntity(pos) instanceof ToolboxTileEntity box
                && box.identity().equals(previous.getUniqueId("Id"))) {
            ItemStack held = player.inventory.getStackInSlot(hotbar);
            ItemStack sample = new ItemStack(previous.getCompoundTag("Item"));
            if (!held.isEmpty() && ItemStack.areItemsEqual(held, sample) && ItemStack.areItemStackTagsEqual(held, sample))
                player.inventory.setInventorySlotContents(hotbar, box.deposit(held));
        }
        data.removeTag(Integer.toString(hotbar));
        player.inventory.markDirty();
        if (notify) player.sendStatusMessage(new TextComponentTranslation("gui.cogworkbridge.toolbox.disconnected"), true);
        sync(player);
    }
    public static void deposit(EntityPlayer player, ToolboxTileEntity box) {
        for (int slot = 0; slot < 9; slot++) {
            NBTTagCompound previous = links(player).getCompoundTag(Integer.toString(slot));
            if (previous.hasUniqueId("Id") && previous.getUniqueId("Id").equals(box.identity())) detach(player, slot, false);
        }
        for (int slot = 0; slot < player.inventory.mainInventory.size(); slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) player.inventory.setInventorySlotContents(slot, box.deposit(stack));
        }
        player.inventory.markDirty();
        sync(player);
    }
    public static NBTTagCompound snapshot(EntityPlayer player) { return links(player).copy(); }
    public static void applyClient(EntityPlayer player, NBTTagCompound data) {
        NBTTagCompound root = player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        root.setTag(ROOT, data.copy()); player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, root);
    }
    private static void sync(EntityPlayer player) {
        if (player instanceof net.minecraft.entity.player.EntityPlayerMP server)
            com.cogwork.cogworkbridge.network.ModNetwork.CHANNEL.sendTo(new ToolboxSyncPacket(snapshot(player)), server);
    }
    @SubscribeEvent public static void login(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) { sync(event.player); }
    @SubscribeEvent public static void update(TickEvent.PlayerTickEvent event) {
        EntityPlayer player = event.player;
        if (event.phase != TickEvent.Phase.END || player.world.isRemote || player.ticksExisted % 5 != 0) return;
        NBTTagCompound links = links(player);
        for (int slot = 0; slot < 9; slot++) if (links.hasKey(Integer.toString(slot), 10)) refill(player, slot, links.getCompoundTag(Integer.toString(slot)));
    }
    private static void refill(EntityPlayer player, int hotbar, NBTTagCompound link) {
        if (link.getInteger("Dimension") != player.dimension || !link.hasUniqueId("Id")) return;
        BlockPos pos = BlockPos.fromLong(link.getLong("Pos"));
        if (!player.world.isBlockLoaded(pos) || player.getDistanceSq(pos) >= 100D
                || !(player.world.getTileEntity(pos) instanceof ToolboxTileEntity box) || !box.identity().equals(link.getUniqueId("Id"))) return;
        int compartment = link.getInteger("Compartment");
        if (compartment < 0 || compartment >= 8) return;
        ItemStack sample = new ItemStack(link.getCompoundTag("Item"));
        if (sample.isEmpty()) return;
        ItemStack held = player.inventory.getStackInSlot(hotbar);
        if (!held.isEmpty() && (!ItemStack.areItemsEqual(held, sample) || !ItemStack.areItemStackTagsEqual(held, sample))) return;
        int needed = sample.getMaxStackSize() - held.getCount();
        boolean moved = false;
        for (int attempt = 0; attempt < 4 && needed > 0; attempt++) {
            int row = 0;
            while (row < 4 && box.getStackInSlot(row * 9 + compartment).isEmpty()) row++;
            if (row >= 4) break;
            int source = row * 9 + compartment; ItemStack stock = box.getStackInSlot(source);
            if (!ItemStack.areItemsEqual(stock, sample) || !ItemStack.areItemStackTagsEqual(stock, sample)) break;
            ItemStack extracted = box.decrStackSize(source, needed);
            moved |= !extracted.isEmpty();
            if (held.isEmpty()) { held = extracted; player.inventory.setInventorySlotContents(hotbar, held); }
            else held.grow(extracted.getCount());
            needed -= extracted.getCount();
            if (!extracted.isEmpty()) box.settle(compartment);
        }
        if (moved) { box.settle(compartment); player.inventory.markDirty(); }
    }
}
