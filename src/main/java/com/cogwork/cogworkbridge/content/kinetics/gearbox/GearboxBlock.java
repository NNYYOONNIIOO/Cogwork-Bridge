package com.cogwork.cogworkbridge.content.kinetics.gearbox;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.content.decoration.ConnectedTextureData;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** A four-port gearbox; its stored axis identifies the two closed faces. */
public final class GearboxBlock extends KineticBlock {

    @Override protected BlockStateContainer createBlockState() {
        return new net.minecraftforge.common.property.ExtendedBlockState(this, new IProperty<?>[]{AXIS},
                new net.minecraftforge.common.property.IUnlistedProperty<?>[]{ConnectedTextureData.CONTEXT});
    }

    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return ((net.minecraftforge.common.property.IExtendedBlockState) state)
                .withProperty(ConnectedTextureData.CONTEXT, ConnectedTextureData.capture(world, pos, state));
    }

    private final boolean vertical;

    public GearboxBlock(boolean vertical) {
        super(Material.WOOD);
        this.vertical = vertical;
        setHardness(2.0F);
        setResistance(6.0F);
        setDefaultState(getDefaultState().withProperty(AXIS,
                vertical ? EnumFacing.Axis.X : EnumFacing.Axis.Y));
    }

    @Override
    public boolean hasAxis() {
        return true;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new GearboxTileEntity();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        if (!vertical) {
            return getDefaultState().withProperty(AXIS, EnumFacing.Axis.Y);
        }
        EnumFacing.Axis adjacentAxis = null;
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            BlockPos neighbourPos = pos.offset(side);
            IBlockState neighbour = world.getBlockState(neighbourPos);
            if (!(neighbour.getBlock() instanceof KineticBlock kinetic)
                    || !kinetic.canConnectShaftTowards(world, neighbourPos, neighbour, side.getOpposite())) {
                continue;
            }
            if (adjacentAxis != null && adjacentAxis != side.getAxis()) {
                adjacentAxis = null;
                break;
            }
            adjacentAxis = side.getAxis();
        }
        EnumFacing.Axis openAxis = adjacentAxis != null
                ? adjacentAxis : placer.getHorizontalFacing().getAxis();
        return getDefaultState().withProperty(AXIS,
                openAxis == EnumFacing.Axis.X ? EnumFacing.Axis.Z : EnumFacing.Axis.X);
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            EnumFacing.Axis axis = getAxis(state);
            if (axis == EnumFacing.Axis.X) {
                return state.withProperty(AXIS, EnumFacing.Axis.Z);
            }
            if (axis == EnumFacing.Axis.Z) {
                return state.withProperty(AXIS, EnumFacing.Axis.X);
            }
        }
        return state;
    }

    @Override
    public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        return side.getAxis() != getAxis(state);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos,
                         IBlockState state, int fortune) {
        drops.add(gearboxItem(state));
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world,
                                  BlockPos pos, EntityPlayer player) {
        return gearboxItem(state);
    }

    private ItemStack gearboxItem(IBlockState state) {
        return new ItemStack(getAxis(state) == EnumFacing.Axis.Y ? AllBlocks.GEARBOX : AllBlocks.VERTICAL_GEARBOX);
    }

    @Override
    public double shaftPortModifier(IBlockState state, EnumFacing side) {
        EnumFacing.Axis closed = getAxis(state);
        if (side.getAxis() == closed) {
            return 0.0D;
        }
        boolean secondAxis = closed == EnumFacing.Axis.Z
                ? side.getAxis() == EnumFacing.Axis.Y
                : side.getAxis() == EnumFacing.Axis.Z;
        int direction = side.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1 : -1;
        return secondAxis ? -direction : direction;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }
}
