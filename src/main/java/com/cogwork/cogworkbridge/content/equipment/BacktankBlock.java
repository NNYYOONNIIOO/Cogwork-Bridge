package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class BacktankBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    private final boolean netherite;
    public BacktankBlock(boolean netherite) {
        super(Material.IRON);
        this.netherite = netherite;
        setHardness(2F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }
    @Override public boolean hasAxis() { return true; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta)); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y,
                                                       float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }
    @Override public EnumFacing.Axis getAxis(IBlockState state) { return EnumFacing.Axis.Y; }
    @Override public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) { return side == EnumFacing.UP; }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new BacktankTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public net.minecraft.util.math.AxisAlignedBB getBoundingBox(net.minecraft.block.state.IBlockState state,
            net.minecraft.world.IBlockAccess world, net.minecraft.util.math.BlockPos pos) { return collisionShape(state).bounds(); }
    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof BacktankTileEntity tank ? tank.comparator() : 0;
    }
    private ItemStack asStack(IBlockAccess world, BlockPos pos) {
        ItemStack stack = new ItemStack(netherite ? AllItems.NETHERITE_BACKTANK : AllItems.COPPER_BACKTANK);
        if (world.getTileEntity(pos) instanceof BacktankTileEntity tank) tank.applyToItem(stack);
        return stack;
    }
    @Override public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        drops.add(asStack(world, pos));
    }
    @Override public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest) {
        return willHarvest || super.removedByPlayer(state, world, pos, player, false);
    }
    @Override public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state,
                                       TileEntity tile, ItemStack tool) {
        super.harvestBlock(world, player, pos, state, tile, tool);
        world.setBlockToAir(pos);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                               EnumHand hand, EnumFacing face, float x, float y, float z) {
        if (player.isSneaking() || player.getHeldItem(hand).getItem() instanceof net.minecraft.item.ItemBlock
                || !player.getItemStackFromSlot(EntityEquipmentSlot.CHEST).isEmpty()) return false;
        if (!world.isRemote) {
            player.setItemStackToSlot(EntityEquipmentSlot.CHEST, asStack(world, pos));
            world.setBlockToAir(pos);
        }
        return true;
    }
    private static final com.cogwork.cogworkbridge.util.BlockShape COLLISION = com.cogwork.cogworkbridge.util.BlockShape.pixels(3,0,3,13,12,13, 5,0,5,11,16,11);

    private static com.cogwork.cogworkbridge.util.BlockShape collisionShape(net.minecraft.block.state.IBlockState state) { return COLLISION; }
    @Override public void addCollisionBoxToList(net.minecraft.block.state.IBlockState state, net.minecraft.world.World world,
            net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.AxisAlignedBB query, java.util.List<net.minecraft.util.math.AxisAlignedBB> boxes,
            @javax.annotation.Nullable net.minecraft.entity.Entity entity, boolean actual) { collisionShape(state).addCollisionBoxes(pos, query, boxes); }
    @Override @javax.annotation.Nullable public net.minecraft.util.math.RayTraceResult collisionRayTrace(net.minecraft.block.state.IBlockState state,
            net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.Vec3d start,
            net.minecraft.util.math.Vec3d end) { return collisionShape(state).rayTrace(pos, start, end); }
}
