package com.cogwork.cogworkbridge.content.kinetics.encased;

import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** The server resolves a named property against the current world state; no client state is trusted. */
public final class WrenchRotationPacket implements IMessage {
    private BlockPos pos = BlockPos.ORIGIN;
    private int expectedBlock, expectedMeta, face;
    private String property = "", value = "";

    public WrenchRotationPacket() { }
    public WrenchRotationPacket(BlockPos pos, IBlockState state, EnumFacing face, IProperty<?> property, IBlockState selected) {
        this.pos = pos.toImmutable();
        this.expectedBlock = Block.getIdFromBlock(state.getBlock());
        this.expectedMeta = state.getBlock().getMetaFromState(state);
        this.face = face.getIndex();
        this.property = property.getName();
        this.value = WrenchRotationOptions.valueName(selected, property);
    }
    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        expectedBlock = buf.readInt();
        expectedMeta = buf.readUnsignedByte();
        face = buf.readUnsignedByte();
        property = ByteBufUtils.readUTF8String(buf);
        value = ByteBufUtils.readUTF8String(buf);
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(expectedBlock);
        buf.writeByte(expectedMeta);
        buf.writeByte(face);
        ByteBufUtils.writeUTF8String(buf, property);
        ByteBufUtils.writeUTF8String(buf, value);
    }
    public static final class Handler implements IMessageHandler<WrenchRotationPacket, IMessage> {
        @Override public IMessage onMessage(WrenchRotationPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }
        private static void apply(EntityPlayerMP player, WrenchRotationPacket message) {
            if (message.face >= EnumFacing.VALUES.length || message.property.length() > 64 || message.value.length() > 64
                    || player.isSpectator() || !(player.getHeldItemMainhand().getItem() instanceof WrenchItem)
                    || !player.world.isBlockLoaded(message.pos)) return;
            EnumFacing face = EnumFacing.byIndex(message.face);
            double reach = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue() + 0.75D;
            if (player.getDistanceSqToCenter(message.pos) > reach * reach
                    || !player.canPlayerEdit(message.pos, face, player.getHeldItemMainhand())
                    || !player.world.isBlockModifiable(player, message.pos)) return;
            IBlockState state = player.world.getBlockState(message.pos);
            if (Block.getIdFromBlock(state.getBlock()) != message.expectedBlock
                    || state.getBlock().getMetaFromState(state) != message.expectedMeta) return;
            for (IProperty<?> property : WrenchRotationOptions.properties(player.world, message.pos, state)) {
                if (!property.getName().equals(message.property)) continue;
                for (IBlockState choice : WrenchRotationOptions.states(player.world, message.pos, state, property)) {
                    if (WrenchRotationOptions.valueName(choice, property).equals(message.value)) {
                        WrenchItem.applyMenuRotation(player.world, message.pos, state, choice);
                        return;
                    }
                }
            }
        }
    }
}
