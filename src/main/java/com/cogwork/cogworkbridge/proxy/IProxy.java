package com.cogwork.cogworkbridge.proxy;

import com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public interface IProxy {
    IMessage handleClientMessage(IMessage message, MessageContext context);
    void updateLecternControl(com.cogwork.cogworkbridge.content.redstone.LecternControllerTileEntity tile);
    void updateWhistleAudio(com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleTileEntity tile);
    void spawnFanAirFlow(EncasedFanTileEntity fan, EnumFacing facing, boolean pushing);
    void spawnFluidParticle(net.minecraft.world.World world, net.minecraftforge.fluids.FluidStack fluid,
            double x, double y, double z, double vx, double vy, double vz);
    void requesterEffect(net.minecraft.world.World world, BlockPos pos);

    Object createPackageFilterScreen(InventoryPlayer inventory);
    Object createStockTickerScreen(net.minecraft.world.World world, BlockPos pos);
    Object createRedstoneRequesterScreen(InventoryPlayer player, net.minecraft.world.World world, BlockPos pos);
    Object createPackagePortScreen(InventoryPlayer player, net.minecraft.world.World world, BlockPos pos);
    void updateStockTicker(int window, boolean first, boolean last, java.util.List<net.minecraft.item.ItemStack> items, java.util.List<Integer> counts);
    Object createFilterScreen(InventoryPlayer inventory);
    Object createAttributeFilterScreen(InventoryPlayer inventory);
    Object createFunnelConfigScreen(BlockPos pos);
    void openDisplayLinkScreen(BlockPos pos);
    void openElevatorContactScreen(BlockPos pos);
    void openThresholdSwitchScreen(BlockPos pos);
    void openFactoryPanelScreen(BlockPos pos, int slot);
    void openClipboardScreen(BlockPos target, int slot);
    Object createLinkedControllerScreen(InventoryPlayer inventory);
    void toggleLinkedController(net.minecraft.util.EnumHand hand);
    void bindLinkedController(BlockPos pos, net.minecraft.util.EnumHand hand);
    void openSymmetryWandScreen(net.minecraft.util.EnumHand hand);
    void openWorldshaperScreen(net.minecraft.util.EnumHand hand);
    void spawnSoulPulse(BlockPos pos, int distance);
    Object createBlueprintScreen(InventoryPlayer inventory, net.minecraft.world.World world, int entityId, int section);
    Object createToolboxScreen(InventoryPlayer inventory, net.minecraft.world.World world, BlockPos pos);
    void syncToolboxLinks(net.minecraft.nbt.NBTTagCompound links);
    void potatoCannonFired(net.minecraft.util.EnumHand hand);
    void schematicSelect(boolean clear, BlockPos pos, EnumFacing face);
    void openSchematicEdit();
    void receiveSchematic(String id, int total, int offset, byte[] data);
    Object createSchematicScreen(InventoryPlayer inventory, net.minecraft.world.World world, BlockPos pos, boolean cannon);

    void construct(FMLConstructionEvent event);

    void preInit(FMLPreInitializationEvent event);

    void init(FMLInitializationEvent event);

    void postInit(FMLPostInitializationEvent event);

    void serverStarting(FMLServerStartingEvent event);
}
