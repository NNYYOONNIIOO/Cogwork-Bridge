package com.cogwork.cogworkbridge.compat.ponder;

import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.PonderElementBase;
import net.createmod.ponder.render.SceneProjection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

/** A playback-time failure stays visible instead of silently skipping part of the lesson. */
final class PonderDiagnostics extends PonderElementBase implements PonderOverlayElement {
    private final PonderProgram program;
    PonderDiagnostics(PonderProgram program) { this.program = program; }
    @Override public void tick(PonderScene scene) {
        // Elements follow the scene lifetime without extending its progress bar.
        var nodes = new java.util.ArrayList<net.minecraft.util.math.BlockPos>();
        for (var tile : scene.getWorld().loadedTileEntityList)
            if (tile instanceof com.cogwork.cogworkbridge.content.fluids.FluidPipeTileEntity
                    || tile instanceof com.cogwork.cogworkbridge.content.fluids.MechanicalPumpTileEntity
                    || tile instanceof com.cogwork.cogworkbridge.content.fluids.FluidValveTileEntity) nodes.add(tile.getPos());
        if (!nodes.isEmpty()) try {
            com.cogwork.cogworkbridge.content.fluids.FluidTransferManager.renderTutorialFlows(scene.getWorld(), nodes);
        } catch (RuntimeException failure) { program.report("fluid flow", failure); }
    }
    @Override public void render(PonderScene scene, int mouseX, int mouseY, float partial) {
        if (program.unsupported.isEmpty()) return;
        var viewport = SceneProjection.viewport();
        var font = Minecraft.getMinecraft().fontRenderer;
        String text = PonderText.get("cogworkbridge.ponder.incomplete", "Some demonstration operations are unavailable.")
                + " [" + String.join(", ", program.unsupported) + "]";
        int width = Math.max(40, Math.min(300, viewport.width() - 16));
        var lines = font.listFormattedStringToWidth(text, width - 12);
        int height = lines.size() * 10 + 10;
        int x = viewport.x() + (viewport.width() - width) / 2;
        int y = Math.max(viewport.y() + 6, viewport.y() + viewport.height() - height - 8);
        Gui.drawRect(x, y, x + width, y + height, 0xe02a0e0e);
        for (int i = 0; i < lines.size(); i++) font.drawString(lines.get(i), x + 6, y + 5 + i * 10, 0xffffa0a0);
    }
}
