package com.cogwork.cogworkbridge.content.contraptions.actors;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Horizontal actor attached to the block behind it. */
public abstract class AttachedActorBlock extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    protected AttachedActorBlock() {
        super(Material.IRON);
        setHardness(2.0F);
        setResistance(6.0F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byIndex(meta);
        return getDefaultState().withProperty(FACING,
                facing.getAxis().isHorizontal() ? facing : EnumFacing.NORTH);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        EnumFacing facing = face.getAxis().isHorizontal()
                ? face : placer.getHorizontalFacing().getOpposite();
        if (face.getAxis().isHorizontal()) {
            IBlockState behind = world.getBlockState(pos.offset(face.getOpposite()));
            if (behind.getBlock() instanceof AttachedActorBlock) {
                facing = behind.getValue(FACING);
            }
        } else if (!hasSupport(world, pos, facing)) {
            for (EnumFacing candidate : EnumFacing.HORIZONTALS) {
                if (hasSupport(world, pos, candidate)) {
                    facing = candidate;
                    break;
                }
            }
        }
        return getDefaultState().withProperty(FACING, facing);
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        if (!super.canPlaceBlockOnSide(world, pos, side)) {
            return false;
        }
        if (side.getAxis().isHorizontal()) {
            IBlockState behind = world.getBlockState(pos.offset(side.getOpposite()));
            EnumFacing facing = behind.getBlock() instanceof AttachedActorBlock
                    ? behind.getValue(FACING) : side;
            return hasSupport(world, pos, facing);
        }
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            if (hasSupport(world, pos, facing)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (!world.isRemote && !hasSupport(world, pos, state.getValue(FACING))) {
            world.destroyBlock(pos, true);
        }
    }

    private static boolean hasSupport(World world, BlockPos pos, EnumFacing facing) {
        BlockPos behind = pos.offset(facing.getOpposite());
        IBlockState support = world.getBlockState(behind);
        return world.isSideSolid(behind, facing)
                || support.getBlock() instanceof KineticBlock
                && FULL_BLOCK_AABB.equals(support.getBoundingBox(world, behind));
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        double low = 2.0D / 16.0D;
        double high = 14.0D / 16.0D;
        double depth = 3.0D / 16.0D;
        return switch (state.getValue(FACING)) {
            case NORTH -> new AxisAlignedBB(0, low, 1 - depth, 1, high, 1);
            case SOUTH -> new AxisAlignedBB(0, low, 0, 1, high, depth);
            case WEST -> new AxisAlignedBB(1 - depth, low, 0, 1, high, 1);
            case EAST -> new AxisAlignedBB(0, low, 0, depth, high, 1);
            default -> FULL_BLOCK_AABB;
        };
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }
}
