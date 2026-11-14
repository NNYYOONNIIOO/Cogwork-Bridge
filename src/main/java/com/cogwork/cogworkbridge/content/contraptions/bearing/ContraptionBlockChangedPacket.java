package com.cogwork.cogworkbridge.content.contraptions.bearing;

import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Incremental contraption block replacement or removal. */
public final class ContraptionBlockChangedPacket implements IMessage {

    private int entityId;
    private BlockPos localPos = BlockPos.ORIGIN;
    private int stateId = -1;
    private NBTTagCompound tileData;

    public ContraptionBlockChangedPacket() {
    }

    public ContraptionBlockChangedPacket(int entityId, BlockPos localPos,
                                         int stateId, NBTTagCompound tileData) {
        this.entityId = entityId;
        this.localPos = localPos;
        this.stateId = stateId;
        this.tileData = tileData == null ? null : tileData.copy();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        entityId = buffer.readInt();
        localPos = BlockPos.fromLong(buffer.readLong());
        stateId = buffer.readInt();
        tileData = buffer.readBoolean()
                ? ByteBufUtils.readTag(buffer) : null;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeLong(localPos.toLong());
        buffer.writeInt(stateId);
        buffer.writeBoolean(tileData != null);
        if (tileData != null) {
            ByteBufUtils.writeTag(buffer, tileData);
        }
    }

    public static final class Handler
            implements IMessageHandler<ContraptionBlockChangedPacket, IMessage> {
        @Override
        public IMessage onMessage(ContraptionBlockChangedPacket message,
                                  MessageContext context) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                WorldClient world = Minecraft.getMinecraft().world;
                if (world == null) {
                    return;
                }
                Entity entity = world.getEntityByID(message.entityId);
                if (!(entity instanceof BearingContraptionEntity bearingEntity)) {
                    return;
                }
                IBlockState state = message.stateId < 0
                        ? net.minecraft.init.Blocks.AIR.getDefaultState()
                        : Block.getStateById(message.stateId);
                bearingEntity.applyClientBlockChange(
                        message.localPos, state, message.tileData);
            });
            return null;
        }
    }
}
