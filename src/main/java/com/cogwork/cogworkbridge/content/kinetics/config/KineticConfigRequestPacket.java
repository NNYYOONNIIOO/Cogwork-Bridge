package com.cogwork.cogworkbridge.content.kinetics.config;

import com.cogwork.cogworkbridge.network.ModNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class KineticConfigRequestPacket implements IMessage {
    private BlockPos pos = BlockPos.ORIGIN;

    public KineticConfigRequestPacket() {
    }

    public KineticConfigRequestPacket(BlockPos pos) {
        this.pos = pos.toImmutable();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        pos = BlockPos.fromLong(buffer.readLong());
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(pos.toLong());
    }

    public static final class Handler
            implements IMessageHandler<KineticConfigRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(KineticConfigRequestPacket message,
                                  MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!KineticMachineConfig.mayConfigure(player, message.pos)) {
                    return;
                }
                TileEntity tile = player.world.getTileEntity(message.pos);
                KineticMachineConfig config = KineticMachineConfig.forTile(tile);
                if (config != null) {
                    ModNetwork.CHANNEL.sendTo(new KineticConfigOpenPacket(
                            message.pos, config, config.read(tile)), player);
                }
            });
            return null;
        }
    }
}
