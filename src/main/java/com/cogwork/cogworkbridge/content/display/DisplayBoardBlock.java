package com.cogwork.cogworkbridge.content.display;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.init.Items;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class DisplayBoardBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public DisplayBoardBlock() {
        super(Material.IRON);
        setHardness(2F);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH));
    }
    @Override public boolean hasAxis() { return true; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta)); }
    @Override public EnumFacing.Axis getAxis(IBlockState state) { return state.getValue(FACING).getAxis(); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y,
                                                       float z, int meta, EntityLivingBase placer) {
        IBlockState neighbour = world.getBlockState(pos.offset(side.getOpposite()));
        EnumFacing facing = !placer.isSneaking() && neighbour.getBlock() == this ? neighbour.getValue(FACING) : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, facing);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new DisplayBoardTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public net.minecraft.util.BlockRenderLayer getRenderLayer() { return net.minecraft.util.BlockRenderLayer.CUTOUT; }

    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                               EntityPlayer player, EnumHand hand, EnumFacing side,
                                               float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) return false;
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == Item.getItemFromBlock(this)) {
            EnumFacing best = null;
            double bestDistance = Double.MAX_VALUE;
            for (EnumFacing direction : EnumFacing.VALUES) {
                if (direction.getAxis() == state.getValue(FACING).getAxis()) continue;
                BlockPos target = pos.offset(direction);
                if (!world.isBlockLoaded(target) || !world.getBlockState(target).getBlock().isReplaceable(world, target)) continue;
                double dx = hitX - (.5D + direction.getXOffset() * .5D);
                double dy = hitY - (.5D + direction.getYOffset() * .5D);
                double dz = hitZ - (.5D + direction.getZOffset() * .5D);
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < bestDistance) { bestDistance = distance; best = direction; }
            }
            return best != null && held.getItem().onItemUse(player, world, pos, hand, best, hitX, hitY, hitZ) == EnumActionResult.SUCCESS;
        }
        boolean clipboard = held.getItem() == Item.getItemFromBlock(
                com.cogwork.cogworkbridge.registry.AllBlocks.CLIPBOARD);
        boolean named = held.getItem() == Items.NAME_TAG && held.hasDisplayName();
        EnumDyeColor dye = DisplayDye.color(held);
        if (!held.isEmpty() && !clipboard && !named && dye == null) return false;
        if (world.isRemote) return true;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof DisplayBoardTileEntity board)) return true;
        int selectedLine = hitY >= .5F ? 0 : 1;
        if (dye != null) {
            board.setRowColor(dye, selectedLine);
        } else if (clipboard) {
            net.minecraft.nbt.NBTTagCompound data =
                    com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardData.fromItem(held);
            String[] lines = com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardData.lines(
                    data, data.getInteger("LastPage"));
            for (int i = 0; i < lines.length; i++) {
                int line = selectedLine + i;
                BlockPos row = pos.down(line / 2);
                if (!world.isBlockLoaded(row) || world.getBlockState(row) != state
                        || !(world.getTileEntity(row) instanceof DisplayBoardTileEntity target)) break;
                target.setManualText(lines[i], line % 2);
            }
        } else board.setManualText(named ? held.getDisplayName() : null, selectedLine);
        return true;
    }
}
