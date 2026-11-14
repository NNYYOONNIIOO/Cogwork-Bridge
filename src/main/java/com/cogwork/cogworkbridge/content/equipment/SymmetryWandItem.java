package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Symmetry wand configuration and server-side mirrored block operations. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class SymmetryWandItem extends Item {
    private static final String DATA = "CogworkBridgeSymmetry";
    private static final ThreadLocal<Boolean> COPYING = ThreadLocal.withInitial(() -> false);
    private static final Map<EntityPlayerMP, List<PendingPlacement>> PENDING = new IdentityHashMap<>();
    private static final Map<EntityPlayerMP, List<PendingGroup>> MULTI = new IdentityHashMap<>();
    public SymmetryWandItem() { setMaxStackSize(1); setCreativeTab(ModCreativeTab.INSTANCE); }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking()) {
            if (world.isRemote) CogworkBridge.proxy.openSymmetryWandScreen(hand);
        } else if (!world.isRemote) {
            clear(stack);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                                 EnumFacing side, float x, float y, float z) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking()) {
            if (world.isRemote) CogworkBridge.proxy.openSymmetryWandScreen(hand);
            return EnumActionResult.SUCCESS;
        }
        if (!world.isRemote && hand == EnumHand.MAIN_HAND) {
            NBTTagCompound settings = dataForWrite(stack);
            int mode = mode(stack);
            float angle = Math.abs(player.rotationYaw % 90F);
            int alignment = mode == 0
                    ? (player.getHorizontalFacing().getAxis() == EnumFacing.Axis.Z ? 0 : 1)
                    : mode == 1 && angle > 22.5F && angle < 67.5F ? 1 : 0;
            settings.setLong("Origin", pos.offset(side).toLong());
            settings.setInteger("Mode", mode);
            settings.setInteger("Alignment", alignment);
            settings.setInteger("Dimension", world.provider.getDimension());
            settings.setBoolean("Enabled", true);
        }
        return EnumActionResult.SUCCESS;
    }
    private static void clear(ItemStack stack) {
        NBTTagCompound data = data(stack);
        if (data != null) data.setBoolean("Enabled", false);
    }

    private static NBTTagCompound data(ItemStack stack) {
        NBTTagCompound root = stack.getTagCompound();
        return root != null && root.hasKey(DATA, 10) ? root.getCompoundTag(DATA) : null;
    }

    private static NBTTagCompound dataForWrite(ItemStack stack) {
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) { root = new NBTTagCompound(); stack.setTagCompound(root); }
        if (!root.hasKey(DATA, 10)) root.setTag(DATA, new NBTTagCompound());
        return root.getCompoundTag(DATA);
    }

    public static int mode(ItemStack stack) {
        NBTTagCompound settings = data(stack);
        return settings == null ? 0 : Math.max(0, Math.min(2, settings.getInteger("Mode")));
    }

    public static int alignment(ItemStack stack) {
        NBTTagCompound settings = data(stack);
        if (settings == null) return 0;
        if (settings.hasKey("Alignment")) return Math.floorMod(settings.getInteger("Alignment"), 2);
        return settings.getInteger("Axis") == EnumFacing.Axis.X.ordinal() ? 1 : 0;
    }

    public static boolean enabled(ItemStack stack, World world) {
        NBTTagCompound settings = data(stack);
        return settings != null && settings.getBoolean("Enabled")
                && (!settings.hasKey("Dimension") || settings.getInteger("Dimension") == world.provider.getDimension());
    }

    public static BlockPos origin(ItemStack stack, BlockPos fallback) {
        NBTTagCompound settings = data(stack);
        if (settings == null) return fallback;
        if (settings.hasKey("Origin")) return BlockPos.fromLong(settings.getLong("Origin"));
        int axis = settings.getInteger("Axis"), plane = settings.getInteger("Plane");
        if (axis == EnumFacing.Axis.X.ordinal()) return new BlockPos(plane, fallback.getY(), fallback.getZ());
        if (axis == EnumFacing.Axis.Y.ordinal()) return new BlockPos(fallback.getX(), plane, fallback.getZ());
        return new BlockPos(fallback.getX(), fallback.getY(), plane);
    }

    public static void configure(ItemStack stack, int mode, int alignment) {
        NBTTagCompound settings = dataForWrite(stack);
        settings.setInteger("Mode", Math.max(0, Math.min(2, mode)));
        settings.setInteger("Alignment", Math.floorMod(alignment, 2));
    }

    private static Map<BlockPos, IBlockState> targets(ItemStack wand, World world,
                                                      BlockPos pos, IBlockState state) {
        if (!enabled(wand, world)) return Map.of();
        NBTTagCompound settings = data(wand);
        BlockPos origin = origin(wand, pos);
        if (origin.distanceSq(pos) > 100D * 100D) return Map.of();
        if (!settings.hasKey("Origin") && mode(wand) == 0
                && settings.getInteger("Axis") == EnumFacing.Axis.Y.ordinal()) {
            BlockPos target = new BlockPos(pos.getX(), origin.getY() * 2 - pos.getY(), pos.getZ());
            return target.equals(pos) ? Map.of() : Map.of(target, state);
        }
        return SymmetryGeometry.targets(origin, pos, state, mode(wand), alignment(wand));
    }
    private static ItemStack wand(EntityPlayer player) {
        if (player.getHeldItemOffhand().getItem() instanceof SymmetryWandItem) return player.getHeldItemOffhand();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack candidate = player.inventory.getStackInSlot(slot);
            if (candidate.getItem() instanceof SymmetryWandItem) return candidate;
        }
        return ItemStack.EMPTY;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void placed(BlockEvent.PlaceEvent event) {
        if (event.isCanceled() || COPYING.get() || event.getWorld().isRemote
                || !(event.getPlayer() instanceof EntityPlayerMP player)) return;
        ItemStack wand = wand(player); if (wand.isEmpty()) return;
        if (event instanceof BlockEvent.MultiPlaceEvent multi) {
            if (!enabled(wand, event.getWorld()) || origin(wand, event.getPos()).distanceSq(event.getPos()) > 10000) return;
            Map<BlockPos, IBlockState> originals = new java.util.LinkedHashMap<>();
            for (BlockSnapshot snapshot : multi.getReplacedBlockSnapshots())
                originals.put(snapshot.getPos().toImmutable(), event.getWorld().getBlockState(snapshot.getPos()));
            if (originals.isEmpty() || originals.size() > 64) return;
            BlockPos first = originals.keySet().iterator().next();
            ItemStack material = originals.get(first).getBlock().getItem(event.getWorld(), first, originals.get(first));
            if (material.isEmpty()) return;
            material = material.copy(); material.setCount(1);
            BlockPos center = origin(wand, event.getPos());
            for (Map<BlockPos, IBlockState> copy : SymmetryGeometry.groups(center, originals, mode(wand), alignment(wand)))
                MULTI.computeIfAbsent(player, ignored -> new ArrayList<>()).add(new PendingGroup(event.getWorld(),
                        Map.copyOf(originals), copy, material.copy(), event.getHand(), center));
            return;
        }
        BlockPos mirrorOrigin = origin(wand, event.getPos());
        for (Map.Entry<BlockPos, IBlockState> entry : targets(wand, event.getWorld(), event.getPos(), event.getPlacedBlock()).entrySet()) {
            PENDING.computeIfAbsent(player, ignored -> new ArrayList<>()).add(new PendingPlacement(
                    event.getWorld(), event.getPos().toImmutable(), entry.getKey(),
                    event.getPlacedBlock(), entry.getValue(), event.getHand(), mirrorOrigin));
        }
    }
    @SubscribeEvent public static void finishPlacements(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP player)) return;
        List<PendingPlacement> placements = PENDING.remove(player);
        List<PendingGroup> groups = MULTI.remove(player);
        if (player.isDead) return;
        if (placements != null) for (PendingPlacement placement : placements) placeMirror(player, placement);
        if (groups != null) for (PendingGroup group : groups) placeGroup(player, group);
    }
    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP player) { PENDING.remove(player); MULTI.remove(player); }
    }
    private record PendingGroup(World world, Map<BlockPos, IBlockState> originals,
            Map<BlockPos, IBlockState> copy, ItemStack material, EnumHand hand, BlockPos origin) { }
    private static void placeGroup(EntityPlayerMP player, PendingGroup group) {
        World world = group.world();
        if (player.world != world) return;
        for (var entry : group.originals().entrySet())
            if (!world.isBlockLoaded(entry.getKey()) || !world.getBlockState(entry.getKey()).equals(entry.getValue())) return;
        for (BlockPos pos : group.copy().keySet())
            if (!world.isBlockLoaded(pos) || !world.isBlockModifiable(player, pos)
                    || !player.canPlayerEdit(pos, EnumFacing.UP, group.material())
                    || !world.getBlockState(pos).getBlock().isReplaceable(world, pos)) return;
        for(var entry:group.copy().entrySet()) {
            var box=entry.getValue().getCollisionBoundingBox(world,entry.getKey());
            if(box!=null&&!world.checkNoEntityCollision(box.offset(entry.getKey())))return;
        }
        int slot = -1;
        if (!player.capabilities.isCreativeMode) {
            for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                ItemStack stack = player.inventory.getStackInSlot(i);
                if (ItemStack.areItemsEqual(stack, group.material()) && !stack.isEmpty()) { slot = i; break; }
            }
            if (slot < 0) return;
        }
        List<BlockSnapshot> before = new ArrayList<>(); boolean success = false;
        COPYING.set(true);
        try {
            for (var entry : group.copy().entrySet()) {
                before.add(BlockSnapshot.getBlockSnapshot(world, entry.getKey()));
                if (!world.setBlockState(entry.getKey(), entry.getValue(), 2)) return;
            }
            if (ForgeEventFactory.onPlayerMultiBlockPlace(player, before, EnumFacing.UP, group.hand()).isCanceled()) return;
            for (var entry : group.copy().entrySet()) {
                world.notifyNeighborsOfStateChange(entry.getKey(), entry.getValue().getBlock(), false);
                spawnEffect(world, group.origin(), entry.getKey());
            }
            if (slot >= 0) player.inventory.getStackInSlot(slot).shrink(1);
            player.inventory.markDirty(); success = true;
        } finally {
            if (!success) for (int i = before.size() - 1; i >= 0; i--) before.get(i).restore(true, false);
            COPYING.set(false);
        }
    }
    private static void placeMirror(EntityPlayerMP player, PendingPlacement placement) {
        World world = placement.world();
        BlockPos target = placement.target();
        if (player.world != world || !world.isBlockLoaded(placement.source())
                || !world.getBlockState(placement.source()).equals(placement.state())
                || !world.isBlockLoaded(target) || !world.isBlockModifiable(player, target)
                || !player.canPlayerEdit(target, EnumFacing.UP, player.getHeldItem(placement.hand()))
                || !world.getBlockState(target).getBlock().isReplaceable(world, target)) return;
        IBlockState state = placement.mirroredState();
        if (!world.mayPlace(state.getBlock(), target, false, EnumFacing.UP, player)) return;
        int slot = -1;
        if (!player.capabilities.isCreativeMode) {
            Item required = Item.getItemFromBlock(state.getBlock());
            if (required == net.minecraft.init.Items.AIR) return;
            for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                ItemStack candidate = player.inventory.getStackInSlot(i);
                if (!candidate.isEmpty() && candidate.getItem() == required
                        && (!candidate.getHasSubtypes() || candidate.getMetadata() == state.getBlock().damageDropped(state))) { slot = i; break; }
            }
            if (slot < 0) return;
        }
        BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(world, target);
        COPYING.set(true);
        try {
            if (!world.setBlockState(target, state, 3)) return;
            if (ForgeEventFactory.onPlayerBlockPlace(player, snapshot, EnumFacing.UP, placement.hand()).isCanceled()) {
                snapshot.restore(true, true);
                return;
            }
            if (slot >= 0) player.inventory.getStackInSlot(slot).shrink(1);
            spawnEffect(world, placement.mirrorOrigin(), target);
        } finally { COPYING.set(false); }
    }
    private record PendingPlacement(World world, BlockPos source, BlockPos target,
                                    IBlockState state, IBlockState mirroredState, EnumHand hand, BlockPos mirrorOrigin) { }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void broken(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || COPYING.get() || event.getWorld().isRemote || !(event.getPlayer() instanceof EntityPlayerMP player)) return;
        ItemStack wand = wand(event.getPlayer()); if (wand.isEmpty()) return;
        for (BlockPos target : targets(wand, event.getWorld(), event.getPos(), event.getState()).keySet()) {
            if (!event.getWorld().isBlockLoaded(target) || !event.getWorld().isBlockModifiable(player, target)) continue;
            IBlockState state = event.getWorld().getBlockState(target);
            if (!state.getBlock().isAir(state, event.getWorld(), target)
                    && (player.capabilities.isCreativeMode || state.getBlock() == event.getState().getBlock())) {
                COPYING.set(true);
                try {
                    if (player.interactionManager.tryHarvestBlock(target))
                        spawnEffect(event.getWorld(), origin(wand, event.getPos()), target);
                } finally { COPYING.set(false); }
            }
        }
    }

    private static void spawnEffect(World world, BlockPos start, BlockPos end) {
        if (!(world instanceof WorldServer server)) return;
        double dx = end.getX() - start.getX(), dy = end.getY() - start.getY(), dz = end.getZ() - start.getZ();
        int steps = Math.min(40, Math.max(1, (int) Math.sqrt(dx * dx + dy * dy + dz * dz)));
        for (int i = 1; i <= steps; i++) {
            double fraction = i / (double) steps;
            server.spawnParticle(EnumParticleTypes.END_ROD, start.getX() + .5D + dx * fraction,
                    start.getY() + .5D + dy * fraction, start.getZ() + .5D + dz * fraction,
                    1, 0, 0, 0, .015D);
        }
    }
}
