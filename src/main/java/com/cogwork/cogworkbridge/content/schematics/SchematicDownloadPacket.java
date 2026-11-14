package com.cogwork.cogworkbridge.content.schematics;

import com.cogwork.cogworkbridge.CogworkBridge;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class SchematicDownloadPacket implements IMessage {
    private String id = "";
    private int total, offset;
    private byte[] payload = new byte[0];
    public SchematicDownloadPacket() { }
    public SchematicDownloadPacket(String id, int total, int offset, byte[] payload) {
        this.id = id; this.total = total; this.offset = offset; this.payload = payload;
    }
    @Override public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, id); buf.writeInt(total); buf.writeInt(offset); buf.writeShort(payload.length); buf.writeBytes(payload);
    }
    @Override public void fromBytes(ByteBuf buf) {
        id = ByteBufUtils.readUTF8String(buf); total = buf.readInt(); offset = buf.readInt();
        int len = buf.readUnsignedShort();
        if (len > SchematicData.CHUNK || len > buf.readableBytes()) { total = -1; return; }
        payload = new byte[len]; buf.readBytes(payload);
    }
    public static final class Handler implements IMessageHandler<SchematicDownloadPacket, IMessage> {
        @Override public IMessage onMessage(SchematicDownloadPacket packet, MessageContext context) {
            CogworkBridge.proxy.receiveSchematic(packet.id, packet.total, packet.offset, packet.payload);
            return null;
        }
    }
}
