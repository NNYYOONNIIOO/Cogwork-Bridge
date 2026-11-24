package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionAnglePacket;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionBlockChangedPacket;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionForgeGuiPacket;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionMiningProgressPacket;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionSnapshotPacket;
import com.cogwork.cogworkbridge.content.contraptions.glue.GlueAreaRemovedPacket;
import com.cogwork.cogworkbridge.content.contraptions.glue.GlueAreaUpdatePacket;
import com.cogwork.cogworkbridge.content.contraptions.mounted.MinecartCouplingSyncPacket;
import com.cogwork.cogworkbridge.content.equipment.ToolboxSyncPacket;
import com.cogwork.cogworkbridge.content.kinetics.config.KineticConfigOpenPacket;
import com.cogwork.cogworkbridge.content.logistics.packager.StockTickerDataPacket;
import com.cogwork.cogworkbridge.content.schematics.SchematicDownloadPacket;
import com.cogwork.cogworkbridge.content.trains.track.TrainControlClientPacket;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Client handlers are linked only when the physical client receives a packet. */
@SideOnly(Side.CLIENT)
public final class ClientPacketHandlers {
    private static final Map<Class<?>, BiFunction<IMessage, MessageContext, IMessage>> HANDLERS = Map.ofEntries(
            bind(GlueAreaUpdatePacket.class, new GlueAreaUpdatePacket.Handler()),
            bind(GlueAreaRemovedPacket.class, new GlueAreaRemovedPacket.Handler()),
            bind(ContraptionAnglePacket.class, new ContraptionAnglePacket.Handler()),
            bind(ContraptionSnapshotPacket.class, new ContraptionSnapshotPacket.Handler()),
            bind(ContraptionBlockChangedPacket.class, new ContraptionBlockChangedPacket.Handler()),
            bind(ContraptionMiningProgressPacket.class, new ContraptionMiningProgressPacket.Handler()),
            bind(KineticConfigOpenPacket.class, new KineticConfigOpenPacket.Handler()),
            bind(ContraptionForgeGuiPacket.class, new ContraptionForgeGuiPacket.Handler()),
            bind(StockTickerDataPacket.class, new StockTickerDataPacket.Handler()),
            bind(ToolboxSyncPacket.class, new ToolboxSyncPacket.Handler()),
            bind(SchematicDownloadPacket.class, new SchematicDownloadPacket.Handler()),
            bind(MinecartCouplingSyncPacket.class, new MinecartCouplingSyncPacket.Handler()),
            bind(TrainControlClientPacket.class, new TrainControlClientPacket.Handler()));

    private ClientPacketHandlers() { }

    private static <T extends IMessage> Map.Entry<Class<?>, BiFunction<IMessage, MessageContext, IMessage>> bind(
            Class<T> type, IMessageHandler<T, IMessage> handler) {
        return Map.entry(type, (message, context) -> handler.onMessage(type.cast(message), context));
    }

    public static IMessage handle(IMessage message, MessageContext context) {
        return HANDLERS.get(message.getClass()).apply(message, context);
    }
}
