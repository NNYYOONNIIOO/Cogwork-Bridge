package com.cogwork.cogworkbridge.content.contraptions.gantry;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

public final class GantryShaftBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final IUnlistedProperty<Integer> VISUAL = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "gantry_visual"; }
        @Override public boolean isValid(Integer value) { return value != null && value >= 0 && value < 8; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };
    public GantryShaftBlock() {
        super(Material.IRON); setHardness(2F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
    }
    @Override public boolean hasAxis() { return false; }
    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{FACING}, new IUnlistedProperty<?>[]{VISUAL});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess access, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState extended)) return state;
        EnumFacing direction = state.getValue(FACING);
        IBlockState before = access.getBlockState(pos.offset(direction.getOpposite()));
        IBlockState after = access.getBlockState(pos.offset(direction));
        boolean back = before.getBlock() == this && before.getValue(FACING) == direction;
        boolean front = after.getBlock() == this && after.getValue(FACING) == direction;
        int part = back ? (front ? 3 : 2) : (front ? 1 : 0);
        TileEntity tile = access.getTileEntity(pos);
        boolean powered = tile instanceof GantryShaftTileEntity shaft && shaft.isVisuallyLocked();
        return extended.withProperty(VISUAL, part + (powered ? 4 : 0));
    }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(Math.floorMod(meta, 6))); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public EnumFacing.Axis getAxis(IBlockState state) { return state.getValue(FACING).getAxis(); }
    @Override public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) { return side.getAxis() == getAxis(state); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        EnumFacing facing = getPlacementFacing(placer, placer.isSneaking());
        IBlockState neighbour = world.getBlockState(pos.offset(side.getOpposite()));
        if (!placer.isSneaking() && neighbour.getBlock() == this && neighbour.getValue(FACING).getAxis() == facing.getAxis())
            facing = neighbour.getValue(FACING);
        return getDefaultState().withProperty(FACING, facing);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new GantryShaftTileEntity(); }
    @Override public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state) { return net.minecraft.util.EnumBlockRenderType.ENTITYBLOCK_ANIMATED; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                              EnumHand hand, EnumFacing face, float x, float y, float z) {
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() != Item.getItemFromBlock(this) || player.isSneaking()) return false;
        BlockPos target = placementTarget(world, pos, state, player, held, face);
        if (target == null) return false;
        if (!world.isRemote && world.setBlockState(target, getDefaultState().withProperty(FACING, state.getValue(FACING)), 3)
                && !player.capabilities.isCreativeMode) held.shrink(1);
        return true;
    }

    @javax.annotation.Nullable
    public BlockPos placementTarget(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    ItemStack held, EnumFacing face) {
        EnumFacing facing = state.getValue(FACING);
        EnumFacing.Axis axis = facing.getAxis();
        double look = axis == EnumFacing.Axis.X ? player.getLookVec().x
                : axis == EnumFacing.Axis.Y ? player.getLookVec().y : player.getLookVec().z;
        EnumFacing scan = face.getAxis() == axis ? face : EnumFacing.getFacingFromAxis(
                look >= 0 ? EnumFacing.AxisDirection.POSITIVE : EnumFacing.AxisDirection.NEGATIVE, axis);
        for (int i = 1; i <= 64; i++) {
            BlockPos target = pos.offset(scan, i);
            if (!world.isBlockLoaded(target)) return null;
            IBlockState next = world.getBlockState(target);
            if (next.getBlock() == this && next.getValue(FACING).getAxis() == axis) continue;
            if (!next.getBlock().isReplaceable(world, target) || !player.canPlayerEdit(target, scan, held)
                    || !world.mayPlace(this, target, false, scan, player)) return null;
            return target.toImmutable();
        }
        return null;
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return switch (getAxis(state)) {
            case X -> new AxisAlignedBB(0, .25, .25, 1, .75, .75);
            case Y -> new AxisAlignedBB(.25, 0, .25, .75, 1, .75);
            case Z -> new AxisAlignedBB(.25, .25, 0, .75, .75, 1);
        };
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof GantryShaftTileEntity shaft) shaft.releaseAll();
        super.breakBlock(world, pos, state);
    }
}
