package com.cogwork.cogworkbridge.content.contraptions.chassis;

import com.cogwork.cogworkbridge.content.contraptions.actors.AttachedActorBlock;
import com.cogwork.cogworkbridge.content.contraptions.bearing.MechanicalBearingBlock;
import com.cogwork.cogworkbridge.content.contraptions.bearing.SailBlock;
import com.cogwork.cogworkbridge.content.contraptions.piston.PistonPoleBlock;
import com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillBlock;
import com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBasePressurePlate;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockRedstoneDiode;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.BlockSign;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/** Computes face connections contributed by one chassis to an assembly. */
public final class ChassisConnectivity {
    private ChassisConnectivity() { }

    public interface Access {
        IBlockState state(BlockPos pos);
        NBTTagCompound tileData(BlockPos pos);
        boolean terminal(BlockPos pos);
        default EnumFacing forcedMovement() { return null; }
    }

    public static void collect(Access access, BlockPos pos, BiConsumer<BlockPos, BlockPos> edge) {
        IBlockState state = access.state(pos);
        if (state == null || !(state.getBlock() instanceof ChassisBlock chassis)) return;
        NBTTagCompound data = access.tileData(pos);
        int range = data == null || !data.hasKey("Range") ? 8 : data.getInteger("Range");
        range = Math.max(1, Math.min(ChassisTileEntity.MAX_RANGE, range));
        int sticky = data == null ? 0 : data.getInteger("StickyMask");
        EnumFacing.Axis axis = state.getValue(ChassisBlock.AXIS);

        for (EnumFacing side : EnumFacing.VALUES) {
            BlockPos next = pos.offset(side);
            IBlockState neighbour = access.state(next);
            if (neighbour != null && neighbour.getBlock() == chassis
                    && neighbour.getValue(ChassisBlock.AXIS) == axis
                    && (!chassis.isRadial() || side.getAxis() == axis)) {
                edge.accept(pos, next);
            }
        }

        for (EnumFacing side : EnumFacing.VALUES) {
            if (!chassis.canGlue(state, side)) continue;
            boolean glued = (sticky & 1 << side.getIndex()) != 0;
            if (!glued && (chassis.isRadial() || access.forcedMovement() != side)) continue;
            if (chassis.isRadial()) collectRadial(access, pos, side, axis, range, edge);
            else {
                // The range belongs to this sticky face, not to each block
                // along it. Stop at the first gap or terminal block.
                BlockPos previous = pos;
                for (int distance = 1; distance <= range; distance++) {
                    BlockPos current = pos.offset(side, distance);
                    IBlockState currentState = access.state(current);
                    if (!movable(currentState) || brittle(currentState)) break;
                    edge.accept(previous, current);
                    if (access.terminal(current) || isNotSupportive(access, current, currentState, side)) break;
                    previous = current;
                }
            }
        }
    }

    private static void collectRadial(Access access, BlockPos origin, EnumFacing side, EnumFacing.Axis axis,
                                      int range, BiConsumer<BlockPos, BlockPos> edge) {
        BlockPos first = origin.offset(side);
        if (!movable(access.state(first)) || brittle(access.state(first))) return;
        edge.accept(origin, first);
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(first);
        double maxDistance = (range + .5D) * (range + .5D);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (!visited.add(current) || access.terminal(current)) continue;
            IBlockState currentState = access.state(current);
            for (EnumFacing direction : EnumFacing.VALUES) {
                if (direction.getAxis() == axis) continue;
                if (isNotSupportive(access, current, currentState, direction)) continue;
                BlockPos next = current.offset(direction);
                IBlockState nextState = access.state(next);
                if (next.equals(origin) || next.distanceSq(origin) >= maxDistance
                        || !movable(nextState) || brittle(nextState)) continue;
                edge.accept(current, next);
                if (!visited.contains(next)) queue.addLast(next);
            }
        }
    }

    private static boolean movable(IBlockState state) {
        return state != null && state.getMaterial().blocksMovement();
    }

    /** Mirrors the upstream movement checks relevant to chassis propagation. */
    public static boolean isNotSupportive(Access access, BlockPos pos, IBlockState state, EnumFacing side) {
        if (state == null) return true;
        Block block = state.getBlock();
        if (block instanceof MechanicalDrillBlock) return state.getValue(MechanicalDrillBlock.FACING) == side;
        if (block instanceof MechanicalSawBlock) return state.getValue(MechanicalSawBlock.FACING) == side;
        if (block instanceof MechanicalBearingBlock) return state.getValue(MechanicalBearingBlock.FACING) == side;
        if (block instanceof AttachedActorBlock && block != AllBlocks.MECHANICAL_ROLLER)
            return state.getValue(AttachedActorBlock.FACING) == side;
        if (block == AllBlocks.CART_ASSEMBLER || block == AllBlocks.ROPE_PULLEY) return side == EnumFacing.DOWN;
        if (block == AllBlocks.PORTABLE_STORAGE_INTERFACE)
            return state.getValue(com.cogwork.cogworkbridge.content.logistics.portable.PortableStorageInterfaceBlock.FACING) == side;
        if (block instanceof BlockCarpet) return side == EnumFacing.UP;
        if (block instanceof SailBlock) return side.getAxis() == state.getValue(SailBlock.FACING).getAxis();
        if (block instanceof PistonPoleBlock) return side.getAxis() != state.getValue(PistonPoleBlock.FACING).getAxis();
        if (block instanceof StickerBlock) {
            NBTTagCompound data = access.tileData(pos);
            return (data == null || !data.getBoolean("Extended")) && state.getValue(StickerBlock.FACING) == side;
        }
        return brittle(state);
    }

    private static boolean brittle(IBlockState state) {
        if (state == null) return false;
        Block block = state.getBlock();
        if (block == AllBlocks.CART_ASSEMBLER) return false;
        if (block instanceof BlockCarpet) return true;
        return block instanceof BlockLadder || block instanceof BlockTorch || block instanceof BlockSign
                || block instanceof BlockBasePressurePlate || block instanceof BlockRailBase
                || block instanceof BlockRedstoneDiode || block instanceof BlockRedstoneWire;
    }
}
