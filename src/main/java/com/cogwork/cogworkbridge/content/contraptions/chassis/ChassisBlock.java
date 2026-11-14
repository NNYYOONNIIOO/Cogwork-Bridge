package com.cogwork.cogworkbridge.content.contraptions.chassis;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.content.decoration.ConnectedTextureData;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.oredict.OreDictionary;

/** Structural chassis; only its axis is encoded in metadata. */
public final class ChassisBlock extends BlockContainer {
    public static final PropertyEnum<EnumFacing.Axis> AXIS = KineticBlock.AXIS;
    public static final IUnlistedProperty<Integer> STICKY = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "sticky_faces"; }
        @Override public boolean isValid(Integer value) { return value != null && value >= 0 && value < 64; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };
    private final boolean radial;

    public ChassisBlock(boolean radial) {
        super(Material.WOOD);
        this.radial = radial;
        setHardness(2F);
        setResistance(6F);
        setDefaultState(blockState.getBaseState().withProperty(AXIS, EnumFacing.Axis.Y));
    }

    public boolean isRadial() { return radial; }
    public boolean canGlue(IBlockState state, EnumFacing side) {
        return (side.getAxis() != state.getValue(AXIS)) == radial;
    }
    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{AXIS}, new IUnlistedProperty<?>[]{STICKY, ConnectedTextureData.CONTEXT});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return ((IExtendedBlockState) state).withProperty(STICKY,
                tile instanceof ChassisTileEntity chassis ? chassis.stickyMask() : 0)
                .withProperty(ConnectedTextureData.CONTEXT, radial ? 0L : ConnectedTextureData.capture(world, pos, state));
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new ChassisTileEntity(); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(AXIS).ordinal(); }
    @Override public IBlockState getStateFromMeta(int meta) {
        EnumFacing.Axis[] axes = EnumFacing.Axis.values();
        return getDefaultState().withProperty(AXIS, axes[Math.floorMod(meta, axes.length)]);
    }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
                                                       float x, float y, float z, int meta, EntityLivingBase placer) {
        IBlockState neighbour = world.getBlockState(pos.offset(side.getOpposite()));
        EnumFacing.Axis axis = !placer.isSneaking() && neighbour.getBlock() instanceof ChassisBlock
                ? neighbour.getValue(AXIS) : KineticBlock.getPlacementAxis(placer);
        return getDefaultState().withProperty(AXIS, axis);
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            EnumFacing.Axis axis = state.getValue(AXIS);
            if (axis == EnumFacing.Axis.X) return state.withProperty(AXIS, EnumFacing.Axis.Z);
            if (axis == EnumFacing.Axis.Z) return state.withProperty(AXIS, EnumFacing.Axis.X);
        }
        return state;
    }
    public boolean acceptsGlue(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        if (!canGlue(state, side)) return false;
        IBlockState neighbour = world.getBlockState(pos.offset(side));
        return radial || neighbour.getBlock() != this || neighbour.getValue(AXIS) != state.getValue(AXIS);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                               EntityPlayer player, EnumHand hand, EnumFacing side,
                                               float x, float y, float z) {
        ItemStack held = player.getHeldItem(hand);
        boolean glue = held.getItem() == Items.SLIME_BALL || held.getItem() == AllItems.SUPER_GLUE;
        if (!glue && !held.isEmpty()) {
            int id = OreDictionary.getOreID("slimeball");
            for (int candidate : OreDictionary.getOreIDs(held)) if (candidate == id) glue = true;
        }
        boolean removing = held.isEmpty() && player.isSneaking();
        if ((!glue && !removing) || !acceptsGlue(world, pos, state, side)
                || !(world.getTileEntity(pos) instanceof ChassisTileEntity chassis)) return false;
        if (!world.isRemote) {
            if (glue && chassis.isSticky(side)) {
                for (EnumFacing other : EnumFacing.VALUES)
                    if (acceptsGlue(world, pos, state, other)) chassis.setSticky(other, true);
            } else chassis.setSticky(side, glue);
        }
        return true;
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        if (world.isRemote || radial || !(world.getTileEntity(pos) instanceof ChassisTileEntity chassis)) return;
        for (EnumFacing side : EnumFacing.VALUES) {
            if (!pos.offset(side).equals(fromPos) || side.getAxis() != state.getValue(AXIS)) continue;
            IBlockState neighbour = world.getBlockState(fromPos);
            if (neighbour.getBlock() == this && neighbour.getValue(AXIS) == state.getValue(AXIS))
                chassis.setSticky(side, false);
        }
    }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
}
