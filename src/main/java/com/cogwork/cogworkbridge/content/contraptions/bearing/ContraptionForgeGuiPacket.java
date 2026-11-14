package com.cogwork.cogworkbridge.content.contraptions.bearing;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class ContraptionForgeGuiPacket implements IMessage {
    private int entityId, windowId, guiId;
    private BlockPos pos = BlockPos.ORIGIN;
    private String modId = "";

    public ContraptionForgeGuiPacket() { }
    public ContraptionForgeGuiPacket(int entityId, BlockPos pos, int windowId, String modId, int guiId) {
        this.entityId = entityId;
        this.pos = pos.toImmutable();
        this.windowId = windowId;
        this.modId = modId;
        this.guiId = guiId;
    }
    @Override public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt(); pos = BlockPos.fromLong(buf.readLong()); windowId = buf.readInt();
        modId = ByteBufUtils.readUTF8String(buf); guiId = buf.readInt();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId); buf.writeLong(pos.toLong()); buf.writeInt(windowId);
        ByteBufUtils.writeUTF8String(buf, modId); buf.writeInt(guiId);
    }
    public static final class Handler implements IMessageHandler<ContraptionForgeGuiPacket, IMessage> {
        @Override public IMessage onMessage(ContraptionForgeGuiPacket message, MessageContext context) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.world == null || mc.player == null) return;
                Entity candidate = mc.world.getEntityByID(message.entityId);
                ModContainer owner = Loader.instance().getIndexedModList().get(message.modId);
                if (!(candidate instanceof BearingContraptionEntity entity) || owner == null) return;
                World local = entity.getInteractionWorld();
                World previous = mc.player.world;
                Object gui;
                try {
                    mc.player.setWorld(local);
                    gui = NetworkRegistry.INSTANCE.getLocalGuiContainer(owner, mc.player, message.guiId,
                            local, message.pos.getX(), message.pos.getY(), message.pos.getZ());
                } finally {
                    mc.player.setWorld(previous);
                }
                if (!(gui instanceof GuiScreen screen)) return;
                mc.displayGuiScreen(screen);
                if (mc.player.openContainer != null) mc.player.openContainer.windowId = message.windowId;
            });
            return null;
        }
    }
}
