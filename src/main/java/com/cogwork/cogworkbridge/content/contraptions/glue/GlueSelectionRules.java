package com.cogwork.cogworkbridge.content.contraptions.glue;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.LinkedHashSet;
import java.util.Set;

/** Server-side validation used before a glue area is created. */
public final class GlueSelectionRules {

    private GlueSelectionRules() {
    }

    public static Set<GridPos> connectedGroup(World world, BlockPos first, BlockPos second) {
        if (!GlueBox.withinSelectionDistance(grid(first), grid(second))) {
            return java.util.Collections.emptySet();
        }
        GlueBox box = GlueBox.span(grid(first), grid(second));
        return box.connectedGroup(grid(first), grid(second),
                pos -> isMovable(world.getBlockState(
                        new BlockPos(pos.x(), pos.y(), pos.z()))));
    }

    /**
     * Mirrors which cell {@code ItemBlock} will actually replace. A clicked
     * replaceable cell uses that cell; otherwise the targeted adjacent cell
     * is the only candidate.
     */
    public static BlockPos placementTarget(World world, BlockPos clicked,
                                           net.minecraft.util.EnumFacing face) {
        if (world == null || clicked == null || face == null) {
            return null;
        }
        IBlockState clickedState = world.getBlockState(clicked);
        if (clickedState.getBlock().isReplaceable(world, clicked)) {
            return clicked;
        }
        return clicked.offset(face);
    }

    /**
     * Returns the stable server-side owner for a cell. Empty cells in the
     * geometric box select an existing area whose range legally contains them;
     * occupied cells only count when already captured as a rigid member.
     */
    public static Set<SuperGlueEntity> areasAffecting(World world,
                                                       BlockPos blockPos) {
        GridPos grid = grid(blockPos);
        Set<SuperGlueEntity> result = new LinkedHashSet<>();
        for (SuperGlueEntity glue : loadedAreas(world)) {
            if (glue.isEntityAlive()
                    && glue.getGlueBox().contains(grid)) {
                result.add(glue);
            }
        }
        return result;
    }

    public static java.util.List<SuperGlueEntity> loadedAreas(World world) {
        return world.loadedEntityList.stream()
                .filter(SuperGlueEntity.class::isInstance)
                .map(SuperGlueEntity.class::cast)
                .filter(SuperGlueEntity::isEntityAlive)
                .toList();
    }

    public static boolean isMovable(IBlockState state) {
        return !state.getBlock().isAir(state, null, null)
                && state.getBlock().getMaterial(state).blocksMovement();
    }

    public static boolean isReplaceable(World world, BlockPos pos) {
        return world != null && pos != null
                && world.getBlockState(pos).getBlock().isReplaceable(world, pos);
    }

    public static boolean canAfford(boolean creative, int damage, int maxDamage) {
        return creative || damage < maxDamage;
    }

    public static GridPos grid(BlockPos pos) {
        return new GridPos(pos.getX(), pos.getY(), pos.getZ());
    }
}
