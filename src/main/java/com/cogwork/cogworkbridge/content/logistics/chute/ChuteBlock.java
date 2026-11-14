package com.cogwork.cogworkbridge.content.logistics.chute;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld;
import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public final class ChuteBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", facing -> facing != EnumFacing.UP);
    public static final IUnlistedProperty<String> MODEL = new IUnlistedProperty<String>() {
        @Override public String getName() { return "chute_model"; }
        @Override public boolean isValid(String value) { return value != null; }
        @Override public Class<String> getType() { return String.class; }
        @Override public String valueToString(String value) { return value; }
    };
    private static final Map<EnumFacing, List<AxisAlignedBB>> SLOPE_SHAPES = createSlopeShapes();
    private final boolean smart;

    public ChuteBlock(boolean smart) {
        super(Material.IRON);
        this.smart = smart;
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.DOWN));
        setHardness(2.0F);
        setResistance(6.0F);
        setSoundType(SoundType.METAL);
        setLightOpacity(0);
    }
    public boolean isSmart() { return smart; }
    @Override protected net.minecraft.block.state.BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{FACING}, new IUnlistedProperty<?>[]{MODEL});
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byIndex(Math.floorMod(meta, 6));
        return getDefaultState().withProperty(FACING, facing == EnumFacing.UP ? EnumFacing.DOWN : facing);
    }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        EnumFacing facing = EnumFacing.DOWN;
        if (!smart && !placer.isSneaking() && side.getAxis().isHorizontal()
                && world.getBlockState(pos.down().offset(side.getOpposite())).getBlock() instanceof ChuteBlock) facing = side;
        return getDefaultState().withProperty(FACING, facing);
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withProperty(FACING, mirror.mirror(state.getValue(FACING)));
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState extended)) return state;
        TileEntity tile = world.getTileEntity(pos);
        if (smart) return extended.withProperty(MODEL, tile instanceof ChuteTileEntity chute && chute.isPowered()
                ? "smart_powered" : "smart");
        EnumFacing facing = state.getValue(FACING);
        boolean diagonalInput = hasDiagonalInput(world, pos);
        String name = facing == EnumFacing.DOWN
                ? diagonalInput ? "intersection" : tile instanceof ChuteTileEntity chute && chute.hasWindow() ? "window" : "normal"
                : diagonalInput ? "diagonal_intersection" : "diagonal";
        return extended.withProperty(MODEL, name);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) {
        return smart ? new SmartChuteTileEntity() : new ChuteTileEntity();
    }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    private static boolean hasDiagonalInput(IBlockAccess world, BlockPos pos) {
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
            IBlockState above = world.getBlockState(pos.up().offset(side));
            if (above.getBlock() instanceof ChuteBlock && above.getValue(FACING) == side) return true;
        }
        return false;
    }

    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        EnumFacing facing = state.getValue(FACING);
        boolean intersection = hasDiagonalInput(world, pos);
        if (smart || (facing == EnumFacing.DOWN && intersection)) return FULL_BLOCK_AABB;
        if (facing == EnumFacing.DOWN) return new AxisAlignedBB(1 / 16D, 0, 1 / 16D, 15 / 16D, 1, 15 / 16D);
        List<AxisAlignedBB> shapes = SLOPE_SHAPES.get(facing);
        AxisAlignedBB result = intersection ? FULL_BLOCK_AABB : shapes.get(0);
        for (AxisAlignedBB box : shapes) result = result.union(intersection ? box.setMaxY(Math.min(box.maxY, 1)) : box);
        return result;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        EnumFacing facing = state.getValue(FACING);
        boolean intersection = hasDiagonalInput(world, pos);
        if (smart || (facing == EnumFacing.DOWN && intersection)) {
            addBox(pos, query, boxes, FULL_BLOCK_AABB);
        } else if (facing == EnumFacing.DOWN) {
            addBox(pos, query, boxes, new AxisAlignedBB(2 / 16D, 0, 2 / 16D, 14 / 16D, 8 / 16D, 14 / 16D));
            addBox(pos, query, boxes, new AxisAlignedBB(1 / 16D, 8 / 16D, 1 / 16D, 15 / 16D, 1, 15 / 16D));
        } else {
            if (intersection) addBox(pos, query, boxes, FULL_BLOCK_AABB);
            double maxHeight = intersection ? 1D : 24 / 16D;
            for (AxisAlignedBB shape : SLOPE_SHAPES.get(facing)) {
                double minY = Math.max(0, shape.minY), maxY = Math.min(maxHeight, shape.maxY);
                if (minY < maxY) addBox(pos, query, boxes,
                        new AxisAlignedBB(shape.minX, minY, shape.minZ, shape.maxX, maxY, shape.maxZ));
            }
        }
    }

    private static Map<EnumFacing, List<AxisAlignedBB>> createSlopeShapes() {
        Map<EnumFacing, List<AxisAlignedBB>> shapes = new EnumMap<>(EnumFacing.class);
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            List<AxisAlignedBB> pieces = new ArrayList<>(16);
            for (int i = 0; i < 16; i++) {
                AxisAlignedBB piece = new AxisAlignedBB(1 / 16D, (i - 15) / 16D, i / 16D,
                        15 / 16D, (i + 4) / 16D, (i + 1) / 16D);
                pieces.add(rotateFromSouth(piece, direction));
            }
            shapes.put(direction, Collections.unmodifiableList(pieces));
        }
        return Collections.unmodifiableMap(shapes);
    }

    private static AxisAlignedBB rotateFromSouth(AxisAlignedBB box, EnumFacing direction) {
        return switch (direction) {
            case NORTH -> new AxisAlignedBB(1 - box.maxX, box.minY, 1 - box.maxZ,
                    1 - box.minX, box.maxY, 1 - box.minZ);
            case EAST -> new AxisAlignedBB(box.minZ, box.minY, 1 - box.maxX,
                    box.maxZ, box.maxY, 1 - box.minX);
            case WEST -> new AxisAlignedBB(1 - box.maxZ, box.minY, box.minX,
                    1 - box.minZ, box.maxY, box.maxX);
            default -> box;
        };
    }

    private static void addBox(BlockPos pos, AxisAlignedBB query, List<AxisAlignedBB> boxes, AxisAlignedBB local) {
        AxisAlignedBB box = local.offset(pos);
        if (query.intersects(box)) boxes.add(box);
    }

    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos neighbor) {
        if (world.getTileEntity(pos) instanceof ChuteTileEntity chute) chute.neighborsChanged();
        world.markBlockRangeForRenderUpdate(pos, pos);
    }
    @Override public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (!world.isRemote && entity instanceof EntityItem item && item.posY >= pos.getY() + 0.75D
                && world.getTileEntity(pos) instanceof ChuteTileEntity chute) chute.acceptEntity(item);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!(world.getTileEntity(pos) instanceof ChuteTileEntity chute)) return false;
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == AllItems.WRENCH) return false;
        boolean filterSlot = smart && side.getAxis().isHorizontal() && hitY > 0.45F && hitY < 0.9F
                && (side.getAxis() == EnumFacing.Axis.X ? Math.abs(hitZ - 0.5F) : Math.abs(hitX - 0.5F)) < 0.2F;
        if (filterSlot) {
            if (!world.isRemote && player.isSneaking() && held.isEmpty()) {
                chute.toggleExact();
                player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                        "cogworkbridge.logistics." + (chute.isExact() ? "exact" : "up_to")), true);
            }
            else if (!world.isRemote) chute.selectFilter(player, hand);
            return true;
        }
        if (!held.isEmpty() || chute.getItem().isEmpty()) return false;
        if (!world.isRemote) ItemHandlerHelper.giveItemToPlayer(player, chute.takeAll());
        return true;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof ChuteTileEntity chute) {
            drop(world, pos, chute.takeAll());
            ItemStack filter = chute.getFilter();
            if (ListFilterItem.isFilterItem(filter)) drop(world, pos, filter);
        }
        super.breakBlock(world, pos, state);
    }
    static void drop(World world, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (world instanceof ContraptionWorld local) local.spawnCapturedDrop(pos, stack);
        else if (world instanceof FakeWorld fake
                && fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity entity)
            entity.spawnDrop(pos, Collections.singletonList(stack));
        else if (!(world instanceof FakeWorld)) InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
    }
}
