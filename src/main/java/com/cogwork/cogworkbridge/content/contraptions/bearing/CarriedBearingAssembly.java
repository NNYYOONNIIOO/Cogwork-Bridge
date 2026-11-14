package com.cogwork.cogworkbridge.content.contraptions.bearing;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Plans unpowered bearing children before any of their world blocks are removed. */
public final class CarriedBearingAssembly {
    private final World world;
    private final Consumer<Set<BlockPos>> beforeSnapshot;
    private final Set<BlockPos> claimed = new LinkedHashSet<>();
    private final List<BearingContraption> children = new ArrayList<>();

    private CarriedBearingAssembly(World world, Consumer<Set<BlockPos>> beforeSnapshot) {
        this.world = world;
        this.beforeSnapshot = beforeSnapshot;
    }

    public static CarriedBearingAssembly prepare(World world, BearingContraption parent,
                                          Consumer<Set<BlockPos>> beforeSnapshot) {
        CarriedBearingAssembly assembly = new CarriedBearingAssembly(world, beforeSnapshot);
        assembly.claimed.addAll(parent.blocks().keySet());
        assembly.collect(parent, 0);
        return assembly;
    }

    private void collect(BearingContraption parent, int depth) {
        if (depth >= NestedBearingManager.MAX_DEPTH) {
            return;
        }
        for (BearingContraption.Snapshot snapshot :
                new ArrayList<>(parent.blocks().values())) {
            IBlockState state = snapshot.state();
            NBTTagCompound tile = snapshot.tileData();
            if (!(state.getBlock() instanceof MechanicalBearingBlock)
                    || !state.getPropertyKeys().contains(MechanicalBearingBlock.FACING)
                    || tile == null || tile.getBoolean("Assembled")) {
                continue;
            }
            EnumFacing facing = state.getValue(MechanicalBearingBlock.FACING);
            BearingContraption child = BearingContraption.capture(world, snapshot.pos(),
                    snapshot.pos().offset(facing), claimed, beforeSnapshot);
            if (child == null) {
                continue;
            }
            claimed.addAll(child.blocks().keySet());
            children.add(child);
            collect(child, depth + 1);
            child.preserveControllerGlue(world, snapshot.pos());
            tile.setBoolean("Assembled", true);
            tile.setBoolean("AutomaticCarriedAssembly", true);
            tile.setFloat("Angle", 0.0F);
            tile.setInteger("ContraptionEntity", -1);
            tile.removeTag("ContraptionEntityUuidMost");
            tile.removeTag("ContraptionEntityUuidLeast");
            tile.setTag("SavedContraption", child.writeToNBT(new NBTTagCompound()));
            parent.persistBlockData(snapshot.pos().subtract(parent.anchor()), state, tile);
        }
    }

    public void removeFromWorld() {
        for (BearingContraption child : children) {
            child.removeFromWorld(world, true);
        }
    }

    public Set<BlockPos> claimedPositions() {
        return Set.copyOf(claimed);
    }
}
