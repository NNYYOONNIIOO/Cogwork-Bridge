package com.cogwork.cogworkbridge.content.redstone;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class AnalogLeverBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyEnum<Mount> MOUNT = PropertyEnum.create("mount", Mount.class);
    public enum Mount implements IStringSerializable {
        FLOOR, WALL, CEILING;
        @Override public String getName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public AnalogLeverBlock() { super(Material.CIRCUITS); setHardness(.5F); setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(MOUNT, Mount.FLOOR)); }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, MOUNT); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex() | state.getValue(MOUNT).ordinal() << 2; }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)).withProperty(MOUNT, Mount.values()[Math.min(2, (meta >> 2) & 3)]); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(MOUNT, side == EnumFacing.UP ? Mount.FLOOR : side == EnumFacing.DOWN ? Mount.CEILING : Mount.WALL)
                .withProperty(FACING, side.getAxis().isHorizontal() ? side : placer.getHorizontalFacing().getOpposite());
    }
    public static EnumFacing connectedDirection(IBlockState state) {
        return state.getValue(MOUNT) == Mount.FLOOR ? EnumFacing.UP : state.getValue(MOUNT) == Mount.CEILING ? EnumFacing.DOWN : state.getValue(FACING);
    }
    @Override public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        return world.isSideSolid(pos.offset(side.getOpposite()), side);
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, net.minecraft.block.Block block, BlockPos from) {
        if (!world.isRemote && !canPlaceBlockOnSide(world, pos, connectedDirection(state))) {
            dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
        }
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new AnalogLeverTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        EnumFacing direction = connectedDirection(state);
        return switch (direction) {
            case UP -> new AxisAlignedBB(.25, 0, .25, .75, .625, .75);
            case DOWN -> new AxisAlignedBB(.25, .375, .25, .75, 1, .75);
            case NORTH -> new AxisAlignedBB(.25, .25, .375, .75, .75, 1);
            case SOUTH -> new AxisAlignedBB(.25, .25, 0, .75, .75, .625);
            case WEST -> new AxisAlignedBB(.375, .25, .25, 1, .75, .75);
            case EAST -> new AxisAlignedBB(0, .25, .25, .625, .75, .75);
        };
    }
    @Override public boolean canProvidePower(IBlockState state) { return true; }
    @Override public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return world.getTileEntity(pos) instanceof AnalogLeverTileEntity lever ? lever.value() : 0;
    }
    @Override public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return side == connectedDirection(state) ? getWeakPower(state, world, pos, side) : 0;
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof AnalogLeverTileEntity lever) {
            lever.change(player.isSneaking() ? -1 : 1);
            float pitch = .25F + ((lever.value() + 5F) / 15F) * .5F;
            world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, .2F, pitch);
        }
        return true;
    }
    @Override public void randomDisplayTick(IBlockState state, World world, BlockPos pos, java.util.Random random) {
        if (world.getTileEntity(pos) instanceof AnalogLeverTileEntity lever && lever.value() > 0 && random.nextFloat() < .25F) {
            EnumFacing direction = connectedDirection(state);
            world.spawnParticle(net.minecraft.util.EnumParticleTypes.REDSTONE,
                    pos.getX() + .5D + direction.getXOffset() * .2D,
                    pos.getY() + .5D + direction.getYOffset() * .2D,
                    pos.getZ() + .5D + direction.getZOffset() * .2D, .5D, 0, 0);
        }
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (world.getTileEntity(pos) instanceof AnalogLeverTileEntity lever && lever.value() != 0) notifyOutputs(world, pos, state);
        super.breakBlock(world, pos, state);
    }
    static void notifyOutputs(World world, BlockPos pos, IBlockState state) {
        world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
        world.notifyNeighborsOfStateChange(pos.offset(connectedDirection(state).getOpposite()), state.getBlock(), false);
    }
}
