package com.cogwork.cogworkbridge.content.equipment.clipboard;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.chassis.ChassisTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.config.KineticMachineConfig;
import com.cogwork.cogworkbridge.content.redstone.PulseDiodeTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Copies only explicitly supported machine settings, never full tile NBT. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class ClipboardInteractionEvents {
    private ClipboardInteractionEvents() { }

    @SubscribeEvent public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        ItemStack held = player.getHeldItem(event.getHand());
        if (!canInteract(player, event.getPos(), event.getFace(), held)) return;
        TileEntity tile = event.getWorld().getTileEntity(event.getPos());
        NBTTagCompound settings = MachineClipboardSettings.copy(tile, event.getFace());
        if (settings != null) {
            if (!event.getWorld().isRemote) {
                NBTTagCompound data = ClipboardData.fromItem(held);
                data.setTag("MachineSettings", settings);
                ClipboardData.toItem(held, data);
                player.inventory.markDirty();
                player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.clipboard.copied"), true);
            }
            event.setCanceled(true); event.setCancellationResult(EnumActionResult.SUCCESS); return;
        }
        KineticMachineConfig config = KineticMachineConfig.forTile(tile);
        if (!(tile instanceof ClipboardTileEntity) && !(tile instanceof ChassisTileEntity)
                && !(tile instanceof PulseDiodeTileEntity) && config == null) return;
        if (!event.getWorld().isRemote) {
            NBTTagCompound data = ClipboardData.fromItem(held);
            if (tile instanceof ClipboardTileEntity clipboard) {
                ClipboardData.merge(data, clipboard.content());
            } else if (tile instanceof PulseDiodeTileEntity diode) {
                NBTTagCompound copied = new NBTTagCompound();
                copied.setString("Type", "PULSE_DIODE");
                copied.setInteger("Value", diode.getDelay());
                copied.setBoolean("Inverted", diode.isInverted());
                data.setTag("CopiedValues", copied);
            } else {
                NBTTagCompound copied = new NBTTagCompound();
                copied.setString("Type", config == null ? "CHASSIS" : config.name());
                copied.setInteger("Value", config == null ? ((ChassisTileEntity) tile).getRange() : config.read(tile));
                data.setTag("CopiedValues", copied);
            }
            ClipboardData.toItem(held, data);
            player.inventory.markDirty();
            player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.clipboard.copied"), true);
        }
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
    }

    @SubscribeEvent public static void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        ItemStack held = player.getHeldItem(EnumHand.MAIN_HAND);
        if (!canInteract(player, event.getPos(), event.getFace(), held)) return;
        NBTTagCompound data = ClipboardData.fromItem(held);
        TileEntity machine = event.getWorld().getTileEntity(event.getPos());
        NBTTagCompound settings = data.getCompoundTag("MachineSettings");
        if (MachineClipboardSettings.supports(machine, event.getFace(), settings)) {
            if (!event.getWorld().isRemote && MachineClipboardSettings.paste(machine, event.getFace(), player, settings))
                player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.clipboard.pasted"), true);
            event.setCanceled(true); return;
        }
        if (!data.hasKey("CopiedValues", 10)) return;
        NBTTagCompound copied = data.getCompoundTag("CopiedValues");
        TileEntity tile = event.getWorld().getTileEntity(event.getPos());
        KineticMachineConfig config = KineticMachineConfig.forTile(tile);
        boolean chassis = tile instanceof ChassisTileEntity && "CHASSIS".equals(copied.getString("Type"));
        boolean diode = tile instanceof PulseDiodeTileEntity && "PULSE_DIODE".equals(copied.getString("Type"));
        if (!chassis && !diode && (config == null || !config.name().equals(copied.getString("Type")))) return;
        if (!event.getWorld().isRemote) {
            if (chassis) ((ChassisTileEntity) tile).setRange(copied.getInteger("Value"));
            else if (diode) {
                PulseDiodeTileEntity pulse = (PulseDiodeTileEntity) tile;
                pulse.setDelay(copied.getInteger("Value"));
                pulse.setInverted(copied.getBoolean("Inverted"));
            }
            else config.write(tile, copied.getInteger("Value"));
            player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.clipboard.pasted"), true);
        }
        event.setCanceled(true);
    }

    private static boolean canInteract(EntityPlayer player, BlockPos pos, EnumFacing side, ItemStack held) {
        return !player.isSneaking() && !player.isSpectator() && !held.isEmpty()
                && held.getItem() == Item.getItemFromBlock(AllBlocks.CLIPBOARD)
                && player.world.isBlockLoaded(pos) && player.world.isBlockModifiable(player, pos)
                && player.canPlayerEdit(pos, side == null ? EnumFacing.UP : side, held);
    }
}
