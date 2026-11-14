package com.cogwork.cogworkbridge.content.decoration.palette;

import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.BlockFamily;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class CardboardBlock extends Block {
    public static final PropertyEnum<EnumFacing.Axis> AXIS = PropertyEnum.create("axis", EnumFacing.Axis.class, EnumFacing.Axis.X, EnumFacing.Axis.Z);
    private final boolean bound;
    public CardboardBlock(boolean bound) {
        super(Material.WOOD); this.bound = bound; setHardness(1F); setHarvestLevel("axe", 0);
        setDefaultState(blockState.getBaseState().withProperty(AXIS, EnumFacing.Axis.Z));
    }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, AXIS); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(AXIS, (meta & 1) == 0 ? EnumFacing.Axis.Z : EnumFacing.Axis.X); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(AXIS) == EnumFacing.Axis.X ? 1 : 0; }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face, float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(AXIS, placer.getHorizontalFacing().getAxis());
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing face, float x, float y, float z) {
        if (bound || player.getHeldItem(hand).getItem() != Items.STRING) return false;
        if (!world.isRemote) {
            Block replacement = BlockFamily.get("bound_cardboard_block");
            world.setBlockState(pos, replacement.getDefaultState().withProperty(AXIS, state.getValue(AXIS)), 3);
            if (!player.capabilities.isCreativeMode) player.getHeldItem(hand).shrink(1);
        }
        return true;
    }
    @Override public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        if (!bound) { super.getDrops(drops, world, pos, state, fortune); return; }
        drops.add(new ItemStack(AllBlocks.CARDBOARD_BLOCK));
        drops.add(new ItemStack(Items.STRING));
    }
    @Override protected boolean canSilkHarvest() { return true; }
}
