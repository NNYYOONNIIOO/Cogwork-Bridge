package com.cogwork.cogworkbridge.content.contraptions.bearing;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Full contraption state sent after the MovingWorld entity has spawned. */
public final class ContraptionSnapshotPacket implements IMessage {

    private int entityId;
    private NBTTagCompound snapshot = new NBTTagCompound();

    public ContraptionSnapshotPacket() {
    }

    public ContraptionSnapshotPacket(int entityId, NBTTagCompound snapshot) {
        this.entityId = entityId;
        this.snapshot = snapshot;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        entityId = buffer.readInt();
        snapshot = ByteBufUtils.readTag(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(entityId);
        ByteBufUtils.writeTag(buffer, snapshot);
    }

    public static final class Handler implements IMessageHandler<ContraptionSnapshotPacket, IMessage> {
        @Override
        public IMessage onMessage(ContraptionSnapshotPacket message, MessageContext context) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                WorldClient world = Minecraft.getMinecraft().world;
                if (world == null) {
                    return;
                }
                net.minecraft.entity.Entity entity = world.getEntityByID(message.entityId);
                if (!(entity instanceof BearingContraptionEntity bearingEntity)) {
                    BearingContraptionEntity.queueClientSnapshot(message.entityId, message.snapshot);
                    return;
                }
                bearingEntity.acceptClientSnapshot(message.snapshot);
            });
            return null;
        }
    }
}
