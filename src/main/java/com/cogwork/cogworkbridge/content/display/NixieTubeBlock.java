package com.cogwork.cogworkbridge.content.display;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Rotation;
import net.minecraft.util.IStringSerializable;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class NixieTubeBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyEnum<Mount> MOUNT = PropertyEnum.create("mount", Mount.class);
    public enum Mount implements IStringSerializable {
        FLOOR, WALL, WALL_REVERSED, CEILING;
        @Override public String getName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public NixieTubeBlock() {
        super(Material.GLASS); setHardness(.3F); setLightLevel(.375F);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH).withProperty(MOUNT, Mount.FLOOR));
    }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, MOUNT); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex() | state.getValue(MOUNT).ordinal() << 2; }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)).withProperty(MOUNT, Mount.values()[(meta >> 2) & 3]); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y,
                                                       float z, int meta, EntityLivingBase placer) {
        if (side.getAxis().isVertical()) return getDefaultState()
                .withProperty(FACING, placer.getHorizontalFacing().rotateY())
                .withProperty(MOUNT, side == EnumFacing.UP ? Mount.FLOOR : Mount.CEILING);
        Vec3d look = placer.getLookVec();
        EnumFacing lateral = side.getOpposite().rotateY();
        boolean reversed = look.x * lateral.getXOffset() + look.z * lateral.getZOffset() < 0;
        return getDefaultState().withProperty(FACING, side).withProperty(MOUNT, reversed ? Mount.WALL_REVERSED : Mount.WALL);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new NixieTubeTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.TRANSLUCENT; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        EnumFacing facing = state.getValue(FACING);
        Mount mount = state.getValue(MOUNT);
        if (mount == Mount.FLOOR || mount == Mount.CEILING) {
            double minY = mount == Mount.FLOOR ? 0 : .25;
            double maxY = mount == Mount.FLOOR ? .75 : 1;
            return facing.getAxis() == EnumFacing.Axis.Z
                    ? new AxisAlignedBB(5 / 16D, minY, 1 / 16D, 11 / 16D, maxY, 15 / 16D)
                    : new AxisAlignedBB(1 / 16D, minY, 5 / 16D, 15 / 16D, maxY, 11 / 16D);
        }
        return switch (facing) {
            case NORTH -> new AxisAlignedBB(5 / 16D, 1 / 16D, .25, 11 / 16D, 15 / 16D, 1);
            case SOUTH -> new AxisAlignedBB(5 / 16D, 1 / 16D, 0, 11 / 16D, 15 / 16D, .75);
            case WEST -> new AxisAlignedBB(.25, 1 / 16D, 5 / 16D, 1, 15 / 16D, 11 / 16D);
            default -> new AxisAlignedBB(0, 1 / 16D, 5 / 16D, .75, 15 / 16D, 11 / 16D);
        };
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                               EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.isSneaking()) return false;
        ItemStack held = player.getHeldItem(hand);
        boolean clipboard = held.getItem() == Item.getItemFromBlock(com.cogwork.cogworkbridge.registry.AllBlocks.CLIPBOARD);
        EnumDyeColor dye = DisplayDye.color(held);
        if (!held.isEmpty() && !clipboard && dye == null && (held.getItem() != Items.NAME_TAG || !held.hasDisplayName())) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof NixieTubeTileEntity tube) {
            if (dye != null) {
                tube.setRowColor(dye);
                if (!player.capabilities.isCreativeMode) held.shrink(1);
                return true;
            }
            String text = held.isEmpty() ? null : held.getDisplayName();
            if (clipboard) {
                net.minecraft.nbt.NBTTagCompound data = com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardData.fromItem(held);
                String[] lines = com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardData.lines(data, data.getInteger("LastPage"));
                text = lines[0];
            }
            tube.setManualText(text);
        }
        return true;
    }
}
