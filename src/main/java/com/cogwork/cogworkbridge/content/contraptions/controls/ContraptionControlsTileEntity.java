package com.cogwork.cogworkbridge.content.contraptions.controls;

import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

/** Persistent controller state; filter items are references and are not consumed. */
public final class ContraptionControlsTileEntity extends TileEntity implements ITickable {
    private ItemStack filter = ItemStack.EMPTY;
    private boolean disabled, powered, powerKnown;
    private float button, previousButton;

    public boolean isDisabled() { return disabled; }
    public ItemStack getFilter() { return filter; }
    public float button(float partial) { return previousButton + (button - previousButton) * partial; }
    public boolean disables(Block actor) {
        return disabled && (filter.isEmpty() || filter.getItem() == Item.getItemFromBlock(actor));
    }
    public static boolean isControllable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        Block[] blocks = {AllBlocks.MECHANICAL_DRILL, AllBlocks.MECHANICAL_SAW, AllBlocks.DEPLOYER,
                AllBlocks.MECHANICAL_PLOUGH, AllBlocks.MECHANICAL_HARVESTER, AllBlocks.MECHANICAL_ROLLER,
                AllBlocks.ANDESITE_FUNNEL, AllBlocks.BRASS_FUNNEL, AllBlocks.PORTABLE_STORAGE_INTERFACE,
                AllBlocks.PORTABLE_FLUID_INTERFACE, AllBlocks.REDSTONE_CONTACT};
        for (Block block : blocks) if (item == Item.getItemFromBlock(block)) return true;
        return false;
    }
    public void setFilter(ItemStack stack) {
        filter = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!filter.isEmpty()) filter.setCount(1);
        changed();
    }
    public void toggle() {
        if (world instanceof FakeWorld fake && fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity entity) {
            entity.toggleActorControl(filter);
            button = 1F;
            changed();
            return;
        }
        disabled = !disabled; button = 1F; changed();
    }
    public void setMovingDisabled(boolean value) {
        if (disabled == value) return;
        disabled = value;
        changed();
    }
    public void updatePower() {
        if (world == null || world.isRemote) return;
        boolean current = world.isBlockPowered(pos);
        if (!powerKnown || powered != current) {
            powerKnown = true;
            powered = current;
            if (world instanceof FakeWorld fake && fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity entity) {
                if (!entity.isElevator()) entity.setActorControl(filter, current);
                else disabled = false;
            } else disabled = current;
            changed();
        }
    }
    @Override public void update() {
        if (world == null) return;
        // Carried neighbours do not always emit the same neighbour callbacks
        // as real-world blocks, so also observe the virtual world's signal.
        if (!world.isRemote) updatePower();
        previousButton = button;
        button *= .75F;
    }
    private void changed() {
        markDirty();
        if (world == null || world.isRemote) return;
        if (world instanceof FakeWorld fake) fake.getMobileChunk().markTileDirty(pos);
        else world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("Disabled", disabled); tag.setBoolean("Powered", powered); tag.setBoolean("PowerKnown", powerKnown);
        tag.setTag("Filter", filter.writeToNBT(new NBTTagCompound()));
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        boolean oldDisabled = disabled;
        disabled = tag.getBoolean("Disabled"); powered = tag.getBoolean("Powered"); powerKnown = tag.getBoolean("PowerKnown");
        filter = tag.hasKey("Filter", 10) ? new ItemStack(tag.getCompoundTag("Filter")) : ItemStack.EMPTY;
        if (world != null && world.isRemote && oldDisabled != disabled) button = 1F;
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        boolean changed = disabled != packet.getNbtCompound().getBoolean("Disabled");
        readFromNBT(packet.getNbtCompound());
        if (changed) button = 1F;
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
