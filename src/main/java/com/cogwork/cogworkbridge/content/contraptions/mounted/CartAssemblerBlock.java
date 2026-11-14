package com.cogwork.cogworkbridge.content.contraptions.mounted;

import net.minecraft.block.BlockRailBase;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

public final class CartAssemblerBlock extends BlockRailBase {
    private static final AxisAlignedBB PLAYER_X = new AxisAlignedBB(0D, 0D, .0625D, 1D, 1D, .9375D);
    private static final AxisAlignedBB PLAYER_Z = new AxisAlignedBB(.0625D, 0D, 0D, .9375D, 1D, 1D);
    // The side housings extend perpendicular to the rail, as in the model.
    private static final double SIDE_EXTENSION = 2.05D / 16D;
    private static final AxisAlignedBB OUTLINE_X = new AxisAlignedBB(0D, 0D, -SIDE_EXTENSION, 1D, 1D, 1D + SIDE_EXTENSION);
    private static final AxisAlignedBB OUTLINE_Z = new AxisAlignedBB(-SIDE_EXTENSION, 0D, 0D, 1D + SIDE_EXTENSION, 1D, 1D);
    public static final PropertyEnum<EnumRailDirection> SHAPE = PropertyEnum.create("shape", EnumRailDirection.class,
            EnumRailDirection.EAST_WEST, EnumRailDirection.NORTH_SOUTH);
    public static final IUnlistedProperty<Integer> VISUAL = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "cart_assembler_visual"; }
        @Override public boolean isValid(Integer value) { return value != null && value >= 0 && value < 10; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };
    public CartAssemblerBlock() { super(true); setHardness(.7F); setDefaultState(blockState.getBaseState().withProperty(SHAPE, EnumRailDirection.NORTH_SOUTH)); }
    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{SHAPE}, new IUnlistedProperty<?>[]{VISUAL});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (state instanceof IExtendedBlockState extended && world.getTileEntity(pos) instanceof CartAssemblerTileEntity tile)
            return extended.withProperty(VISUAL, tile.getRailType() + (tile.isPowered() ? 5 : 0));
        return state;
    }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(SHAPE, (meta & 1) != 0 ? EnumRailDirection.EAST_WEST : EnumRailDirection.NORTH_SOUTH); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(SHAPE) == EnumRailDirection.EAST_WEST ? 1 : 0; }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        EnumRailDirection shape = placer.getHorizontalFacing().getAxis() == EnumFacing.Axis.X ? EnumRailDirection.EAST_WEST : EnumRailDirection.NORTH_SOUTH;
        return getDefaultState().withProperty(SHAPE, shape);
    }
    @Override public PropertyEnum<EnumRailDirection> getShapeProperty() { return SHAPE; }
    @Override public boolean canMakeSlopes(IBlockAccess world, BlockPos pos) { return false; }
    @Override public void onMinecartPass(World world, EntityMinecart cart, BlockPos pos) {
        if (world.isRemote || !(world.getTileEntity(pos) instanceof CartAssemblerTileEntity assembler)) return;
        if (assembler.getRailType() == 4)
            ControllerRailBlock.applyToMinecart(world, cart, pos, assembler.getRailState(world.getBlockState(pos)),
                    ControllerRailBlock.power(world, pos));
        assembler.accept(cart);
    }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new CartAssemblerTileEntity(); }
    @Override public void getDrops(net.minecraft.util.NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos,
                                   IBlockState state, int fortune) {
        super.getDrops(drops, world, pos, state, fortune);
        if (world.getTileEntity(pos) instanceof CartAssemblerTileEntity assembler) drops.add(assembler.getRailDrop());
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(SHAPE) == EnumRailDirection.EAST_WEST ? OUTLINE_X : OUTLINE_Z;
    }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return FULL_BLOCK_AABB;
    }
    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                                java.util.List<AxisAlignedBB> boxes, @javax.annotation.Nullable Entity entity,
                                                boolean actualState) {
        if (entity instanceof EntityMinecart) return;
        AxisAlignedBB collision = entity instanceof EntityPlayer
                ? (state.getValue(SHAPE) == EnumRailDirection.EAST_WEST ? PLAYER_X : PLAYER_Z) : FULL_BLOCK_AABB;
        addCollisionBoxToList(pos, entityBox, boxes, collision);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                              EnumHand hand, EnumFacing side, float x, float y, float z) {
        ItemStack held = player.getHeldItem(hand);
        if (!(world.getTileEntity(pos) instanceof CartAssemblerTileEntity assembler)) return false;
        return assembler.changeRail(player, held);
    }
}
