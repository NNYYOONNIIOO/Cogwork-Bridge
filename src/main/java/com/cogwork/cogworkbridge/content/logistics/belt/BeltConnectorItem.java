package com.cogwork.cogworkbridge.content.logistics.belt;

import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class BeltConnectorItem extends Item {
    private static final String FIRST = "CogworkBridgeBeltFirst";
    private static final int MAX_LENGTH = 20;
    public BeltConnectorItem() { setCreativeTab(ModCreativeTab.INSTANCE); }

    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing face, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking()) {
            if (!world.isRemote && stack.hasTagCompound()) {
                stack.getTagCompound().removeTag(FIRST);
                clearEmptyTag(stack);
            }
            return EnumActionResult.SUCCESS;
        }
        if (!validShaft(world, pos)) return EnumActionResult.PASS;
        if (world.isRemote) return EnumActionResult.SUCCESS;
        NBTTagCompound root = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        if (root.hasKey(FIRST, 10)) {
            NBTTagCompound first = root.getCompoundTag(FIRST);
            BlockPos start = new BlockPos(first.getInteger("X"), first.getInteger("Y"), first.getInteger("Z"));
            if (first.getInteger("Dimension") == world.provider.getDimension() && validShaft(world, start)) {
                if (start.equals(pos)) {
                    root.removeTag(FIRST);
                    setTag(stack, root);
                    return EnumActionResult.SUCCESS;
                }
                if (!create(world, player, start, pos)) return EnumActionResult.FAIL;
                com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAction(world, pos, player, "belt");
                root.removeTag(FIRST);
                setTag(stack, root);
                if (!player.capabilities.isCreativeMode) stack.shrink(1);
                return EnumActionResult.SUCCESS;
            }
            root.removeTag(FIRST);
            setTag(stack, root);
        }
        NBTTagCompound selection = new NBTTagCompound();
        selection.setInteger("X", pos.getX());
        selection.setInteger("Y", pos.getY());
        selection.setInteger("Z", pos.getZ());
        selection.setInteger("Dimension", world.provider.getDimension());
        root.setTag(FIRST, selection);
        stack.setTagCompound(root);
        return EnumActionResult.SUCCESS;
    }

    private static void setTag(ItemStack stack, NBTTagCompound tag) {
        stack.setTagCompound(tag.isEmpty() ? null : tag);
    }

    private static void clearEmptyTag(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().isEmpty()) stack.setTagCompound(null);
    }

    private static boolean validShaft(World world, BlockPos pos) {
        return world.isBlockLoaded(pos) && world.getBlockState(pos).getBlock() instanceof ShaftBlock;
    }
    public static boolean canConnect(World world, EntityPlayer player, BlockPos start, BlockPos end) {
        if (!validShaft(world, start) || !validShaft(world, end)) return false;
        IBlockState first = world.getBlockState(start);
        IBlockState last = world.getBlockState(end);
        EnumFacing.Axis axis = first.getValue(KineticBlock.AXIS);
        if (last.getValue(KineticBlock.AXIS) != axis) return false;
        BlockPos delta = end.subtract(start);
        int dx = delta.getX(), dy = delta.getY(), dz = delta.getZ();
        int alongAxis = axis == EnumFacing.Axis.X ? dx : axis == EnumFacing.Axis.Y ? dy : dz;
        int nonzero = (dx == 0 ? 0 : 1) + (dy == 0 ? 0 : 1) + (dz == 0 ? 0 : 1);
        int longest = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        if (alongAxis != 0 || nonzero < 1 || nonzero > 2 || longest < 1
                || start.distanceSq(end) >= MAX_LENGTH * MAX_LENGTH
                || axis == EnumFacing.Axis.Y && nonzero != 1) return false;
        if (nonzero == 2 && ((dx != 0 && Math.abs(dx) != longest)
                || (dy != 0 && Math.abs(dy) != longest) || (dz != 0 && Math.abs(dz) != longest))) return false;
        if (world.getTileEntity(start) instanceof KineticTileEntity a && world.getTileEntity(end) instanceof KineticTileEntity b
                && a.getSpeed() != 0 && b.getSpeed() != 0 && Math.signum(a.getSpeed()) != Math.signum(b.getSpeed())) return false;
        BlockPos step = new BlockPos(Integer.signum(dx), Integer.signum(dy), Integer.signum(dz));
        for (int i = 0; i <= longest; i++) {
            BlockPos current = start.add(step.getX() * i, step.getY() * i, step.getZ() * i);
            if (!world.isBlockLoaded(current) || !player.canPlayerEdit(current, EnumFacing.UP, player.getHeldItemMainhand())
                    || !world.isBlockModifiable(player, current)) return false;
            IBlockState state = world.getBlockState(current);
            boolean pulley = state.getBlock() instanceof ShaftBlock && state.getValue(KineticBlock.AXIS) == axis;
            if (!pulley && !state.getBlock().isReplaceable(world, current)) return false;
        }
        return true;
    }
    private static boolean create(World world, EntityPlayer player, BlockPos start, BlockPos end) {
        if (!canConnect(world, player, start, end)) return false;
        IBlockState first = world.getBlockState(start);
        EnumFacing.Axis axis = first.getValue(KineticBlock.AXIS);
        BlockPos delta = end.subtract(start);
        int dx = delta.getX(), dy = delta.getY(), dz = delta.getZ();
        int longest = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        BlockPos step = new BlockPos(Integer.signum(dx), Integer.signum(dy), Integer.signum(dz));
        List<Original> original = new ArrayList<>();
        for (int i = 0; i <= longest; i++) {
            BlockPos current = start.add(step.getX() * i, step.getY() * i, step.getZ() * i);
            IBlockState state = world.getBlockState(current);
            boolean pulley = state.getBlock() instanceof ShaftBlock && state.getValue(KineticBlock.AXIS) == axis;
            TileEntity tile = world.getTileEntity(current);
            original.add(new Original(current, state, tile == null ? null : tile.writeToNBT(new NBTTagCompound()), pulley));
        }
        int placed = 0;
        boolean success = false;
        try {
            for (int i = 0; i < original.size(); i++) {
                Original old = original.get(i);
                if (!world.setBlockState(old.pos(), AllBlocks.BELT.getDefaultState().withProperty(KineticBlock.AXIS, axis), 2)) return false;
                placed++;
                if (!(world.getTileEntity(old.pos()) instanceof BeltTileEntity belt)) return false;
                belt.initialize(start, step, i, original.size(), old.pulley());
            }
            success = true;
        } finally {
            if (!success) {
                for (int i = placed - 1; i >= 0; i--) {
                    Original old = original.get(i);
                    world.removeTileEntity(old.pos());
                    world.setBlockState(old.pos(), old.state(), 2);
                    TileEntity restored = world.getTileEntity(old.pos());
                    if (restored != null && old.tile() != null) restored.readFromNBT(old.tile());
                }
            }
        }
        for (Original old : original) world.notifyNeighborsOfStateChange(old.pos(), AllBlocks.BELT, true);
        KineticNetworkManager.discard(world);
        return true;
    }
    private record Original(BlockPos pos, IBlockState state, NBTTagCompound tile, boolean pulley) { }
}
