package com.cogwork.cogworkbridge.content.decoration.palette;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import com.cogwork.cogworkbridge.content.decoration.ConnectedTextureData;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

/** 1.12 state and collision adapters for Create's building palette. */
public final class PaletteBlocks {
    private PaletteBlocks() { }

    public static class Pillar extends BlockRotatedPillar {
        public Pillar(Material material) { super(material); setHardness(1.5F); setHarvestLevel("pickaxe", 0); }
        @Override protected BlockStateContainer createBlockState() {
            return new ExtendedBlockState(this, new IProperty<?>[]{AXIS}, new IUnlistedProperty<?>[]{ConnectedTextureData.CONTEXT});
        }
        @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
            return ((IExtendedBlockState) state).withProperty(ConnectedTextureData.CONTEXT, ConnectedTextureData.capture(world, pos, state));
        }
    }

    public static class Layered extends Block {
        public Layered() { super(Material.ROCK); setHardness(1.5F); setHarvestLevel("pickaxe", 0); }
        @Override protected BlockStateContainer createBlockState() {
            return new ExtendedBlockState(this, new IProperty<?>[0], new IUnlistedProperty<?>[]{ConnectedTextureData.CONTEXT});
        }
        @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
            return ((IExtendedBlockState) state).withProperty(ConnectedTextureData.CONTEXT, ConnectedTextureData.capture(world, pos, state));
        }
    }

    public static class Stairs extends BlockStairs {
        public Stairs(Block base) { super(base.getDefaultState()); setHarvestLevel("pickaxe", 0); }
    }

    public static class Slab extends Block {
        private static final AxisAlignedBB LOWER = new AxisAlignedBB(0, 0, 0, 1, .5, 1);
        private static final AxisAlignedBB UPPER = new AxisAlignedBB(0, .5, 0, 1, 1, 1);
        private Block doubleSlab;

        public Slab(Material material) {
            super(material);
            setDefaultState(blockState.getBaseState().withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.BOTTOM));
            setHardness(1.5F);
            setHarvestLevel("pickaxe", 0);
        }

        public void setDoubleSlab(Block block) { doubleSlab = block; }
        public Block getDoubleSlab() { return doubleSlab; }
        @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, BlockStairs.HALF); }
        @Override public IBlockState getStateFromMeta(int meta) {
            return getDefaultState().withProperty(BlockStairs.HALF, (meta & 1) != 0 ? BlockStairs.EnumHalf.TOP : BlockStairs.EnumHalf.BOTTOM);
        }
        @Override public int getMetaFromState(IBlockState state) { return state.getValue(BlockStairs.HALF) == BlockStairs.EnumHalf.TOP ? 1 : 0; }
        @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float x, float y, float z, int meta, EntityLivingBase placer) {
            return getDefaultState().withProperty(BlockStairs.HALF, facing == EnumFacing.DOWN || facing.getAxis().isHorizontal() && y > .5F
                    ? BlockStairs.EnumHalf.TOP : BlockStairs.EnumHalf.BOTTOM);
        }
        @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
            return state.getValue(BlockStairs.HALF) == BlockStairs.EnumHalf.TOP ? UPPER : LOWER;
        }
        @Override public boolean isOpaqueCube(IBlockState state) { return false; }
        @Override public boolean isFullCube(IBlockState state) { return false; }
        @Override public boolean isTopSolid(IBlockState state) { return state.getValue(BlockStairs.HALF) == BlockStairs.EnumHalf.TOP; }
        @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing side) {
            boolean top = state.getValue(BlockStairs.HALF) == BlockStairs.EnumHalf.TOP;
            return side == (top ? EnumFacing.UP : EnumFacing.DOWN) ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
        }
    }

    public static class DoubleSlab extends Block {
        private final Slab single;
        public DoubleSlab(Slab single) { super(single.getDefaultState().getMaterial()); this.single = single; setHardness(1.5F); setHarvestLevel("pickaxe", 0); }
        @Override public Item getItemDropped(IBlockState state, Random random, int fortune) { return Item.getItemFromBlock(single); }
        @Override public int quantityDropped(Random random) { return 2; }
        @Override public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) { return new ItemStack(single); }
    }

    public static class SlabItem extends ItemBlock {
        public SlabItem(Block block) { super(block); }
        @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing face, float x, float y, float z) {
            ItemStack stack = player.getHeldItem(hand);
            Slab slab = (Slab) block;
            IBlockState current = world.getBlockState(pos);
            if (current.getBlock() == slab) {
                boolean top = current.getValue(BlockStairs.HALF) == BlockStairs.EnumHalf.TOP;
                if ((!top && face == EnumFacing.UP || top && face == EnumFacing.DOWN) && merge(player, world, pos, face, stack, slab)) return EnumActionResult.SUCCESS;
            }
            BlockPos adjacent = pos.offset(face);
            if (world.getBlockState(adjacent).getBlock() == slab && merge(player, world, adjacent, face, stack, slab)) return EnumActionResult.SUCCESS;
            return super.onItemUse(player, world, pos, hand, face, x, y, z);
        }
        private static boolean merge(EntityPlayer player, World world, BlockPos pos, EnumFacing face, ItemStack stack, Slab slab) {
            if (stack.isEmpty() || !player.canPlayerEdit(pos, face, stack) || !world.checkNoEntityCollision(new AxisAlignedBB(pos))) return false;
            IBlockState state = slab.getDoubleSlab().getDefaultState();
            if (!world.setBlockState(pos, state, 3)) return false;
            net.minecraft.block.SoundType sound = slab.getSoundType(state, world, pos, player);
            world.playSound(player, pos, sound.getPlaceSound(), net.minecraft.util.SoundCategory.BLOCKS, (sound.getVolume() + 1F) / 2F, sound.getPitch() * .8F);
            if (!player.capabilities.isCreativeMode) stack.shrink(1);
            return true;
        }
    }

    public static class Wall extends BlockWall {
        public Wall(Block base) { super(base); setHarvestLevel("pickaxe", 0); }
        @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState(); }
        @Override public int getMetaFromState(IBlockState state) { return 0; }
        @Override public int damageDropped(IBlockState state) { return 0; }
        @Override public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> items) { items.add(new ItemStack(this)); }
        @Override public String getLocalizedName() { return net.minecraft.util.text.translation.I18n.translateToLocal(getTranslationKey() + ".name"); }
    }

    public static class Glass extends BlockGlass {
        public Glass() { super(Material.GLASS, false); setHardness(.3F); }
        @Override protected BlockStateContainer createBlockState() {
            return new ExtendedBlockState(this, new IProperty<?>[0], new IUnlistedProperty<?>[]{ConnectedTextureData.CONTEXT});
        }
        @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
            return ((IExtendedBlockState) state).withProperty(ConnectedTextureData.CONTEXT, ConnectedTextureData.capture(world, pos, state));
        }
        @Override @SideOnly(Side.CLIENT) public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.TRANSLUCENT; }
    }

    public static class Pane extends BlockPane {
        public Pane(boolean bars) { super(bars ? Material.IRON : Material.GLASS, bars); setHardness(bars ? 5F : .3F); }
        @Override protected BlockStateContainer createBlockState() {
            return new ExtendedBlockState(this, new IProperty<?>[]{NORTH, SOUTH, EAST, WEST}, new IUnlistedProperty<?>[]{ConnectedTextureData.CONTEXT});
        }
        @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
            return ((IExtendedBlockState) state).withProperty(ConnectedTextureData.CONTEXT, ConnectedTextureData.capture(world, pos, state));
        }
        @Override @SideOnly(Side.CLIENT) public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
            // A neighboring pane may cover its center but not every arm.
            if (side.getAxis().isVertical() && world.getBlockState(pos.offset(side)).getBlock() == this) return true;
            return super.shouldSideBeRendered(state, world, pos, side);
        }
        @Override @SideOnly(Side.CLIENT) public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.TRANSLUCENT; }
    }

    public static class Ladder extends BlockLadder {
        public Ladder() { super(); setHardness(3F); setHarvestLevel("pickaxe", 0); }
        @Override @SideOnly(Side.CLIENT) public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
            // The hoop projects into the block above; its cullface is UP in the upstream model.
            if (side == EnumFacing.UP && world.getBlockState(pos.up()).getBlock() instanceof BlockLadder) return false;
            return super.shouldSideBeRendered(state, world, pos, side);
        }
        private boolean supported(IBlockState state, IBlockAccess world, BlockPos pos) {
            EnumFacing face = state.getValue(FACING);
            BlockPos wall = pos.offset(face.getOpposite());
            IBlockState above = world.getBlockState(pos.up());
            return world.getBlockState(wall).isSideSolid(world, wall, face)
                    || above.getBlock() == this && above.getValue(FACING) == face;
        }
        @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block from, BlockPos fromPos) {
            if (!world.isRemote && !supported(state, world, pos)) { dropBlockAsItem(world, pos, state, 0); world.setBlockToAir(pos); }
        }
        @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing face, float x, float y, float z) {
            ItemStack held = player.getHeldItem(hand);
            if (player.isSneaking() || !(held.getItem() instanceof ItemBlock item) || !(item.getBlock() instanceof Ladder next)) return false;
            BlockPos target = placementTarget(world, pos, state, player, held, face, next);
            if (target == null) return true;
            IBlockState placed = next.getDefaultState().withProperty(FACING, state.getValue(FACING));
            if (!world.isRemote && world.setBlockState(target, placed, 3)) {
                net.minecraft.block.SoundType sound = next.getSoundType(placed, world, target, player);
                world.playSound(null, target, sound.getPlaceSound(), net.minecraft.util.SoundCategory.BLOCKS, (sound.getVolume() + 1F) / 2F, sound.getPitch() * .8F);
                if (!player.capabilities.isCreativeMode) held.shrink(1);
            }
            return true;
        }
        @Nullable public BlockPos placementTarget(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                                   ItemStack held, EnumFacing face, Ladder next) {
            if (player.isSneaking()) return null;
            EnumFacing direction = player.rotationPitch < 0 ? EnumFacing.UP : EnumFacing.DOWN;
            BlockPos target = pos.offset(direction); int count = 0;
            while (world.isBlockLoaded(target) && world.getBlockState(target).getBlock() instanceof BlockLadder && count++ < 16) target = target.offset(direction);
            if (count >= 16 || !world.isBlockLoaded(target) || !world.getWorldBorder().contains(target)
                    || !world.getBlockState(target).getBlock().isReplaceable(world, target)
                    || !player.canPlayerEdit(target, face, held) || !world.isBlockModifiable(player, target)) return null;
            IBlockState placed = next.getDefaultState().withProperty(FACING, state.getValue(FACING));
            return next.supported(placed, world, target) ? target.toImmutable() : null;
        }
        @Override @SideOnly(Side.CLIENT) public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT; }
    }

    public static class Experience extends Block {
        public Experience() { super(Material.IRON); setHardness(3F); setLightLevel(1F); setHarvestLevel("pickaxe", 0); }
        @Override @SideOnly(Side.CLIENT) public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random random) {
            if (random.nextInt(5) != 0) return;
            double x = pos.getX() + .5D + (random.nextDouble() - .5D) * 1.1D;
            double y = pos.getY() + .5D + (random.nextDouble() - .5D) * 1.1D;
            double z = pos.getZ() + .5D + (random.nextDouble() - .5D) * 1.1D;
            world.spawnParticle(net.minecraft.util.EnumParticleTypes.END_ROD, x, y, z, random.nextGaussian() * .005, random.nextGaussian() * .005, random.nextGaussian() * .005);
        }
    }
}
