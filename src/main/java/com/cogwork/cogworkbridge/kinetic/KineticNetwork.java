package com.cogwork.cogworkbridge.kinetic;

import com.cogwork.cogworkbridge.content.kinetics.speedController.SpeedControllerTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable result of one server-side kinetic connected-component pass. */
public final class KineticNetwork {
    private static final float MAX_ROTATION_SPEED = 256.0F;

    private final Set<BlockPos> positions;
    private final Map<BlockPos, BlockPos> predecessors;
    private final Map<BlockPos, Float> memberSpeeds;
    private final Map<BlockPos, EnumFacing.Axis> memberAxes;
    private final float stress;
    private final float capacity;
    private final boolean overstressed;

    private KineticNetwork(Set<BlockPos> positions,
                           Map<BlockPos, BlockPos> predecessors,
                           float speed,
                           Map<BlockPos, Float> memberSpeeds,
                           Map<BlockPos, EnumFacing.Axis> memberAxes,
                           float stress, float capacity) {
        this.positions = Collections.unmodifiableSet(new HashSet<>(positions));
        this.predecessors = Collections.unmodifiableMap(new HashMap<>(predecessors));
        this.memberSpeeds = Collections.unmodifiableMap(new HashMap<>(memberSpeeds));
        this.memberAxes = Collections.unmodifiableMap(new HashMap<>(memberAxes));
        this.stress = stress;
        this.capacity = capacity;
        this.overstressed = speed != 0.0F && capacity < stress;
    }

    public static Agent agent() {
        return new Agent();
    }

    public Set<BlockPos> getPositions() {
        return positions;
    }

    public Map<BlockPos, BlockPos> getPredecessors() {
        return predecessors;
    }

    public float speedAt(NetworkParticipant member) {
        return memberSpeeds.getOrDefault(member.getNetworkPos(), 0.0F);
    }

    public EnumFacing.Axis axisAt(NetworkParticipant member) {
        return memberAxes.getOrDefault(member.getNetworkPos(), member.axis());
    }

    public float getStress() {
        return stress;
    }

    public float getCapacity() {
        return capacity;
    }

    public boolean isOverstressed() {
        return overstressed;
    }

    /** Shortest-path visit order from a source; every member sees a moving predecessor first. */
    public static final class Agent {

        private Agent() { }

        public KineticNetwork resolve(NetworkParticipant root) {
            return resolve(root, new HashSet<>());
        }

        private KineticNetwork resolve(NetworkParticipant root, Set<BlockPos> attemptedRoots) {
            attemptedRoots.add(root.getNetworkPos());
            Deque<NetworkParticipant> queue = new ArrayDeque<>();
            Set<BlockPos> positions = new HashSet<>();
            boolean hasSource = false;
            List<NetworkParticipant> members = new ArrayList<>();
            Map<BlockPos, Double> signs = new HashMap<>();
            Map<BlockPos, BlockPos> predecessors = new HashMap<>();
            Map<BlockPos, Float> memberSpeeds = new HashMap<>();
            Map<BlockPos, EnumFacing.Axis> memberAxes = new HashMap<>();
            queue.add(root);
            positions.add(root.getNetworkPos());
            signs.put(root.getNetworkPos(), 1.0D);
            memberSpeeds.put(root.getNetworkPos(), root.isNetworkSource() ? root.generatedSpeed() : 0.0F);

            float totalCapacity = 0.0F;
            float totalStress = 0.0F;
            float sourceSpeed = 0.0F;
            NetworkParticipant overspeedSource = null;
            NetworkParticipant strongerSource = null;
            NetworkParticipant conflictGear = null;
            boolean sourceConflict = false;
            boolean loopConflict = false;

            while (!queue.isEmpty()) {
                NetworkParticipant member = queue.removeFirst();
                members.add(member);
                if (member.isNetworkSource()) {
                    hasSource = true;
                    float abs = Math.abs(member.generatedSpeed());
                    totalCapacity += member.stressCapacity() * abs;
                    if (abs > Math.abs(sourceSpeed)) {
                        sourceSpeed = member.generatedSpeed();
                    }
                }

                for (NetworkParticipant next : member.kineticNeighbours()) {
                    BlockPos nextPos = next.getNetworkPos();
                    double edgeModifier = member.speedModifierTo(next);
                    double modifier = signs.getOrDefault(member.getNetworkPos(), 1.0D)
                            * edgeModifier;
                    if (edgeModifier == 0.0D) {
                        continue;
                    }
                    float currentSpeed = memberSpeeds.getOrDefault(member.getNetworkPos(), 0.0F);
                    float nextSpeed = member.propagatedSpeedTo(next, currentSpeed);
                    if (positions.contains(nextPos)) {
                        BlockPos memberPos = member.getNetworkPos();
                        boolean treeEdge = nextPos.equals(predecessors.get(memberPos))
                                || memberPos.equals(predecessors.get(nextPos));
                        boolean conflicting = !treeEdge && (controllerLink(member, next)
                                ? controllerConflict(member, next, memberSpeeds)
                                : differentSpeed(nextSpeed, memberSpeeds.getOrDefault(nextPos, 0.0F)));
                        if (conflicting) {
                            loopConflict = true;
                            if (conflictGear == null) {
                                conflictGear = isCog(member) ? member : isCog(next) ? next : null;
                            }
                        }
                        continue;
                    }
                    if (Math.abs(nextSpeed) > MAX_ROTATION_SPEED) {
                        if (overspeedSource == null) {
                            overspeedSource = member;
                        }
                        continue;
                    }
                    if (next.isNetworkSource() && next.generatedSpeed() != 0.0F && nextSpeed != 0.0F) {
                        float generated = next.generatedSpeed();
                        if (Math.signum(generated) != Math.signum(nextSpeed)) {
                            sourceConflict = true;
                        } else if (Math.abs(generated) > Math.abs(nextSpeed) + speedTolerance(nextSpeed)
                                && !attemptedRoots.contains(nextPos)) {
                            strongerSource = next;
                        }
                    }
                    positions.add(nextPos);
                    predecessors.put(nextPos, member.getNetworkPos());
                    signs.put(nextPos, modifier);
                    memberSpeeds.put(nextPos, nextSpeed);
                    queue.addLast(next);
                }
            }

            if (!sourceConflict && strongerSource != null) {
                return resolve(strongerSource, attemptedRoots);
            }

            if (sourceConflict || loopConflict) {
                if (!sourceConflict && conflictGear != null) {
                    conflictGear.breakForOverspeed();
                }
                for (NetworkParticipant member : members) {
                    member.applyNetworkSpeed(0.0F, member.axis());
                    memberSpeeds.put(member.getNetworkPos(), 0.0F);
                    memberAxes.put(member.getNetworkPos(), member.axis());
                }
                return new KineticNetwork(positions, predecessors,
                        0.0F, memberSpeeds,
                        memberAxes, 0.0F, totalCapacity);
            }

            if (overspeedSource != null) {
                overspeedSource.breakForOverspeed();
                for (NetworkParticipant member : members) {
                    member.applyNetworkSpeed(0.0F, member.axis());
                    memberSpeeds.put(member.getNetworkPos(), 0.0F);
                    memberAxes.put(member.getNetworkPos(), member.axis());
                }
                return new KineticNetwork(positions, predecessors,
                        0.0F, memberSpeeds,
                        memberAxes, 0.0F, totalCapacity);
            }

            // A network with no generator is a broken source chain. Clear every
            // member; otherwise stale client/server speeds keep old contraptions
            // rotating after the motor or bearing input disappears.
            if (!hasSource) {
                for (NetworkParticipant member : members) {
                    member.applyNetworkSpeed(0.0F, member.axis());
                    memberSpeeds.put(member.getNetworkPos(), 0.0F);
                    memberAxes.put(member.getNetworkPos(), member.axis());
                    totalStress += member.stressForNetwork();
                }
                return new KineticNetwork(positions, predecessors,
                        0.0F, memberSpeeds,
                        memberAxes, totalStress, totalCapacity);
            }

            // Revisit after propagation so every member has the source's final speed.
            for (NetworkParticipant member : members) {
                float memberSpeed = memberSpeeds.getOrDefault(member.getNetworkPos(), 0.0F);
                EnumFacing.Axis memberAxis = member.isNetworkSource()
                        ? member.generatedAxis() : member.axis();
                memberSpeeds.put(member.getNetworkPos(), memberSpeed);
                memberAxes.put(member.getNetworkPos(), memberAxis);
                member.applyNetworkSpeed(memberSpeed, memberAxis);
                totalStress += member.stressForNetwork();
            }

            return new KineticNetwork(positions, predecessors, sourceSpeed, memberSpeeds, memberAxes, totalStress, totalCapacity);
        }

        private static float speedTolerance(float speed) {
            return Math.max(0.0001F, Math.abs(speed) / 65536.0F);
        }

        private static boolean differentSpeed(float expected, float actual) {
            return Math.abs(expected - actual) > Math.max(speedTolerance(expected), speedTolerance(actual));
        }

        private static boolean controllerLink(NetworkParticipant first, NetworkParticipant second) {
            return first instanceof KineticTileEntity from && second instanceof KineticTileEntity to
                    && KineticPropagator.isSpeedControllerGearLink(from, to);
        }

        private static boolean controllerConflict(NetworkParticipant first, NetworkParticipant second,
                                                  Map<BlockPos, Float> speeds) {
            SpeedControllerTileEntity controller = first instanceof SpeedControllerTileEntity
                    ? (SpeedControllerTileEntity) first : (SpeedControllerTileEntity) second;
            NetworkParticipant cog = first == controller ? second : first;
            float controllerSpeed = speeds.getOrDefault(controller.getNetworkPos(), 0.0F);
            float cogSpeed = speeds.getOrDefault(cog.getNetworkPos(), 0.0F);
            float target = controller.getTargetSpeed();
            if (controllerSpeed == 0.0F || target == 0.0F) {
                return false;
            }
            return (cogSpeed != 0.0F && Math.signum(target) != Math.signum(cogSpeed))
                    || Math.abs(target) > Math.abs(cogSpeed) + speedTolerance(cogSpeed);
        }

        private static boolean isCog(NetworkParticipant participant) {
            if (!(participant instanceof KineticTileEntity tile) || tile.getWorld() == null) return false;
            return tile.getWorld().getBlockState(tile.getPos()).getBlock()
                    instanceof com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelBlock;
        }

    }
}
