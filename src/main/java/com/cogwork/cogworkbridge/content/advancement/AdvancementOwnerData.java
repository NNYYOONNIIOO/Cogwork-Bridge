package com.cogwork.cogworkbridge.content.advancement;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import javax.annotation.Nullable;
import java.util.UUID;

/** Saved with the tile, including contraption snapshots and moved machines. */
public final class AdvancementOwnerData implements ICapabilitySerializable<NBTTagCompound> {
    @CapabilityInject(AdvancementOwnerData.class)
    public static Capability<AdvancementOwnerData> CAPABILITY = null;
    UUID owner;
    private NBTTagCompound counters = new NBTTagCompound();
    public static void register() {
        CapabilityManager.INSTANCE.register(AdvancementOwnerData.class, new Capability.IStorage<AdvancementOwnerData>() {
            @Override public NBTBase writeNBT(Capability<AdvancementOwnerData> cap, AdvancementOwnerData value, EnumFacing side) { return value.serializeNBT(); }
            @Override public void readNBT(Capability<AdvancementOwnerData> cap, AdvancementOwnerData value, EnumFacing side, NBTBase nbt) { value.deserializeNBT((NBTTagCompound) nbt); }
        }, AdvancementOwnerData::new);
    }
    @Nullable static AdvancementOwnerData get(@Nullable TileEntity tile) {
        return tile == null ? null : tile.getCapability(CAPABILITY, null);
    }
    int add(String key, int amount) {
        int result = counters.getInteger(key) + amount;
        counters.setInteger(key, result);
        return result;
    }
    int bits(String key, int flag) {
        int result = counters.getInteger(key) | flag;
        counters.setInteger(key, result);
        return result;
    }
    @Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) { return capability == CAPABILITY; }
    @Override @Nullable public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) { return capability == CAPABILITY ? CAPABILITY.cast(this) : null; }
    @Override public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        if (owner != null) tag.setUniqueId("Owner", owner);
        tag.setTag("Counters", counters.copy());
        return tag;
    }
    @Override public void deserializeNBT(NBTTagCompound tag) {
        owner = tag.hasUniqueId("Owner") ? tag.getUniqueId("Owner") : null;
        counters = tag.getCompoundTag("Counters").copy();
    }
}
