package com.cogwork.cogworkbridge.content.kinetics.placement;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.state.IBlockState;

public record KineticPlacementPlan(BlockPos target, EnumFacing.Axis axis,
                                   boolean valid, boolean assisted,
                                   IBlockState previewState) {
}
