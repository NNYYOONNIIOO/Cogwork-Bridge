package com.cogwork.cogworkbridge.content.contraptions.glue;

import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.AllEntities;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.Set;

/** Client-to-server request to create one two-point glue area. */
public final class GlueSelectionPacket implements IMessage {

    private BlockPos first;
    private BlockPos second;
    private EnumHand hand = EnumHand.MAIN_HAND;

    public GlueSelectionPacket() {
    }

    public GlueSelectionPacket(BlockPos first, BlockPos second) {
        this(first, second, EnumHand.MAIN_HAND);
    }

    public GlueSelectionPacket(BlockPos first, BlockPos second, EnumHand hand) {
        this.first = first;
        this.second = second;
        this.hand = hand == null ? EnumHand.MAIN_HAND : hand;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        first = readBlockPos(buffer);
        second = readBlockPos(buffer);
        hand = buffer.readBoolean() ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        writeBlockPos(buffer, first);
        writeBlockPos(buffer, second);
        buffer.writeBoolean(hand == EnumHand.OFF_HAND);
    }

    private static BlockPos readBlockPos(ByteBuf buffer) {
        return new BlockPos(buffer.readInt(), buffer.readInt(), buffer.readInt());
    }

    private static void writeBlockPos(ByteBuf buffer, BlockPos pos) {
        buffer.writeInt(pos.getX());
        buffer.writeInt(pos.getY());
        buffer.writeInt(pos.getZ());
    }

    public static final class Handler implements IMessageHandler<GlueSelectionPacket, IMessage> {
        @Override
        public IMessage onMessage(GlueSelectionPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            if (message.first == null || message.second == null) {
                return null;
            }
            GlueBox box = GlueBox.span(AllEntities.gridPosition(message.first),
                    AllEntities.gridPosition(message.second));
            Set<GridPos> group = GlueSelectionRules.connectedGroup(
                    player.world, message.first, message.second);
            if (!group.contains(AllEntities.gridPosition(message.second))) {
                return null;
            }
            ItemStack glue = player.getHeldItem(message.hand);
            if (!(glue.getItem() instanceof SuperGlueItem)) {
                return null;
            }
            if (!GlueSelectionRules.canAfford(player.capabilities.isCreativeMode,
                    glue.getItemDamage(), glue.getMaxDamage())) {
                return null;
            }

            SuperGlueEntity entity = new SuperGlueEntity(player.world, box, group);
            if (!player.world.spawnEntity(entity)) return null;
            com.cogwork.cogworkbridge.content.advancement.AllAdvancements.award(player, "super_glue");
            ModNetwork.CHANNEL.sendToAllAround(new GlueAreaUpdatePacket(
                            entity.getEntityId(), box, group, true),
                    new NetworkRegistry.TargetPoint(player.dimension, entity.posX, entity.posY, entity.posZ, 64.0D));
            if (!player.capabilities.isCreativeMode) {
                glue.setItemDamage(glue.getItemDamage() + 1);
            }
            return null;
        }
    }
}
