package com.cogwork.cogworkbridge.content.schematics;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.Mirror;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.template.PlacementSettings;
import net.minecraft.world.gen.structure.template.Template;
import xy177.farmersfuturedelight.api.WaterloggedBlockApi;
import xy177.farmersfuturedelight.common.fluid.FFDStoredFluidStates;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Bounded Minecraft structure-NBT reader shared by the uploader, preview and printer. */
public final class SchematicData {
    public static final int MAX_COMPRESSED = 2 * 1024 * 1024;
    public static final int MAX_BLOCKS = 131072;
    public static final int MAX_AXIS = 128;
    public static final int CHUNK = 16384;
    public final BlockPos size;
    public final List<Entry> blocks;
    public final List<Entry> containedFluids;
    public final List<EntityEntry> entities;
    public final NBTTagCompound nbt;

    private SchematicData(BlockPos size, List<Entry> blocks, List<EntityEntry> entities, NBTTagCompound nbt) {
        this.size = size; this.blocks = Collections.unmodifiableList(blocks);
        List<Entry> wet = new ArrayList<>();
        for (Entry entry : blocks) if (entry.containedFluid != null) wet.add(entry);
        this.containedFluids = Collections.unmodifiableList(wet);
        this.entities = Collections.unmodifiableList(entities); this.nbt = nbt;
    }

    public static SchematicData read(byte[] bytes) throws IOException {
        try { return parse(bytes); }
        catch (RuntimeException ex) { throw new IOException("invalid schematic NBT", ex); }
    }

    /** Capture both solid blocks and world fluid states through the same structure format. */
    public static byte[] capture(World world, BlockPos origin, BlockPos size, String author) throws IOException {
        Template template = new Template();
        template.takeBlocksFromWorld(world, origin, size, true, Blocks.AIR);
        template.setAuthor(author);
        NBTTagCompound structure = template.writeToNBT(new NBTTagCompound());
        NBTTagList fluids = new NBTTagList();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = 0; x < size.getX(); x++) for (int y = 0; y < size.getY(); y++) for (int z = 0; z < size.getZ(); z++) {
            at.setPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            IBlockState host = world.getBlockState(at);
            if (SchematicFluids.fluidOf(host) != null || !WaterloggedBlockApi.isWaterlogged(world, at)) continue;
            IBlockState contained = WaterloggedBlockApi.getFluidState(world, at);
            if (contained == null || SchematicFluids.fluidOf(contained) == null) continue;
            NBTTagCompound entry = new NBTTagCompound();
            NBTTagList local = new NBTTagList();
            local.appendTag(new NBTTagInt(x)); local.appendTag(new NBTTagInt(y)); local.appendTag(new NBTTagInt(z));
            entry.setTag("pos", local);
            entry.setTag("state", NBTUtil.writeBlockState(new NBTTagCompound(), contained));
            entry.setBoolean("stored", FFDStoredFluidStates.get(world, at) != null);
            fluids.appendTag(entry);
        }
        if (!fluids.isEmpty()) structure.setTag("cogworkbridge:contained_fluids", fluids);
        byte[] bytes = encode(structure);
        read(bytes);
        return bytes;
    }

    private static SchematicData parse(byte[] bytes) throws IOException {
        if (bytes.length < 1 || bytes.length > MAX_COMPRESSED) throw new IOException("schematic file size");
        NBTTagCompound data;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new ByteArrayInputStream(bytes))))) {
            data = CompressedStreamTools.read(input, new NBTSizeTracker(16L * 1024 * 1024));
        } catch (RuntimeException ex) {
            throw new IOException("invalid schematic NBT", ex);
        }
        NBTTagList dim = data.getTagList("size", 3);
        if (dim.tagCount() != 3) throw new IOException("missing size");
        int sx = dim.getIntAt(0), sy = dim.getIntAt(1), sz = dim.getIntAt(2);
        if (sx < 1 || sy < 1 || sz < 1 || sx > MAX_AXIS || sy > MAX_AXIS || sz > MAX_AXIS
                || (long) sx * sy * sz > MAX_BLOCKS) throw new IOException("schematic dimensions");
        NBTTagList palette = data.getTagList("palette", 10);
        NBTTagList list = data.getTagList("blocks", 10);
        if (palette.tagCount() < 1 || palette.tagCount() > 65536 || list.tagCount() > MAX_BLOCKS)
            throw new IOException("schematic palette");
        List<IBlockState> states = new ArrayList<>(palette.tagCount());
        for (int i = 0; i < palette.tagCount(); i++) {
            NBTTagCompound stateTag = palette.getCompoundTagAt(i).copy();
            normalizeModernState(stateTag);
            String id = stateTag.getString("Name");
            if (id.startsWith("create:")) {
                String local = "cogworkbridge:" + id.substring(7);
                if (net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(local)))
                    stateTag.setString("Name", local);
            }
            String resolved = stateTag.getString("Name");
            if (resolved.isEmpty() || !net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(resolved)))
                throw new IOException("unknown block: " + resolved);
            try { states.add(NBTUtil.readBlockState(stateTag)); }
            catch (RuntimeException ex) { throw new IOException("invalid block state: " + resolved, ex); }
        }
        Map<BlockPos, IBlockState> contained = new HashMap<>();
        Set<BlockPos> stored = new HashSet<>();
        NBTTagList fluidList = data.getTagList("cogworkbridge:contained_fluids", 10);
        if (fluidList.tagCount() > MAX_BLOCKS) throw new IOException("too many contained fluids");
        for (int i = 0; i < fluidList.tagCount(); i++) {
            NBTTagCompound fluidTag = fluidList.getCompoundTagAt(i);
            NBTTagList pos = fluidTag.getTagList("pos", 3);
            if (pos.tagCount() != 3 || !fluidTag.hasKey("state", 10)) throw new IOException("invalid contained fluid");
            BlockPos local = new BlockPos(pos.getIntAt(0), pos.getIntAt(1), pos.getIntAt(2));
            if (local.getX() < 0 || local.getY() < 0 || local.getZ() < 0
                    || local.getX() >= sx || local.getY() >= sy || local.getZ() >= sz || contained.containsKey(local))
                throw new IOException("invalid contained fluid position");
            NBTTagCompound fluidState = fluidTag.getCompoundTag("state");
            String fluidName = fluidState.getString("Name");
            if (fluidName.isEmpty() || !net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(fluidName)))
                throw new IOException("unknown contained fluid: " + fluidName);
            IBlockState fluid = NBTUtil.readBlockState(fluidState);
            if (SchematicFluids.fluidOf(fluid) == null) throw new IOException("invalid contained fluid state");
            contained.put(local, fluid);
            if (fluidTag.getBoolean("stored")) stored.add(local);
        }
        List<Entry> entries = new ArrayList<>();
        Set<BlockPos> occupied = new HashSet<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound block = list.getCompoundTagAt(i);
            NBTTagList pos = block.getTagList("pos", 3);
            int state = block.getInteger("state");
            if (pos.tagCount() != 3 || state < 0 || state >= states.size()) throw new IOException("invalid block entry");
            BlockPos local = new BlockPos(pos.getIntAt(0), pos.getIntAt(1), pos.getIntAt(2));
            if (local.getX() < 0 || local.getY() < 0 || local.getZ() < 0
                    || local.getX() >= sx || local.getY() >= sy || local.getZ() >= sz || !occupied.add(local))
                throw new IOException("invalid block position");
            IBlockState value = states.get(state);
            IBlockState fluid = contained.remove(local);
            // Older files can contain a wet host property but lack FFD's separate fluid data.
            if (fluid == null && SchematicFluids.fluidOf(value) == null && WaterloggedBlockApi.isWaterlogged(value))
                fluid = Blocks.WATER.getDefaultState();
            if (fluid != null) {
                IBlockState dry = WaterloggedBlockApi.withWaterlogged(value, false);
                if (dry != null) value = dry;
            }
            NBTTagCompound safe = block.hasKey("nbt", 10) ? safeBlockData(value, block.getCompoundTag("nbt")) : null;
            entries.add(new Entry(local, value, safe, fluid, stored.contains(local)));
        }
        if (!contained.isEmpty()) throw new IOException("contained fluid without host");
        // Build the containing blocks first, then sources and finally their flowing surfaces.
        entries.sort(Comparator.comparingInt(entry -> SchematicFluids.fluidOf(entry.state) == null ? 0
                : SchematicFluids.requiredAmount(entry.state) > 0 ? 1 : 2));
        List<EntityEntry> entities = new ArrayList<>();
        NBTTagList entityList = data.getTagList("entities", 10);
        if (entityList.tagCount() > 8192) throw new IOException("too many entities");
        for (int i = 0; i < entityList.tagCount(); i++) {
            NBTTagCompound entry = entityList.getCompoundTagAt(i);
            NBTTagCompound entity = entry.getCompoundTag("nbt");
            String type = entity.getString("id");
            if (!type.equals("minecraft:armor_stand") && !type.equals("minecraft:item_frame")) continue;
            NBTTagList vec = entry.getTagList("pos", 6);
            NBTTagList blockPos = entry.getTagList("blockPos", 3);
            if (vec.tagCount() != 3 || blockPos.tagCount() != 3) continue;
            double x = vec.getDoubleAt(0), y = vec.getDoubleAt(1), z = vec.getDoubleAt(2);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || x < -1 || y < -1 || z < -1 || x > sx + 1 || y > sy + 1 || z > sz + 1) continue;
            BlockPos local = new BlockPos(blockPos.getIntAt(0), blockPos.getIntAt(1), blockPos.getIntAt(2));
            if (local.getX() < -1 || local.getY() < -1 || local.getZ() < -1
                    || local.getX() > sx || local.getY() > sy || local.getZ() > sz) continue;
            NBTTagCompound safe = new NBTTagCompound(); safe.setString("id", type);
            String[] keys = type.equals("minecraft:armor_stand")
                    ? new String[]{"Pose", "Small", "ShowArms", "NoBasePlate", "Marker", "Invisible", "CustomName", "CustomNameVisible", "ArmorItems", "HandItems", "Rotation"}
                    : new String[]{"Item", "ItemRotation", "Facing", "Rotation"};
            for (String key : keys) if (entity.hasKey(key)) safe.setTag(key, entity.getTag(key).copy());
            sanitizeEquipment(safe);
            entities.add(new EntityEntry(new net.minecraft.util.math.Vec3d(x, y, z), local, safe));
        }
        return new SchematicData(new BlockPos(sx, sy, sz), entries, entities, data);
    }

    private static void sanitizeEquipment(NBTTagCompound entity) {
        if (entity.hasKey("Item", 10)) {
            ItemStack item = new ItemStack(entity.getCompoundTag("Item"));
            if (item.isEmpty()) entity.removeTag("Item");
            else { item.setCount(1); entity.setTag("Item", item.writeToNBT(new NBTTagCompound())); }
        }
        for (String key : new String[]{"ArmorItems", "HandItems"}) {
            if (!entity.hasKey(key, 9)) continue;
            NBTTagList source = entity.getTagList(key, 10), clean = new NBTTagList();
            int count = key.equals("ArmorItems") ? 4 : 2;
            for (int i = 0; i < count; i++) {
                ItemStack item = new ItemStack(source.getCompoundTagAt(i));
                if (!item.isEmpty()) item.setCount(1);
                clean.appendTag(item.isEmpty() ? new NBTTagCompound() : item.writeToNBT(new NBTTagCompound()));
            }
            entity.setTag(key, clean);
        }
    }

    /** Convert common post-1.12 vanilla names to the matching legacy block states. */
    public static void normalizeModernState(NBTTagCompound tag) {
        String id = tag.getString("Name");
        if (!id.startsWith("minecraft:")) return;
        String path = id.substring(10);
        NBTTagCompound props = tag.hasKey("Properties", 10) ? tag.getCompoundTag("Properties").copy() : new NBTTagCompound();
        String[] woods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
        for (int i = 0; i < woods.length; i++) {
            String wood = woods[i];
            if (path.equals(wood + "_planks")) {
                tag.setString("Name", "minecraft:planks"); props.setString("variant", wood);
            } else if (path.equals(wood + "_log") || path.equals(wood + "_wood")) {
                tag.setString("Name", i < 4 ? "minecraft:log" : "minecraft:log2"); props.setString("variant", wood);
                if (path.endsWith("_wood")) props.setString("axis", "none");
            } else if (path.equals(wood + "_leaves")) {
                tag.setString("Name", i < 4 ? "minecraft:leaves" : "minecraft:leaves2");
                props.setString("variant", wood); props.setString("check_decay", "false");
                props.setString("decayable", "true".equals(props.getString("persistent")) ? "false" : "true");
            } else if (path.equals(wood + "_slab")) {
                boolean doubleSlab = "double".equals(props.getString("type"));
                tag.setString("Name", doubleSlab ? "minecraft:double_wooden_slab" : "minecraft:wooden_slab");
                props.setString("variant", wood);
                if (!doubleSlab) props.setString("half", "top".equals(props.getString("type")) ? "top" : "bottom");
            }
        }
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        for (String color : colors) {
            String legacy = color.equals("light_gray") ? "silver" : color;
            if (path.equals(color + "_wool")) { tag.setString("Name", "minecraft:wool"); props.setString("color", legacy); }
            if (path.equals(color + "_terracotta")) { tag.setString("Name", "minecraft:stained_hardened_clay"); props.setString("color", legacy); }
            if (path.equals(color + "_stained_glass")) { tag.setString("Name", "minecraft:stained_glass"); props.setString("color", legacy); }
            if (path.equals(color + "_stained_glass_pane")) { tag.setString("Name", "minecraft:stained_glass_pane"); props.setString("color", legacy); }
            if (path.equals(color + "_concrete")) { tag.setString("Name", "minecraft:concrete"); props.setString("color", legacy); }
            if (path.equals(color + "_concrete_powder")) { tag.setString("Name", "minecraft:concrete_powder"); props.setString("color", legacy); }
        }
        switch (path) {
            case "grass_block" -> tag.setString("Name", "minecraft:grass");
            case "smooth_stone" -> tag.setString("Name", "farmers_future_delight:smooth_stone");
            case "dirt_path" -> tag.setString("Name", "minecraft:grass_path");
            case "oak_door" -> tag.setString("Name", "minecraft:wooden_door");
            case "oak_fence" -> tag.setString("Name", "minecraft:fence");
            case "oak_fence_gate" -> tag.setString("Name", "minecraft:fence_gate");
            case "bricks" -> tag.setString("Name", "minecraft:brick_block");
            case "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks" -> {
                tag.setString("Name", "minecraft:stonebrick");
                props.setString("variant", path.equals("stone_bricks") ? "stonebrick" : path.substring(0, path.indexOf('_')) + "_stonebrick");
            }
            case "short_grass" -> { tag.setString("Name", "minecraft:tallgrass"); props.setString("type", "tall_grass"); }
            default -> { }
        }
        tag.setTag("Properties", props);
    }

    @Nullable private static NBTTagCompound safeBlockData(IBlockState state, NBTTagCompound input) {
        ResourceLocation name = state.getBlock().getRegistryName();
        if (name == null) return null;
        String id = name.toString();
        if (id.equals("minecraft:standing_sign") || id.equals("minecraft:wall_sign")
                || id.equals("minecraft:standing_banner") || id.equals("minecraft:wall_banner")) {
            NBTTagCompound result = new NBTTagCompound();
            for (String key : new String[]{"id", "Text1", "Text2", "Text3", "Text4", "Base", "Patterns", "CustomName"})
                if (input.hasKey(key)) result.setTag(key, input.getTag(key).copy());
            return result;
        }
        if (!"cogworkbridge".equals(name.getNamespace())) return null;
        Set<String> safeBlocks = Set.of("copycat_panel", "copycat_step", "creative_motor", "creative_fluid_tank", "mechanical_piston",
                "sticky_mechanical_piston", "windmill_bearing", "mechanical_bearing", "clockwork_bearing",
                "rope_pulley", "cart_assembler", "linear_chassis", "secondary_linear_chassis", "radial_chassis",
                "sequenced_gearshift", "rotation_speed_controller", "andesite_funnel", "brass_funnel",
                "andesite_belt_funnel", "brass_belt_funnel", "redstone_link", "analog_lever", "placard",
                "pulse_repeater", "pulse_extender", "pulse_timer", "clipboard", "display_board", "nixie_tube");
        if (!safeBlocks.contains(name.getPath())) return null;
        NBTTagCompound result = input.copy();
        for (String key : new String[]{"Items", "Inventory", "inventory", "ItemsIn", "ItemsOut", "Filter", "Filters",
                "ForgeCaps", "ForgeData", "Owner", "owner", "LootTable", "LootTableSeed", "Command", "LastOutput",
                "KineticSpeed", "KineticAxis", "NetworkStress", "NetworkCapacity", "Overstressed"}) result.removeTag(key);
        return result;
    }

    public static byte[] encode(NBTTagCompound data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(data, output);
        byte[] result = output.toByteArray();
        if (result.length > MAX_COMPRESSED) throw new IOException("schematic file size");
        return result;
    }

    public static byte[] readFile(Path file) throws IOException {
        long size = Files.size(file);
        if (size < 1 || size > MAX_COMPRESSED) throw new IOException("schematic file size");
        byte[] bytes = Files.readAllBytes(file);
        read(bytes);
        return bytes;
    }

    public static Path serverDirectory(World world) {
        return world.getSaveHandler().getWorldDirectory().toPath().resolve("cogworkbridge_schematics");
    }

    @Nullable public static Path serverFile(World world, ItemStack item) {
        if (item.isEmpty() || item.getItem() != com.cogwork.cogworkbridge.registry.AllItems.SCHEMATIC || !item.hasTagCompound()) return null;
        String id = item.getTagCompound().getString("FileId");
        try { UUID.fromString(id); } catch (IllegalArgumentException ex) { return null; }
        return serverDirectory(world).resolve(id + ".nbt");
    }

    public static BlockPos transformed(BlockPos local, Mirror mirror, Rotation rotation) {
        return Template.transformedBlockPos(new PlacementSettings().setMirror(mirror).setRotation(rotation), local);
    }

    public static Rotation rotation(ItemStack stack) {
        int i = stack.hasTagCompound() ? Math.floorMod(stack.getTagCompound().getInteger("Rotation"), 4) : 0;
        return Rotation.values()[i];
    }

    public static Mirror mirror(ItemStack stack) {
        if (!stack.hasTagCompound()) return Mirror.NONE;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag.hasKey("MirrorAxis", 3)) {
            int axis = tag.getInteger("MirrorAxis");
            return axis == 1 ? Mirror.FRONT_BACK : axis == 2 ? Mirror.LEFT_RIGHT : Mirror.NONE;
        }
        return tag.getBoolean("Mirror") ? Mirror.FRONT_BACK : Mirror.NONE;
    }

    /** The horizontal bounds of transformed block coordinates, before adding the anchor. */
    public static int[] horizontalBounds(int width, int depth, Mirror mirror, Rotation rotation) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int x : new int[]{0, Math.max(0, width - 1)}) for (int z : new int[]{0, Math.max(0, depth - 1)}) {
            BlockPos p = transformed(new BlockPos(x, 0, z), mirror, rotation);
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        return new int[]{minX, maxX, minZ, maxZ};
    }

    public static BlockPos anchor(ItemStack stack) {
        return stack.hasTagCompound() ? BlockPos.fromLong(stack.getTagCompound().getLong("Anchor")) : BlockPos.ORIGIN;
    }

    private List<Entry> missingAir;
    /** Air is implicit in structure NBT; the clear mode must traverse the whole volume. */
    public List<Entry> missingAir() {
        if (missingAir != null) return missingAir;
        Set<BlockPos> occupied = new HashSet<>();
        for (Entry entry : blocks) occupied.add(entry.local);
        List<Entry> air = new ArrayList<>();
        for (int y = 0; y < size.getY(); y++) for (int z = 0; z < size.getZ(); z++) for (int x = 0; x < size.getX(); x++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!occupied.contains(pos)) air.add(new Entry(pos, Blocks.AIR.getDefaultState(), null));
        }
        missingAir = Collections.unmodifiableList(air);
        return missingAir;
    }

    public static final class EntityEntry {
        public final net.minecraft.util.math.Vec3d local;
        public final BlockPos blockPos;
        public final NBTTagCompound data;
        private EntityEntry(net.minecraft.util.math.Vec3d local, BlockPos blockPos, NBTTagCompound data) {
            this.local = local; this.blockPos = blockPos; this.data = data;
        }
        public net.minecraft.util.math.Vec3d target(BlockPos origin, Mirror mirror, Rotation rotation) {
            double x = mirror == Mirror.FRONT_BACK ? 1D - local.x : local.x;
            double z = mirror == Mirror.LEFT_RIGHT ? 1D - local.z : local.z;
            double tx = x, tz = z;
            switch (rotation) {
                case CLOCKWISE_90 -> { tx = 1D - z; tz = x; }
                case CLOCKWISE_180 -> { tx = 1D - x; tz = 1D - z; }
                case COUNTERCLOCKWISE_90 -> { tx = z; tz = 1D - x; }
                default -> { }
            }
            return new net.minecraft.util.math.Vec3d(origin.getX() + tx, origin.getY() + local.y, origin.getZ() + tz);
        }
        public List<ItemStack> requirements() {
            List<ItemStack> result = new ArrayList<>();
            result.add(new ItemStack(data.getString("id").equals("minecraft:armor_stand")
                    ? net.minecraft.init.Items.ARMOR_STAND : net.minecraft.init.Items.ITEM_FRAME));
            if (data.hasKey("Item", 10)) addItem(result, data.getCompoundTag("Item"));
            for (String key : new String[]{"ArmorItems", "HandItems"}) {
                NBTTagList list = data.getTagList(key, 10);
                for (int i = 0; i < list.tagCount(); i++) addItem(result, list.getCompoundTagAt(i));
            }
            return result;
        }
        private static void addItem(List<ItemStack> result, NBTTagCompound tag) {
            if (tag.isEmpty()) return;
            ItemStack stack = new ItemStack(tag);
            if (stack.isEmpty()) return;
            stack.setCount(1);
            for (ItemStack previous : result) {
                if (ItemStack.areItemsEqual(previous, stack) && ItemStack.areItemStackTagsEqual(previous, stack)) {
                    previous.grow(1); return;
                }
            }
            result.add(stack);
        }
    }

    public static final class Entry {
        public final BlockPos local;
        public final IBlockState state;
        @Nullable public final NBTTagCompound tile;
        @Nullable public final IBlockState containedFluid;
        public final boolean storedFluid;
        private Entry(BlockPos local, IBlockState state, @Nullable NBTTagCompound tile) {
            this(local, state, tile, null, false);
        }
        private Entry(BlockPos local, IBlockState state, @Nullable NBTTagCompound tile,
                      @Nullable IBlockState containedFluid, boolean storedFluid) {
            this.local = local; this.state = state; this.tile = tile;
            this.containedFluid = containedFluid; this.storedFluid = storedFluid;
        }
        public BlockPos target(BlockPos anchor, Mirror mirror, Rotation rotation) {
            return anchor.add(transformed(local, mirror, rotation));
        }
        public IBlockState transformedState(Mirror mirror, Rotation rotation) {
            return state.withMirror(mirror).withRotation(rotation);
        }
    }
}
