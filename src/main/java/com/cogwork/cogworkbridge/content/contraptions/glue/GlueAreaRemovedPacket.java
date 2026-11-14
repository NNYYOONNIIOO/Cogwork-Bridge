package com.cogwork.cogworkbridge.content.contraptions.glue;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class GlueAreaRemovedPacket implements IMessage {

    private int entityId;

    public GlueAreaRemovedPacket() {
    }

    public GlueAreaRemovedPacket(int entityId) {
        this.entityId = entityId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        entityId = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(entityId);
    }

    public static final class Handler implements IMessageHandler<GlueAreaRemovedPacket, IMessage> {
        @Override
        public IMessage onMessage(GlueAreaRemovedPacket message, MessageContext context) {
            Minecraft.getMinecraft().addScheduledTask(() ->
                    ClientGlueAreas.remove(message.entityId));
            return null;
        }
    }
}
