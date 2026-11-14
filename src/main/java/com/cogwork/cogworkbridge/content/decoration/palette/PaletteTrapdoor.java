package com.cogwork.cogworkbridge.content.decoration.palette;

import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class PaletteTrapdoor extends BlockTrapDoor {
    public PaletteTrapdoor() { super(Material.IRON); setHardness(3F); setHarvestLevel("pickaxe", 0); }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Nullable @Override public TileEntity createTileEntity(World world, IBlockState state) { return new DoorStateTile(); }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)).withProperty(HALF, (meta & 4) != 0 ? DoorHalf.TOP : DoorHalf.BOTTOM);
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex() | (state.getValue(HALF) == DoorHalf.TOP ? 4 : 0); }
    @Override public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof DoorStateTile door ? state.withProperty(OPEN, door.open) : state;
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return super.getBoundingBox(getActualState(state, world, pos), world, pos); }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, net.minecraft.entity.EntityLivingBase placer, net.minecraft.item.ItemStack stack) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof DoorStateTile door) {
            door.powered = world.isBlockPowered(pos); door.open = door.powered; door.changed();
        }
    }
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return super.getBlockFaceShape(world, getActualState(state, world, pos), pos, face);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing face, float x, float y, float z) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof DoorStateTile door)) return false;
        if (!world.isRemote) { door.open = !door.open; door.changed(); playSound(player, world, pos, door.open); }
        return true;
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block from, BlockPos fromPos) {
        if (world.isRemote) return;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof DoorStateTile door)) return;
        boolean powered = world.isBlockPowered(pos);
        if (powered != door.powered) { door.powered = powered; door.open = powered; door.changed(); playSound(null, world, pos, powered); }
    }
    @Override @SideOnly(Side.CLIENT) public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT; }
}
