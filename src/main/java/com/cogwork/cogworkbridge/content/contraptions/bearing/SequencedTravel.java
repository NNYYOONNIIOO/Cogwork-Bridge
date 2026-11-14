package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencedGearshiftTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencerInstruction;
import com.cogwork.cogworkbridge.kinetic.KineticNetwork;
import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/** Distance budget inherited from a sequenced gearshift on the source path. */
public final class SequencedTravel {
    private BlockPos sequencer;
    private long sequenceId = Long.MIN_VALUE;
    private double remaining = -1D;

    public float limit(KineticTileEntity target, float requested) {
        if (target.getWorld() == null || requested == 0F) return requested;
        KineticNetwork network = KineticNetworkManager.networkFor(target);
        BlockPos child = target.getPos();
        Set<BlockPos> visited = new HashSet<>();
        while (visited.add(child)) {
            BlockPos parent = network.getPredecessors().get(child);
            if (parent == null || !target.getWorld().isBlockLoaded(parent)) break;
            TileEntity tile = target.getWorld().getTileEntity(parent);
            if (tile instanceof SequencedGearshiftTileEntity gearshift) {
                SequencerInstruction instruction = gearshift.getActiveInstruction();
                EnumFacing input = gearshift.getInputFace();
                if (instruction != null && instruction.type() == SequencerInstruction.Type.TURN_DISTANCE
                        && input != null && parent.offset(input.getOpposite()).equals(child)) {
                    float output = gearshift.getSpeed() * gearshift.getOutputModifier();
                    if (output == 0F) return 0F;
                    if (!parent.equals(sequencer) || sequenceId != gearshift.getSequenceId()) {
                        sequencer = parent.toImmutable();
                        sequenceId = gearshift.getSequenceId();
                        remaining = Math.max(0D, instruction.value() - gearshift.getInstructionProgress())
                                * Math.abs(target.getSpeed() / output);
                    }
                    return (float) Math.copySign(Math.min(Math.abs(requested), remaining), requested);
                }
            }
            child = parent;
        }
        sequencer = null; sequenceId = Long.MIN_VALUE; remaining = -1D;
        return requested;
    }
    public void moved(double distance) {
        if (remaining >= 0D) remaining = Math.max(0D, remaining - Math.abs(distance));
    }
    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        if (sequencer != null) tag.setLong("Source", sequencer.toLong());
        tag.setLong("SequenceId", sequenceId);
        tag.setDouble("Remaining", remaining);
        return tag;
    }
    public void read(NBTTagCompound tag) {
        sequencer = tag.hasKey("Source") ? BlockPos.fromLong(tag.getLong("Source")) : null;
        sequenceId = tag.hasKey("SequenceId") ? tag.getLong("SequenceId") : Long.MIN_VALUE;
        remaining = tag.hasKey("Remaining") ? tag.getDouble("Remaining") : -1D;
    }
}
