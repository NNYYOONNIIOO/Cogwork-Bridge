package com.cogwork.cogworkbridge.content.logistics.belt;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import net.minecraft.block.material.Material;
import net.minecraft.block.SoundType;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.init.Items;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

public final class BeltBlock extends KineticBlock {
    public static final IUnlistedProperty<String> MODEL = new IUnlistedProperty<String>() {
        @Override public String getName() { return "belt_model"; }
        @Override public boolean isValid(String value) { return value != null; }
        @Override public Class<String> getType() { return String.class; }
        @Override public String valueToString(String value) { return value; }
    };
    private static final Set<World> DISASSEMBLING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0, 0, 0, 1, 13 / 16.0D, 1);
    private static final String[] DYE_ORES = {"dyeWhite", "dyeOrange", "dyeMagenta", "dyeLightBlue",
            "dyeYellow", "dyeLime", "dyePink", "dyeGray", "dyeLightGray", "dyeCyan",
            "dyePurple", "dyeBlue", "dyeBrown", "dyeGreen", "dyeRed", "dyeBlack"};
    private static final Map<String, List<AxisAlignedBB>> SHAPE_CACHE = new ConcurrentHashMap<>();

    public BeltBlock() {
        super(Material.CLOTH);
        setHardness(0.8F);
        setResistance(2.0F);
        setLightOpacity(0);
        setDefaultState(getDefaultState().withProperty(AXIS, EnumFacing.Axis.X));
    }
    @Override public boolean hasAxis() { return true; }
    @Override protected net.minecraft.block.state.BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{AXIS}, new IUnlistedProperty<?>[]{MODEL});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState extended)) return state;
        TileEntity tile = world.getTileEntity(pos);
        return extended.withProperty(MODEL, tile instanceof BeltTileEntity belt ? belt.modelKey() : "middle_flat_north");
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new BeltTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        List<AxisAlignedBB> shapes = shapes(state, world, pos);
        AxisAlignedBB result = shapes.get(0);
        for (int i = 1; i < shapes.size(); i++) result = result.union(shapes.get(i));
        return result;
    }

    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return getBoundingBox(state, world, pos);
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        for (AxisAlignedBB local : shapes(state, world, pos)) {
            double minY = Math.max(0, local.minY), maxY = Math.min(19 / 16D, local.maxY);
            if (minY >= maxY) continue;
            AxisAlignedBB clipped = new AxisAlignedBB(local.minX, minY, local.minZ,
                    local.maxX, maxY, local.maxZ).offset(pos);
            if (query.intersects(clipped)) boxes.add(clipped);
        }
    }

    List<AxisAlignedBB> shapes(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(world.getTileEntity(pos) instanceof BeltTileEntity belt)) return Collections.singletonList(SHAPE);
        BlockPos step = belt.step();
        if (step.equals(BlockPos.ORIGIN)) return Collections.singletonList(SHAPE);
        int part = belt.length() < 2 ? 0 : belt.index() == 0 ? -1 : belt.index() == belt.length() - 1 ? 1 : 0;
        EnumFacing.Axis axis = getAxis(state);
        String key = axis.getName() + ':' + step.getX() + ':' + step.getY() + ':' + step.getZ() + ':' + part;
        return SHAPE_CACHE.computeIfAbsent(key, ignored -> createShapes(axis, step, part));
    }

    // Shapes are authored toward south, then rotated to the actual belt direction.
    private static List<AxisAlignedBB> createShapes(EnumFacing.Axis axis, BlockPos step, int part) {
        boolean sideways = axis == EnumFacing.Axis.Y;
        boolean vertical = !sideways && step.getY() != 0 && step.getX() == 0 && step.getZ() == 0;
        boolean sloped = !sideways && step.getY() != 0 && !vertical;
        List<AxisAlignedBB> shapes = new ArrayList<>(20);
        double bodyMin = part == -1 ? 0.5D : 0D;
        double bodyMax = part == 1 ? 0.5D : 1D;
        if (sloped) {
            boolean ascending = step.getY() > 0;
            for (int i = 0; i < 16; i++) {
                double minZ = (15 - i) / 16D, maxZ = (16 - i) / 16D;
                if (minZ < bodyMin || maxZ > bodyMax) continue;
                double y = (ascending ? 10 - i : i - 5) / 16D;
                shapes.add(new AxisAlignedBB(1 / 16D, y, minZ, 15 / 16D, y + 11 / 16D, maxZ));
            }
        } else if (sideways) {
            shapes.add(new AxisAlignedBB(3 / 16D, 1 / 16D, bodyMin, 13 / 16D, 15 / 16D, bodyMax));
        } else {
            shapes.add(new AxisAlignedBB(1 / 16D, 3 / 16D, bodyMin, 15 / 16D, 13 / 16D, bodyMax));
        }
        if (part != 0) {
            double capMin = part == -1 ? 0D : 0.5D;
            double capMax = part == -1 ? 0.5D : 1D;
            double innerMin = Math.max(capMin, 1 / 16D), innerMax = Math.min(capMax, 15 / 16D);
            if (sideways) {
                shapes.add(new AxisAlignedBB(4 / 16D, 1 / 16D, capMin, 12 / 16D, 15 / 16D, capMax));
                shapes.add(new AxisAlignedBB(3 / 16D, 1 / 16D, innerMin, 13 / 16D, 15 / 16D, innerMax));
            } else {
                shapes.add(new AxisAlignedBB(1 / 16D, 4 / 16D, capMin, 15 / 16D, 12 / 16D, capMax));
                shapes.add(new AxisAlignedBB(1 / 16D, 3 / 16D, innerMin, 15 / 16D, 13 / 16D, innerMax));
            }
        }
        EnumFacing direction = step.getX() > 0 ? EnumFacing.EAST : step.getX() < 0 ? EnumFacing.WEST
                : step.getZ() > 0 ? EnumFacing.SOUTH : step.getZ() < 0 ? EnumFacing.NORTH
                : axis == EnumFacing.Axis.X ? (step.getY() > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH)
                : (step.getY() > 0 ? EnumFacing.EAST : EnumFacing.WEST);
        List<AxisAlignedBB> rotated = new ArrayList<>(shapes.size());
        for (AxisAlignedBB box : shapes) {
            if (vertical) box = verticalize(box, step.getY() > 0);
            rotated.add(rotateFromSouth(box, direction));
        }
        return Collections.unmodifiableList(rotated);
    }

    private static AxisAlignedBB verticalize(AxisAlignedBB box, boolean ascending) {
        return ascending
                ? new AxisAlignedBB(box.minX, box.minZ, 1 - box.maxY, box.maxX, box.maxZ, 1 - box.minY)
                : new AxisAlignedBB(box.minX, 1 - box.maxZ, box.minY, box.maxX, 1 - box.minZ, box.maxY);
    }

    private static AxisAlignedBB rotateFromSouth(AxisAlignedBB box, EnumFacing direction) {
        return switch (direction) {
            case NORTH -> new AxisAlignedBB(1 - box.maxX, box.minY, 1 - box.maxZ,
                    1 - box.minX, box.maxY, 1 - box.minZ);
            case EAST -> new AxisAlignedBB(box.minZ, box.minY, 1 - box.maxX,
                    box.maxZ, box.maxY, 1 - box.minX);
            case WEST -> new AxisAlignedBB(1 - box.maxZ, box.minY, box.minX,
                    1 - box.minZ, box.maxY, box.maxX);
            default -> box;
        };
    }
    @Override public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        return side.getAxis() == getAxis(state) && world.getTileEntity(pos) instanceof BeltTileEntity belt && belt.hasPulley();
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof BeltTileEntity belt)) return false;
        ItemStack held = player.getHeldItem(hand);
        if (!player.isSneaking() && held.getItem() == AllItems.BELT_CONNECTOR) {
            BeltSlicer.extendOrJoin(world, pos, player, hand, side, hitX, hitY, hitZ);
            return true;
        }
        EnumDyeColor dye = dyeColor(held);
        if (dye != null) {
            if (!world.isRemote && belt.applyColor(dye)) {
                world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_METAL_PLACE,
                        SoundCategory.BLOCKS, 0.8F, 1.0F);
            }
            return true;
        }
        Item andesiteItem = Item.getItemFromBlock(AllBlocks.ANDESITE_CASING);
        Item brassItem = Item.getItemFromBlock(AllBlocks.BRASS_CASING);
        if (held.getItem() == andesiteItem || held.getItem() == brassItem) {
            if (!world.isRemote) {
                belt.setCasing(held.getItem() == andesiteItem
                        ? BeltTileEntity.CasingType.ANDESITE : BeltTileEntity.CasingType.BRASS);
                SoundType sound = held.getItem() == andesiteItem
                        ? AllBlocks.ANDESITE_CASING.getSoundType(AllBlocks.ANDESITE_CASING.getDefaultState(), world, pos, player)
                        : AllBlocks.BRASS_CASING.getSoundType(AllBlocks.BRASS_CASING.getDefaultState(), world, pos, player);
                world.playSound(null, pos, sound.getPlaceSound(), SoundCategory.BLOCKS,
                        (sound.getVolume() + 1F) / 2F, sound.getPitch() * 0.8F);
            }
            return true;
        }
        if (held.getItem() == Item.getItemFromBlock(AllBlocks.SHAFT)
                && belt.index() > 0 && belt.index() + 1 < belt.length() && !belt.hasPulley()) {
            if (!world.isRemote) {
                belt.setPulley(true);
                if (!player.capabilities.isCreativeMode) held.shrink(1);
                KineticNetworkManager.discard(world);
            }
            return true;
        }
        if (held.isEmpty()) {
            if (belt.getCarried().isEmpty() && belt.getProcessingOutput().isEmpty()) return false;
            if (!world.isRemote) ItemHandlerHelper.giveItemToPlayer(player,
                    belt.getCarried().isEmpty() ? belt.takeProcessingOutput() : belt.takeCarried());
            return true;
        }
        ItemStack simulated = belt.insertItem(0, held, true);
        if (simulated.getCount() == held.getCount()) return false;
        if (!world.isRemote) player.setHeldItem(hand, belt.insertItem(0, held, false));
        return true;
    }

    @Nullable private static EnumDyeColor dyeColor(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() == Items.DYE) return EnumDyeColor.byDyeDamage(stack.getMetadata());
        for (int id : OreDictionary.getOreIDs(stack)) {
            String name = OreDictionary.getOreName(id);
            for (int i = 0; i < DYE_ORES.length; i++)
                if (DYE_ORES[i].equals(name)) return EnumDyeColor.byMetadata(i);
        }
        return null;
    }
    @Override public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (world.getTileEntity(pos) instanceof BeltTileEntity belt) {
            belt.trackPassenger(entity);
        }
    }

    /** Remove one segment without invoking whole-chain disassembly. */
    static boolean removeSegment(World world, BlockPos pos) {
        boolean ownsGuard = DISASSEMBLING.add(world);
        try { return world.setBlockToAir(pos); }
        finally { if (ownsGuard) DISASSEMBLING.remove(world); }
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof BeltTileEntity belt && DISASSEMBLING.add(world)) {
            try {
                BlockPos origin = belt.origin();
                BlockPos step = belt.step();
                int length = belt.length();
                for (int i = 0; i < length; i++) {
                    BlockPos member = origin.add(step.getX() * i, step.getY() * i, step.getZ() * i);
                    if (!world.isBlockLoaded(member) || !(world.getTileEntity(member) instanceof BeltTileEntity other)
                            || !other.sameChain(belt)) continue;
                    ItemTransfer.drop(world, member, other.takeAll());
                    for (ItemStack output : other.takeProcessingOutputs()) ItemTransfer.drop(world, member, output);
                    if (member.equals(pos)) {
                        if (other.hasPulley()) ItemTransfer.drop(world, member, new ItemStack(AllBlocks.SHAFT));
                    } else if (other.hasPulley()) {
                        world.setBlockState(member, AllBlocks.SHAFT.getDefaultState().withProperty(AXIS, getAxis(state)), 3);
                    } else world.setBlockToAir(member);
                }
                ItemTransfer.drop(world, pos, new ItemStack(AllItems.BELT_CONNECTOR));
                KineticNetworkManager.discard(world);
            } finally { DISASSEMBLING.remove(world); }
        }
        super.breakBlock(world, pos, state);
    }
}
