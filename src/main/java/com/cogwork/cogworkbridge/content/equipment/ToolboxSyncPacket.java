package com.cogwork.cogworkbridge.content.equipment;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class ToolboxSyncPacket implements IMessage {
    private NBTTagCompound links = new NBTTagCompound();
    public ToolboxSyncPacket() { }
    public ToolboxSyncPacket(NBTTagCompound links) { this.links = links; }
    @Override public void toBytes(ByteBuf buf) { ByteBufUtils.writeTag(buf, links); }
    @Override public void fromBytes(ByteBuf buf) { NBTTagCompound read = ByteBufUtils.readTag(buf); links = read == null ? new NBTTagCompound() : read; }
    public static final class Handler implements IMessageHandler<ToolboxSyncPacket, IMessage> {
        @Override public IMessage onMessage(ToolboxSyncPacket message, MessageContext context) {
            com.cogwork.cogworkbridge.CogworkBridge.proxy.syncToolboxLinks(message.links);
            return null;
        }
    }
}
