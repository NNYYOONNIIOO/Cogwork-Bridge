package com.cogwork.cogworkbridge.content.kinetics.encased;

import com.cogwork.cogworkbridge.content.kinetics.chain.ChainDriveBlock;
import com.cogwork.cogworkbridge.content.kinetics.chain.ChainDriveTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.waterwheel.LargeWaterWheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.waterwheel.WaterWheelStructuralBlock;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltBlock;
import com.cogwork.cogworkbridge.content.fluids.FluidPipeBlock;
import com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeBlock;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The 1.12 orientation properties that can be selected in the wrench menu. */
public final class WrenchRotationOptions {
    private WrenchRotationOptions() { }

    public static List<IProperty<?>> properties(World world, BlockPos pos, IBlockState state) {
        List<IProperty<?>> result = new ArrayList<>();
        Block block = state.getBlock();
        // These structures have special wrench actions or occupy more than one block.
        if (block instanceof BeltBlock || block instanceof LargeWaterWheelBlock
                || block instanceof WaterWheelStructuralBlock
                || block instanceof com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorBlock
                || block instanceof com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock
                || block instanceof com.cogwork.cogworkbridge.content.contraptions.mounted.ControllerRailBlock
                || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.GirderShaftBlock
                || block instanceof com.cogwork.cogworkbridge.content.logistics.vault.ItemVaultBlock
                || block instanceof com.cogwork.cogworkbridge.content.fluids.FluidTankBlock
                || block instanceof com.cogwork.cogworkbridge.content.contraptions.actors.AttachedActorBlock
                || block instanceof com.cogwork.cogworkbridge.content.kinetics.fan.NozzleBlock
                || block instanceof FluidPipeBlock && !(block instanceof SmartFluidPipeBlock)) return result;
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (!isOrientation(property)) continue;
            if (states(world, pos, state, property).size() > 1) result.add(property);
        }
        result.sort(Comparator.comparingInt(WrenchRotationOptions::order).thenComparing(property -> property.getName()));
        return result;
    }

    private static int order(IProperty<?> property) {
        return switch (property.getName()) {
            case "facing", "horizontal_facing" -> 0;
            case "axis", "horizontal_axis" -> 1;
            case "target" -> 2;
            default -> 3;
        };
    }

    private static boolean isOrientation(IProperty<?> property) {
        String name = property.getName();
        Class<?> type = property.getValueClass();
        if (type == EnumFacing.class)
            return name.equals("facing") || name.equals("horizontal_facing") || name.equals("target");
        if (type == EnumFacing.Axis.class)
            return name.equals("axis") || name.equals("horizontal_axis") || name.equals("shaft_axis")
                    || name.equals("axis_along_first");
        if (type == Boolean.class) return name.equals("vertical") || name.equals("axis_along_first");
        return name.equals("target") && type == com.cogwork.cogworkbridge.content.redstone.DetectorBlock.Target.class;
    }

    public static List<IBlockState> states(World world, BlockPos pos, IBlockState state, IProperty<?> property) {
        return collectStates(world, pos, state, property);
    }

    private static <T extends Comparable<T>> List<IBlockState> collectStates(World world, BlockPos pos,
                                                                            IBlockState state, IProperty<T> property) {
        List<IBlockState> result = new ArrayList<>();
        for (T value : property.getAllowedValues()) {
            IBlockState candidate = state.withProperty(property, value);
            // The chosen orientation must survive a chunk save in the 1.12 metadata.
            try {
                IBlockState saved = state.getBlock().getStateFromMeta(state.getBlock().getMetaFromState(candidate));
                if (!saved.getPropertyKeys().contains(property) || !saved.getValue(property).equals(value)) continue;
            } catch (RuntimeException ignored) { continue; }
            if (state.getBlock() instanceof ChainDriveBlock && world.getTileEntity(pos) instanceof ChainDriveTileEntity chain) {
                boolean invalid = false;
                for (EnumFacing side : EnumFacing.VALUES) {
                    if (chain.connects(side) && candidate.getValue(KineticBlock.AXIS) == side.getAxis()) invalid = true;
                }
                if (invalid) continue;
            }
            result.add(candidate);
        }
        return result;
    }

    public static String valueName(IBlockState state, IProperty<?> property) {
        return nameValue(state, property);
    }
    private static <T extends Comparable<T>> String nameValue(IBlockState state, IProperty<T> property) {
        return property.getName(state.getValue(property));
    }
}
