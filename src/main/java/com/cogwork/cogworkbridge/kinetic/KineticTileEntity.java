package com.cogwork.cogworkbridge.kinetic;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;

/**
 * Base tile entity for kinetic blocks.
 *
 * <p>The tile entity owns everything metadata cannot encode: current speed, the
 * stress contribution of this block, and (for generators) the source rotation.
 * Neighbour propagation is intentionally simple for the first playable build:
 * every kinetic tile entity polls its six neighbours once per tick and adopts the
 * rotation of the fastest moving neighbour that drives this block's axis.
 */
public abstract class KineticTileEntity extends CogworkTileEntity implements ITickable, NetworkParticipant {
    private final com.cogwork.cogworkbridge.content.decoration.palette.BracketAttachment bracket = new com.cogwork.cogworkbridge.content.decoration.palette.BracketAttachment();
    public com.cogwork.cogworkbridge.content.decoration.palette.BracketAttachment bracket() { return bracket; }

    /**
     * Current rotation. It is part of the save payload because captured tiles
     * are deserialized outside a live neighbour graph inside contraptions.
     */
    protected Rotation rotation = Rotation.NONE;

    /** Set by a generator each tick; brokers keep it across ticks. */
    protected Rotation sourceRotation = Rotation.NONE;

    /** Visual phase accumulator, persisted so the client does not restart at 0. */
    protected float phaseDegrees;

    /**
     * Optional visual phase supplied by a moving structure renderer.
     *
     * <p>Mechanical bearing heads use this as a structure-clock override.
     * Ordinary kinetic tiles leave it null and advance from their saved phase
     * and speed, including while captured.</p>
     */
    private Float contraptionRenderPhase;
    private net.minecraft.block.state.IBlockState contraptionRenderState;
    private BlockPos contraptionRenderPos;
    private long contraptionRenderPhaseTime = Long.MIN_VALUE;

    protected boolean needsSync;
    protected float networkStress;
    protected float networkCapacity;
    protected boolean overstressed;

    public Rotation getRotation() {
        return rotation;
    }

    public float getSpeed() {
        return overstressed ? 0.0F : rotation.getSpeed();
    }

    public boolean isSource() {
        return false;
    }

    /** How much stress this block consumes while moving, in SU. */
    public float getStressImpact() {
        return 0.0F;
    }

    /** How much stress this block can supply, in SU. Zero for consumers. */
    public float getStressCapacity() {
        return 0.0F;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        tickKinetics();
    }

    protected void tickKinetics() {
        KineticNetwork network = KineticNetworkManager.networkFor(this);
        applyNetwork(network);
        com.cogwork.cogworkbridge.content.advancement.AllAdvancements.checkPoweredMachine(this);
        if (rotation.isMoving()) {
            phaseDegrees = (float) ((phaseDegrees + rotation.getSpeed() * 0.3F) % 360.0F);
        }
    }

    /** Forces a fresh neighbour scan after placement and movement transformations. */
    public void refreshKinetics() {
        if (world == null || world.isRemote) {
            return;
        }
        rotation = Rotation.NONE;
        overstressed = false;
        needsSync = true;
        markDirty();
        sync();
    }

    protected Rotation computeSourceRotation() {
        return sourceRotation;
    }

    private void applyNetwork(KineticNetwork network) {
        boolean nextOverstressed = network.isOverstressed();
        Rotation next = Rotation.of(network.axisAt(this), network.speedAt(this));
        if (!sameRotation(rotation, next)) {
            rotation = next;
            markDirty();
            needsSync = true;
        }
        if (Float.compare(networkStress, network.getStress()) != 0
                || Float.compare(networkCapacity, network.getCapacity()) != 0
                || overstressed != nextOverstressed) {
            networkStress = network.getStress();
            networkCapacity = network.getCapacity();
            overstressed = nextOverstressed;
            markDirty();
            needsSync = true;
        }
        if (needsSync) {
            needsSync = false;
            sync();
        }
    }

    /**
     * Whether rotation may travel through the given face. Shafts only connect along
     * their own axis; cogwheels connect sideways; generators connect on all faces.
     */
    protected boolean acceptsConnection(EnumFacing facing) {
        return facing.getAxis() == getAxis();
    }

    /** Mechanical gears may mesh with perpendicular rotating blocks. */
    protected boolean isGear() {
        return false;
    }


    /** Continuous render phase in degrees, interpolated by the partial tick. */
    public float getRenderAngle(float partialTicks) {
        float angle = phaseDegrees + getSpeed() * 0.3F * partialTicks;
        return angle % 360.0F;
    }

    @Override
    public BlockPos getNetworkPos() {
        return pos;
    }

    @Override
    public java.util.List<NetworkParticipant> kineticNeighbours() {
        java.util.ArrayList<NetworkParticipant> out = new java.util.ArrayList<>();
        if (world == null) {
            return out;
        }
        for (BlockPos candidatePos : KineticPropagator.candidatePositions(this)) {
            if (!world.isBlockLoaded(candidatePos)) {
                continue;
            }
            TileEntity tile = world.getTileEntity(candidatePos);
            if (!(tile instanceof NetworkParticipant)) {
                continue;
            }
            NetworkParticipant candidate = (NetworkParticipant) tile;
            if (candidate instanceof KineticTileEntity
                    && KineticPropagator.speedModifier(this, (KineticTileEntity) candidate) != 0.0D) {
                out.add(candidate);
            }
        }
        return out;
    }


    @Override
    public void applyNetworkSpeed(float speed, EnumFacing.Axis sourceAxis) {
        Rotation next = Rotation.of(getAxis(), speed);
        if (!sameRotation(rotation, next)) {
            rotation = next;
            needsSync = true;
        }
    }

    @Override
    public double signTo(NetworkParticipant next) {
        return next instanceof KineticTileEntity
                ? KineticPropagator.speedModifier(this, (KineticTileEntity) next) : 0.0D;
    }

    @Override
    public double speedModifierTo(NetworkParticipant next) {
        return signTo(next);
    }

    @Override
    public float propagatedSpeedTo(NetworkParticipant next, float inputSpeed) {
        if (next instanceof KineticTileEntity other
                && KineticPropagator.isSpeedControllerGearLink(this, other)) {
            com.cogwork.cogworkbridge.content.kinetics.speedController.SpeedControllerTileEntity controller =
                    this instanceof com.cogwork.cogworkbridge.content.kinetics.speedController.SpeedControllerTileEntity
                            ? (com.cogwork.cogworkbridge.content.kinetics.speedController.SpeedControllerTileEntity) this
                            : (com.cogwork.cogworkbridge.content.kinetics.speedController.SpeedControllerTileEntity) other;
            return inputSpeed == 0.0F ? 0.0F : controller.getTargetSpeed();
        }
        return NetworkParticipant.super.propagatedSpeedTo(next, inputSpeed);
    }

    @Override
    public boolean isNetworkSource() {
        return isSource();
    }

    @Override
    public float generatedSpeed() {
        return computeSourceRotation().getSpeed();
    }

    @Override
    public EnumFacing.Axis generatedAxis() {
        return getAxis();
    }

    @Override
    public EnumFacing.Axis axis() {
        return getAxis();
    }

    @Override
    public float stressForNetwork() {
        if (world instanceof com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld fakeWorld
                && fakeWorld.getMobileChunk().getEntityMovingWorld() instanceof
                com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity) {
            return 0.0F;
        }
        // The previous tick's overstress flag must not erase this tick's demand.
        return getStressImpact() * Math.abs(rotation.getSpeed());
    }

    @Override
    public float stressCapacity() {
        return getStressCapacity();
    }

    @Override
    public void breakForOverspeed() {
        if (world == null || world.isRemote || pos == null) {
            return;
        }
        if (world instanceof com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld) {
            world.setBlockToAir(pos);
        } else {
            world.destroyBlock(pos, true);
        }
    }

    @Override
    public boolean canConnectKinetics(EnumFacing thisSide, NetworkParticipant source, EnumFacing sourceSide) {
        if (!(source instanceof KineticTileEntity)) {
            return false;
        }
        KineticTileEntity other = (KineticTileEntity) source;
        boolean adjacentFaces = thisSide.getAxis() == sourceSide.getAxis();
        EnumFacing.Axis ownAxis = getAxis();
        EnumFacing.Axis otherAxis = other.getAxis();

        boolean axisConnection = adjacentFaces
                && ownAxis == otherAxis
                && thisSide.getAxis() == ownAxis
                && sourceSide.getAxis() == otherAxis;
        if (axisConnection) {
            return true;
        }

        boolean ownGear = isGear();
        boolean otherGear = other.isGear();
        if (!ownGear && !otherGear) {
            return false;
        }
        if (!adjacentFaces) {
            return false;
        }
        if (ownGear && otherGear) {
            return ownAxis != otherAxis
                    && thisSide.getAxis() != ownAxis
                    && thisSide.getAxis() != otherAxis;
        }
        if (ownGear) {
            return otherAxis != ownAxis
                    && thisSide.getAxis() != ownAxis
                    && sourceSide.getAxis() == ownAxis;
        }
        return ownAxis != otherAxis
                && sourceSide.getAxis() == otherAxis
                && thisSide.getAxis() == otherAxis;
    }

    public float getNetworkStress() {
        return networkStress;
    }

    public float getNetworkCapacity() {
        return networkCapacity;
    }

    public boolean isOverstressed() {
        return overstressed;
    }

    public Float getContraptionRenderPhase() {
        return contraptionRenderPhase;
    }

    public float getPhaseDegrees() {
        return phaseDegrees;
    }

    /** Local kinetic phase used by a virtual contraption renderer. */
    public float getLocalRenderPhase(float partialTicks) {
        double elapsed = partialTicks;
        if (world != null && world.isRemote && contraptionRenderPos != null
                && contraptionRenderPhaseTime != Long.MIN_VALUE) {
            elapsed += Math.max(0L,
                    world.getTotalWorldTime() - contraptionRenderPhaseTime);
        }
        return (float) ((phaseDegrees + elapsed * getSpeed() * 0.3F) % 360.0F);
    }

    public void setContraptionRenderPhase(Float degrees) {
        contraptionRenderPhase = degrees == null ? null : degrees % 360.0F;
    }

    public net.minecraft.block.state.IBlockState getContraptionRenderState() {
        return contraptionRenderState;
    }

    public BlockPos getContraptionRenderPos() {
        return contraptionRenderPos;
    }

    public void setContraptionRenderContext(
            net.minecraft.block.state.IBlockState state, BlockPos localPos) {
        contraptionRenderState = state;
        contraptionRenderPos = localPos == null ? null : localPos.toImmutable();
        if (world != null && world.isRemote && contraptionRenderPos != null) {
            contraptionRenderPhaseTime = world.getTotalWorldTime();
        } else {
            contraptionRenderPhaseTime = Long.MIN_VALUE;
        }
    }

    protected EnumFacing.Axis getAxis() {
        if (world != null && pos != null) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof KineticBlock block) {
                return block.getAxis(state);
            }
        }
        return EnumFacing.Axis.Y;
    }

    protected static boolean sameRotation(Rotation a, Rotation b) {
        return a.getAxis() == b.getAxis() && Float.compare(a.getSpeed(), b.getSpeed()) == 0;
    }

    protected void sync() {
        if (world instanceof com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld fakeWorld) {
            if (fakeWorld.getMobileChunk() != null) {
                fakeWorld.getMobileChunk().markTileDirty(pos);
            }
        } else if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setFloat("Phase", phaseDegrees);
        compound.setFloat("KineticSpeed", rotation.getSpeed());
        compound.setByte("KineticAxis", (byte) rotation.getAxis().ordinal());
        compound.setFloat("NetworkStress", networkStress);
        compound.setFloat("NetworkCapacity", networkCapacity);
        compound.setBoolean("Overstressed", overstressed);
        bracket.write(compound);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        phaseDegrees = compound.getFloat("Phase");
        EnumFacing.Axis[] axes = EnumFacing.Axis.values();
        int axis = compound.hasKey("KineticAxis")
                ? compound.getByte("KineticAxis") : EnumFacing.Axis.Y.ordinal();
        rotation = Rotation.of(axes[Math.floorMod(axis, axes.length)],
                compound.hasKey("KineticSpeed")
                        ? compound.getFloat("KineticSpeed") : 0.0F);
        networkStress = compound.getFloat("NetworkStress");
        networkCapacity = compound.getFloat("NetworkCapacity");
        overstressed = compound.getBoolean("Overstressed");
        bracket.read(compound);
        if (world != null && world.isRemote && contraptionRenderPos != null) {
            contraptionRenderPhaseTime = world.getTotalWorldTime();
        }
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setFloat("Phase", phaseDegrees);
        tag.setFloat("Speed", rotation.getSpeed());
        tag.setByte("Axis", (byte) rotation.getAxis().ordinal());
        tag.setFloat("NetworkStress", networkStress);
        tag.setFloat("NetworkCapacity", networkCapacity);
        tag.setBoolean("Overstressed", overstressed);
        bracket.write(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        phaseDegrees = tag.getFloat("Phase");
        EnumFacing.Axis[] axes = EnumFacing.Axis.values();
        int index = tag.getByte("Axis");
        rotation = Rotation.of(axes[Math.floorMod(index, axes.length)], tag.getFloat("Speed"));
        networkStress = tag.getFloat("NetworkStress");
        networkCapacity = tag.getFloat("NetworkCapacity");
        overstressed = tag.getBoolean("Overstressed");
        bracket.read(tag);
    }

    /** Applies a carried tile's saved state through its normal client update path. */
    public void handleContraptionUpdate(NBTTagCompound saved) {
        NBTTagCompound update = saved.copy();
        update.setFloat("Speed", saved.getFloat("KineticSpeed"));
        update.setByte("Axis", saved.hasKey("KineticAxis")
                ? saved.getByte("KineticAxis") : (byte) EnumFacing.Axis.Y.ordinal());
        handleUpdateTag(update);
        if (world != null && world.isRemote && contraptionRenderPos != null) {
            contraptionRenderPhaseTime = world.getTotalWorldTime();
        }
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        handleUpdateTag(packet.getNbtCompound());
    }
}
