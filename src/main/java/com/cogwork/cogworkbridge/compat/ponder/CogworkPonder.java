package com.cogwork.cogworkbridge.compat.ponder;

import com.cogwork.cogworkbridge.Reference;
import com.google.gson.*;
import net.createmod.ponder.api.registration.*;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.api.structure.PonderStructureProviders;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Client entry point; referenced only after Loader has detected the optional runtime. */
public final class CogworkPonder implements PonderPlugin {
    private final JsonObject data;
    public CogworkPonder() {
        try (InputStream stream = CogworkPonder.class.getResourceAsStream(
                "/assets/cogworkbridge/ponder/upstream-scenes.json")) {
            if (stream == null) throw new IOException("Missing compiled Ponder programs");
            data = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) { throw new IllegalStateException("Cannot read Ponder programs", e); }
    }
    public static void initialize() {
        CogworkPonder plugin = new CogworkPonder();
        PonderStructureProviders.register(new UpstreamPonderStructures(plugin.data));
        PonderIndex.addPlugin(plugin);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new PonderModels());
    }
    @Override public String getModId() { return Reference.MOD_ID; }
    @Override public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        JsonObject programs = data.getAsJsonObject("programs");
        Map<String, PonderStoryBoard> boards = new LinkedHashMap<>();
        Map<String, String> sceneIds = new LinkedHashMap<>();
        Map<String, Set<ResourceLocation>> subjects = new LinkedHashMap<>();
        Set<String> usedIds = new HashSet<>();
        for (JsonElement entry : data.getAsJsonArray("catalog")) {
            JsonObject row = entry.getAsJsonObject();
            String name = row.get("owner").getAsString() + "." + row.get("method").getAsString();
            JsonObject program = programs.getAsJsonObject(name);
            ResourceLocation schematic = new ResourceLocation(Reference.MOD_ID, row.get("structure").getAsString());
            ResourceLocation[] tags = new ResourceLocation[row.getAsJsonArray("tags").size()];
            for (int i = 0; i < tags.length; i++) tags[i] = new ResourceLocation(Reference.MOD_ID, row.getAsJsonArray("tags").get(i).getAsString());
            String key = name + "@" + schematic;
            String declaredId = sceneIds.computeIfAbsent(key, ignored -> {
                String id = program.get("id").getAsString();
                if (!usedIds.add(id)) {
                    id += "/" + schematic.getPath();
                    if (!usedIds.add(id)) throw new IllegalArgumentException("Conflicting Ponder scene " + key);
                }
                return id;
            });
            // Ponder recognises component aliases by the same storyboard instance and structure.
            PonderStoryBoard board = boards.computeIfAbsent(key, ignored ->
                    (scene, util) -> new PonderProgram(scene, util, name, declaredId).build(program));
            Set<ResourceLocation> registered = subjects.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
            for (JsonElement component : row.getAsJsonArray("components"))
                for (String id : PonderData.components(component.getAsString())) {
                    ItemStack item = PonderData.item(id);
                    if (item.isEmpty() || !registered.add(item.getItem().getRegistryName())) continue;
                    helper.addStoryBoard(item.getItem().getRegistryName(), schematic, board, tags).identifiedBy(declaredId);
                }
        }
    }
    @Override public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        if (!data.has("tags")) return;
        for (JsonElement entry : data.getAsJsonArray("tags")) {
            JsonObject row = entry.getAsJsonObject();
            String id = row.get("id").getAsString();
            ResourceLocation key = new ResourceLocation(Reference.MOD_ID, id);
            TagBuilder tag = helper.registerTag(key)
                    .title(PonderText.get("create.ponder.tag." + id, row.get("title").getAsString()))
                    .description(PonderText.get("create.ponder.tag." + id + ".description", row.get("description").getAsString()));
            ItemStack icon = PonderData.item(row.get("icon").getAsString());
            if (!icon.isEmpty()) tag.item(icon, true, false);
            if (row.get("index").getAsBoolean()) tag.addToIndex();
            tag.register();
            for (JsonElement component : row.getAsJsonArray("components"))
                for (String path : PonderData.components(component.getAsString())) {
                    ItemStack item = PonderData.item(path);
                    if (!item.isEmpty()) helper.addTagToComponent(item.getItem().getRegistryName(), key);
                }
        }
    }
    @Override public void registerSharedText(SharedTextRegistrationHelper helper) {
        PonderText.shared().forEach((key, value) -> helper.registerSharedText(key, value));
    }
}
