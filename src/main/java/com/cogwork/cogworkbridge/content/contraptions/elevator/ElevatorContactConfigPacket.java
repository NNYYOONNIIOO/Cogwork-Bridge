package com.cogwork.cogworkbridge.content.contraptions.elevator;

import io.netty.buffer.ByteBuf;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class ElevatorContactConfigPacket implements IMessage {
    private BlockPos pos = BlockPos.ORIGIN;
    private int contraptionId = -1;
    private String shortName = "", longName = "";
    private int doorMode;
    public ElevatorContactConfigPacket() { }
    public ElevatorContactConfigPacket(BlockPos pos, String shortName, String longName, int doorMode) {
        this(-1, pos, shortName, longName, doorMode);
    }
    public ElevatorContactConfigPacket(int contraptionId, BlockPos pos, String shortName, String longName, int doorMode) {
        this.contraptionId = contraptionId;
        this.pos = pos; this.shortName = shortName; this.longName = longName; this.doorMode = doorMode;
    }
    @Override public void fromBytes(ByteBuf buf) {
        contraptionId = buf.readInt();
        pos = BlockPos.fromLong(buf.readLong()); shortName = ByteBufUtils.readUTF8String(buf);
        longName = ByteBufUtils.readUTF8String(buf); doorMode = buf.readUnsignedByte();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(contraptionId);
        buf.writeLong(pos.toLong()); ByteBufUtils.writeUTF8String(buf, shortName);
        ByteBufUtils.writeUTF8String(buf, longName); buf.writeByte(doorMode);
    }
    public static final class Handler implements IMessageHandler<ElevatorContactConfigPacket, IMessage> {
        @Override public IMessage onMessage(ElevatorContactConfigPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.shortName.length() > 4 || message.longName.length() > 30 || message.doorMode >= 6) return;
                if (message.contraptionId < 0) {
                    if (!player.world.isBlockLoaded(message.pos) || player.getDistanceSqToCenter(message.pos) > 64D
                            || !player.canPlayerEdit(message.pos, EnumFacing.UP, player.getHeldItemMainhand())) return;
                    if (player.world.getBlockState(message.pos).getBlock() instanceof RedstoneContactBlock block
                            && block.isElevator() && player.world.getTileEntity(message.pos) instanceof RedstoneContactTileEntity tile)
                        tile.configure(message.shortName, message.longName, message.doorMode);
                    return;
                }
                if (!(player.world.getEntityByID(message.contraptionId) instanceof BearingContraptionEntity contraption)
                        || !contraption.hasContraptionData()) return;
                net.minecraft.util.math.Vec3d center = contraption.localBlockCenterToWorld(message.pos);
                BlockPos worldPos = new BlockPos(center);
                if (player.getDistanceSq(center.x, center.y, center.z) > 64D
                        || !player.world.isBlockModifiable(player, worldPos)
                        || !player.canPlayerEdit(worldPos, EnumFacing.UP, player.getHeldItemMainhand())
                        || !contraption.startInteractionSession()) return;
                try {
                    net.minecraft.world.World carried = contraption.getInteractionWorld();
                    if (carried.getBlockState(message.pos).getBlock() instanceof RedstoneContactBlock block
                            && block.isElevator() && carried.getTileEntity(message.pos) instanceof RedstoneContactTileEntity tile)
                        tile.configure(message.shortName, message.longName, message.doorMode);
                } finally {
                    contraption.commitInteractionChanges();
                    contraption.finishInteractionSession();
                }
            });
            return null;
        }
    }
}
