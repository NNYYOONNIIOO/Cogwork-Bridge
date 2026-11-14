package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import javax.annotation.Nullable;
import java.util.List;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

public class FluidTankBlock extends BlockContainer {
    public static final IUnlistedProperty<String> MODEL = new IUnlistedProperty<String>() {
        @Override public String getName() { return "tank_model"; }
        @Override public boolean isValid(String value) { return value != null; }
        @Override public Class<String> getType() { return String.class; }
        @Override public String valueToString(String value) { return value; }
    };
    public static final IUnlistedProperty<Integer> CULLED_SIDES = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "tank_culled_sides"; }
        @Override public boolean isValid(Integer value) { return value != null; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return String.valueOf(value); }
    };
    public static final IUnlistedProperty<Integer> CONNECTED = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "tank_connected"; }
        @Override public boolean isValid(Integer value) { return value != null; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };

    public FluidTankBlock() {
        super(Material.IRON);
        setHardness(2.0F);
        setResistance(6.0F);
        setSoundType(SoundType.METAL);
        setLightOpacity(0);
    }

    @Override public TileEntity createNewTileEntity(World world, int meta) { return new FluidTankTileEntity(); }
    @Override public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state) {
        return net.minecraft.util.EnumBlockRenderType.MODEL;
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }

    @Override public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof FluidTankTileEntity tank && tank.hasWindows() ? tank.getLuminosity() : 0;
    }

    @Override protected net.minecraft.block.state.BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[0], new IUnlistedProperty<?>[]{MODEL, CULLED_SIDES, CONNECTED});
    }

    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState extended)) return state;
        TileEntity tile = world.getTileEntity(pos);
        int culled = 0;
        int connected = 0;
        if (tile instanceof FluidTankTileEntity tank) {
            Iterable<EnumFacing> faces = tank.getTileData().hasKey("TutorialState", 10)
                    ? java.util.Arrays.asList(EnumFacing.VALUES) : EnumFacing.Plane.HORIZONTAL;
            for (EnumFacing side : faces) {
                if (connected(world, tank, pos.offset(side))) {
                    culled |= 1 << side.getIndex();
                }
            }
            for (EnumFacing face : EnumFacing.VALUES) {
                EnumFacing up = face.getAxis().isHorizontal() ? EnumFacing.UP : EnumFacing.NORTH;
                EnumFacing right = face.getAxis() == EnumFacing.Axis.X ? EnumFacing.SOUTH : EnumFacing.WEST;
                if (face.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE) right = right.getOpposite();
                if (face == EnumFacing.DOWN) { up = up.getOpposite(); right = right.getOpposite(); }
                int bits = (connected(world, tank, pos.offset(up)) ? 1 : 0)
                        | (connected(world, tank, pos.offset(up.getOpposite())) ? 2 : 0)
                        | (connected(world, tank, pos.offset(right.getOpposite())) ? 4 : 0)
                        | (connected(world, tank, pos.offset(right)) ? 8 : 0);
                connected |= bits << (face.getIndex() * 4);
            }
        }
        return extended.withProperty(MODEL, tile instanceof FluidTankTileEntity tank
                ? tank.getModelName() : "single_window").withProperty(CULLED_SIDES, culled)
                .withProperty(CONNECTED, connected);
    }

    private static boolean connected(IBlockAccess world, FluidTankTileEntity tank, BlockPos pos) {
        TileEntity neighbor = world.getTileEntity(pos);
        return neighbor instanceof FluidTankTileEntity other && other.getClass() == tank.getClass()
                && other.getControllerPos().equals(tank.getControllerPos());
    }

    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof FluidTankTileEntity tank)) return 0;
        if (tank instanceof CreativeFluidTankTileEntity creative) {
            return creative.getFluid() == null ? 0 : 15;
        }
        if (tank.getFluid() == null || tank.getCapacity() <= 0) return 0;
        return Math.min(15, 1 + (int) Math.floor(
                14.0D * tank.getFluid().amount / tank.getCapacity()));
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return new AxisAlignedBB(1 / 16D, 0, 1 / 16D, 15 / 16D, 1, 15 / 16D);
    }

    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return getBoundingBox(state, world, pos);
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        AxisAlignedBB box = getBoundingBox(state, world, pos).offset(pos);
        if (query.intersects(box)) boxes.add(box);
    }

    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                          EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        FluidTankConnectivity.markAround(world, pos);
    }

    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                          net.minecraft.block.Block block, BlockPos neighborPos) {
        FluidTankConnectivity.markAround(world, pos);
    }

    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        FluidTankConnectivity.beforeRemoved(world, pos);
        super.breakBlock(world, pos, state);
        FluidTankConnectivity.markAround(world, pos);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof FluidTankTileEntity tank)) return false;
        ItemStack held = player.getHeldItem(hand);
        if (!player.capabilities.isCreativeMode && !(tank instanceof CreativeFluidTankTileEntity)) return false;
        if (tank instanceof CreativeFluidTankTileEntity creative) {
            if (held.isEmpty()) return false;
            FluidStack contained = FluidContainerHelper.contents(held);
            if (contained != null && contained.amount > 0) {
                if (world.isRemote) return true;
                ItemStack emptied = FluidContainerHelper.empty(held, creative.getFluidHandler(), contained);
                if (emptied == null) return false;
                creative.setSelectedFluid(contained);
                replaceHeldItem(player, hand, held, emptied);
                return true;
            }
            if (creative.hasSelectedFluid()
                    && FluidContainerHelper.canFill(held, creative.getFluidHandler(), creative.getCapacity())) {
                if (world.isRemote) return true;
                ItemStack filled = FluidContainerHelper.fill(held, creative.getFluidHandler(), creative.getCapacity());
                if (filled == null) return false;
                creative.setSelectedFluid(null);
                replaceHeldItem(player, hand, held, filled);
                return true;
            }
            return false;
        }
        if (held.isEmpty()) return false;
        if (PotionFluid.isPotionItem(held) || held.getItem() == net.minecraft.init.Items.GLASS_BOTTLE
                || held.getItem() == net.minecraft.init.Items.MILK_BUCKET
                || held.getItem() == net.minecraft.init.Items.BUCKET && MilkFluid.isMilk(tank.getFluid())) {
            FluidStack contained = FluidContainerHelper.contents(held);
            boolean canFill = FluidContainerHelper.canFill(held, tank.getFluidHandler(), tank.getCapacity());
            if (contained == null && !canFill) return false;
            if (world.isRemote) return true;
            ItemStack result = contained != null ? FluidContainerHelper.empty(held, tank.getFluidHandler(), contained)
                    : FluidContainerHelper.fill(held, tank.getFluidHandler(), tank.getCapacity());
            if (result == null) return false;
            replaceHeldItem(player, hand, held, result);
            return true;
        }
        if (FluidUtil.getFluidHandler(held) == null) return false;
        return world.isRemote || FluidUtil.interactWithFluidHandler(player, hand, tank.getFluidHandler());
    }

    private static void replaceHeldItem(EntityPlayer player, EnumHand hand, ItemStack held, ItemStack result) {
        if (player.capabilities.isCreativeMode) return;
        if (held.getCount() == 1) {
            player.setHeldItem(hand, result);
            return;
        }
        held.shrink(1);
        if (!result.isEmpty() && !player.inventory.addItemStackToInventory(result)) {
            player.dropItem(result, false);
        }
    }
}
