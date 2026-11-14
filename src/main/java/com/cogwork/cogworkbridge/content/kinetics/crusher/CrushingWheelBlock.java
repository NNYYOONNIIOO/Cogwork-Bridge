package com.cogwork.cogworkbridge.content.kinetics.crusher;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;


public final class CrushingWheelBlock extends KineticBlock {
    public CrushingWheelBlock() {
        super(Material.ROCK, "cogworkbridge.crushing_wheel");
        setHardness(3.0F);
        setResistance(10.0F);
    }

    @Override public boolean hasAxis() { return true; }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing clickedFace,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        EnumFacing preferred = placer.isSneaking() ? null : getPreferredShaftFacing(world, pos);
        return getDefaultState().withProperty(AXIS,
                preferred != null ? preferred.getAxis() : getPlacementAxis(placer));
    }

    @Override public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new CrushingWheelTileEntity();
    }

    @Override
    public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        return side.getAxis() == state.getValue(AXIS);
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        updateControllers(world, pos, state);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                net.minecraft.block.Block blockIn, BlockPos fromPos) {
        updateControllers(world, pos, state);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            for (EnumFacing side : EnumFacing.values()) {
                if (side.getAxis() == state.getValue(AXIS)) continue;
                BlockPos controllerPos = pos.offset(side);
                if (world.getBlockState(controllerPos).getBlock() instanceof CrushingWheelControllerBlock) {
                    world.setBlockToAir(controllerPos);
                }
            }
        }
        super.breakBlock(world, pos, state);
    }

    public static void updateControllers(World world, BlockPos pos, IBlockState state) {
        if (world.isRemote) return;
        EnumFacing.Axis wheelAxis = state.getValue(AXIS);
        TileEntity tile = world.getTileEntity(pos);
        float speed = tile instanceof CrushingWheelTileEntity ? ((CrushingWheelTileEntity) tile).getSpeed() : 0.0F;
        for (EnumFacing side : EnumFacing.values()) {
            if (side.getAxis() == wheelAxis) continue;
            BlockPos controllerPos = pos.offset(side);
            BlockPos otherWheelPos = pos.offset(side, 2);
            IBlockState otherState = world.getBlockState(otherWheelPos);
            boolean paired = otherState.getBlock() instanceof CrushingWheelBlock
                    && otherState.getValue(AXIS) == wheelAxis;
            IBlockState existing = world.getBlockState(controllerPos);

            if (!paired) {
                if (existing.getBlock() instanceof CrushingWheelControllerBlock) {
                    world.setBlockToAir(controllerPos);
                }
                continue;
            }

            TileEntity otherTile = world.getTileEntity(otherWheelPos);
            float otherSpeed = otherTile instanceof CrushingWheelTileEntity
                    ? ((CrushingWheelTileEntity) otherTile).getSpeed() : 0.0F;
            boolean valid = speed != 0.0F && otherSpeed != 0.0F
                    && (speed > 0.0F) != (otherSpeed > 0.0F);
            EnumFacing outputFacing = valid ? outputFacing(wheelAxis, side, speed) : EnumFacing.DOWN;

            boolean controllerExists = existing.getBlock() instanceof CrushingWheelControllerBlock;
            if (!controllerExists) {
                if (!world.isAirBlock(controllerPos)) continue;
                existing = AllBlocks.CRUSHING_WHEEL_CONTROLLER.getDefaultState();
            }

            IBlockState next = existing.withProperty(CrushingWheelControllerBlock.FACING, outputFacing);
            if (!controllerExists || !next.equals(existing)) world.setBlockState(controllerPos, next, 3);
            TileEntity controller = world.getTileEntity(controllerPos);
            if (controller instanceof CrushingWheelControllerTileEntity) {
                ((CrushingWheelControllerTileEntity) controller).setCrushingSpeed(valid ? Math.abs(speed) / 50.0F : 0.0F);
            }
        }
    }

    private static EnumFacing outputFacing(EnumFacing.Axis wheelAxis, EnumFacing side, float speed) {
        int ax = wheelAxis == EnumFacing.Axis.X ? 1 : 0;
        int ay = wheelAxis == EnumFacing.Axis.Y ? 1 : 0;
        int az = wheelAxis == EnumFacing.Axis.Z ? 1 : 0;
        int sx = side.getXOffset();
        int sy = side.getYOffset();
        int sz = side.getZOffset();
        int x = ay * sz - az * sy;
        int y = az * sx - ax * sz;
        int z = ax * sy - ay * sx;
        float modifier = Math.signum(speed);
        return EnumFacing.getFacingFromVector(x * modifier, y * modifier, z * modifier);
    }
}
