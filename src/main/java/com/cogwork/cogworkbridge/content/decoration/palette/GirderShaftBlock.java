package com.cogwork.cogworkbridge.content.decoration.palette;

import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.BlockFamily;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraft.world.IBlockAccess;

/** Girder shell with a normal horizontal kinetic shaft through its centre. */
public class GirderShaftBlock extends ShaftBlock {
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, AXIS, GirderBlock.TOP, GirderBlock.BOTTOM); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override @SideOnly(Side.CLIENT) public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT; }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                               EnumHand hand, EnumFacing side, float x, float y, float z) {
        return GirderPlacement.place(world, pos, state, player, hand, side, x, y, z)
                || super.onBlockActivated(world, pos, state, player, hand, side, x, y, z);
    }
    @Override public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.withProperty(GirderBlock.TOP, GirderBlock.supports(world, pos, EnumFacing.UP))
                .withProperty(GirderBlock.BOTTOM, GirderBlock.supports(world, pos, EnumFacing.DOWN));
    }
    private AxisAlignedBB beam(IBlockState state) {
        return getAxis(state) == EnumFacing.Axis.Z ? new AxisAlignedBB(0, .125, .25, 1, .875, .75)
                : new AxisAlignedBB(.25, .125, 0, .75, .875, 1);
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return beam(state).union(ShaftBlock.boundingBoxFor(getAxis(state)));
    }
    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox, List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actual) {
        addCollisionBoxToList(pos, entityBox, boxes, beam(state));
        addCollisionBoxToList(pos, entityBox, boxes, ShaftBlock.boundingBoxFor(getAxis(state)));
    }
    @Override public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        drops.add(new ItemStack(BlockFamily.get("metal_girder"))); drops.add(new ItemStack(AllBlocks.SHAFT));
    }
}
