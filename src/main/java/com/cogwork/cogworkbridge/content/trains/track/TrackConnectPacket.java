package com.cogwork.cogworkbridge.content.trains.track;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Carries Ctrl with the placement it belongs to, rather than a stale key-state flag. */
public final class TrackConnectPacket implements IMessage {
    private BlockPos clicked;
    private int face;
    private float x, y, z;
    private boolean maximise;
    public TrackConnectPacket() { }
    public TrackConnectPacket(BlockPos clicked, EnumFacing face, float x, float y, float z, boolean maximise) {
        this.clicked = clicked; this.face = face.getIndex(); this.x = x; this.y = y; this.z = z; this.maximise = maximise;
    }
    @Override public void fromBytes(ByteBuf buf) {
        clicked = BlockPos.fromLong(buf.readLong()); face = buf.readUnsignedByte();
        x = buf.readFloat(); y = buf.readFloat(); z = buf.readFloat(); maximise = buf.readBoolean();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(clicked.toLong()); buf.writeByte(face); buf.writeFloat(x); buf.writeFloat(y); buf.writeFloat(z); buf.writeBoolean(maximise);
    }
    public static final class Handler implements IMessageHandler<TrackConnectPacket, IMessage> {
        @Override public IMessage onMessage(TrackConnectPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.face > 5 || !unit(message.x) || !unit(message.y) || !unit(message.z)
                        || player.isSpectator() || player.isDead || !player.world.isBlockLoaded(message.clicked)) return;
                double reach = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue() + 1;
                Vec3d target = new Vec3d(message.clicked).add(message.x, message.y, message.z);
                if (player.getPositionEyes(1).squareDistanceTo(target) > reach * reach) return;
                if (player.getHeldItemMainhand().getItem() instanceof TrackItem item)
                    item.connect(player, player.world, message.clicked, EnumFacing.byIndex(message.face), message.x, message.y, message.z, message.maximise);
            });
            return null;
        }
        private static boolean unit(float value) { return Float.isFinite(value) && value >= 0 && value <= 1; }
    }
}
