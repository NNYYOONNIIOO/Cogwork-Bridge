package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class WorldshaperItem extends Item {
    private static final String KEY = "Worldshaper";
    // 1.12 has no block tags. Keep the 1.21 safe-NBT allowlist explicit.
    private static final java.util.Set<String> SAFE_DATA_BLOCKS = java.util.Set.of(
            "creative_motor", "creative_fluid_tank", "mechanical_piston", "sticky_mechanical_piston",
            "windmill_bearing", "mechanical_bearing", "clockwork_bearing", "rope_pulley", "cart_assembler",
            "linear_chassis", "secondary_linear_chassis", "radial_chassis", "sequenced_gearshift",
            "rotation_speed_controller", "andesite_funnel", "brass_funnel", "andesite_belt_funnel",
            "brass_belt_funnel", "redstone_link", "analog_lever", "placard", "pulse_repeater",
            "pulse_extender", "pulse_timer", "clipboard");
    public WorldshaperItem() { setMaxStackSize(1); setCreativeTab(ModCreativeTab.INSTANCE); }
    private static NBTTagCompound data(ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        if (!stack.getTagCompound().hasKey(KEY, 10)) stack.getTagCompound().setTag(KEY, new NBTTagCompound());
        return stack.getTagCompound().getCompoundTag(KEY);
    }
    public static int setting(ItemStack stack, String key, int fallback) { NBTTagCompound tag = data(stack); return tag.hasKey(key) ? tag.getInteger(key) : fallback; }
    public static void configure(ItemStack stack, int shape, int tool, int width, int height, int depth, int pattern, int placement, boolean diagonal, boolean across) {
        NBTTagCompound tag = data(stack);
        tag.setInteger("Shape", Math.max(0, Math.min(4, shape)));
        tag.setInteger("Tool", Math.max(0, Math.min(5, tool)));
        tag.setInteger("Width", Math.max(shape == 1 || shape == 2 ? 0 : 1, Math.min(32, width)));
        tag.setInteger("Height", Math.max(1, Math.min(32, height)));
        tag.setInteger("Depth", Math.max(1, Math.min(32, depth)));
        tag.setInteger("Pattern", Math.max(0, Math.min(5, pattern)));
        tag.setInteger("Placement", Math.max(0, Math.min(2, placement)));
        tag.setBoolean("Diagonal", diagonal); tag.setBoolean("Across", across);
    }
    public static boolean flag(ItemStack stack, String key) { return data(stack).getBoolean(key); }
    private static boolean safeData(IBlockState state, net.minecraft.tileentity.TileEntity tile) {
        if (tile instanceof net.minecraft.tileentity.TileEntitySign || tile instanceof net.minecraft.tileentity.TileEntityBanner) return true;
        net.minecraft.util.ResourceLocation name = state.getBlock().getRegistryName();
        return name != null && Reference.MOD_ID.equals(name.getNamespace()) && SAFE_DATA_BLOCKS.contains(name.getPath());
    }
    public static boolean select(ItemStack stack, World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() == net.minecraft.init.Blocks.AIR || state.getBlock().getRegistryName() == null) return false;
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(pos);
        if (state.getBlock().hasTileEntity(state) && (tile == null || !safeData(state, tile))) return false;
        NBTTagCompound tag = data(stack);
        tag.setString("Block", state.getBlock().getRegistryName().toString());
        tag.setInteger("Meta", state.getBlock().getMetaFromState(state));
        tag.removeTag("BlockData");
        if (tile != null) {
            NBTTagCompound saved = tile.writeToNBT(new NBTTagCompound());
            for (String key : new String[]{"id", "x", "y", "z", "Items", "Inventory", "ForgeCaps",
                    "KineticSpeed", "KineticAxis", "NetworkStress", "NetworkCapacity", "Overstressed"}) saved.removeTag(key);
            tag.setTag("BlockData", saved);
        }
        return true;
    }
    public static IBlockState selected(ItemStack stack) {
        NBTTagCompound tag = data(stack);
        Block block = Block.getBlockFromName(tag.getString("Block"));
        return block == null || block == net.minecraft.init.Blocks.AIR ? null : block.getStateFromMeta(tag.getInteger("Meta"));
    }
    public static RayTraceResult target(EntityPlayer player) {
        Vec3d start = player.getPositionEyes(1F);
        return player.world.rayTraceBlocks(start, start.add(player.getLook(1F).scale(128D)), false, true, false);
    }
    public static BlockPos center(ItemStack stack, BlockPos hit, EnumFacing face) {
        int shape = setting(stack, "Shape", 0);
        if (shape >= 3) return hit;
        int placement = setting(stack, "Placement", 0);
        if (placement == 0) return hit;
        boolean attached = placement == 1, negative = face.getAxisDirection() == EnumFacing.AxisDirection.NEGATIVE;
        int distance;
        if (shape == 1) distance = Math.min(10, setting(stack, "Width", 3)) + (attached ? 1 : 0);
        else if (shape == 2 && face.getAxis() != EnumFacing.Axis.Y)
            distance = Math.min(8, setting(stack, "Width", 3)) + (attached ? 1 : 0);
        else if (shape == 2) {
            int height = Math.min(8, setting(stack, "Height", 3));
            distance = (height + (attached ? negative ? 1 : 2 : negative ? 0 : -1)) / 2;
        } else {
            int size = switch (face.getAxis()) { case X -> setting(stack, "Width", 3); case Y -> setting(stack, "Height", 3); case Z -> setting(stack, "Depth", 3); };
            distance = (size + (attached ? negative ? 2 : 1 : 0)) / 2;
        }
        if (attached) return hit.offset(face, distance);
        if (placement == 2) return hit.offset(face.getOpposite(), distance);
        return hit;
    }
    @SubscribeEvent public static void selectBlock(PlayerInteractEvent.LeftClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        ItemStack stack = player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof WorldshaperItem)) return;
        event.setCanceled(true);
        if (!player.capabilities.isCreativeMode || !event.getWorld().isRemote) return;
        RayTraceResult hit = target(player);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) return;
        if (select(stack, player.world, hit.getBlockPos()))
            ModNetwork.CHANNEL.sendToServer(WorldshaperPacket.select(hit.getBlockPos(), player.inventory.currentItem));
    }
    @Override public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float x, float y, float z, EnumHand hand) {
        return use(world, player, hand).getType();
    }
    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side, float x, float y, float z) {
        return use(world, player, hand).getType();
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) { return use(world, player, hand); }
    private ActionResult<ItemStack> use(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!player.capabilities.isCreativeMode) return new ActionResult<>(EnumActionResult.FAIL, stack);
        if (player.isSneaking()) {
            if (world.isRemote) CogworkBridge.proxy.openWorldshaperScreen(hand);
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        RayTraceResult hit = target(player);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) return new ActionResult<>(EnumActionResult.PASS, stack);
        if (!world.isRemote) apply(world, player, stack, hit);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
    public static java.util.List<BlockPos> positions(World world, ItemStack stack, RayTraceResult hit) {
        int shape = setting(stack, "Shape", 0), tool = setting(stack, "Tool", 0);
        BlockPos center = center(stack, hit.getBlockPos(), hit.sideHit);
        java.util.List<BlockPos> result = new java.util.ArrayList<>();
        if (shape >= 3) {
            int range = Math.min(10, setting(stack, "Width", 3));
            boolean surface = shape == 3;
            boolean overlay = surface && tool == 4;
            BlockPos start = overlay ? center.offset(hit.sideHit) : center;
            Block reference = world.getBlockState(center).getBlock();
            boolean diagonal = flag(stack, "Diagonal"), across = flag(stack, "Across");
            java.util.Set<BlockPos> visited = new java.util.HashSet<>();
            java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                if (!visited.add(current) || current.distanceSq(start) >= range * range || !world.isBlockLoaded(current)) continue;
                BlockPos support = overlay ? current.offset(hit.sideHit.getOpposite()) : current;
                IBlockState state = world.getBlockState(support);
                if (!across && state.getBlock() != reference || state.getBlock().isReplaceable(world, support)
                        || !overlay && state.getBlockHardness(world, support) < 0) continue;
                if (overlay && !world.getBlockState(current).getBlock().isReplaceable(world, current)) continue;
                if (surface && !overlay && world.isSideSolid(current.offset(hit.sideHit), hit.sideHit.getOpposite())) continue;
                result.add(current);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (distance == 0 || !diagonal && distance > 1) continue;
                    if (surface && (hit.sideHit.getAxis() == EnumFacing.Axis.X && dx != 0
                            || hit.sideHit.getAxis() == EnumFacing.Axis.Y && dy != 0
                            || hit.sideHit.getAxis() == EnumFacing.Axis.Z && dz != 0)) continue;
                    queue.addLast(current.add(dx, dy, dz));
                }
            }
            return result;
        }
        int width = setting(stack, "Width", 3), height = setting(stack, "Height", 3), depth = setting(stack, "Depth", 3);
        if (shape == 1) {
            int radius = Math.min(10, width); double threshold = (radius + .5D) * (radius + .5D);
            for (int x = -radius; x <= radius; x++) for (int y = -radius; y <= radius; y++) for (int z = -radius; z <= radius; z++)
                if (x * x + y * y + z * z < threshold) result.add(center.add(x, y, z));
            return result;
        }
        if (shape == 2) {
            int radius = Math.min(8, width); height = Math.min(8, height); double threshold = (radius + .42D) * (radius + .42D);
            for (int x = -radius; x <= radius; x++) for (int y = 0; y < height; y++) for (int z = -radius; z <= radius; z++)
                if (x * x + z * z < threshold) result.add(center.add(x, y - height / 2, z));
            return result;
        }
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) for (int z = 0; z < depth; z++)
            result.add(center.add(x - (width - 1) / 2, y - (height - 1) / 2, z - (depth - 1) / 2));
        return result;
    }
    private static void apply(World world, EntityPlayer player, ItemStack stack, RayTraceResult hit) {
        int tool = setting(stack, "Tool", 0), shape = setting(stack, "Shape", 0), pattern = setting(stack, "Pattern", 0);
        IBlockState selected = selected(stack);
        if (selected == null && tool != 3 && tool != 5) return;
        if (shape >= 3 && (tool == 0 || tool == 1 || tool == 5 || shape == 4 && tool == 4)) return;
        java.util.List<BlockPos> positions = positions(world, stack, hit);
        if (tool == 5) { flatten(world, player, positions, hit.sideHit); return; }
        for (BlockPos pos : positions) {
            if (pos.getY() < 0 || pos.getY() >= world.getHeight() || !world.isBlockLoaded(pos) || !world.isBlockModifiable(player, pos)) continue;
            if (pattern == 1 && (pos.getX() + pos.getY() + pos.getZ()) % 2 == 0 || pattern == 2 && (pos.getX() + pos.getY() + pos.getZ()) % 2 != 0) continue;
            if (pattern >= 3 && world.rand.nextInt(4) >= pattern - 2) continue;
            IBlockState old = world.getBlockState(pos);
            boolean replaceable = old.getBlock().isReplaceable(world, pos);
            if (tool == 0 && !replaceable || tool == 2 && replaceable) continue;
            if (tool == 4 && shape != 3) {
                if (replaceable) continue;
                pos = pos.up();
                if (!world.isBlockLoaded(pos) || !world.isBlockModifiable(player, pos) || !world.getBlockState(pos).getBlock().isReplaceable(world, pos)) continue;
            }
            IBlockState placed = tool == 3 ? net.minecraft.init.Blocks.AIR.getDefaultState() : selected;
            world.setBlockState(pos, placed, 3);
            if (tool != 3 && world.getBlockState(pos).equals(placed) && data(stack).hasKey("BlockData", 10))
                applyBlockData(world, pos, placed, data(stack).getCompoundTag("BlockData"));
        }
        player.getCooldownTracker().setCooldown(stack.getItem(), 2);
    }
    private static void applyBlockData(World world, BlockPos pos, IBlockState state, NBTTagCompound saved) {
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(pos);
        if (tile == null || !safeData(state, tile)) return;
        NBTTagCompound target = tile.writeToNBT(new NBTTagCompound());
        target.merge(saved);
        target.setInteger("x", pos.getX()); target.setInteger("y", pos.getY()); target.setInteger("z", pos.getZ());
        tile.readFromNBT(target);
        tile.markDirty();
        world.notifyBlockUpdate(pos, state, state, 3);
    }
    private static void flatten(World world, EntityPlayer player, java.util.List<BlockPos> positions, EnumFacing face) {
        java.util.Map<Long, BlockPos> surface = new java.util.HashMap<>();
        for (BlockPos pos : positions) {
            if (!world.isBlockLoaded(pos) || !world.isBlockLoaded(pos.offset(face))) continue;
            if (world.getBlockState(pos).getBlock().isReplaceable(world, pos) || !world.getBlockState(pos.offset(face)).getBlock().isReplaceable(world, pos.offset(face))) continue;
            long key = planeKey(pos, face);
            BlockPos old = surface.get(key);
            if (old == null || coordinate(pos, face) > coordinate(old, face)) surface.put(key, pos);
        }
        java.util.Map<BlockPos, Integer> desired = new java.util.HashMap<>();
        final float[] gaussian = {.06136F, .24477F, .38774F, .24477F, .06136F};
        for (BlockPos pos : surface.values()) {
            float sum = 0F;
            for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) {
                BlockPos neighbor = switch (face.getAxis()) {
                    case X -> pos.add(0, a, b); case Y -> pos.add(a, 0, b); case Z -> pos.add(a, b, 0);
                };
                BlockPos other = surface.get(planeKey(neighbor, face));
                sum += coordinate(other == null ? pos : other, face) * gaussian[a + 2] * gaussian[b + 2];
            }
            desired.put(pos, Math.round(sum));
        }
        for (var entry : desired.entrySet()) {
            BlockPos pos = entry.getKey(); int from = coordinate(pos, face), to = entry.getValue();
            IBlockState material = world.getBlockState(pos);
            int delta = Math.max(-8, Math.min(8, to - from));
            for (int step = 0; step < Math.abs(delta); step++) {
                BlockPos edit = delta < 0 ? pos.offset(face.getOpposite(), step) : pos.offset(face, step + 1);
                if (!world.isBlockLoaded(edit) || !world.isBlockModifiable(player, edit)) break;
                world.setBlockState(edit, delta < 0 ? net.minecraft.init.Blocks.AIR.getDefaultState() : material, 3);
            }
        }
    }
    private static long planeKey(BlockPos pos, EnumFacing face) {
        return switch (face.getAxis()) {
            case X -> ((long) pos.getY() << 32) ^ (pos.getZ() & 0xffffffffL);
            case Y -> ((long) pos.getX() << 32) ^ (pos.getZ() & 0xffffffffL);
            case Z -> ((long) pos.getX() << 32) ^ (pos.getY() & 0xffffffffL);
        };
    }
    private static int coordinate(BlockPos pos, EnumFacing face) {
        return (face.getAxis() == EnumFacing.Axis.X ? pos.getX() : face.getAxis() == EnumFacing.Axis.Y ? pos.getY() : pos.getZ()) * face.getAxisDirection().getOffset();
    }
}
