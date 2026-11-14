package com.cogwork.cogworkbridge.content.kinetics.deployer;

import com.cogwork.cogworkbridge.content.processing.AssemblyOperatorItemBlock;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import javax.annotation.Nullable;

/** A six-way Deployer with its perpendicular shaft orientation encoded as an axis. */
public final class DeployerBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final PropertyEnum<EnumFacing.Axis> AXIS_ALONG_FIRST_COORDINATE =
            PropertyEnum.create("axis_along_first", EnumFacing.Axis.class,
                    EnumFacing.Axis.X, EnumFacing.Axis.Z);

    public DeployerBlock() {
        super(Material.ROCK, "cogworkbridge.deployer");
        setHardness(2.0F);
        setResistance(6.0F);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH)
                .withProperty(AXIS_ALONG_FIRST_COORDINATE, EnumFacing.Axis.Z));
    }

    @Override public boolean hasAxis() { return false; }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, AXIS_ALONG_FIRST_COORDINATE);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        int value = Math.floorMod(meta, 12);
        return getDefaultState()
                .withProperty(FACING, EnumFacing.byIndex(value % 6))
                .withProperty(AXIS_ALONG_FIRST_COORDINATE,
                        value >= 6 ? EnumFacing.Axis.X : EnumFacing.Axis.Z);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex()
                + (state.getValue(AXIS_ALONG_FIRST_COORDINATE) == EnumFacing.Axis.X ? 6 : 0);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new DeployerTileEntity();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        EnumFacing facing = AssemblyOperatorItemBlock.isRedirectedPlacement()
                ? EnumFacing.DOWN : getPlacementFacing(placer, placer.isSneaking());
        EnumFacing.Axis alignment = placer.getHorizontalFacing().getAxis();
        EnumFacing.Axis preferred = null;
        for (EnumFacing neighbourSide : EnumFacing.VALUES) {
            BlockPos neighbourPos = pos.offset(neighbourSide);
            IBlockState neighbourState = world.getBlockState(neighbourPos);
            if (!(neighbourState.getBlock() instanceof KineticBlock neighbour)
                    || !neighbour.canConnectShaftTowards(world, neighbourPos,
                    neighbourState, neighbourSide.getOpposite())) {
                continue;
            }
            if (neighbourSide.getAxis() != rotationAxis(facing, EnumFacing.Axis.X)
                    && neighbourSide.getAxis() != rotationAxis(facing, EnumFacing.Axis.Z)) {
                continue;
            }
            if (preferred != null && preferred != neighbourSide.getAxis()) {
                preferred = null;
                break;
            }
            preferred = neighbourSide.getAxis();
        }
        if (preferred != null) {
            alignment = preferred == rotationAxis(facing, EnumFacing.Axis.X)
                    ? EnumFacing.Axis.X : EnumFacing.Axis.Z;
        }
        return getDefaultState().withProperty(FACING, facing)
                .withProperty(AXIS_ALONG_FIRST_COORDINATE, alignment);
    }

    @Override
    public EnumFacing.Axis getAxis(IBlockState state) {
        return rotationAxis(state.getValue(FACING),
                state.getValue(AXIS_ALONG_FIRST_COORDINATE));
    }

    public static EnumFacing.Axis rotationAxis(EnumFacing facing, EnumFacing.Axis alignment) {
        boolean alongFirst = alignment == EnumFacing.Axis.X;
        return switch (facing.getAxis()) {
            case X -> alongFirst ? EnumFacing.Axis.Y : EnumFacing.Axis.Z;
            case Y -> alongFirst ? EnumFacing.Axis.X : EnumFacing.Axis.Z;
            case Z -> alongFirst ? EnumFacing.Axis.X : EnumFacing.Axis.Y;
        };
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        EnumFacing oldFacing = state.getValue(FACING);
        EnumFacing.Axis oldAxis = getAxis(state);
        EnumFacing newFacing = rotation.rotate(oldFacing);
        EnumFacing.Axis newAxis = rotateAxis(oldAxis, rotation);
        return state.withProperty(FACING, newFacing)
                .withProperty(AXIS_ALONG_FIRST_COORDINATE, alignmentFor(newFacing, newAxis));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        EnumFacing newFacing = mirror.mirror(state.getValue(FACING));
        return state.withProperty(FACING, newFacing)
                .withProperty(AXIS_ALONG_FIRST_COORDINATE,
                        alignmentFor(newFacing, getAxis(state)));
    }

    private static EnumFacing.Axis rotateAxis(EnumFacing.Axis axis, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            if (axis == EnumFacing.Axis.X) return EnumFacing.Axis.Z;
            if (axis == EnumFacing.Axis.Z) return EnumFacing.Axis.X;
        }
        return axis;
    }

    private static EnumFacing.Axis alignmentFor(EnumFacing facing, EnumFacing.Axis shaftAxis) {
        return rotationAxis(facing, EnumFacing.Axis.X) == shaftAxis
                ? EnumFacing.Axis.X : EnumFacing.Axis.Z;
    }

    @Override
    public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        return side.getAxis() == getAxis(state);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return FULL_BLOCK_AABB;
    }

    private static AxisAlignedBB casing(IBlockState state) {
        return switch (state.getValue(FACING)) {
            case UP -> new AxisAlignedBB(0, 0, 0, 1, .75, 1);
            case DOWN -> new AxisAlignedBB(0, .25, 0, 1, 1, 1);
            case NORTH -> new AxisAlignedBB(0, 0, .25, 1, 1, 1);
            case SOUTH -> new AxisAlignedBB(0, 0, 0, 1, 1, .75);
            case WEST -> new AxisAlignedBB(.25, 0, 0, 1, 1, 1);
            case EAST -> new AxisAlignedBB(0, 0, 0, .75, 1, 1);
        };
    }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return casing(state); }
    @Override @Nullable public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos, Vec3d start, Vec3d end) {
        AxisAlignedBB pole = switch (state.getValue(FACING).getAxis()) {
            case X -> new AxisAlignedBB(0, 5D / 16D, 5D / 16D, 1, 11D / 16D, 11D / 16D);
            case Y -> new AxisAlignedBB(5D / 16D, 0, 5D / 16D, 11D / 16D, 1, 11D / 16D);
            case Z -> new AxisAlignedBB(5D / 16D, 5D / 16D, 0, 11D / 16D, 11D / 16D, 1);
        };
        RayTraceResult bodyHit = rayTrace(pos, start, end, casing(state));
        RayTraceResult poleHit = rayTrace(pos, start, end, pole);
        return poleHit != null && (bodyHit == null || poleHit.hitVec.squareDistanceTo(start) < bodyHit.hitVec.squareDistanceTo(start)) ? poleHit : bodyHit;
    }

    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block,
                                BlockPos fromPos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof DeployerTileEntity deployer) {
            deployer.updateRedstone();
        }
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof DeployerTileEntity deployer) deployer.updateRedstone();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing side,
                                    float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return false;
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == com.cogwork.cogworkbridge.registry.AllItems.WRENCH) {
            return false;
        }
        if (!(world.getTileEntity(pos) instanceof DeployerTileEntity deployer)) return false;
        if (isFilterPanelHit(state, side, hitX, hitY, hitZ)) {
            if (!world.isRemote) {
                ItemStack previous = deployer.getFilter().copy();
                deployer.setFilter(held);
                if (!player.capabilities.isCreativeMode && com.cogwork.cogworkbridge.content.fluids.ListFilterItem.isFilterItem(held)) held.shrink(1);
                if (com.cogwork.cogworkbridge.content.fluids.ListFilterItem.isFilterItem(previous)
                        && (!player.capabilities.isCreativeMode || !hasFilter(player, previous)))
                    net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(player, previous);
            }
            return true;
        }
        if (side == state.getValue(FACING).getOpposite() && held.isEmpty()
                && !deployer.getCloggedItem().isEmpty()) {
            if (!world.isRemote) {
                ItemStack returned = deployer.takeCloggedItem();
                if (!player.addItemStackToInventory(returned)) spawnAsEntity(world, pos, returned);
            }
            return true;
        }
        if (side != state.getValue(FACING) || player.isSneaking()) return false;
        if (world.isRemote) {
            return true;
        }
        ItemStack previous = deployer.swapHeldItem(held);
        player.setHeldItem(hand, previous);
        return !held.isEmpty() || !previous.isEmpty();
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof DeployerTileEntity deployer) {
            if (!deployer.getHeldItem().isEmpty()) {
                spawnAsEntity(world, pos, deployer.getHeldItem().copy());
            }
            if (!deployer.getCloggedItem().isEmpty()) {
                spawnAsEntity(world, pos, deployer.getCloggedItem().copy());
            }
            if (com.cogwork.cogworkbridge.content.fluids.ListFilterItem.isFilterItem(deployer.getFilter()))
                com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer.drop(world, pos, deployer.getFilter());
        }
        super.breakBlock(world, pos, state);
    }

    private static boolean hasFilter(EntityPlayer player, ItemStack filter) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack present = player.inventory.getStackInSlot(i);
            if (ItemStack.areItemsEqual(present, filter) && ItemStack.areItemStackTagsEqual(present, filter)) return true;
        }
        return false;
    }

    public static boolean isFilterSide(IBlockState state, EnumFacing side) {
        return side.getAxis() != state.getValue(FACING).getAxis()
                && side.getAxis() != rotationAxis(state.getValue(FACING), state.getValue(AXIS_ALONG_FIRST_COORDINATE));
    }
    public static Vec3d filterCenter(IBlockState state, EnumFacing side) {
        EnumFacing facing = state.getValue(FACING);
        return new Vec3d(.5D + side.getXOffset() * .5D - facing.getXOffset() * .125D,
                .5D + side.getYOffset() * .5D - facing.getYOffset() * .125D,
                .5D + side.getZOffset() * .5D - facing.getZOffset() * .125D);
    }
    public static boolean isFilterPanelHit(IBlockState state, EnumFacing side,
                                     float hitX, float hitY, float hitZ) {
        if (!isFilterSide(state, side)) return false;
        Vec3d center = filterCenter(state, side);
        double dx = side.getAxis() == EnumFacing.Axis.X ? 0 : hitX - center.x;
        double dy = side.getAxis() == EnumFacing.Axis.Y ? 0 : hitY - center.y;
        double dz = side.getAxis() == EnumFacing.Axis.Z ? 0 : hitZ - center.z;
        return dx * dx + dy * dy + dz * dz <= .25D * .25D;
    }

    public boolean wrench(World world, BlockPos pos, IBlockState state,
                          float hitX, float hitY, float hitZ) {
        if (!(world.getTileEntity(pos) instanceof DeployerTileEntity deployer)) {
            return false;
        }
        EnumFacing facing = state.getValue(FACING);
        double distance = (hitX - 0.5D) * facing.getXOffset()
                + (hitY - 0.5D) * facing.getYOffset()
                + (hitZ - 0.5D) * facing.getZOffset() + 0.5D;
        if (distance <= 0.75D) return false;
        deployer.toggleMode();
        return true;
    }
}
