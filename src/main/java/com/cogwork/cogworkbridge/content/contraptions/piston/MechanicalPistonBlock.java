package com.cogwork.cogworkbridge.content.contraptions.piston;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

/** Piston facing and perpendicular shaft axis are the only persisted block states. */
public final class MechanicalPistonBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final PropertyBool AXIS_ALONG_FIRST = PropertyBool.create("axis_along_first");
    public static final IUnlistedProperty<Integer> VISUAL_STATE = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "piston_visual_state"; }
        @Override public boolean isValid(Integer value) { return value != null && value >= 0 && value <= 2; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };
    private final boolean sticky;

    public MechanicalPistonBlock(boolean sticky) {
        super(Material.WOOD);
        this.sticky = sticky;
        setHardness(1F);
        setResistance(8F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(AXIS_ALONG_FIRST, false));
    }

    public boolean isSticky() { return sticky; }
    @Override public boolean hasAxis() { return false; }
    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{FACING, AXIS_ALONG_FIRST}, new IUnlistedProperty<?>[]{VISUAL_STATE});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess access, BlockPos pos) {
        if (state instanceof IExtendedBlockState extended && access.getTileEntity(pos) instanceof MechanicalPistonTileEntity piston)
            return extended.withProperty(VISUAL_STATE, piston.visualState());
        return state;
    }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(Math.floorMod(meta, 6)))
                .withProperty(AXIS_ALONG_FIRST, meta >= 6);
    }
    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex() + (state.getValue(AXIS_ALONG_FIRST) ? 6 : 0);
    }
    @Override public EnumFacing.Axis getAxis(IBlockState state) {
        EnumFacing.Axis facing = state.getValue(FACING).getAxis();
        int index = 0;
        for (EnumFacing.Axis axis : EnumFacing.Axis.values()) {
            if (axis == facing) continue;
            if ((index++ == 0) == state.getValue(AXIS_ALONG_FIRST)) return axis;
        }
        return EnumFacing.Axis.X;
    }
    @Override public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) { return side.getAxis() == getAxis(state); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        EnumFacing facing = getPlacementFacing(placer, placer.isSneaking());
        EnumFacing shaft = getPreferredShaftFacing(world, pos);
        boolean first = false;
        if (shaft != null && shaft.getAxis() != facing.getAxis()) {
            for (EnumFacing.Axis axis : EnumFacing.Axis.values()) {
                if (axis != facing.getAxis()) { first = axis == shaft.getAxis(); break; }
            }
        }
        return getDefaultState().withProperty(FACING, facing).withProperty(AXIS_ALONG_FIRST, first);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new MechanicalPistonTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.isSneaking()) return false;
        net.minecraft.item.ItemStack held = player.getHeldItem(hand);
        if (!sticky && held.getItem() == net.minecraft.init.Items.SLIME_BALL && side == state.getValue(FACING)
                && world.getTileEntity(pos) instanceof MechanicalPistonTileEntity piston && piston.visualState() == 0) {
            if (!world.isRemote) {
                net.minecraft.nbt.NBTTagCompound data = piston.writeToNBT(new net.minecraft.nbt.NBTTagCompound());
                IBlockState replacement = com.cogwork.cogworkbridge.registry.AllBlocks.STICKY_MECHANICAL_PISTON.getDefaultState()
                        .withProperty(FACING, state.getValue(FACING)).withProperty(AXIS_ALONG_FIRST, state.getValue(AXIS_ALONG_FIRST));
                if (world.setBlockState(pos, replacement, 3)) {
                    TileEntity next = world.getTileEntity(pos);
                    if (next instanceof MechanicalPistonTileEntity) { next.readFromNBT(data); next.markDirty(); }
                    if (!player.capabilities.isCreativeMode) held.shrink(1);
                }
            }
            return true;
        }
        if (!held.isEmpty()) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof MechanicalPistonTileEntity piston) piston.requestAssembly();
        return true;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof MechanicalPistonTileEntity piston) piston.disassemble(true);
        super.breakBlock(world, pos, state);
    }
    @Override public void onBlockHarvested(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof MechanicalPistonTileEntity piston
                && piston.disassemble(true)) PistonPoleBlock.dismantleFromBase(world, pos, state, player);
        super.onBlockHarvested(world, pos, state, player);
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, net.minecraft.block.Block block, BlockPos fromPos) {
        super.neighborChanged(state, world, pos, block, fromPos);
        if (!world.isRemote && world.getTileEntity(pos) instanceof MechanicalPistonTileEntity piston) piston.requestAssembly();
    }
}
