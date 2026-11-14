package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld;
import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.init.SoundEvents;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.List;

public final class SmartFluidPipeBlock extends FluidPipeBlock {
    public enum Attachment implements IStringSerializable {
        FLOOR, WALL, CEILING;
        @Override public String getName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public static final PropertyEnum<Attachment> ATTACHMENT = PropertyEnum.create("face", Attachment.class);
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    // Preserve the previous axis-only metadata in existing worlds.
    private static final Attachment[] META_FACE = {
            Attachment.FLOOR, Attachment.WALL, Attachment.FLOOR, Attachment.FLOOR,
            Attachment.FLOOR, Attachment.CEILING, Attachment.CEILING, Attachment.CEILING,
            Attachment.CEILING, Attachment.WALL, Attachment.WALL, Attachment.WALL
    };
    private static final EnumFacing[] META_FACING = {
            EnumFacing.EAST, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST,
            EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH,
            EnumFacing.WEST, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST
    };

    public SmartFluidPipeBlock() {
        setDefaultState(getDefaultState().withProperty(ATTACHMENT, Attachment.WALL)
                .withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new net.minecraftforge.common.property.ExtendedBlockState(
                this, new IProperty<?>[]{ATTACHMENT, FACING}, visualProperties());
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        int index = Math.floorMod(meta, META_FACE.length);
        return getDefaultState().withProperty(ATTACHMENT, META_FACE[index]).withProperty(FACING, META_FACING[index]);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        for (int i = 0; i < META_FACE.length; i++) {
            if (META_FACE[i] == state.getValue(ATTACHMENT) && META_FACING[i] == state.getValue(FACING)) return i;
        }
        return 1;
    }

    public static EnumFacing.Axis getPipeAxis(IBlockState state) {
        return state.getValue(ATTACHMENT) == Attachment.WALL
                ? EnumFacing.Axis.Y : state.getValue(FACING).getAxis();
    }

    public static EnumFacing getPanelSide(IBlockState state) {
        return switch (state.getValue(ATTACHMENT)) {
            case FLOOR -> EnumFacing.UP;
            case CEILING -> EnumFacing.DOWN;
            case WALL -> state.getValue(FACING);
        };
    }

    public static float filterYaw(IBlockState state) {
        float angle = switch (state.getValue(FACING)) {
            case SOUTH -> 0F;
            case WEST -> -90F;
            case NORTH -> 180F;
            case EAST -> 90F;
            default -> 0F;
        };
        return state.getValue(ATTACHMENT) == Attachment.WALL ? angle + 180F : angle;
    }

    public static float filterPitch(IBlockState state) {
        return state.getValue(ATTACHMENT) == Attachment.CEILING ? -45F : 45F;
    }

    public static Vec3d filterCenter(IBlockState state) {
        Attachment face = state.getValue(ATTACHMENT);
        double y = (face == Attachment.CEILING ? 0.55D : face == Attachment.WALL ? 11.4D : 15.45D) / 16.0D;
        double z = (face == Attachment.CEILING ? 4.6D : face == Attachment.WALL ? 0.55D : 4.625D) / 16.0D;
        EnumFacing facing = state.getValue(FACING);
        int turns = switch (facing) { case SOUTH -> 0; case WEST -> 1; case NORTH -> 2; default -> 3; };
        if (face == Attachment.WALL) turns = (turns + 2) % 4;
        double x = 0.5D;
        for (int i = 0; i < turns; i++) {
            double nextX = 1.0D - z;
            z = x;
            x = nextX;
        }
        return new Vec3d(x, y, z);
    }

    private static AxisAlignedBB pipeShape(IBlockState state) {
        return switch (getPipeAxis(state)) {
            case X -> new AxisAlignedBB(0, 0.25D, 0.25D, 1, 0.75D, 0.75D);
            case Y -> new AxisAlignedBB(0.25D, 0, 0.25D, 0.75D, 1, 0.75D);
            case Z -> new AxisAlignedBB(0.25D, 0.25D, 0, 0.75D, 0.75D, 1);
        };
    }

    private static AxisAlignedBB panelShape(IBlockState state) {
        return switch (getPanelSide(state)) {
            case DOWN -> new AxisAlignedBB(0.3125D, 0, 0.3125D, 0.6875D, 0.25D, 0.6875D);
            case UP -> new AxisAlignedBB(0.3125D, 0.75D, 0.3125D, 0.6875D, 1, 0.6875D);
            case NORTH -> new AxisAlignedBB(0.3125D, 0.3125D, 0, 0.6875D, 0.8125D, 0.25D);
            case SOUTH -> new AxisAlignedBB(0.3125D, 0.3125D, 0.75D, 0.6875D, 0.8125D, 1);
            case WEST -> new AxisAlignedBB(0, 0.3125D, 0.3125D, 0.25D, 0.8125D, 0.6875D);
            case EAST -> new AxisAlignedBB(0.75D, 0.3125D, 0.3125D, 1, 0.8125D, 0.6875D);
        };
    }

    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return pipeShape(state).union(panelShape(state))
                .union(new AxisAlignedBB(0.1875D, 0.1875D, 0.1875D, 0.8125D, 0.8125D, 0.8125D));
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        addBox(pos, query, boxes, pipeShape(state));
        addBox(pos, query, boxes, panelShape(state));
        addBox(pos, query, boxes, new AxisAlignedBB(0.1875D, 0.1875D, 0.1875D, 0.8125D, 0.8125D, 0.8125D));
    }

    private static void addBox(BlockPos pos, AxisAlignedBB query, List<AxisAlignedBB> boxes, AxisAlignedBB local) {
        AxisAlignedBB box = local.offset(pos);
        if (query.intersects(box)) boxes.add(box);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float hitX, float hitY,
                                            float hitZ, int meta, EntityLivingBase placer) {
        EnumFacing horizontal = placer.getHorizontalFacing().getOpposite();
        Attachment face = side == EnumFacing.UP ? Attachment.FLOOR
                : side == EnumFacing.DOWN ? Attachment.CEILING : Attachment.WALL;
        if (face == Attachment.WALL) horizontal = side;
        EnumFacing.Axis connectedAxis = null;
        for (EnumFacing direction : EnumFacing.values()) {
            BlockPos neighborPos = pos.offset(direction);
            if (!world.isBlockLoaded(neighborPos)) continue;
            IBlockState neighbor = world.getBlockState(neighborPos);
            boolean canConnect = neighbor.getBlock() instanceof FluidPipeBlock pipe
                    && pipe.allowsAxis(neighbor, direction.getAxis())
                    || neighbor.getBlock() instanceof FluidValveBlock valve
                    && valve.canConnectTowards(world, neighborPos, direction.getOpposite())
                    || neighbor.getBlock() instanceof MechanicalPumpBlock
                    && neighbor.getValue(MechanicalPumpBlock.FACING).getAxis() == direction.getAxis();
            TileEntity neighborTile = world.getTileEntity(neighborPos);
            canConnect |= neighborTile != null && neighborTile.hasCapability(
                    net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                    direction.getOpposite());
            if (!canConnect) continue;
            if (connectedAxis != null && connectedAxis != direction.getAxis()) {
                connectedAxis = null;
                break;
            }
            connectedAxis = direction.getAxis();
        }
        if (connectedAxis == EnumFacing.Axis.Y) {
            if (face != Attachment.WALL) horizontal = horizontal.getOpposite();
            face = Attachment.WALL;
        } else if (connectedAxis != null) {
            if (face == Attachment.WALL) face = Attachment.FLOOR;
            if (horizontal.getAxis() != connectedAxis) {
                horizontal = EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, connectedAxis);
                Vec3d look = placer.getLookVec();
                double component = connectedAxis == EnumFacing.Axis.X ? look.x : look.z;
                if (component > 0) horizontal = horizontal.getOpposite();
            }
        }
        return getDefaultState().withProperty(ATTACHMENT, face).withProperty(FACING, horizontal);
    }

    public static IBlockState rotateAround(IBlockState state, EnumFacing.Axis around) {
        EnumFacing panel = getPanelSide(state);
        EnumFacing pipe = state.getValue(ATTACHMENT) == Attachment.WALL ? EnumFacing.UP : state.getValue(FACING);
        if (panel.getAxis() != around) panel = panel.rotateAround(around);
        if (pipe.getAxis() != around) pipe = pipe.rotateAround(around);
        Attachment face = panel == EnumFacing.UP ? Attachment.FLOOR
                : panel == EnumFacing.DOWN ? Attachment.CEILING : Attachment.WALL;
        EnumFacing facing = face == Attachment.WALL ? panel : pipe;
        return state.withProperty(ATTACHMENT, face).withProperty(FACING, facing);
    }

    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withProperty(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) { return new SmartFluidPipeTileEntity(); }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                     EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (side != getPanelSide(state)) return false;
        Vec3d center = filterCenter(state);
        double dx = hitX - center.x, dy = hitY - center.y, dz = hitZ - center.z;
        if (side.getAxis() == EnumFacing.Axis.X) dx = 0;
        if (side.getAxis() == EnumFacing.Axis.Y) dy = 0;
        if (side.getAxis() == EnumFacing.Axis.Z) dz = 0;
        if (dx * dx + dy * dy + dz * dz > 0.09D) return false;
        ItemStack held = player.getHeldItem(hand);
        if (!held.isEmpty() && held.getItem() == AllItems.WRENCH) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof SmartFluidPipeTileEntity pipe) {
            ItemStack previous = pipe.getFilterItem();
            ItemStack next = held.copy();
            pipe.setFilterItem(next);
            if (!player.capabilities.isCreativeMode && next.getItem() instanceof ListFilterItem) {
                held.shrink(1);
                if (held.isEmpty()) player.setHeldItem(hand, ItemStack.EMPTY);
            }
            if (previous.getItem() instanceof ListFilterItem
                    && (!player.capabilities.isCreativeMode || !hasFilter(player, previous))) {
                ItemHandlerHelper.giveItemToPlayer(player, previous);
            }
            world.playSound(null, pos, SoundEvents.ENTITY_ITEMFRAME_ADD_ITEM, SoundCategory.BLOCKS, 0.25F, 0.1F);
        }
        return true;
    }

    private static boolean hasFilter(EntityPlayer player, ItemStack filter) {
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack candidate = player.inventory.getStackInSlot(slot);
            if (ItemStack.areItemsEqual(candidate, filter)
                    && ItemStack.areItemStackTagsEqual(candidate, filter)) return true;
        }
        return false;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof SmartFluidPipeTileEntity pipe) {
            ItemStack filter = pipe.getFilterItem();
            if (filter.getItem() instanceof ListFilterItem) {
                if (world instanceof ContraptionWorld local) {
                    local.spawnCapturedDrop(pos, filter);
                } else if (world instanceof FakeWorld fake
                        && fake.getMobileChunk().getEntityMovingWorld() instanceof
                        com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity entity) {
                    entity.spawnDrop(pos, java.util.Collections.singletonList(filter));
                } else if (!(world instanceof FakeWorld)) {
                    InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), filter);
                }
            }
        }
        super.breakBlock(world, pos, state);
    }
}
