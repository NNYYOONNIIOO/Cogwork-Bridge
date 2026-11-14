package com.cogwork.cogworkbridge.content.equipment.clipboard;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.ByteBufUtils;

public final class ClipboardEditPacket implements IMessage {
    private int slot = -1, page;
    private BlockPos target;
    private String[] lines = new String[ClipboardData.ROWS];
    private ItemStack[] icons = new ItemStack[ClipboardData.ROWS];
    private int checked;
    public ClipboardEditPacket() { }
    public ClipboardEditPacket(int slot, BlockPos target, int page, String[] lines, int checked, ItemStack[] icons) {
        this.slot = slot; this.target = target; this.page = page; this.lines = lines.clone(); this.checked = checked;
        for (int i = 0; i < this.icons.length; i++) this.icons[i] = icons[i].copy();
    }
    @Override public void fromBytes(ByteBuf buf) {
        slot = buf.readByte();
        page = buf.readByte();
        if (buf.readBoolean()) target = BlockPos.fromLong(buf.readLong());
        checked = buf.readInt();
        for (int i = 0; i < ClipboardData.ROWS; i++) lines[i] = readText(buf);
        for (int i = 0; i < ClipboardData.ROWS; i++) icons[i] = ByteBufUtils.readItemStack(buf);
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeByte(slot); buf.writeByte(page); buf.writeBoolean(target != null);
        if (target != null) buf.writeLong(target.toLong());
        buf.writeInt(checked);
        for (int i = 0; i < ClipboardData.ROWS; i++) writeText(buf, lines.length > i ? lines[i] : "");
        for (ItemStack icon : icons) ByteBufUtils.writeItemStack(buf, icon == null ? ItemStack.EMPTY : icon);
    }
    private static String readText(ByteBuf buf) { int length = buf.readUnsignedByte(); String text = buf.readCharSequence(length * 2, java.nio.charset.StandardCharsets.UTF_16BE).toString(); return text.substring(0, Math.min(text.length(), ClipboardData.MAX_TEXT)); }
    private static void writeText(ByteBuf buf, String value) { String text = value == null ? "" : value.replaceAll("[\\x00-\\x1f\\x7f]", ""); if (text.length() > ClipboardData.MAX_TEXT) text = text.substring(0, ClipboardData.MAX_TEXT); buf.writeByte(text.length()); buf.writeCharSequence(text, java.nio.charset.StandardCharsets.UTF_16BE); }
    public static final class Handler implements IMessageHandler<ClipboardEditPacket, IMessage> {
        @Override public IMessage onMessage(ClipboardEditPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.page < 0 || message.page >= ClipboardData.MAX_PAGES) return;
                if (message.target != null) {
                    if (!player.world.isBlockLoaded(message.target) || player.getDistanceSqToCenter(message.target) > 100D
                            || !player.world.isBlockModifiable(player, message.target)) return;
                    TileEntity tile = player.world.getTileEntity(message.target);
                    if (tile instanceof ClipboardTileEntity clipboard) {
                        NBTTagCompound data = clipboard.content();
                        ClipboardData.applyEdit(data, message.page, message.lines, message.checked, message.icons);
                        clipboard.setContent(data);
                    }
                    return;
                }
                if (message.slot < 0 || message.slot >= 9 || message.slot != player.inventory.currentItem) return;
                ItemStack stack = player.inventory.getStackInSlot(message.slot);
                if (stack.getItem() != Item.getItemFromBlock(com.cogwork.cogworkbridge.registry.AllBlocks.CLIPBOARD)) return;
                NBTTagCompound data = ClipboardData.fromItem(stack);
                ClipboardData.applyEdit(data, message.page, message.lines, message.checked, message.icons);
                ClipboardData.toItem(stack, data);
                player.inventory.markDirty();
            });
            return null;
        }
    }
}
