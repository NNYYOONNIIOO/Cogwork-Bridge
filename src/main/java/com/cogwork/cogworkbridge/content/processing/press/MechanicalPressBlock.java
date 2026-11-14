package com.cogwork.cogworkbridge.content.processing.press;

import com.cogwork.cogworkbridge.content.processing.basin.BasinBlock;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import javax.annotation.Nullable;
import java.util.List;

public final class MechanicalPressBlock extends KineticBlock {
    public static final PropertyEnum<EnumFacing.Axis> HORIZONTAL_AXIS =
            PropertyEnum.create("axis", EnumFacing.Axis.class, EnumFacing.Axis.X, EnumFacing.Axis.Z);
    private static final AxisAlignedBB SHAPE = FULL_BLOCK_AABB;

    public MechanicalPressBlock() {
        super(Material.ROCK);
        setHardness(2.0F);
        setResistance(6.0F);
        setHarvestLevel("pickaxe", 0);
        setDefaultState(getDefaultState().withProperty(HORIZONTAL_AXIS, EnumFacing.Axis.Z));
    }

    @Override public boolean hasAxis() { return true; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, HORIZONTAL_AXIS); }
    @Override public EnumFacing.Axis getAxis(IBlockState state) { return state.getValue(HORIZONTAL_AXIS); }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(HORIZONTAL_AXIS, (meta & 1) == 0 ? EnumFacing.Axis.Z : EnumFacing.Axis.X);
    }
    @Override public int getMetaFromState(IBlockState state) { return getAxis(state) == EnumFacing.Axis.X ? 1 : 0; }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new MechanicalPressTileEntity(); }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos)
                && !(world.getBlockState(pos.down()).getBlock() instanceof BasinBlock);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face, float hitX, float hitY,
                                            float hitZ, int meta, EntityLivingBase placer) {
        EnumFacing.Axis preferred = null;
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            BlockPos neighbourPos = pos.offset(side);
            IBlockState neighbour = world.getBlockState(neighbourPos);
            if (!(neighbour.getBlock() instanceof KineticBlock kinetic)
                    || !kinetic.canConnectShaftTowards(world, neighbourPos, neighbour, side.getOpposite())) continue;
            if (preferred != null && preferred != side.getAxis()) {
                preferred = null;
                break;
            }
            preferred = side.getAxis();
        }
        return getDefaultState().withProperty(HORIZONTAL_AXIS,
                preferred == null ? placer.getHorizontalFacing().rotateY().getAxis() : preferred);
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        if (rotation != Rotation.CLOCKWISE_90 && rotation != Rotation.COUNTERCLOCKWISE_90) return state;
        return state.withProperty(HORIZONTAL_AXIS,
                getAxis(state) == EnumFacing.Axis.X ? EnumFacing.Axis.Z : EnumFacing.Axis.X);
    }

    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return SHAPE; }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return getBoundingBox(state, world, pos); }
    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
                                                 List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        if (entity instanceof EntityPlayer) {
            addBox(pos, query, boxes, new AxisAlignedBB(0, 2 / 16D, 0, 1, 1, 1));
            return;
        }
        addBox(pos, query, boxes, new AxisAlignedBB(0, 0, 0, 4 / 16D, 1, 1));
        addBox(pos, query, boxes, new AxisAlignedBB(12 / 16D, 0, 0, 1, 1, 1));
        addBox(pos, query, boxes, new AxisAlignedBB(4 / 16D, 0, 0, 12 / 16D, 1, 4 / 16D));
        addBox(pos, query, boxes, new AxisAlignedBB(4 / 16D, 0, 12 / 16D, 12 / 16D, 1, 1));
    }
    private static void addBox(BlockPos pos, AxisAlignedBB query, List<AxisAlignedBB> boxes, AxisAlignedBB local) {
        AxisAlignedBB box = local.offset(pos);
        if (query.intersects(box)) boxes.add(box);
    }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
}
