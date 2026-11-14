package com.cogwork.cogworkbridge.content.logistics.packager;

import com.cogwork.cogworkbridge.CogworkBridge;
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
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class StockTickerBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public StockTickerBlock() { super(Material.IRON); setHardness(2F); setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH)); }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta)); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.isSneaking() ? placer.getHorizontalFacing() : placer.getHorizontalFacing().getOpposite());
    }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof StockTickerTileEntity ticker) {
            if (stack.hasTagCompound() && stack.getTagCompound().hasUniqueId("LogisticsFrequency")) {
                java.util.UUID frequency = stack.getTagCompound().getUniqueId("LogisticsFrequency");
                if (!(placer instanceof EntityPlayer player) || LogisticsNetworkSecurity.mayInteract(world, frequency, player)) ticker.setNetwork(frequency);
            }
            if (placer instanceof EntityPlayer player) LogisticsNetworkSecurity.claim(world, ticker.getNetwork(), player);
        }
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new StockTickerTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public net.minecraft.util.BlockRenderLayer getRenderLayer() { return net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.getHeldItem(hand).getItem() == AllItems.WRENCH || player.getHeldItem(hand).getItem() instanceof StockLinkItem) return false;
        if (player.getHeldItem(hand).getItem() == AllItems.SHOPPING_LIST) {
            if (!world.isRemote && world.getTileEntity(pos) instanceof StockTickerTileEntity ticker)
                com.cogwork.cogworkbridge.content.logistics.table.ShoppingListItem.checkout(ticker, player, hand);
            return true;
        }
        if (!world.isRemote && world.getTileEntity(pos) instanceof StockTickerTileEntity ticker
                && !LogisticsNetworkSecurity.mayInteract(world, ticker.getNetwork(), player)) {
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("cogworkbridge.stock_ticker.locked"), true); return true;
        }
        if (!world.isRemote && world.getTileEntity(pos) instanceof StockTickerTileEntity ticker) {
            boolean hadPayments = false;
            for (int i = 0; LogisticsNetworkSecurity.mayAdminister(world, ticker.getNetwork(), player) && i < ticker.payments().getSlots(); i++) {
                ItemStack payment = ticker.payments().extractItem(i, 64, false);
                if (payment.isEmpty()) continue;
                hadPayments = true; net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(player, payment);
            }
            if (hadPayments) return true;
        }
        if (!world.isRemote) player.openGui(CogworkBridge.instance, com.cogwork.cogworkbridge.content.fluids.ListFilterGuiHandler.STOCK_TICKER_GUI,
                world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof StockTickerTileEntity ticker)
            for (int i = 0; i < ticker.payments().getSlots(); i++) {
                ItemStack payment = ticker.payments().extractItem(i, 64, false);
                if (!payment.isEmpty()) com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer.drop(world, pos, payment);
            }
        if (!world.isRemote && world.getTileEntity(pos) instanceof StockTickerTileEntity ticker)
            for (int i = ticker.categories().size() - 1; i >= 0; i--) {
                ItemStack filter = ticker.removeCategory(i);
                if (!filter.isEmpty()) com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer.drop(world, pos, filter);
            }
        super.breakBlock(world, pos, state);
    }
    private static final com.cogwork.cogworkbridge.util.BlockShape COLLISION = com.cogwork.cogworkbridge.util.BlockShape.pixels(1,0,1,15,4,15, 2,0,2,14,16,14);

    private static com.cogwork.cogworkbridge.util.BlockShape collisionShape(net.minecraft.block.state.IBlockState state) { return COLLISION; }
    @Override public void addCollisionBoxToList(net.minecraft.block.state.IBlockState state, net.minecraft.world.World world,
            net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.AxisAlignedBB query, java.util.List<net.minecraft.util.math.AxisAlignedBB> boxes,
            @javax.annotation.Nullable net.minecraft.entity.Entity entity, boolean actual) { collisionShape(state).addCollisionBoxes(pos, query, boxes); }

    @Override public net.minecraft.util.math.AxisAlignedBB getBoundingBox(net.minecraft.block.state.IBlockState state,
            net.minecraft.world.IBlockAccess world, net.minecraft.util.math.BlockPos pos) { return collisionShape(state).bounds(); }
    @Override @javax.annotation.Nullable public net.minecraft.util.math.RayTraceResult collisionRayTrace(net.minecraft.block.state.IBlockState state,
            net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.Vec3d start,
            net.minecraft.util.math.Vec3d end) { return collisionShape(state).rayTrace(pos, start, end); }
}
