package com.cogwork.cogworkbridge.proxy;
import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanTileEntity;
import com.cogwork.cogworkbridge.content.fluids.PotionFluid;
import com.cogwork.cogworkbridge.content.fluids.MilkFluid;
import com.cogwork.cogworkbridge.content.fluids.ListFilterGuiHandler;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.AllEntities;
import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.util.EnumFacing;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class CommonProxy implements IProxy {
    @Override public IMessage handleClientMessage(IMessage message, MessageContext context) { return null; }
    @Override public void updateLecternControl(com.cogwork.cogworkbridge.content.redstone.LecternControllerTileEntity tile) { }
    @Override public void updateWhistleAudio(com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleTileEntity tile) { }
    @Override public void spawnFluidParticle(net.minecraft.world.World world, net.minecraftforge.fluids.FluidStack fluid,
            double x, double y, double z, double vx, double vy, double vz) { }

    @Override
    public Object createPackageFilterScreen(InventoryPlayer inventory) { return null; }
    @Override public Object createStockTickerScreen(net.minecraft.world.World world, BlockPos pos) { return null; }
    @Override public Object createRedstoneRequesterScreen(InventoryPlayer player, net.minecraft.world.World world, BlockPos pos) { return null; }
    @Override public Object createPackagePortScreen(InventoryPlayer player, net.minecraft.world.World world, BlockPos pos) { return null; }
    @Override public void updateStockTicker(int window, boolean first, boolean last, java.util.List<net.minecraft.item.ItemStack> items, java.util.List<Integer> counts) { }
    @Override public Object createFilterScreen(InventoryPlayer inventory) { return null; }
    @Override public Object createAttributeFilterScreen(InventoryPlayer inventory) { return null; }
    @Override public Object createFunnelConfigScreen(BlockPos pos) { return null; }
    @Override public void openDisplayLinkScreen(BlockPos pos) { }
    @Override public void openElevatorContactScreen(BlockPos pos) { }
    @Override public void openThresholdSwitchScreen(BlockPos pos) { }
    @Override public void openFactoryPanelScreen(BlockPos pos, int slot) { }
    @Override public void openClipboardScreen(BlockPos target, int slot) { }
    @Override public Object createLinkedControllerScreen(InventoryPlayer inventory) { return null; }
    @Override public void toggleLinkedController(net.minecraft.util.EnumHand hand) { }
    @Override public void bindLinkedController(BlockPos pos, net.minecraft.util.EnumHand hand) { }
    @Override public void openSymmetryWandScreen(net.minecraft.util.EnumHand hand) { }
    @Override public void openWorldshaperScreen(net.minecraft.util.EnumHand hand) { }
    @Override public void spawnSoulPulse(BlockPos pos, int distance) { }
    @Override public Object createBlueprintScreen(InventoryPlayer inventory, net.minecraft.world.World world, int entityId, int section) { return null; }
    @Override public Object createToolboxScreen(InventoryPlayer inventory, net.minecraft.world.World world, BlockPos pos) { return null; }
    @Override public void syncToolboxLinks(net.minecraft.nbt.NBTTagCompound links) { }
    @Override public void potatoCannonFired(net.minecraft.util.EnumHand hand) { }
    @Override public void schematicSelect(boolean clear, BlockPos pos, EnumFacing face) { }
    @Override public void openSchematicEdit() { }
    @Override public void receiveSchematic(String id, int total, int offset, byte[] data) { }
    @Override public Object createSchematicScreen(InventoryPlayer inventory, net.minecraft.world.World world, BlockPos pos, boolean cannon) { return null; }

    @Override
    public void spawnFanAirFlow(EncasedFanTileEntity fan, EnumFacing facing, boolean pushing) {
    }

    @Override
    public void construct(FMLConstructionEvent event) {
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        MilkFluid.register();
        PotionFluid.register();
        com.cogwork.cogworkbridge.content.fluids.ProcessingFluids.register();
        ModNetwork.register();
        AllItems.init();
        AllEntities.class.getName();
        AllBlocks.init();
        AllBlocks.registerTileEntities();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        com.cogwork.cogworkbridge.content.equipment.DivingUpgradeRecipes.registerSmithing();
        com.cogwork.cogworkbridge.content.decoration.palette.PaletteRecipes.registerStonecutting();
        com.cogwork.cogworkbridge.registry.BuildingPalette.registerCopperWeathering();
        NetworkRegistry.INSTANCE.registerGuiHandler(CogworkBridge.instance, new ListFilterGuiHandler());
        net.minecraftforge.fml.common.registry.GameRegistry.registerWorldGenerator(new com.cogwork.cogworkbridge.registry.ZincWorldGen(), 0);
        net.minecraftforge.fml.common.registry.GameRegistry.registerWorldGenerator(new com.cogwork.cogworkbridge.registry.PaletteWorldGen(), 0);
        net.minecraftforge.fml.common.registry.GameRegistry.addSmelting(AllBlocks.DEEPSLATE_ZINC_ORE, new net.minecraft.item.ItemStack(AllItems.ZINC_INGOT), 0.7F);
        net.minecraftforge.fml.common.registry.GameRegistry.addSmelting(AllBlocks.ZINC_ORE, new net.minecraft.item.ItemStack(AllItems.ZINC_INGOT), 0.7F);
        net.minecraftforge.fml.common.registry.GameRegistry.addSmelting(AllItems.CRUSHED_RAW_IRON, new net.minecraft.item.ItemStack(net.minecraft.init.Items.IRON_INGOT), .1F);
        net.minecraftforge.fml.common.registry.GameRegistry.addSmelting(AllItems.CRUSHED_RAW_GOLD, new net.minecraft.item.ItemStack(net.minecraft.init.Items.GOLD_INGOT), .1F);
        net.minecraftforge.fml.common.registry.GameRegistry.addSmelting(AllItems.CRUSHED_RAW_ZINC, new net.minecraft.item.ItemStack(AllItems.ZINC_INGOT), .1F);
        net.minecraftforge.fml.common.registry.GameRegistry.addSmelting(AllItems.DOUGH, new net.minecraft.item.ItemStack(net.minecraft.init.Items.BREAD), 0F);
        net.minecraft.item.Item copper = net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(new net.minecraft.util.ResourceLocation("farmers_future_delight", "copper_ingot"));
        if (copper != null && copper != net.minecraft.init.Items.AIR)
            net.minecraftforge.fml.common.registry.GameRegistry.addSmelting(AllItems.CRUSHED_RAW_COPPER, new net.minecraft.item.ItemStack(copper), .1F);
        for (net.minecraft.item.ItemStack raw : net.minecraftforge.oredict.OreDictionary.getOres("rawZinc"))
            net.minecraftforge.fml.common.registry.GameRegistry.addSmelting(raw.copy(), new net.minecraft.item.ItemStack(AllItems.ZINC_INGOT), .7F);
        net.minecraftforge.fml.common.registry.GameRegistry.addSmelting(net.minecraft.init.Blocks.SOUL_SAND,
                new net.minecraft.item.ItemStack(com.cogwork.cogworkbridge.registry.BlockFamily.get("scoria")), 0F);
        for (String glass : com.cogwork.cogworkbridge.registry.BuildingPalette.GLASS) {
            net.minecraftforge.fml.common.registry.GameRegistry.addSmelting(com.cogwork.cogworkbridge.registry.BlockFamily.get(glass),
                    new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.GLASS), 0F);
            net.minecraftforge.fml.common.registry.GameRegistry.addSmelting(com.cogwork.cogworkbridge.registry.BlockFamily.get(glass + "_pane"),
                    new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.GLASS_PANE), 0F);
        }
        registerCookingRecipes(copper);
    }

    private static void registerCookingRecipes(net.minecraft.item.Item copper) {
        var blast = xy177.farmersfuturedelight.api.WorkstationRecipes.Type.BLASTING;
        registerCooking(blast, "iron_from_crushed", net.minecraft.item.crafting.Ingredient.fromItem(AllItems.CRUSHED_RAW_IRON), new net.minecraft.item.ItemStack(net.minecraft.init.Items.IRON_INGOT), 100, .1F);
        registerCooking(blast, "gold_from_crushed", net.minecraft.item.crafting.Ingredient.fromItem(AllItems.CRUSHED_RAW_GOLD), new net.minecraft.item.ItemStack(net.minecraft.init.Items.GOLD_INGOT), 100, .1F);
        registerCooking(blast, "zinc_from_crushed", net.minecraft.item.crafting.Ingredient.fromItem(AllItems.CRUSHED_RAW_ZINC), new net.minecraft.item.ItemStack(AllItems.ZINC_INGOT), 100, .1F);
        registerCooking(blast, "zinc_from_ore", new net.minecraftforge.oredict.OreIngredient("oreZinc"), new net.minecraft.item.ItemStack(AllItems.ZINC_INGOT), 100, .7F);
        registerCooking(blast, "zinc_from_raw", new net.minecraftforge.oredict.OreIngredient("rawZinc"), new net.minecraft.item.ItemStack(AllItems.ZINC_INGOT), 100, .7F);
        if (copper != null && copper != net.minecraft.init.Items.AIR)
            registerCooking(blast, "copper_from_crushed", net.minecraft.item.crafting.Ingredient.fromItem(AllItems.CRUSHED_RAW_COPPER), new net.minecraft.item.ItemStack(copper), 100, .1F);
        var dough = net.minecraft.item.crafting.Ingredient.fromItem(AllItems.DOUGH);
        registerCooking(xy177.farmersfuturedelight.api.WorkstationRecipes.Type.SMOKING, "bread", dough, new net.minecraft.item.ItemStack(net.minecraft.init.Items.BREAD), 100, 0F);
        registerCooking(xy177.farmersfuturedelight.api.WorkstationRecipes.Type.CAMPFIRE_COOKING, "bread", dough, new net.minecraft.item.ItemStack(net.minecraft.init.Items.BREAD), 600, 0F);
    }

    private static void registerCooking(xy177.farmersfuturedelight.api.WorkstationRecipes.Type type, String name,
                                        net.minecraft.item.crafting.Ingredient input, net.minecraft.item.ItemStack output, int ticks, float experience) {
        xy177.farmersfuturedelight.api.WorkstationRecipes.register(type,
                new net.minecraft.util.ResourceLocation("cogworkbridge", "upstream/" + name),
                java.util.Collections.singletonList(input), output, ticks, experience);
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        com.cogwork.cogworkbridge.content.processing.CompatCrushedOres.registerSmelting();
    }

    @Override
    public void serverStarting(FMLServerStartingEvent event) {
    }
    public void requesterEffect(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos) { }
}
