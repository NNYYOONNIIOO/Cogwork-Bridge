package com.cogwork.cogworkbridge.content.decoration.palette;

import com.cogwork.cogworkbridge.registry.AllItems;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.items.ItemHandlerHelper;

public abstract class CopycatBlock extends Block implements com.cogwork.cogworkbridge.content.schematics.SchematicMaterialProvider {
    @Override public java.util.List<ItemStack> schematicMaterials(IBlockState state, @Nullable net.minecraft.nbt.NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("Material", 10)) return java.util.Collections.emptyList();
        IBlockState material = net.minecraft.nbt.NBTUtil.readBlockState(tag.getCompoundTag("Material"));
        if (material.getBlock() == Blocks.AIR) return java.util.Collections.emptyList();
        ItemStack consumed = tag.hasKey("Consumed", 10) ? new ItemStack(tag.getCompoundTag("Consumed")) : ItemStack.EMPTY;
        if (consumed.isEmpty()) consumed = new ItemStack(material.getBlock(), 1, material.getBlock().damageDropped(material));
        if (consumed.isEmpty()) throw new IllegalArgumentException("Unsupported copycat material");
        consumed.setCount(1);
        return java.util.Collections.singletonList(consumed);
    }
    public static final IUnlistedProperty<Integer> OCCLUSION = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "copycat_occlusion"; }
        @Override public boolean isValid(Integer value) { return value != null; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };
    public static final IUnlistedProperty<IBlockState> MATERIAL = new IUnlistedProperty<IBlockState>() {
        @Override public String getName() { return "copycat_material"; }
        @Override public boolean isValid(IBlockState value) { return value != null; }
        @Override public Class<IBlockState> getType() { return IBlockState.class; }
        @Override public String valueToString(IBlockState value) { return value.toString(); }
    };
    protected CopycatBlock() { super(Material.IRON); setHardness(1F); setHarvestLevel("pickaxe", 0); }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Nullable @Override public TileEntity createTileEntity(World world, IBlockState state) { return new CopycatTile(); }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        IBlockState material = tile instanceof CopycatTile copycat ? copycat.material() : Blocks.AIR.getDefaultState();
        int occlusion = 0;
        if (material.getBlock() != Blocks.AIR) {
            AxisAlignedBB shape = bounds(state);
            for (EnumFacing face : EnumFacing.VALUES) {
                BlockPos otherPos = pos.offset(face);
                IBlockState other = world.getBlockState(otherPos);
                AxisAlignedBB otherShape = FULL_BLOCK_AABB;
                if (other.getBlock() instanceof CopycatBlock copycat && world.getTileEntity(otherPos) instanceof CopycatTile otherTile) {
                    otherShape = copycat.bounds(other); other = otherTile.material();
                }
                if ((other.isOpaqueCube() || other == material) && coversFace(shape, otherShape, face))
                    occlusion |= 1 << face.getIndex();
            }
            MaterialAccess access = new MaterialAccess(world, pos, material, shape);
            try {
                material = material.getBlock().getActualState(material, access, pos);
                material = material.getBlock().getExtendedState(material, access, pos);
            } catch (RuntimeException ignored) { /* Unknown material models retain their ordinary state. */ }
        }
        return ((IExtendedBlockState) state).withProperty(MATERIAL, material).withProperty(OCCLUSION, occlusion);
    }
    private static boolean coversFace(AxisAlignedBB box, AxisAlignedBB other, EnumFacing face) {
        double[] low = {box.minX, box.minY, box.minZ}, high = {box.maxX, box.maxY, box.maxZ};
        double[] ol = {other.minX, other.minY, other.minZ}, oh = {other.maxX, other.maxY, other.maxZ};
        int axis = face.getAxis() == EnumFacing.Axis.X ? 0 : face.getAxis() == EnumFacing.Axis.Y ? 1 : 2;
        if (face.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? high[axis] < 1 || ol[axis] > 0 : low[axis] > 0 || oh[axis] < 1) return false;
        for (int i = 0; i < 3; i++) if (i != axis && (ol[i] > low[i] || oh[i] < high[i])) return false;
        return true;
    }
    /** A material sees neighbouring coplanar copycats as their material, not as iron panels. */
    private record MaterialAccess(IBlockAccess delegate, BlockPos origin, IBlockState material, AxisAlignedBB shape) implements IBlockAccess {
        @Override public IBlockState getBlockState(BlockPos pos) {
            if (origin.equals(pos)) return material;
            IBlockState state = delegate.getBlockState(pos);
            if (state.getBlock() instanceof CopycatBlock copycat) {
                if (!shape.equals(copycat.bounds(state))) return Blocks.AIR.getDefaultState();
                return delegate.getTileEntity(pos) instanceof CopycatTile tile ? tile.material() : Blocks.AIR.getDefaultState();
            }
            return state;
        }
        @Override public TileEntity getTileEntity(BlockPos pos) { return delegate.getBlockState(pos).getBlock() instanceof CopycatBlock ? null : delegate.getTileEntity(pos); }
        @Override public int getCombinedLight(BlockPos pos, int light) { return delegate.getCombinedLight(pos, light); }
        @Override public boolean isAirBlock(BlockPos pos) { return getBlockState(pos).getBlock() == Blocks.AIR; }
        @Override public net.minecraft.world.biome.Biome getBiome(BlockPos pos) { return delegate.getBiome(pos); }
        @Override public int getStrongPower(BlockPos pos, EnumFacing face) { return delegate.getStrongPower(pos, face); }
        @Override public net.minecraft.world.WorldType getWorldType() { return delegate.getWorldType(); }
        @Override public boolean isSideSolid(BlockPos pos, EnumFacing face, boolean fallback) { return getBlockState(pos).isSideSolid(this, pos, face); }
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) { return true; }
    @Override public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return true; }
    @Override public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof CopycatTile copycat) || copycat.material().getBlock() == Blocks.AIR) return 0;
        return copycat.material().getLightValue(world, pos);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing face, float x, float y, float z) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof CopycatTile copycat)) return false;
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == AllItems.WRENCH && copycat.material().getBlock() != Blocks.AIR) {
            if (!world.isRemote) { ItemStack old = copycat.takeMaterial(); if (!player.capabilities.isCreativeMode && !old.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, old); }
            return true;
        }
        IBlockState material = acceptedMaterial(held, state, face, player, y);
        if (material == null) return false;
        if (copycat.material().getBlock() == material.getBlock()) {
            IBlockState next = cycleOrientation(copycat.material());
            if (next == copycat.material()) return false;
            if (!world.isRemote) copycat.setMaterial(next, copycat.consumed());
            return true;
        }
        if (copycat.material().getBlock() != Blocks.AIR) return false;
        if (!world.isRemote) {
            copycat.setMaterial(material, player.capabilities.isCreativeMode ? ItemStack.EMPTY : held);
            if (!player.capabilities.isCreativeMode) held.shrink(1);
        }
        return true;
    }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote || !(placer instanceof EntityPlayer player) || !(world.getTileEntity(pos) instanceof CopycatTile tile)) return;
        ItemStack offhand = player.getHeldItemOffhand();
        IBlockState material = acceptedMaterial(offhand, state, EnumFacing.getDirectionFromEntityLiving(pos, placer), player, .5F);
        if (material == null || tile.material().getBlock() != Blocks.AIR) return;
        tile.setMaterial(material, player.capabilities.isCreativeMode ? ItemStack.EMPTY : offhand);
        if (!player.capabilities.isCreativeMode) offhand.shrink(1);
    }
    @Nullable private IBlockState acceptedMaterial(ItemStack held, IBlockState copycatState, EnumFacing face, EntityPlayer player, float hitY) {
        if (held.isEmpty() || !(held.getItem() instanceof ItemBlock item)) return null;
        Block block = item.getBlock();
        IBlockState material = block.getStateFromMeta(item.getMetadata(held.getMetadata()));
        if (block instanceof CopycatBlock || block.hasTileEntity(material)) return null;
        if (this instanceof Panel && block instanceof BlockTrapDoor) {
            EnumFacing panel = copycatState.getValue(Panel.FACING);
            boolean vertical = panel.getAxis().isHorizontal();
            return material.withProperty(BlockTrapDoor.FACING, vertical ? panel : player.getHorizontalFacing())
                    .withProperty(BlockTrapDoor.OPEN, vertical)
                    .withProperty(BlockTrapDoor.HALF, panel == EnumFacing.DOWN || vertical && hitY > .5F
                            ? BlockTrapDoor.DoorHalf.TOP : BlockTrapDoor.DoorHalf.BOTTOM);
        }
        if (!material.isFullCube() && !(this instanceof Panel && isBars(material))) return null;
        for (IProperty<?> property : material.getPropertyKeys()) {
            if (property instanceof PropertyDirection direction && direction.getName().equals("facing")
                    && direction.getAllowedValues().contains(face)) material = material.withProperty(direction, face);
            else if (property instanceof PropertyEnum<?> axis && axis.getValueClass() == EnumFacing.Axis.class && axis.getName().equals("axis")) {
                @SuppressWarnings("unchecked") PropertyEnum<EnumFacing.Axis> orientation = (PropertyEnum<EnumFacing.Axis>) axis;
                if (orientation.getAllowedValues().contains(face.getAxis())) material = material.withProperty(orientation, face.getAxis());
            }
        }
        return material;
    }
    private static IBlockState cycleOrientation(IBlockState material) {
        if (material.getBlock() instanceof BlockTrapDoor && material.getValue(BlockTrapDoor.OPEN))
            return material.cycleProperty(BlockTrapDoor.HALF);
        for (IProperty<?> property : material.getPropertyKeys()) {
            if (property instanceof PropertyDirection direction && direction.getName().equals("facing")) return material.cycleProperty(direction);
            if (property instanceof PropertyEnum<?> axis && axis.getValueClass() == EnumFacing.Axis.class && axis.getName().equals("axis")) {
                @SuppressWarnings("unchecked") PropertyEnum<EnumFacing.Axis> orientation = (PropertyEnum<EnumFacing.Axis>) axis;
                return material.cycleProperty(orientation);
            }
        }
        return material;
    }
    public static boolean isBars(IBlockState material) {
        return material.getBlock() instanceof BlockPane && material.getMaterial() == Material.IRON;
    }
    @Override public void onBlockHarvested(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        if (!world.isRemote && player.capabilities.isCreativeMode && world.getTileEntity(pos) instanceof CopycatTile tile) tile.takeMaterial();
        super.onBlockHarvested(world, pos, state, player);
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof CopycatTile tile && !tile.consumed().isEmpty()) spawnAsEntity(world, pos, tile.consumed().copy());
        super.breakBlock(world, pos, state);
    }
    public abstract AxisAlignedBB bounds(IBlockState state);
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return bounds(state); }

    public static class Panel extends CopycatBlock {
        public static final PropertyDirection FACING = PropertyDirection.create("facing");
        public Panel() { setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP)); }
        @Override protected BlockStateContainer createBlockState() { return new ExtendedBlockState(this, new IProperty<?>[]{FACING}, new IUnlistedProperty<?>[]{MATERIAL, OCCLUSION}); }
        @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta % 6)); }
        @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
        @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
            return getDefaultState().withProperty(FACING, EnumFacing.getDirectionFromEntityLiving(pos, placer).getOpposite());
        }
        @Override public AxisAlignedBB bounds(IBlockState state) {
            double t = 3D / 16D;
            switch (state.getValue(FACING)) {
                case DOWN: return new AxisAlignedBB(0, 1-t, 0, 1, 1, 1);
                case NORTH: return new AxisAlignedBB(0, 0, 1-t, 1, 1, 1);
                case SOUTH: return new AxisAlignedBB(0, 0, 0, 1, 1, t);
                case WEST: return new AxisAlignedBB(1-t, 0, 0, 1, 1, 1);
                case EAST: return new AxisAlignedBB(0, 0, 0, t, 1, 1);
                default: return new AxisAlignedBB(0, 0, 0, 1, t, 1);
            }
        }
    }

    public static class Step extends CopycatBlock {
        public Step() { setDefaultState(blockState.getBaseState().withProperty(BlockHorizontal.FACING, EnumFacing.SOUTH).withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.BOTTOM)); }
        @Override protected BlockStateContainer createBlockState() { return new ExtendedBlockState(this, new IProperty<?>[]{BlockHorizontal.FACING, BlockStairs.HALF}, new IUnlistedProperty<?>[]{MATERIAL, OCCLUSION}); }
        @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.byHorizontalIndex(meta & 3)).withProperty(BlockStairs.HALF, (meta & 4) != 0 ? BlockStairs.EnumHalf.TOP : BlockStairs.EnumHalf.BOTTOM); }
        @Override public int getMetaFromState(IBlockState state) { return state.getValue(BlockHorizontal.FACING).getHorizontalIndex() | (state.getValue(BlockStairs.HALF) == BlockStairs.EnumHalf.TOP ? 4 : 0); }
        @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
            return getDefaultState().withProperty(BlockHorizontal.FACING, placer.getHorizontalFacing()).withProperty(BlockStairs.HALF, side == EnumFacing.DOWN || side.getAxis().isHorizontal() && y > .5F ? BlockStairs.EnumHalf.TOP : BlockStairs.EnumHalf.BOTTOM);
        }
        @Override public AxisAlignedBB bounds(IBlockState state) {
            boolean top = state.getValue(BlockStairs.HALF) == BlockStairs.EnumHalf.TOP;
            double minY = top ? .5D : 0D, maxY = top ? 1D : .5D;
            switch (state.getValue(BlockHorizontal.FACING)) {
                case NORTH: return new AxisAlignedBB(0, minY, 0, 1, maxY, .5);
                case WEST: return new AxisAlignedBB(0, minY, 0, .5, maxY, 1);
                case EAST: return new AxisAlignedBB(.5, minY, 0, 1, maxY, 1);
                default: return new AxisAlignedBB(0, minY, .5, 1, maxY, 1);
            }
        }
    }
}
