package com.cogwork.cogworkbridge.content.kinetics.drill;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class MechanicalDrillBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final DamageSource DRILL_DAMAGE = new DamageSource("cogworkbridge.drill");

    public MechanicalDrillBlock() {
        super(Material.ROCK, "cogworkbridge.mechanical_drill");
        setHardness(2.0F);
        setResistance(6.0F);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public boolean hasAxis() {
        return false;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        EnumFacing preferred = placer.isSneaking() ? null : getPreferredShaftFacing(world, pos);
        return getDefaultState().withProperty(FACING, preferred != null
                ? preferred.getOpposite() : getPlacementFacing(placer, placer.isSneaking()));
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new MechanicalDrillTileEntity();
    }

    @Override
    public EnumFacing.Axis getAxis(IBlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        return side == state.getValue(FACING).getOpposite();
    }

    @Override
    public EnumFacing getShaftFacing(World world, BlockPos pos, IBlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return switch (state.getValue(FACING)) {
            case DOWN -> new AxisAlignedBB(0, 0.25D, 0, 1, 1, 1);
            case UP -> new AxisAlignedBB(0, 0, 0, 1, 0.75D, 1);
            case NORTH -> new AxisAlignedBB(0, 0, 0.25D, 1, 1, 1);
            case SOUTH -> new AxisAlignedBB(0, 0, 0, 1, 1, 0.75D);
            case WEST -> new AxisAlignedBB(0.25D, 0, 0, 1, 1, 1);
            case EAST -> new AxisAlignedBB(0, 0, 0, 0.75D, 1, 1);
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
    public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (world.isRemote || entity instanceof EntityItem
                || world instanceof com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld
                || world instanceof com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld
                || !new AxisAlignedBB(pos).shrink(0.1D).intersects(entity.getEntityBoundingBox())) {
            return;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof MechanicalDrillTileEntity drill && drill.getSpeed() != 0.0F) {
            float speed = Math.abs(drill.getSpeed());
            float damage = MathHelper.clamp(Math.min(speed / 16.0F, 2.0F)
                    + Math.min(speed / 32.0F, 4.0F)
                    + Math.min(speed / 64.0F, 4.0F), 1.0F, 10.0F);
            entity.attackEntityFrom(DRILL_DAMAGE, damage);
        }
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof MechanicalDrillTileEntity drill) {
            drill.wakeBreaker();
        }
    }
}
