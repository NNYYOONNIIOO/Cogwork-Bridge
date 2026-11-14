package com.cogwork.cogworkbridge.compat.ponder;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes;
import com.cogwork.cogworkbridge.content.schematics.SchematicData;
import com.cogwork.cogworkbridge.registry.BlockFamily;
import com.google.gson.*;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import java.util.*;

/** Explicit modern-to-legacy mapping, scoped to the isolated demonstration world. */
final class PonderData {
    static final String[] COLORS = "white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black".split(" ");
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("andesite_encased_shaft", "shaft_encased_andesite"), Map.entry("brass_encased_shaft", "shaft_encased_brass"),
            Map.entry("andesite_encased_cogwheel", "cogwheel_encased_andesite"), Map.entry("brass_encased_cogwheel", "cogwheel_encased_brass"),
            Map.entry("andesite_encased_large_cogwheel", "large_cogwheel_encased_andesite"), Map.entry("brass_encased_large_cogwheel", "large_cogwheel_encased_brass"),
            Map.entry("smart_observer", "content_observer"), Map.entry("threshold_switch", "stockpile_switch"),
            Map.entry("orange_nixie_tube", "nixie_tube"), Map.entry("train_controls", "controls"),
            Map.entry("water_wheel_structure", "water_wheel_structural"), Map.entry("vertical_gearbox", "gearbox"),
            Map.entry("lit_blaze_burner", "empty_blaze_burner"),
            Map.entry("andesite_belt_funnel", "andesite_funnel"), Map.entry("brass_belt_funnel", "brass_funnel"));
    static String id(String id) {
        if (!id.contains(":")) id = "create:" + id;
        if (id.startsWith("create:")) id = Reference.MOD_ID + ":" + ALIASES.getOrDefault(id.substring(7), id.substring(7));
        return id;
    }
    static String colored(String family, Object color) {
        String path = family.substring(family.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String dye = String.valueOf(color).toLowerCase(Locale.ROOT);
        dye = dye.substring(dye.lastIndexOf('.') + 1);
        if (!Arrays.asList(COLORS).contains(dye)) throw new IllegalArgumentException("Unknown Ponder color " + color);
        return "create:" + switch (path) {
            case "dyed_sails" -> dye + "_sail";
            case "dyed_valve_handles" -> dye + "_valve_handle";
            case "postboxes", "package_postboxes" -> dye + "_postbox";
            case "table_cloths" -> dye + "_table_cloth";
            case "nixie_tubes" -> "nixie_tube";
            default -> throw new IllegalArgumentException("Unknown Ponder block family " + family);
        };
    }
    static List<String> components(String path) {
        List<String> result = new ArrayList<>();
        String suffix = switch (path) {
            case "dyed_sails" -> "_sail";
            case "dyed_valve_handles" -> "_valve_handle";
            case "postboxes", "package_postboxes" -> "_postbox";
            case "table_cloths" -> "_table_cloth";
            default -> null;
        };
        if (suffix != null) for (String color : COLORS) result.add("create:" + color + suffix);
        else if (path.equals("nixie_tubes")) result.add("create:nixie_tube");
        else if (path.equals("white_sail") || path.equals("sail")) for (String color : COLORS) result.add("create:" + color + "_sail");
        else result.add(path);
        return result;
    }
    static ItemStack item(String name) {
        String mapped = id(name);
        ItemStack stack = UpstreamProcessingRecipes.resolve(mapped);
        if (!stack.isEmpty()) return stack;
        NBTTagCompound stateTag = new NBTTagCompound(); stateTag.setString("Name", mapped);
        IBlockState state;
        try { state = state(stateTag); } catch (IllegalArgumentException missing) { return ItemStack.EMPTY; }
        Item item = Item.getItemFromBlock(state.getBlock());
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, 1, state.getBlock().damageDropped(state));
    }
    static IBlockState state(String name, Map<String, Object> properties) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("Name", name);
        NBTTagCompound props = new NBTTagCompound(); properties.forEach((key, value) -> props.setString(key, String.valueOf(value).toLowerCase(Locale.ROOT)));
        tag.setTag("Properties", props); return state(tag);
    }
    static IBlockState state(NBTTagCompound original) {
        NBTTagCompound tag = original.copy(); tag.setString("Name", id(tag.getString("Name")));
        // Handle modern names first: grass_block must not become tallgrass after legacy conversion.
        normalize(tag);
        SchematicData.normalizeModernState(tag);
        ResourceLocation key = new ResourceLocation(tag.getString("Name"));
        if (!ForgeRegistries.BLOCKS.containsKey(key) && key.getNamespace().equals("minecraft")) {
            ResourceLocation ffd = new ResourceLocation("farmers_future_delight", key.getPath());
            ResourceLocation local = new ResourceLocation(Reference.MOD_ID, key.getPath());
            if (ForgeRegistries.BLOCKS.containsKey(ffd)) key = ffd;
            else if (ForgeRegistries.BLOCKS.containsKey(local)) key = local;
        }
        if (!ForgeRegistries.BLOCKS.containsKey(key)) throw new IllegalArgumentException("Unmapped Ponder block: " + tag.getString("Name"));
        IBlockState state = ForgeRegistries.BLOCKS.getValue(key).getDefaultState();
        NBTTagCompound properties = tag.getCompoundTag("Properties");
        if (key.getPath().equals("belt") && !properties.getString("facing").isEmpty()) {
            String facing = properties.getString("facing");
            properties.setString("axis", properties.getString("slope").equals("sideways") ? "y" : facing.equals("east") || facing.equals("west") ? "z" : "x");
        }
        if (key.getPath().equals("water_wheel") && properties.hasKey("facing"))
            properties.setString("axis", net.minecraft.util.EnumFacing.byName(properties.getString("facing")).getAxis().getName());
        if (key.getPath().equals("blaze_burner") && properties.getString("heat_level").equals("none"))
            state = BlockFamily.get("empty_blaze_burner").getDefaultState();
        for (String property : properties.getKeySet()) state = property(state, property, properties.getString(property));
        return state;
    }
    private static void normalize(NBTTagCompound tag) {
        String name = tag.getString("Name");
        if (!name.startsWith("minecraft:")) return;
        String path = name.substring(10);
        ResourceLocation ffd = new ResourceLocation("farmers_future_delight", path);
        if (!ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(name)) && ForgeRegistries.BLOCKS.containsKey(ffd)) { tag.setString("Name", ffd.toString()); return; }
        NBTTagCompound props = tag.getCompoundTag("Properties");
        // Modern buttons split the mounting face from horizontal facing. Legacy stores both in facing.
        if (path.endsWith("_button")) {
            if (props.getString("face").equals("floor")) props.setString("facing", "up");
            else if (props.getString("face").equals("ceiling")) props.setString("facing", "down");
        }
        if (path.equals("lever") && props.hasKey("face")) {
            String face = props.getString("face"), facing = props.getString("facing");
            if (!face.equals("wall")) props.setString("facing", (face.equals("floor") ? "up_" : "down_")
                    + (facing.equals("east") || facing.equals("west") ? "x" : "z"));
        }
        String[] flowers = {"poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"};
        for (String flower : flowers) if (path.equals(flower)) { tag.setString("Name", "minecraft:red_flower"); props.setString("type", flower.equals("poppy") ? "poppy" : flower); }
        if (path.equals("dandelion")) tag.setString("Name", "minecraft:yellow_flower");
        if (path.startsWith("potted_")) tag.setString("Name", "minecraft:flower_pot");
        if (path.equals("rose_bush")) { tag.setString("Name", "minecraft:double_plant"); props.setString("variant", "double_rose"); }
        if (path.equals("fern") || path.equals("grass")) { tag.setString("Name", "minecraft:tallgrass"); props.setString("type", path.equals("fern") ? "fern" : "tall_grass"); }
        if (path.equals("coarse_dirt")) { tag.setString("Name", "minecraft:dirt"); props.setString("variant", "coarse_dirt"); }
        switch (path) {
            case "snow_block" -> tag.setString("Name", "minecraft:snow");
            case "snow" -> tag.setString("Name", "minecraft:snow_layer");
            case "chain" -> tag.setString("Name", "farmers_future_delight:iron_chain");
            case "oak_wall_hanging_sign" -> tag.setString("Name", "minecraft:wall_sign");
            case "redstone_torch", "redstone_wall_torch" -> tag.setString("Name", props.getString("lit").equals("false") ? "minecraft:unlit_redstone_torch" : "minecraft:redstone_torch");
            case "redstone_lamp" -> { if (props.getString("lit").equals("true")) tag.setString("Name", "minecraft:lit_redstone_lamp"); }
            case "repeater" -> tag.setString("Name", props.getString("powered").equals("true") ? "minecraft:powered_repeater" : "minecraft:unpowered_repeater");
            case "comparator" -> tag.setString("Name", props.getString("powered").equals("true") ? "minecraft:powered_comparator" : "minecraft:unpowered_comparator");
            case "wall_torch" -> tag.setString("Name", "minecraft:torch");
            case "nether_portal" -> tag.setString("Name", "minecraft:portal");
            case "note_block" -> tag.setString("Name", "minecraft:noteblock");
            case "spawner" -> tag.setString("Name", "minecraft:mob_spawner");
            case "oak_sign" -> tag.setString("Name", "minecraft:standing_sign");
            case "oak_wall_sign" -> tag.setString("Name", "minecraft:wall_sign");
            case "oak_trapdoor" -> tag.setString("Name", "minecraft:trapdoor");
            case "birch_button", "dark_oak_button" -> tag.setString("Name", "minecraft:wooden_button");
            case "cobblestone_slab" -> { tag.setString("Name", "minecraft:stone_slab"); props.setString("variant", "cobblestone"); props.setString("half", "top".equals(props.getString("type")) ? "top" : "bottom"); }
            default -> { }
        }
        tag.setTag("Properties", props);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    static IBlockState property(IBlockState state, String name, Object value) {
        name = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if ((name.equals("axis_along_first") || name.equals("axis_along_first_coordinate"))
                && state.getBlock() instanceof com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock)
            return state.withProperty(com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock.SHAFT_AXIS,
                    Boolean.parseBoolean(String.valueOf(value)) ? net.minecraft.util.EnumFacing.Axis.X : net.minecraft.util.EnumFacing.Axis.Z);
        for (IProperty property : state.getPropertyKeys()) if (property.getName().equals(name)) {
            com.google.common.base.Optional parsed = property.parseValue(String.valueOf(value).toLowerCase(Locale.ROOT));
            if (parsed.isPresent()) return state.withProperty(property, (Comparable) parsed.get());
        }
        return state;
    }
    static NBTTagCompound tile(net.minecraft.world.World world, IBlockState state, BlockPos pos, NBTTagCompound source, NBTTagCompound properties) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile == null) tile = state.getBlock().createTileEntity(world, state);
        if (tile == null) return null;
        tile.setWorld(world);
        tile.setPos(pos);
        NBTTagCompound result = tile.writeToNBT(new NBTTagCompound());
        NBTTagCompound converted = source.copy(); convertNested(converted);
        for (String key : converted.getKeySet()) if (!key.equals("id") && !key.equals("x") && !key.equals("y") && !key.equals("z"))
            result.setTag(key, converted.getTag(key).copy());
        String path = state.getBlock().getRegistryName().getPath();
        if (source.hasKey("Speed")) result.setFloat("KineticSpeed", source.getFloat("Speed"));
        String axis = properties.getString("axis");
        if (!axis.isEmpty()) result.setByte("KineticAxis", (byte) (axis.equals("x") ? 0 : axis.equals("y") ? 1 : 2));
        if (state.getBlock() instanceof com.cogwork.cogworkbridge.kinetic.KineticBlock kinetic)
            result.setByte("KineticAxis", (byte) kinetic.getAxis(state).ordinal());
        if (path.equals("belt")) {
            String facing = properties.getString("facing"), slope = properties.getString("slope"), part = properties.getString("part");
            result.setInteger("StepX", slope.equals("vertical") ? 0 : facing.equals("east") ? 1 : facing.equals("west") ? -1 : 0);
            result.setInteger("StepZ", slope.equals("vertical") ? 0 : facing.equals("south") ? 1 : facing.equals("north") ? -1 : 0);
            result.setInteger("StepY", slope.equals("upward") ? 1 : slope.equals("downward") ? -1 : slope.equals("vertical")
                    ? facing.equals("west") || facing.equals("north") ? -1 : 1 : 0);
            result.setBoolean("Pulley", !part.equals("middle"));
            result.setInteger("Index", source.getInteger("Index"));
            result.setInteger("Length", Math.max(1, source.getInteger("Length")));
            if (source.hasKey("Controller")) {
                BlockPos origin = modernPos(source.getTag("Controller")), last = modernPos(source.getTag("LastKnownPos"));
                if (source.hasKey("LastKnownPos")) result.setInteger("Index", Math.max(Math.abs(origin.getX() - last.getX()), Math.max(Math.abs(origin.getY() - last.getY()), Math.abs(origin.getZ() - last.getZ()))));
            }
        }
        if (path.contains("pipe") || path.equals("mechanical_pump") || path.equals("fluid_valve")) {
            int connections = 0;
            boolean sided = false;
            for (net.minecraft.util.EnumFacing side : net.minecraft.util.EnumFacing.values()) sided |= properties.hasKey(side.getName());
            for (net.minecraft.util.EnumFacing side : net.minecraft.util.EnumFacing.values()) {
                // Pressure records can exist on closed faces; the saved block state is authoritative.
                boolean connected = sided ? properties.getBoolean(side.getName()) || properties.getString(side.getName()).equals("true")
                        : !axis.isEmpty() ? side.getAxis().getName().equals(axis) : source.hasKey(side.getName(), 10);
                if (connected) connections |= 1 << side.getIndex();
            }
            result.setByte("Connections", (byte) connections);
            result.setInteger("PreferredAxis", axis.equals("x") ? 0 : axis.equals("y") ? 1 : 2);
        }
        aliases(result, path);
        if (path.equals("rope_pulley")) result.setBoolean("Running", true);
        if (path.equals("mechanical_saw") && properties.hasKey("flipped"))
            result.setBoolean("SawFlipped", properties.getString("flipped").equals("true"));
        if (path.equals("mechanical_crafter")) {
            NBTTagCompound forge = result.getCompoundTag("ForgeData");
            forge.setBoolean("TutorialCrafter", true);
            result.setTag("ForgeData", forge);
        }
        if (path.equals("mechanical_arm")) {
            NBTTagCompound forge = result.getCompoundTag("ForgeData");
            forge.setBoolean("TutorialArm", true);
            result.setTag("ForgeData", forge);
            armPointSides(world, pos, result);
        }
        if (path.equals("water_wheel") || path.equals("large_water_wheel")) waterWheelMaterial(result);
        if (path.equals("track") && source.hasKey("Connections", 9)) {
            NBTTagList connections = source.getTagList("Connections", 10), curves = new NBTTagList();
            for (int i = 0; i < connections.tagCount(); i++) {
                NBTTagCompound connection = connections.getCompoundTagAt(i);
                NBTTagList positions = connection.getTagList("Positions", 10), starts = connection.getTagList("Starts", 10), axes = connection.getTagList("Axes", 10);
                if (positions.tagCount() != 2 || starts.tagCount() != 2 || axes.tagCount() != 2) continue;
                BlockPos first = pos.add(nestedPos(positions.getCompoundTagAt(0))), second = pos.add(nestedPos(positions.getCompoundTagAt(1)));
                net.minecraft.util.math.Vec3d offset = new net.minecraft.util.math.Vec3d(pos);
                net.minecraft.util.math.Vec3d start = modernVec(starts.getCompoundTagAt(0)).add(offset), end = modernVec(starts.getCompoundTagAt(1)).add(offset);
                net.minecraft.util.math.Vec3d axis1 = modernVec(axes.getCompoundTagAt(0)).normalize(), axis2 = modernVec(axes.getCompoundTagAt(1)).normalize();
                double handle = curveHandle(start, end, axis1, axis2);
                var curve = new com.cogwork.cogworkbridge.content.trains.track.TrackCurve(first, second, start, end, start.add(axis1.scale(handle)), end.add(axis2.scale(handle)), 1);
                curves.appendTag(curve.write());
            }
            result.setTag("Curves", curves);
        }
        if (path.equals("fluid_pipe") || path.equals("glass_fluid_pipe") || path.equals("encased_fluid_pipe") || path.equals("smart_fluid_pipe")) {
            NBTTagCompound flows = new NBTTagCompound();
            for (net.minecraft.util.EnumFacing side : net.minecraft.util.EnumFacing.values()) {
                NBTTagCompound modern = source.getCompoundTag(side.getName());
                if (!modern.hasKey("Flow", 10)) continue;
                NBTTagCompound flow = modern.getCompoundTag("Flow");
                NBTTagCompound legacy = new NBTTagCompound();
                legacy.setTag("Fluid", flow.getCompoundTag("Fluid").copy());
                legacy.setBoolean("Inbound", flow.getBoolean("Inbound"));
                legacy.setFloat("Progress", 1);
                legacy.setFloat("Speed", .1F);
                flows.setTag(side.getName(), legacy);
                result.setTag("DisplayedFluid", flow.getCompoundTag("Fluid").copy());
            }
            if (!flows.getKeySet().isEmpty()) result.setTag("PipeFlows", flows);
        }
        if (result.hasKey("TargetTrack", 10) || result.hasKey("TargetTrack", 11)) {
            result.setLong("TargetTrack", pos.add(modernPos(result.getTag("TargetTrack"))).toLong());
            result.setBoolean("TargetFront", source.getBoolean("TargetDirection"));
        }
        // StateGuard keeps non-orientation state in tiles rather than block metadata.
        for (String property : properties.getKeySet()) applyVisualNbt(result, path, property, properties.getString(property));
        return result;
    }
    static void aliases(NBTTagCompound nbt, String path) {
        if (nbt.hasKey("Speed")) nbt.setFloat("KineticSpeed", nbt.getFloat("Speed"));
        if (path.equals("mechanical_mixer")) {
            copy(nbt, "Running", "MixerRunning"); copy(nbt, "RunningTicks", "MixerRunningTicks"); copy(nbt, "Ticks", "MixerRunningTicks");
        }
        if (path.equals("mechanical_press")) copy(nbt, "Running", "PressRunning");
        if (path.equals("mechanical_press") && nbt.hasKey("Ticks")) nbt.setFloat("PressProgress", nbt.getFloat("Ticks") * 1000F / 120F);
        if (path.contains("pulley")) copy(nbt, "Offset", "CableOffset");
        if (path.equals("fluid_tank") || path.equals("creative_fluid_tank")) {
            copy(nbt, "Width", "TankWidth");
            copy(nbt, "Height", "TankHeight"); copy(nbt, "TankContent", "Tank"); copy(nbt, "Window", "TankWindows");
            if (nbt.hasKey("Size")) nbt.setInteger("TankWidth", Math.max(1, nbt.getInteger("Size")));
            if (nbt.hasKey("Controller")) {
                BlockPos relative = modernPos(nbt.getTag("Controller")).subtract(modernPos(nbt.getTag("LastKnownPos")));
                com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity.setControllerOffset(nbt, relative);
            }
        }
        if (path.equals("depot") && nbt.hasKey("HeldItem", 10)) {
            NBTTagCompound held = nbt.getCompoundTag("HeldItem"); if (held.hasKey("Item", 10)) nbt.setTag("HeldItem", held.getCompoundTag("Item").copy());
        }
        if (path.equals("deployer")) {
            copy(nbt, "Filter", "DeployerFilter"); copy(nbt, "Timer", "DeployerTimer");
            NBTTagList items = nbt.getCompoundTag("Inventory").getTagList("Items", 10);
            if (items.tagCount() > 0) nbt.setTag("DeployerHeld", items.getCompoundTagAt(0).copy());
            if (nbt.hasKey("Mode")) nbt.setBoolean("DeployerPunch", nbt.getString("Mode").equals("PUNCH"));
        }
        if (path.contains("funnel")) { copy(nbt, "FilterAmount", "Amount"); copy(nbt, "UpTo", "Exact"); }
        if (path.equals("nixie_tube")) {
            copy(nbt, "RawCustomText", "Text"); copy(nbt, "RawCustomText", "RowText");
            if (nbt.hasKey("RawCustomText")) nbt.setBoolean("Manual", true);
            else if (nbt.hasKey("RedstoneStrength")) { nbt.setString("Text", Integer.toString(nbt.getInteger("RedstoneStrength"))); nbt.setString("RowText", nbt.getString("Text")); nbt.setBoolean("Manual", true); }
        }
        if (path.equals("mechanical_arm")) {
            copy(nbt, "HeldItem", "Held"); copy(nbt, "MovementProgress", "Progress");
            if (nbt.hasKey("Phase", 8)) nbt.setInteger("Phase", PonderTile.ordinal(nbt.getString("Phase")));
            if (nbt.hasKey("InteractionPoints", 9)) {
                NBTTagList original = nbt.getTagList("InteractionPoints", 10), points = new NBTTagList();
                int inputs = 0;
                for (int i = 0; i < original.tagCount(); i++) if (original.getCompoundTagAt(i).getString("Mode").equals("TAKE")) inputs++;
                for (boolean output : new boolean[]{false, true}) for (int i = 0; i < original.tagCount(); i++) {
                    NBTTagCompound point = original.getCompoundTagAt(i);
                    if (point.getString("Mode").equals("DEPOSIT") != output) continue;
                    NBTTagCompound converted = new NBTTagCompound();
                    converted.setLong("Offset", modernPos(point.getTag("Pos")).toLong());
                    converted.setBoolean("Output", output);
                    converted.setInteger("Side", net.minecraft.util.EnumFacing.DOWN.getIndex());
                    points.appendTag(converted);
                }
                nbt.setTag("Points", points);
                int target = nbt.getInteger("TargetPointIndex");
                int phase = nbt.getInteger("Phase");
                nbt.setInteger("Target", target < 0 || phase != 1 && phase != 3 ? -1 : target + (phase == 3 ? inputs : 0));
            }
        }
        if (path.equals("belt") && nbt.hasKey("Casing", 8)) nbt.setByte("Casing", (byte) switch (nbt.getString("Casing").toLowerCase(Locale.ROOT)) { case "andesite" -> 1; case "brass" -> 2; default -> 0; });
        if (path.equals("track_signal") && nbt.hasKey("State", 8)) nbt.setInteger("SignalState", switch (nbt.getString("State")) { case "GREEN" -> 2; case "RED" -> 1; default -> 0; });
        if (path.equals("factory_gauge")) {
            NBTTagList panels = new NBTTagList();
            boolean modern = false;
            for (String slot : new String[]{"top_left", "top_right", "bottom_left", "bottom_right"}) {
                NBTTagCompound panel = new NBTTagCompound();
                if (nbt.hasKey(slot, 10)) {
                    modern = true; NBTTagCompound source = nbt.getCompoundTag(slot);
                    panel.setUniqueId("Network", new UUID(0, 1));
                    panel.setTag("Filter", source.getCompoundTag("Filter").copy());
                    panel.setInteger("Target", source.getInteger("FilterAmount"));
                    panel.setInteger("Current", source.getBoolean("Satisfied") ? source.getInteger("FilterAmount") : source.getInteger("LastLevel"));
                    panel.setInteger("Promised", source.getInteger("LastPromised"));
                    panel.setInteger("RecipeOutput", Math.max(1, source.getInteger("RecipeOutput")));
                    panel.setString("Address", source.getString("RecipeAddress"));
                }
                panels.appendTag(panel);
            }
            if (modern) nbt.setTag("Panels", panels);
        }
        if (path.equals("redstone_link")) { copy(nbt, "FrequencyFirst", "First"); copy(nbt, "FrequencyLast", "Second"); copy(nbt, "Receive", "Power"); if (nbt.hasKey("Transmitter")) nbt.setBoolean("Receiver", !nbt.getBoolean("Transmitter")); }
        if (path.equals("packager") || path.equals("repackager")) { copy(nbt, "HeldBox", "Box"); copy(nbt, "InsertedBox", "IncomingVisual"); copy(nbt, "AnimationTicks", "Animation"); }
        if (path.equals("chute") || path.equals("smart_chute")) { copy(nbt, "Pull", "PullForce"); copy(nbt, "Push", "PushForce"); }
        if (path.equals("portable_storage_interface") || path.equals("portable_fluid_interface")) { copy(nbt, "Distance", "ConnectionDistance"); if (nbt.hasKey("Timer")) nbt.setBoolean("Connected", nbt.getInteger("Timer") > 0); }
        if (path.equals("mechanical_bearing") || path.equals("windmill_bearing")) copy(nbt, "Running", "Assembled");
        if (path.equals("millstone")) {
            NBTTagList items = nbt.getCompoundTag("InputInventory").getTagList("Items", 10);
            if (items.tagCount() > 0) nbt.setTag("MillstoneInput", items.getCompoundTagAt(0).copy());
        }
        if (path.equals("mechanical_crafter")) {
            NBTTagCompound inventory = nbt.getCompoundTag("Inventory");
            NBTTagList items = inventory.getTagList("Items", 10);
            if (items.tagCount() > 0) nbt.setTag("CrafterInput", items.getCompoundTagAt(0).copy());
            if (nbt.hasKey("Cover", 1)) {
                if (nbt.getBoolean("Cover")) nbt.setTag("CrafterInput", new ItemStack(
                        com.cogwork.cogworkbridge.registry.AllItems.CRAFTER_SLOT_COVER).serializeNBT());
                else if (new ItemStack(nbt.getCompoundTag("CrafterInput")).getItem()
                        == com.cogwork.cogworkbridge.registry.AllItems.CRAFTER_SLOT_COVER) nbt.removeTag("CrafterInput");
            }
        }
    }
    static void waterWheelMaterial(NBTTagCompound nbt) {
        if (!nbt.hasKey("Material", 10)) return;
        IBlockState material = state(nbt.getCompoundTag("Material"));
        String path = material.getBlock().getRegistryName().getPath();
        if (path.equals("planks")) nbt.setInteger("Wood", material.getBlock().getMetaFromState(material) & 7);
        else if (path.equals("crimson_planks")) nbt.setInteger("Wood", 6);
        else if (path.equals("warped_planks")) nbt.setInteger("Wood", 7);
    }
    static void armPointSides(net.minecraft.world.World world, BlockPos pos, NBTTagCompound nbt) {
        NBTTagList points = nbt.getTagList("Points", 10);
        for (int i = 0; i < points.tagCount(); i++) {
            NBTTagCompound point = points.getCompoundTagAt(i);
            IBlockState target = world.getBlockState(pos.add(BlockPos.fromLong(point.getLong("Offset"))));
            point.setInteger("Side", net.minecraft.util.EnumFacing.DOWN.getIndex());
            if (!target.getBlock().getRegistryName().getPath().contains("funnel")) continue;
            for (IProperty<?> property : target.getPropertyKeys())
                if (property.getName().equals("facing") && target.getValue(property) instanceof net.minecraft.util.EnumFacing facing)
                    point.setInteger("Side", facing.getOpposite().getIndex());
        }
    }
    private static void applyVisualNbt(NBTTagCompound tag, String path, String property, Object value) {
        String text = String.valueOf(value).toLowerCase(Locale.ROOT);
        NBTTagCompound forge = tag.getCompoundTag("ForgeData"), visual = forge.getCompoundTag("TutorialState");
        visual.setString(property, text); forge.setTag("TutorialState", visual); tag.setTag("ForgeData", forge);
        if (property.equals("powered")) tag.setBoolean("Powered", text.equals("true"));
        if (property.equals("heat_level") && path.contains("blaze_burner")) {
            tag.setInteger("Fuel", text.equals("fading") ? 100 : text.equals("none") || text.equals("smouldering") ? 0 : 10000);
            tag.setBoolean("SpecialFuel", text.equals("seething"));
        }
        if (property.equals("flame_type") && path.equals("empty_blaze_burner")) {
            tag.setBoolean("EmptyLit", true);
            tag.setBoolean("EmptySoulFire", text.equals("soul"));
        }
        if (property.equals("open")) tag.setBoolean("Open", text.equals("true"));
        if (path.equals("nixie_tube") && property.equals("color")) tag.setInteger("Color", Math.max(0, Arrays.asList(COLORS).indexOf(text)));
        if (property.equals("calling")) tag.setBoolean("Calling", text.equals("true"));
        if (property.equals("receiver")) tag.setBoolean("Receiver", text.equals("true"));
        if (property.equals("powering")) { tag.setBoolean("Powering", text.equals("true")); tag.setBoolean("Output", text.equals("true")); tag.setBoolean("Lit", text.equals("true")); }
        if (path.contains("funnel") && property.equals("extracting")) tag.setBoolean("Extracting", text.equals("true"));
        if (path.contains("funnel") && property.equals("shape")) tag.setBoolean("Extended", text.equals("extended"));
        if (path.equals("belt") && property.equals("part")) tag.setBoolean("Pulley", !text.equals("middle"));
        if (property.equals("assembling")) tag.setBoolean("Assembling", text.equals("true"));
        if (path.equals("mechanical_crafter") && property.equals("pointing")) tag.setByte("CrafterPointing", (byte) crafterPointing(text).ordinal());
        if (path.equals("basin") && property.equals("facing")) tag.setInteger("OutputSide", net.minecraft.util.EnumFacing.valueOf(text.toUpperCase(Locale.ROOT)).getIndex());
        if (path.equals("chute") && property.equals("shape")) tag.setBoolean("Window", text.equals("window"));
        if (path.equals("sticker") && property.equals("extended")) { tag.setBoolean("Extended", text.equals("true")); tag.setFloat("Progress", text.equals("true") ? 1 : 0); }
        if (path.equals("fluid_valve") && property.equals("enabled")) { tag.setBoolean("ValveOpen", text.equals("true")); tag.setFloat("ValvePointer", text.equals("true") ? 1 : 0); }
        if (property.startsWith("sticky_")) {
            String face = property.substring(7);
            net.minecraft.util.EnumFacing side = face.equals("top") || face.equals("bottom")
                    ? net.minecraft.util.EnumFacing.getFacingFromAxis(face.equals("top") ? net.minecraft.util.EnumFacing.AxisDirection.POSITIVE : net.minecraft.util.EnumFacing.AxisDirection.NEGATIVE,
                    net.minecraft.util.EnumFacing.Axis.values()[Math.max(0, Math.min(2, tag.getByte("KineticAxis")))])
                    : net.minecraft.util.EnumFacing.valueOf(face.toUpperCase(Locale.ROOT));
            int mask = tag.getInteger("StickyMask");
            tag.setInteger("StickyMask", text.equals("true") ? mask | 1 << side.getIndex() : mask & ~(1 << side.getIndex()));
        }
    }
    static void visualProperty(PonderProgram program, TileEntity tile, String property, Object value) {
        String text = String.valueOf(value).toLowerCase(Locale.ROOT);
        NBTTagCompound visual = tile.getTileData().getCompoundTag("TutorialState");
        visual.setString(property, text); tile.getTileData().setTag("TutorialState", visual);
        String path = tile.getBlockType().getRegistryName().getPath();
        PonderTile access = new PonderTile(program, tile);
        if (access.hasField(property) && !(value instanceof PonderValues.Construct)) access.assign(property,
                property.equals("color") && path.equals("nixie_tube")
                        ? net.minecraft.item.EnumDyeColor.byMetadata(Math.max(0, Arrays.asList(COLORS).indexOf(text))) : value);
        if (property.equals("powered") && access.hasField("powered")) access.assign("powered", PonderValues.truth(value));
        if (path.equals("display_link") && property.equals("powered")) access.assign("observedPower", PonderValues.truth(value));
        if (path.contains("funnel")) {
            if (property.equals("extracting")) access.assign("extracting", PonderValues.truth(value));
            if (property.equals("shape")) access.assign("extended", String.valueOf(value).equals("extended"));
        }
        if (path.equals("belt") && property.equals("part")) access.assign("pulley", !String.valueOf(value).equals("middle"));
        if (path.equals("belt") && property.equals("casing")) access.assign("casing", PonderValues.truth(value) ? "ANDESITE" : "NONE");
        if (path.equals("mechanical_crafter") && property.equals("pointing")) access.assign("pointing", crafterPointing(text));
        if (path.equals("basin") && property.equals("facing")) access.assign("outputSide", PonderValues.direction(value));
        if (path.equals("chute") && property.equals("shape")) access.assign("window", text.equals("window"));
        if (path.equals("sticker") && property.equals("extended")) access.assign("extended", PonderValues.truth(value));
        if (path.equals("fluid_valve") && property.equals("enabled")) access.assign("open", PonderValues.truth(value));
        if ((property.equals("axis_along_first") || property.equals("axis_along_first_coordinate")) && access.hasField("axisAlongFirst")) access.assign("axisAlongFirst", PonderValues.truth(value));
        if (path.equals("mechanical_saw") && property.equals("flipped")) access.assign("flipped", PonderValues.truth(value));
        if (path.equals("mechanical_saw") && (property.equals("axis_along_first") || property.equals("axis_along_first_coordinate"))) {
            IBlockState state = property(tile.getWorld().getBlockState(tile.getPos()), property, value);
            program.mutate(tile, tag -> tag.setByte("KineticAxis", (byte)
                    ((com.cogwork.cogworkbridge.kinetic.KineticBlock) state.getBlock()).getAxis(state).ordinal()));
        }
        if (path.equals("stockpile_switch") && property.equals("level")) { access.assign("maximum", 5); access.assign("amount", (int) PonderValues.num(value)); }
        if (property.equals("powering") && access.hasField("lit")) access.assign("lit", PonderValues.truth(value));
        if (property.equals("open") && access.hasField("open")) access.assign("open", PonderValues.truth(value));
        if (property.equals("shape") && access.hasField("doubled")) access.assign("doubled", text.equals("double"));
        if (property.equals("powered") && access.hasField("input")) access.assign("input", PonderValues.truth(value));
        if (property.equals("powered") && access.hasField("sounding")) access.assign("sounding", PonderValues.truth(value));
        if (property.startsWith("sticky_") && tile instanceof com.cogwork.cogworkbridge.content.contraptions.chassis.ChassisTileEntity chassis) {
            String face = property.substring(7);
            net.minecraft.util.EnumFacing.Axis axis = tile.getWorld().getBlockState(tile.getPos()).getValue(com.cogwork.cogworkbridge.kinetic.KineticBlock.AXIS);
            net.minecraft.util.EnumFacing side = face.equals("top") || face.equals("bottom")
                    ? net.minecraft.util.EnumFacing.getFacingFromAxis(face.equals("top") ? net.minecraft.util.EnumFacing.AxisDirection.POSITIVE : net.minecraft.util.EnumFacing.AxisDirection.NEGATIVE, axis)
                    : net.minecraft.util.EnumFacing.valueOf(face.toUpperCase(Locale.ROOT));
            chassis.setSticky(side, PonderValues.truth(value));
        }
        if (path.contains("blaze_burner") && property.equals("heat_level")) {
            String heat = String.valueOf(value);
            access.assign("remainingFuel", heat.equals("fading") ? 100 : heat.equals("smouldering") || heat.equals("none") ? 0 : 10000);
            access.assign("specialFuel", heat.equals("seething"));
        }
        if (property.equals("assembling") && access.hasField("assembling")) access.assign("assembling", PonderValues.truth(value));
        if (property.equals("receiver") && access.hasField("receiver")) access.assign("receiver", PonderValues.truth(value));
        if (property.equals("powering") && access.hasField("output")) access.assign("output", PonderValues.truth(value));
        if (property.equals("size") && access.hasField("size")) access.assign("size", switch (String.valueOf(value)) { case "small" -> 0; case "medium" -> 1; default -> 2; });
        tile.getWorld().markBlockRangeForRenderUpdate(tile.getPos(), tile.getPos());
    }
    static Object visualValue(TileEntity tile, String property) {
        if (tile == null) return false;
        NBTTagCompound state = tile.getTileData().getCompoundTag("TutorialState");
        if (state.hasKey(property)) {
            String text = state.getString(property);
            if (text.equals("true") || text.equals("false")) return Boolean.parseBoolean(text);
            try { return Double.parseDouble(text); } catch (NumberFormatException ignored) { return new PonderValues.Symbol(text); }
        }
        return false;
    }
    static com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterDirection crafterPointing(String value) {
        // Legacy's LEFT/RIGHT are world-relative to the back of the work surface.
        return com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterDirection.valueOf(
                (value.equalsIgnoreCase("left") ? "right" : value.equalsIgnoreCase("right") ? "left" : value).toUpperCase(Locale.ROOT));
    }
    private static void copy(NBTTagCompound tag, String source, String target) { if (tag.hasKey(source)) tag.setTag(target, tag.getTag(source).copy()); }
    static BlockPos modernPos(NBTBase value) {
        if (value instanceof NBTTagIntArray array && array.getIntArray().length >= 3) { int[] p = array.getIntArray(); return new BlockPos(p[0], p[1], p[2]); }
        if (value instanceof NBTTagCompound tag) return new BlockPos(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z"));
        if (value instanceof NBTTagList list && list.tagCount() >= 3) return new BlockPos(list.getIntAt(0), list.getIntAt(1), list.getIntAt(2));
        return BlockPos.ORIGIN;
    }
    private static BlockPos nestedPos(NBTTagCompound value) { return modernPos(value.hasKey("Pos") ? value.getTag("Pos") : value); }
    private static net.minecraft.util.math.Vec3d modernVec(NBTTagCompound tag) {
        NBTTagList v = tag.getTagList("V", 6);
        return new net.minecraft.util.math.Vec3d(v.getDoubleAt(0), v.getDoubleAt(1), v.getDoubleAt(2));
    }
    /** Same circular-arc handle construction used by upstream BezierConnection. */
    private static double curveHandle(net.minecraft.util.math.Vec3d start, net.minecraft.util.math.Vec3d end,
                                      net.minecraft.util.math.Vec3d first, net.minecraft.util.math.Vec3d second) {
        var up = new net.minecraft.util.math.Vec3d(0, 1, 0);
        var cross1 = first.crossProduct(up); var cross2 = second.crossProduct(up);
        double full = Math.PI * 2;
        double angle = (Math.atan2(-second.z, -second.x) - Math.atan2(first.z, first.x) + full) % full;
        angle = Math.min(angle, full - angle);
        if (angle < 1E-5) {
            double[] intersection = intersectXZ(start, end, first, cross2);
            if (intersection != null) {
                double min = Math.min(Math.abs(intersection[0]), Math.abs(intersection[1])), max = Math.max(Math.abs(intersection[0]), Math.abs(intersection[1]));
                if (min > 1.2 && max / min > 1 && max / min < 3) return max - min;
            }
            return start.distanceTo(end) / 3;
        }
        double[] intersection = intersectXZ(start, end, cross1, cross2);
        if (intersection == null) return start.distanceTo(end) / 3;
        double length = Math.abs(intersection[1]) * 4 / 3 * Math.tan(angle / 4);
        return length < 1E-5 ? 1 : length;
    }
    private static double[] intersectXZ(net.minecraft.util.math.Vec3d first, net.minecraft.util.math.Vec3d second,
                                        net.minecraft.util.math.Vec3d a, net.minecraft.util.math.Vec3d b) {
        double determinant = a.x * b.z - a.z * b.x;
        if (Math.abs(determinant) < 1E-8) return null;
        double x = second.x - first.x, z = second.z - first.z;
        return new double[]{(x * b.z - z * b.x) / determinant, (x * a.z - z * a.x) / determinant};
    }
    static void convertNested(NBTBase value) {
        if (value instanceof NBTTagList list) for (int i = 0; i < list.tagCount(); i++) convertNested(list.get(i));
        if (!(value instanceof NBTTagCompound tag)) return;
        if (tag.hasKey("id", 8) && (tag.hasKey("count") || tag.hasKey("Count"))) {
            ItemStack stack = item(tag.getString("id"));
            if (!stack.isEmpty()) {
                stack.setCount(tag.hasKey("count") ? tag.getInteger("count") : tag.getInteger("Count"));
                NBTTagCompound legacy = stack.writeToNBT(new NBTTagCompound());
                for (String key : legacy.getKeySet()) tag.setTag(key, legacy.getTag(key).copy());
            }
        }
        if (tag.hasKey("FluidName", 8)) tag.setString("FluidName", tag.getString("FluidName").replace("create:", "").replace("minecraft:", ""));
        for (String key : new ArrayList<>(tag.getKeySet())) convertNested(tag.getTag(key));
    }
}
