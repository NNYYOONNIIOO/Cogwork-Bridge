package com.cogwork.cogworkbridge.content.equipment.clipboard;

import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.deployer.DeployerTileEntity;
import com.cogwork.cogworkbridge.content.logistics.chute.ChuteTileEntity;
import com.cogwork.cogworkbridge.content.logistics.funnel.FunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BrassTunnelTileEntity;
import com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity;
import com.cogwork.cogworkbridge.content.redstone.RedstoneLinkTileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.items.ItemHandlerHelper;

/** Copies configuration only; inventories and runtime state never enter the clipboard. */
final class MachineClipboardSettings {
    private MachineClipboardSettings() { }
    static NBTTagCompound copy(TileEntity tile, EnumFacing side) {
        if (tile instanceof RedstoneLinkTileEntity link) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag("First", link.getFrequency(0).writeToNBT(new NBTTagCompound()));
            tag.setTag("Second", link.getFrequency(1).writeToNBT(new NBTTagCompound()));
            return tag;
        }
        ItemStack filter = filter(tile, side);
        if (filter == null) return null;
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("Filter", filter.writeToNBT(new NBTTagCompound()));
        if (tile instanceof FunnelTileEntity funnel) {
            tag.setInteger("Amount", funnel.getAmount()); tag.setBoolean("Exact", funnel.isExact());
        } else if (tile instanceof ChuteTileEntity chute) {
            tag.setInteger("Amount", chute.getAmount()); tag.setBoolean("Exact", chute.isExact());
        } else if (tile instanceof BrassTunnelTileEntity tunnel) tag.setInteger("TunnelMode", tunnel.getMode());
        else if (tile instanceof DeployerTileEntity deployer) tag.setInteger("DeployerMode", deployer.getMode().ordinal());
        else if (tile instanceof DetectorTileEntity detector) {
            tag.setInteger("On", detector.getOnAbove()); tag.setInteger("Off", detector.getOffBelow());
            tag.setBoolean("Reverse", detector.isInverted()); tag.setBoolean("Stacks", detector.isInStacks());
        }
        return tag;
    }
    static boolean supports(TileEntity tile, EnumFacing side, NBTTagCompound tag) {
        return tag.hasKey("First", 10) && tile instanceof RedstoneLinkTileEntity
                || tag.hasKey("Filter", 10) && filter(tile, side) != null;
    }
    static boolean paste(TileEntity tile, EnumFacing side, EntityPlayer player, NBTTagCompound tag) {
        if (tile instanceof RedstoneLinkTileEntity link && tag.hasKey("First", 10)) {
            link.setFrequency(0, new ItemStack(tag.getCompoundTag("First")));
            link.setFrequency(1, new ItemStack(tag.getCompoundTag("Second")));
            return true;
        }
        ItemStack previous = filter(tile, side);
        if (previous == null || !tag.hasKey("Filter", 10)) return false;
        ItemStack replacement = new ItemStack(tag.getCompoundTag("Filter"));
        if (!replacement.isEmpty()) replacement.setCount(1);
        if (!payForFilter(player, previous, replacement)) return false;
        if (tile instanceof FunnelTileEntity funnel) {
            funnel.setFilter(replacement);
            if (tag.hasKey("Amount")) funnel.setAmount(tag.getInteger("Amount"), tag.getBoolean("Exact"));
        } else if (tile instanceof ChuteTileEntity chute) {
            chute.setFilter(replacement);
            if (tag.hasKey("Amount")) { chute.setAmount(tag.getInteger("Amount")); if (chute.isExact() != tag.getBoolean("Exact")) chute.toggleExact(); }
        } else if (tile instanceof BrassTunnelTileEntity tunnel) {
            tunnel.setFilter(side, replacement);
            if (tag.hasKey("TunnelMode")) tunnel.setMode(tag.getInteger("TunnelMode"));
        } else if (tile instanceof SmartFluidPipeTileEntity pipe) pipe.setFilterItem(replacement);
        else if (tile instanceof DeployerTileEntity deployer) {
            deployer.setFilter(replacement);
            if (tag.hasKey("DeployerMode") && deployer.getMode().ordinal() != Math.floorMod(tag.getInteger("DeployerMode"), 2)) deployer.toggleMode();
        } else if (tile instanceof DetectorTileEntity detector) {
            detector.applyClipboardFilter(replacement);
            if (tag.hasKey("On")) detector.configure(tag.getInteger("On"), tag.getInteger("Off"), tag.getBoolean("Reverse"), tag.getBoolean("Stacks"));
        }
        return true;
    }
    private static ItemStack filter(TileEntity tile, EnumFacing side) {
        if (tile instanceof FunnelTileEntity funnel) return funnel.getFilter();
        if (tile instanceof ChuteTileEntity chute) return chute.getFilter();
        if (tile instanceof BrassTunnelTileEntity tunnel) return tunnel.filters()[side.getIndex()].copy();
        if (tile instanceof SmartFluidPipeTileEntity pipe) return pipe.getFilterItem();
        if (tile instanceof DeployerTileEntity deployer) return deployer.getFilter().copy();
        if (tile instanceof DetectorTileEntity detector) return detector.getFilter();
        return null;
    }
    private static boolean payForFilter(EntityPlayer player, ItemStack previous, ItemStack replacement) {
        if (player.capabilities.isCreativeMode) return true;
        boolean oldFilter = ListFilterItem.isFilterItem(previous), newFilter = ListFilterItem.isFilterItem(replacement);
        // A filter already installed can be reprogrammed in place, exactly like a blank of that type.
        if (oldFilter && newFilter && previous.getItem() == replacement.getItem()) return true;
        if (newFilter) {
            int slot = -1;
            for (int pass = 0; pass < 2 && slot < 0; pass++)
                for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                    ItemStack candidate = player.inventory.getStackInSlot(i);
                    if (!candidate.isEmpty() && candidate.getItem() == replacement.getItem()
                            && (pass == 1 || !candidate.hasTagCompound())) { slot = i; break; }
                }
            if (slot < 0) {
                player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.clipboard.missing_filter"), true);
                return false;
            }
            player.inventory.decrStackSize(slot, 1);
        }
        if (oldFilter) ItemHandlerHelper.giveItemToPlayer(player, previous.copy());
        player.inventory.markDirty();
        return true;
    }
}
