package com.cogwork.cogworkbridge.content.logistics.belt;

import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Edits a belt chain without destroying its segments or their transported items. */
public final class BeltSlicer {
    private BeltSlicer() { }

    public static EnumActionResult extendOrJoin(World world, BlockPos pos, EntityPlayer player, EnumHand hand,
                                                 EnumFacing face, float x, float y, float z) {
        if (!(world.getTileEntity(pos) instanceof BeltTileEntity end) || !isOuterHit(end, x, y, z))
            return EnumActionResult.FAIL;
        if (world.isRemote) return EnumActionResult.SUCCESS;
        List<BeltTileEntity> chain = collect(world, end, player, hand, face);
        if (chain == null || !end.hasPulley()) return EnumActionResult.FAIL;
        BlockPos step = end.step();
        boolean start = end.index() == 0;
        BlockPos next = shift(pos, step, start ? -1 : 1);
        if (!canEdit(world, player, hand, face, next)) return EnumActionResult.FAIL;
        TileEntity neighbor = world.getTileEntity(next);
        if (neighbor instanceof BeltTileEntity other)
            return join(world, player, hand, face, end, chain, other, start);
        if (end.length() + 1 > limit(step)) return EnumActionResult.FAIL;
        IBlockState replaced = world.getBlockState(next);
        if (!replaced.getBlock().isReplaceable(world, next)
                || !world.mayPlace(AllBlocks.BELT, next, false, face, player)) return EnumActionResult.FAIL;
        NBTTagCompound oldTile = neighbor == null ? null : neighbor.writeToNBT(new NBTTagCompound());
        IBlockState beltState = AllBlocks.BELT.getDefaultState().withProperty(KineticBlock.AXIS, end.shaftAxis());
        if (!world.setBlockState(next, beltState, 2)) return EnumActionResult.FAIL;
        if (!(world.getTileEntity(next) instanceof BeltTileEntity added)) {
            BeltBlock.removeSegment(world, next);
            world.setBlockState(next, replaced, 2);
            TileEntity restored = world.getTileEntity(next);
            if (restored != null && oldTile != null) restored.readFromNBT(oldTile);
            return EnumActionResult.FAIL;
        }
        if (start) chain.add(0, added); else chain.add(added);
        end.setPulley(false);
        added.setPulley(true);
        added.applyColor(end.color());
        rebuild(chain, step);
        finish(world, chain, pos);
        return EnumActionResult.SUCCESS;
    }

    private static EnumActionResult join(World world, EntityPlayer player, EnumHand hand, EnumFacing face,
                                          BeltTileEntity end, List<BeltTileEntity> first,
                                          BeltTileEntity other, boolean start) {
        if (end.sameChain(other) || !other.hasPulley() || other.shaftAxis() != end.shaftAxis()) return EnumActionResult.FAIL;
        BlockPos step = end.step(), otherStep = other.step();
        if (!step.equals(otherStep) && !step.equals(shift(BlockPos.ORIGIN, otherStep, -1))) return EnumActionResult.FAIL;
        if (other.index() != 0 && other.index() != other.length() - 1) return EnumActionResult.FAIL;
        BlockPos outer = shift(other.getPos(), otherStep, other.index() == 0 ? -1 : 1);
        if (!outer.equals(end.getPos())) return EnumActionResult.FAIL;
        List<BeltTileEntity> second = collect(world, other, player, hand, face);
        if (second == null || first.size() + second.size() > limit(step) || conflicting(first, second))
            return EnumActionResult.FAIL;
        if (!otherStep.equals(step)) {
            java.util.Collections.reverse(second);
        }
        List<BeltTileEntity> merged = new ArrayList<>(first.size() + second.size());
        if (start) { merged.addAll(second); merged.addAll(first); }
        else { merged.addAll(first); merged.addAll(second); }
        for (int i = 1; i < merged.size(); i++)
            if (!shift(merged.get(i - 1).getPos(), step, 1).equals(merged.get(i).getPos())) return EnumActionResult.FAIL;
        if (!otherStep.equals(step)) for (BeltTileEntity tile : second) tile.reverseTransportCoordinates();
        end.setPulley(false);
        other.setPulley(false);
        rebuild(merged, step);
        for (BeltTileEntity tile : merged) tile.setSegmentColor(end.color());
        if (!player.capabilities.isCreativeMode) {
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(AllBlocks.SHAFT, 2));
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(AllItems.BELT_CONNECTOR));
        }
        finish(world, merged, end.getPos());
        return EnumActionResult.SUCCESS;
    }

    public static EnumActionResult shortenOrSplit(World world, BlockPos pos, EntityPlayer player, EnumHand hand,
                                                    EnumFacing face, float x, float y, float z) {
        if (!(world.getTileEntity(pos) instanceof BeltTileEntity belt)) return EnumActionResult.FAIL;
        if (world.isRemote) return EnumActionResult.SUCCESS;
        List<BeltTileEntity> chain = collect(world, belt, player, hand, face);
        if (chain == null) return EnumActionResult.FAIL;
        if (isOuterHit(belt, x, y, z)) return shorten(world, player, chain, belt);
        int cut = belt.index() + (projection(belt.step(), x, y, z) > 0 ? 0 : -1);
        int leftLength = cut + 1;
        if (leftLength < 2 || chain.size() - leftLength < 2) return EnumActionResult.FAIL;
        Item shaft = Item.getItemFromBlock(AllBlocks.SHAFT);
        if (!player.capabilities.isCreativeMode && countItems(player, shaft) < 2) return EnumActionResult.FAIL;
        chain.get(cut).setPulley(true);
        chain.get(cut + 1).setPulley(true);
        List<BeltTileEntity> left = new ArrayList<>(chain.subList(0, leftLength));
        List<BeltTileEntity> right = new ArrayList<>(chain.subList(leftLength, chain.size()));
        rebuild(left, belt.step());
        rebuild(right, belt.step());
        if (!player.capabilities.isCreativeMode) consumeItems(player, shaft, 2);
        finish(world, chain, pos);
        return EnumActionResult.SUCCESS;
    }

    private static EnumActionResult shorten(World world, EntityPlayer player, List<BeltTileEntity> chain,
                                              BeltTileEntity end) {
        if (chain.size() <= 2 || !end.hasPulley()) return EnumActionResult.FAIL;
        boolean start = end.index() == 0;
        BeltTileEntity replacement = chain.get(start ? 1 : chain.size() - 2);
        boolean spareShaft = replacement.hasPulley();
        BlockPos removed = end.getPos(), step = end.step();
        NBTTagCompound saved = end.writeToNBT(new NBTTagCompound());
        if (!BeltBlock.removeSegment(world, removed)) return EnumActionResult.FAIL;
        if (start) chain.remove(0); else chain.remove(chain.size() - 1);
        replacement.setPulley(true);
        rebuild(chain, step);
        if (saved.hasKey("Carried", 10)) ItemTransfer.drop(world, removed, new ItemStack(saved.getCompoundTag("Carried")));
        if (saved.hasKey("ProcessingOutput", 10)) ItemTransfer.drop(world, removed, new ItemStack(saved.getCompoundTag("ProcessingOutput")));
        NBTTagList outputs = saved.getTagList("ProcessingOutputs", 10);
        for (int i = 0; i < outputs.tagCount(); i++) ItemTransfer.drop(world, removed, new ItemStack(outputs.getCompoundTagAt(i)));
        if (spareShaft && !player.capabilities.isCreativeMode)
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(AllBlocks.SHAFT));
        finish(world, chain, removed);
        return EnumActionResult.SUCCESS;
    }

    @Nullable private static List<BeltTileEntity> collect(World world, BeltTileEntity member, EntityPlayer player,
                                                          EnumHand hand, EnumFacing face) {
        if (member.length() < 2 || member.length() > limit(member.step()) || member.step().equals(BlockPos.ORIGIN)) return null;
        List<BeltTileEntity> result = new ArrayList<>();
        BlockPos origin = member.origin();
        for (int i = 0; i < member.length(); i++) {
            BlockPos p = shift(origin, member.step(), i);
            if (!canEdit(world, player, hand, face, p) || !(world.getTileEntity(p) instanceof BeltTileEntity tile)
                    || !member.sameChain(tile) || tile.index() != i || tile.shaftAxis() != member.shaftAxis()) return null;
            result.add(tile);
        }
        return result;
    }

    private static boolean canEdit(World world, EntityPlayer player, EnumHand hand, EnumFacing face, BlockPos pos) {
        return world.isBlockLoaded(pos) && player.canPlayerEdit(pos, face, player.getHeldItem(hand))
                && world.isBlockModifiable(player, pos);
    }

    private static boolean conflicting(List<BeltTileEntity> a, List<BeltTileEntity> b) {
        float left = 0, right = 0;
        for (BeltTileEntity tile : a) if (tile.getSpeed() != 0) { left = tile.getSpeed(); break; }
        for (BeltTileEntity tile : b) if (tile.getSpeed() != 0) { right = tile.getSpeed(); break; }
        return left != 0 && right != 0 && Math.abs(left - right) > 0.0001F;
    }

    private static void rebuild(List<BeltTileEntity> chain, BlockPos step) {
        BlockPos origin = chain.get(0).getPos();
        for (int i = 0; i < chain.size(); i++) {
            BeltTileEntity tile = chain.get(i);
            tile.initialize(origin, step, i, chain.size(), tile.hasPulley());
        }
    }

    private static void finish(World world, List<BeltTileEntity> chain, BlockPos soundPos) {
        for (BeltTileEntity tile : chain) world.notifyNeighborsOfStateChange(tile.getPos(), AllBlocks.BELT, true);
        KineticNetworkManager.discard(world);
        world.playSound(null, soundPos, SoundEvents.BLOCK_CLOTH_PLACE, SoundCategory.BLOCKS, 0.7F, 1F);
    }

    private static boolean isOuterHit(BeltTileEntity belt, float x, float y, float z) {
        float projection = projection(belt.step(), x, y, z);
        return belt.index() == 0 && projection < 0 || belt.index() == belt.length() - 1 && projection > 0;
    }
    private static float projection(BlockPos step, float x, float y, float z) {
        return (x - 0.5F) * step.getX() + (y - 0.5F) * step.getY() + (z - 0.5F) * step.getZ();
    }
    private static int limit(BlockPos step) {
        int components = Math.abs(step.getX()) + Math.abs(step.getY()) + Math.abs(step.getZ());
        return components > 1 ? 15 : 20;
    }
    private static BlockPos shift(BlockPos pos, BlockPos step, int amount) {
        return pos.add(step.getX() * amount, step.getY() * amount, step.getZ() * amount);
    }
    private static int countItems(EntityPlayer player, Item item) {
        int count = 0;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }
    private static void consumeItems(EntityPlayer player, Item item, int amount) {
        for (int i = 0; i < player.inventory.getSizeInventory() && amount > 0; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.isEmpty() || stack.getItem() != item) continue;
            int take = Math.min(amount, stack.getCount());
            stack.shrink(take);
            amount -= take;
        }
        player.inventory.markDirty();
    }
}
