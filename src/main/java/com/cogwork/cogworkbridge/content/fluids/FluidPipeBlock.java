package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.IProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.content.decoration.ConnectedTextureData;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Base pipe node. Connection and flow state live outside metadata. */
public class FluidPipeBlock extends BlockContainer {
    private static final AxisAlignedBB CORE = new AxisAlignedBB(0.25D, 0.25D, 0.25D, 0.75D, 0.75D, 0.75D);
    public static final Map<EnumFacing, IUnlistedProperty<Boolean>> CONNECTIONS = createConnectionProperties();
    public static final Map<EnumFacing, IUnlistedProperty<Integer>> ATTACHMENTS = createAttachmentProperties();
    public static final int NO_ATTACHMENT = 0, CONNECTION = 1, RIM_CONNECTOR = 2,
            RIM_WITH_CONNECTOR = 3, DRAIN_WITH_CONNECTOR = 4, RIM_ONLY = 5, DRAIN_ONLY = 6;

    private static Map<EnumFacing, IUnlistedProperty<Boolean>> createConnectionProperties() {
        Map<EnumFacing, IUnlistedProperty<Boolean>> properties = new EnumMap<>(EnumFacing.class);
        for (EnumFacing side : EnumFacing.values()) properties.put(side, new ConnectionProperty(side));
        return properties;
    }

    private static final class ConnectionProperty implements IUnlistedProperty<Boolean> {
        private final String name;
        private ConnectionProperty(EnumFacing side) { name = "connection_" + side.getName(); }
        @Override public String getName() { return name; }
        @Override public boolean isValid(Boolean value) { return value != null; }
        @Override public Class<Boolean> getType() { return Boolean.class; }
        @Override public String valueToString(Boolean value) { return String.valueOf(value); }
    }

    private static Map<EnumFacing, IUnlistedProperty<Integer>> createAttachmentProperties() {
        Map<EnumFacing, IUnlistedProperty<Integer>> properties = new EnumMap<>(EnumFacing.class);
        for (EnumFacing side : EnumFacing.values()) {
            properties.put(side, new IUnlistedProperty<Integer>() {
                @Override public String getName() { return "attachment_" + side.getName(); }
                @Override public boolean isValid(Integer value) { return value != null && value >= 0 && value <= 6; }
                @Override public Class<Integer> getType() { return Integer.class; }
                @Override public String valueToString(Integer value) { return String.valueOf(value); }
            });
        }
        return properties;
    }

    protected static IUnlistedProperty<?>[] visualProperties() {
        java.util.List<IUnlistedProperty<?>> properties = new java.util.ArrayList<>(CONNECTIONS.values());
        properties.addAll(ATTACHMENTS.values());
        properties.add(ConnectedTextureData.CONTEXT);
        return properties.toArray(new IUnlistedProperty<?>[0]);
    }

    public FluidPipeBlock() {
        super(Material.IRON);
        setHardness(2.0F);
        setResistance(6.0F);
        setSoundType(SoundType.METAL);
        setLightOpacity(0);
    }

    @Override
    protected net.minecraft.block.state.BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[0], visualProperties());
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState extended)) return state;
        IExtendedBlockState result = extended;
        TileEntity tile = world.getTileEntity(pos);
        for (EnumFacing side : EnumFacing.values()) {
            boolean connected = this instanceof SmartFluidPipeBlock
                    ? SmartFluidPipeBlock.getPipeAxis(state) == side.getAxis()
                    : tile instanceof FluidPipeTileEntity pipe && pipe.hasConnection(side);
            result = result.withProperty(CONNECTIONS.get(side), connected)
                    .withProperty(ATTACHMENTS.get(side), connected ? attachment(world, pos, state, tile, side) : NO_ATTACHMENT);
        }
        return this instanceof EncasedFluidPipeBlock
                ? result.withProperty(ConnectedTextureData.CONTEXT, ConnectedTextureData.capture(world, pos, state)) : result;
    }

    private int attachment(IBlockAccess world, BlockPos pos, IBlockState state, TileEntity tile, EnumFacing side) {
        BlockPos neighborPos = pos.offset(side);
        IBlockState neighbor = world.getBlockState(neighborPos);
        net.minecraft.block.Block block = neighbor.getBlock();
        boolean straightPipe = this instanceof SmartFluidPipeBlock;
        if (straightPipe) {
            if (block.getClass() == FluidPipeBlock.class) return NO_ATTACHMENT;
            if (block instanceof SmartFluidPipeBlock && SmartFluidPipeBlock.getPipeAxis(neighbor) == side.getAxis())
                return NO_ATTACHMENT;
            if (block instanceof MechanicalPumpBlock
                    && neighbor.getValue(MechanicalPumpBlock.FACING).getAxis() == side.getAxis()) return NO_ATTACHMENT;
            if (block instanceof FluidValveBlock && FluidValveBlock.getPipeAxis(neighbor) == side.getAxis())
                return NO_ATTACHMENT;
        } else if (block.getClass() == FluidPipeBlock.class) {
            return tile instanceof FluidPipeTileEntity pipe && pipe.getStraightAxis() == side.getAxis()
                    ? CONNECTION : RIM_CONNECTOR;
        } else if (block instanceof SmartFluidPipeBlock || block instanceof FluidValveBlock
                || block instanceof MechanicalPumpBlock) {
            return RIM_CONNECTOR;
        }

        TileEntity other = world.getTileEntity(neighborPos);
        boolean drain = !(block instanceof HosePulleyBlock) && other != null && other.hasCapability(
                net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite());
        if (drain) return straightPipe ? DRAIN_ONLY : DRAIN_WITH_CONNECTOR;
        return straightPipe ? RIM_ONLY : RIM_WITH_CONNECTOR;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new FluidPipeTileEntity();
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }

    @Override
    public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        AxisAlignedBB bounds = CORE;
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof FluidPipeTileEntity pipe) {
            for (EnumFacing side : EnumFacing.values()) {
                if (pipe.hasConnection(side)) bounds = bounds.union(armShape(side));
            }
        }
        return bounds;
    }

    @Override
    @Nullable
    public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos, Vec3d start, Vec3d end) {
        // Full-cube encased pipes and the smart pipe define their own outlines.
        if (!(getClass() == FluidPipeBlock.class || this instanceof GlassFluidPipeBlock)) {
            return super.collisionRayTrace(state, world, pos, start, end);
        }
        RayTraceResult closest = rayTrace(pos, start, end, CORE);
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof FluidPipeTileEntity pipe) {
            for (EnumFacing side : EnumFacing.values()) {
                if (!pipe.hasConnection(side)) continue;
                RayTraceResult hit = rayTrace(pos, start, end, armShape(side));
                if (hit != null && (closest == null
                        || hit.hitVec.squareDistanceTo(start) < closest.hitVec.squareDistanceTo(start))) {
                    closest = hit;
                }
            }
        }
        return closest;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        addCollisionBox(pos, CORE, query, boxes);
        TileEntity tile = world.getTileEntity(pos);
        for (EnumFacing side : EnumFacing.values()) {
            if (tile instanceof FluidPipeTileEntity pipe && pipe.hasConnection(side)) {
                addCollisionBox(pos, armShape(side), query, boxes);
            }
        }
    }

    private static AxisAlignedBB armShape(EnumFacing side) {
        return switch (side) {
            case DOWN -> new AxisAlignedBB(0.375D, 0, 0.375D, 0.625D, 0.5D, 0.625D);
            case UP -> new AxisAlignedBB(0.375D, 0.5D, 0.375D, 0.625D, 1, 0.625D);
            case NORTH -> new AxisAlignedBB(0.375D, 0.375D, 0, 0.625D, 0.625D, 0.5D);
            case SOUTH -> new AxisAlignedBB(0.375D, 0.375D, 0.5D, 0.625D, 0.625D, 1);
            case WEST -> new AxisAlignedBB(0, 0.375D, 0.375D, 0.5D, 0.625D, 0.625D);
            case EAST -> new AxisAlignedBB(0.5D, 0.375D, 0.375D, 1, 0.625D, 0.625D);
        };
    }

    private static void addCollisionBox(BlockPos pos, AxisAlignedBB local, AxisAlignedBB query,
                                        List<AxisAlignedBB> boxes) {
        AxisAlignedBB worldBox = local.offset(pos);
        if (query.intersects(worldBox)) boxes.add(worldBox);
    }

    public boolean canConnectTowards(World world, BlockPos pos, EnumFacing side) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof FluidPipeTileEntity pipe && pipe.hasConnection(side)
                && allowsAxis(world.getBlockState(pos), side.getAxis());
    }

    public boolean allowsAxis(IBlockState state, EnumFacing.Axis axis) {
        if (this instanceof SmartFluidPipeBlock) return SmartFluidPipeBlock.getPipeAxis(state) == axis;
        return !(this instanceof GlassFluidPipeBlock) || state.getValue(GlassFluidPipeBlock.AXIS) == axis;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!(getClass() == FluidPipeBlock.class || this instanceof GlassFluidPipeBlock)
                || player.getHeldItem(hand).isEmpty()
                || player.getHeldItem(hand).getItem() != Item.getItemFromBlock(AllBlocks.COPPER_CASING)) return false;
        return world.isRemote || replace(world, pos, AllBlocks.ENCASED_FLUID_PIPE.getDefaultState());
    }

    public static boolean replace(World world, BlockPos pos, IBlockState replacement) {
        TileEntity previous = world.getTileEntity(pos);
        NBTTagCompound saved = previous instanceof FluidPipeTileEntity
                ? previous.writeToNBT(new NBTTagCompound()) : null;
        if (!world.setBlockState(pos, replacement, 3)) return false;
        TileEntity next = world.getTileEntity(pos);
        if (next instanceof FluidPipeTileEntity pipe && saved != null) {
            pipe.readFromNBT(saved);
            if (replacement.getBlock() instanceof GlassFluidPipeBlock) {
                pipe.setPreferredAxis(replacement.getValue(GlassFluidPipeBlock.AXIS));
            }
            pipe.markDirty();
            world.notifyBlockUpdate(pos, replacement, replacement, 3);
        }
        FluidTransferManager.markDirty(world);
        return true;
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        FluidTransferManager.registerPipe(world, pos);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, net.minecraft.block.Block block,
                                BlockPos neighborPos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof FluidPipeTileEntity pipe) pipe.markConnectionsDirty();
        FluidTransferManager.markDirty(world);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
                                ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof FluidPipeTileEntity pipe) {
            net.minecraft.util.math.Vec3d look = placer.getLookVec();
            pipe.setPreferredAxis(EnumFacing.getFacingFromVector(
                    (float) look.x, (float) look.y, (float) look.z).getAxis());
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        FluidTransferManager.unregisterPipe(world, pos);
        super.breakBlock(world, pos, state);
    }
}
