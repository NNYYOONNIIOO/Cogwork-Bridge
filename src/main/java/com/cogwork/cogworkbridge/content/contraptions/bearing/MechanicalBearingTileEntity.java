package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticNetwork;
import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencedGearshiftTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencerInstruction;
import com.cogwork.cogworkbridge.content.contraptions.transform.ContraptionTransform;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * A bearing consumes rotation from its back face and spins its front assembly.
 *
 * <p>For the first playable build the "contraption" is a single rotating sail
 * plate rendered by {@link MechanicalBearingRenderer}; real multi-block assembly
 * lands in Phase 3.
 */
public class MechanicalBearingTileEntity extends KineticTileEntity {
    public static final float ASSEMBLED_STRESS_IMPACT = 4.0F;

    private static final int LOADED_CONTRAPTION_RECOVERY_TICKS = 5;

    private boolean assembled;
    private boolean awaitingUpdate;
    private float angleDegrees;
    private float previousAngleDegrees;
    private float clientAngleDiff;
    private int contraptionEntityId = -1;
    private UUID contraptionEntityUuid;
    private BearingContraption savedContraption;
    private boolean assembleRequested;
    private boolean disassembleRequested;
    private boolean manualDisassemblyRequested;
    private boolean contraptionStalled;
    private boolean automaticCarriedAssembly;
    private boolean automaticReleasePending;
    private float observedSpeed;
    private RotationMode rotationMode = RotationMode.ROTATE_PLACE;
    private int loadedContraptionRecoveryTicks;
    private boolean sequenceAngleActive;
    private long sequenceSourcePos = Long.MIN_VALUE;
    private long sequenceId;
    private double sequenceAngleRemaining;

    public enum RotationMode {
        ROTATE_PLACE,
        ROTATE_PLACE_RETURNED,
        ROTATE_NEVER_PLACE;

        RotationMode next() {
            RotationMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public RotationMode getRotationMode() {
        return rotationMode;
    }

    public void setRotationMode(RotationMode mode) {
        if (mode == null || rotationMode == mode) {
            return;
        }
        rotationMode = mode;
        markDirty();
        sync();
    }

    public boolean isAssembled() {
        return assembled;
    }

    public boolean isAwaitingUpdate() {
        return awaitingUpdate;
    }

    public void toggleAwaitingUpdate() {
        if (awaitingUpdate) {
            manualDisassemblyRequested = false;
            setAwaitingUpdate(false);
            if (!assembled && getSpeed() != 0.0F) {
                requestAssemble();
            }
            return;
        }
        if (assembled && getSpeed() != 0.0F) {
            manualDisassemblyRequested = true;
            setAwaitingUpdate(true);
        } else if (assembled) {
            requestDisassemble();
        } else {
            requestAssemble();
        }
    }

    private void setAwaitingUpdate(boolean awaiting) {
        if (awaitingUpdate == awaiting) {
            return;
        }
        awaitingUpdate = awaiting;
        markDirty();
        sync();
    }

    public void requestAssemble() {
        this.assembleRequested = true;
    }

    public void requestDisassemble() {
        this.disassembleRequested = true;
    }

    public void onContraptionEmptied() {
        if (world == null || world.isRemote) {
            return;
        }
        contraptionEntityId = -1;
        contraptionEntityUuid = null;
        savedContraption = null;
        angleDegrees = 0.0F;
        previousAngleDegrees = 0.0F;
        contraptionStalled = false;
        awaitingUpdate = false;
        applyAssembledState(false);
    }

    public void syncSavedContraption(BearingContraption contraption) {
        if (world == null || world.isRemote) {
            return;
        }
        savedContraption = contraption;
        markDirty();
    }

    /**
     * Parent contraptions own nested bearing simulation. These accessors keep
     * the normal world-side bearing path unchanged while allowing the parent
     * entity to read the tile entity's authoritative saved structure.
     */
    public BearingContraption carriedContraptionSnapshot() {
        return savedContraption;
    }

    public int getCarriedContraptionEntityId() {
        return contraptionEntityId;
    }

    public UUID getCarriedContraptionEntityUuid() {
        return contraptionEntityUuid;
    }

    public void bindCarriedContraptionEntity(
            BearingContraptionEntity entity) {
        if (world == null || world.isRemote || entity == null) {
            return;
        }
        contraptionEntityId = entity.getEntityId();
        contraptionEntityUuid = entity.getUniqueID();
        markDirty();
    }

    public boolean isCarriedAssembled() {
        return assembled;
    }

    public float getCarriedAngleDegrees() {
        return angleDegrees;
    }

    public float getCarriedPreviousAngleDegrees() {
        return previousAngleDegrees;
    }

    public void setCarriedRotation(float carriedAngle, float carriedSpeed,
                                   boolean stalled) {
        boolean stalledChanged = contraptionStalled != stalled;
        previousAngleDegrees = angleDegrees;
        angleDegrees = normalize(carriedAngle);
        contraptionStalled = stalled;
        clientAngleDiff = 0.0F;
        setContraptionRenderPhase(angleDegrees);
        if (stalledChanged || !(world instanceof FakeWorld)) {
            markDirty();
        }
    }

    void releaseAutomaticCarriedAssembly() {
        if (automaticCarriedAssembly) {
            automaticCarriedAssembly = false;
            automaticReleasePending = true;
            markDirty();
        }
    }

    private void applyAssembledState(boolean value) {
        if (assembled == value) {
            return;
        }
        this.assembled = value;
        if (!value) {
            awaitingUpdate = false;
            manualDisassemblyRequested = false;
            automaticCarriedAssembly = false;
            automaticReleasePending = false;
        }
        this.angleDegrees = 0.0F;
        this.previousAngleDegrees = 0.0F;
        onAssemblyChanged();
        markDirty();
        sync();
    }

    protected void onAssemblyChanged() { }
    protected boolean canStartAssembly() { return getSpeed() != 0.0F; }
    protected boolean acceptsAssembly(BearingContraption contraption) { return true; }

    @Override
    public boolean isSource() {
        return false;
    }

    public EnumFacing getFacing() {
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof MechanicalBearingBlock && state.getPropertyKeys().contains(MechanicalBearingBlock.FACING)) {
            return state.getValue(MechanicalBearingBlock.FACING);
        }
        return EnumFacing.NORTH;
    }

    @Override
    public float getStressImpact() {
        return assembled ? ASSEMBLED_STRESS_IMPACT : 0.0F;
    }

    @Override
    public void update() {
        previousAngleDegrees = angleDegrees;
        super.update();
        if (world == null) {
            return;
        }
        if (world instanceof FakeWorld) {
            // A bearing carried by a contraption may contribute its local
            // kinetics, but it must never search for or control the outer
            // contraption entity. FakeWorld has no entity chunk provider.
            return;
        }
        if (world.isRemote) {
            clientAngleDiff *= 0.5F;
            if (assembled && !awaitingUpdate && !contraptionStalled
                    && getSpeed() != 0.0F) {
                double delta = ContraptionTransform.clientTickDelta(getSpeed(), clientAngleDiff);
                if (sequenceAngleActive) {
                    delta = Math.copySign(Math.min(Math.abs(delta), sequenceAngleRemaining), delta);
                    sequenceAngleRemaining = Math.max(0.0D, sequenceAngleRemaining - Math.abs(delta));
                }
                angleDegrees = normalize(angleDegrees + (float) delta);
            }
            return;
        }
        float currentSpeed = getSpeed();
        updateSequenceAngleLimit(currentSpeed);
        boolean firstObservation = Float.isNaN(observedSpeed);
        boolean speedChanged = !firstObservation
                && Float.compare(observedSpeed, currentSpeed) != 0;
        if (firstObservation && !assembled && currentSpeed != 0.0F) {
            setAwaitingUpdate(true);
        } else if (speedChanged) {
            if (awaitingUpdate) {
                manualDisassemblyRequested = false;
                setAwaitingUpdate(false);
            }
            if (!assembled && currentSpeed != 0.0F) {
                requestAssemble();
            } else if (assembled && currentSpeed == 0.0F && canPlaceOnStop()) {
                requestDisassemble();
            }
        }
        observedSpeed = currentSpeed;
        if (automaticReleasePending) {
            disassembleRequested = true;
        }
        if (assembleRequested) {
            assembleRequested = false;
            assemble();
            if (!assembled && getSpeed() != 0.0F) {
                setAwaitingUpdate(true);
            }
        }
        if (disassembleRequested || manualDisassemblyRequested) {
            boolean systemRequest = disassembleRequested;
            disassembleRequested = false;
            boolean waitForUpdate = awaitingUpdate;
            if (disassemble()) {
                if (waitForUpdate && getSpeed() != 0.0F) {
                    setAwaitingUpdate(true);
                }
            } else if (systemRequest) {
                disassembleRequested = true;
            }
        }
        if (!assembled) {
            return;
        }
        float speed = getSpeed();
        BearingContraptionEntity contraptionEntity =
                attachedContraptionEntity();
        if (contraptionEntity == null) {
            recoverLoadedContraption();
            contraptionEntity = attachedContraptionEntity();
        }
        if (contraptionEntity == null) {
            return;
        }
        float effectiveSpeed = awaitingUpdate ? 0.0F : speed;
        if (sequenceAngleActive) {
            effectiveSpeed = (float) Math.copySign(
                    Math.min(Math.abs(effectiveSpeed), sequenceAngleRemaining / 0.3D), effectiveSpeed);
        }
        contraptionEntity.setSpeed(effectiveSpeed);
        contraptionStalled = contraptionEntity.isStalled();
        float angleBeforeAdvance = angleDegrees;
        float requestedAngle = (float) ContraptionTransform.advance(
                angleDegrees, effectiveSpeed);
        if (contraptionEntity.advanceToward(requestedAngle, effectiveSpeed)) {
            contraptionStalled = false;
            angleDegrees = contraptionEntity.getAngle();
        } else {
            contraptionStalled = true;
            angleDegrees = contraptionEntity.getAngle();
        }
        if (sequenceAngleActive) {
            double oldRemaining = sequenceAngleRemaining;
            sequenceAngleRemaining = Math.max(0.0D, sequenceAngleRemaining - Math.abs(
                    ContraptionTransform.shortestDelta(angleBeforeAdvance, angleDegrees)));
            if (oldRemaining != sequenceAngleRemaining) {
                markDirty();
                if (sequenceAngleRemaining <= 1.0E-4D) {
                    sequenceAngleRemaining = 0.0D;
                    sync();
                }
            }
        }
        contraptionEntity.sendPoseToTrackingPlayers();
        if (world.getTotalWorldTime() % 10L == 0L) {
            sync();
        }
    }

    /** Follow the resolved source chain to the nearest sequence-producing gearbox. */
    private void updateSequenceAngleLimit(float speed) {
        boolean found = false;
        boolean changed = false;
        if (speed != 0.0F) {
            KineticNetwork network = KineticNetworkManager.networkFor(this);
            BlockPos cursor = pos;
            for (int remaining = network.getPredecessors().size() + 1; remaining > 0; remaining--) {
                BlockPos predecessor = network.getPredecessors().get(cursor);
                if (predecessor == null) {
                    break;
                }
                net.minecraft.tileentity.TileEntity tile = world.getTileEntity(predecessor);
                if (tile instanceof com.cogwork.cogworkbridge.content.kinetics.crank.ValveHandleTileEntity valve && valve.isTurning()) {
                    float sourceSpeed = valve.generatedSpeed();
                    if (sourceSpeed != 0.0F) {
                        found = true;
                        long source = predecessor.toLong();
                        if (source != sequenceSourcePos || sequenceId != valve.getSequenceId()) {
                            sequenceSourcePos = source;
                            sequenceId = valve.getSequenceId();
                            sequenceAngleRemaining = Math.abs(valve.getTargetDegrees() * speed / sourceSpeed);
                            changed = true;
                        }
                    }
                    break;
                }
                if (tile instanceof SequencedGearshiftTileEntity sequencer) {
                    EnumFacing input = sequencer.getInputFace();
                    SequencerInstruction instruction = sequencer.getActiveInstruction();
                    float outputSpeed = sequencer.getSpeed() * sequencer.getOutputModifier();
                    if (input != null && predecessor.offset(input.getOpposite()).equals(cursor)
                            && instruction != null && instruction.type() == SequencerInstruction.Type.TURN_ANGLE
                            && outputSpeed != 0.0F) {
                        found = true;
                        long source = predecessor.toLong();
                        if (source != sequenceSourcePos || sequenceId != sequencer.getSequenceId()) {
                            sequenceSourcePos = source;
                            sequenceId = sequencer.getSequenceId();
                            sequenceAngleRemaining = instruction.value() * Math.abs(speed / outputSpeed);
                            changed = true;
                        }
                    }
                    break;
                }
                cursor = predecessor;
            }
        }
        if (sequenceAngleActive != found || changed) {
            sequenceAngleActive = found;
            markDirty();
            sync();
        }
    }

    private boolean canPlaceOnStop() {
        return switch (rotationMode) {
            case ROTATE_PLACE -> true;
            case ROTATE_PLACE_RETURNED ->
                    Math.abs(ContraptionTransform.shortestDelta(angleDegrees, 0.0D)) < 22.5D;
            case ROTATE_NEVER_PLACE -> false;
        };
    }

    private void recoverLoadedContraption() {
        if (!assembled || savedContraption == null) {
            return;
        }
        if (loadedContraptionRecoveryTicks > 0) {
            loadedContraptionRecoveryTicks--;
            return;
        }
        if (!world.isBlockLoaded(pos)) {
            return;
        }
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof MechanicalBearingBlock)) {
            return;
        }
        BearingContraptionEntity existing = attachedContraptionEntity();
        if (existing != null) {
            rememberContraptionEntity(existing);
            return;
        }
        loadedContraptionRecoveryTicks = LOADED_CONTRAPTION_RECOVERY_TICKS;
        savedContraption.syncAnchor(pos, getFacing());
        BearingContraptionEntity entity = new BearingContraptionEntity(
                world, pos, getFacing(), savedContraption);
        entity.setSpeed(awaitingUpdate ? 0.0F : getSpeed());
        entity.setAngle(angleDegrees);
        world.spawnEntity(entity);
        rememberContraptionEntity(entity);
        entity.sendSnapshotToTrackingPlayers();
    }

    private void assemble() {
        if (assembled || world == null || world.isRemote || !canStartAssembly()) {
            return;
        }
        EnumFacing facing = getFacing();
        BlockPos anchor = pos.offset(facing);
        java.util.function.Consumer<java.util.Set<BlockPos>> closeCapturedGuis = capturedPositions ->
                        ContraptionGuiProxy.closeCapturedBlock(
                                world.getMinecraftServer(),
                                capturedPositions,
                                world.provider.getDimension());
        BearingContraption contraption = BearingContraption.capture(
                world, pos, anchor, closeCapturedGuis);
        if (contraption == null || !acceptsAssembly(contraption)) {
            return;
        }
        CarriedBearingAssembly carried = CarriedBearingAssembly.prepare(
                world, contraption, closeCapturedGuis);
        BearingContraptionEntity entity = new BearingContraptionEntity(world, pos, facing, contraption);
        entity.prepareNestedChildren();
        savedContraption = contraption;
        carried.removeFromWorld();
        com.cogwork.cogworkbridge.content.decoration.SeatBlock.transferOccupants(entity, contraption);
        contraption.removeFromWorld(world, true);
        entity.setSpeed(getSpeed());
        entity.beginRotation();
        world.spawnEntity(entity);
        rememberContraptionEntity(entity);
        entity.sendSnapshotToTrackingPlayers();
        entity.sendNestedSnapshots();
        angleDegrees = 0.0F;
        applyAssembledState(true);
    }

    private boolean disassemble() {
        if (!assembled) {
            return true;
        }
        BearingContraptionEntity entity = attachedContraptionEntity();
        if (entity != null) {
            entity.restoreAndDiscard();
            if (!entity.isDead) {
                return false;
            }
        } else if (savedContraption != null && contraptionEntityUuid != null) {
            return false;
        } else if (savedContraption != null) {
            savedContraption.restore(world, pos.offset(getFacing()), getFacing().getAxis(),
                    (float) ContraptionTransform.quarterTurn(angleDegrees));
        }
        contraptionEntityId = -1;
        contraptionEntityUuid = null;
        savedContraption = null;
        angleDegrees = 0.0F;
        applyAssembledState(false);
        return true;
    }

    /** Restores blocks before the bearing block and its TileEntity are removed. */
    public boolean emergencyDisassemble() {
        if (world == null || world.isRemote) {
            return false;
        }
        if (assembled) {
            return disassemble();
        }
        BearingContraptionEntity entity = attachedContraptionEntity();
        if (entity != null) {
            entity.restoreAndDiscard();
            if (!entity.isDead) {
                return false;
            }
            contraptionEntityId = -1;
        } else if (savedContraption != null && contraptionEntityUuid != null) {
            return false;
        } else if (savedContraption != null) {
            savedContraption.restore(world, pos.offset(getFacing()), getFacing().getAxis(),
                    (float) ContraptionTransform.quarterTurn(angleDegrees));
        } else {
            return true;
        }
        savedContraption = null;
        angleDegrees = 0.0F;
        markDirty();
        return true;
    }

    private BearingContraptionEntity attachedContraptionEntity() {
        if (world == null || world instanceof FakeWorld) {
            return null;
        }
        net.minecraft.entity.Entity entity = world.getEntityByID(contraptionEntityId);
        if (entity instanceof BearingContraptionEntity candidate
                && candidate.getBearingPos().equals(pos)
                && candidate.hasContraptionData()) {
            rememberContraptionEntity(candidate);
            return candidate;
        }
        if (contraptionEntityUuid != null) {
            for (BearingContraptionEntity candidate : world.getEntitiesWithinAABB(
                    BearingContraptionEntity.class,
                    new net.minecraft.util.math.AxisAlignedBB(pos).grow(64.0D))) {
                if (candidate.getUniqueID().equals(contraptionEntityUuid)
                        && candidate.getBearingPos().equals(pos)
                        && candidate.hasContraptionData()) {
                    rememberContraptionEntity(candidate);
                    return candidate;
                }
            }
        }
        for (BearingContraptionEntity candidate : world.getEntitiesWithinAABB(
                BearingContraptionEntity.class,
                new net.minecraft.util.math.AxisAlignedBB(pos).grow(64.0D))) {
            if (candidate.getBearingPos().equals(pos) && candidate.hasContraptionData()) {
                rememberContraptionEntity(candidate);
                return candidate;
            }
        }
        return null;
    }

    private void rememberContraptionEntity(BearingContraptionEntity entity) {
        contraptionEntityId = entity.getEntityId();
        contraptionEntityUuid = entity.getUniqueID();
        entity.markOwnedByBearing();
    }

    public float getRenderAngle(float partialTicks) {
        if (world != null && world.isRemote && assembled
                && !awaitingUpdate && !contraptionStalled
                && getSpeed() != 0.0F) {
            if (sequenceAngleActive) {
                return (float) ContraptionTransform.interpolate(previousAngleDegrees, angleDegrees, partialTicks);
            }
            return (float) ContraptionTransform.interpolateClientTick(
                    previousAngleDegrees,
                    ContraptionTransform.clientTickDelta(getSpeed(), clientAngleDiff),
                    partialTicks);
        }
        return angleDegrees;
    }

    public int getContraptionEntityId() {
        return contraptionEntityId;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("Assembled", assembled);
        compound.setBoolean("AwaitingUpdate", awaitingUpdate);
        compound.setBoolean("ManualDisassemblyRequested", manualDisassemblyRequested);
        compound.setFloat("Angle", angleDegrees);
        compound.setInteger("ContraptionEntity", contraptionEntityId);
        if (contraptionEntityUuid != null) {
            compound.setUniqueId("ContraptionEntityUuid", contraptionEntityUuid);
        }
        compound.setBoolean("ContraptionStalled", contraptionStalled);
        compound.setBoolean("AutomaticCarriedAssembly", automaticCarriedAssembly);
        compound.setBoolean("AutomaticReleasePending", automaticReleasePending);
        compound.setLong("SequenceSourcePos", sequenceSourcePos);
        compound.setLong("SequenceId", sequenceId);
        compound.setDouble("SequenceAngleRemaining", sequenceAngleRemaining);
        compound.setString("RotationMode", rotationMode.name());
        if (savedContraption != null) {
            compound.setTag("SavedContraption", savedContraption.writeToNBT(new NBTTagCompound()));
        }
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        observedSpeed = Float.NaN;
        assembled = compound.getBoolean("Assembled");
        awaitingUpdate = compound.getBoolean("AwaitingUpdate");
        manualDisassemblyRequested = assembled && awaitingUpdate
                && compound.getBoolean("ManualDisassemblyRequested");
        angleDegrees = compound.getFloat("Angle");
        contraptionEntityId = compound.getInteger("ContraptionEntity");
        contraptionEntityUuid = compound.hasUniqueId("ContraptionEntityUuid")
                ? compound.getUniqueId("ContraptionEntityUuid") : null;
        contraptionStalled = compound.getBoolean("ContraptionStalled");
        automaticCarriedAssembly = compound.getBoolean("AutomaticCarriedAssembly");
        automaticReleasePending = compound.getBoolean("AutomaticReleasePending");
        sequenceSourcePos = compound.hasKey("SequenceSourcePos")
                ? compound.getLong("SequenceSourcePos") : Long.MIN_VALUE;
        sequenceId = compound.getLong("SequenceId");
        sequenceAngleRemaining = Math.max(0.0D, compound.getDouble("SequenceAngleRemaining"));
        sequenceAngleActive = false;
        readRotationMode(compound);
        if (compound.hasKey("SavedContraption")) {
            savedContraption = BearingContraption.readFromNBT(compound.getCompoundTag("SavedContraption"));
        }
        loadedContraptionRecoveryTicks = assembled && savedContraption != null
                ? LOADED_CONTRAPTION_RECOVERY_TICKS : 0;
    }

    @Override
    public void validate() {
        super.validate();
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setBoolean("Assembled", assembled);
        tag.setBoolean("AwaitingUpdate", awaitingUpdate);
        tag.setFloat("Angle", angleDegrees);
        tag.setInteger("ContraptionEntity", contraptionEntityId);
        tag.setBoolean("ContraptionStalled", contraptionStalled);
        tag.setBoolean("SequenceAngleActive", sequenceAngleActive);
        tag.setDouble("SequenceAngleRemaining", sequenceAngleRemaining);
        tag.setString("RotationMode", rotationMode.name());
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        assembled = tag.getBoolean("Assembled");
        awaitingUpdate = tag.getBoolean("AwaitingUpdate");
        contraptionEntityId = tag.getInteger("ContraptionEntity");
        contraptionStalled = tag.getBoolean("ContraptionStalled");
        sequenceAngleActive = tag.getBoolean("SequenceAngleActive");
        sequenceAngleRemaining = Math.max(0.0D, tag.getDouble("SequenceAngleRemaining"));
        readRotationMode(tag);
        float syncedAngle = tag.getFloat("Angle");
        if (world != null && world.isRemote && assembled
                && !awaitingUpdate && !contraptionStalled) {
            clientAngleDiff = (float) ContraptionTransform.shortestDelta(
                    angleDegrees, syncedAngle);
        } else {
            clientAngleDiff = 0.0F;
            previousAngleDegrees = syncedAngle;
            angleDegrees = syncedAngle;
        }
    }

    private void readRotationMode(NBTTagCompound tag) {
        if (!tag.hasKey("RotationMode")) {
            return;
        }
        for (RotationMode mode : RotationMode.values()) {
            if (mode.name().equals(tag.getString("RotationMode"))) {
                rotationMode = mode;
                return;
            }
        }
    }

    private static float normalize(float angle) {
        return (float) ContraptionTransform.normalize(angle);
    }
}
