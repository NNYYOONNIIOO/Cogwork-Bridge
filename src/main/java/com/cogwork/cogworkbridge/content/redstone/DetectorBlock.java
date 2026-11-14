package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import javax.annotation.Nullable;

/** Shared three-axis targeting for the observer and threshold switch. */
public final class DetectorBlock extends BlockContainer {
    public enum Target implements IStringSerializable { FLOOR, WALL, CEILING; @Override public String getName() { return name().toLowerCase(java.util.Locale.ROOT); } }
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyEnum<Target> TARGET = PropertyEnum.create("target", Target.class);
    public static final IUnlistedProperty<Integer> LEVEL = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "detector_level"; }
        @Override public boolean isValid(Integer value) { return value != null && value >= 0 && value <= 5; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };
    private final boolean threshold;
    public DetectorBlock(boolean threshold) { super(Material.ROCK); this.threshold = threshold; setHardness(2); setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(TARGET, Target.WALL)); }
    public boolean isThreshold() { return threshold; }
    @Override protected BlockStateContainer createBlockState() { return new ExtendedBlockState(this, new IProperty<?>[]{FACING, TARGET}, new IUnlistedProperty<?>[]{LEVEL}); }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos); int level = tile instanceof DetectorTileEntity detector ? threshold ? detector.displayLevel() : detector.isPowered() ? 1 : 0 : 0;
        return ((IExtendedBlockState) state).withProperty(LEVEL, level);
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex() | state.getValue(TARGET).ordinal() << 2; }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)).withProperty(TARGET, Target.values()[Math.min(2, (meta >> 2) & 3)]); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    public static EnumFacing target(IBlockState state) { return state.getValue(TARGET) == Target.CEILING ? EnumFacing.UP : state.getValue(TARGET) == Target.FLOOR ? EnumFacing.DOWN : state.getValue(FACING); }
    /** The same filter panels as Create's FilteredDetectorFilterSlot. */
    public static boolean isFilterSide(IBlockState state, EnumFacing side) {
        EnumFacing target = target(state);
        if (side == target) return false;
        if (side == target.getOpposite()) return true;
        boolean bottom = ((DetectorBlock) state.getBlock()).isThreshold();
        if (target.getAxis() != EnumFacing.Axis.Y) return side == EnumFacing.UP || bottom && side == EnumFacing.DOWN;
        if (target == EnumFacing.UP) side = side.getOpposite();
        EnumFacing facing = state.getValue(FACING);
        return bottom ? side.getAxis() == facing.rotateY().getAxis() : side == facing;
    }
    public static net.minecraft.util.math.Vec3d filterCenter(EnumFacing side) {
        return new net.minecraft.util.math.Vec3d(.5D + side.getXOffset() * .502D,
                .5D + side.getYOffset() * .502D, .5D + side.getZOffset() * .502D);
    }
    public static IBlockState withTarget(IBlockState state, EnumFacing direction) {
        if (direction == EnumFacing.UP) return state.withProperty(TARGET, Target.CEILING);
        if (direction == EnumFacing.DOWN) return state.withProperty(TARGET, Target.FLOOR);
        return state.withProperty(TARGET, Target.WALL).withProperty(FACING, direction);
    }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        EnumFacing target = null;
        for (EnumFacing direction : EnumFacing.values()) {
            TileEntity tile = world.getTileEntity(pos.offset(direction));
            if (tile != null && (threshold && tile instanceof ThresholdObservable || tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction.getOpposite())
                    || tile.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, direction.getOpposite()))) { target = direction; break; }
        }
        if (target == null) { target = KineticBlock.getPlacementFacing(placer); if (!placer.isSneaking()) target = target.getOpposite(); }
        return withTarget(getDefaultState().withProperty(FACING, placer.getHorizontalFacing()), target);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new DetectorTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean canProvidePower(IBlockState state) { return true; }
    @Override public boolean shouldCheckWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return false; }
    @Override public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, @Nullable EnumFacing side) { return side != null && side.getOpposite() != target(state); }
    @Override public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return side != target(state).getOpposite() && world.getTileEntity(pos) instanceof DetectorTileEntity detector && detector.isPowered() ? 15 : 0;
    }
    @Override public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return getWeakPower(state, world, pos, side); }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.isSneaking() || player.getHeldItem(hand).getItem() == AllItems.WRENCH || !(world.getTileEntity(pos) instanceof DetectorTileEntity tile)) return false;
        ItemStack held = player.getHeldItem(hand);
        if (!held.isEmpty() && side != target(state)) { if (!world.isRemote) tile.setFilter(player, held); return true; }
        if (threshold && world.isRemote) CogworkBridge.proxy.openThresholdSwitchScreen(pos);
        return true;
    }
}
