package com.cogwork.cogworkbridge.registry;

import com.cogwork.cogworkbridge.content.contraptions.bearing.MechanicalBearingBlock;
import com.cogwork.cogworkbridge.content.contraptions.bearing.MechanicalBearingTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.actors.harvester.MechanicalHarvesterBlock;
import com.cogwork.cogworkbridge.content.contraptions.actors.harvester.MechanicalHarvesterTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.actors.plough.MechanicalPloughBlock;
import com.cogwork.cogworkbridge.content.contraptions.actors.plough.MechanicalPloughTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerBlock;
import com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.chassis.ChassisBlock;
import com.cogwork.cogworkbridge.content.contraptions.chassis.ChassisTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.chassis.StickerBlock;
import com.cogwork.cogworkbridge.content.contraptions.chassis.StickerTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.placement.KineticPlacementItemBlock;
import com.cogwork.cogworkbridge.content.processing.AssemblyOperatorItemBlock;
import com.cogwork.cogworkbridge.content.kinetics.chain.ChainDriveBlock;
import com.cogwork.cogworkbridge.content.kinetics.chain.ChainDriveTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillBlock;
import com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.deployer.DeployerBlock;
import com.cogwork.cogworkbridge.content.kinetics.deployer.DeployerTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanBlock;
import com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.fan.NozzleBlock;
import com.cogwork.cogworkbridge.content.kinetics.fan.NozzleTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.encased.EncasedShaftBlock;
import com.cogwork.cogworkbridge.content.kinetics.encased.EncasedShaftTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.encased.EncasedCogwheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.encased.EncasedCogwheelTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.gearbox.GearboxBlock;
import com.cogwork.cogworkbridge.content.kinetics.gearbox.GearboxTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.motor.CreativeMotorBlock;
import com.cogwork.cogworkbridge.content.kinetics.motor.CreativeMotorTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.crank.HandCrankBlock;
import com.cogwork.cogworkbridge.content.kinetics.crank.HandCrankTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.crank.ValveHandleBlock;
import com.cogwork.cogworkbridge.content.kinetics.crank.ValveHandleTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock;
import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock;
import com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.crusher.CrushingWheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.crusher.CrushingWheelTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.crusher.CrushingWheelControllerBlock;
import com.cogwork.cogworkbridge.content.kinetics.crusher.CrushingWheelControllerTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.speedController.SpeedControllerBlock;
import com.cogwork.cogworkbridge.content.kinetics.speedController.SpeedControllerTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.transmission.SplitShaftBlock;
import com.cogwork.cogworkbridge.content.kinetics.transmission.SplitShaftTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencedGearshiftBlock;
import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencedGearshiftTileEntity;
import com.cogwork.cogworkbridge.content.processing.basin.BasinBlock;
import com.cogwork.cogworkbridge.content.processing.basin.BasinTileEntity;
import com.cogwork.cogworkbridge.content.fluids.FluidPipeBlock;
import com.cogwork.cogworkbridge.content.fluids.FluidPipeTileEntity;
import com.cogwork.cogworkbridge.content.fluids.GlassFluidPipeBlock;
import com.cogwork.cogworkbridge.content.fluids.EncasedFluidPipeBlock;
import com.cogwork.cogworkbridge.content.fluids.MechanicalPumpBlock;
import com.cogwork.cogworkbridge.content.fluids.MechanicalPumpTileEntity;
import com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeBlock;
import com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeTileEntity;
import com.cogwork.cogworkbridge.content.fluids.FluidValveBlock;
import com.cogwork.cogworkbridge.content.fluids.FluidValveTileEntity;
import com.cogwork.cogworkbridge.content.fluids.FluidTankBlock;
import com.cogwork.cogworkbridge.content.fluids.FluidTankItem;
import com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity;
import com.cogwork.cogworkbridge.content.fluids.CreativeFluidTankBlock;
import com.cogwork.cogworkbridge.content.fluids.CreativeFluidTankTileEntity;
import com.cogwork.cogworkbridge.content.fluids.FluidSpoutBlock;
import com.cogwork.cogworkbridge.content.fluids.FluidSpoutTileEntity;
import com.cogwork.cogworkbridge.content.fluids.HosePulleyBlock;
import com.cogwork.cogworkbridge.content.fluids.HosePulleyTileEntity;
import com.cogwork.cogworkbridge.content.fluids.PortableFluidInterfaceBlock;
import com.cogwork.cogworkbridge.content.fluids.PortableFluidInterfaceTileEntity;
import com.cogwork.cogworkbridge.content.fluids.ItemDrainBlock;
import com.cogwork.cogworkbridge.content.fluids.ItemDrainTileEntity;
import com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmBlock;
import com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmItem;
import com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmTileEntity;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BeltTunnelBlock;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BeltTunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BrassTunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.depot.DepotBlock;
import com.cogwork.cogworkbridge.content.logistics.depot.DepotTileEntity;
import com.cogwork.cogworkbridge.content.logistics.crate.CreativeCrateBlock;
import com.cogwork.cogworkbridge.content.logistics.crate.CreativeCrateTileEntity;
import com.cogwork.cogworkbridge.content.logistics.portable.PortableStorageInterfaceBlock;
import com.cogwork.cogworkbridge.content.logistics.portable.PortableStorageInterfaceTileEntity;
import com.cogwork.cogworkbridge.content.logistics.chute.ChuteBlock;
import com.cogwork.cogworkbridge.content.logistics.chute.ChuteTileEntity;
import com.cogwork.cogworkbridge.content.logistics.chute.SmartChuteTileEntity;
import com.cogwork.cogworkbridge.content.logistics.funnel.FunnelBlock;
import com.cogwork.cogworkbridge.content.logistics.funnel.FunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.funnel.BrassFunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltBlock;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.content.logistics.vault.ItemVaultBlock;
import com.cogwork.cogworkbridge.content.logistics.vault.ItemVaultTileEntity;
import com.cogwork.cogworkbridge.content.logistics.hatch.ItemHatchBlock;
import com.cogwork.cogworkbridge.content.logistics.hatch.ItemHatchTileEntity;
import com.cogwork.cogworkbridge.content.logistics.ejector.EjectorBlock;
import com.cogwork.cogworkbridge.content.logistics.ejector.EjectorTileEntity;
import com.cogwork.cogworkbridge.content.logistics.ejector.EjectorItem;
import com.cogwork.cogworkbridge.content.processing.burner.BlazeBurnerBlock;
import com.cogwork.cogworkbridge.content.processing.burner.BlazeBurnerTileEntity;
import com.cogwork.cogworkbridge.content.processing.burner.EmptyBlazeBurnerItem;
import com.cogwork.cogworkbridge.content.processing.press.MechanicalPressBlock;
import com.cogwork.cogworkbridge.content.processing.press.MechanicalPressTileEntity;
import com.cogwork.cogworkbridge.content.processing.mixer.MechanicalMixerBlock;
import com.cogwork.cogworkbridge.content.processing.mixer.MechanicalMixerTileEntity;
import com.cogwork.cogworkbridge.content.processing.millstone.MillstoneBlock;
import com.cogwork.cogworkbridge.content.processing.millstone.MillstoneTileEntity;
import com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterBlock;
import com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterItem;
import com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Every block and tile entity of Cogwork Bridge, declared once.
 *
 * <p>Rule: a visual or behavioural variant is a <em>registry name</em>, never a
 * metadata value. See {@code docs/BLOCK-META-CONTRACT.md}.
 */
public final class AllBlocks {

    public static final com.cogwork.cogworkbridge.content.trains.track.TrackBlock TRACK =
            BlockFamily.register("track", new com.cogwork.cogworkbridge.content.trains.track.TrackBlock(),
                    com.cogwork.cogworkbridge.content.trains.track.TrackItem::new);
    public static final com.cogwork.cogworkbridge.content.trains.track.FakeTrackBlock FAKE_TRACK =
            BlockFamily.register("fake_track",new com.cogwork.cogworkbridge.content.trains.track.FakeTrackBlock(),null);
    public static final com.cogwork.cogworkbridge.content.trains.track.RailwayTargetBlock TRACK_STATION =
            BlockFamily.register("track_station",new com.cogwork.cogworkbridge.content.trains.track.RailwayTargetBlock("station"),
                    block -> new com.cogwork.cogworkbridge.content.trains.track.RailwayTargetItem(block,"station"));
    public static final com.cogwork.cogworkbridge.content.trains.track.RailwayTargetBlock TRACK_SIGNAL =
            BlockFamily.register("track_signal",new com.cogwork.cogworkbridge.content.trains.track.RailwayTargetBlock("signal"),
                    block -> new com.cogwork.cogworkbridge.content.trains.track.RailwayTargetItem(block,"signal"));
    public static final com.cogwork.cogworkbridge.content.trains.track.RailwayTargetBlock TRACK_OBSERVER =
            BlockFamily.register("track_observer",new com.cogwork.cogworkbridge.content.trains.track.RailwayTargetBlock("observer"),
                    block -> new com.cogwork.cogworkbridge.content.trains.track.RailwayTargetItem(block,"observer"));
    public static final com.cogwork.cogworkbridge.content.trains.track.BogeyBlock SMALL_BOGEY =
            BlockFamily.register("small_bogey",new com.cogwork.cogworkbridge.content.trains.track.BogeyBlock(false),null);
    public static final com.cogwork.cogworkbridge.content.trains.track.BogeyBlock LARGE_BOGEY =
            BlockFamily.register("large_bogey",new com.cogwork.cogworkbridge.content.trains.track.BogeyBlock(true),null);
    public static final com.cogwork.cogworkbridge.content.trains.track.TrainControlsBlock TRAIN_CONTROLS =
            BlockFamily.register("controls",new com.cogwork.cogworkbridge.content.trains.track.TrainControlsBlock());

    public static final com.cogwork.cogworkbridge.content.schematics.SchematicMachineBlock SCHEMATIC_TABLE =
            BlockFamily.register("schematic_table", new com.cogwork.cogworkbridge.content.schematics.SchematicMachineBlock(false));
    public static final com.cogwork.cogworkbridge.content.schematics.SchematicMachineBlock SCHEMATICANNON =
            BlockFamily.register("schematicannon", new com.cogwork.cogworkbridge.content.schematics.SchematicMachineBlock(true));
    public static final RenderPartBlock SCHEMATICANNON_PIPE = BlockFamily.register("render_schematicannon_pipe", new RenderPartBlock(), null);
    public static final RenderPartBlock SCHEMATICANNON_CONNECTOR = BlockFamily.register("render_schematicannon_connector", new RenderPartBlock(), null);
    public static final com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelBlock FACTORY_GAUGE =
            BlockFamily.register("factory_gauge", new com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelBlock(),
                    com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelItem::new);

    public static final com.cogwork.cogworkbridge.content.logistics.port.PackagePortBlock PACKAGE_FROGPORT =
            BlockFamily.register("package_frogport", new com.cogwork.cogworkbridge.content.logistics.port.PackagePortBlock(true));
    public static final String[] PACKAGE_COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    public static final com.cogwork.cogworkbridge.content.decoration.SeatBlock[] SEATS = createSeats();
    public static final com.cogwork.cogworkbridge.content.equipment.ToolboxBlock[] TOOLBOXES = createToolboxes();
    public static final RenderPartBlock[] TOOLBOX_LIDS = createToolboxLids();
    public static final RenderPartBlock TOOLBOX_DRAWER = BlockFamily.register("render_toolbox_drawer", new RenderPartBlock(), null);
    public static final com.cogwork.cogworkbridge.content.equipment.BellBlock PECULIAR_BELL = BlockFamily.register("peculiar_bell", new com.cogwork.cogworkbridge.content.equipment.BellBlock(false));
    public static final com.cogwork.cogworkbridge.content.equipment.BellBlock HAUNTED_BELL = BlockFamily.register("haunted_bell", new com.cogwork.cogworkbridge.content.equipment.BellBlock(true));
    public static final RenderPartBlock PECULIAR_BELL_BODY = BlockFamily.register("render_peculiar_bell", new RenderPartBlock(), null);
    public static final RenderPartBlock HAUNTED_BELL_BODY = BlockFamily.register("render_haunted_bell", new RenderPartBlock(), null);
    public static final RenderPartBlock BLUEPRINT_MODEL = BlockFamily.register("render_blueprint", new RenderPartBlock(), null);
    public static final RenderPartBlock BLUEPRINT_MEDIUM = BlockFamily.register("render_blueprint_medium", new RenderPartBlock(), null);
    public static final RenderPartBlock BLUEPRINT_LARGE = BlockFamily.register("render_blueprint_large", new RenderPartBlock(), null);
    public static final com.cogwork.cogworkbridge.content.equipment.BacktankBlock COPPER_BACKTANK = BlockFamily.register("copper_backtank", new com.cogwork.cogworkbridge.content.equipment.BacktankBlock(false), null);
    public static final com.cogwork.cogworkbridge.content.equipment.BacktankBlock NETHERITE_BACKTANK = BlockFamily.register("netherite_backtank", new com.cogwork.cogworkbridge.content.equipment.BacktankBlock(true), null);
    public static final RenderPartBlock COPPER_BACKTANK_SHAFT = BlockFamily.register("render_copper_backtank_shaft", new RenderPartBlock(), null);
    public static final RenderPartBlock COPPER_BACKTANK_COGS = BlockFamily.register("render_copper_backtank_cogs", new RenderPartBlock(), null);
    public static final RenderPartBlock NETHERITE_BACKTANK_SHAFT = BlockFamily.register("render_netherite_backtank_shaft", new RenderPartBlock(), null);
    public static final RenderPartBlock NETHERITE_BACKTANK_COGS = BlockFamily.register("render_netherite_backtank_cogs", new RenderPartBlock(), null);
    public static final com.cogwork.cogworkbridge.content.decoration.MetalScaffoldingBlock ANDESITE_SCAFFOLDING = BlockFamily.register("andesite_scaffolding", new com.cogwork.cogworkbridge.content.decoration.MetalScaffoldingBlock(), com.cogwork.cogworkbridge.content.decoration.MetalScaffoldingItem::new);
    public static final com.cogwork.cogworkbridge.content.decoration.MetalScaffoldingBlock BRASS_SCAFFOLDING = BlockFamily.register("brass_scaffolding", new com.cogwork.cogworkbridge.content.decoration.MetalScaffoldingBlock(), com.cogwork.cogworkbridge.content.decoration.MetalScaffoldingItem::new);
    public static final com.cogwork.cogworkbridge.content.decoration.MetalScaffoldingBlock COPPER_SCAFFOLDING = BlockFamily.register("copper_scaffolding", new com.cogwork.cogworkbridge.content.decoration.MetalScaffoldingBlock(), com.cogwork.cogworkbridge.content.decoration.MetalScaffoldingItem::new);
    private static com.cogwork.cogworkbridge.content.decoration.SeatBlock[] createSeats() {
        var seats = new com.cogwork.cogworkbridge.content.decoration.SeatBlock[PACKAGE_COLORS.length];
        for (int i = 0; i < seats.length; i++) seats[i] = BlockFamily.register(PACKAGE_COLORS[i] + "_seat", new com.cogwork.cogworkbridge.content.decoration.SeatBlock(i));
        return seats;
    }
    private static com.cogwork.cogworkbridge.content.equipment.ToolboxBlock[] createToolboxes() {
        var boxes = new com.cogwork.cogworkbridge.content.equipment.ToolboxBlock[PACKAGE_COLORS.length];
        for (int i = 0; i < boxes.length; i++) boxes[i] = BlockFamily.register(PACKAGE_COLORS[i] + "_toolbox", new com.cogwork.cogworkbridge.content.equipment.ToolboxBlock(i));
        return boxes;
    }
    private static RenderPartBlock[] createToolboxLids() {
        RenderPartBlock[] lids = new RenderPartBlock[PACKAGE_COLORS.length];
        for (int i = 0; i < lids.length; i++) lids[i] = BlockFamily.register("render_toolbox_lid_" + PACKAGE_COLORS[i], new RenderPartBlock(), null);
        return lids;
    }
    public static final com.cogwork.cogworkbridge.content.logistics.port.PackagePortBlock[] POSTBOXES = createPostboxes();
    public static final com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock[] TABLE_CLOTHS = createTableCloths();
    public static final com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock ANDESITE_TABLE_CLOTH =
            BlockFamily.register("andesite_table_cloth", new com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock());
    public static final com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock BRASS_TABLE_CLOTH =
            BlockFamily.register("brass_table_cloth", new com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock());
    public static final com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock COPPER_TABLE_CLOTH =
            BlockFamily.register("copper_table_cloth", new com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock());
    private static com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock[] createTableCloths() {
        var result = new com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock[PACKAGE_COLORS.length];
        for (int i = 0; i < result.length; i++) result[i] = BlockFamily.register(PACKAGE_COLORS[i] + "_table_cloth", new com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock());
        return result;
    }
    private static com.cogwork.cogworkbridge.content.logistics.port.PackagePortBlock[] createPostboxes() {
        var result = new com.cogwork.cogworkbridge.content.logistics.port.PackagePortBlock[PACKAGE_COLORS.length];
        for (int i = 0; i < result.length; i++) result[i] = BlockFamily.register(PACKAGE_COLORS[i] + "_postbox", new com.cogwork.cogworkbridge.content.logistics.port.PackagePortBlock(false));
        return result;
    }

    public static final com.cogwork.cogworkbridge.content.logistics.packager.RedstoneRequesterBlock REDSTONE_REQUESTER =
            BlockFamily.register("redstone_requester", new com.cogwork.cogworkbridge.content.logistics.packager.RedstoneRequesterBlock(),
                    com.cogwork.cogworkbridge.content.logistics.packager.RedstoneRequesterItem::new);
    public static final com.cogwork.cogworkbridge.content.logistics.packager.RepackagerBlock REPACKAGER =
            BlockFamily.register("repackager", new com.cogwork.cogworkbridge.content.logistics.packager.RepackagerBlock());

    public static final com.cogwork.cogworkbridge.content.logistics.packager.StockTickerBlock STOCK_TICKER =
            BlockFamily.register("stock_ticker", new com.cogwork.cogworkbridge.content.logistics.packager.StockTickerBlock(),
                    com.cogwork.cogworkbridge.content.logistics.packager.StockLinkItem::new);
    public static final com.cogwork.cogworkbridge.content.logistics.packager.StockLinkBlock STOCK_LINK =
            BlockFamily.register("stock_link", new com.cogwork.cogworkbridge.content.logistics.packager.StockLinkBlock(),
                    com.cogwork.cogworkbridge.content.logistics.packager.StockLinkItem::new);
    public static final com.cogwork.cogworkbridge.content.redstone.DetectorBlock CONTENT_OBSERVER =
            BlockFamily.register("content_observer", new com.cogwork.cogworkbridge.content.redstone.DetectorBlock(false));
    public static final com.cogwork.cogworkbridge.content.redstone.DetectorBlock STOCKPILE_SWITCH =
            BlockFamily.register("stockpile_switch", new com.cogwork.cogworkbridge.content.redstone.DetectorBlock(true));
    public static final com.cogwork.cogworkbridge.content.redstone.PlacardBlock PLACARD =
            BlockFamily.register("placard", new com.cogwork.cogworkbridge.content.redstone.PlacardBlock());
    public static final com.cogwork.cogworkbridge.content.logistics.packager.PackagerBlock PACKAGER =
            BlockFamily.register("packager", new com.cogwork.cogworkbridge.content.logistics.packager.PackagerBlock());
    public static final com.cogwork.cogworkbridge.content.redstone.PulseDiodeBlock PULSE_EXTENDER =
            BlockFamily.register("pulse_extender", new com.cogwork.cogworkbridge.content.redstone.PulseDiodeBlock(com.cogwork.cogworkbridge.content.redstone.PulseDiodeBlock.Kind.EXTENDER));
    public static final com.cogwork.cogworkbridge.content.redstone.PulseDiodeBlock PULSE_REPEATER =
            BlockFamily.register("pulse_repeater", new com.cogwork.cogworkbridge.content.redstone.PulseDiodeBlock(com.cogwork.cogworkbridge.content.redstone.PulseDiodeBlock.Kind.REPEATER));
    public static final com.cogwork.cogworkbridge.content.redstone.PulseDiodeBlock PULSE_TIMER =
            BlockFamily.register("pulse_timer", new com.cogwork.cogworkbridge.content.redstone.PulseDiodeBlock(com.cogwork.cogworkbridge.content.redstone.PulseDiodeBlock.Kind.TIMER));
    public static final com.cogwork.cogworkbridge.content.redstone.LatchBlock POWERED_LATCH =
            BlockFamily.register("powered_latch", new com.cogwork.cogworkbridge.content.redstone.LatchBlock(false));
    public static final com.cogwork.cogworkbridge.content.redstone.LatchBlock POWERED_TOGGLE_LATCH =
            BlockFamily.register("powered_toggle_latch", new com.cogwork.cogworkbridge.content.redstone.LatchBlock(true));
    public static final com.cogwork.cogworkbridge.content.redstone.AnalogLeverBlock ANALOG_LEVER =
            BlockFamily.register("analog_lever", new com.cogwork.cogworkbridge.content.redstone.AnalogLeverBlock());
    public static final com.cogwork.cogworkbridge.content.redstone.DeskBellBlock DESK_BELL =
            BlockFamily.register("desk_bell", new com.cogwork.cogworkbridge.content.redstone.DeskBellBlock());
    public static final com.cogwork.cogworkbridge.content.redstone.RoseQuartzLampBlock ROSE_QUARTZ_LAMP =
            BlockFamily.register("rose_quartz_lamp", new com.cogwork.cogworkbridge.content.redstone.RoseQuartzLampBlock());
    public static final com.cogwork.cogworkbridge.content.redstone.RedstoneLinkBlock REDSTONE_LINK =
            BlockFamily.register("redstone_link", new com.cogwork.cogworkbridge.content.redstone.RedstoneLinkBlock());
    public static final com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardBlock CLIPBOARD =
            BlockFamily.register("clipboard", new com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardBlock(), com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardItem::new);
    public static final com.cogwork.cogworkbridge.content.display.DisplayLinkBlock DISPLAY_LINK =
            BlockFamily.register("display_link", new com.cogwork.cogworkbridge.content.display.DisplayLinkBlock(), com.cogwork.cogworkbridge.content.display.DisplayLinkItem::new);
    public static final com.cogwork.cogworkbridge.content.display.DisplayBoardBlock DISPLAY_BOARD =
            BlockFamily.register("display_board", new com.cogwork.cogworkbridge.content.display.DisplayBoardBlock());
    public static final com.cogwork.cogworkbridge.content.display.NixieTubeBlock NIXIE_TUBE =
            BlockFamily.register("nixie_tube", new com.cogwork.cogworkbridge.content.display.NixieTubeBlock());
    public static final com.cogwork.cogworkbridge.content.kinetics.gauge.GaugeBlock SPEEDOMETER =
            BlockFamily.register("speedometer", new com.cogwork.cogworkbridge.content.kinetics.gauge.GaugeBlock(false));
    public static final com.cogwork.cogworkbridge.content.kinetics.gauge.GaugeBlock STRESSOMETER =
            BlockFamily.register("stressometer", new com.cogwork.cogworkbridge.content.kinetics.gauge.GaugeBlock(true));
    public static final RenderPartBlock GAUGE_HEAD_SPEED = BlockFamily.register("render_gauge_head_speed", new RenderPartBlock(), null);
    public static final RenderPartBlock GAUGE_HEAD_STRESS = BlockFamily.register("render_gauge_head_stress", new RenderPartBlock(), null);
    public static final RenderPartBlock GAUGE_DIAL = BlockFamily.register("render_gauge_dial", new RenderPartBlock(), null);
    public static final HandCrankBlock HAND_CRANK = BlockFamily.register("hand_crank", new HandCrankBlock());
    public static final ValveHandleBlock COPPER_VALVE_HANDLE = BlockFamily.register("copper_valve_handle", new ValveHandleBlock());
    public static final ValveHandleBlock[] COLOR_VALVE_HANDLES = registerValveHandles();
    public static final RenderPartBlock[] COLOR_VALVE_MODELS = registerValveModels();
    public static final RenderPartBlock HAND_CRANK_BASE = BlockFamily.register("render_hand_crank_base", new RenderPartBlock(), null);
    public static final RenderPartBlock HAND_CRANK_HANDLE = BlockFamily.register("render_hand_crank_handle", new RenderPartBlock(), null);
    public static final RenderPartBlock VALVE_HANDLE_MODEL = BlockFamily.register("render_valve_handle", new RenderPartBlock(), null);
    public static final com.cogwork.cogworkbridge.content.kinetics.waterwheel.LargeWaterWheelBlock LARGE_WATER_WHEEL =
            BlockFamily.register("large_water_wheel", new com.cogwork.cogworkbridge.content.kinetics.waterwheel.LargeWaterWheelBlock(), com.cogwork.cogworkbridge.content.kinetics.waterwheel.LargeWaterWheelItem::new);
    public static final com.cogwork.cogworkbridge.content.kinetics.waterwheel.WaterWheelBlock WATER_WHEEL =
            BlockFamily.register("water_wheel", new com.cogwork.cogworkbridge.content.kinetics.waterwheel.WaterWheelBlock());
    public static final com.cogwork.cogworkbridge.content.kinetics.waterwheel.WaterWheelStructuralBlock WATER_WHEEL_STRUCTURAL =
            BlockFamily.register("water_wheel_structural", new com.cogwork.cogworkbridge.content.kinetics.waterwheel.WaterWheelStructuralBlock(), null);
    public static final com.cogwork.cogworkbridge.content.contraptions.bearing.WindmillBearingBlock WINDMILL_BEARING =
            BlockFamily.register("windmill_bearing", new com.cogwork.cogworkbridge.content.contraptions.bearing.WindmillBearingBlock());
    public static final com.cogwork.cogworkbridge.content.contraptions.bearing.SailBlock SAIL_FRAME =
            BlockFamily.register("sail_frame", new com.cogwork.cogworkbridge.content.contraptions.bearing.SailBlock(true));
    public static final com.cogwork.cogworkbridge.content.contraptions.bearing.SailBlock[] SAILS = registerSails();
    public static final ChassisBlock LINEAR_CHASSIS = BlockFamily.register("linear_chassis", new ChassisBlock(false));
    public static final ChassisBlock SECONDARY_LINEAR_CHASSIS = BlockFamily.register("secondary_linear_chassis", new ChassisBlock(false));
    public static final ChassisBlock RADIAL_CHASSIS = BlockFamily.register("radial_chassis", new ChassisBlock(true));
    public static final StickerBlock STICKER = BlockFamily.register("sticker", new StickerBlock());
    public static final com.cogwork.cogworkbridge.content.contraptions.controls.ContraptionControlsBlock CONTRAPTION_CONTROLS =
            BlockFamily.register("contraption_controls", new com.cogwork.cogworkbridge.content.contraptions.controls.ContraptionControlsBlock());
    public static final com.cogwork.cogworkbridge.content.contraptions.elevator.RedstoneContactBlock REDSTONE_CONTACT =
            BlockFamily.register("redstone_contact", new com.cogwork.cogworkbridge.content.contraptions.elevator.RedstoneContactBlock(false));
    public static final com.cogwork.cogworkbridge.content.contraptions.elevator.RedstoneContactBlock ELEVATOR_CONTACT =
            BlockFamily.register("elevator_contact", new com.cogwork.cogworkbridge.content.contraptions.elevator.RedstoneContactBlock(true));
    public static final com.cogwork.cogworkbridge.content.contraptions.elevator.ElevatorPulleyBlock ELEVATOR_PULLEY =
            BlockFamily.register("elevator_pulley", new com.cogwork.cogworkbridge.content.contraptions.elevator.ElevatorPulleyBlock());
    public static final com.cogwork.cogworkbridge.content.contraptions.pulley.RopePulleyBlock ROPE_PULLEY =
            BlockFamily.register("rope_pulley", new com.cogwork.cogworkbridge.content.contraptions.pulley.RopePulleyBlock());
    public static final com.cogwork.cogworkbridge.content.contraptions.pulley.RopeSegmentBlock ROPE =
            BlockFamily.register("rope", new com.cogwork.cogworkbridge.content.contraptions.pulley.RopeSegmentBlock(false), null);
    public static final com.cogwork.cogworkbridge.content.contraptions.pulley.RopeSegmentBlock PULLEY_MAGNET =
            BlockFamily.register("pulley_magnet", new com.cogwork.cogworkbridge.content.contraptions.pulley.RopeSegmentBlock(true), null);
    public static final com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock MECHANICAL_PISTON =
            BlockFamily.register("mechanical_piston", new com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock(false));
    public static final com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock STICKY_MECHANICAL_PISTON =
            BlockFamily.register("sticky_mechanical_piston", new com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock(true));
    public static final com.cogwork.cogworkbridge.content.contraptions.piston.PistonPoleBlock PISTON_EXTENSION_POLE =
            BlockFamily.register("piston_extension_pole", new com.cogwork.cogworkbridge.content.contraptions.piston.PistonPoleBlock(false));
    public static final com.cogwork.cogworkbridge.content.contraptions.piston.PistonPoleBlock MECHANICAL_PISTON_HEAD =
            BlockFamily.register("mechanical_piston_head", new com.cogwork.cogworkbridge.content.contraptions.piston.PistonPoleBlock(true), null);
    public static final com.cogwork.cogworkbridge.content.contraptions.piston.PistonPoleBlock STICKY_PISTON_HEAD =
            BlockFamily.register("sticky_piston_head", new com.cogwork.cogworkbridge.content.contraptions.piston.PistonPoleBlock(true), null);
    public static final com.cogwork.cogworkbridge.content.contraptions.bearing.ClockworkBearingBlock CLOCKWORK_BEARING =
            BlockFamily.register("clockwork_bearing", new com.cogwork.cogworkbridge.content.contraptions.bearing.ClockworkBearingBlock());
    public static final com.cogwork.cogworkbridge.content.contraptions.gantry.GantryShaftBlock GANTRY_SHAFT =
            BlockFamily.register("gantry_shaft", new com.cogwork.cogworkbridge.content.contraptions.gantry.GantryShaftBlock());
    public static final com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageBlock GANTRY_CARRIAGE =
            BlockFamily.register("gantry_carriage", new com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageBlock());
    public static final com.cogwork.cogworkbridge.content.contraptions.mounted.ControllerRailBlock CONTROLLER_RAIL =
            BlockFamily.register("controller_rail", new com.cogwork.cogworkbridge.content.contraptions.mounted.ControllerRailBlock());
    public static final com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock CART_ASSEMBLER =
            BlockFamily.register("cart_assembler", new com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock(),
                    com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerItem::new);
    public static final com.cogwork.cogworkbridge.content.contraptions.mounted.MinecartAnchorBlock MINECART_ANCHOR =
            BlockFamily.register("minecart_anchor", new com.cogwork.cogworkbridge.content.contraptions.mounted.MinecartAnchorBlock(), null);
    public static final com.cogwork.cogworkbridge.content.kinetics.steam.SteamEngineBlock STEAM_ENGINE =
            BlockFamily.register("steam_engine", new com.cogwork.cogworkbridge.content.kinetics.steam.SteamEngineBlock());
    public static final com.cogwork.cogworkbridge.content.kinetics.steam.PoweredShaftBlock POWERED_SHAFT =
            BlockFamily.register("powered_shaft", new com.cogwork.cogworkbridge.content.kinetics.steam.PoweredShaftBlock(), null);
    public static final RenderPartBlock STEAM_ENGINE_PISTON = BlockFamily.register("render_steam_engine_piston", new RenderPartBlock(), null);
    public static final RenderPartBlock STEAM_ENGINE_LINKAGE = BlockFamily.register("render_steam_engine_linkage", new RenderPartBlock(), null);
    public static final RenderPartBlock STEAM_ENGINE_CONNECTOR = BlockFamily.register("render_steam_engine_connector", new RenderPartBlock(), null);
    public static final RenderPartBlock BOILER_GAUGE = BlockFamily.register("render_boiler_gauge", new RenderPartBlock(), null);
    public static final RenderPartBlock BOILER_GAUGE_DIAL = BlockFamily.register("render_boiler_gauge_dial", new RenderPartBlock(), null);
    public static final RenderPartBlock WINDMILL_BEARING_TOP = BlockFamily.register("render_windmill_bearing_top", new RenderPartBlock(), null);
    public static final RenderPartBlock[] WATER_WHEEL_MODELS = registerWaterWheelModels();
    public static final RenderPartBlock[] SMALL_WATER_WHEEL_MODELS = registerSmallWaterWheelModels();

    private static ValveHandleBlock[] registerValveHandles() {
        String[] names = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        ValveHandleBlock[] handles = new ValveHandleBlock[names.length];
        for (int i = 0; i < names.length; i++) handles[i] = BlockFamily.register(names[i] + "_valve_handle", new ValveHandleBlock(i));
        return handles;
    }
    private static RenderPartBlock[] registerValveModels() {
        String[] names = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        RenderPartBlock[] models = new RenderPartBlock[names.length];
        for (int i = 0; i < names.length; i++) models[i] = BlockFamily.register("render_" + names[i] + "_valve_handle", new RenderPartBlock(), null);
        return models;
    }

    private static RenderPartBlock[] registerWaterWheelModels() {
        String[] woods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "crimson", "warped"};
        RenderPartBlock[] models = new RenderPartBlock[woods.length];
        for (int i = 0; i < woods.length; i++) models[i] = BlockFamily.register("render_large_water_wheel_" + woods[i], new RenderPartBlock(), null);
        return models;
    }

    private static RenderPartBlock[] registerSmallWaterWheelModels() {
        String[] woods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "crimson", "warped"};
        RenderPartBlock[] models = new RenderPartBlock[woods.length];
        for (int i = 0; i < woods.length; i++) models[i] = BlockFamily.register("render_water_wheel_" + woods[i], new RenderPartBlock(), null);
        return models;
    }

    private static com.cogwork.cogworkbridge.content.contraptions.bearing.SailBlock[] registerSails() {
        String[] names = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        var sails = new com.cogwork.cogworkbridge.content.contraptions.bearing.SailBlock[names.length];
        for (int i = 0; i < names.length; i++) sails[i] = BlockFamily.register(names[i] + "_sail", new com.cogwork.cogworkbridge.content.contraptions.bearing.SailBlock(false));
        return sails;
    }

    public static final ShaftBlock SHAFT = BlockFamily.register("shaft", new ShaftBlock(), KineticPlacementItemBlock::new);
    public static final EncasedShaftBlock SHAFT_ENCASED_ANDESITE =
            BlockFamily.register("shaft_encased_andesite", new EncasedShaftBlock(), KineticPlacementItemBlock::new);
    public static final EncasedShaftBlock SHAFT_ENCASED_BRASS =
            BlockFamily.register("shaft_encased_brass", new EncasedShaftBlock(), KineticPlacementItemBlock::new);
    public static final CogwheelBlock COGWHEEL = BlockFamily.register("cogwheel", new CogwheelBlock(false), KineticPlacementItemBlock::new);
    public static final CogwheelBlock LARGE_COGWHEEL = BlockFamily.register("large_cogwheel", new CogwheelBlock(true), KineticPlacementItemBlock::new);
    public static final EncasedCogwheelBlock COGWHEEL_ENCASED_ANDESITE =
            BlockFamily.register("cogwheel_encased_andesite", new EncasedCogwheelBlock(false), KineticPlacementItemBlock::new);
    public static final EncasedCogwheelBlock COGWHEEL_ENCASED_BRASS =
            BlockFamily.register("cogwheel_encased_brass", new EncasedCogwheelBlock(false), KineticPlacementItemBlock::new);
    public static final EncasedCogwheelBlock LARGE_COGWHEEL_ENCASED_ANDESITE =
            BlockFamily.register("large_cogwheel_encased_andesite", new EncasedCogwheelBlock(true), KineticPlacementItemBlock::new);
    public static final EncasedCogwheelBlock LARGE_COGWHEEL_ENCASED_BRASS =
            BlockFamily.register("large_cogwheel_encased_brass", new EncasedCogwheelBlock(true), KineticPlacementItemBlock::new);
    public static final Block BRASS_BLOCK = BlockFamily.register("brass_block", new Block(Material.IRON).setHardness(5.0F).setResistance(6.0F));
    public static final Block ZINC_BLOCK = BlockFamily.register("zinc_block", new Block(Material.IRON).setHardness(5.0F).setResistance(6.0F));
    public static final Block CARDBOARD_BLOCK = BlockFamily.register("cardboard_block", new com.cogwork.cogworkbridge.content.decoration.palette.CardboardBlock(false),
            block -> new com.cogwork.cogworkbridge.content.processing.FuelItem.BlockItem(block, 4000));
    public static final com.cogwork.cogworkbridge.content.redstone.LecternControllerBlock LECTERN_CONTROLLER = BlockFamily.register("lectern_controller", new com.cogwork.cogworkbridge.content.redstone.LecternControllerBlock(), null);
    public static final com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleBlock STEAM_WHISTLE = BlockFamily.register("steam_whistle", new com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleBlock());
    public static final com.cogwork.cogworkbridge.content.fluids.whistle.WhistleExtensionBlock STEAM_WHISTLE_EXTENSION = BlockFamily.register("steam_whistle_extension", new com.cogwork.cogworkbridge.content.fluids.whistle.WhistleExtensionBlock(), null);
    public static final RenderPartBlock[] WHISTLE_BASE = whistleParts("base", 12);
    public static final RenderPartBlock[] WHISTLE_MOUTH = whistleParts("mouth", 3);
    public static final RenderPartBlock[] WHISTLE_EXTENSION_PART = whistleParts("extension", 9);
    private static RenderPartBlock[] whistleParts(String kind, int count) { RenderPartBlock[] parts=new RenderPartBlock[count]; for(int i=0;i<count;i++) parts[i]=BlockFamily.register("render_whistle_"+kind+"_"+i,new RenderPartBlock(),null);return parts; }
    public static final com.cogwork.cogworkbridge.content.kinetics.clock.CuckooClockBlock CUCKOO_CLOCK = BlockFamily.register("cuckoo_clock", new com.cogwork.cogworkbridge.content.kinetics.clock.CuckooClockBlock(false));
    public static final com.cogwork.cogworkbridge.content.kinetics.clock.CuckooClockBlock MYSTERIOUS_CUCKOO_CLOCK = BlockFamily.register("mysterious_cuckoo_clock", new com.cogwork.cogworkbridge.content.kinetics.clock.CuckooClockBlock(true));
    public static final RenderPartBlock CUCKOO_HOUR = BlockFamily.register("render_cuckoo_hour", new RenderPartBlock(), null);
    public static final RenderPartBlock CUCKOO_MINUTE = BlockFamily.register("render_cuckoo_minute", new RenderPartBlock(), null);
    public static final RenderPartBlock CUCKOO_LEFT = BlockFamily.register("render_cuckoo_left", new RenderPartBlock(), null);
    public static final RenderPartBlock CUCKOO_RIGHT = BlockFamily.register("render_cuckoo_right", new RenderPartBlock(), null);
    public static final RenderPartBlock CUCKOO_PIG = BlockFamily.register("render_cuckoo_pig", new RenderPartBlock(), null);
    public static final RenderPartBlock CUCKOO_CREEPER = BlockFamily.register("render_cuckoo_creeper", new RenderPartBlock(), null);
    public static final com.cogwork.cogworkbridge.content.kinetics.turntable.TurntableBlock TURNTABLE = BlockFamily.register("turntable", new com.cogwork.cogworkbridge.content.kinetics.turntable.TurntableBlock());
    public static final com.cogwork.cogworkbridge.content.kinetics.flywheel.FlywheelBlock FLYWHEEL = BlockFamily.register("flywheel", new com.cogwork.cogworkbridge.content.kinetics.flywheel.FlywheelBlock());
    public static final Block DEEPSLATE_ZINC_ORE = BlockFamily.register("deepslate_zinc_ore", new DeepZincOreBlock());
    public static final Block ZINC_ORE = BlockFamily.register("zinc_ore",
            new com.cogwork.cogworkbridge.registry.ZincOreBlock());
    public static final Block ANDESITE_CASING = BlockFamily.register("andesite_casing",
            new com.cogwork.cogworkbridge.content.decoration.CasingBlock());
    public static final Block BRASS_CASING = BlockFamily.register("brass_casing",
            new com.cogwork.cogworkbridge.content.decoration.CasingBlock());
    public static final Block COPPER_CASING = BlockFamily.register("copper_casing",
            new com.cogwork.cogworkbridge.content.decoration.CasingBlock());
    public static final GearboxBlock GEARBOX = BlockFamily.register("gearbox", new GearboxBlock(false));
    public static final GearboxBlock VERTICAL_GEARBOX =
            BlockFamily.register("vertical_gearbox", new GearboxBlock(true));
    public static final SplitShaftBlock CLUTCH = BlockFamily.register("clutch", new SplitShaftBlock(true));
    public static final SplitShaftBlock GEARSHIFT = BlockFamily.register("gearshift", new SplitShaftBlock(false));
    public static final SequencedGearshiftBlock SEQUENCED_GEARSHIFT =
            BlockFamily.register("sequenced_gearshift", new SequencedGearshiftBlock());
    public static final ChainDriveBlock ENCASED_CHAIN_DRIVE =
            BlockFamily.register("encased_chain_drive", new ChainDriveBlock(false));
    public static final ChainDriveBlock ADJUSTABLE_CHAIN_GEARSHIFT =
            BlockFamily.register("adjustable_chain_gearshift", new ChainDriveBlock(true));
    public static final com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorBlock CHAIN_CONVEYOR =
            BlockFamily.register("chain_conveyor", new com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorBlock());
    public static final SpeedControllerBlock ROTATION_SPEED_CONTROLLER =
            BlockFamily.register("rotation_speed_controller", new SpeedControllerBlock());
    public static final CreativeMotorBlock CREATIVE_MOTOR = BlockFamily.register("creative_motor", new CreativeMotorBlock());
    public static final MechanicalBearingBlock MECHANICAL_BEARING =
            BlockFamily.register("mechanical_bearing", new MechanicalBearingBlock());
    public static final MechanicalDrillBlock MECHANICAL_DRILL =
            BlockFamily.register("mechanical_drill", new MechanicalDrillBlock());
    public static final MechanicalSawBlock MECHANICAL_SAW =
            BlockFamily.register("mechanical_saw", new MechanicalSawBlock());
    public static final DeployerBlock DEPLOYER =
            BlockFamily.register("deployer", new DeployerBlock(), AssemblyOperatorItemBlock::new);
    public static final MechanicalPloughBlock MECHANICAL_PLOUGH =
            BlockFamily.register("mechanical_plough", new MechanicalPloughBlock());
    public static final MechanicalHarvesterBlock MECHANICAL_HARVESTER =
            BlockFamily.register("mechanical_harvester", new MechanicalHarvesterBlock());
    public static final MechanicalRollerBlock MECHANICAL_ROLLER =
            BlockFamily.register("mechanical_roller", new MechanicalRollerBlock());
    public static final EncasedFanBlock ENCASED_FAN = BlockFamily.register("encased_fan", new EncasedFanBlock());
    public static final NozzleBlock NOZZLE = BlockFamily.register("nozzle", new NozzleBlock());
    public static final BasinBlock BASIN = BlockFamily.register("basin", new BasinBlock());
    public static final FluidPipeBlock FLUID_PIPE = BlockFamily.register("fluid_pipe", new FluidPipeBlock());
    public static final GlassFluidPipeBlock GLASS_FLUID_PIPE =
            BlockFamily.register("glass_fluid_pipe", new GlassFluidPipeBlock(), null);
    public static final EncasedFluidPipeBlock ENCASED_FLUID_PIPE =
            BlockFamily.register("encased_fluid_pipe", new EncasedFluidPipeBlock(), null);
    public static final MechanicalPumpBlock MECHANICAL_PUMP =
            BlockFamily.register("mechanical_pump", new MechanicalPumpBlock());
    public static final SmartFluidPipeBlock SMART_FLUID_PIPE =
            BlockFamily.register("smart_fluid_pipe", new SmartFluidPipeBlock());
    public static final FluidValveBlock FLUID_VALVE =
            BlockFamily.register("fluid_valve", new FluidValveBlock());
    public static final FluidTankBlock FLUID_TANK = BlockFamily.register("fluid_tank", new FluidTankBlock(), FluidTankItem::new);
    public static final CreativeFluidTankBlock CREATIVE_FLUID_TANK =
            BlockFamily.register("creative_fluid_tank", new CreativeFluidTankBlock(), FluidTankItem::new);
    public static final FluidSpoutBlock SPOUT = BlockFamily.register("spout", new FluidSpoutBlock(), AssemblyOperatorItemBlock::new);
    public static final HosePulleyBlock HOSE_PULLEY = BlockFamily.register("hose_pulley", new HosePulleyBlock());
    public static final PortableFluidInterfaceBlock PORTABLE_FLUID_INTERFACE =
            BlockFamily.register("portable_fluid_interface", new PortableFluidInterfaceBlock());
    public static final ItemDrainBlock ITEM_DRAIN = BlockFamily.register("item_drain", new ItemDrainBlock());
    public static final MechanicalArmBlock MECHANICAL_ARM = BlockFamily.register("mechanical_arm", new MechanicalArmBlock(), MechanicalArmItem::new);
    public static final RenderPartBlock FUNNEL_FLAP = BlockFamily.register("render_funnel_flap", new RenderPartBlock(), null);
    public static final RenderPartBlock BELT_FUNNEL_FLAP = BlockFamily.register("render_belt_funnel_flap", new RenderPartBlock(), null);
    public static final RenderPartBlock BELT_TUNNEL_FLAP = BlockFamily.register("render_belt_tunnel_flap", new RenderPartBlock(), null);
    public static final RenderPartBlock ARM_COG = BlockFamily.register("render_arm_cog", new RenderPartBlock(), null);
    public static final RenderPartBlock ARM_BASE = BlockFamily.register("render_arm_base", new RenderPartBlock(), null);
    public static final RenderPartBlock ARM_LOWER = BlockFamily.register("render_arm_lower", new RenderPartBlock(), null);
    public static final RenderPartBlock ARM_UPPER = BlockFamily.register("render_arm_upper", new RenderPartBlock(), null);
    public static final RenderPartBlock ARM_CLAW = BlockFamily.register("render_arm_claw", new RenderPartBlock(), null);
    public static final RenderPartBlock ARM_GRIP_LOWER = BlockFamily.register("render_arm_grip_lower", new RenderPartBlock(), null);
    public static final RenderPartBlock ARM_GRIP_UPPER = BlockFamily.register("render_arm_grip_upper", new RenderPartBlock(), null);
    public static final BeltTunnelBlock ANDESITE_TUNNEL = BlockFamily.register("andesite_tunnel", new BeltTunnelBlock(false));
    public static final BeltTunnelBlock BRASS_TUNNEL = BlockFamily.register("brass_tunnel", new BeltTunnelBlock(true));
    public static final DepotBlock DEPOT = BlockFamily.register("depot", new DepotBlock());
    public static final CreativeCrateBlock CREATIVE_CRATE = BlockFamily.register("creative_crate", new CreativeCrateBlock());
    public static final PortableStorageInterfaceBlock PORTABLE_STORAGE_INTERFACE =
            BlockFamily.register("portable_storage_interface", new PortableStorageInterfaceBlock());
    public static final ChuteBlock CHUTE = BlockFamily.register("chute", new ChuteBlock(false));
    public static final ChuteBlock SMART_CHUTE = BlockFamily.register("smart_chute", new ChuteBlock(true));
    public static final FunnelBlock ANDESITE_FUNNEL = BlockFamily.register("andesite_funnel", new FunnelBlock(false));
    public static final FunnelBlock BRASS_FUNNEL = BlockFamily.register("brass_funnel", new FunnelBlock(true));
    public static final BeltBlock BELT = BlockFamily.register("belt", new BeltBlock(), null);
    public static final RenderPartBlock BELT_PULLEY = BlockFamily.register("render_belt_pulley", new RenderPartBlock(), null);
    public static final ItemVaultBlock ITEM_VAULT = BlockFamily.register("item_vault", new ItemVaultBlock());
    public static final ItemHatchBlock ITEM_HATCH = BlockFamily.register("item_hatch", new ItemHatchBlock());
    public static final EjectorBlock WEIGHTED_EJECTOR = BlockFamily.register("weighted_ejector", new EjectorBlock(), EjectorItem::new);
    public static final RenderPartBlock EJECTOR_TOP = BlockFamily.register("render_ejector_top", new RenderPartBlock(), null);
    public static final BlazeBurnerBlock BLAZE_BURNER =
            BlockFamily.register("blaze_burner", new BlazeBurnerBlock(true));
    public static final BlazeBurnerBlock EMPTY_BLAZE_BURNER =
            BlockFamily.register("empty_blaze_burner", new BlazeBurnerBlock(false), EmptyBlazeBurnerItem::new);
    public static final MechanicalPressBlock MECHANICAL_PRESS =
            BlockFamily.register("mechanical_press", new MechanicalPressBlock(), AssemblyOperatorItemBlock::new);
    public static final MechanicalMixerBlock MECHANICAL_MIXER =
            BlockFamily.register("mechanical_mixer", new MechanicalMixerBlock(), AssemblyOperatorItemBlock::new);
    public static final MillstoneBlock MILLSTONE = BlockFamily.register("millstone", new MillstoneBlock());
    public static final MechanicalCrafterBlock MECHANICAL_CRAFTER =
            BlockFamily.register("mechanical_crafter", new MechanicalCrafterBlock(), MechanicalCrafterItem::new);
    public static final CrushingWheelBlock CRUSHING_WHEEL =
            BlockFamily.register("crushing_wheel", new CrushingWheelBlock());
    public static final CrushingWheelControllerBlock CRUSHING_WHEEL_CONTROLLER =
            BlockFamily.register("crushing_wheel_controller", new CrushingWheelControllerBlock(), null);

    /** Model-only registry entries used as cached TESR sub-parts; they have no items. */
    public static final RenderPartBlock CREATIVE_MOTOR_ROTOR =
            BlockFamily.register("render_creative_motor_rotor", new RenderPartBlock(), null);
    public static final RenderPartBlock MECHANICAL_BEARING_ASSEMBLY =
            BlockFamily.register("render_mechanical_bearing_assembly", new RenderPartBlock(), null);
    public static final RenderPartBlock MECHANICAL_BEARING_TOP =
            BlockFamily.register("render_mechanical_bearing_top", new RenderPartBlock(), null);
    public static final RenderPartBlock MECHANICAL_BEARING_SHAFT =
            BlockFamily.register("render_mechanical_bearing_shaft", new RenderPartBlock(), null);
    public static final RenderPartBlock MECHANICAL_DRILL_HEAD =
            BlockFamily.register("render_mechanical_drill_head", new RenderPartBlock(), null);
    public static final RenderPartBlock DEPLOYER_POLE =
            BlockFamily.register("render_deployer_pole", new RenderPartBlock(), null);
    public static final RenderPartBlock DEPLOYER_HAND_POINTING =
            BlockFamily.register("render_deployer_hand_pointing", new RenderPartBlock(), null);
    public static final RenderPartBlock DEPLOYER_HAND_HOLDING =
            BlockFamily.register("render_deployer_hand_holding", new RenderPartBlock(), null);
    public static final RenderPartBlock DEPLOYER_HAND_PUNCHING =
            BlockFamily.register("render_deployer_hand_punching", new RenderPartBlock(), null);
    public static final RenderPartBlock MECHANICAL_SAW_BLADE =
            BlockFamily.register("render_mechanical_saw_blade", new RenderPartBlock(), null);
    public static final RenderPartBlock MECHANICAL_SAW_BLADE_ACTIVE =
            BlockFamily.register("render_mechanical_saw_blade_active", new RenderPartBlock(), null);
    public static final RenderPartBlock MECHANICAL_SAW_BLADE_REVERSED =
            BlockFamily.register("render_mechanical_saw_blade_reversed", new RenderPartBlock(), null);
    public static final RenderPartBlock MECHANICAL_HARVESTER_BLADE =
            BlockFamily.register("render_mechanical_harvester_blade", new RenderPartBlock(), null);
    public static final RenderPartBlock MECHANICAL_ROLLER_FRAME =
            BlockFamily.register("render_mechanical_roller_frame", new RenderPartBlock(), null);
    public static final RenderPartBlock MECHANICAL_ROLLER_WHEEL =
            BlockFamily.register("render_mechanical_roller_wheel", new RenderPartBlock(), null);
    public static final RenderPartBlock ENCASED_FAN_PROPELLER =
            BlockFamily.register("render_encased_fan_propeller", new RenderPartBlock(), null);
    public static final RenderPartBlock MECHANICAL_PRESS_HEAD =
            BlockFamily.register("render_mechanical_press_head", new RenderPartBlock(), null);
    public static final RenderPartBlock MECHANICAL_PUMP_COG =
            BlockFamily.register("render_mechanical_pump_cog", new RenderPartBlock(), null);
    public static final RenderPartBlock FLUID_VALVE_POINTER =
            BlockFamily.register("render_fluid_valve_pointer", new RenderPartBlock(), null);
    public static final RenderPartBlock MECHANICAL_MIXER_POLE =
            BlockFamily.register("render_mechanical_mixer_pole", new RenderPartBlock(), null);
    public static final RenderPartBlock MECHANICAL_MIXER_HEAD =
            BlockFamily.register("render_mechanical_mixer_head", new RenderPartBlock(), null);
    public static final RenderPartBlock MILLSTONE_INNER =
            BlockFamily.register("render_millstone_inner", new RenderPartBlock(), null);
    public static final RenderPartBlock COGWHEEL_SHAFTLESS =
            BlockFamily.register("render_cogwheel_shaftless", new RenderPartBlock(), null);
    public static final RenderPartBlock LARGE_COGWHEEL_SHAFTLESS =
            BlockFamily.register("render_large_cogwheel_shaftless", new RenderPartBlock(), null);
    public static final RenderPartBlock CLUTCH_POWERED_MODEL =
            BlockFamily.register("render_clutch_powered", new RenderPartBlock(), null);
    public static final RenderPartBlock GEARSHIFT_POWERED_MODEL =
            BlockFamily.register("render_gearshift_powered", new RenderPartBlock(), null);
    public static final RenderPartBlock SPEED_CONTROLLER_BRACKET =
            BlockFamily.register("render_speed_controller_bracket", new RenderPartBlock(), null);
    private static final RenderPartBlock[][] ENCASED_COG_MODELS = createEncasedCogModels();

    private static RenderPartBlock[][] createEncasedCogModels() {
        String[] families = {"cogwheel_encased_andesite", "cogwheel_encased_brass",
                "large_cogwheel_encased_andesite", "large_cogwheel_encased_brass"};
        String[] ports = {"top", "bottom", "top_bottom"};
        RenderPartBlock[][] models = new RenderPartBlock[families.length][ports.length];
        for (int family = 0; family < families.length; family++) {
            for (int port = 0; port < ports.length; port++) {
                models[family][port] = BlockFamily.register(
                        "render_" + families[family] + "_" + ports[port], new RenderPartBlock(), null);
            }
        }
        return models;
    }

    public static RenderPartBlock encasedCogModel(int family, int mask) {
        return ENCASED_COG_MODELS[family][mask - 1];
    }

    private AllBlocks() {
    }

    /** Tile entity ids must be stable; renaming one breaks existing saves. */
    public static void registerTileEntities() {
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.contraptions.mounted.ControllerRailTileEntity.class, "cogworkbridge:controller_rail");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.schematics.SchematicTableTile.class, "cogworkbridge:schematic_table");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.schematics.SchematicannonTile.class, "cogworkbridge:schematicannon");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.decoration.palette.DoorStateTile.class, "cogworkbridge:palette_door");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.decoration.palette.CopycatTile.class, "cogworkbridge:copycat");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.equipment.BacktankTileEntity.class, "cogworkbridge:backtank");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.equipment.BellTileEntity.class, "cogworkbridge:bell");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.equipment.ToolboxTileEntity.class, "cogworkbridge:toolbox");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorTileEntity.class, "cogworkbridge:chain_conveyor");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelTileEntity.class, "cogworkbridge:factory_gauge");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.logistics.table.TableClothTileEntity.class, "cogworkbridge:table_cloth");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.logistics.port.PackagePortTileEntity.class, "cogworkbridge:package_port");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.logistics.packager.RepackagerTileEntity.class, "cogworkbridge:repackager");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.logistics.packager.RedstoneRequesterTileEntity.class, "cogworkbridge:redstone_requester");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.logistics.packager.StockTickerTileEntity.class, "cogworkbridge:stock_ticker");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.logistics.packager.StockLinkTileEntity.class, "cogworkbridge:stock_link");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity.class, "cogworkbridge:detector");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.redstone.PlacardTileEntity.class, "cogworkbridge:placard");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.logistics.packager.PackagerTileEntity.class, "cogworkbridge:packager");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.redstone.PulseDiodeTileEntity.class, "cogworkbridge:pulse_diode");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.redstone.LatchTileEntity.class, "cogworkbridge:latch");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.redstone.AnalogLeverTileEntity.class, "cogworkbridge:analog_lever");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.redstone.DeskBellTileEntity.class, "cogworkbridge:desk_bell");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.redstone.RoseQuartzLampTileEntity.class, "cogworkbridge:rose_quartz_lamp");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.redstone.RedstoneLinkTileEntity.class, "cogworkbridge:redstone_link");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardTileEntity.class, "cogworkbridge:clipboard");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.display.DisplayLinkTileEntity.class, "cogworkbridge:display_link");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.display.DisplayBoardTileEntity.class, "cogworkbridge:display_board");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.display.NixieTubeTileEntity.class, "cogworkbridge:nixie_tube");
        GameRegistry.registerTileEntity(ChassisTileEntity.class, "cogworkbridge:chassis");
        GameRegistry.registerTileEntity(StickerTileEntity.class, "cogworkbridge:sticker");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.contraptions.controls.ContraptionControlsTileEntity.class, "cogworkbridge:contraption_controls");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.contraptions.elevator.RedstoneContactTileEntity.class, "cogworkbridge:redstone_contact");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.contraptions.elevator.ElevatorPulleyTileEntity.class, "cogworkbridge:elevator_pulley");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.contraptions.pulley.RopePulleyTileEntity.class, "cogworkbridge:rope_pulley");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonTileEntity.class, "cogworkbridge:mechanical_piston");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.contraptions.bearing.ClockworkBearingTileEntity.class, "cogworkbridge:clockwork_bearing");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.contraptions.gantry.GantryShaftTileEntity.class, "cogworkbridge:gantry_shaft");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageTileEntity.class, "cogworkbridge:gantry_carriage");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerTileEntity.class, "cogworkbridge:cart_assembler");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.kinetics.gauge.GaugeTileEntity.class, "cogworkbridge:kinetic_gauge");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.redstone.LecternControllerTileEntity.class, "cogworkbridge:lectern_controller");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleTileEntity.class, "cogworkbridge:steam_whistle");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.fluids.whistle.WhistleExtensionTileEntity.class, "cogworkbridge:steam_whistle_extension");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.kinetics.clock.CuckooClockTileEntity.class, "cogworkbridge:cuckoo_clock");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.kinetics.turntable.TurntableTileEntity.class, "cogworkbridge:turntable");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.kinetics.flywheel.FlywheelTileEntity.class, "cogworkbridge:flywheel");
        GameRegistry.registerTileEntity(HandCrankTileEntity.class, "cogworkbridge:hand_crank");
        GameRegistry.registerTileEntity(ValveHandleTileEntity.class, "cogworkbridge:valve_handle");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.kinetics.waterwheel.LargeWaterWheelTileEntity.class, "cogworkbridge:large_water_wheel");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.kinetics.waterwheel.WaterWheelTileEntity.class, "cogworkbridge:water_wheel");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.contraptions.bearing.WindmillBearingTileEntity.class, "cogworkbridge:windmill_bearing");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.kinetics.steam.SteamEngineTileEntity.class, "cogworkbridge:steam_engine");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.kinetics.steam.PoweredShaftTileEntity.class, "cogworkbridge:powered_shaft");
        GameRegistry.registerTileEntity(ShaftTileEntity.class, "cogworkbridge:shaft");
        GameRegistry.registerTileEntity(EncasedShaftTileEntity.class, "cogworkbridge:encased_shaft");
        GameRegistry.registerTileEntity(EncasedCogwheelTileEntity.class, "cogworkbridge:encased_cogwheel");
        GameRegistry.registerTileEntity(CogwheelTileEntity.class, "cogworkbridge:cogwheel");
        GameRegistry.registerTileEntity(GearboxTileEntity.class, "cogworkbridge:gearbox");
        GameRegistry.registerTileEntity(SplitShaftTileEntity.class, "cogworkbridge:split_shaft");
        GameRegistry.registerTileEntity(SequencedGearshiftTileEntity.class, "cogworkbridge:sequenced_gearshift");
        GameRegistry.registerTileEntity(ChainDriveTileEntity.class, "cogworkbridge:chain_drive");
        GameRegistry.registerTileEntity(SpeedControllerTileEntity.class, "cogworkbridge:speed_controller");
        GameRegistry.registerTileEntity(CreativeMotorTileEntity.class, "cogworkbridge:creative_motor");
        GameRegistry.registerTileEntity(MechanicalBearingTileEntity.class, "cogworkbridge:mechanical_bearing");
        GameRegistry.registerTileEntity(MechanicalDrillTileEntity.class, "cogworkbridge:mechanical_drill");
        GameRegistry.registerTileEntity(DeployerTileEntity.class, "cogworkbridge:deployer");
        GameRegistry.registerTileEntity(MechanicalSawTileEntity.class, "cogworkbridge:mechanical_saw");
        GameRegistry.registerTileEntity(MechanicalPloughTileEntity.class, "cogworkbridge:mechanical_plough");
        GameRegistry.registerTileEntity(MechanicalHarvesterTileEntity.class, "cogworkbridge:mechanical_harvester");
        GameRegistry.registerTileEntity(MechanicalRollerTileEntity.class, "cogworkbridge:mechanical_roller");
        GameRegistry.registerTileEntity(EncasedFanTileEntity.class, "cogworkbridge:encased_fan");
        GameRegistry.registerTileEntity(NozzleTileEntity.class, "cogworkbridge:nozzle");
        GameRegistry.registerTileEntity(BasinTileEntity.class, "cogworkbridge:basin");
        GameRegistry.registerTileEntity(FluidPipeTileEntity.class, "cogworkbridge:fluid_pipe");
        GameRegistry.registerTileEntity(MechanicalPumpTileEntity.class, "cogworkbridge:mechanical_pump");
        GameRegistry.registerTileEntity(SmartFluidPipeTileEntity.class, "cogworkbridge:smart_fluid_pipe");
        GameRegistry.registerTileEntity(FluidValveTileEntity.class, "cogworkbridge:fluid_valve");
        GameRegistry.registerTileEntity(FluidTankTileEntity.class, "cogworkbridge:fluid_tank");
        GameRegistry.registerTileEntity(CreativeFluidTankTileEntity.class, "cogworkbridge:creative_fluid_tank");
        GameRegistry.registerTileEntity(FluidSpoutTileEntity.class, "cogworkbridge:spout");
        GameRegistry.registerTileEntity(HosePulleyTileEntity.class, "cogworkbridge:hose_pulley");
        GameRegistry.registerTileEntity(PortableFluidInterfaceTileEntity.class,
                "cogworkbridge:portable_fluid_interface");
        GameRegistry.registerTileEntity(ItemDrainTileEntity.class, "cogworkbridge:item_drain");
        GameRegistry.registerTileEntity(MechanicalArmTileEntity.class, "cogworkbridge:mechanical_arm");
        GameRegistry.registerTileEntity(BeltTunnelTileEntity.class, "cogworkbridge:andesite_tunnel");
        GameRegistry.registerTileEntity(BrassTunnelTileEntity.class, "cogworkbridge:brass_tunnel");
        GameRegistry.registerTileEntity(DepotTileEntity.class, "cogworkbridge:depot");
        GameRegistry.registerTileEntity(CreativeCrateTileEntity.class, "cogworkbridge:creative_crate");
        GameRegistry.registerTileEntity(PortableStorageInterfaceTileEntity.class, "cogworkbridge:portable_storage_interface");
        GameRegistry.registerTileEntity(ChuteTileEntity.class, "cogworkbridge:chute");
        GameRegistry.registerTileEntity(SmartChuteTileEntity.class, "cogworkbridge:smart_chute");
        GameRegistry.registerTileEntity(FunnelTileEntity.class, "cogworkbridge:andesite_funnel");
        GameRegistry.registerTileEntity(BrassFunnelTileEntity.class, "cogworkbridge:brass_funnel");
        GameRegistry.registerTileEntity(BeltTileEntity.class, "cogworkbridge:belt");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.trains.track.TrackTileEntity.class, "cogworkbridge:track");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.trains.track.FakeTrackTile.class, "cogworkbridge:fake_track");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.trains.track.RailwayTargetTile.Station.class, "cogworkbridge:track_station");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.trains.track.RailwayTargetTile.Signal.class, "cogworkbridge:track_signal");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.trains.track.RailwayTargetTile.Observer.class, "cogworkbridge:track_observer");
        GameRegistry.registerTileEntity(com.cogwork.cogworkbridge.content.trains.track.BogeyTile.class, "cogworkbridge:bogey");
        GameRegistry.registerTileEntity(ItemVaultTileEntity.class, "cogworkbridge:item_vault");
        GameRegistry.registerTileEntity(ItemHatchTileEntity.class, "cogworkbridge:item_hatch");
        GameRegistry.registerTileEntity(EjectorTileEntity.class, "cogworkbridge:weighted_ejector");
        GameRegistry.registerTileEntity(BlazeBurnerTileEntity.class, "cogworkbridge:blaze_burner");
        GameRegistry.registerTileEntity(MechanicalPressTileEntity.class, "cogworkbridge:mechanical_press");
        GameRegistry.registerTileEntity(MechanicalMixerTileEntity.class, "cogworkbridge:mechanical_mixer");
        GameRegistry.registerTileEntity(MillstoneTileEntity.class, "cogworkbridge:millstone");
        GameRegistry.registerTileEntity(MechanicalCrafterTileEntity.class, "cogworkbridge:mechanical_crafter");
        GameRegistry.registerTileEntity(CrushingWheelTileEntity.class, "cogworkbridge:crushing_wheel");
        GameRegistry.registerTileEntity(CrushingWheelControllerTileEntity.class, "cogworkbridge:crushing_wheel_controller");
    }

    /** Forces class initialisation so the static fields above register themselves. */
    public static void init() {
        BuildingPalette.init();
        Block[] ignored = {
                SHAFT, SHAFT_ENCASED_ANDESITE, SHAFT_ENCASED_BRASS, COGWHEEL, LARGE_COGWHEEL,
                LINEAR_CHASSIS, SECONDARY_LINEAR_CHASSIS, RADIAL_CHASSIS,
                STICKER, CONTRAPTION_CONTROLS, REDSTONE_CONTACT, ELEVATOR_CONTACT, ELEVATOR_PULLEY, ROPE_PULLEY, ROPE, PULLEY_MAGNET,
                MECHANICAL_PISTON, STICKY_MECHANICAL_PISTON, PISTON_EXTENSION_POLE, MECHANICAL_PISTON_HEAD, STICKY_PISTON_HEAD, CLOCKWORK_BEARING, GANTRY_SHAFT, GANTRY_CARRIAGE, CONTROLLER_RAIL, CART_ASSEMBLER, MINECART_ANCHOR,
                COGWHEEL_ENCASED_ANDESITE, COGWHEEL_ENCASED_BRASS,
                LARGE_COGWHEEL_ENCASED_ANDESITE, LARGE_COGWHEEL_ENCASED_BRASS, ANDESITE_CASING, BRASS_CASING,
                GEARBOX, VERTICAL_GEARBOX, CLUTCH, GEARSHIFT, SEQUENCED_GEARSHIFT, ENCASED_CHAIN_DRIVE, ADJUSTABLE_CHAIN_GEARSHIFT,
                ROTATION_SPEED_CONTROLLER, SPEED_CONTROLLER_BRACKET,
                CREATIVE_MOTOR, MECHANICAL_BEARING, MECHANICAL_DRILL, MECHANICAL_SAW, DEPLOYER, MECHANICAL_PLOUGH, MECHANICAL_HARVESTER,
                MECHANICAL_ROLLER, MECHANICAL_ROLLER_FRAME, MECHANICAL_ROLLER_WHEEL,
                ENCASED_FAN, NOZZLE, BASIN, FLUID_PIPE, MECHANICAL_PUMP, MECHANICAL_PUMP_COG, SMART_FLUID_PIPE, FLUID_VALVE, FLUID_VALVE_POINTER,
                FLUID_TANK, CREATIVE_FLUID_TANK, SPOUT, HOSE_PULLEY,
                PORTABLE_FLUID_INTERFACE, ITEM_DRAIN, DEPOT, GLASS_FLUID_PIPE, ENCASED_FLUID_PIPE, COPPER_CASING,
                BLAZE_BURNER, EMPTY_BLAZE_BURNER, ENCASED_FAN_PROPELLER,
                MECHANICAL_PRESS, MECHANICAL_PRESS_HEAD, MECHANICAL_MIXER, MILLSTONE, MILLSTONE_INNER,
                MECHANICAL_CRAFTER,
                CRUSHING_WHEEL, CRUSHING_WHEEL_CONTROLLER,
                CREATIVE_MOTOR_ROTOR, MECHANICAL_BEARING_ASSEMBLY, MECHANICAL_BEARING_TOP,
                MECHANICAL_BEARING_SHAFT, MECHANICAL_DRILL_HEAD,
                DEPLOYER_POLE, DEPLOYER_HAND_POINTING, DEPLOYER_HAND_HOLDING, DEPLOYER_HAND_PUNCHING,
                MECHANICAL_HARVESTER_BLADE,
                MECHANICAL_SAW_BLADE, MECHANICAL_SAW_BLADE_ACTIVE, MECHANICAL_SAW_BLADE_REVERSED,
                CLUTCH_POWERED_MODEL, GEARSHIFT_POWERED_MODEL,
                MECHANICAL_MIXER_POLE, MECHANICAL_MIXER_HEAD
        };
    }
}
