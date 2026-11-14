package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.SoundType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class SailBlock extends Block {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    private final boolean frame;
    public SailBlock(boolean frame) { super(Material.CLOTH); this.frame = frame; setHardness(.8F); setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.UP)); }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta)); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) { return getDefaultState().withProperty(FACING, side); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return (frame ? FRAME : SAIL).get(state.getValue(FACING)).bounds();
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        ItemStack held = player.getHeldItem(hand);
        if (!player.isSneaking() && player.canPlayerEdit(pos, side, held)) {
            Block heldBlock = Block.getBlockFromItem(held.getItem());
            if (heldBlock instanceof SailBlock sail && placeAdjacent(world, pos, state, player, hand, held, sail, x, y, z))
                return true;
        }
        SailBlock replacement = null;
        if (!frame && held.getItem() == Items.SHEARS) replacement = AllBlocks.SAIL_FRAME;
        else if (frame && held.getItem() == Item.getItemFromBlock(Blocks.WOOL)) replacement = AllBlocks.SAILS[EnumDyeColor.byMetadata(held.getMetadata()).getMetadata()];
        else if (!frame && held.getItem() == Items.DYE) replacement = AllBlocks.SAILS[EnumDyeColor.byDyeDamage(held.getMetadata()).getMetadata()];
        if (replacement == null || replacement == this) return false;
        if (!world.isRemote) {
            world.setBlockState(pos, replacement.getDefaultState().withProperty(FACING, state.getValue(FACING)), 3);
            if (!player.capabilities.isCreativeMode) {
                if (held.getItem() == Items.SHEARS) held.damageItem(1, player); else held.shrink(1);
            }
        }
        return true;
    }

    private boolean placeAdjacent(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                  ItemStack held, SailBlock sail, float hitX, float hitY, float hitZ) {
        BlockPos target = adjacentPlacement(world, pos, state, player, held, sail, hitX, hitY, hitZ);
        if (target == null) return false;
        if (!world.isRemote) {
            world.setBlockState(target, sail.getDefaultState().withProperty(FACING, state.getValue(FACING)), 3);
            world.playSound(null, target, SoundType.WOOD.getPlaceSound(), SoundCategory.BLOCKS,
                    SoundType.WOOD.getVolume(), SoundType.WOOD.getPitch());
            if (!player.capabilities.isCreativeMode) held.shrink(1);
        }
        return true;
    }

    /** The preview and the actual placement must choose the same available edge. */
    public static BlockPos adjacentPlacement(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                             ItemStack held, SailBlock sail, float hitX, float hitY, float hitZ) {
        EnumFacing.Axis normal = state.getValue(FACING).getAxis();
        Vec3d hit = new Vec3d(hitX, hitY, hitZ);
        EnumFacing targetSide = null;
        double nearest = Double.MAX_VALUE;
        for (EnumFacing direction : EnumFacing.values()) {
            if (direction.getAxis() == normal) continue;
            BlockPos target = pos.offset(direction);
            if (!world.getBlockState(target).getBlock().isReplaceable(world, target)
                    || !player.canPlayerEdit(target, direction.getOpposite(), held)
                    || !sail.canPlaceBlockAt(world, target)) continue;
            Vec3d center = new Vec3d(.5D + direction.getXOffset(), .5D + direction.getYOffset(),
                    .5D + direction.getZOffset());
            double distance = center.squareDistanceTo(hit);
            if (distance < nearest) {
                nearest = distance;
                targetSide = direction;
            }
        }
        return targetSide == null ? null : pos.offset(targetSide);
    }
    private static final java.util.Map<EnumFacing, com.cogwork.cogworkbridge.util.BlockShape> SAIL = com.cogwork.cogworkbridge.util.BlockShape.pixels(0,5,0,16,10,16).fromUp();
    private static final java.util.Map<EnumFacing, com.cogwork.cogworkbridge.util.BlockShape> FRAME = com.cogwork.cogworkbridge.util.BlockShape.pixels(0,5,0,16,9,16).fromUp();
    private static final java.util.Map<EnumFacing, com.cogwork.cogworkbridge.util.BlockShape> FRAME_COLLISION = com.cogwork.cogworkbridge.util.BlockShape.pixels(
            0,5,0,2,9,16, 14,5,0,16,9,16, 2,5,0,14,9,2, 2,5,14,14,9,16).fromUp();
    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
            java.util.List<AxisAlignedBB> boxes, @javax.annotation.Nullable net.minecraft.entity.Entity entity, boolean actual) {
        (frame ? FRAME_COLLISION : SAIL).get(state.getValue(FACING)).addCollisionBoxes(pos, query, boxes);
    }
}
