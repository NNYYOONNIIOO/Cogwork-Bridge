package com.cogwork.cogworkbridge.content.equipment;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class WorldshaperPacket implements IMessage {
    private boolean select, diagonal, across;
    private int hand, slot, shape, tool, width, height, depth, pattern, placement;
    private long position;
    public WorldshaperPacket() { }
    public WorldshaperPacket(EnumHand hand, int slot, int shape, int tool, int width, int height, int depth, int pattern, int placement, boolean diagonal, boolean across) {
        this.hand = hand.ordinal(); this.slot = slot; this.shape = shape; this.tool = tool; this.width = width; this.height = height; this.depth = depth; this.pattern = pattern; this.placement = placement; this.diagonal = diagonal; this.across = across;
    }
    public static WorldshaperPacket select(BlockPos pos, int slot) { WorldshaperPacket packet = new WorldshaperPacket(); packet.select = true; packet.position = pos.toLong(); packet.slot = slot; return packet; }
    @Override public void toBytes(ByteBuf buf) { buf.writeBoolean(select); buf.writeByte(hand); buf.writeByte(slot); buf.writeLong(position); buf.writeByte(shape); buf.writeByte(tool); buf.writeByte(width); buf.writeByte(height); buf.writeByte(depth); buf.writeByte(pattern); buf.writeByte(placement); buf.writeBoolean(diagonal); buf.writeBoolean(across); }
    @Override public void fromBytes(ByteBuf buf) { select = buf.readBoolean(); hand = buf.readByte(); slot = buf.readByte(); position = buf.readLong(); shape = buf.readByte(); tool = buf.readByte(); width = buf.readByte(); height = buf.readByte(); depth = buf.readByte(); pattern = buf.readByte(); placement = buf.readByte(); diagonal = buf.readBoolean(); across = buf.readBoolean(); }
    public static final class Handler implements IMessageHandler<WorldshaperPacket, IMessage> {
        @Override public IMessage onMessage(WorldshaperPacket packet, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!player.capabilities.isCreativeMode || packet.hand < 0 || packet.hand >= EnumHand.values().length) return;
                if (packet.hand == EnumHand.MAIN_HAND.ordinal() && packet.slot != player.inventory.currentItem) return;
                ItemStack stack = player.getHeldItem(EnumHand.values()[packet.hand]);
                if (!(stack.getItem() instanceof WorldshaperItem)) return;
                if (packet.select) {
                    RayTraceResult hit = WorldshaperItem.target(player);
                    if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || !hit.getBlockPos().equals(BlockPos.fromLong(packet.position))) return;
                    WorldshaperItem.select(stack, player.world, hit.getBlockPos());
                } else WorldshaperItem.configure(stack, packet.shape, packet.tool, packet.width, packet.height, packet.depth, packet.pattern, packet.placement, packet.diagonal, packet.across);
                player.inventory.markDirty();
            });
            return null;
        }
    }
}
