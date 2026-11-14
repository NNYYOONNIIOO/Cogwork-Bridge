package com.cogwork.cogworkbridge.content.logistics.vault;

import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.properties.IProperty;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraft.world.IBlockAccess;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class ItemVaultBlock extends BlockContainer {
    public static final PropertyEnum<EnumFacing.Axis> AXIS = PropertyEnum.create("axis", EnumFacing.Axis.class,
            axis -> axis != EnumFacing.Axis.Y);
    public static final IUnlistedProperty<Integer> CONNECTED = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "vault_connected"; }
        @Override public boolean isValid(Integer value) { return value != null; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };
    public ItemVaultBlock() {
        super(Material.IRON);
        setHardness(2.0F); setResistance(6.0F); setSoundType(SoundType.METAL);
        setDefaultState(getDefaultState().withProperty(AXIS, EnumFacing.Axis.Z));
    }
    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{AXIS}, new IUnlistedProperty<?>[]{CONNECTED});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState extended) || !(world.getTileEntity(pos) instanceof ItemVaultTileEntity vault)) return state;
        int value = (vault.width() > 1 ? 1 : 0) << 24;
        EnumFacing.Axis axis = state.getValue(AXIS);
        for (EnumFacing face : EnumFacing.VALUES) {
            EnumFacing up = face.getAxis().isHorizontal() ? EnumFacing.UP : EnumFacing.NORTH;
            EnumFacing right = face.getAxis() == EnumFacing.Axis.X ? EnumFacing.SOUTH : EnumFacing.WEST;
            if (face.getAxis().isVertical() && axis == EnumFacing.Axis.X) { up = up.rotateY(); right = right.rotateY(); }
            else if (face.getAxis().isHorizontal() && face.getAxis() != axis) {
                up = EnumFacing.getFacingFromAxis(axis == EnumFacing.Axis.X ? EnumFacing.AxisDirection.POSITIVE : EnumFacing.AxisDirection.NEGATIVE, axis);
                right = EnumFacing.getFacingFromAxis(face.getAxisDirection(), EnumFacing.Axis.Y);
            }
            if (face.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE) right = right.getOpposite();
            if (face == EnumFacing.DOWN) { up = up.getOpposite(); right = right.getOpposite(); }
            int mask = (connected(world, vault, pos.offset(up)) ? 1 : 0)
                    | (connected(world, vault, pos.offset(up.getOpposite())) ? 2 : 0)
                    | (connected(world, vault, pos.offset(right.getOpposite())) ? 4 : 0)
                    | (connected(world, vault, pos.offset(right)) ? 8 : 0);
            value |= mask << (face.getIndex() * 4);
        }
        return extended.withProperty(CONNECTED, value);
    }
    private static boolean connected(IBlockAccess world, ItemVaultTileEntity vault, BlockPos other) {
        return world.getTileEntity(other) instanceof ItemVaultTileEntity neighbor
                && vault.axis() == neighbor.axis() && vault.controllerPos().equals(neighbor.controllerPos());
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(AXIS) == EnumFacing.Axis.X ? 0 : 1; }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(AXIS, (meta & 1) == 0 ? EnumFacing.Axis.X : EnumFacing.Axis.Z);
    }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        IBlockState adjacent = world.getBlockState(pos.offset(side.getOpposite()));
        EnumFacing.Axis axis = !placer.isSneaking() && adjacent.getBlock() instanceof ItemVaultBlock
                ? adjacent.getValue(AXIS) : placer.getHorizontalFacing().getAxis();
        return getDefaultState().withProperty(AXIS, axis);
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)
            return state.withProperty(AXIS, state.getValue(AXIS) == EnumFacing.Axis.X ? EnumFacing.Axis.Z : EnumFacing.Axis.X);
        return state;
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new ItemVaultTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        VaultConnectivity.markAround(world, pos);
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        VaultConnectivity.markAround(world, pos);
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof ItemVaultTileEntity vault) {
            for (int slot = 0; slot < vault.ownInventory().getSlots(); slot++)
                ItemTransfer.drop(world, pos, vault.ownInventory().extractItem(slot, 64, false));
        }
        super.breakBlock(world, pos, state);
        VaultConnectivity.markAround(world, pos);
    }
    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof ItemVaultTileEntity vault ? vault.comparatorLevel() : 0;
    }
}
