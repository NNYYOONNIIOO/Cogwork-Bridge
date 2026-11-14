package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import java.util.*;

/** Select a first track, then connect a second track or a newly placed endpoint. */
public final class TrackItem extends ItemBlock {
    private static final String SELECTION = "TrackFrom";
    public record Preview(BlockPos first, Map<BlockPos,IBlockState> placements, Map<BlockPos,IBlockState> pavement,
                          TrackCurve curve, boolean valid, int required, int requiredPavement, String error) { }
    public TrackItem(Block block) { super(block); }
    @Override public boolean hasEffect(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey(SELECTION,10) || super.hasEffect(stack);
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world,EntityPlayer player,EnumHand hand) {
        ItemStack held=player.getHeldItem(hand);
        if(player.isSneaking() && selected(held)) {
            if(!world.isRemote){clear(held); player.inventory.markDirty(); status(player,"cleared");}
            return new ActionResult<>(EnumActionResult.SUCCESS,held);
        }
        return super.onItemRightClick(world,player,hand);
    }
    @Override public EnumActionResult onItemUse(EntityPlayer player,World world,BlockPos clicked,EnumHand hand,
            EnumFacing face,float hitX,float hitY,float hitZ) {
        ItemStack stack=player.getHeldItem(hand);
        if(hand!=EnumHand.MAIN_HAND) return super.onItemUse(player,world,clicked,hand,face,hitX,hitY,hitZ);
        IBlockState hit=world.getBlockState(clicked);
        boolean isTrack=hit.getBlock() instanceof TrackBlock;
        if(!selected(stack)) {
            if(!isTrack) return super.onItemUse(player,world,clicked,hand,face,hitX,hitY,hitZ);
            if(!world.isRemote) {
                if(hit.getValue(TrackBlock.SHAPE).junction()) {status(player,"junction_start"); return EnumActionResult.SUCCESS;}
                NBTTagCompound root=stack.hasTagCompound()?stack.getTagCompound():new NBTTagCompound();
                NBTTagCompound selection=new NBTTagCompound();
                selection.setLong("Pos",clicked.toLong()); selection.setInteger("Dim",world.provider.getDimension());
                root.setTag(SELECTION,selection); stack.setTagCompound(root); player.inventory.markDirty(); status(player,"select_second");
            }
            return EnumActionResult.SUCCESS;
        }
        if(player.isSneaking()) {
            if(!world.isRemote){clear(stack);player.inventory.markDirty();status(player,"cleared");}
            return EnumActionResult.SUCCESS;
        }
        // The client sends the modifier with the second click; the vanilla packet cannot carry Ctrl.
        return EnumActionResult.SUCCESS;
    }
    public static boolean selected(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTagCompound() && stack.getTagCompound().hasKey(SELECTION, 10);
    }

    public EnumActionResult connect(EntityPlayer player, World world, BlockPos clicked, EnumFacing face,
            float hitX, float hitY, float hitZ, boolean maximise) {
        ItemStack stack = player.getHeldItemMainhand();
        if (world.isRemote || stack.getItem() != this || !selected(stack) || player.isSneaking()) return EnumActionResult.FAIL;
        Preview plan = preview(world, player, stack, clicked, face, hitX, hitY, hitZ, maximise);
        if (plan == null) return EnumActionResult.FAIL;
        if (!plan.valid()) {
            if ("original_missing".equals(plan.error())) { clear(stack); player.inventory.markDirty(); }
            status(player, plan.error()); return EnumActionResult.FAIL;
        }
        ItemStack pavement = player.getHeldItemOffhand().copy();
        Map<BlockPos, Snapshot> previous = new LinkedHashMap<>();
        plan.pavement().keySet().forEach(pos -> remember(world, previous, pos));
        plan.placements().keySet().forEach(pos -> remember(world, previous, pos));
        TrackCurve curve = plan.curve();
        if (curve != null) { remember(world, previous, curve.first); remember(world, previous, curve.second); }
        Map<BlockPos, IBlockState> changes = new LinkedHashMap<>(plan.pavement());
        changes.putAll(plan.placements());
        for (Map.Entry<BlockPos, IBlockState> entry : changes.entrySet()) {
            if (!world.setBlockState(entry.getKey(), entry.getValue(), 3)) {
                restore(world, previous); status(player, "blocked"); return EnumActionResult.FAIL;
            }
        }
        if (curve != null) {
            if (!(world.getTileEntity(curve.first) instanceof TrackTileEntity a)
                    || !(world.getTileEntity(curve.second) instanceof TrackTileEntity b)) {
                restore(world, previous); status(player, "unavailable"); return EnumActionResult.FAIL;
            }
            if (!TrackCurveSupport.place(world, curve)) {
                restore(world, previous); status(player, "blocked"); return EnumActionResult.FAIL;
            }
            a.add(curve); b.add(curve);
        }
        for (BlockPos pos : plan.placements().keySet()) RailwayNetworkData.get(world).update(world, pos);
        clear(stack);
        if (!player.capabilities.isCreativeMode) {
            consumeTracks(player, stack.getItem(), plan.required());
            consumePavement(player, pavement, plan.requiredPavement());
        }
        player.inventory.markDirty(); status(player, "connected");
        return EnumActionResult.SUCCESS;
    }

    /** Preview and placement share geometry, overlays, paving, permissions and material accounting. */
    @javax.annotation.Nullable public Preview preview(World world, EntityPlayer player, ItemStack stack, BlockPos clicked,
            EnumFacing face, float hitX, float hitY, float hitZ, boolean maximise) {
        if (!selected(stack)) return null;
        NBTTagCompound saved = stack.getTagCompound().getCompoundTag(SELECTION);
        BlockPos first = BlockPos.fromLong(saved.getLong("Pos"));
        if (saved.getInteger("Dim") != world.provider.getDimension() || !world.isBlockLoaded(first)
                || !(world.getBlockState(first).getBlock() instanceof TrackBlock)) return invalid(first, "original_missing");
        if (!world.isBlockLoaded(clicked)) return invalid(first, "unloaded");
        IBlockState hit = world.getBlockState(clicked);
        boolean existingEnd = hit.getBlock() instanceof TrackBlock;
        BlockPos end = existingEnd ? clicked : hit.getBlock().isReplaceable(world, clicked) ? clicked : clicked.offset(face);
        if (end.equals(first)) return invalid(first, "second_point");
        if (!world.isBlockLoaded(end) || first.distanceSq(end) > 32D * 32D) return invalid(first, "too_far");
        IBlockState start = world.getBlockState(first);
        IBlockState finish = existingEnd ? hit : block.getStateForPlacement(world, end, face, hitX, hitY, hitZ, 0, player);
        TrackPlacementGeometry.Plan geometry = TrackPlacementGeometry.plan(first, start.getValue(TrackBlock.SHAPE),
                end, finish.getValue(TrackBlock.SHAPE), maximise);
        if (geometry.error() != null) return invalid(first, geometry.error());
        Map<BlockPos, IBlockState> placements = new LinkedHashMap<>();
        String error = null;
        int required = 0;
        TrackCurve curve = geometry.curve();
        for (Map.Entry<BlockPos, TrackShape> entry : geometry.tracks().entrySet()) {
            BlockPos pos = entry.getKey();
            if (!editable(world, player, pos, face, stack)) { error = "blocked"; continue; }
            IBlockState old = world.getBlockState(pos);
            if (old.getBlock() instanceof TrackBlock) {
                TrackShape shape = TrackShape.overlay(old.getValue(TrackBlock.SHAPE), entry.getValue());
                if (shape == null || curve != null && (pos.equals(curve.first) || pos.equals(curve.second)) && shape.junction()) error = "blocked";
                else if (shape != old.getValue(TrackBlock.SHAPE)) placements.put(pos, old.withProperty(TrackBlock.SHAPE, shape));
            } else if (canReplace(world, player, pos, face, stack)) {
                placements.put(pos, block.getDefaultState().withProperty(TrackBlock.SHAPE, entry.getValue())); required++;
            } else error = "blocked";
        }
        if (curve != null) {
            if (world.getTileEntity(curve.first) instanceof TrackTileEntity tile && tile.has(curve.second)) error = "unavailable";
            if (!curveClear(world, player, curve, face, stack)) error = "blocked";
            required += curve.cost;
        }
        Map<BlockPos, IBlockState> pavement = new LinkedHashMap<>();
        ItemStack material = player.getHeldItemOffhand();
        int requiredPavement = 0;
        for (Map.Entry<BlockPos, TrackPavement.Placement> entry : TrackPavement.plan(world, geometry.tracks(), curve, material).entrySet()) {
            BlockPos pos = entry.getKey(); IBlockState state = entry.getValue().state();
            if (!editable(world, player, pos, EnumFacing.UP, material)) error = "paving_blocked";
            else {
                AxisAlignedBB box = state.getCollisionBoundingBox(world, pos);
                if (box != null && !world.checkNoEntityCollision(box.offset(pos))) error = "paving_blocked";
            }
            pavement.put(pos, state); requiredPavement += entry.getValue().cost();
        }
        if (!player.capabilities.isCreativeMode) {
            if (countTracks(player, stack.getItem()) < required) error = "missing";
            else if (countPavement(player, material) < requiredPavement) error = "missing_pavement";
        }
        return new Preview(first, placements, pavement, curve, error == null, required, requiredPavement, error);
    }
    private static Preview invalid(BlockPos first, String error) {
        return new Preview(first, new LinkedHashMap<>(), new LinkedHashMap<>(), null, false, 0, 0, error);
    }
    private record Snapshot(IBlockState state, NBTTagCompound tile) { }
    private static void remember(World world, Map<BlockPos, Snapshot> previous, BlockPos pos) {
        if (previous.containsKey(pos)) return;
        var tile = world.getTileEntity(pos);
        previous.put(pos, new Snapshot(world.getBlockState(pos), tile == null ? null : tile.writeToNBT(new NBTTagCompound())));
    }
    private static void restore(World world, Map<BlockPos, Snapshot> previous) {
        List<BlockPos> positions = new ArrayList<>(previous.keySet()); Collections.reverse(positions);
        for (BlockPos pos : positions) {
            Snapshot snapshot = previous.get(pos);
            if (world.getBlockState(pos) != snapshot.state()) world.setBlockState(pos, snapshot.state(), 3);
            var tile = world.getTileEntity(pos);
            if (tile != null && snapshot.tile() != null) { tile.readFromNBT(snapshot.tile()); tile.markDirty(); }
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
        for (BlockPos pos : positions) RailwayNetworkData.get(world).update(world, pos);
    }
    private static void clear(ItemStack stack) {
        if(!stack.hasTagCompound())return; stack.getTagCompound().removeTag(SELECTION);
        if(stack.getTagCompound().isEmpty()) stack.setTagCompound(null);
    }
    private static boolean editable(World world, EntityPlayer player, BlockPos pos, EnumFacing face, ItemStack held) {
        return pos.getY() >= 0 && pos.getY() < world.getHeight() && world.isBlockLoaded(pos) && world.getWorldBorder().contains(pos)
                && player.canPlayerEdit(pos, face, held) && world.isBlockModifiable(player, pos);
    }
    private static boolean canReplace(World world, EntityPlayer player, BlockPos pos, EnumFacing face, ItemStack held) {
        return editable(world, player, pos, face, held) && !world.getBlockState(pos).getBlock().hasTileEntity(world.getBlockState(pos))
                && world.getBlockState(pos).getBlock().isReplaceable(world, pos);
    }
    private static List<ItemStack> inventory(EntityPlayer player) {
        List<ItemStack> stacks = new ArrayList<>(player.inventory.mainInventory); stacks.addAll(player.inventory.offHandInventory); return stacks;
    }
    private static int countTracks(EntityPlayer player, net.minecraft.item.Item item) {
        int total = 0; for (ItemStack stack : inventory(player)) if (stack.getItem() == item) total += stack.getCount(); return total;
    }
    private static void consumeTracks(EntityPlayer player, net.minecraft.item.Item item, int amount) {
        for (ItemStack stack : inventory(player)) {
            if (amount <= 0) break;
            if (stack.getItem() != item) continue;
            int used = Math.min(amount, stack.getCount()); stack.shrink(used); amount -= used;
        }
    }
    private static boolean matching(ItemStack stack, ItemStack template) {
        return !stack.isEmpty() && !template.isEmpty() && ItemStack.areItemsEqual(stack, template) && ItemStack.areItemStackTagsEqual(stack, template);
    }
    private static int countPavement(EntityPlayer player, ItemStack template) {
        int total = 0; for (ItemStack stack : inventory(player)) if (matching(stack, template)) total += stack.getCount(); return total;
    }
    private static void consumePavement(EntityPlayer player, ItemStack template, int amount) {
        for (ItemStack stack : inventory(player)) {
            if (amount <= 0) break;
            if (!matching(stack, template)) continue;
            int used = Math.min(amount, stack.getCount()); stack.shrink(used); amount -= used;
        }
    }
    private static void status(EntityPlayer player,String key){
        player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.track."+key),true);
    }
    private static boolean curveClear(World world,EntityPlayer player,TrackCurve curve,EnumFacing face,ItemStack stack) {
        int segments=Math.max(8,(int)Math.ceil(curve.length()*4));
        for(int i=1;i<segments;i++) {
            Vec3d point=curve.point(i/(double)segments);
            BlockPos p=new BlockPos(point.x,point.y+.125D,point.z);
            if(p.equals(curve.first)||p.equals(curve.second))continue;
            if(!editable(world,player,p,face,stack))return false;
            IBlockState state=world.getBlockState(p);
            if(!(state.getBlock() instanceof TrackBlock) && !(state.getBlock() instanceof FakeTrackBlock)
                    && (state.getBlock().hasTileEntity(state) || !state.getBlock().isReplaceable(world,p)))return false;
        }
        return true;
    }
}
