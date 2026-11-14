package com.cogwork.cogworkbridge.content.contraptions.bearing;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Small server correction packet for the client-side contraption angle clock. */
public final class ContraptionAnglePacket implements IMessage {

    private int entityId;
    private int poseSequence;
    private float previousAngle;
    private float angle;
    private float speed;
    private boolean stalled;

    public ContraptionAnglePacket() {
    }

    public ContraptionAnglePacket(int entityId, int poseSequence,
                                  float previousAngle, float angle,
                                  float speed, boolean stalled) {
        this.entityId = entityId;
        this.poseSequence = poseSequence;
        this.previousAngle = previousAngle;
        this.angle = angle;
        this.speed = speed;
        this.stalled = stalled;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        entityId = buffer.readInt();
        poseSequence = buffer.readInt();
        previousAngle = buffer.readFloat();
        angle = buffer.readFloat();
        speed = buffer.readFloat();
        stalled = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeInt(poseSequence);
        buffer.writeFloat(previousAngle);
        buffer.writeFloat(angle);
        buffer.writeFloat(speed);
        buffer.writeBoolean(stalled);
    }

    public static final class Handler implements IMessageHandler<ContraptionAnglePacket, IMessage> {
        @Override
        public IMessage onMessage(ContraptionAnglePacket message, MessageContext context) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                WorldClient world = Minecraft.getMinecraft().world;
                if (world == null) {
                    return;
                }
                net.minecraft.entity.Entity entity = world.getEntityByID(message.entityId);
                if (entity instanceof BearingContraptionEntity) {
                    ((BearingContraptionEntity) entity).applyServerCorrection(
                            message.poseSequence, message.previousAngle,
                            message.angle, message.speed, message.stalled);
                }
            });
            return null;
        }
    }
}
