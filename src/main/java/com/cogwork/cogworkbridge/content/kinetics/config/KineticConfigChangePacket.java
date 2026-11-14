package com.cogwork.cogworkbridge.content.kinetics.config;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class KineticConfigChangePacket implements IMessage {
    private BlockPos pos = BlockPos.ORIGIN;
    private int configId;
    private int value;

    public KineticConfigChangePacket() {
    }

    public KineticConfigChangePacket(BlockPos pos, KineticMachineConfig config,
                                     int value) {
        this.pos = pos.toImmutable();
        configId = config.id();
        this.value = value;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        pos = BlockPos.fromLong(buffer.readLong());
        configId = buffer.readByte();
        value = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(pos.toLong());
        buffer.writeByte(configId);
        buffer.writeInt(value);
    }

    public static final class Handler
            implements IMessageHandler<KineticConfigChangePacket, IMessage> {
        @Override
        public IMessage onMessage(KineticConfigChangePacket message,
                                  MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!KineticMachineConfig.mayConfigure(player, message.pos)
                        || message.configId < 0
                        || message.configId >= KineticMachineConfig.values().length) {
                    return;
                }
                TileEntity tile = player.world.getTileEntity(message.pos);
                KineticMachineConfig config = KineticMachineConfig.forTile(tile);
                if (config == KineticMachineConfig.values()[message.configId]) {
                    if (config == KineticMachineConfig.VALVE_HANDLE && message.value == Integer.MAX_VALUE) {
                        ((com.cogwork.cogworkbridge.content.kinetics.crank.ValveHandleTileEntity) tile).turn(false);
                        return;
                    }
                    if (config == KineticMachineConfig.WINDMILL_BEARING && message.value == Integer.MAX_VALUE) {
                        com.cogwork.cogworkbridge.content.contraptions.bearing.WindmillBearingTileEntity bearing =
                                (com.cogwork.cogworkbridge.content.contraptions.bearing.WindmillBearingTileEntity) tile;
                        if (bearing.isAssembled()) bearing.requestDisassemble(); else bearing.requestAssemble();
                        return;
                    }
                    config.write(tile, message.value);
                }
            });
            return null;
        }
    }
}
