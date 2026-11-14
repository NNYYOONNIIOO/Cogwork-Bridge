package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraft.block.properties.IProperty;

public final class RedstoneLinkBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final IUnlistedProperty<Integer> MODEL = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "link_model"; }
        @Override public boolean isValid(Integer value) { return value != null && value >= 0 && value < 4; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };
    public RedstoneLinkBlock() {
        super(Material.IRON); setHardness(1.5F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
    }
    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{FACING}, new IUnlistedProperty<?>[]{MODEL});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        int model = tile instanceof RedstoneLinkTileEntity link
                ? (link.isReceiver() ? 2 : 0) + (link.isPowered() ? 1 : 0) : 0;
        return ((IExtendedBlockState) state).withProperty(MODEL, model);
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta)); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) { return getDefaultState().withProperty(FACING, side); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new RedstoneLinkTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public net.minecraft.util.BlockRenderLayer getRenderLayer() { return net.minecraft.util.BlockRenderLayer.CUTOUT; }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return switch (state.getValue(FACING)) {
            case UP -> new AxisAlignedBB(0, 0, 0, 1, 11D / 16D, 1);
            case DOWN -> new AxisAlignedBB(0, 5D / 16D, 0, 1, 1, 1);
            case NORTH -> new AxisAlignedBB(0, 0, 5D / 16D, 1, 1, 1);
            case SOUTH -> new AxisAlignedBB(0, 0, 0, 1, 1, 11D / 16D);
            case WEST -> new AxisAlignedBB(5D / 16D, 0, 0, 1, 1, 1);
            case EAST -> new AxisAlignedBB(0, 0, 0, 11D / 16D, 1, 1);
        };
    }
    @Override public boolean canProvidePower(IBlockState state) { return true; }
    @Override public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return world.getTileEntity(pos) instanceof RedstoneLinkTileEntity link ? link.getReceivedPower() : 0;
    }
    @Override public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return side == state.getValue(FACING) ? getWeakPower(state, world, pos, side) : 0; }
    @Override public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return true; }
    /** Centre of each 4-by-4 frequency pad on the model's exposed face. */
    public static Vec3d frequencySlotCenter(EnumFacing facing, int slot) {
        double face = 3.5D / 16D;
        if (facing == EnumFacing.UP) return new Vec3d(.5D, face, (slot == 0 ? 10.5D : 5.5D) / 16D);
        if (facing == EnumFacing.DOWN) return new Vec3d(.5D, 1D - face, (slot == 0 ? 5.5D : 10.5D) / 16D);
        double inset = .5D - face;
        return new Vec3d(.5D - facing.getXOffset() * inset, (slot == 0 ? 10.5D : 5.5D) / 16D,
                .5D - facing.getZOffset() * inset);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == AllItems.WRENCH || held.getItem() == AllItems.LINKED_CONTROLLER) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof RedstoneLinkTileEntity link) {
            if (player.isSneaking() && held.isEmpty()) link.toggleReceiver();
            else {
                EnumFacing facing = state.getValue(FACING);
                int index = facing == EnumFacing.UP ? (z >= .5F ? 0 : 1)
                        : facing == EnumFacing.DOWN ? (z < .5F ? 0 : 1) : (y >= .5F ? 0 : 1);
                link.setFrequency(index, held);
            }
        }
        return true;
    }
}
