package com.cogwork.cogworkbridge.compat.ponder;

import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import java.util.*;

/** Uses the original Create icon sheet, with coordinates emitted from AllIcons.java. */
final class PonderIcons {
    private static final Map<String, int[]> COORDS = new HashMap<>();
    static ScreenElement icon(String id) {
        if (COORDS.isEmpty()) {
            try (var input = PonderIcons.class.getResourceAsStream("/assets/cogworkbridge/ponder/upstream-icons.json")) {
                var json = new com.google.gson.JsonParser().parse(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                json.entrySet().forEach(e -> COORDS.put(e.getKey(), new int[]{e.getValue().getAsJsonArray().get(0).getAsInt(), e.getValue().getAsJsonArray().get(1).getAsInt()}));
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }
        int[] xy = COORDS.get(id);
        if (xy == null) throw new IllegalArgumentException("Unknown Create icon " + id);
        return (x, y) -> {
            Minecraft.getMinecraft().getTextureManager().bindTexture(new ResourceLocation("create", "textures/gui/icons.png"));
            GlStateManager.color(1, 1, 1, 1);
            Gui.drawModalRectWithCustomSizedTexture(x, y, xy[0] * 16, xy[1] * 16, 16, 16, 256, 256);
        };
    }
}
