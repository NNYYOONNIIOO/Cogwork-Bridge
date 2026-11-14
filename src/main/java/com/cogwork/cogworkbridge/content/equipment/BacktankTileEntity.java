package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;

public final class BacktankTileEntity extends KineticTileEntity {
    private int air;
    private int timer;
    private int ticksSinceSync;
    private NBTTagCompound itemData;

    public int getAir() { return air; }
    public void setFromItem(ItemStack stack) {
        itemData = stack.hasTagCompound() ? stack.getTagCompound().copy() : null;
        air = BacktankItem.getAir(stack);
        markDirty();
        sync();
    }
    public void applyToItem(ItemStack stack) {
        if (itemData != null) stack.setTagCompound(itemData.copy());
        BacktankItem.setAir(stack, air);
    }
    @Override public void update() {
        super.update();
        int maxAir = maxAir();
        if (world == null || world.isRemote || getSpeed() == 0F || air >= maxAir) return;
        ticksSinceSync++;
        if (timer > 0) { timer--; return; }
        float speed = Math.abs(getSpeed());
        int previous = comparator();
        air = Math.min(maxAir, air + MathHelper.clamp(((int) speed - 100) / 20, 1, 5));
        timer = MathHelper.clamp((int) (128F - speed / 5F) - 108, 0, 20);
        markDirty();
        if (comparator() != previous) world.updateComparatorOutputLevel(pos, getBlockType());
        if (air == maxAir || ticksSinceSync >= 20) { ticksSinceSync = 0; sync(); }
    }
    private int maxAir() {
        ItemStack stack = new ItemStack(com.cogwork.cogworkbridge.registry.AllItems.COPPER_BACKTANK);
        if (itemData != null) stack.setTagCompound(itemData.copy());
        return BacktankItem.maxAir(stack);
    }
    public int comparator() { return air <= 0 ? 0 : 1 + air * 14 / maxAir(); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setInteger("Air", air); tag.setInteger("AirTimer", timer);
        if (itemData != null) tag.setTag("ItemData", itemData.copy());
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        itemData = tag.hasKey("ItemData", 10) ? tag.getCompoundTag("ItemData").copy() : null;
        air = MathHelper.clamp(tag.getInteger("Air"), 0, maxAir());
        timer = tag.getInteger("AirTimer");
    }
    @Override public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        itemData = tag.hasKey("ItemData", 10) ? tag.getCompoundTag("ItemData").copy() : null;
        air = MathHelper.clamp(tag.getInteger("Air"), 0, maxAir());
        timer = tag.getInteger("AirTimer");
    }
}
