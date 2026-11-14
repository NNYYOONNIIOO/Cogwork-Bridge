package com.cogwork.cogworkbridge.content.schematics;

import com.cogwork.cogworkbridge.registry.AllItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Capture the selected region on the server when converting a quill directly. */
public final class SchematicInstantPacket implements IMessage {
    private String name = "";
    private BlockPos origin = BlockPos.ORIGIN, size = BlockPos.ORIGIN;
    private int slot;
    public SchematicInstantPacket() { }
    public SchematicInstantPacket(String name, BlockPos origin, BlockPos size, int slot) {
        this.name = name; this.origin = origin; this.size = size; this.slot = slot;
    }
    @Override public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, name); buf.writeLong(origin.toLong());
        buf.writeInt(size.getX()); buf.writeInt(size.getY()); buf.writeInt(size.getZ()); buf.writeInt(slot);
    }
    @Override public void fromBytes(ByteBuf buf) {
        name = ByteBufUtils.readUTF8String(buf); origin = BlockPos.fromLong(buf.readLong());
        size = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()); slot = buf.readInt();
    }
    public static final class Handler implements IMessageHandler<SchematicInstantPacket, IMessage> {
        @Override public IMessage onMessage(SchematicInstantPacket packet, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> capture(player, packet));
            return null;
        }
        private static void capture(EntityPlayerMP player, SchematicInstantPacket packet) {
            int sx = packet.size.getX(), sy = packet.size.getY(), sz = packet.size.getZ();
            if (packet.slot != player.inventory.currentItem || player.getHeldItemMainhand().getItem() != AllItems.SCHEMATIC_AND_QUILL
                    || packet.name.length() > 128 || sx < 1 || sy < 1 || sz < 1
                    || sx > SchematicData.MAX_AXIS || sy > SchematicData.MAX_AXIS || sz > SchematicData.MAX_AXIS
                    || (long) sx * sy * sz > SchematicData.MAX_BLOCKS || packet.origin.getY() < 0
                    || (long) packet.origin.getY() + sy > player.world.getHeight()) return;
            BlockPos max = packet.origin.add(sx - 1, sy - 1, sz - 1);
            if (!player.world.getWorldBorder().contains(packet.origin) || !player.world.getWorldBorder().contains(max)) return;
            for (int x = packet.origin.getX() >> 4; x <= max.getX() >> 4; x++)
                for (int z = packet.origin.getZ() >> 4; z <= max.getZ() >> 4; z++)
                    if (!player.world.isBlockLoaded(new BlockPos(x << 4, packet.origin.getY(), z << 4))) {
                        player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schematic.unloaded"), true);
                        return;
                    }
            try {
                byte[] bytes = SchematicData.capture(player.world, packet.origin, packet.size, player.getName());
                Path directory = SchematicData.serverDirectory(player.world);
                Files.createDirectories(directory);
                String id = UUID.randomUUID().toString();
                Files.write(directory.resolve(id + ".nbt"), bytes, StandardOpenOption.CREATE_NEW);
                ItemStack output = new ItemStack(AllItems.SCHEMATIC);
                NBTTagCompound data = new NBTTagCompound();
                data.setString("FileId", id); data.setString("Name", packet.name);
                data.setInteger("Width", sx); data.setInteger("Height", sy); data.setInteger("Depth", sz);
                data.setBoolean("Deployed", false); output.setTagCompound(data);
                player.inventory.setInventorySlotContents(packet.slot, output);
                player.inventory.markDirty();
                player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schematic.uploaded", packet.name), true);
            } catch (IOException | RuntimeException ex) {
                player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schematic.invalid"), true);
            }
        }
    }
}
