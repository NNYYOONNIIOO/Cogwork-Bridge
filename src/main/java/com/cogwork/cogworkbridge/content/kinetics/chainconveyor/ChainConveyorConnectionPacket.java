package com.cogwork.cogworkbridge.content.kinetics.chainconveyor;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client selection, validated against the two real server-side wheels and held chain. */
public final class ChainConveyorConnectionPacket implements IMessage {
    private BlockPos first, second;
    private boolean offhand;
    public ChainConveyorConnectionPacket() { }
    public ChainConveyorConnectionPacket(BlockPos first, BlockPos second, EnumHand hand) {
        this.first = first; this.second = second; offhand = hand == EnumHand.OFF_HAND;
    }
    @Override public void fromBytes(ByteBuf buf) { first = BlockPos.fromLong(buf.readLong()); second = BlockPos.fromLong(buf.readLong()); offhand = buf.readBoolean(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeLong(first.toLong()); buf.writeLong(second.toLong()); buf.writeBoolean(offhand); }
    public static final class Handler implements IMessageHandler<ChainConveyorConnectionPacket, IMessage> {
        @Override public IMessage onMessage(ChainConveyorConnectionPacket packet, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.isSpectator() || player.isSneaking()) return;
                ItemStack held = player.getHeldItem(packet.offhand ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND);
                if (!ChainConveyorBlock.isChain(held) || !player.world.isBlockLoaded(packet.first)
                        || !player.world.isBlockLoaded(packet.second)) {
                    player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.chain.failure.invalid"), true);
                    return;
                }
                double reach = Math.max(0, Math.min(32, player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue()));
                if (!(player.world.getTileEntity(packet.first) instanceof ChainConveyorTileEntity source)
                        || !(player.world.getTileEntity(packet.second) instanceof ChainConveyorTileEntity target)) {
                    player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.chain.failure.invalid"), true);
                    return;
                }
                AxisAlignedBB bounds = ChainConveyorBlock.OUTLINE.offset(packet.second);
                Vec3d eye = player.getPositionEyes(1F);
                double x = Math.max(bounds.minX, Math.min(bounds.maxX, eye.x));
                double y = Math.max(bounds.minY, Math.min(bounds.maxY, eye.y));
                double z = Math.max(bounds.minZ, Math.min(bounds.maxZ, eye.z));
                if (eye.squareDistanceTo(x, y, z) > (reach + .5D) * (reach + .5D)) {
                    player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.chain.failure.unreachable"), true);
                    return;
                }
                ChainConveyorTileEntity.ConnectionResult result = source.connectTo(target, player, held);
                if (result != ChainConveyorTileEntity.ConnectionResult.SUCCESS) {
                    String key = "cogworkbridge.chain.failure." + result.name().toLowerCase(java.util.Locale.ROOT);
                    player.sendStatusMessage(new TextComponentTranslation(key, ChainConveyorTileEntity.chainCost(packet.first, packet.second)), true);
                }
            });
            return null;
        }
    }
}
