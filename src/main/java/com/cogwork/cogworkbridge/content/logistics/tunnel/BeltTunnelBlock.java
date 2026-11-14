package com.cogwork.cogworkbridge.content.logistics.tunnel;

import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import com.cogwork.cogworkbridge.content.decoration.ConnectedTextureData;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.properties.IProperty;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import javax.annotation.Nullable;
import java.util.List;

public final class BeltTunnelBlock extends BlockContainer {
    public static final PropertyEnum<EnumFacing.Axis> AXIS = PropertyEnum.create("axis", EnumFacing.Axis.class, EnumFacing.Axis.X, EnumFacing.Axis.Z);
    public static final IUnlistedProperty<String> MODEL = new IUnlistedProperty<String>() {
        @Override public String getName() { return "tunnel_model"; }
        @Override public boolean isValid(String value) { return value != null; }
        @Override public Class<String> getType() { return String.class; }
        @Override public String valueToString(String value) { return value; }
    };

    private final boolean brass;
    public BeltTunnelBlock(boolean brass) {
        super(Material.IRON); this.brass = brass; setHardness(2); setResistance(6); setLightOpacity(0);
        setDefaultState(getDefaultState().withProperty(AXIS, EnumFacing.Axis.X));
    }
    public boolean isBrass() { return brass; }
    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{AXIS}, new IUnlistedProperty<?>[]{MODEL, ConnectedTextureData.CONTEXT});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState extended)) return state;
        EnumFacing forward = EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, state.getValue(AXIS));
        EnumFacing left = forward.rotateY();
        boolean l = hasExit(world, pos.down(), left), r = hasExit(world, pos.down(), left.getOpposite());
        String shape = l && r ? "cross" : l ? "t_left" : r ? "t_right" : "straight";
        if (shape.equals("straight") && world.getBlockState(pos.offset(forward)).getBlock() instanceof BeltTunnelBlock
                && world.getBlockState(pos.offset(forward.getOpposite())).getBlock() instanceof BeltTunnelBlock) {
            shape = world.getTileEntity(pos) instanceof BeltTunnelTileEntity tunnel && tunnel.windowClosed()
                    ? "straight" : "window";
        }
        return extended.withProperty(MODEL, shape)
                .withProperty(ConnectedTextureData.CONTEXT, brass ? ConnectedTextureData.capture(world, pos, state) : 0L);
    }
    public static boolean hasExit(IBlockAccess world, BlockPos beltPos, EnumFacing side) {
        TileEntity tile = world.getTileEntity(beltPos.offset(side));
        if (tile instanceof BeltTileEntity belt) return (belt.step().getX() != 0 ? EnumFacing.Axis.X : EnumFacing.Axis.Z) == side.getAxis();
        return tile != null && tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side.getOpposite());
    }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(AXIS, (meta & 1) == 0 ? EnumFacing.Axis.X : EnumFacing.Axis.Z); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(AXIS) == EnumFacing.Axis.X ? 0 : 1; }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z,
            int meta, EntityLivingBase placer) {
        if (world.getTileEntity(pos.down()) instanceof BeltTileEntity belt)
            return getDefaultState().withProperty(AXIS, belt.step().getX() == 0 ? EnumFacing.Axis.Z : EnumFacing.Axis.X);
        return getDefaultState();
    }
    @Override public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && world.getTileEntity(pos.down()) instanceof BeltTileEntity belt && belt.step().getY() == 0;
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(AXIS, rotation.rotate(EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, state.getValue(AXIS))).getAxis());
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return brass ? new BrassTunnelTileEntity() : new BeltTunnelTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return new AxisAlignedBB(0, 0, 0, 1, 1, 1);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return getBoundingBox(state, world, pos);
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        EnumFacing.Axis axis = state.getValue(AXIS);
        // The tunnel is a frame around the belt: two side posts and a top rail.
        addBox(pos, query, boxes, axis == EnumFacing.Axis.X
                ? new AxisAlignedBB(0, 0, 0, 1, 10 / 16D, 2 / 16D)
                : new AxisAlignedBB(0, 0, 0, 2 / 16D, 10 / 16D, 1));
        addBox(pos, query, boxes, axis == EnumFacing.Axis.X
                ? new AxisAlignedBB(0, 0, 14 / 16D, 1, 10 / 16D, 1)
                : new AxisAlignedBB(14 / 16D, 0, 0, 1, 10 / 16D, 1));
        addBox(pos, query, boxes, new AxisAlignedBB(0, 10 / 16D, 0, 1, 1, 1));
    }

    private static void addBox(BlockPos pos, AxisAlignedBB query, List<AxisAlignedBB> boxes, AxisAlignedBB local) {
        AxisAlignedBB box = local.offset(pos);
        if (query.intersects(box)) boxes.add(box);
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        if (!world.isRemote && fromPos.equals(pos.down()) && !(world.getTileEntity(pos.down()) instanceof BeltTileEntity)) world.destroyBlock(pos, true);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (!brass || !(world.getTileEntity(pos) instanceof BrassTunnelTileEntity tunnel) || player.getHeldItem(hand).getItem() == AllItems.WRENCH) return false;
        if (side.getAxis().isHorizontal()) {
            if (!world.isRemote && (!player.getHeldItem(hand).isEmpty() || player.isSneaking()))
                tunnel.selectFilter(player, hand, side);
            return true;
        }
        return false;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof BrassTunnelTileEntity tunnel)
            for (ItemStack filter : tunnel.filters()) if (ListFilterItem.isFilterItem(filter)) ItemTransfer.drop(world, pos, filter);
        super.breakBlock(world, pos, state);
    }
}
