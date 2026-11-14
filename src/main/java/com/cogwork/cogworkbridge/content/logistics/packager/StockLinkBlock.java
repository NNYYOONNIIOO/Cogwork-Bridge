package com.cogwork.cogworkbridge.content.logistics.packager;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class StockLinkBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    public StockLinkBlock() {
        super(Material.IRON);
        setHardness(2F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta)); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, side);
    }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof StockLinkTileEntity link) {
            if (stack.hasTagCompound() && stack.getTagCompound().hasUniqueId("LogisticsFrequency")) {
                java.util.UUID frequency = stack.getTagCompound().getUniqueId("LogisticsFrequency");
                if (!(placer instanceof net.minecraft.entity.player.EntityPlayer player) || LogisticsNetworkSecurity.mayInteract(world, frequency, player)) link.setNetwork(frequency);
            }
            if (placer instanceof net.minecraft.entity.player.EntityPlayer player) LogisticsNetworkSecurity.claim(world, link.getNetwork(), player);
        }
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new StockLinkTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public net.minecraft.util.BlockRenderLayer getRenderLayer() { return net.minecraft.util.BlockRenderLayer.TRANSLUCENT; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return switch (state.getValue(FACING)) {
            case UP -> new AxisAlignedBB(.1875, 0, .1875, .8125, .375, .8125);
            case DOWN -> new AxisAlignedBB(.1875, .625, .1875, .8125, 1, .8125);
            case NORTH -> new AxisAlignedBB(.1875, .1875, .625, .8125, .8125, 1);
            case SOUTH -> new AxisAlignedBB(.1875, .1875, 0, .8125, .8125, .375);
            case WEST -> new AxisAlignedBB(.625, .1875, .1875, 1, .8125, .8125);
            case EAST -> new AxisAlignedBB(0, .1875, .1875, .375, .8125, .8125);
        };
    }
}
