package com.cogwork.cogworkbridge.content.kinetics.sequencer;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.kinetic.NetworkParticipant;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SequencedGearshiftTileEntity extends KineticTileEntity {
    private List<SequencerInstruction> instructions = defaultProgram();
    private int activeStep = -1;
    private double progress;
    private boolean previousPower;
    private boolean pendingStart;
    private EnumFacing inputFace;
    private float outputPhase;
    private long outputPhaseTime = Long.MIN_VALUE;
    private long sequenceId = java.util.concurrent.ThreadLocalRandom.current().nextLong();
    private int completionTicks;

    private static List<SequencerInstruction> defaultProgram() {
        List<SequencerInstruction> program = new ArrayList<>();
        program.add(SequencerInstruction.of(SequencerInstruction.Type.TURN_ANGLE));
        program.add(SequencerInstruction.of(SequencerInstruction.Type.END));
        return program;
    }

    public List<SequencerInstruction> getInstructions() {
        return new ArrayList<>(instructions);
    }

    public int getActiveStep() {
        return activeStep;
    }

    public EnumFacing getInputFace() {
        return inputFace;
    }

    public long getSequenceId() {
        return sequenceId;
    }
    public double getInstructionProgress() { return progress; }

    public SequencerInstruction getActiveInstruction() {
        return activeStep >= 0 && activeStep < instructions.size() ? instructions.get(activeStep) : null;
    }

    public int getOutputModifier() {
        if (activeStep < 0 || activeStep >= instructions.size()) {
            return 0;
        }
        SequencerInstruction instruction = instructions.get(activeStep);
        return instruction.type().moves() ? instruction.speed() : 0;
    }

    public float getOutputRenderAngle(float partialTicks) {
        double elapsed = partialTicks;
        if (world != null && world.isRemote && outputPhaseTime != Long.MIN_VALUE) {
            elapsed += Math.max(0L, world.getTotalWorldTime() - outputPhaseTime);
        }
        return (float) ((outputPhase + elapsed * getSpeed() * getOutputModifier() * 0.3D) % 360.0D);
    }

    public double portModifier(EnumFacing side) {
        if (inputFace == null) {
            return 0.0D;
        }
        return side == inputFace ? 1.0D : side == inputFace.getOpposite() ? getOutputModifier() : 0.0D;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        updateInputFace();
        super.update();
        outputPhase = (outputPhase + getSpeed() * getOutputModifier() * 0.3F) % 360.0F;

        boolean powered = world.isBlockPowered(pos);
        boolean rising = powered && !previousPower;
        if (powered != previousPower) {
            markDirty();
        }
        previousPower = powered;
        if (!powered) {
            pendingStart = false;
        }
        if (activeStep >= 0 && getSpeed() == 0.0F) {
            setStep(-1);
        }
        boolean changedStep = false;
        if (rising) {
            if (activeStep < 0) {
                pendingStart = true;
            } else if (instructions.get(activeStep).type() == SequencerInstruction.Type.AWAIT) {
                setStep(activeStep + 1);
                changedStep = true;
            }
        }
        if (pendingStart && powered && getSpeed() != 0.0F) {
            pendingStart = false;
            setStep(0);
            changedStep = true;
        }
        if (activeStep < 0 || changedStep) {
            return;
        }
        SequencerInstruction instruction = instructions.get(activeStep);
        double speed = Math.abs(getSpeed() * instruction.speed());
        progress += switch (instruction.type()) {
            case TURN_ANGLE -> speed * 0.3D;
            case TURN_DISTANCE -> speed / 512.0D;
            case DELAY -> 1.0D;
            case AWAIT, END -> 0.0D;
        };
        if (instruction.type() != SequencerInstruction.Type.AWAIT && progress >= instruction.value()) {
            if (instruction.type().moves() && completionTicks++ < 2) {
                markDirty();
                return;
            }
            setStep(activeStep + 1);
        } else if (progress > 0.0D) {
            markDirty();
        }
    }

    public void setInstructions(List<SequencerInstruction> updated) {
        if (world == null || world.isRemote) {
            return;
        }
        List<SequencerInstruction> bounded = new ArrayList<>();
        for (SequencerInstruction instruction : updated) {
            if (instruction == null || bounded.size() >= SequencerInstruction.MAX_STEPS) {
                break;
            }
            bounded.add(instruction);
            if (instruction.type() == SequencerInstruction.Type.END) {
                break;
            }
        }
        if (bounded.isEmpty()) {
            bounded.add(SequencerInstruction.of(SequencerInstruction.Type.END));
        }
        instructions = bounded;
        setStep(-1);
    }

    private void setStep(int next) {
        activeStep = next >= 0 && next < instructions.size()
                && instructions.get(next).type() != SequencerInstruction.Type.END ? next : -1;
        progress = 0.0D;
        completionTicks = 0;
        sequenceId++;
        KineticNetworkManager.discard(world);
        markDirty();
        sync();
        world.updateComparatorOutputLevel(pos, getBlockType());
    }

    /** Determine the source side without walking through this disconnected gearbox. */
    private void updateInputFace() {
        EnumFacing bestFace = null;
        float strongest = 0.0F;
        EnumFacing.Axis axis = getAxis();
        for (EnumFacing side : EnumFacing.VALUES) {
            if (side.getAxis() != axis) {
                continue;
            }
            BlockPos adjacentPos = pos.offset(side);
            if (!world.isBlockLoaded(adjacentPos)) {
                continue;
            }
            IBlockState adjacentState = world.getBlockState(adjacentPos);
            TileEntity adjacent = world.getTileEntity(adjacentPos);
            if (!(adjacent instanceof KineticTileEntity kinetic)
                    || !(adjacentState.getBlock() instanceof KineticBlock block)
                    || !block.canConnectShaftTowards(world, adjacentPos, adjacentState, side.getOpposite())
                    || block.shaftPortModifier(world, adjacentPos, adjacentState, side.getOpposite()) == 0.0D) {
                continue;
            }
            float candidate = strongestSourceWithoutThis(kinetic);
            if (candidate > strongest || candidate > 0.0F && candidate == strongest && side == inputFace) {
                strongest = candidate;
                bestFace = side;
            }
        }
        if (bestFace != inputFace) {
            inputFace = bestFace;
            KineticNetworkManager.discard(world);
            markDirty();
            sync();
        }
    }

    private float strongestSourceWithoutThis(KineticTileEntity first) {
        ArrayDeque<NetworkParticipant> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(first);
        visited.add(first.getPos());
        visited.add(pos);
        float strongest = 0.0F;
        while (!queue.isEmpty()) {
            NetworkParticipant member = queue.removeFirst();
            if (member.isNetworkSource()) {
                strongest = Math.max(strongest, Math.abs(member.generatedSpeed()));
            }
            for (NetworkParticipant neighbour : member.kineticNeighbours()) {
                if (visited.add(neighbour.getNetworkPos())) {
                    queue.addLast(neighbour);
                }
            }
        }
        return strongest;
    }

    private void writeSequence(NBTTagCompound tag) {
        NBTTagList program = new NBTTagList();
        for (SequencerInstruction instruction : instructions) {
            program.appendTag(instruction.write());
        }
        tag.setTag("Instructions", program);
        tag.setInteger("ActiveStep", activeStep);
        tag.setDouble("StepProgress", progress);
        tag.setBoolean("PreviousPower", previousPower);
        tag.setBoolean("PendingStart", pendingStart);
        tag.setInteger("InputFace", inputFace == null ? -1 : inputFace.getIndex());
        tag.setFloat("OutputPhase", outputPhase);
        tag.setLong("SequenceId", sequenceId);
        tag.setInteger("CompletionTicks", completionTicks);
    }

    private void readSequence(NBTTagCompound tag) {
        if (tag.hasKey("Instructions", 9)) {
            NBTTagList list = tag.getTagList("Instructions", 10);
            List<SequencerInstruction> program = new ArrayList<>();
            for (int i = 0; i < list.tagCount() && i < SequencerInstruction.MAX_STEPS; i++) {
                SequencerInstruction instruction = SequencerInstruction.read(list.getCompoundTagAt(i));
                program.add(instruction);
                if (instruction.type() == SequencerInstruction.Type.END) {
                    break;
                }
            }
            instructions = program.isEmpty() ? defaultProgram() : program;
        }
        int index = tag.hasKey("ActiveStep") ? tag.getInteger("ActiveStep") : -1;
        activeStep = index >= 0 && index < instructions.size()
                && instructions.get(index).type() != SequencerInstruction.Type.END ? index : -1;
        progress = Math.max(0.0D, tag.getDouble("StepProgress"));
        previousPower = tag.getBoolean("PreviousPower");
        pendingStart = tag.getBoolean("PendingStart");
        int face = tag.hasKey("InputFace") ? tag.getInteger("InputFace") : -1;
        inputFace = face >= 0 && face < EnumFacing.VALUES.length ? EnumFacing.byIndex(face) : null;
        outputPhase = tag.getFloat("OutputPhase");
        if (tag.hasKey("SequenceId")) {
            sequenceId = tag.getLong("SequenceId");
        }
        completionTicks = Math.max(0, Math.min(2, tag.getInteger("CompletionTicks")));
        if (world != null && world.isRemote) {
            outputPhaseTime = world.getTotalWorldTime();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        writeSequence(tag);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readSequence(tag);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        writeSequence(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        int oldStep = activeStep;
        super.handleUpdateTag(tag);
        readSequence(tag);
        if (oldStep != activeStep && world != null && world.isRemote) {
            if (world instanceof com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld fakeWorld) {
                fakeWorld.getMobileChunk().setChunkModified();
            } else {
                world.markBlockRangeForRenderUpdate(pos, pos);
            }
        }
    }
}
