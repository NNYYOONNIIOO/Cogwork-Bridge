package com.cogwork.cogworkbridge.content.schematics;

import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.AllItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

public final class SchematicActionPacket implements IMessage {
    private int action, window, slot;
    public SchematicActionPacket() { }
    public SchematicActionPacket(int action, int window, int slot) { this.action = action; this.window = window; this.slot = slot; }
    @Override public void toBytes(ByteBuf buf) { buf.writeByte(action); buf.writeInt(window); buf.writeInt(slot); }
    @Override public void fromBytes(ByteBuf buf) { action = buf.readUnsignedByte(); window = buf.readInt(); slot = buf.readInt(); }
    public static final class Handler implements IMessageHandler<SchematicActionPacket, IMessage> {
        @Override public IMessage onMessage(SchematicActionPacket packet, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (packet.action >= 10 && packet.action <= 14 || packet.action >= 20 && packet.action <= 25) {
                    if (player.openContainer instanceof SchematicContainer container && container.cannon
                            && container.windowId == packet.window && container.canInteractWith(player)
                            && container.tile instanceof SchematicannonTile cannon)
                        cannon.action(packet.action >= 20 ? packet.action - 15 : packet.action - 10);
                    return;
                }
                // A preview request can arrive after the player has selected a different slot.
                // Read only the slot belonging to this player; the server still validates the item and file.
                if (packet.action == 3) {
                    if (packet.slot < 0 || packet.slot >= 9) return;
                    ItemStack requested = player.inventory.getStackInSlot(packet.slot);
                    Path file = SchematicData.serverFile(player.world, requested);
                    if (file == null) return;
                    try {
                        byte[] data = SchematicData.readFile(file);
                        String id = requested.getTagCompound().getString("FileId");
                        for (int offset = 0; offset < data.length; offset += SchematicData.CHUNK) {
                            byte[] piece = Arrays.copyOfRange(data, offset, Math.min(data.length, offset + SchematicData.CHUNK));
                            ModNetwork.CHANNEL.sendTo(new SchematicDownloadPacket(id, data.length, offset, piece), player);
                        }
                    } catch (IOException ignored) { }
                    return;
                }
                if (packet.slot != player.inventory.currentItem) return;
                ItemStack stack = player.getHeldItemMainhand();
                if (stack.getItem() != AllItems.SCHEMATIC || !stack.hasTagCompound()) return;
                NBTTagCompound tag = stack.getTagCompound();
                if (packet.action == 0 || packet.action == 1 || packet.action == 30 || packet.action == 31)
                    transform(stack, packet.action);
                if (packet.action == 2 && tag.hasKey("Anchor", 4)) tag.setBoolean("Deployed", !tag.getBoolean("Deployed"));
                if (packet.action >= 0 && packet.action <= 2 || packet.action == 30 || packet.action == 31)
                    player.inventory.markDirty();
                if (packet.action >= 4 && packet.action <= 9 && tag.getBoolean("Deployed")) {
                    net.minecraft.util.EnumFacing[] directions = {net.minecraft.util.EnumFacing.WEST, net.minecraft.util.EnumFacing.EAST,
                            net.minecraft.util.EnumFacing.DOWN, net.minecraft.util.EnumFacing.UP,
                            net.minecraft.util.EnumFacing.NORTH, net.minecraft.util.EnumFacing.SOUTH};
                    net.minecraft.util.math.BlockPos at = SchematicData.anchor(stack).offset(directions[packet.action - 4]);
                    if (at.getY() >= 0 && at.getY() < player.world.getHeight()) { tag.setLong("Anchor", at.toLong()); player.inventory.markDirty(); }
                }
                if (packet.action == 15) CreativeSchematicPlacement.start(player, stack);

            });
            return null;
        }

        private static void transform(ItemStack stack, int action) {
            NBTTagCompound tag = stack.getTagCompound();
            int width = Math.max(1, Math.min(SchematicData.MAX_AXIS, tag.getInteger("Width")));
            int depth = Math.max(1, Math.min(SchematicData.MAX_AXIS, tag.getInteger("Depth")));
            int[] before = SchematicData.horizontalBounds(width, depth, SchematicData.mirror(stack), SchematicData.rotation(stack));
            int rotation = Math.floorMod(tag.getInteger("Rotation"), 4);
            net.minecraft.util.Mirror mirror = SchematicData.mirror(stack);
            if (action == 0 || action == 30) rotation = Math.floorMod(rotation + (action == 0 ? 1 : -1), 4);
            else {
                net.minecraft.util.Mirror selected = action == 1 ? net.minecraft.util.Mirror.FRONT_BACK : net.minecraft.util.Mirror.LEFT_RIGHT;
                if (mirror == net.minecraft.util.Mirror.NONE) mirror = selected;
                else if (mirror == selected) mirror = net.minecraft.util.Mirror.NONE;
                else { mirror = net.minecraft.util.Mirror.NONE; rotation = (rotation + 2) % 4; }
            }
            tag.setInteger("Rotation", rotation);
            tag.setInteger("MirrorAxis", mirror == net.minecraft.util.Mirror.FRONT_BACK ? 1 : mirror == net.minecraft.util.Mirror.LEFT_RIGHT ? 2 : 0);
            tag.setBoolean("Mirror", mirror != net.minecraft.util.Mirror.NONE);
            if (tag.getBoolean("Deployed") && tag.hasKey("Anchor", 4)) {
                int[] after = SchematicData.horizontalBounds(width, depth, mirror, SchematicData.rotation(stack));
                net.minecraft.util.math.BlockPos anchor = SchematicData.anchor(stack).add(
                        Math.floorDiv(before[0] + before[1] - after[0] - after[1], 2), 0,
                        Math.floorDiv(before[2] + before[3] - after[2] - after[3], 2));
                tag.setLong("Anchor", anchor.toLong());
            }
        }
    }
}
