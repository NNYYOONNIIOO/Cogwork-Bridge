package com.cogwork.cogworkbridge.content.contraptions.glue;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.LinkedHashSet;
import java.util.Set;

/** Server-to-client mirror of a persistent glue box. */
public final class GlueAreaUpdatePacket implements IMessage {

    private int entityId;
    private GlueBox box = GlueBox.of(0, 0, 0, 1, 1, 1);
    private Set<GridPos> cells = Set.of();
    private boolean created;

    public GlueAreaUpdatePacket() {
    }

    public GlueAreaUpdatePacket(int entityId, GlueBox box) {
        this(entityId, box, box.positions());
    }

    public GlueAreaUpdatePacket(int entityId, GlueBox box, Set<GridPos> cells) {
        this(entityId, box, cells, false);
    }

    public GlueAreaUpdatePacket(int entityId, GlueBox box, Set<GridPos> cells,
                                boolean created) {
        this.entityId = entityId;
        this.box = box;
        this.cells = Set.copyOf(cells);
        this.created = created;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        entityId = buffer.readInt();
        box = GlueBox.of(buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readInt(), buffer.readInt());
        int count = buffer.readInt();
        Set<GridPos> received = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            received.add(new GridPos(buffer.readInt(), buffer.readInt(), buffer.readInt()));
        }
        cells = Set.copyOf(received);
        created = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeInt(box.minX());
        buffer.writeInt(box.minY());
        buffer.writeInt(box.minZ());
        buffer.writeInt(box.maxX());
        buffer.writeInt(box.maxY());
        buffer.writeInt(box.maxZ());
        buffer.writeInt(cells.size());
        for (GridPos pos : cells) {
            buffer.writeInt(pos.x());
            buffer.writeInt(pos.y());
            buffer.writeInt(pos.z());
        }
        buffer.writeBoolean(created);
    }

    public int entityId() {
        return entityId;
    }

    public GlueBox box() {
        return box;
    }

    public Set<GridPos> cells() {
        return cells;
    }

    public boolean created() {
        return created;
    }

    public static final class Handler implements IMessageHandler<GlueAreaUpdatePacket, IMessage> {
        @Override
        public IMessage onMessage(GlueAreaUpdatePacket message, MessageContext context) {
            Minecraft.getMinecraft().addScheduledTask(() ->
                    ClientGlueAreas.put(message.entityId, message.box,
                            message.cells, message.created));
            return null;
        }
    }
}
