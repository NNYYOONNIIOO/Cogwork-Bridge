package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/** Persistent, one-bucket buffers for the open faces of a fluid transport block. */
final class OpenEndStorage {
    private static final int BUCKET = 1000;
    private final TileEntity owner;
    private final Map<EnumFacing, OpenEnd> ends = new EnumMap<>(EnumFacing.class);
    private final Map<EnumFacing, Flow> flows = new EnumMap<>(EnumFacing.class);

    OpenEndStorage(TileEntity owner) {
        this.owner = owner;
    }

    @Nullable
    IFluidHandler get(EnumFacing side) {
        OpenEnd end = ends.computeIfAbsent(side, OpenEnd::new);
        return end.canAccess() ? end : null;
    }

    @Nullable
    Flow getFlow(EnumFacing side) { return flows.get(side); }

    /** Retry a full outgoing buffer after the target becomes available again. */
    void flush(EnumFacing side) {
        OpenEnd end = ends.get(side);
        if (end == null || end.buffer.getFluidAmount() != BUCKET) return;
        FluidStack fluid = end.buffer.getFluid();
        if (fluid != null && end.place(fluid)) {
            end.buffer.drain(BUCKET, true);
            owner.markDirty();
        }
    }

    boolean setFlow(EnumFacing side, @Nullable Flow next) {
        Flow previous = flows.get(side);
        if (previous == null ? next == null : previous.sameState(next)) return false;
        if (next == null) flows.remove(side);
        else flows.put(side, next);
        boolean changed = previous == null || next == null || previous.inbound != next.inbound
                || !previous.fluid.isFluidEqual(next.fluid) || previous.complete() != next.complete()
                || Float.compare(previous.speed, next.speed) != 0;
        if (changed) owner.markDirty();
        return changed;
    }

    void tickClient() {
        for (Map.Entry<EnumFacing, Flow> entry : flows.entrySet()) {
            Flow flow = entry.getValue();
            entry.setValue(new Flow(flow.fluid, flow.inbound,
                    Math.min(1.0F, flow.progress + flow.speed), flow.speed, flow.progress));
            spawnLeakParticles(entry.getKey(), flow);
        }
    }

    private void spawnLeakParticles(EnumFacing side, Flow flow) {
        World world = owner.getWorld();
        if (world == null || !world.isRemote || !isExposed(side)
                || flow.fluid == null || flow.fluid.amount <= 0) return;
        if (world.rand.nextInt(flow.inbound ? 10 : 5) != 0) return;

        BlockPos source = owner.getPos();
        Vec3d normal = new Vec3d(side.getDirectionVec());
        double radius = (1D / 4D + 1D / 64D) * .75D;
        Vec3d offset = new Vec3d(
                side.getAxis() == EnumFacing.Axis.X ? 0 : (world.rand.nextDouble() * 2 - 1) * radius,
                side.getAxis() == EnumFacing.Axis.Y ? 0 : (world.rand.nextDouble() * 2 - 1) * radius,
                side.getAxis() == EnumFacing.Axis.Z ? 0 : (world.rand.nextDouble() * 2 - 1) * radius)
                .add(normal.scale(.5D + world.rand.nextDouble() / 4D));
        Vec3d motion = offset.scale(.25D);
        if (flow.inbound) {
            offset = offset.add(motion);
            motion = normal.scale(.5D).subtract(offset).scale(1D / 16D);
        }
        Vec3d center = new Vec3d(source.getX() + .5D, source.getY() + .5D - 1D / 16D,
                source.getZ() + .5D).add(offset);
        com.cogwork.cogworkbridge.CogworkBridge.proxy.spawnFluidParticle(world, flow.fluid,
                center.x, center.y, center.z, motion.x, motion.y, motion.z);
    }

    private boolean isExposed(EnumFacing side) {
        World world = owner.getWorld();
        BlockPos target = owner.getPos().offset(side);
        if (world == null || !world.isBlockLoaded(target)) return false;
        IBlockState state = world.getBlockState(target);
        Block block = state.getBlock();
        if (!block.isReplaceable(world, target) && !(block instanceof BlockLiquid)
                && !(block instanceof IFluidBlock) && block != Blocks.CAULDRON) return false;
        if (state.getBlock() instanceof FluidPipeBlock || state.getBlock() instanceof FluidValveBlock
                || state.getBlock() instanceof MechanicalPumpBlock || state.getBlock() instanceof HosePulleyBlock
                || state.getBlock() instanceof PortableFluidInterfaceBlock) return false;
        TileEntity tile = world.getTileEntity(target);
        return tile == null || !tile.hasCapability(
                net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                side.getOpposite());
    }

    void write(NBTTagCompound tag) {
        NBTTagCompound stored = new NBTTagCompound();
        for (Map.Entry<EnumFacing, OpenEnd> entry : ends.entrySet()) {
            if (entry.getValue().buffer.getFluidAmount() > 0) {
                stored.setTag(entry.getKey().getName(),
                        entry.getValue().buffer.writeToNBT(new NBTTagCompound()));
            }
        }
        tag.setTag("OpenEnds", stored);
        NBTTagCompound flowData = new NBTTagCompound();
        for (Map.Entry<EnumFacing, Flow> entry : flows.entrySet()) {
            Flow flow = entry.getValue();
            NBTTagCompound encoded = new NBTTagCompound();
            encoded.setTag("Fluid", flow.fluid.writeToNBT(new NBTTagCompound()));
            encoded.setBoolean("Inbound", flow.inbound);
            encoded.setFloat("Progress", flow.progress);
            encoded.setFloat("Speed", flow.speed);
            flowData.setTag(entry.getKey().getName(), encoded);
        }
        tag.setTag("PipeFlows", flowData);
    }

    void read(NBTTagCompound tag) {
        ends.clear();
        flows.clear();
        if (tag.hasKey("OpenEnds", 10)) {
            NBTTagCompound stored = tag.getCompoundTag("OpenEnds");
            for (EnumFacing side : EnumFacing.values()) {
                if (!stored.hasKey(side.getName(), 10)) continue;
                OpenEnd end = new OpenEnd(side);
                end.buffer.readFromNBT(stored.getCompoundTag(side.getName()));
                ends.put(side, end);
            }
        }
        if (tag.hasKey("PipeFlows", 10)) {
            NBTTagCompound stored = tag.getCompoundTag("PipeFlows");
            for (EnumFacing side : EnumFacing.values()) {
                if (!stored.hasKey(side.getName(), 10)) continue;
                NBTTagCompound encoded = stored.getCompoundTag(side.getName());
                FluidStack fluid = FluidStack.loadFluidStackFromNBT(encoded.getCompoundTag("Fluid"));
                if (fluid != null && fluid.amount > 0) {
                    flows.put(side, new Flow(fluid, encoded.getBoolean("Inbound"),
                            Math.max(0.0F, Math.min(1.0F, encoded.getFloat("Progress"))),
                            Math.max(0.0F, Math.min(1.0F, encoded.getFloat("Speed")))));
                }
            }
        }
    }

    static final class Flow {
        final FluidStack fluid;
        final boolean inbound;
        final float progress;
        final float previousProgress;
        final float speed;

        Flow(FluidStack fluid, boolean inbound, float progress, float speed) {
            this(fluid, inbound, progress, speed, progress);
        }

        private Flow(FluidStack fluid, boolean inbound, float progress, float speed, float previousProgress) {
            this.fluid = fluid.copy();
            this.fluid.amount = 1;
            this.inbound = inbound;
            this.progress = progress;
            this.previousProgress = previousProgress;
            this.speed = speed;
        }

        boolean complete() { return progress >= 1.0F; }

        boolean sameState(@Nullable Flow other) {
            return other != null && inbound == other.inbound && Float.compare(progress, other.progress) == 0
                    && Float.compare(speed, other.speed) == 0 && fluid.isFluidEqual(other.fluid);
        }
    }

    static void reactWithWorld(IFluidHandler handler, FluidStack incoming) {
        if (handler instanceof OpenEndStorage.OpenEnd end) end.react(incoming);
    }

    private final class OpenEnd implements IFluidHandler {
        private final EnumFacing side;
        private final FluidTank buffer = new FluidTank(BUCKET);
        private long lastVirtualEffectTick = Long.MIN_VALUE;

        private OpenEnd(EnumFacing side) { this.side = side; }

        private BlockPos target() { return owner.getPos().offset(side); }

        private boolean canAccess() {
            World world = owner.getWorld();
            if (world == null || !world.isBlockLoaded(target())) return false;
            if (buffer.getFluidAmount() > 0) return true;
            IBlockState state = world.getBlockState(target());
            return state.getBlock() == Blocks.CAULDRON || sourceFluid(false) != null
                    || state.getBlock().isReplaceable(world, target());
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            FluidStack contents = buffer.getFluid();
            if (contents == null) contents = sourceFluid(false);
            return new IFluidTankProperties[]{new FluidTankProperties(contents, BUCKET, true, true)};
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (resource == null || resource.amount <= 0 || !canPlace(resource)) return 0;
            World world = owner.getWorld();
            FluidStack buffered = buffer.getFluid();
            boolean replaceBuffered = buffered != null && !buffered.isFluidEqual(resource);
            if (resource.getFluid().getBlock() == null) {
                // Virtual effect fluids are applied at one millibucket per tick, as in Create.
                if (!OpenPipeEffects.isVirtualEffectFluid(resource) || buffer.getFluidAmount() > 0 && !replaceBuffered
                        || lastVirtualEffectTick == world.getTotalWorldTime()) return 0;
                if (doFill) {
                    if (replaceBuffered) {
                        buffer.setFluid(null);
                        owner.markDirty();
                    }
                    lastVirtualEffectTick = world.getTotalWorldTime();
                    OpenPipeEffects.apply(world, target(), side, resource);
                }
                return 1;
            }
            // The upstream open end discards its previous fluid when flow switches
            // to a different fluid. Simulation must leave that buffer unchanged.
            int accepted = replaceBuffered ? Math.min(BUCKET, resource.amount) : buffer.fill(resource, false);
            if (doFill && accepted > 0) {
                if (replaceBuffered) buffer.setFluid(null);
                accepted = buffer.fill(resource, true);
                OpenPipeEffects.apply(world, target(), side, resource);
                owner.markDirty();
                if (buffer.getFluidAmount() == BUCKET && place(buffer.getFluid())) {
                    buffer.drain(BUCKET, true);
                    owner.markDirty();
                }
            }
            return accepted;
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || resource.amount <= 0) return null;
            FluidStack available = buffer.getFluid();
            if (available == null) available = sourceFluid(false);
            if (available == null || !available.isFluidEqual(resource)) return null;
            return drain(resource.amount, doDrain);
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            if (maxDrain <= 0) return null;
            FluidStack buffered = buffer.drain(maxDrain, doDrain);
            if (buffered != null && buffered.amount > 0) {
                if (doDrain) owner.markDirty();
                return buffered;
            }
            FluidStack source = sourceFluid(false);
            if (source == null || source.amount <= 0 || source.amount > BUCKET) return null;
            if (!doDrain) {
                source.amount = Math.min(maxDrain, source.amount);
                return source;
            }
            source = sourceFluid(true);
            if (source == null || source.amount <= 0) return null;
            if (source.getFluid() == FluidRegistry.WATER) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(owner.getWorld(), owner.getPos(), "water_supply");
            if (source.getFluid().getName().equals("honey")) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(owner.getWorld(), owner.getPos(), "honey_drain");
            buffer.fill(source, true);
            FluidStack extracted = buffer.drain(maxDrain, true);
            owner.markDirty();
            return extracted;
        }

        @Nullable
        private FluidStack sourceFluid(boolean drain) {
            World world = owner.getWorld();
            BlockPos target = target();
            if (world == null || !world.isBlockLoaded(target)) return null;
            IBlockState state = world.getBlockState(target);
            Block block = state.getBlock();
            if (block instanceof xy177.farmersfuturedelight.common.block.BlockBeehive
                    && world.getTileEntity(target) instanceof xy177.farmersfuturedelight.common.tile.TileEntityBeehive hive) {
                net.minecraftforge.fluids.Fluid honey = net.minecraftforge.fluids.FluidRegistry.getFluid("honey");
                if (honey == null || hive.getHoneyLevel() < 5) return null;
                if (drain) {
                    hive.setHoneyLevel(0);
                    com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, owner.getPos(), "honey_drain");
                }
                return new FluidStack(honey, 250);
            }
            if (block == Blocks.CAULDRON) {
                if (state.getValue(BlockCauldron.LEVEL) != 3) return null;
                if (drain && !world.setBlockState(target, state.withProperty(BlockCauldron.LEVEL, 0), 3)) return null;
                return new FluidStack(FluidRegistry.WATER, BUCKET);
            }
            if (block instanceof IFluidBlock fluidBlock) {
                return fluidBlock.canDrain(world, target) ? fluidBlock.drain(world, target, drain) : null;
            }
            Fluid fluid = WorldFluidHelper.fluidOf(state);
            if (fluid == null || state.getPropertyKeys().contains(BlockLiquid.LEVEL)
                    && state.getValue(BlockLiquid.LEVEL) != 0) return null;
            if (drain && !world.setBlockState(target, Blocks.AIR.getDefaultState(), 3)) return null;
            return new FluidStack(fluid, BUCKET);
        }

        private boolean canPlace(FluidStack resource) {
            World world = owner.getWorld();
            BlockPos target = target();
            if (world == null || !world.isBlockLoaded(target)) return false;
            if (resource.getFluid().getBlock() == null && !OpenPipeEffects.isVirtualEffectFluid(resource)) return false;
            IBlockState state = world.getBlockState(target);
            Fluid existing = WorldFluidHelper.fluidOf(state);
            if (existing != null && existing != resource.getFluid()) return false;
            return sourceFluid(false) == null && state.getBlock().isReplaceable(world, target);
        }

        private boolean place(FluidStack resource) {
            if (!canPlace(resource)) return false;
            World world = owner.getWorld();
            if (!FluidConfig.pipesPlaceFluidSourceBlocks) return true;
            if (resource.getFluid() == FluidRegistry.WATER && world.provider.doesWaterVaporize()) {
                world.playEvent(1501, target(), 0);
                return true;
            }
            return world.setBlockState(target(), resource.getFluid().getBlock().getDefaultState(), 3);
        }

        private void react(FluidStack incoming) {
            World world = owner.getWorld();
            BlockPos target = target();
            if (world == null || world.isRemote || incoming == null || !world.isBlockLoaded(target)) return;
            IBlockState state = world.getBlockState(target);
            Fluid existing = WorldFluidHelper.fluidOf(state);
            boolean source = !state.getPropertyKeys().contains(BlockLiquid.LEVEL)
                    || state.getValue(BlockLiquid.LEVEL) == 0;
            IBlockState result;
            if (incoming.getFluid() == FluidRegistry.WATER && existing == FluidRegistry.LAVA) {
                result = (source ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).getDefaultState();
            } else if (incoming.getFluid() == FluidRegistry.LAVA && existing == FluidRegistry.WATER) {
                result = (source ? Blocks.STONE : Blocks.COBBLESTONE).getDefaultState();
            } else return;
            if (world.setBlockState(target, result, 3)) {
                com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, owner.getPos(), "cross_streams");
                world.playEvent(1501, target, 0);
            }
        }
    }
}
