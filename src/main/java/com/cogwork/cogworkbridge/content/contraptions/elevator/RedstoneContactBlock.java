package com.cogwork.cogworkbridge.content.contraptions.elevator;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
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

import javax.annotation.Nullable;

/** A normal contact, or the configurable stationary floor contact of an elevator. */
public final class RedstoneContactBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final IUnlistedProperty<Boolean> POWERING = new IUnlistedProperty<Boolean>() {
        @Override public String getName() { return "powering"; }
        @Override public boolean isValid(Boolean value) { return value != null; }
        @Override public Class<Boolean> getType() { return Boolean.class; }
        @Override public String valueToString(Boolean value) { return value.toString(); }
    };
    public static final IUnlistedProperty<Boolean> CALLING = new IUnlistedProperty<Boolean>() {
        @Override public String getName() { return "calling"; }
        @Override public boolean isValid(Boolean value) { return value != null; }
        @Override public Class<Boolean> getType() { return Boolean.class; }
        @Override public String valueToString(Boolean value) { return value.toString(); }
    };
    private final boolean elevator;
    public RedstoneContactBlock(boolean elevator) {
        super(Material.IRON); this.elevator = elevator; setHardness(1.5F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
    }
    public boolean isElevator() { return elevator; }
    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{FACING}, new IUnlistedProperty<?>[]{POWERING, CALLING});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return ((IExtendedBlockState) state)
                .withProperty(POWERING, tile instanceof RedstoneContactTileEntity contact && contact.isPowering())
                .withProperty(CALLING, tile instanceof RedstoneContactTileEntity contact && contact.isCalling());
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta)); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        EnumFacing facing = placer.isSneaking() ? side.getOpposite() : KineticBlock.getPlacementFacing(placer).getOpposite();
        IBlockState neighbor = world.getBlockState(pos.offset(side.getOpposite()));
        if (neighbor.getBlock() instanceof RedstoneContactBlock && neighbor.getValue(FACING) == side)
            facing = side.getOpposite();
        return getDefaultState().withProperty(FACING, facing);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new RedstoneContactTileEntity(); }
    @Override public Item getItemDropped(IBlockState state, java.util.Random random, int fortune) {
        return elevator ? Item.getItemFromBlock(AllBlocks.REDSTONE_CONTACT) : super.getItemDropped(state, random, fortune);
    }
    @Override public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
        return elevator ? new ItemStack(AllBlocks.REDSTONE_CONTACT) : super.getItem(world, pos, state);
    }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return elevator && tile instanceof RedstoneContactTileEntity contact && contact.isPowering() ? 10 : 0;
    }
    @Override public boolean canProvidePower(IBlockState state) { return true; }
    @Override public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, @Nullable EnumFacing side) {
        return side == null || side.getOpposite() != state.getValue(FACING);
    }
    @Override public boolean shouldCheckWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        // The contact is a signal source, not a solid conductor whose input
        // should be sampled again as its own output.
        return false;
    }
    @Override public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity tile = world.getTileEntity(pos);
        if (side == null || !(tile instanceof RedstoneContactTileEntity contact) || !contact.isPowering()) return 0;
        if (elevator) {
            // Neighbouring floor contacts must not call each other when one
            // of them emits its arrival signal, as in the upstream block.
            return world.getBlockState(pos.offset(side.getOpposite())).getBlock() == this ? 0 : 15;
        }
        return side != state.getValue(FACING).getOpposite() ? 15 : 0;
    }
    @Override public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return 0; }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof RedstoneContactTileEntity contact) contact.refresh();
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                               EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (!elevator || player.getHeldItem(hand).getItem() == AllItems.WRENCH) return false;
        if (world.isRemote) CogworkBridge.proxy.openElevatorContactScreen(pos);
        return true;
    }
}
