package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.registry.AllSounds;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

public final class DeskBellBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final IUnlistedProperty<Boolean> PRESSED = new IUnlistedProperty<Boolean>() {
        @Override public String getName() { return "pressed"; }
        @Override public boolean isValid(Boolean value) { return value != null; }
        @Override public Class<Boolean> getType() { return Boolean.class; }
        @Override public String valueToString(Boolean value) { return value.toString(); }
    };
    public DeskBellBlock() { super(Material.IRON); setHardness(1F); setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP)); }
    @Override protected BlockStateContainer createBlockState() { return new ExtendedBlockState(this, new IProperty<?>[]{FACING}, new IUnlistedProperty<?>[]{PRESSED}); }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return ((IExtendedBlockState) state).withProperty(PRESSED, world.getTileEntity(pos) instanceof DeskBellTileEntity bell && bell.isPressed());
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta)); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) { return getDefaultState().withProperty(FACING, side); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new DeskBellTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return switch (state.getValue(FACING)) {
            case UP -> new AxisAlignedBB(.1875, 0, .1875, .8125, .625, .8125);
            case DOWN -> new AxisAlignedBB(.1875, .375, .1875, .8125, 1, .8125);
            case NORTH -> new AxisAlignedBB(.1875, .1875, .375, .8125, .8125, 1);
            case SOUTH -> new AxisAlignedBB(.1875, .1875, 0, .8125, .8125, .625);
            case WEST -> new AxisAlignedBB(.375, .1875, .1875, 1, .8125, .8125);
            case EAST -> new AxisAlignedBB(0, .1875, .1875, .625, .8125, .8125);
        };
    }
    @Override public boolean canProvidePower(IBlockState state) { return true; }
    @Override public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return world.getTileEntity(pos) instanceof DeskBellTileEntity bell && bell.isPressed() ? 15 : 0;
    }
    @Override public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return side == state.getValue(FACING) ? getWeakPower(state, world, pos, side) : 0;
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.getHeldItem(hand).getItem() == AllItems.WRENCH) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof DeskBellTileEntity bell) {
            bell.press();
            world.playSound(null, pos, AllSounds.DESK_BELL, SoundCategory.BLOCKS, 1F, 1F);
        }
        return true;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (world.getTileEntity(pos) instanceof DeskBellTileEntity bell && bell.isPressed()) notifyOutputs(world, pos, state);
        super.breakBlock(world, pos, state);
    }
    static void notifyOutputs(World world, BlockPos pos, IBlockState state) {
        world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
        world.notifyNeighborsOfStateChange(pos.offset(state.getValue(FACING).getOpposite()), state.getBlock(), false);
    }
}
