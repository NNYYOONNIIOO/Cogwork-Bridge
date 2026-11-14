package com.cogwork.cogworkbridge.content.kinetics.config;

import com.cogwork.cogworkbridge.client.KineticMachineConfigScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class KineticConfigOpenPacket implements IMessage {
    private BlockPos pos = BlockPos.ORIGIN;
    private int configId;
    private int value;

    public KineticConfigOpenPacket() {
    }

    public KineticConfigOpenPacket(BlockPos pos, KineticMachineConfig config,
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
            implements IMessageHandler<KineticConfigOpenPacket, IMessage> {
        @Override
        public IMessage onMessage(KineticConfigOpenPacket message,
                                  MessageContext context) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (message.configId < 0
                        || message.configId >= KineticMachineConfig.values().length) {
                    return;
                }
                Minecraft.getMinecraft().displayGuiScreen(
                        new KineticMachineConfigScreen(message.pos,
                                KineticMachineConfig.values()[message.configId],
                                message.value));
            });
            return null;
        }
    }
}
