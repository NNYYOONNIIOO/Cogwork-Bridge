package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.CogworkBridge;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.FMLFileResourcePack;
import net.minecraftforge.fml.common.ModContainer;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Resource pack that reads straight out of the user's upstream Create jar.
 *
 * <p>Extends {@link FMLFileResourcePack}, whose {@code FileResourcePack} base opens
 * the container's source file as a zip. That means the jar does <em>not</em> need
 * to be on the classpath or loaded as a mod - which is exactly what we need, since
 * the upstream jar must never be treated as a 1.12.2 mod.
 *
 * <p>A small allow-list is served from Cogwork Bridge's own jar instead, because
 * 1.21.1 and 1.12.2 disagree on the language file format:
 * {@code lang/*.lang} is the 1.12.2 form, upstream ships JSON.
 */
public final class TargetResourcePack extends FMLFileResourcePack {

    private static final Gson JSON = new Gson();
    private static final String PLACEMENT_INDICATOR = "textures/gui/placement_indicator.png";
    private static final String PONDER_INDICATOR = "assets/ponder/" + PLACEMENT_INDICATOR;
    private static final String PONDER_WIDGETS = "textures/gui/ponder_widgets.png";
    private static final String PONDER_WIDGETS_SOURCE = "assets/ponder/textures/gui/widgets.png";
    private static final Set<String> LOCAL_OVERRIDES = new HashSet<>(Arrays.asList(
            "lang/en_us.lang",
            "lang/zh_cn.lang"
    ));
    private static final Set<String> EXPLICIT_16X16_TEXTURES = new HashSet<>(Arrays.asList(
            "create:block/axis",
            "create:block/axis_top",
            "create:block/cogwheel_axis"
    ));
    private final Map<ResourceLocation, Integer> textureSizes = new HashMap<>();
    private final File sourceFile;
    private boolean ponderLoaded;
    private byte[] indicatorBytes;
    private byte[] ponderWidgetsBytes;

    public TargetResourcePack(ModContainer container) {
        super(container);
        sourceFile = container.getSource();
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        return isGeneratedSailModel(location)
                || isGeneratedWaterWheelModel(location)
                || isBlueprintModelAlias(location) && super.resourceExists(blueprintModelSource(location))
                || isLocalOverride(location)
                || isPlacementIndicator(location) && ponderIndicator() != null
                || isPonderWidgets(location) && ponderWidgets() != null
                || super.resourceExists(location);
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        if (isBlueprintModelAlias(location)) {
            ResourceLocation source = blueprintModelSource(location);
            return adaptModel(source, super.getInputStream(source));
        }
        if (isGeneratedSailModel(location)) {
            String path = location.getPath();
            String file = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
            String color = file.substring(0, file.length() - "_sail".length());
            String model = "{\"parent\":\"create:block/white_sail\",\"textures\":{\"0\":\"create:block/sail/canvas_"
                    + color + "\"}}";
            return new ByteArrayInputStream(model.getBytes(StandardCharsets.UTF_8));
        }
        if (isGeneratedWaterWheelModel(location)) {
            return new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
        }
        if (isLocalOverride(location)) {
            String path = "/assets/cogworkbridge/" + location.getPath();
            InputStream stream = TargetResourcePack.class.getResourceAsStream(path);
            if (stream == null) {
                throw new FileNotFoundException(location.toString());
            }
            return stream;
        }

        if (isPlacementIndicator(location)) {
            byte[] indicator = ponderIndicator();
            if (indicator != null) {
                return new ByteArrayInputStream(indicator);
            }
        }
        if (isPonderWidgets(location)) {
            byte[] widgets = ponderWidgets();
            if (widgets != null) return new ByteArrayInputStream(widgets);
        }

        InputStream stream = super.getInputStream(location);
        if (!isUpstreamModel(location)) {
            return stream;
        }
        return adaptModel(location, stream);
    }

    private synchronized byte[] ponderIndicator() {
        loadPonderResources();
        return indicatorBytes;
    }

    private synchronized byte[] ponderWidgets() {
        loadPonderResources();
        return ponderWidgetsBytes;
    }

    private void loadPonderResources() {
        if (ponderLoaded) return;
        ponderLoaded = true;
        try (ZipFile source = new ZipFile(sourceFile)) {
            var entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry nested = entries.nextElement();
                if (!nested.getName().startsWith("META-INF/jarjar/ponder")
                        || !nested.getName().endsWith(".jar")) {
                    continue;
                }
                try (ZipInputStream ponder = new ZipInputStream(source.getInputStream(nested))) {
                    ZipEntry entry;
                    while ((entry = ponder.getNextEntry()) != null) {
                        if (PONDER_INDICATOR.equals(entry.getName())) {
                            indicatorBytes = ponder.readAllBytes();
                        } else if (PONDER_WIDGETS_SOURCE.equals(entry.getName())) {
                            ponderWidgetsBytes = ponder.readAllBytes();
                        }
                        if (indicatorBytes != null && ponderWidgetsBytes != null) return;
                    }
                }
            }
        } catch (IOException exception) {
            CogworkBridge.LOGGER.warn("Could not read the upstream Ponder GUI textures", exception);
        }
    }

    private static boolean isPlacementIndicator(ResourceLocation location) {
        return "create".equals(location.getNamespace())
                && PLACEMENT_INDICATOR.equals(location.getPath());
    }

    private static boolean isPonderWidgets(ResourceLocation location) {
        return "create".equals(location.getNamespace()) && PONDER_WIDGETS.equals(location.getPath());
    }

    private InputStream adaptModel(ResourceLocation location, InputStream source) throws IOException {
        byte[] contents;
        try (InputStream input = source; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            contents = output.toByteArray();
        }

        JsonObject model;
        try (InputStreamReader reader = new InputStreamReader(
                new ByteArrayInputStream(contents), StandardCharsets.UTF_8)) {
            model = new JsonParser().parse(reader).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Invalid upstream model JSON: " + location, exception);
        }

        boolean remappedTextures = false;
        // The valve's dial panels use inverted cuboid coordinates in Create's
        // models. 1.12 bakes those panels with reversed winding, hiding them.
        if (location.getPath().contains("/fluid_valve/block_") && model.has("elements")) {
            for (JsonElement value : model.getAsJsonArray("elements")) {
                JsonObject element = value.getAsJsonObject();
                if (!element.has("from") || !element.has("to")) continue;
                JsonArray from = element.getAsJsonArray("from"), to = element.getAsJsonArray("to");
                if (from.size() != 3 || to.size() != 3) continue;
                for (int axis = 0; axis < 3; axis++) {
                    float start = from.get(axis).getAsFloat(), end = to.get(axis).getAsFloat();
                    if (start <= end) continue;
                    // Put the outward face just beyond the casing to avoid z-fighting.
                    from.set(axis, new com.google.gson.JsonPrimitive(end - .01F));
                    to.set(axis, new com.google.gson.JsonPrimitive(start + .01F));
                    remappedTextures = true;
                }
            }
        }
        if ((location.getPath().endsWith("/nixie_tube/block.json")
                || location.getPath().endsWith("/display_link/block.json")
                || location.getPath().endsWith("/display_link/block_powered.json")
                || location.getPath().contains("/stock_link/block_"))
                && model.has("children") && model.get("children").isJsonObject()) {
            JsonArray elements = new JsonArray();
            for (Map.Entry<String, JsonElement> child : model.getAsJsonObject("children").entrySet()) {
                JsonObject part = child.getValue().getAsJsonObject();
                appendModelPart(model, elements, part, "part_" + child.getKey() + "_");
            }
            model.add("elements", elements);
            model.remove("children");
            model.remove("loader");
            remappedTextures = true;
        }
        if (location.getPath().endsWith("/item/wand_of_symmetry/item.json")) {
            JsonArray elements = model.getAsJsonArray("elements");
            for (String partName : new String[]{"bits", "core", "core_glow"}) {
                ResourceLocation partLocation = new ResourceLocation("create",
                        "models/item/wand_of_symmetry/" + partName + ".json");
                try (InputStream partStream = super.getInputStream(partLocation);
                     InputStreamReader reader = new InputStreamReader(partStream, StandardCharsets.UTF_8)) {
                    appendModelPart(model, elements, new JsonParser().parse(reader).getAsJsonObject(),
                            "wand_" + partName + "_");
                }
            }
            remappedTextures = true;
        }
        if (location.getPath().endsWith("/item/handheld_worldshaper/item.json")) {
            JsonArray elements = model.getAsJsonArray("elements");
            for (String partName : new String[]{"core", "core_glow", "accelerator"}) {
                ResourceLocation partLocation = new ResourceLocation("create",
                        "models/item/handheld_worldshaper/" + partName + ".json");
                try (InputStream partStream = super.getInputStream(partLocation);
                     InputStreamReader reader = new InputStreamReader(partStream, StandardCharsets.UTF_8)) {
                    appendModelPart(model, elements, new JsonParser().parse(reader).getAsJsonObject(),
                            "worldshaper_" + partName + "_");
                }
            }
            remappedTextures = true;
        }
        if (location.getPath().endsWith("/item/potato_cannon/item.json")) {
            ResourceLocation cogLocation = new ResourceLocation("create", "models/item/potato_cannon/cog.json");
            try (InputStream partStream = super.getInputStream(cogLocation);
                 InputStreamReader reader = new InputStreamReader(partStream, StandardCharsets.UTF_8)) {
                appendModelPart(model, model.getAsJsonArray("elements"),
                        new JsonParser().parse(reader).getAsJsonObject(), "cannon_cog_");
            }
            remappedTextures = true;
        }
        remappedTextures |= remapModernVanillaParent(model);
        remappedTextures |= remapModernVanillaTextures(model);
        if (!model.has("texture_size")) {
            return new ByteArrayInputStream(remappedTextures
                    ? JSON.toJson(model).getBytes(StandardCharsets.UTF_8) : contents);
        }

        try {
            JsonArray textureSize = model.getAsJsonArray("texture_size");
            if (textureSize.size() != 2) {
                throw new IllegalArgumentException("texture_size must have two entries");
            }
            positiveSize(textureSize.get(0));
            positiveSize(textureSize.get(1));
            // These upstream models already store vanilla's 0..16 UVs even
            // though their source textures and Blockbench metadata are 32x32.
            // Scaling them again selects the wrong quarter of the texture.
            String path = location.getPath();
            if (!path.contains("/fluid_pipe/") && !path.contains("/mechanical_pump/")
                    && !path.contains("/funnel/") && !path.contains("/toolbox/")
                    && !path.contains("/item/potato_cannon/") && !path.contains("/block/track/")) {
                normalizeFaceUvs(location, model);
            }
            model.remove("texture_size");
        } catch (RuntimeException | IOException exception) {
            throw new IOException("Invalid texture_size in upstream model " + location, exception);
        }

        return new ByteArrayInputStream(JSON.toJson(model).getBytes(StandardCharsets.UTF_8));
    }

    /** Merge modern composite/partial geometry while preserving each part's texture bindings. */
    private static void appendModelPart(JsonObject model, JsonArray elements, JsonObject part, String prefix) {
        JsonObject textures = model.has("textures") ? model.getAsJsonObject("textures") : new JsonObject();
        model.add("textures", textures);
        JsonObject partTextures = part.has("textures") ? part.getAsJsonObject("textures") : new JsonObject();
        for (Map.Entry<String, JsonElement> texture : partTextures.entrySet()) {
            String value = texture.getValue().getAsString();
            if (value.startsWith("#") && partTextures.has(value.substring(1)))
                value = "#" + prefix + value.substring(1);
            textures.addProperty(prefix + texture.getKey(), value);
            if (texture.getKey().equals("particle") && !textures.has("particle"))
                textures.addProperty("particle", value);
        }
        if (!part.has("elements")) return;
        for (JsonElement element : part.getAsJsonArray("elements")) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            if (faces != null) for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
                JsonObject face = entry.getValue().getAsJsonObject();
                String texture = face.get("texture").getAsString();
                if (texture.startsWith("#") && partTextures.has(texture.substring(1)))
                    face.addProperty("texture", "#" + prefix + texture.substring(1));
            }
            elements.add(element);
        }
    }

    private static boolean remapModernVanillaParent(JsonObject model) {
        if (!model.has("parent")) return false;
        String parent = model.get("parent").getAsString();
        String mapped = switch (parent) {
            case "minecraft:block/slab" -> "minecraft:block/half_slab";
            case "minecraft:block/slab_top" -> "minecraft:block/upper_slab";
            case "minecraft:block/cube_column_horizontal" -> "minecraft:block/cube_column";
            case "minecraft:block/template_wall_post" -> "minecraft:block/wall_post";
            case "minecraft:block/template_wall_side" -> "minecraft:block/wall_side";
            case "minecraft:block/template_glass_pane_post" -> "minecraft:block/pane_post";
            case "minecraft:block/template_glass_pane_side" -> "minecraft:block/pane_side";
            case "minecraft:block/template_glass_pane_side_alt" -> "minecraft:block/pane_side_alt";
            case "minecraft:block/template_glass_pane_noside" -> "minecraft:block/pane_noside";
            case "minecraft:block/template_glass_pane_noside_alt" -> "minecraft:block/pane_noside_alt";
            default -> parent;
        };
        if (mapped.equals(parent)) return false;
        model.addProperty("parent", mapped);
        return true;
    }

    private static boolean remapModernVanillaTextures(JsonObject model) {
        if (!model.has("textures") || !model.get("textures").isJsonObject()) {
            return false;
        }
        JsonObject textures = model.getAsJsonObject("textures");
        boolean changed = false;
        for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                continue;
            }
            String value = entry.getValue().getAsString();
            String replacement = switch (value) {
                case "minecraft:block/glass_pane_top", "block/glass_pane_top" -> "minecraft:blocks/glass_pane_top";
                case "minecraft:block/iron_bars", "block/iron_bars" -> "minecraft:blocks/iron_bars";
                case "minecraft:block/oak_planks", "block/oak_planks" -> "minecraft:blocks/planks_oak";
                case "minecraft:block/spruce_planks", "block/spruce_planks" -> "minecraft:blocks/planks_spruce";
                case "minecraft:block/birch_planks", "block/birch_planks" -> "minecraft:blocks/planks_birch";
                case "minecraft:block/jungle_planks", "block/jungle_planks" -> "minecraft:blocks/planks_jungle";
                case "minecraft:block/acacia_planks", "block/acacia_planks" -> "minecraft:blocks/planks_acacia";
                case "minecraft:block/dark_oak_planks", "block/dark_oak_planks" -> "minecraft:blocks/planks_big_oak";
                case "minecraft:block/crimson_planks", "block/crimson_planks" -> "farmers_future_delight:block/crimson_planks";
                case "minecraft:block/warped_planks", "block/warped_planks" -> "farmers_future_delight:block/warped_planks";
                case "minecraft:block/mangrove_planks", "block/mangrove_planks" -> "create:block/palettes/mangrove_window";
                case "minecraft:block/cherry_planks", "block/cherry_planks" -> "create:block/palettes/cherry_window";
                case "minecraft:block/bamboo_planks", "block/bamboo_planks" -> "create:block/palettes/bamboo_window";
                case "minecraft:block/spruce_log", "block/spruce_log" -> "minecraft:blocks/log_spruce";
                case "minecraft:block/stripped_oak_log", "block/stripped_oak_log" -> "farmers_future_delight:block/stripped_oak_log";
                case "minecraft:block/spruce_log_top", "block/spruce_log_top" ->
                        "minecraft:blocks/log_spruce_top";
                case "minecraft:block/stripped_spruce_log_top", "block/stripped_spruce_log_top" ->
                        "minecraft:blocks/log_spruce_top";
                case "minecraft:block/stripped_spruce_log", "block/stripped_spruce_log" ->
                        "minecraft:blocks/log_spruce";
                case "minecraft:block/stripped_dark_oak_log_top", "block/stripped_dark_oak_log_top" ->
                        "minecraft:blocks/log_big_oak_top";
                case "minecraft:block/stripped_dark_oak_log", "block/stripped_dark_oak_log" ->
                        "minecraft:blocks/log_big_oak";
                case "minecraft:block/dark_oak_log", "block/dark_oak_log" ->
                        "minecraft:blocks/log_big_oak";
                case "minecraft:block/dark_oak_log_top", "block/dark_oak_log_top" ->
                        "minecraft:blocks/log_big_oak_top";
                case "minecraft:block/anvil", "block/anvil" ->
                        "minecraft:blocks/anvil_base";
                case "minecraft:block/redstone_torch", "block/redstone_torch" ->
                        "minecraft:blocks/redstone_torch_on";
                case "minecraft:block/redstone_torch_off", "block/redstone_torch_off" ->
                        "minecraft:blocks/redstone_torch_off";
                case "minecraft:block/cobblestone", "block/cobblestone" ->
                        "minecraft:blocks/cobblestone";
                case "minecraft:block/smooth_stone", "block/smooth_stone" ->
                        "minecraft:blocks/stone_slab_top";
                case "minecraft:block/smooth_stone_slab_side", "block/smooth_stone_slab_side" ->
                        "minecraft:blocks/stone_slab_side";
                case "minecraft:block/lever", "block/lever" ->
                        "minecraft:blocks/lever";
                case "minecraft:blocks/planks_dark_oak" ->
                        "minecraft:blocks/planks_big_oak";
                case "minecraft:block/stonecutter_bottom", "block/stonecutter_bottom" ->
                        "minecraft:blocks/stone_slab_top";
                case "minecraft:block/stone", "block/stone" ->
                        "minecraft:blocks/stone";
                case "minecraft:block/stonecutter_saw", "block/stonecutter_saw" ->
                        "create:block/saw_reversed";
                case "minecraft:block/polished_andesite", "block/polished_andesite" ->
                        "minecraft:blocks/stone_andesite_smooth";
                case "minecraft:block/obsidian", "block/obsidian" ->
                        "minecraft:blocks/obsidian";
                case "minecraft:block/black_stained_glass", "block/black_stained_glass" ->
                        "minecraft:blocks/glass_black";
                case "minecraft:block/white_stained_glass", "block/white_stained_glass" ->
                        "minecraft:blocks/glass_white";
                case "minecraft:block/light_blue_stained_glass", "block/light_blue_stained_glass" ->
                        "minecraft:blocks/glass_light_blue";
                case "minecraft:block/white_concrete_powder", "block/white_concrete_powder" ->
                        "minecraft:blocks/concrete_powder_white";
                case "minecraft:block/light_blue_concrete_powder", "block/light_blue_concrete_powder" ->
                        "minecraft:blocks/concrete_powder_light_blue";
                case "minecraft:block/copper_block", "block/copper_block" ->
                        "create:block/copper_casing";
                case "minecraft:block/piston_bottom", "block/piston_bottom" ->
                        "minecraft:blocks/piston_bottom";
                case "minecraft:block/piston_inner", "block/piston_inner" ->
                        "minecraft:blocks/piston_inner";
                case "minecraft:block/piston_side", "block/piston_side" ->
                        "minecraft:blocks/piston_side";
                case "minecraft:block/piston_top", "block/piston_top" ->
                        "minecraft:blocks/piston_top_normal";
                case "minecraft:block/piston_top_sticky", "block/piston_top_sticky" ->
                        "minecraft:blocks/piston_top_sticky";
                case "minecraft:block/rail", "block/rail" -> "minecraft:blocks/rail_normal";
                case "minecraft:block/powered_rail", "block/powered_rail" -> "minecraft:blocks/rail_golden";
                case "minecraft:block/powered_rail_on", "block/powered_rail_on" -> "minecraft:blocks/rail_golden_powered";
                case "minecraft:block/detector_rail", "block/detector_rail" -> "minecraft:blocks/rail_detector";
                case "minecraft:block/detector_rail_on", "block/detector_rail_on" -> "minecraft:blocks/rail_detector_powered";
                case "minecraft:block/activator_rail", "block/activator_rail" -> "minecraft:blocks/rail_activator";
                case "minecraft:block/activator_rail_on", "block/activator_rail_on" -> "minecraft:blocks/rail_activator_powered";
                default -> null;
            };
            if (replacement != null) {
                textures.addProperty(entry.getKey(), replacement);
                changed = true;
            }
        }
        return changed;
    }

    private static double positiveSize(JsonElement element) {
        double value = element.getAsDouble();
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw new IllegalArgumentException("texture_size entries must be positive numbers");
        }
        return value;
    }

    private void normalizeFaceUvs(ResourceLocation modelLocation, JsonObject model) throws IOException {
        JsonArray elements = model.getAsJsonArray("elements");
        if (elements == null) {
            return;
        }
        JsonObject textures = model.has("textures")
                ? model.getAsJsonObject("textures")
                : new JsonObject();
        for (JsonElement element : elements) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            if (faces == null) {
                continue;
            }
            for (Map.Entry<String, JsonElement> faceEntry : faces.entrySet()) {
                JsonObject face = faceEntry.getValue().getAsJsonObject();
                JsonArray uv = face.getAsJsonArray("uv");
                if (uv == null) {
                    continue;
                }
                if (uv.size() != 4) {
                    throw new IllegalArgumentException("face UV must have four entries");
                }
                String texture = resolveTexture(face.get("texture").getAsString(), textures);
                int textureSize = rawTextureIs16x16(texture)
                        ? 16
                        : texture.startsWith("#") ? 16 : textureSize(textureLocation(texture));
                double uScale = 16.0D / textureSize;
                double vScale = uScale;
                JsonArray normalized = new JsonArray();
                normalized.add(uv.get(0).getAsDouble() * uScale);
                normalized.add(uv.get(1).getAsDouble() * vScale);
                normalized.add(uv.get(2).getAsDouble() * uScale);
                normalized.add(uv.get(3).getAsDouble() * vScale);
                face.add("uv", normalized);
            }
        }
    }

    private static String resolveTexture(String reference, JsonObject textures) {
        Set<String> visited = new HashSet<>();
        while (reference.startsWith("#") && visited.add(reference)) {
            JsonElement target = textures.get(reference.substring(1));
            if (target == null) {
                break;
            }
            reference = target.getAsString();
        }
        return reference;
    }

    private static boolean rawTextureIs16x16(String texture) {
        return EXPLICIT_16X16_TEXTURES.contains(texture);
    }

    private int textureSize(ResourceLocation texture) throws IOException {
        Integer cached = textureSizes.get(texture);
        if (cached != null) {
            return cached;
        }

        ResourceLocation metadata = new ResourceLocation(
                texture.getNamespace(), texture.getPath() + ".mcmeta");
        if (super.resourceExists(metadata)) {
            try (InputStream input = super.getInputStream(metadata);
                 InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
                JsonObject textureMeta = root.getAsJsonObject("texture");
                if (textureMeta != null && textureMeta.has("width")) {
                    int width = textureMeta.get("width").getAsInt();
                    if (width > 0) {
                        textureSizes.put(texture, width);
                        return width;
                    }
                }
            } catch (RuntimeException exception) {
                throw new IOException("Invalid texture metadata: " + metadata, exception);
            }
        }

        ResourceLocation png = new ResourceLocation(
                texture.getNamespace(), texture.getPath() + ".png");
        if (!super.resourceExists(png)) {
            return 16;
        }
        try (InputStream input = super.getInputStream(png)) {
            int width = inspectPngWidth(input);
            textureSizes.put(texture, width);
            return width;
        }
    }

    private static int inspectPngWidth(InputStream input) throws IOException {
        byte[] header = new byte[24];
        int offset = 0;
        while (offset < header.length) {
            int read = input.read(header, offset, header.length - offset);
            if (read < 0) {
                throw new IOException("Truncated PNG");
            }
            offset += read;
        }
        if ((header[0] & 0xFF) != 0x89 || header[1] != 'P' || header[2] != 'N' || header[3] != 'G') {
            throw new IOException("Invalid PNG");
        }
        int width = readBigEndianInt(header, 16);
        int height = readBigEndianInt(header, 20);
        if (width <= 0 || width != height) {
            throw new IOException("Only square block textures are supported");
        }
        return width;
    }

    private static int readBigEndianInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static ResourceLocation textureLocation(String texture) {
        if (texture.contains(":")) {
            String namespace = texture.substring(0, texture.indexOf(':'));
            String path = texture.substring(texture.indexOf(':') + 1);
            if (!path.startsWith("textures/")) {
                path = "textures/" + path;
            }
            return new ResourceLocation(namespace, path);
        }
        return new ResourceLocation("minecraft", "textures/" + texture);
    }

    private static boolean isUpstreamModel(ResourceLocation location) {
        String path = location.getPath();
        return "create".equals(location.getNamespace())
                && (path.startsWith("models/")
                    || path.startsWith("block/")
                    || path.startsWith("item/"))
                && path.endsWith(".json");
    }

    /** 1.12 adds block/ to blockstate model paths; Create stores these models under entity/. */
    private static boolean isBlueprintModelAlias(ResourceLocation location) {
        if (!"create".equals(location.getNamespace())) return false;
        String path = location.getPath();
        return path.startsWith("models/block/entity/minecart_coupling/")
                || path.equals("models/block/entity/crafting_blueprint_small.json")
                || path.equals("models/block/entity/crafting_blueprint_medium.json")
                || path.equals("models/block/entity/crafting_blueprint_large.json");
    }

    private static ResourceLocation blueprintModelSource(ResourceLocation location) {
        return new ResourceLocation("create", location.getPath().replace("models/block/entity/", "models/entity/"));
    }

    private static boolean isLocalOverride(ResourceLocation location) {
        return "create".equals(location.getNamespace())
                && LOCAL_OVERRIDES.contains(location.getPath());
    }

    private static boolean isGeneratedSailModel(ResourceLocation location) {
        if (!"create".equals(location.getNamespace())) return false;
        String path = location.getPath();
        if (!path.startsWith("models/") || !path.endsWith(".json")) return false;
        String file = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
        if (!file.endsWith("_sail")) return false;
        return switch (file.substring(0, file.length() - "_sail".length())) {
            case "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
                    "cyan", "purple", "blue", "brown", "green", "red", "black" -> true;
            default -> false;
        };
    }

    private static boolean isGeneratedWaterWheelModel(ResourceLocation location) {
        if (!"create".equals(location.getNamespace())) return false;
        String path = location.getPath();
        return path.startsWith("models/block/water_wheel/material_") && path.endsWith(".json")
                || path.equals("models/item/water_wheel.json");
    }

}
