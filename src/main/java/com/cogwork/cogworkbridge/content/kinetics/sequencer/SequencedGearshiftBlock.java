package com.cogwork.cogworkbridge.content.kinetics.sequencer;

import com.cogwork.cogworkbridge.client.SequencedGearshiftScreen;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public final class SequencedGearshiftBlock extends KineticBlock {
    public static final PropertyEnum<EnumFacing.Axis> HORIZONTAL_AXIS =
            PropertyEnum.create("horizontal_axis", EnumFacing.Axis.class, EnumFacing.Axis.X, EnumFacing.Axis.Z);
    public static final PropertyBool VERTICAL = PropertyBool.create("vertical");
    public static final IUnlistedProperty<Integer> STAGE = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "stage"; }
        @Override public boolean isValid(Integer value) { return value != null && value >= 0 && value <= 5; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };

    public SequencedGearshiftBlock() {
        super(Material.WOOD);
        setHardness(2.0F);
        setResistance(6.0F);
        setDefaultState(getDefaultState().withProperty(HORIZONTAL_AXIS, EnumFacing.Axis.Z)
                .withProperty(VERTICAL, true));
    }

    @Override public boolean hasAxis() { return true; }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{HORIZONTAL_AXIS, VERTICAL},
                new IUnlistedProperty<?>[]{STAGE});
    }

    @Override
    public EnumFacing.Axis getAxis(IBlockState state) {
        return state.getValue(VERTICAL) ? EnumFacing.Axis.Y : state.getValue(HORIZONTAL_AXIS);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return switch (meta & 3) {
            case 0 -> getDefaultState().withProperty(HORIZONTAL_AXIS, EnumFacing.Axis.X).withProperty(VERTICAL, false);
            case 1 -> getDefaultState();
            case 2 -> getDefaultState().withProperty(HORIZONTAL_AXIS, EnumFacing.Axis.Z).withProperty(VERTICAL, false);
            default -> getDefaultState().withProperty(HORIZONTAL_AXIS, EnumFacing.Axis.X);
        };
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        boolean horizontalX = state.getValue(HORIZONTAL_AXIS) == EnumFacing.Axis.X;
        return state.getValue(VERTICAL) ? (horizontalX ? 3 : 1) : (horizontalX ? 0 : 2);
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
                ? state.cycleProperty(HORIZONTAL_AXIS) : state;
    }

    public static IBlockState wrenchedState(IBlockState state, EnumFacing.Axis clickedAxis) {
        if (clickedAxis == EnumFacing.Axis.Y) return state.cycleProperty(HORIZONTAL_AXIS);
        return clickedAxis != state.getValue(HORIZONTAL_AXIS) ? state.cycleProperty(VERTICAL) : state;
    }

    public static IBlockState transformState(IBlockState state, EnumFacing.Axis rotationAxis, int turns) {
        if ((turns & 1) == 0) return state;
        if (rotationAxis == EnumFacing.Axis.Y) return state.cycleProperty(HORIZONTAL_AXIS);
        if (rotationAxis != state.getValue(HORIZONTAL_AXIS)) return state.cycleProperty(VERTICAL);
        return state.getValue(VERTICAL)
                ? state.cycleProperty(VERTICAL).cycleProperty(HORIZONTAL_AXIS) : state;
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        int stage = tile instanceof SequencedGearshiftTileEntity sequencer ? sequencer.getActiveStep() + 1 : 0;
        return ((IExtendedBlockState) state).withProperty(STAGE, stage);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        EnumFacing.Axis preferred = null;
        if (!placer.isSneaking()) {
            for (EnumFacing side : EnumFacing.VALUES) {
                BlockPos adjacentPos = pos.offset(side);
                IBlockState adjacent = world.getBlockState(adjacentPos);
                if (!(adjacent.getBlock() instanceof KineticBlock block)
                        || !block.canConnectShaftTowards(world, adjacentPos, adjacent, side.getOpposite())) {
                    continue;
                }
                if (preferred != null && preferred != side.getAxis()) {
                    preferred = null;
                    break;
                }
                preferred = side.getAxis();
            }
        }
        EnumFacing.Axis axis = preferred != null ? preferred : getPlacementAxis(placer);
        return getDefaultState()
                .withProperty(HORIZONTAL_AXIS, axis == EnumFacing.Axis.Y ? placer.getHorizontalFacing().getAxis() : axis)
                .withProperty(VERTICAL, axis == EnumFacing.Axis.Y);
    }

    @Override public TileEntity createNewTileEntity(World world, int meta) {
        return new SequencedGearshiftTileEntity();
    }

    @Override
    public double shaftPortModifier(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof SequencedGearshiftTileEntity sequencer ? sequencer.portModifier(side) : 0.0D;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing face, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == AllItems.WRENCH || held.getItem() instanceof ItemBlock item
                && item.getBlock() instanceof KineticBlock && face.getAxis() == getAxis(state)) {
            return false;
        }
        if (world.isRemote && world.getTileEntity(pos) instanceof SequencedGearshiftTileEntity tile) {
            openScreen(tile);
        }
        return true;
    }

    @SideOnly(Side.CLIENT)
    private static void openScreen(SequencedGearshiftTileEntity tile) {
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new SequencedGearshiftScreen(tile));
    }

    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof SequencedGearshiftTileEntity sequencer ? sequencer.getActiveStep() + 1 : 0;
    }

    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
}
