package com.cogwork.cogworkbridge.content.logistics.depot;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class DepotBlock extends BlockContainer {
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0, 0, 0, 1, 13.0D / 16.0D, 1);

    public DepotBlock() {
        super(Material.ROCK);
        setHardness(2.0F);
        setResistance(6.0F);
        setSoundType(SoundType.WOOD);
        setLightOpacity(0);
    }

    @Override public TileEntity createNewTileEntity(World world, int meta) { return new DepotTileEntity(); }
    @Override public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state) {
        return net.minecraft.util.EnumBlockRenderType.MODEL;
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return SHAPE; }

    @Override
    public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (!world.isRemote && entity instanceof EntityItem item && item.posY >= pos.getY() + 0.7D
                && world.getTileEntity(pos) instanceof DepotTileEntity depot) {
            depot.acceptItemEntity(item);
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (side != EnumFacing.UP || !(world.getTileEntity(pos) instanceof DepotTileEntity depot)) return false;
        if (!world.isRemote) depot.interact(player, hand);
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof DepotTileEntity depot) depot.dropContents();
        super.breakBlock(world, pos, state);
    }

    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof DepotTileEntity depot ? depot.comparatorLevel() : 0;
    }
}
