package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.client.render.CogwheelRenderer;
import com.cogwork.cogworkbridge.client.render.ChainDriveRenderer;
import com.cogwork.cogworkbridge.client.render.EncasedFanRenderer;
import com.cogwork.cogworkbridge.client.render.CreativeMotorRenderer;
import com.cogwork.cogworkbridge.client.render.GearboxRenderer;
import com.cogwork.cogworkbridge.client.render.EncasedShaftRenderer;
import com.cogwork.cogworkbridge.client.render.EncasedCogwheelRenderer;
import com.cogwork.cogworkbridge.client.render.MechanicalBearingRenderer;
import com.cogwork.cogworkbridge.client.render.MechanicalDrillRenderer;
import com.cogwork.cogworkbridge.client.render.DeployerRenderer;
import com.cogwork.cogworkbridge.client.render.MechanicalSawRenderer;
import com.cogwork.cogworkbridge.client.render.MechanicalHarvesterRenderer;
import com.cogwork.cogworkbridge.client.render.MechanicalRollerRenderer;
import com.cogwork.cogworkbridge.client.render.ShaftRenderer;
import com.cogwork.cogworkbridge.client.render.SplitShaftRenderer;
import com.cogwork.cogworkbridge.client.render.SequencedGearshiftRenderer;
import com.cogwork.cogworkbridge.client.render.SpeedControllerRenderer;
import com.cogwork.cogworkbridge.client.render.BasinRenderer;
import com.cogwork.cogworkbridge.client.render.FluidTankRenderer;
import com.cogwork.cogworkbridge.client.render.MechanicalPumpRenderer;
import com.cogwork.cogworkbridge.client.render.FluidValveRenderer;
import com.cogwork.cogworkbridge.client.render.HosePulleyRenderer;
import com.cogwork.cogworkbridge.client.render.FluidSpoutRenderer;
import com.cogwork.cogworkbridge.client.render.TunnelRenderer;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BrassTunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BeltTunnelTileEntity;
import com.cogwork.cogworkbridge.client.render.MechanicalArmRenderer;
import com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmTileEntity;
import com.cogwork.cogworkbridge.client.render.DepotRenderer;
import com.cogwork.cogworkbridge.client.render.CreativeCrateRenderer;
import com.cogwork.cogworkbridge.content.logistics.crate.CreativeCrateTileEntity;
import com.cogwork.cogworkbridge.client.render.PortableStorageInterfaceRenderer;
import com.cogwork.cogworkbridge.content.logistics.portable.PortableStorageInterfaceTileEntity;
import com.cogwork.cogworkbridge.client.render.ChuteRenderer;
import com.cogwork.cogworkbridge.content.logistics.chute.ChuteTileEntity;
import com.cogwork.cogworkbridge.content.logistics.chute.SmartChuteTileEntity;
import com.cogwork.cogworkbridge.client.render.FunnelRenderer;
import com.cogwork.cogworkbridge.content.logistics.funnel.FunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.funnel.BrassFunnelTileEntity;
import com.cogwork.cogworkbridge.client.render.BeltRenderer;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.client.render.ItemHatchRenderer;
import com.cogwork.cogworkbridge.content.logistics.hatch.ItemHatchTileEntity;
import com.cogwork.cogworkbridge.client.render.EjectorRenderer;
import com.cogwork.cogworkbridge.content.logistics.ejector.EjectorTileEntity;
import com.cogwork.cogworkbridge.client.render.GlassFluidPipeRenderer;
import com.cogwork.cogworkbridge.client.render.SmartFluidPipeRenderer;
import com.cogwork.cogworkbridge.client.render.PortableFluidInterfaceRenderer;
import com.cogwork.cogworkbridge.client.render.BlazeBurnerRenderer;
import com.cogwork.cogworkbridge.client.render.MechanicalPressRenderer;
import com.cogwork.cogworkbridge.client.render.MechanicalMixerRenderer;
import com.cogwork.cogworkbridge.client.render.MillstoneRenderer;
import com.cogwork.cogworkbridge.client.render.MechanicalCrafterRenderer;
import com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity;
import com.cogwork.cogworkbridge.client.render.CrushingWheelRenderer;
import com.cogwork.cogworkbridge.content.kinetics.crusher.CrushingWheelTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.MechanicalBearingTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.actors.harvester.MechanicalHarvesterTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.chassis.StickerTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.chain.ChainDriveTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.deployer.DeployerTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.gearbox.GearboxTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.encased.EncasedShaftTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.encased.EncasedCogwheelTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.motor.CreativeMotorTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.transmission.SplitShaftTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencedGearshiftTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.speedController.SpeedControllerTileEntity;
import com.cogwork.cogworkbridge.content.processing.basin.BasinTileEntity;
import com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity;
import com.cogwork.cogworkbridge.content.fluids.FluidPipeTileEntity;
import com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeTileEntity;
import com.cogwork.cogworkbridge.content.fluids.MechanicalPumpTileEntity;
import com.cogwork.cogworkbridge.content.fluids.FluidValveTileEntity;
import com.cogwork.cogworkbridge.content.fluids.HosePulleyTileEntity;
import com.cogwork.cogworkbridge.content.fluids.CreativeFluidTankTileEntity;
import com.cogwork.cogworkbridge.content.fluids.ItemDrainTileEntity;
import com.cogwork.cogworkbridge.content.fluids.FluidSpoutTileEntity;
import com.cogwork.cogworkbridge.content.fluids.PortableFluidInterfaceTileEntity;
import com.cogwork.cogworkbridge.content.logistics.depot.DepotTileEntity;
import com.cogwork.cogworkbridge.content.processing.burner.BlazeBurnerTileEntity;
import com.cogwork.cogworkbridge.content.processing.press.MechanicalPressTileEntity;
import com.cogwork.cogworkbridge.content.processing.mixer.MechanicalMixerTileEntity;
import com.cogwork.cogworkbridge.content.processing.millstone.MillstoneTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.client.render.BearingContraptionRenderer;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import com.cogwork.cogworkbridge.Reference;
import net.minecraftforge.fml.relauncher.Side;

/** Client-only registrar for tile entity special renderers. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ClientRenderers {

    private ClientRenderers() {
    }

    public static void register() {
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.trains.track.TrackTileEntity.class,
                new com.cogwork.cogworkbridge.client.render.TrackCurveRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.trains.track.BogeyTile.class,
                new com.cogwork.cogworkbridge.client.render.BogeyRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.trains.track.RailwayTargetTile.Signal.class,
                new com.cogwork.cogworkbridge.client.render.RailwayDeviceRenderer.Signal());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.trains.track.RailwayTargetTile.Observer.class,
                new com.cogwork.cogworkbridge.client.render.RailwayDeviceRenderer.Observer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.trains.track.RailwayTargetTile.Station.class,
                new com.cogwork.cogworkbridge.client.render.RailwayDeviceRenderer.Station());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.decoration.palette.DoorStateTile.class, new com.cogwork.cogworkbridge.client.render.SlidingDoorRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.equipment.BacktankTileEntity.class, new com.cogwork.cogworkbridge.client.render.BacktankRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.equipment.BellTileEntity.class, new com.cogwork.cogworkbridge.client.render.BellRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.equipment.ToolboxTileEntity.class, new com.cogwork.cogworkbridge.client.render.ToolboxRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity.class, new com.cogwork.cogworkbridge.client.render.DetectorRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorTileEntity.class, new com.cogwork.cogworkbridge.client.render.ChainConveyorRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelTileEntity.class, new com.cogwork.cogworkbridge.client.render.FactoryPanelRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.logistics.table.TableClothTileEntity.class, new com.cogwork.cogworkbridge.client.render.TableClothRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.logistics.port.PackagePortTileEntity.class, new com.cogwork.cogworkbridge.client.render.PackagePortRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.logistics.packager.RepackagerTileEntity.class, new com.cogwork.cogworkbridge.client.render.PackagerRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.redstone.PulseDiodeTileEntity.class, new com.cogwork.cogworkbridge.client.render.PulseDiodeRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.redstone.PlacardTileEntity.class, new com.cogwork.cogworkbridge.client.render.PlacardRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.logistics.packager.PackagerTileEntity.class, new com.cogwork.cogworkbridge.client.render.PackagerRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.redstone.AnalogLeverTileEntity.class, new com.cogwork.cogworkbridge.client.render.AnalogLeverRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.redstone.DeskBellTileEntity.class, new com.cogwork.cogworkbridge.client.render.DeskBellRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.redstone.RedstoneLinkTileEntity.class, new com.cogwork.cogworkbridge.client.render.RedstoneLinkRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.display.DisplayBoardTileEntity.class, new com.cogwork.cogworkbridge.client.render.DisplayBoardRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.display.NixieTubeTileEntity.class, new com.cogwork.cogworkbridge.client.render.NixieTubeRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.kinetics.gauge.GaugeTileEntity.class, new com.cogwork.cogworkbridge.client.render.GaugeRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.kinetics.crank.HandCrankTileEntity.class, new com.cogwork.cogworkbridge.client.render.HandCrankRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.kinetics.crank.ValveHandleTileEntity.class, new com.cogwork.cogworkbridge.client.render.HandCrankRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.kinetics.waterwheel.LargeWaterWheelTileEntity.class, new com.cogwork.cogworkbridge.client.render.LargeWaterWheelRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.kinetics.waterwheel.WaterWheelTileEntity.class, new com.cogwork.cogworkbridge.client.render.WaterWheelRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.contraptions.bearing.WindmillBearingTileEntity.class, new MechanicalBearingRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.kinetics.steam.PoweredShaftTileEntity.class, new ShaftRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.kinetics.steam.SteamEngineTileEntity.class, new com.cogwork.cogworkbridge.client.render.SteamEngineRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.redstone.LecternControllerTileEntity.class, new com.cogwork.cogworkbridge.client.render.LecternControllerRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleTileEntity.class, new com.cogwork.cogworkbridge.client.render.SteamWhistleRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.fluids.whistle.WhistleExtensionTileEntity.class, new com.cogwork.cogworkbridge.client.render.WhistleExtensionRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.kinetics.clock.CuckooClockTileEntity.class, new com.cogwork.cogworkbridge.client.render.CuckooClockRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.schematics.SchematicannonTile.class, new com.cogwork.cogworkbridge.client.render.SchematicannonRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.kinetics.turntable.TurntableTileEntity.class, new com.cogwork.cogworkbridge.client.render.TurntableRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.kinetics.flywheel.FlywheelTileEntity.class, new com.cogwork.cogworkbridge.client.render.FlywheelRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(ShaftTileEntity.class, new ShaftRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(EncasedShaftTileEntity.class, new EncasedShaftRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(EncasedCogwheelTileEntity.class, new EncasedCogwheelRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(CogwheelTileEntity.class, new CogwheelRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(ChainDriveTileEntity.class, new ChainDriveRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(SpeedControllerTileEntity.class, new SpeedControllerRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(EncasedFanTileEntity.class, new EncasedFanRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(GearboxTileEntity.class, new GearboxRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(SplitShaftTileEntity.class, new SplitShaftRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(SequencedGearshiftTileEntity.class, new SequencedGearshiftRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(CreativeMotorTileEntity.class, new CreativeMotorRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(MechanicalBearingTileEntity.class, new MechanicalBearingRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(StickerTileEntity.class, new com.cogwork.cogworkbridge.client.render.StickerRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.contraptions.controls.ContraptionControlsTileEntity.class, new com.cogwork.cogworkbridge.client.render.ContraptionControlsRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(MechanicalDrillTileEntity.class, new MechanicalDrillRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(DeployerTileEntity.class, new DeployerRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(MechanicalSawTileEntity.class, new MechanicalSawRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(MechanicalHarvesterTileEntity.class, new MechanicalHarvesterRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(MechanicalRollerTileEntity.class, new MechanicalRollerRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(BasinTileEntity.class, new BasinRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(FluidTankTileEntity.class, new FluidTankRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(FluidPipeTileEntity.class, new GlassFluidPipeRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(SmartFluidPipeTileEntity.class, new SmartFluidPipeRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(MechanicalPumpTileEntity.class, new MechanicalPumpRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(FluidValveTileEntity.class, new FluidValveRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(HosePulleyTileEntity.class, new HosePulleyRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.contraptions.elevator.ElevatorPulleyTileEntity.class, new com.cogwork.cogworkbridge.client.render.ElevatorPulleyRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.contraptions.pulley.RopePulleyTileEntity.class, new com.cogwork.cogworkbridge.client.render.RopePulleyRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.contraptions.gantry.GantryShaftTileEntity.class, new com.cogwork.cogworkbridge.client.render.GantryShaftRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageTileEntity.class, new com.cogwork.cogworkbridge.client.render.GantryCarriageRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonTileEntity.class, new com.cogwork.cogworkbridge.client.render.MechanicalPistonRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.cogwork.cogworkbridge.content.contraptions.bearing.ClockworkBearingTileEntity.class, new com.cogwork.cogworkbridge.client.render.ClockworkBearingRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(CreativeFluidTankTileEntity.class, new FluidTankRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(ItemDrainTileEntity.class, new FluidTankRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(FluidSpoutTileEntity.class, new FluidSpoutRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(BeltTunnelTileEntity.class, new TunnelRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(BrassTunnelTileEntity.class, new TunnelRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(MechanicalArmTileEntity.class, new MechanicalArmRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(DepotTileEntity.class, new DepotRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(CreativeCrateTileEntity.class, new CreativeCrateRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(PortableStorageInterfaceTileEntity.class, new PortableStorageInterfaceRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(ChuteTileEntity.class, new ChuteRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(SmartChuteTileEntity.class, new ChuteRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(FunnelTileEntity.class, new FunnelRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(BrassFunnelTileEntity.class, new FunnelRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(BeltTileEntity.class, new BeltRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(ItemHatchTileEntity.class, new ItemHatchRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(EjectorTileEntity.class, new EjectorRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(PortableFluidInterfaceTileEntity.class, new PortableFluidInterfaceRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(BlazeBurnerTileEntity.class, new BlazeBurnerRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(MechanicalPressTileEntity.class, new MechanicalPressRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(MechanicalMixerTileEntity.class, new MechanicalMixerRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(MillstoneTileEntity.class, new MillstoneRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(MechanicalCrafterTileEntity.class, new MechanicalCrafterRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(CrushingWheelTileEntity.class, new CrushingWheelRenderer());
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
                BearingContraptionEntity.class, BearingContraptionRenderer::new);
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
                com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity.class,
                manager -> new net.minecraft.client.renderer.entity.Render<com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity>(manager) {
                    @Override protected net.minecraft.util.ResourceLocation getEntityTexture(com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity entity){return null;}
                    @Override public void doRender(com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity entity,double x,double y,double z,float yaw,float partial){ }
                });
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
                com.cogwork.cogworkbridge.content.equipment.PotatoProjectileEntity.class,
                com.cogwork.cogworkbridge.client.render.PotatoProjectileRenderer::new);
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
                com.cogwork.cogworkbridge.content.equipment.BlueprintEntity.class,
                com.cogwork.cogworkbridge.client.render.BlueprintRenderer::new);
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
                com.cogwork.cogworkbridge.content.decoration.SeatEntity.class,
                manager -> new net.minecraft.client.renderer.entity.Render<com.cogwork.cogworkbridge.content.decoration.SeatEntity>(manager) {
                    @Override protected net.minecraft.util.ResourceLocation getEntityTexture(com.cogwork.cogworkbridge.content.decoration.SeatEntity seat) { return null; }
                    @Override public void doRender(com.cogwork.cogworkbridge.content.decoration.SeatEntity seat, double x, double y, double z, float yaw, float partialTicks) { }
                });
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
                com.cogwork.cogworkbridge.content.logistics.packagebox.PackageEntity.class,
                com.cogwork.cogworkbridge.client.render.PackageEntityRenderer::new);
    }
}
