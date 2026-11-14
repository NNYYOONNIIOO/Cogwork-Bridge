package com.cogwork.cogworkbridge.compat.ponder;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads translations from the user-installed Create resource pack, including its Chinese text. */
final class PonderText {
    private static String language = "";
    private static final Map<String, String> TEXT = new HashMap<>();
    static String get(String key, String fallback) {
        if (net.minecraft.client.resources.I18n.hasKey(key)) return net.minecraft.client.resources.I18n.format(key);
        load();
        return TEXT.getOrDefault(key, fallback);
    }
    static Map<String, String> shared() {
        load();
        Map<String, String> shared = new LinkedHashMap<>();
        TEXT.forEach((key, value) -> {
            if (key.startsWith("create.ponder.shared.")) shared.put(key.substring(21), value);
        });
        return shared;
    }
    static void invalidate() { language = ""; TEXT.clear(); }
    private static void load() {
        String current = Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode();
        if (current.equals(language)) return;
        language = current; TEXT.clear();
        read("en_us");
        if (!current.equals("en_us")) read(current);
    }
    private static void read(String lang) {
        try (InputStream stream = Minecraft.getMinecraft().getResourceManager().getResource(
                new ResourceLocation("create", "lang/" + lang + ".json")).getInputStream()) {
            JsonObject json = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            json.entrySet().forEach(e -> { if (e.getValue().isJsonPrimitive()) TEXT.put(e.getKey(), e.getValue().getAsString()); });
        } catch (IOException ignored) { /* English scene source remains available if a pack has no translation. */ }
    }
}
