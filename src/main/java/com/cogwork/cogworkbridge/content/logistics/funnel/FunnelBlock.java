package com.cogwork.cogworkbridge.content.logistics.funnel;

import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.content.logistics.depot.DepotTileEntity;
import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
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
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class FunnelBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final IUnlistedProperty<String> MODEL = new IUnlistedProperty<String>() {
        @Override public String getName() { return "funnel_model"; }
        @Override public boolean isValid(String value) { return value != null; }
        @Override public Class<String> getType() { return String.class; }
        @Override public String valueToString(String value) { return value; }
    };
    private final boolean brass;
    public FunnelBlock(boolean brass) {
        super(Material.IRON);
        this.brass = brass;
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH));
        setHardness(2.0F);
        setResistance(6.0F);
        setSoundType(SoundType.METAL);
        setLightOpacity(0);
    }
    public boolean isBrass() { return brass; }
    @Override protected net.minecraft.block.state.BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{FACING}, new IUnlistedProperty<?>[]{MODEL});
    }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(Math.floorMod(meta, 6)));
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, side);
    }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof FunnelTileEntity funnel)
            funnel.setExtracting(!placer.isSneaking());
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
        boolean extracting = !(tile instanceof FunnelTileEntity funnel) || funnel.isExtracting();
        boolean powered = tile instanceof FunnelTileEntity funnel && funnel.isPowered();
        EnumFacing face = state.getValue(FACING);
        TileEntity surface = world.getTileEntity(pos.down());
        if (face.getAxis().isHorizontal() && (surface instanceof BeltTileEntity || surface instanceof DepotTileEntity)) {
            EnumFacing.Axis pathAxis = surface instanceof BeltTileEntity belt
                    ? (belt.step().getX() == 0 ? EnumFacing.Axis.Z : EnumFacing.Axis.X) : EnumFacing.Axis.Y;
            String shape = face.getAxis() == pathAxis
                    ? tile instanceof FunnelTileEntity funnel && funnel.isExtended() ? "extended" : "retracted"
                    : extracting ? "pushing" : "pulling";
            return extended.withProperty(MODEL, "belt_" + shape + "_" + (powered ? "powered" : "unpowered"));
        }
        return extended.withProperty(MODEL, (extracting ? "push_" : "pull_") + (powered ? "powered" : "unpowered"));
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) {
        return brass ? new BrassFunnelTileEntity() : new FunnelTileEntity();
    }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        List<AxisAlignedBB> shapes = shapes(state, world, pos);
        AxisAlignedBB bounds = shapes.get(0);
        for (int i = 1; i < shapes.size(); i++) bounds = bounds.union(shapes.get(i));
        return bounds;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return getBoundingBox(state, world, pos);
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        EnumFacing facing = state.getValue(FACING);
        List<AxisAlignedBB> shapes = entity instanceof EntityItem && facing.getAxis().isHorizontal()
                && !isAlignedBelt(world, pos, facing)
                ? java.util.Collections.singletonList(itemCollisionShape(facing)) : shapes(state, world, pos);
        for (AxisAlignedBB local : shapes) {
            if ((world instanceof FakeWorld || world instanceof ContraptionWorld) && local.minY < 0) {
                local = new AxisAlignedBB(local.minX, 0, local.minZ,
                        local.maxX, local.maxY, local.maxZ);
            }
            AxisAlignedBB box = local.offset(pos);
            if (query.intersects(box)) boxes.add(box);
        }
    }

    private static boolean isAlignedBelt(IBlockAccess world, BlockPos pos, EnumFacing facing) {
        if (!facing.getAxis().isHorizontal() || !(world.getTileEntity(pos.down()) instanceof BeltTileEntity belt)) return false;
        BlockPos step = belt.step();
        return (step.getX() != 0 ? EnumFacing.Axis.X : step.getZ() != 0 ? EnumFacing.Axis.Z : EnumFacing.Axis.Y)
                == facing.getAxis();
    }

    private static List<AxisAlignedBB> shapes(IBlockState state, IBlockAccess world, BlockPos pos) {
        EnumFacing facing = state.getValue(FACING);
        List<AxisAlignedBB> boxes = new ArrayList<>(6);
        if (isAlignedBelt(world, pos, facing)) {
            boxes.add(box16(2, -2, 14, 14, 14, 18));
            boolean extended = world.getTileEntity(pos) instanceof FunnelTileEntity funnel && funnel.isExtended();
            if (extended) {
                boxes.add(box16(3, -4, 10, 13, 13, 14));
                boxes.add(box16(2, -4, 6, 14, 14, 10));
                boxes.add(box16(0, -5, 0, 16, 16, 6));
            } else boxes.add(box16(0, -5, 8, 16, 16, 14));
            return rotateFromNorth(boxes, facing);
        }
        if (facing == EnumFacing.UP) {
            boxes.add(box16(2, -2, 2, 14, 8, 14));
            boxes.add(box16(1, 1, 1, 15, 8, 15));
            boxes.add(box16(0, 4, 0, 16, 10, 16));
            return boxes;
        }
        if (facing == EnumFacing.DOWN) {
            boxes.add(box16(2, 8, 2, 14, 18, 14));
            boxes.add(box16(1, 8, 1, 15, 15, 15));
            boxes.add(box16(0, 6, 0, 16, 12, 16));
            return boxes;
        }

        boxes.add(box16(2, 2, 14, 14, 14, 18));
        boxes.add(box16(1, 8, 12, 15, 15, 14));
        boxes.add(box16(0.1D, 13, 7, 15.9D, 15, 11));
        boxes.add(box16(0.1D, 9, 8, 15.9D, 13, 12));
        boxes.add(box16(0.1D, 5, 9, 15.9D, 9, 13));
        boxes.add(box16(0.1D, 1, 10, 15.9D, 5, 14));
        boxes.add(box16(0.1D, -1, 11, 15.9D, 1, 15));
        return rotateFromNorth(boxes, facing);
    }

    private static AxisAlignedBB itemCollisionShape(EnumFacing facing) {
        return switch (facing) {
            case NORTH -> box16(0, 0, 0, 16, 16, 4);
            case SOUTH -> box16(0, 0, 12, 16, 16, 16);
            case WEST -> box16(0, 0, 0, 4, 16, 16);
            case EAST -> box16(12, 0, 0, 16, 16, 16);
            default -> FULL_BLOCK_AABB;
        };
    }

    private static List<AxisAlignedBB> rotateFromNorth(List<AxisAlignedBB> boxes, EnumFacing facing) {
        if (facing == EnumFacing.NORTH) return boxes;
        List<AxisAlignedBB> rotated = new ArrayList<>(boxes.size());
        for (AxisAlignedBB box : boxes) {
            if (facing == EnumFacing.SOUTH) {
                rotated.add(new AxisAlignedBB(1 - box.maxX, box.minY, 1 - box.maxZ,
                        1 - box.minX, box.maxY, 1 - box.minZ));
            } else if (facing == EnumFacing.EAST) {
                rotated.add(new AxisAlignedBB(1 - box.maxZ, box.minY, box.minX,
                        1 - box.minZ, box.maxY, box.maxX));
            } else {
                rotated.add(new AxisAlignedBB(box.minZ, box.minY, 1 - box.maxX,
                        box.maxZ, box.maxY, 1 - box.minX));
            }
        }
        return rotated;
    }

    private static AxisAlignedBB box16(double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
        return new AxisAlignedBB(minX / 16D, minY / 16D, minZ / 16D,
                maxX / 16D, maxY / 16D, maxZ / 16D);
    }
    @Override public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (!world.isRemote && entity instanceof EntityItem item
                && world.getTileEntity(pos) instanceof FunnelTileEntity funnel) funnel.acceptEntity(item);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!(world.getTileEntity(pos) instanceof FunnelTileEntity funnel)) return false;
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == AllItems.WRENCH) return false;
        EnumFacing facing = state.getValue(FACING);
        boolean filterSlot = brass && isFilterSlot(world, pos, state, side, hitX, hitY, hitZ);
        if (filterSlot) {
            if (!world.isRemote && (!held.isEmpty() || player.isSneaking())) funnel.selectFilter(player, hand);
            return true;
        }
        if (side != facing || funnel.isExtracting() || held.isEmpty()) return false;
        if (!world.isRemote) player.setHeldItem(hand, funnel.insertItem(0, held, false));
        return true;
    }
    public static boolean isFilterSlot(IBlockAccess world, BlockPos pos, IBlockState state, EnumFacing side,
                                       float hitX, float hitY, float hitZ) {
        EnumFacing facing = state.getValue(FACING);
        if (facing.getAxis().isHorizontal() && isAlignedBelt(world, pos, facing)
                && world.getTileEntity(pos) instanceof FunnelTileEntity funnel && funnel.isExtended()) {
            float lateral = facing.getAxis() == EnumFacing.Axis.X ? hitZ : hitX;
            float along = facing.getAxis() == EnumFacing.Axis.X ? hitX : hitZ;
            float center = 0.5F + (facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1F : -1F) * 5F / 16F;
            return side == EnumFacing.UP && Math.abs(lateral - 0.5F) < 0.2F
                    && Math.abs(along - center) < 0.2F;
        }
        boolean filterHeight = facing == EnumFacing.UP ? hitY < 0.35F : hitY > 0.55F;
        return (facing.getAxis().isVertical() ? side.getAxis().isHorizontal() : side == facing)
                && filterHeight && (side.getAxis() == EnumFacing.Axis.X
                ? Math.abs(hitZ - 0.5F) : Math.abs(hitX - 0.5F)) < 0.2F;
    }

    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof FunnelTileEntity funnel) {
            ItemStack filter = funnel.getFilter();
            if (ListFilterItem.isFilterItem(filter)) ItemTransfer.drop(world, pos, filter);
        }
        super.breakBlock(world, pos, state);
    }
}
