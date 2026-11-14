package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import javax.annotation.Nullable;

/** Processing fluids and the two upstream placeable fluids and bucket items. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class ProcessingFluids {
    public static Block HONEY_BLOCK, CHOCOLATE_BLOCK;
    public static Item HONEY_BUCKET, CHOCOLATE_BUCKET;
    private ProcessingFluids() { }

    public static void register() {
        registerFluid("honey", 1400, 2000);
        registerFluid("chocolate", 1400, 2000);
        registerFluid("tea", 1000, 1000);
    }
    private static void registerFluid(String name, int density, int viscosity) {
        if (FluidRegistry.getFluid(name) != null) return;
        FluidRegistry.registerFluid(new Fluid(name, new ResourceLocation("create", "fluid/" + name + "_still"),
                new ResourceLocation("create", "fluid/" + name + "_flow"))
                .setDensity(density).setViscosity(viscosity));
    }
    @SubscribeEvent public static void registerBlocks(RegistryEvent.Register<Block> event) {
        register();
        HONEY_BLOCK = registerBlock(event, "honey");
        CHOCOLATE_BLOCK = registerBlock(event, "chocolate");
    }
    private static Block registerBlock(RegistryEvent.Register<Block> event, String name) {
        Fluid fluid = FluidRegistry.getFluid(name);
        // Reuse an existing mod's world fluid rather than reassigning its Fluid instance.
        if (fluid.getBlock() != null) return fluid.getBlock();
        Block block = new FoodFluidBlock(fluid).setRegistryName(Reference.MOD_ID, name)
                .setTranslationKey(Reference.MOD_ID + "." + name);
        event.getRegistry().register(block);
        return block;
    }
    @SubscribeEvent public static void registerItems(RegistryEvent.Register<Item> event) {
        HONEY_BUCKET = registerBucket(event, "honey", HONEY_BLOCK);
        CHOCOLATE_BUCKET = registerBucket(event, "chocolate", CHOCOLATE_BLOCK);
    }
    private static Item registerBucket(RegistryEvent.Register<Item> event, String name, Block block) {
        Item bucket = new FilledBucket(block, FluidRegistry.getFluid(name));
        bucket.setRegistryName(Reference.MOD_ID, name + "_bucket");
        bucket.setTranslationKey(Reference.MOD_ID + "." + name + "_bucket");
        bucket.setCreativeTab(com.cogwork.cogworkbridge.registry.ModCreativeTab.INSTANCE);
        event.getRegistry().register(bucket);
        return bucket;
    }
    @Nullable public static Item bucketFor(@Nullable FluidStack fluid) {
        if (fluid == null) return null;
        if (fluid.getFluid() == FluidRegistry.getFluid("honey")) return HONEY_BUCKET;
        if (fluid.getFluid() == FluidRegistry.getFluid("chocolate")) return CHOCOLATE_BUCKET;
        return null;
    }
    @SubscribeEvent public static void fillBucket(FillBucketEvent event) {
        if (event.getEmptyBucket().getItem() != Items.BUCKET || event.getTarget() == null
                || event.getTarget().typeOfHit != RayTraceResult.Type.BLOCK) return;
        World world = event.getWorld(); BlockPos pos = event.getTarget().getBlockPos();
        if (!world.isBlockModifiable(event.getEntityPlayer(), pos)
                || !event.getEntityPlayer().canPlayerEdit(pos, event.getTarget().sideHit, event.getEmptyBucket())) return;
        Block block = world.getBlockState(pos).getBlock();
        if (!(block instanceof IFluidBlock source)) return;
        FluidStack fluid = source.drain(world, pos, false);
        Item bucket = bucketFor(fluid);
        if (bucket == null || fluid.amount != Fluid.BUCKET_VOLUME) return;
        if (!world.isRemote) source.drain(world, pos, true);
        event.setFilledBucket(new ItemStack(bucket));
        event.setResult(Event.Result.ALLOW);
    }

    private static final class FilledBucket extends ItemBucket {
        private final Fluid fluid;
        FilledBucket(Block block, Fluid fluid) { super(block); this.fluid = fluid; setContainerItem(Items.BUCKET); }
        @Override public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(ItemStack stack, @Nullable net.minecraft.nbt.NBTTagCompound tag) {
            return new FluidBucketWrapper(stack) {
                @Override public FluidStack getFluid() {
                    return container.getItem() == FilledBucket.this ? new FluidStack(fluid, Fluid.BUCKET_VOLUME) : null;
                }
                @Override protected void setFluid(@Nullable FluidStack contents) {
                    if (contents == null) container = new ItemStack(Items.BUCKET);
                    else super.setFluid(contents);
                }
            };
        }
    }
    private static final class FoodFluidBlock extends BlockFluidClassic {
        FoodFluidBlock(Fluid fluid) { super(fluid, Material.WATER); setQuantaPerBlock(4); setTickRandomly(true); }
        @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
            super.neighborChanged(state, world, pos, block, from);
            if (!world.isRemote) interactWithLava(world, pos);
        }
        @Override public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
            super.onBlockAdded(world, pos, state);
            if (!world.isRemote) interactWithLava(world, pos);
        }
        private void interactWithLava(World world, BlockPos pos) {
            for (EnumFacing side : EnumFacing.VALUES) {
                BlockPos other = pos.offset(side); IBlockState lava = world.getBlockState(other);
                if (lava.getBlock() != Blocks.LAVA && lava.getBlock() != Blocks.FLOWING_LAVA) continue;
                boolean source = lava.getValue(net.minecraft.block.BlockLiquid.LEVEL) == 0;
                Block result = source ? Blocks.OBSIDIAN : com.cogwork.cogworkbridge.registry.BlockFamily.get(
                        getFluid().getName().equals("honey") ? "limestone" : "scoria");
                if (result != null) world.setBlockState(other, result.getDefaultState(), 3);
            }
        }
    }
}
