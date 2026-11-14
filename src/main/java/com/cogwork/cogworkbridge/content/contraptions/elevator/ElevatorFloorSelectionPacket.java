package com.cogwork.cogworkbridge.content.contraptions.elevator;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server verifies the actual ray hit before accepting a control wheel change. */
public final class ElevatorFloorSelectionPacket implements IMessage {
    private int entityId, delta;
    private BlockPos local = BlockPos.ORIGIN;
    public ElevatorFloorSelectionPacket() { }
    public ElevatorFloorSelectionPacket(int entityId, BlockPos local, int delta) {
        this.entityId = entityId; this.local = local.toImmutable(); this.delta = Integer.signum(delta);
    }
    @Override public void fromBytes(ByteBuf buf) { entityId = buf.readInt(); local = BlockPos.fromLong(buf.readLong()); delta = buf.readByte(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(entityId); buf.writeLong(local.toLong()); buf.writeByte(delta); }
    public static final class Handler implements IMessageHandler<ElevatorFloorSelectionPacket, IMessage> {
        @Override public IMessage onMessage(ElevatorFloorSelectionPacket msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.delta == 0 || player.isSpectator() || !(player.world.getEntityByID(msg.entityId)
                        instanceof BearingContraptionEntity entity) || !entity.isElevator() || entity.getContraption() == null) return;
                var snapshot = entity.getContraption().snapshotAtLocal(msg.local).orElse(null);
                if (snapshot == null || snapshot.state().getBlock() != AllBlocks.CONTRAPTION_CONTROLS) return;
                double reach = player.getEntityAttribute(EntityPlayerMP.REACH_DISTANCE).getAttributeValue();
                Vec3d start = player.getPositionEyes(1F);
                Vec3d end = start.add(player.getLook(1F).scale(reach));
                BearingContraptionEntity.ContraptionHit hit = entity.rayTrace(start, end, 1F);
                if (hit == null || !hit.localPos().equals(msg.local)) return;
                net.minecraft.util.math.RayTraceResult obstacle = player.world.rayTraceBlocks(start, end, false, true, false);
                if (obstacle != null && obstacle.hitVec != null && start.squareDistanceTo(obstacle.hitVec)
                        + .0001D < start.squareDistanceTo(hit.worldHit())) return;
                if (player.world.getTileEntity(entity.getElevatorController()) instanceof ElevatorPulleyTileEntity pulley)
                    pulley.selectFloor(msg.delta, player);
            });
            return null;
        }
    }
}
