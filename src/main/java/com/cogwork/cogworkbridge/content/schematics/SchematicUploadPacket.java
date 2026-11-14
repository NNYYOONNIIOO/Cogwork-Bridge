package com.cogwork.cogworkbridge.content.schematics;

import com.cogwork.cogworkbridge.registry.AllItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class SchematicUploadPacket implements IMessage {
    private int kind, window, total, offset;
    private long pos;
    private String name = "";
    private byte[] payload = new byte[0];
    public SchematicUploadPacket() { }
    public static SchematicUploadPacket start(int window, BlockPos pos, String name, int total) {
        SchematicUploadPacket p = new SchematicUploadPacket(); p.kind = 0; p.window = window; p.pos = pos.toLong(); p.name = name; p.total = total; return p;
    }
    public static SchematicUploadPacket chunk(int window, BlockPos pos, int offset, byte[] bytes) {
        SchematicUploadPacket p = new SchematicUploadPacket(); p.kind = 1; p.window = window; p.pos = pos.toLong(); p.offset = offset; p.payload = bytes; return p;
    }
    public static SchematicUploadPacket finish(int window, BlockPos pos) {
        SchematicUploadPacket p = new SchematicUploadPacket(); p.kind = 2; p.window = window; p.pos = pos.toLong(); return p;
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeByte(kind); buf.writeInt(window); buf.writeLong(pos);
        if (kind == 0) { ByteBufUtils.writeUTF8String(buf, name); buf.writeInt(total); }
        if (kind == 1) { buf.writeInt(offset); buf.writeShort(payload.length); buf.writeBytes(payload); }
    }
    @Override public void fromBytes(ByteBuf buf) {
        kind = buf.readUnsignedByte(); window = buf.readInt(); pos = buf.readLong();
        if (kind == 0) { name = ByteBufUtils.readUTF8String(buf); total = buf.readInt(); }
        if (kind == 1) {
            offset = buf.readInt(); int length = buf.readUnsignedShort();
            if (length > SchematicData.CHUNK || length > buf.readableBytes()) { kind = -1; return; }
            payload = new byte[length]; buf.readBytes(payload);
        }
    }
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    private static final class Upload {
        final int window, total, dimension; final long pos, started;
        final String name; final ByteArrayOutputStream data = new ByteArrayOutputStream();
        Upload(SchematicUploadPacket packet, int dimension) {
            window = packet.window; total = packet.total; pos = packet.pos; name = packet.name;
            this.dimension = dimension; started = System.currentTimeMillis();
        }
    }
    private static boolean matches(EntityPlayerMP player, SchematicUploadPacket packet) {
        if (!(player.openContainer instanceof SchematicContainer container) || container.cannon
                || container.windowId != packet.window || container.world != player.world
                || container.pos.toLong() != packet.pos || !container.canInteractWith(player)) return false;
        return container.tile instanceof SchematicTableTile
                && container.tile.items().getStackInSlot(0).getItem() == AllItems.EMPTY_SCHEMATIC
                && container.tile.items().getStackInSlot(1).isEmpty();
    }
    public static final class Handler implements IMessageHandler<SchematicUploadPacket, IMessage> {
        @Override public IMessage onMessage(SchematicUploadPacket packet, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> process(player, packet));
            return null;
        }
        private void process(EntityPlayerMP player, SchematicUploadPacket packet) {
            UUID id = player.getUniqueID();
            if (!matches(player, packet)) { UPLOADS.remove(id); return; }
            if (packet.kind == 0) {
                UPLOADS.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue().started > 120000L);
                if (UPLOADS.size() >= 32 && !UPLOADS.containsKey(id)) return;
                if (packet.total < 1 || packet.total > SchematicData.MAX_COMPRESSED || packet.name.length() > 128
                        || packet.name.indexOf('/') >= 0 || packet.name.indexOf('\\') >= 0) return;
                UPLOADS.put(id, new Upload(packet, player.dimension)); return;
            }
            Upload upload = UPLOADS.get(id);
            if (upload == null || upload.window != packet.window || upload.pos != packet.pos || upload.dimension != player.dimension
                    || System.currentTimeMillis() - upload.started > 120000L) { UPLOADS.remove(id); return; }
            if (packet.kind == 1) {
                if (packet.offset != upload.data.size() || packet.payload.length == 0
                        || upload.data.size() + packet.payload.length > upload.total) { UPLOADS.remove(id); return; }
                upload.data.write(packet.payload, 0, packet.payload.length); return;
            }
            if (packet.kind != 2) { UPLOADS.remove(id); return; }
            UPLOADS.remove(id);
            if (upload.data.size() != upload.total) return;
            try {
                byte[] data = upload.data.toByteArray();
                SchematicData structure = SchematicData.read(data);
                Path directory = SchematicData.serverDirectory(player.world);
                Files.createDirectories(directory);
                String fileId = UUID.randomUUID().toString();
                Files.write(directory.resolve(fileId + ".nbt"), data, StandardOpenOption.CREATE_NEW);
                ItemStack output = new ItemStack(AllItems.SCHEMATIC);
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("FileId", fileId); tag.setString("Name", upload.name);
                tag.setInteger("Width", structure.size.getX()); tag.setInteger("Height", structure.size.getY()); tag.setInteger("Depth", structure.size.getZ());
                tag.setBoolean("Deployed", false); output.setTagCompound(tag);
                SchematicInventoryTile tile = ((SchematicContainer) player.openContainer).tile;
                tile.items().extractItem(0, 1, false); tile.items().setStackInSlot(1, output); tile.changed();
                player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schematic.uploaded", upload.name), true);
            } catch (IOException | RuntimeException ex) {
                player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schematic.invalid"), true);
            }
        }
    }
}
