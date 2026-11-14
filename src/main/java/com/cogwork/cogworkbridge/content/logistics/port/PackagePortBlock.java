package com.cogwork.cogworkbridge.content.logistics.port;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.content.fluids.ListFilterGuiHandler;
import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
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
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

public final class PackagePortBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final IUnlistedProperty<Boolean> OPEN = new IUnlistedProperty<Boolean>() {
        @Override public String getName() { return "postbox_open"; }
        @Override public boolean isValid(Boolean value) { return value != null; }
        @Override public Class<Boolean> getType() { return Boolean.class; }
        @Override public String valueToString(Boolean value) { return value.toString(); }
    };
    private final boolean frog;
    public PackagePortBlock(boolean frog) {
        super(Material.IRON); this.frog = frog; setHardness(2F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }
    public boolean isFrog() { return frog; }
    @Override protected BlockStateContainer createBlockState() { return new ExtendedBlockState(this, new IProperty<?>[]{FACING}, new IUnlistedProperty<?>[]{OPEN}); }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return ((IExtendedBlockState) state).withProperty(OPEN, !frog && world.getTileEntity(pos) instanceof PackagePortTileEntity port && port.open());
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta)); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rot) { return state.withProperty(FACING, rot.rotate(state.getValue(FACING))); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face, float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if(!frog)MailboxNetwork.bindPlaced(world,pos,stack);
        if (world.isRemote || !frog || !(placer instanceof EntityPlayer player)
                || !(world.getTileEntity(pos) instanceof PackagePortTileEntity port)) return;
        String key = com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorBlock.PORT_SELECTION;
        if (!player.getEntityData().hasKey(key, 10)) return;
        net.minecraft.nbt.NBTTagCompound selection = player.getEntityData().getCompoundTag(key);
        player.getEntityData().removeTag(key);
        BlockPos wheel = BlockPos.fromLong(selection.getLong("Pos"));
        BlockPos end = selection.hasKey("End") ? BlockPos.fromLong(selection.getLong("End")) : null;
        if (!world.isBlockLoaded(wheel) || !(world.getTileEntity(wheel) instanceof com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorTileEntity conveyor)) return;
        float point = selection.getFloat("Angle");
        net.minecraft.util.math.Vec3d target;
        if (end != null && conveyor.connections().contains(end)) {
            var g = conveyor.geometry(end);
            double fraction = Math.max(0, Math.min(1, point / Math.max(.001F, g.length())));
            target = g.start().add(g.end().subtract(g.start()).scale(fraction));
        } else {
            double rad = Math.toRadians(point);
            target = new net.minecraft.util.math.Vec3d(wheel.getX() + .5D + Math.sin(rad) * .875D, wheel.getY() + 6D/16D, wheel.getZ() + .5D + Math.cos(rad) * .875D);
        }
        double x = target.x - (pos.getX() + .5D), y = target.y - pos.getY(), z = target.z - (pos.getZ() + .5D);
        if (selection.getInteger("Dimension") == world.provider.getDimension() && y >= 0 && x*x + y*y + z*z <= 25D)
            port.bindChain(wheel, end, point);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new PackagePortTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return frog ? new AxisAlignedBB(0, 0, 0, 1, .875, 1) : new AxisAlignedBB(.125, 0, .125, .875, 1, .875);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.isSneaking() || player.getHeldItem(hand).getItem() == AllItems.WRENCH) return false;
        if (frog && player.getHeldItem(hand).getItem() == AllItems.GOGGLES
                && world.getTileEntity(pos) instanceof PackagePortTileEntity port && !port.hasGoggles()) {
            if (!world.isRemote) port.equipGoggles();
            return true;
        }
        if (player.getHeldItem(hand).getItem() instanceof com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardItem) {
            if (!world.isRemote && world.getTileEntity(pos) instanceof PackagePortTileEntity port) {
                port.copyAddressToClipboard(player.getHeldItem(hand)); player.inventory.markDirty();
            }
            return true;
        }
        if (!world.isRemote) player.openGui(CogworkBridge.instance, ListFilterGuiHandler.PACKAGE_PORT_GUI, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof PackagePortTileEntity port) {
            port.onRemoved();
            for (int i = 0; i < port.inventory().getSlots(); i++) ItemTransfer.drop(world, pos, port.inventory().extractItem(i, 1, false));
        }
        super.breakBlock(world, pos, state);
    }
    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof PackagePortTileEntity port ? port.comparator() : 0;
    }
}
