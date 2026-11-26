package com.cogwork.cogworkbridge.content.decoration;

import com.cogwork.cogworkbridge.content.contraptions.chassis.ChassisBlock;
import com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks;
import com.cogwork.cogworkbridge.content.fluids.FluidPipeTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.encased.EncasedCogwheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.encased.EncasedCogwheelTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.encased.EncasedShaftBlock;
import com.cogwork.cogworkbridge.content.kinetics.gearbox.GearboxBlock;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BeltTunnelBlock;
import com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterBlock;
import com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.IUnlistedProperty;

/** Neighbor information for the upstream connected-texture sheets. */
public final class ConnectedTextureData {
    public static final IUnlistedProperty<Long> CONTEXT = new IUnlistedProperty<Long>() {
        @Override public String getName() { return "connected_texture_context"; }
        @Override public boolean isValid(Long value) { return value != null; }
        @Override public Class<Long> getType() { return Long.class; }
        @Override public String valueToString(Long value) { return value.toString(); }
    };
    public static final int UP = 1, DOWN = 2, LEFT = 4, RIGHT = 8,
            TOP_LEFT = 16, TOP_RIGHT = 32, BOTTOM_LEFT = 64, BOTTOM_RIGHT = 128;
    // The six face contexts occupy the low 48 bits. Pane arms above and below
    // need separate coverage information for each half of a pane model.
    public static final int PANE_ABOVE_SHIFT = 48, PANE_BELOW_SHIFT = 53, PANE_CENTER = 16;

    private ConnectedTextureData() { }

    public static long capture(IBlockAccess world, BlockPos pos, IBlockState state) {
        long result = 0;
        for (EnumFacing face : EnumFacing.VALUES) {
            EnumFacing vertical = up(state, face), horizontal = right(state, face);
            if (face.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE) horizontal = horizontal.getOpposite();
            if (face == EnumFacing.DOWN) { vertical = vertical.getOpposite(); horizontal = horizontal.getOpposite(); }
            if (flipHorizontal(state, face)) horizontal = horizontal.getOpposite();
            if (flipVertical(state, face)) vertical = vertical.getOpposite();
            boolean u = connects(world, pos, pos.offset(vertical), state, face);
            boolean d = connects(world, pos, pos.offset(vertical.getOpposite()), state, face);
            boolean l = connects(world, pos, pos.offset(horizontal.getOpposite()), state, face);
            boolean r = connects(world, pos, pos.offset(horizontal), state, face);
            int bits = (u ? UP : 0) | (d ? DOWN : 0) | (l ? LEFT : 0) | (r ? RIGHT : 0);
            if (u && l && connects(world, pos, pos.offset(vertical).offset(horizontal.getOpposite()), state, face)) bits |= TOP_LEFT;
            if (u && r && connects(world, pos, pos.offset(vertical).offset(horizontal), state, face)) bits |= TOP_RIGHT;
            if (d && l && connects(world, pos, pos.offset(vertical.getOpposite()).offset(horizontal.getOpposite()), state, face)) bits |= BOTTOM_LEFT;
            if (d && r && connects(world, pos, pos.offset(vertical.getOpposite()).offset(horizontal), state, face)) bits |= BOTTOM_RIGHT;
            result |= (long) bits << (face.getIndex() * 8);
        }
        if (state.getBlock() instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Pane) {
            result |= (long) paneCoverage(world, pos.up(), state.getBlock()) << PANE_ABOVE_SHIFT;
            result |= (long) paneCoverage(world, pos.down(), state.getBlock()) << PANE_BELOW_SHIFT;
        }
        return result;
    }

    private static int paneCoverage(IBlockAccess world, BlockPos pos, Block block) {
        IBlockState other = world.getBlockState(pos);
        if (other.getBlock() != block) return 0;
        other = block.getActualState(other, world, pos);
        int mask = PANE_CENTER;
        if (other.getValue(net.minecraft.block.BlockPane.NORTH)) mask |= 1 << EnumFacing.NORTH.getHorizontalIndex();
        if (other.getValue(net.minecraft.block.BlockPane.SOUTH)) mask |= 1 << EnumFacing.SOUTH.getHorizontalIndex();
        if (other.getValue(net.minecraft.block.BlockPane.WEST)) mask |= 1 << EnumFacing.WEST.getHorizontalIndex();
        if (other.getValue(net.minecraft.block.BlockPane.EAST)) mask |= 1 << EnumFacing.EAST.getHorizontalIndex();
        return mask;
    }

    private static EnumFacing up(IBlockState state, EnumFacing face) {
        if (state.getBlock() == AllBlocks.BRASS_TUNNEL && face == EnumFacing.UP)
            return state.getValue(BeltTunnelBlock.AXIS) == EnumFacing.Axis.X ? EnumFacing.NORTH : EnumFacing.EAST;
        if (state.getBlock() instanceof ChassisBlock chassis && !chassis.isRadial()) {
            EnumFacing.Axis axis = state.getValue(ChassisBlock.AXIS);
            if (face.getAxis() != axis) return EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, axis);
        }
        if (state.getBlock() instanceof PaletteBlocks.Pillar) {
            EnumFacing.Axis axis = state.getValue(BlockRotatedPillar.AXIS);
            if (axis == EnumFacing.Axis.X && face.getAxis().isVertical()) return EnumFacing.EAST;
            if (axis.isHorizontal() && face.getAxis().isHorizontal() && face.getAxis() != axis)
                return EnumFacing.getFacingFromAxis(axis == EnumFacing.Axis.X
                        ? EnumFacing.AxisDirection.POSITIVE : EnumFacing.AxisDirection.NEGATIVE, axis);
        }
        return face.getAxis().isHorizontal() ? EnumFacing.UP : EnumFacing.NORTH;
    }

    private static EnumFacing right(IBlockState state, EnumFacing face) {
        if (state.getBlock() instanceof ChassisBlock chassis && !chassis.isRadial()) {
            EnumFacing.Axis axis = state.getValue(ChassisBlock.AXIS);
            if (axis != face.getAxis() && axis.isHorizontal())
                return face.getAxis().isHorizontal() ? EnumFacing.DOWN : axis == EnumFacing.Axis.X ? EnumFacing.NORTH : EnumFacing.EAST;
        }
        if (state.getBlock() instanceof PaletteBlocks.Pillar) {
            EnumFacing.Axis axis = state.getValue(BlockRotatedPillar.AXIS);
            if (axis == EnumFacing.Axis.X && face.getAxis().isVertical()) return EnumFacing.NORTH;
            if (axis.isHorizontal() && face.getAxis().isHorizontal() && face.getAxis() != axis)
                return EnumFacing.getFacingFromAxis(face.getAxisDirection(), EnumFacing.Axis.Y);
        }
        return face.getAxis() == EnumFacing.Axis.X ? EnumFacing.SOUTH : EnumFacing.WEST;
    }

    private static boolean reverse(IBlockState state, EnumFacing face) {
        if (state.getBlock() == AllBlocks.BRASS_TUNNEL) return true;
        if (state.getBlock() instanceof PaletteBlocks.Pillar) {
            EnumFacing.Axis axis = state.getValue(BlockRotatedPillar.AXIS);
            if (axis == EnumFacing.Axis.X)
                return face.getAxisDirection() == EnumFacing.AxisDirection.NEGATIVE && face.getAxis() != axis;
            if (axis == EnumFacing.Axis.Z)
                return face != EnumFacing.NORTH && face.getAxisDirection() == EnumFacing.AxisDirection.NEGATIVE;
        }
        if (state.getBlock() instanceof ChassisBlock chassis && !chassis.isRadial()) {
            EnumFacing.Axis axis = state.getValue(ChassisBlock.AXIS);
            return axis.isHorizontal() && (face.getAxis() == axis
                    ? face.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE : face == EnumFacing.DOWN);
        }
        if (state.getBlock() instanceof EncasedCogwheelBlock) {
            EnumFacing.Axis axis = state.getValue(KineticBlock.AXIS);
            return axis.isHorizontal() && face.getAxis().isHorizontal()
                    && face.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE;
        }
        if (state.getBlock() instanceof MechanicalCrafterBlock) {
            EnumFacing facing = state.getValue(MechanicalCrafterBlock.FACING);
            if (!face.getAxis().isVertical()) return false;
            boolean negative = facing.getAxisDirection() == EnumFacing.AxisDirection.NEGATIVE;
            return face == EnumFacing.DOWN && facing.getAxis() == EnumFacing.Axis.Z ? !negative : negative;
        }
        return false;
    }

    private static boolean flipHorizontal(IBlockState state, EnumFacing face) {
        if (state.getBlock() instanceof ChassisBlock chassis && !chassis.isRadial()
                && state.getValue(ChassisBlock.AXIS) == EnumFacing.Axis.X && face.getAxis().isHorizontal()
                && face.getAxis() != EnumFacing.Axis.X) return true;
        if (state.getBlock() instanceof EncasedCogwheelBlock cog && !cog.isLarge()) {
            EnumFacing.Axis axis = state.getValue(KineticBlock.AXIS);
            if (axis.isVertical() && face.getAxis().isHorizontal() || axis == EnumFacing.Axis.Z && face == EnumFacing.DOWN) return true;
        }
        return reverse(state, face);
    }

    private static boolean flipVertical(IBlockState state, EnumFacing face) {
        if (state.getBlock() instanceof PaletteBlocks.Pillar) {
            EnumFacing.Axis axis = state.getValue(BlockRotatedPillar.AXIS);
            if (axis == EnumFacing.Axis.X && face == EnumFacing.NORTH
                    || axis == EnumFacing.Axis.Z && face == EnumFacing.WEST) return false;
        }
        if (state.getBlock() instanceof EncasedCogwheelBlock cog && !cog.isLarge()
                && state.getValue(KineticBlock.AXIS) == EnumFacing.Axis.X && face.getAxis() == EnumFacing.Axis.Z)
            return face != EnumFacing.SOUTH;
        return reverse(state, face);
    }

    private static boolean connects(IBlockAccess world, BlockPos pos, BlockPos otherPos, IBlockState state, EnumFacing face) {
        IBlockState other = world.getBlockState(otherPos);
        Block block = state.getBlock();
        if (block instanceof com.cogwork.cogworkbridge.content.decoration.palette.WeatheringPaletteBlocks.Cube
                || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.WeatheringPaletteBlocks.Stairs
                || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.WeatheringPaletteBlocks.Slab
                || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.WeatheringPaletteBlocks.DoubleSlab) {
            if (face != EnumFacing.UP) return false;
            double top = roofHeight(state), otherTop = roofHeight(other);
            if (other.getBlock() instanceof xy177.farmersfuturedelight.common.block.IWeatheringCopper && top == otherTop) return true;
            if (top == .5D && otherTop > top) return true;
            IBlockState above = world.getBlockState(otherPos.up());
            return !above.getMaterial().isReplaceable() && above.getBoundingBox(world, otherPos.up()).maxY > 0;
        }
        if (block instanceof PaletteBlocks.Pillar) {
            EnumFacing.Axis axis = state.getValue(BlockRotatedPillar.AXIS);
            if (other.getBlock() != block || other.getValue(BlockRotatedPillar.AXIS) != axis) return false;
            // RotatedPillarCTBehaviour only lets a matching column obscure an end face.
            if (face.getAxis() != axis) return true;
            IBlockState blocking = world.getBlockState(otherPos.offset(face));
            return blocking.getBlock() != block || blocking.getValue(BlockRotatedPillar.AXIS) != axis;
        }
        if (block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Glass
                || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Pane
                || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Layered) {
            if (other.getBlock() != block) return false;
            return world.getBlockState(otherPos.offset(face)).getBlock() != block;
        }
        if (block instanceof ChassisBlock chassis && !chassis.isRadial())
            return block == other.getBlock() && state.getValue(ChassisBlock.AXIS) == other.getValue(ChassisBlock.AXIS);
        if (block == AllBlocks.BRASS_TUNNEL) {
            if (face != EnumFacing.UP || other.getBlock() != block || state.getValue(BeltTunnelBlock.AXIS) != other.getValue(BeltTunnelBlock.AXIS)) return false;
            EnumFacing.Axis across = state.getValue(BeltTunnelBlock.AXIS) == EnumFacing.Axis.X ? EnumFacing.Axis.Z : EnumFacing.Axis.X;
            if (across == EnumFacing.Axis.X ? pos.getZ() != otherPos.getZ() : pos.getX() != otherPos.getX()) return false;
            return world.getTileEntity(pos.down()) instanceof BeltTileEntity && world.getTileEntity(otherPos.down()) instanceof BeltTileEntity;
        }
        if (block instanceof MechanicalCrafterBlock) {
            if (block != other.getBlock() || state.getValue(MechanicalCrafterBlock.FACING) != other.getValue(MechanicalCrafterBlock.FACING)) return false;
            if (!(world.getTileEntity(pos) instanceof MechanicalCrafterTileEntity current)
                    || !(world.getTileEntity(otherPos) instanceof MechanicalCrafterTileEntity target)) return false;
            EnumFacing facing = state.getValue(MechanicalCrafterBlock.FACING);
            return pos.offset(current.getPointing().relativeTo(facing)).equals(otherPos)
                    || otherPos.offset(target.getPointing().relativeTo(facing)).equals(pos);
        }
        if (block instanceof EncasedCogwheelBlock cog && !cog.isLarge() && face.getAxis() != state.getValue(KineticBlock.AXIS)) {
            if (block == other.getBlock() && other.getValue(KineticBlock.AXIS) == state.getValue(KineticBlock.AXIS)) return true;
            IBlockState inFront = world.getBlockState(otherPos.offset(face));
            return inFront.getBlock() instanceof CogwheelBlock wheel && wheel.isLarge()
                    && inFront.getValue(KineticBlock.AXIS) == state.getValue(KineticBlock.AXIS);
        }
        int casing = casingType(state);
        if (casing == 0 || casing != casingType(other) || !casingSide(world, pos, state, face) || !casingSide(world, otherPos, other, face)) return false;
        BlockPos blockingPos = otherPos.offset(face);
        IBlockState blocking = world.getBlockState(blockingPos);
        return casing != casingType(blocking) || !casingSide(world, blockingPos, blocking, face) || !blocking.isFullCube();
    }

    private static double roofHeight(IBlockState state) {
        if (state.getBlock() instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Slab
                && state.getValue(net.minecraft.block.BlockStairs.HALF) == net.minecraft.block.BlockStairs.EnumHalf.BOTTOM) return .5D;
        return state.getMaterial().isReplaceable() ? 0D : 1D;
    }

    private static int casingType(IBlockState state) {
        Block block = state.getBlock();
        if (block == com.cogwork.cogworkbridge.registry.BlockFamily.get("railway_casing")) return 4;
        if (block == AllBlocks.ANDESITE_CASING || block == AllBlocks.SHAFT_ENCASED_ANDESITE
                || block == AllBlocks.COGWHEEL_ENCASED_ANDESITE || block == AllBlocks.LARGE_COGWHEEL_ENCASED_ANDESITE
                || block == AllBlocks.GEARBOX || block == AllBlocks.VERTICAL_GEARBOX) return 1;
        if (block == AllBlocks.BRASS_CASING || block == AllBlocks.SHAFT_ENCASED_BRASS
                || block == AllBlocks.COGWHEEL_ENCASED_BRASS || block == AllBlocks.LARGE_COGWHEEL_ENCASED_BRASS) return 2;
        return block == AllBlocks.COPPER_CASING || block == AllBlocks.ENCASED_FLUID_PIPE ? 3 : 0;
    }

    private static boolean casingSide(IBlockAccess world, BlockPos pos, IBlockState state, EnumFacing face) {
        Block block = state.getBlock();
        if (block instanceof EncasedShaftBlock) return face.getAxis() != state.getValue(KineticBlock.AXIS);
        if (block instanceof GearboxBlock) return face.getAxis() == state.getValue(KineticBlock.AXIS);
        if (block instanceof EncasedCogwheelBlock) {
            if (face.getAxis() != state.getValue(KineticBlock.AXIS)) return false;
            TileEntity tile = world.getTileEntity(pos);
            return !(tile instanceof EncasedCogwheelTileEntity cog) || !cog.isOpen(face);
        }
        if (block == AllBlocks.ENCASED_FLUID_PIPE) {
            TileEntity tile = world.getTileEntity(pos);
            return !(tile instanceof FluidPipeTileEntity pipe) || !pipe.hasConnection(face);
        }
        return true;
    }
}
