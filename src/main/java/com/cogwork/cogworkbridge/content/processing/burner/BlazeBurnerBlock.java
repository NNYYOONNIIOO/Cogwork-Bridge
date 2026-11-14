package com.cogwork.cogworkbridge.content.processing.burner;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSpade;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.items.ItemHandlerHelper;
import com.cogwork.cogworkbridge.content.processing.burner.BlazeBurnerTileEntity.FlameType;
import xy177.farmersfuturedelight.core.FFDSoulFireHooks;
import java.util.List;
import javax.annotation.Nullable;

public final class BlazeBurnerBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final IUnlistedProperty<FlameType> FLAME_TYPE = new IUnlistedProperty<FlameType>() {
        @Override public String getName() { return "flame_type"; }
        @Override public boolean isValid(FlameType value) { return value != null; }
        @Override public Class<FlameType> getType() { return FlameType.class; }
        @Override public String valueToString(FlameType value) { return value.name(); }
    };
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(1 / 16D, 0, 1 / 16D, 15 / 16D, 14 / 16D, 15 / 16D);
    private static final AxisAlignedBB BASE = new AxisAlignedBB(0, 0, 0, 1, 4 / 16D, 1);
    private static final AxisAlignedBB[] EMPTY_CAGE = {
            BASE,
            new AxisAlignedBB(1 / 16D, 4 / 16D, 1 / 16D, 3 / 16D, 14 / 16D, 15 / 16D),
            new AxisAlignedBB(13 / 16D, 4 / 16D, 1 / 16D, 15 / 16D, 14 / 16D, 15 / 16D),
            new AxisAlignedBB(3 / 16D, 4 / 16D, 1 / 16D, 13 / 16D, 14 / 16D, 3 / 16D),
            new AxisAlignedBB(3 / 16D, 4 / 16D, 13 / 16D, 13 / 16D, 14 / 16D, 15 / 16D)
    };
    private final boolean occupied;

    public BlazeBurnerBlock(boolean occupied) {
        super(Material.IRON);
        this.occupied = occupied;
        setHardness(2.0F);
        setResistance(6.0F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 1);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH));
    }

    public boolean isOccupied() { return occupied; }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new BlazeBurnerTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return SHAPE; }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return occupied ? SHAPE : BASE; }
    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
            List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actual) {
        if (entity == null) { addCollisionBoxToList(pos, query, boxes, BASE); return; }
        if (occupied) { addCollisionBoxToList(pos, query, boxes, SHAPE); return; }
        for (AxisAlignedBB part : EMPTY_CAGE) addCollisionBoxToList(pos, query, boxes, part);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{FACING}, new IUnlistedProperty<?>[]{FLAME_TYPE});
    }

    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        FlameType flame = tile instanceof BlazeBurnerTileEntity burner ? burner.getEmptyFlame() : FlameType.NONE;
        return ((IExtendedBlockState) state).withProperty(FLAME_TYPE, flame);
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof BlazeBurnerTileEntity burner)) return occupied ? 8 : 0;
        if (!occupied) return switch (burner.getEmptyFlame()) {
            case NONE -> 0;
            case REGULAR -> 12;
            case SOUL -> 9;
        };
        return switch (burner.getHeatLevel()) {
            case NONE -> 0;
            case SMOULDERING -> 8;
            default -> 15;
        };
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!(world.getTileEntity(pos) instanceof BlazeBurnerTileEntity burner)) return false;
        ItemStack held = player.getHeldItem(hand);
        if (occupied && held.getItem() == com.cogwork.cogworkbridge.registry.AllItems.GOGGLES && !burner.hasGoggles()) {
            if (!world.isRemote) burner.setGoggles(true);
            return true;
        }
        if (occupied && held.isEmpty() && burner.hasGoggles()) {
            if (!world.isRemote) burner.setGoggles(false);
            return true;
        }
        if (held.isEmpty()) return false;
        if (!occupied) {
            if (burner.isEmptyLit()) {
                if (held.getItem() instanceof ItemSpade) {
                    if (!world.isRemote) {
                        burner.setEmptyFlame(FlameType.NONE);
                        if (!player.capabilities.isCreativeMode) held.damageItem(1, player);
                        world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH,
                                SoundCategory.BLOCKS, 0.5F, 2.0F);
                    }
                    return true;
                }
                if (burner.getEmptyFlame() == FlameType.REGULAR && FFDSoulFireHooks.isSoulBase(
                        Block.getBlockFromItem(held.getItem()).getDefaultState())) {
                    if (!world.isRemote) {
                        burner.setEmptyFlame(FlameType.SOUL);
                        world.playSound(null, pos, SoundEvents.BLOCK_SAND_PLACE,
                                SoundCategory.BLOCKS, 1.0F, world.rand.nextFloat() * 0.4F + 0.8F);
                    }
                    return true;
                }
                return false;
            }
            boolean flint = held.getItem() == Items.FLINT_AND_STEEL;
            boolean charge = held.getItem() == Items.FIRE_CHARGE;
            if (!flint && !charge) return false;
            if (!world.isRemote) {
                burner.setEmptyFlame(FlameType.REGULAR);
                if (!player.capabilities.isCreativeMode) {
                    if (flint) held.damageItem(1, player);
                    else held.shrink(1);
                }
                world.playSound(null, pos, flint ? SoundEvents.ITEM_FLINTANDSTEEL_USE : SoundEvents.ITEM_FIRECHARGE_USE,
                        SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
            return true;
        }
        if (!burner.canFeed(held)) return false;
        if (!world.isRemote && burner.feed(held)) {
            if (!player.capabilities.isCreativeMode) {
                ItemStack container = held.getItem().getContainerItem(held);
                held.shrink(1);
                if (!container.isEmpty()) {
                    if (held.isEmpty()) player.setHeldItem(hand, container);
                    else ItemHandlerHelper.giveItemToPlayer(player, container);
                }
            }
            world.playSound(null, pos, SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.BLOCKS, 0.7F, 1.0F);
        }
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof BlazeBurnerTileEntity burner) burner.dropContents(world, pos);
        super.breakBlock(world, pos, state);
    }
}
