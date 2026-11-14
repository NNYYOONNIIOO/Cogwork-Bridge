package com.cogwork.cogworkbridge.content.contraptions.glue;

import io.netty.buffer.ByteBuf;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client-to-server request to remove one existing glue area. */
public final class GlueRemovalPacket implements IMessage {

    private int entityId;

    public GlueRemovalPacket() {
    }

    public GlueRemovalPacket(int entityId) {
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

    public static final class Handler implements IMessageHandler<GlueRemovalPacket, IMessage> {
        @Override
        public IMessage onMessage(GlueRemovalPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            Entity entity = player.world.getEntityByID(message.entityId);
            if (!(entity instanceof SuperGlueEntity)) {
                return null;
            }
            if (player.getDistanceSq(entity) > 32 * 32) {
                return null;
            }
            SuperGlueEntity glue = (SuperGlueEntity) entity;
            GlueFeedback.playRemoval(player.world, glue.getGlueBox());
            ModNetwork.CHANNEL.sendToAllAround(new GlueAreaRemovedPacket(entity.getEntityId()),
                    new NetworkRegistry.TargetPoint(player.dimension, entity.posX, entity.posY, entity.posZ, 64.0D));
            entity.setDead();
            return null;
        }
    }
}
