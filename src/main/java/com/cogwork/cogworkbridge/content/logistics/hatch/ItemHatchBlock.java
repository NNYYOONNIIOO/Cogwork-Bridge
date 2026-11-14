package com.cogwork.cogworkbridge.content.logistics.hatch;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
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
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class ItemHatchBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final IUnlistedProperty<Boolean> OPEN = new IUnlistedProperty<Boolean>() {
        @Override public String getName() { return "hatch_open"; }
        @Override public boolean isValid(Boolean value) { return value != null; }
        @Override public Class<Boolean> getType() { return Boolean.class; }
        @Override public String valueToString(Boolean value) { return value.toString(); }
    };
    public ItemHatchBlock() {
        super(Material.IRON);
        setHardness(2); setResistance(6); setSoundType(SoundType.METAL); setLightOpacity(0);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH));
    }
    @Override protected net.minecraft.block.state.BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{FACING}, new IUnlistedProperty<?>[]{OPEN});
    }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(Math.floorMod(meta, 4)));
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, side.getAxis().isHorizontal() ? side.getOpposite() : placer.getHorizontalFacing());
    }
    @Override public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        return side.getAxis().isHorizontal() && super.canPlaceBlockOnSide(world, pos, side);
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withProperty(FACING, mirror.mirror(state.getValue(FACING)));
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state instanceof IExtendedBlockState extended ? extended.withProperty(OPEN,
                world.getTileEntity(pos) instanceof ItemHatchTileEntity hatch && hatch.isOpen()) : state;
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new ItemHatchTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        List<AxisAlignedBB> boxes = shapes(state.getValue(FACING));
        AxisAlignedBB bounds = boxes.get(0);
        for (int i = 1; i < boxes.size(); i++) bounds = bounds.union(boxes.get(i));
        return bounds;
    }

    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return getBoundingBox(state, world, pos);
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        for (AxisAlignedBB local : shapes(state.getValue(FACING))) {
            AxisAlignedBB box = local.offset(pos);
            if (query.intersects(box)) boxes.add(box);
        }
    }

    private static List<AxisAlignedBB> shapes(EnumFacing facing) {
        List<AxisAlignedBB> boxes = new ArrayList<>(4);
        boxes.add(box16(1, 0, 0, 15, 16, 2));
        boxes.add(box16(2, 2, 0, 14, 13, 3.8D));
        boxes.add(box16(2, 4, 0, 14, 11, 5.8D));
        boxes.add(box16(2, 6, 0, 14, 9, 7.8D));

        // The upstream shape is authored facing south, while the block state's facing points outwards.
        EnumFacing shapeFacing = facing.getOpposite();
        if (shapeFacing == EnumFacing.SOUTH) return boxes;
        List<AxisAlignedBB> rotated = new ArrayList<>(boxes.size());
        for (AxisAlignedBB box : boxes) {
            if (shapeFacing == EnumFacing.NORTH) {
                rotated.add(new AxisAlignedBB(1 - box.maxX, box.minY, 1 - box.maxZ,
                        1 - box.minX, box.maxY, 1 - box.minZ));
            } else if (shapeFacing == EnumFacing.WEST) {
                rotated.add(new AxisAlignedBB(1 - box.maxZ, box.minY, box.minX,
                        1 - box.minZ, box.maxY, box.maxX));
            } else {
                rotated.add(new AxisAlignedBB(box.minZ, box.minY, 1 - box.maxX,
                        box.maxZ, box.maxY, 1 - box.minX));
            }
        }
        return rotated;
    }

    private static AxisAlignedBB box16(double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
        return new AxisAlignedBB(minX / 16D, minY / 16D, minZ / 16D,
                maxX / 16D, maxY / 16D, maxZ / 16D);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (!(world.getTileEntity(pos) instanceof ItemHatchTileEntity hatch) || player instanceof FakePlayer) return false;
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == AllItems.WRENCH) return false;
        EnumFacing facing = state.getValue(FACING);
        double offsetX = facing.getAxis() == EnumFacing.Axis.X ? -facing.getXOffset() * 1.5D / 16D : 0;
        double offsetZ = facing.getAxis() == EnumFacing.Axis.Z ? -facing.getZOffset() * 1.5D / 16D : 0;
        double dx = x - (0.5D + offsetX), dy = y - (5.15D / 16D - 0.125D), dz = z - (0.5D + offsetZ);
        boolean filterSlot = dx * dx + dy * dy + dz * dz < Math.pow(0.5D * 0.965D / 2D, 2);
        if (filterSlot && !player.isSneaking()) {
            if (!world.isRemote) hatch.selectFilter(player, hand);
            return true;
        }
        IItemHandler target = ItemTransfer.at(world, pos.offset(facing), null);
        if (target == null) target = ItemTransfer.at(world, pos.offset(facing), facing.getOpposite());
        if (target == null) return false;
        if (world.isRemote) return true;
        // Include the hotbar when the player asks to deposit the whole inventory.
        int first = player.isSneaking() ? 0 : player.inventory.currentItem;
        int end = player.isSneaking() ? 36 : first + 1;
        boolean inserted = false;
        for (int slot = first; slot < end; slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack.isEmpty() || !hatch.accepts(stack)) continue;
            int original = stack.getCount();
            ItemStack rest = ItemHandlerHelper.insertItemStacked(target, stack.copy(), false);
            if (rest.getCount() < original) {
                player.inventory.setInventorySlotContents(slot, rest);
                inserted = true;
            }
        }
        if (inserted) {
            player.inventory.markDirty();
            hatch.open();
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                    player.isSneaking() ? "cogworkbridge.hatch.deposit_inventory" : "cogworkbridge.hatch.deposit_item"), true);
        }
        if (inserted) world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_CHEST_OPEN,
                net.minecraft.util.SoundCategory.BLOCKS, 0.25F, 1.4F);
        return true;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof ItemHatchTileEntity hatch
                && ListFilterItem.isFilterItem(hatch.getFilter())) ItemTransfer.drop(world, pos, hatch.getFilter());
        super.breakBlock(world, pos, state);
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void allowSneaking(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().getBlockState(event.getPos()).getBlock() instanceof ItemHatchBlock)
            event.setUseBlock(Event.Result.ALLOW);
    }
}
