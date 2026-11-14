package com.cogwork.cogworkbridge.content.kinetics.saw;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

/** Six-way saw; the second orientation only selects the shaft axis when vertical. */
public final class MechanicalSawBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final PropertyEnum<EnumFacing.Axis> SHAFT_AXIS =
            PropertyEnum.create("shaft_axis", EnumFacing.Axis.class, EnumFacing.Axis.X, EnumFacing.Axis.Z);
    public static final IUnlistedProperty<Boolean> FLIPPED = new IUnlistedProperty<Boolean>() {
        @Override public String getName() { return "flipped"; }
        @Override public boolean isValid(Boolean value) { return value != null; }
        @Override public Class<Boolean> getType() { return Boolean.class; }
        @Override public String valueToString(Boolean value) { return value.toString(); }
    };
    public static final DamageSource SAW_DAMAGE = new DamageSource("cogworkbridge.mechanical_saw");

    public MechanicalSawBlock() {
        super(Material.ROCK);
        setHardness(2.0F);
        setResistance(6.0F);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH)
                .withProperty(SHAFT_AXIS, EnumFacing.Axis.X));
    }

    @Override public boolean hasAxis() { return false; }
    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new net.minecraft.block.properties.IProperty<?>[]{FACING, SHAFT_AXIS},
                new IUnlistedProperty<?>[]{FLIPPED});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return ((IExtendedBlockState) state).withProperty(FLIPPED,
                tile instanceof MechanicalSawTileEntity saw && saw.isFlipped());
    }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(Math.floorMod(meta, 6)))
                .withProperty(SHAFT_AXIS, meta >= 6 ? EnumFacing.Axis.Z : EnumFacing.Axis.X);
    }
    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex() + (state.getValue(SHAFT_AXIS) == EnumFacing.Axis.Z ? 6 : 0);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) {
        return new MechanicalSawTileEntity();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        EnumFacing facing = getPlacementFacing(placer, placer.isSneaking());
        EnumFacing.Axis shaft = placer.getHorizontalFacing().getAxis();
        if (facing.getAxis() == EnumFacing.Axis.Y) {
            EnumFacing.Axis preferred = null;
            for (EnumFacing horizontal : EnumFacing.HORIZONTALS) {
                BlockPos neighbourPos = pos.offset(horizontal);
                IBlockState neighbour = world.getBlockState(neighbourPos);
                if (!(neighbour.getBlock() instanceof KineticBlock kinetic)
                        || !kinetic.canConnectShaftTowards(world, neighbourPos, neighbour, horizontal.getOpposite())) {
                    continue;
                }
                if (preferred != null && preferred != horizontal.getAxis()) {
                    preferred = null;
                    break;
                }
                preferred = horizontal.getAxis();
            }
            if (preferred != null) {
                shaft = preferred;
            }
        }
        return getDefaultState().withProperty(FACING, facing).withProperty(SHAFT_AXIS, shaft);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (!world.isRemote && state.getValue(FACING).getAxis() == EnumFacing.Axis.Y
                && world.getTileEntity(pos) instanceof MechanicalSawTileEntity saw) {
            saw.setFlipped(placer.getHorizontalFacing().getAxisDirection() == EnumFacing.AxisDirection.POSITIVE);
        }
    }

    @Override public EnumFacing.Axis getAxis(IBlockState state) {
        EnumFacing facing = state.getValue(FACING);
        return facing.getAxis() == EnumFacing.Axis.Y ? state.getValue(SHAFT_AXIS) : facing.getAxis();
    }

    @Override
    public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        EnumFacing facing = state.getValue(FACING);
        return facing.getAxis() == EnumFacing.Axis.Y
                ? side.getAxis() == getAxis(state) : side == facing.getOpposite();
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        EnumFacing facing = state.getValue(FACING);
        EnumFacing.Axis shaft = state.getValue(SHAFT_AXIS);
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            shaft = shaft == EnumFacing.Axis.X ? EnumFacing.Axis.Z : EnumFacing.Axis.X;
        }
        return state.withProperty(FACING, rotation.rotate(facing)).withProperty(SHAFT_AXIS, shaft);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return switch (state.getValue(FACING)) {
            case DOWN -> new AxisAlignedBB(0, 0.25D, 0, 1, 1, 1);
            case UP -> new AxisAlignedBB(0, 0, 0, 1, 0.75D, 1);
            case NORTH -> new AxisAlignedBB(0, 0, 0.25D, 1, 1, 1);
            case SOUTH -> new AxisAlignedBB(0, 0, 0, 1, 1, 0.75D);
            case WEST -> new AxisAlignedBB(0.25D, 0, 0, 1, 1, 1);
            case EAST -> new AxisAlignedBB(0, 0, 0, 0.75D, 1, 1);
        };
    }

    public static Vec3d filterCenter(IBlockState state, boolean flipped) {
        double offset = (flipped ? -3 : 3) / 16D;
        return new Vec3d(.5D + (state.getValue(SHAFT_AXIS) == EnumFacing.Axis.X ? offset : 0),
                12.5D / 16D, .5D + (state.getValue(SHAFT_AXIS) == EnumFacing.Axis.Z ? offset : 0));
    }

    public static boolean isFilterPanelHit(IBlockAccess world, BlockPos pos, IBlockState state,
                                            EnumFacing side, float hitX, float hitY, float hitZ) {
        if (side != EnumFacing.UP || state.getValue(FACING) != EnumFacing.UP) return false;
        boolean flipped = world.getTileEntity(pos) instanceof MechanicalSawTileEntity saw && saw.isFlipped();
        Vec3d center = filterCenter(state, flipped);
        return Math.abs(hitX - center.x) <= .18D && Math.abs(hitZ - center.z) <= .18D;
    }

    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
            EntityPlayer player, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        if (hand != EnumHand.MAIN_HAND || player.isSneaking()
                || held.getItem() == com.cogwork.cogworkbridge.registry.AllItems.WRENCH
                || !player.canPlayerEdit(pos, side, held)
                || !isFilterPanelHit(world, pos, state, side, hitX, hitY, hitZ)
                || !(world.getTileEntity(pos) instanceof MechanicalSawTileEntity saw)) return false;
        if (!world.isRemote) {
            ItemStack previous = saw.getFilter();
            saw.setFilter(held);
            if (!player.capabilities.isCreativeMode
                    && com.cogwork.cogworkbridge.content.fluids.ListFilterItem.isFilterItem(held)) held.shrink(1);
            if (com.cogwork.cogworkbridge.content.fluids.ListFilterItem.isFilterItem(previous)) {
                boolean alreadyHeld = false;
                if (player.capabilities.isCreativeMode) for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                    ItemStack present = player.inventory.getStackInSlot(i);
                    if (ItemStack.areItemsEqual(present, previous) && ItemStack.areItemStackTagsEqual(present, previous)) {
                        alreadyHeld = true; break;
                    }
                }
                if (!alreadyHeld) net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(player, previous);
            }
        }
        return true;
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (entity instanceof EntityItem item) {
            if (!world.isRemote && world.getTileEntity(pos) instanceof MechanicalSawTileEntity saw) saw.acceptItem(item);
            return;
        }
        if (world.isRemote || world instanceof FakeWorld
                || !new AxisAlignedBB(pos).shrink(0.1D).intersects(entity.getEntityBoundingBox())) {
            return;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof MechanicalSawTileEntity saw && saw.getSpeed() != 0.0F) {
            float speed = Math.abs(saw.getSpeed());
            float damage = MathHelper.clamp(Math.min(speed / 16.0F, 2.0F)
                    + Math.min(speed / 32.0F, 4.0F) + Math.min(speed / 64.0F, 4.0F), 1.0F, 10.0F);
            entity.attackEntityFrom(SAW_DAMAGE, damage);
        }
    }

    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (world.getTileEntity(pos) instanceof MechanicalSawTileEntity saw) saw.dropCuttingInventory();
        super.breakBlock(world, pos, state);
    }

    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (world.getTileEntity(pos) instanceof MechanicalSawTileEntity saw) saw.wakeBreaker();
    }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
}
