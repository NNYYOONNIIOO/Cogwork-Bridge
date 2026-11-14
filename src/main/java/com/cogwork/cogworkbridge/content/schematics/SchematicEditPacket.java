package com.cogwork.cogworkbridge.content.schematics;

import com.cogwork.cogworkbridge.registry.AllItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Applies a schematic panel's complete transformation to the item being edited. */
public final class SchematicEditPacket implements IMessage {
    private int slot, x, y, z, rotation, mirror;
    private String fileId = "";
    public SchematicEditPacket() { }
    public SchematicEditPacket(int slot, String fileId, int x, int y, int z, int rotation, int mirror) {
        this.slot = slot; this.fileId = fileId; this.x = x; this.y = y; this.z = z;
        this.rotation = rotation; this.mirror = mirror;
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(slot); ByteBufUtils.writeUTF8String(buf, fileId);
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z); buf.writeByte(rotation); buf.writeByte(mirror);
    }
    @Override public void fromBytes(ByteBuf buf) {
        slot = buf.readInt(); fileId = ByteBufUtils.readUTF8String(buf);
        x = buf.readInt(); y = buf.readInt(); z = buf.readInt(); rotation = buf.readUnsignedByte(); mirror = buf.readUnsignedByte();
    }
    public static final class Handler implements IMessageHandler<SchematicEditPacket, IMessage> {
        @Override public IMessage onMessage(SchematicEditPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.slot != player.inventory.currentItem || message.slot < 0 || message.slot >= 9
                        || message.rotation > 3 || message.mirror > 2 || message.y < 0 || message.y >= player.world.getHeight()) return;
                ItemStack stack = player.inventory.getStackInSlot(message.slot);
                if (stack.getItem() != AllItems.SCHEMATIC || !stack.hasTagCompound()) return;
                NBTTagCompound tag = stack.getTagCompound();
                if (message.fileId.isEmpty() || !message.fileId.equals(tag.getString("FileId"))) return;
                BlockPos anchor = new BlockPos(message.x, message.y, message.z);
                if (!player.world.getWorldBorder().contains(anchor)) return;
                tag.setLong("Anchor", anchor.toLong()); tag.setBoolean("Deployed", true);
                tag.setInteger("Rotation", message.rotation);
                tag.setInteger("MirrorAxis", message.mirror == 1 ? 2 : message.mirror == 2 ? 1 : 0);
                tag.setBoolean("Mirror", message.mirror != 0);
                player.inventory.markDirty();
            });
            return null;
        }
    }
}
