package com.cogwork.cogworkbridge.proxy;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.client.ClientRenderers;
import com.cogwork.cogworkbridge.client.render.FastStateRendering;
import com.cogwork.cogworkbridge.client.TargetResourceContainer;
import com.cogwork.cogworkbridge.client.UpstreamModelLoader;
import com.cogwork.cogworkbridge.client.particle.FanAirParticle;
import com.cogwork.cogworkbridge.client.ListFilterScreen;
import com.cogwork.cogworkbridge.client.AttributeFilterScreen;
import com.cogwork.cogworkbridge.client.BrassFunnelConfigScreen;
import com.cogwork.cogworkbridge.content.fluids.ListFilterContainer;
import com.cogwork.cogworkbridge.content.fluids.AttributeFilterContainer;
import com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanTileEntity;
import com.cogwork.cogworkbridge.core.CogworkBridgeCore;
import com.cogwork.cogworkbridge.core.TargetJar;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.util.List;

/**
 * Client side wiring.
 *
 * <p>The upstream asset jar is registered during {@link FMLConstructionEvent}:
 * Forge builds the resource pack list while constructing {@code Minecraft}, which
 * happens after mod construction but before pre-init. Registering later would be
 * silently ignored.
 */
public class ClientProxy extends CommonProxy {
    @Override public void spawnFluidParticle(net.minecraft.world.World world, net.minecraftforge.fluids.FluidStack fluid,
            double x, double y, double z, double vx, double vy, double vz) {
        if (fluid != null && fluid.getFluid().getStill(fluid) != null)
            Minecraft.getMinecraft().effectRenderer.addEffect(new com.cogwork.cogworkbridge.client.particle.FluidParticle(world, fluid, x, y, z, vx, vy, vz));
    }

    @Override public void openDisplayLinkScreen(BlockPos pos) {
        Minecraft.getMinecraft().displayGuiScreen(new com.cogwork.cogworkbridge.client.DisplayLinkScreen(pos));
    }
    @Override public void openThresholdSwitchScreen(BlockPos pos) { Minecraft.getMinecraft().displayGuiScreen(new com.cogwork.cogworkbridge.client.ThresholdSwitchScreen(pos)); }
    @Override public void openElevatorContactScreen(BlockPos pos) {
        Minecraft.getMinecraft().displayGuiScreen(new com.cogwork.cogworkbridge.client.ElevatorContactScreen(pos));
    }
    @Override public void openClipboardScreen(BlockPos target, int slot) {
        Minecraft.getMinecraft().displayGuiScreen(new com.cogwork.cogworkbridge.client.ClipboardScreen(target, slot));
    }
    @Override public void updateLecternControl(com.cogwork.cogworkbridge.content.redstone.LecternControllerTileEntity tile) { com.cogwork.cogworkbridge.client.LinkedControllerClientEvents.updateLectern(tile); }
    @Override public void updateWhistleAudio(com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleTileEntity tile) { com.cogwork.cogworkbridge.client.WhistleAudio.update(tile); }
    @Override public Object createLinkedControllerScreen(InventoryPlayer inventory) {
        return new com.cogwork.cogworkbridge.client.LinkedControllerScreen(new com.cogwork.cogworkbridge.content.redstone.LinkedControllerContainer(inventory));
    }
    @Override public void toggleLinkedController(net.minecraft.util.EnumHand hand) {
        com.cogwork.cogworkbridge.client.LinkedControllerClientEvents.toggle(hand);
    }
    @Override public void bindLinkedController(BlockPos pos, net.minecraft.util.EnumHand hand) {
        com.cogwork.cogworkbridge.client.LinkedControllerClientEvents.bind(pos, hand);
    }
    @Override public void openWorldshaperScreen(net.minecraft.util.EnumHand hand) {
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new com.cogwork.cogworkbridge.client.WorldshaperScreen(hand));
    }
    @Override public void spawnSoulPulse(BlockPos pos, int distance) { com.cogwork.cogworkbridge.client.SoulPulseClient.add(pos, distance); }
    @Override public Object createBlueprintScreen(InventoryPlayer inventory, net.minecraft.world.World world, int entityId, int section) {
        return world.getEntityByID(entityId) instanceof com.cogwork.cogworkbridge.content.equipment.BlueprintEntity blueprint
                && section >= 0 && section < blueprint.size() * blueprint.size()
                ? new com.cogwork.cogworkbridge.client.BlueprintScreen(new com.cogwork.cogworkbridge.content.equipment.BlueprintContainer(inventory, blueprint, section)) : null;
    }
    @Override public Object createToolboxScreen(InventoryPlayer inventory, net.minecraft.world.World world, BlockPos pos) {
        if (!(world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.equipment.ToolboxTileEntity box)) return null;
        return new com.cogwork.cogworkbridge.client.ToolboxScreen(
                new com.cogwork.cogworkbridge.content.equipment.ToolboxContainer(inventory, box, inventory.player));
    }
    @Override public void syncToolboxLinks(net.minecraft.nbt.NBTTagCompound links) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (Minecraft.getMinecraft().player != null)
                com.cogwork.cogworkbridge.content.equipment.ToolboxLinks.applyClient(Minecraft.getMinecraft().player, links);
        });
    }
    @Override public void schematicSelect(boolean clear, BlockPos pos, EnumFacing face) {
        com.cogwork.cogworkbridge.client.SchematicClient.select(clear, pos, face);
    }
    @Override public void openSchematicEdit() { Minecraft.getMinecraft().displayGuiScreen(new com.cogwork.cogworkbridge.client.SchematicEditScreen()); }
    @Override public void receiveSchematic(String id, int total, int offset, byte[] data) { com.cogwork.cogworkbridge.client.SchematicClient.receive(id, total, offset, data); }
    @Override public Object createSchematicScreen(InventoryPlayer inventory, net.minecraft.world.World world, BlockPos pos, boolean cannon) {
        if (!(world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.schematics.SchematicInventoryTile tile)
                || cannon != (tile instanceof com.cogwork.cogworkbridge.content.schematics.SchematicannonTile)) return null;
        return new com.cogwork.cogworkbridge.client.SchematicMachineScreen(
                new com.cogwork.cogworkbridge.content.schematics.SchematicContainer(inventory, world, pos, tile));
    }
    @Override public void potatoCannonFired(net.minecraft.util.EnumHand hand) { com.cogwork.cogworkbridge.client.PotatoCannonRenderHandler.fire(hand); }
    @Override public void openSymmetryWandScreen(net.minecraft.util.EnumHand hand) {
        Minecraft.getMinecraft().displayGuiScreen(new com.cogwork.cogworkbridge.client.SymmetryWandScreen(hand));
    }

    @Override
    public Object createPackageFilterScreen(InventoryPlayer inventory) {
        return new com.cogwork.cogworkbridge.client.PackageFilterScreen(new com.cogwork.cogworkbridge.content.logistics.packagebox.PackageFilterContainer(inventory));
    }
    @Override public void openFactoryPanelScreen(BlockPos pos, int slot) { Minecraft.getMinecraft().displayGuiScreen(new com.cogwork.cogworkbridge.client.FactoryPanelScreen(pos, slot)); }
    @Override public Object createPackagePortScreen(InventoryPlayer player, net.minecraft.world.World world, BlockPos pos) {
        if (!(world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.logistics.port.PackagePortTileEntity port)) return null;
        return new com.cogwork.cogworkbridge.client.PackagePortScreen(new com.cogwork.cogworkbridge.content.logistics.port.PackagePortContainer(player, world, pos, port));
    }
    @Override public Object createStockTickerScreen(net.minecraft.world.World world, BlockPos pos) {
        return new com.cogwork.cogworkbridge.client.StockTickerScreen(new com.cogwork.cogworkbridge.content.logistics.packager.StockTickerContainer(world, pos));
    }
    @Override public Object createRedstoneRequesterScreen(InventoryPlayer player, net.minecraft.world.World world, BlockPos pos) {
        if (!(world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.logistics.packager.RedstoneRequesterTileEntity requester)) return null;
        return new com.cogwork.cogworkbridge.client.RedstoneRequesterScreen(
                new com.cogwork.cogworkbridge.content.logistics.packager.RedstoneRequesterContainer(player, world, pos, requester));
    }
    @Override public void updateStockTicker(int window, boolean first, boolean last, java.util.List<net.minecraft.item.ItemStack> items, java.util.List<Integer> counts) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (Minecraft.getMinecraft().currentScreen instanceof com.cogwork.cogworkbridge.client.StockTickerScreen screen)
                screen.updateStock(window, first, last, items, counts);
        });
    }
    @Override public Object createFilterScreen(InventoryPlayer inventory) {
        return new ListFilterScreen(new ListFilterContainer(inventory));
    }
    @Override public Object createAttributeFilterScreen(InventoryPlayer inventory) {
        return new AttributeFilterScreen(new AttributeFilterContainer(inventory));
    }
    @Override public Object createFunnelConfigScreen(BlockPos pos) {
        return new BrassFunnelConfigScreen(pos);
    }

    @Override
    public void spawnFanAirFlow(EncasedFanTileEntity fan, EnumFacing facing, boolean pushing) {
        Minecraft.getMinecraft().effectRenderer.addEffect(new FanAirParticle(fan, facing, pushing));
    }

    @Override
    public void construct(FMLConstructionEvent event) {
        super.construct(event);
        mountUpstreamAssets();
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        ModelLoaderRegistry.registerLoader(UpstreamModelLoader.INSTANCE);
        net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(com.cogwork.cogworkbridge.client.ToolboxClientEvents.TOOLBELT);
        net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(com.cogwork.cogworkbridge.client.WrenchRotationClientEvents.ROTATE_MENU);
        net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(com.cogwork.cogworkbridge.client.SchematicToolClient.TOOL_MENU);
        FastStateRendering.initialize();
        ClientRenderers.register();
        if (net.minecraftforge.fml.common.Loader.isModLoaded("ponder-legacy"))
            com.cogwork.cogworkbridge.compat.ponder.CogworkPonder.initialize();
    }

    private void mountUpstreamAssets() {
        List<TargetJar> targets = CogworkBridgeCore.getTargets();
        for (TargetJar target : targets) {
            FMLClientHandler.instance().addModAsResource(new TargetResourceContainer(target));
            CogworkBridge.LOGGER.info("Registered {} as a resource pack", target.getFile().getName());
        }
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
    @Override public void requesterEffect(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos) {
        net.minecraft.client.Minecraft.getMinecraft().effectRenderer.addEffect(
                new com.cogwork.cogworkbridge.client.particle.WifiParticle(world, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5));
    }
}
