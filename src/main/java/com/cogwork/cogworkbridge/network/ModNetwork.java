package com.cogwork.cogworkbridge.network;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.content.contraptions.glue.GlueRemovalPacket;
import com.cogwork.cogworkbridge.content.contraptions.glue.GlueSelectionPacket;
import com.cogwork.cogworkbridge.content.contraptions.glue.GlueAreaUpdatePacket;
import com.cogwork.cogworkbridge.content.contraptions.glue.GlueAreaRemovedPacket;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionAnglePacket;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionSnapshotPacket;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionBlockChangedPacket;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionBreakPacket;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionInteractPacket;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionMiningPacket;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionMiningProgressPacket;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionForgeGuiPacket;
import com.cogwork.cogworkbridge.content.kinetics.config.KineticConfigChangePacket;
import com.cogwork.cogworkbridge.content.kinetics.config.KineticConfigOpenPacket;
import com.cogwork.cogworkbridge.content.kinetics.config.KineticConfigRequestPacket;
import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencerConfigPacket;
import com.cogwork.cogworkbridge.content.fluids.ListFilterOptionPacket;
import com.cogwork.cogworkbridge.content.fluids.ListFilterJeiPacket;
import com.cogwork.cogworkbridge.content.fluids.AttributeFilterOptionPacket;
import com.cogwork.cogworkbridge.content.logistics.transport.LogisticsAmountPacket;
import com.cogwork.cogworkbridge.content.logistics.funnel.BrassFunnelConfigPacket;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BrassTunnelConfigPacket;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;

public final class ModNetwork {

    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(Reference.MOD_ID);

    private static boolean registered;

    private ModNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        CHANNEL.registerMessage(GlueSelectionPacket.Handler.class,
                GlueSelectionPacket.class, 0, Side.SERVER);
        CHANNEL.registerMessage(GlueRemovalPacket.Handler.class,
                GlueRemovalPacket.class, 1, Side.SERVER);
        registerClientMessage(GlueAreaUpdatePacket.class, 2);
        registerClientMessage(GlueAreaRemovedPacket.class, 3);
        registerClientMessage(ContraptionAnglePacket.class, 4);
        registerClientMessage(ContraptionSnapshotPacket.class, 5);
        registerClientMessage(ContraptionBlockChangedPacket.class, 6);
        CHANNEL.registerMessage(ContraptionBreakPacket.Handler.class,
                ContraptionBreakPacket.class, 7, Side.SERVER);
        CHANNEL.registerMessage(ContraptionInteractPacket.Handler.class,
                ContraptionInteractPacket.class, 8, Side.SERVER);
        CHANNEL.registerMessage(ContraptionMiningPacket.Handler.class,
                ContraptionMiningPacket.class, 9, Side.SERVER);
        registerClientMessage(ContraptionMiningProgressPacket.class, 10);
        CHANNEL.registerMessage(KineticConfigRequestPacket.Handler.class,
                KineticConfigRequestPacket.class, 11, Side.SERVER);
        registerClientMessage(KineticConfigOpenPacket.class, 12);
        CHANNEL.registerMessage(KineticConfigChangePacket.Handler.class,
                KineticConfigChangePacket.class, 13, Side.SERVER);
        CHANNEL.registerMessage(SequencerConfigPacket.Handler.class,
                SequencerConfigPacket.class, 14, Side.SERVER);
        CHANNEL.registerMessage(ListFilterOptionPacket.Handler.class,
                ListFilterOptionPacket.class, 15, Side.SERVER);
        CHANNEL.registerMessage(ListFilterJeiPacket.Handler.class,
                ListFilterJeiPacket.class, 18, Side.SERVER);
        CHANNEL.registerMessage(AttributeFilterOptionPacket.Handler.class,
                AttributeFilterOptionPacket.class, 16, Side.SERVER);
        CHANNEL.registerMessage(LogisticsAmountPacket.Handler.class, LogisticsAmountPacket.class, 17, Side.SERVER);
        CHANNEL.registerMessage(BrassFunnelConfigPacket.Handler.class, BrassFunnelConfigPacket.class, 19, Side.SERVER);
        registerClientMessage(ContraptionForgeGuiPacket.class, 20);
        CHANNEL.registerMessage(BrassTunnelConfigPacket.Handler.class, BrassTunnelConfigPacket.class, 21, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.display.DisplayLinkConfigPacket.Handler.class,
                com.cogwork.cogworkbridge.content.display.DisplayLinkConfigPacket.class, 22, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardEditPacket.Handler.class,
                com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardEditPacket.class, 23, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.redstone.LinkedControllerInputPacket.Handler.class,
                com.cogwork.cogworkbridge.content.redstone.LinkedControllerInputPacket.class, 24, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.redstone.LinkedControllerBindPacket.Handler.class,
                com.cogwork.cogworkbridge.content.redstone.LinkedControllerBindPacket.class, 25, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.equipment.SymmetryWandConfigPacket.Handler.class,
                com.cogwork.cogworkbridge.content.equipment.SymmetryWandConfigPacket.class, 26, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.contraptions.elevator.ElevatorContactConfigPacket.Handler.class,
                com.cogwork.cogworkbridge.content.contraptions.elevator.ElevatorContactConfigPacket.class, 27, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.equipment.ExtendoGripEntityPacket.Handler.class,
                com.cogwork.cogworkbridge.content.equipment.ExtendoGripEntityPacket.class, 28, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.contraptions.elevator.ElevatorFloorSelectionPacket.Handler.class,
                com.cogwork.cogworkbridge.content.contraptions.elevator.ElevatorFloorSelectionPacket.class, 29, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.redstone.PulseDiodeConfigPacket.Handler.class,
                com.cogwork.cogworkbridge.content.redstone.PulseDiodeConfigPacket.class, 30, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.logistics.packagebox.PackageFilterPacket.Handler.class,
                com.cogwork.cogworkbridge.content.logistics.packagebox.PackageFilterPacket.class, 31, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.redstone.DetectorConfigPacket.Handler.class,
                com.cogwork.cogworkbridge.content.redstone.DetectorConfigPacket.class, 32, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.logistics.packager.StockTickerRequestPacket.Handler.class,
                com.cogwork.cogworkbridge.content.logistics.packager.StockTickerRequestPacket.class, 33, Side.SERVER);
        registerClientMessage(com.cogwork.cogworkbridge.content.logistics.packager.StockTickerDataPacket.class, 34);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.logistics.port.PackagePortConfigPacket.Handler.class,
                com.cogwork.cogworkbridge.content.logistics.port.PackagePortConfigPacket.class, 35, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.logistics.packager.StockTickerOrderPacket.Handler.class,
                com.cogwork.cogworkbridge.content.logistics.packager.StockTickerOrderPacket.class, 36, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelConfigPacket.Handler.class,
                com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelConfigPacket.class, 37, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelConnectionPacket.Handler.class,
                com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelConnectionPacket.class, 38, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.logistics.packager.RedstoneRequesterConfigPacket.Handler.class,
                com.cogwork.cogworkbridge.content.logistics.packager.RedstoneRequesterConfigPacket.class, 39, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.logistics.packager.StockTickerSecurityPacket.Handler.class,
                com.cogwork.cogworkbridge.content.logistics.packager.StockTickerSecurityPacket.class, 40, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.logistics.packager.StockTickerCategoryPacket.Handler.class,
                com.cogwork.cogworkbridge.content.logistics.packager.StockTickerCategoryPacket.class, 41, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorInteractionPacket.Handler.class,
                com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorInteractionPacket.class, 42, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorRidePacket.Handler.class,
                com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorRidePacket.class, 43, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorConnectionPacket.Handler.class,
                com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorConnectionPacket.class, 44, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.equipment.WorldshaperPacket.Handler.class,
                com.cogwork.cogworkbridge.content.equipment.WorldshaperPacket.class, 45, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.equipment.ToolboxActionPacket.Handler.class,
                com.cogwork.cogworkbridge.content.equipment.ToolboxActionPacket.class, 46, Side.SERVER);
        registerClientMessage(com.cogwork.cogworkbridge.content.equipment.ToolboxSyncPacket.class, 47);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.equipment.BlueprintEditPacket.Handler.class,
                com.cogwork.cogworkbridge.content.equipment.BlueprintEditPacket.class, 48, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.kinetics.encased.WrenchRotationPacket.Handler.class,
                com.cogwork.cogworkbridge.content.kinetics.encased.WrenchRotationPacket.class, 49, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.schematics.SchematicUploadPacket.Handler.class,
                com.cogwork.cogworkbridge.content.schematics.SchematicUploadPacket.class, 50, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.schematics.SchematicActionPacket.Handler.class,
                com.cogwork.cogworkbridge.content.schematics.SchematicActionPacket.class, 51, Side.SERVER);
        registerClientMessage(com.cogwork.cogworkbridge.content.schematics.SchematicDownloadPacket.class, 52);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.schematics.SchematicInstantPacket.Handler.class,
                com.cogwork.cogworkbridge.content.schematics.SchematicInstantPacket.class, 53, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.schematics.SchematicEditPacket.Handler.class,
                com.cogwork.cogworkbridge.content.schematics.SchematicEditPacket.class, 54, Side.SERVER);
        registerClientMessage(com.cogwork.cogworkbridge.content.contraptions.mounted.MinecartCouplingSyncPacket.class, 55);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.trains.track.RailwayStationPacket.Handler.class,
                com.cogwork.cogworkbridge.content.trains.track.RailwayStationPacket.class, 56, Side.SERVER);
        registerClientMessage(com.cogwork.cogworkbridge.content.trains.track.TrainControlClientPacket.class, 57);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.trains.track.TrainControlInputPacket.Handler.class,
                com.cogwork.cogworkbridge.content.trains.track.TrainControlInputPacket.class, 58, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.trains.track.TrainSchedulePacket.Handler.class,
                com.cogwork.cogworkbridge.content.trains.track.TrainSchedulePacket.class, 59, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.logistics.table.TableClothPricePacket.Handler.class,
                com.cogwork.cogworkbridge.content.logistics.table.TableClothPricePacket.class, 60, Side.SERVER);
        CHANNEL.registerMessage(com.cogwork.cogworkbridge.content.trains.track.TrackConnectPacket.Handler.class,
                com.cogwork.cogworkbridge.content.trains.track.TrackConnectPacket.class, 61, Side.SERVER);
        registered = true;
    }

    private static <T extends IMessage> void registerClientMessage(Class<T> type, int id) {
        // Both sides need the codec mapping; only the client proxy loads its handler.
        CHANNEL.registerMessage((message, context) -> CogworkBridge.proxy.handleClientMessage(message, context),
                type, id, Side.CLIENT);
    }
}
