package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.content.decoration.SeatBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class ToolboxBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    private final int color;
    public int color() { return color; }
    public ToolboxBlock(int color) { super(Material.IRON); this.color = color; setHardness(2F); setLightOpacity(0); setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH)); }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta)); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) { return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite()); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new ToolboxTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return new AxisAlignedBB(.125D, 0, .125D, .875D, .75D, .875D); }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (world.getTileEntity(pos) instanceof ToolboxTileEntity tile) {
            if (stack.hasTagCompound() && stack.getTagCompound().hasKey("Toolbox", 10)) {
                NBTTagCompound data = stack.getTagCompound().getCompoundTag("Toolbox").copy();
                data.setInteger("x", pos.getX()); data.setInteger("y", pos.getY()); data.setInteger("z", pos.getZ());
                tile.readFromNBT(data); tile.markDirty();
            }
            if (stack.hasDisplayName()) tile.setCustomName(stack.getDisplayName());
        }
    }
    private ItemStack asStack(IBlockAccess world, BlockPos pos) {
        ItemStack result = new ItemStack(this);
        if (world.getTileEntity(pos) instanceof ToolboxTileEntity tile) {
            result.setTagInfo("Toolbox", tile.writeToNBT(new NBTTagCompound()));
            if (tile.hasCustomName()) result.setStackDisplayName(tile.getName());
        }
        return result;
    }
    @Override public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) { drops.add(asStack(world, pos)); }
    @Override public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest) { return willHarvest || super.removedByPlayer(state, world, pos, player, false); }
    @Override public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state, TileEntity tile, ItemStack tool) { super.harvestBlock(world, player, pos, state, tile, tool); world.setBlockToAir(pos); }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.isSneaking() || player instanceof net.minecraftforge.common.util.FakePlayer) return false;
        int dye = SeatBlock.dyeColor(player.getHeldItem(hand));
        if (dye >= 0 && dye != color) {
            if (!world.isRemote && world.getTileEntity(pos) instanceof ToolboxTileEntity tile) {
                NBTTagCompound data = tile.writeToNBT(new NBTTagCompound());
                world.setBlockState(pos, AllBlocks.TOOLBOXES[dye].getDefaultState().withProperty(FACING, state.getValue(FACING)), 3);
                if (world.getTileEntity(pos) instanceof ToolboxTileEntity replacement) { replacement.readFromNBT(data); replacement.markDirty(); }
            }
            return true;
        }
        if (!world.isRemote && world.getTileEntity(pos) instanceof ToolboxTileEntity)
            player.openGui(com.cogwork.cogworkbridge.CogworkBridge.instance,
                    com.cogwork.cogworkbridge.content.fluids.ListFilterGuiHandler.TOOLBOX_GUI,
                    world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }
    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof ToolboxTileEntity tile ? net.minecraft.inventory.Container.calcRedstoneFromInventory(tile) : 0;
    }
}
