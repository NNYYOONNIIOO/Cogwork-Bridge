package com.cogwork.cogworkbridge.content.kinetics.sequencer;

import com.cogwork.cogworkbridge.content.kinetics.config.KineticMachineConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

public final class SequencerConfigPacket implements IMessage {
    private BlockPos pos = BlockPos.ORIGIN;
    private final List<SequencerInstruction> instructions = new ArrayList<>();
    private boolean valid;

    public SequencerConfigPacket() {
    }

    public SequencerConfigPacket(BlockPos pos, List<SequencerInstruction> instructions) {
        this.pos = pos.toImmutable();
        this.instructions.addAll(instructions.subList(0, Math.min(instructions.size(), SequencerInstruction.MAX_STEPS)));
        valid = !this.instructions.isEmpty();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        pos = BlockPos.fromLong(buffer.readLong());
        int count = buffer.readUnsignedByte();
        valid = count > 0 && count <= SequencerInstruction.MAX_STEPS && buffer.readableBytes() >= count * 6;
        if (!valid) {
            return;
        }
        SequencerInstruction.Type[] types = SequencerInstruction.Type.values();
        for (int i = 0; i < count; i++) {
            int type = buffer.readUnsignedByte();
            int value = buffer.readInt();
            int speed = buffer.readByte();
            if (type >= types.length || speed != -2 && speed != -1 && speed != 1 && speed != 2) {
                valid = false;
                return;
            }
            instructions.add(new SequencerInstruction(types[type], value, speed));
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(pos.toLong());
        buffer.writeByte(instructions.size());
        for (SequencerInstruction instruction : instructions) {
            buffer.writeByte(instruction.type().ordinal());
            buffer.writeInt(instruction.value());
            buffer.writeByte(instruction.speed());
        }
    }

    public static final class Handler implements IMessageHandler<SequencerConfigPacket, IMessage> {
        @Override
        public IMessage onMessage(SequencerConfigPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.valid && KineticMachineConfig.mayConfigure(player, message.pos)
                        && player.world.getTileEntity(message.pos) instanceof SequencedGearshiftTileEntity tile) {
                    tile.setInstructions(message.instructions);
                }
            });
            return null;
        }
    }
}
