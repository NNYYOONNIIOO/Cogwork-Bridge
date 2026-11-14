package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.schematics.*;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class SchematicMachineScreen extends GuiContainer {
    private static final ResourceLocation SCHEMATICS = new ResourceLocation("create", "textures/gui/schematics.png");
    private static final ResourceLocation CANNON = new ResourceLocation("create", "textures/gui/schematics_2.png");
    private static final ResourceLocation INVENTORY = new ResourceLocation("create", "textures/gui/player_inventory.png");
    private final SchematicContainer container;
    private final List<Path> files = new ArrayList<>();
    private int selected;
    private boolean settingsShown;
    public SchematicMachineScreen(SchematicContainer container) {
        super(container); this.container = container; xSize = 214; ySize = container.cannon ? 243 : 197;
    }
    @Override public void initGui() {
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        super.initGui(); buttonList.clear();
        if (container.cannon) {
            buttonList.add(new CreateIconButton(0, guiLeft + 75, guiTop + 85, 0, 5));
            buttonList.add(new CreateIconButton(1, guiLeft + 93, guiTop + 85, 1, 5));
            buttonList.add(new CreateIconButton(2, guiLeft + 111, guiTop + 85, 2, 5));
            buttonList.add(new CreateIconButton(9, guiLeft + 8, guiTop + 111, 3, 5));
            for (int i = 0; i < 4; i++) buttonList.add(new CreateIconButton(3 + i, guiLeft + 33 + i * 18, guiTop + 111, i, 2));
            buttonList.add(new CreateIconButton(7, guiLeft + 111, guiTop + 111, 1, 3));
            buttonList.add(new CreateIconButton(8, guiLeft + 135, guiTop + 111, 2, 3));
            buttonList.add(new CreateIconButton(10, guiLeft + 180, guiTop + 111, 0, 1));
        } else {
            refresh();
            buttonList.add(new CreateIconButton(11, guiLeft + 44, guiTop + 58, 0, 1));
            buttonList.add(new CreateIconButton(12, guiLeft + 20, guiTop + 23, 2, 1));
            buttonList.add(new CreateIconButton(13, guiLeft + 206, guiTop + 23, 3, 1));
        }
        updateButtons();
    }
    @Override public void onGuiClosed() {
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        super.onGuiClosed();
    }
    private void refresh() {
        files.clear();
        try {
            Files.createDirectories(SchematicClient.directory());
            try (java.util.stream.Stream<Path> stream = Files.list(SchematicClient.directory())) {
                stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".nbt"))
                        .sorted().limit(1000).forEach(files::add);
            }
        } catch (IOException ignored) { }
        selected = Math.max(0, Math.min(selected, files.size() - 1));
    }
    private void updateButtons() {
        if (!container.cannon || !(container.tile instanceof SchematicannonTile tile)) return;
        for (GuiButton button : buttonList) {
            if (button.id >= 3 && button.id <= 8) button.visible = settingsShown;
            if (button instanceof CreateIconButton icon) {
                icon.setSelected(button.id == 0 && tile.isRunning() || button.id == 1 && !tile.isRunning()
                        || button.id >= 3 && button.id <= 6 && tile.replaceMode() == button.id - 3
                        || button.id == 7 && tile.skipMissing() || button.id == 8 && tile.replaceBlockEntities()
                        || button.id == 9 && settingsShown);
            }
        }
    }
    private void send(int action) { ModNetwork.CHANNEL.sendToServer(new SchematicActionPacket(action, container.windowId, 0)); }
    @Override protected void actionPerformed(GuiButton button) {
        if (container.cannon) {
            switch (button.id) {
                case 0 -> send(20);
                case 1 -> send(21);
                case 2 -> send(11);
                case 3, 4, 5, 6 -> send(22 + button.id - 3);
                case 7 -> send(13);
                case 8 -> send(14);
                case 9 -> { settingsShown = !settingsShown; updateButtons(); }
                case 10 -> mc.player.closeScreen();
                default -> { }
            }
            return;
        }
        if (button.id == 13) { refresh(); return; }
        if (button.id == 12) {
            try {
                Files.createDirectories(SchematicClient.directory());
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(SchematicClient.directory().toFile());
            } catch (IOException | UnsupportedOperationException ignored) { }
            return;
        }
        if (button.id == 11 && !files.isEmpty() && container.tile.items().getStackInSlot(0).getItem() == com.cogwork.cogworkbridge.registry.AllItems.EMPTY_SCHEMATIC
                && container.tile.items().getStackInSlot(1).isEmpty()) {
            Path path = files.get(selected);
            try {
                byte[] data = SchematicData.readFile(path);
                ModNetwork.CHANNEL.sendToServer(SchematicUploadPacket.start(container.windowId, container.pos, path.getFileName().toString(), data.length));
                for (int offset = 0; offset < data.length; offset += SchematicData.CHUNK)
                    ModNetwork.CHANNEL.sendToServer(SchematicUploadPacket.chunk(container.windowId, container.pos, offset,
                            Arrays.copyOfRange(data, offset, Math.min(data.length, offset + SchematicData.CHUNK))));
                ModNetwork.CHANNEL.sendToServer(SchematicUploadPacket.finish(container.windowId, container.pos));
            } catch (IOException ex) { mc.player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("cogworkbridge.schematic.invalid"), true); }
        }
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput(); int wheel = Mouse.getEventDWheel();
        int mouseX = Mouse.getEventX() * width / mc.displayWidth, mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (!container.cannon && wheel != 0 && !files.isEmpty() && mouseX >= guiLeft + 45 && mouseX < guiLeft + 184
                && mouseY >= guiTop + 21 && mouseY < guiTop + 41)
            selected = Math.floorMod(selected + (wheel > 0 ? -1 : 1), files.size());
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (!container.cannon && !files.isEmpty() && x >= guiLeft + 45 && x < guiLeft + 184 && y >= guiTop + 21 && y < guiTop + 41)
            selected = Math.floorMod(selected + (button == 1 ? -1 : 1), files.size());
        super.mouseClicked(x, y, button);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(container.cannon ? CANNON : SCHEMATICS);
        if (container.cannon) {
            drawTexturedModalRect(guiLeft, guiTop, 0, 77, 213, 42);
            drawTexturedModalRect(guiLeft, guiTop + 42, 0, 119, 213, 99);
            if (container.tile instanceof SchematicannonTile tile && tile.total() > 0) {
                int progress = Math.min(114, (int) (114L * tile.progress() / tile.total()));
                drawTexturedModalRect(guiLeft + 44, guiTop + 64, 76, 239, progress, 16);
            }
            if (container.tile instanceof SchematicannonTile tile) {
                int fuel = Math.min(47, (int) (47L * tile.fuel() / 400));
                drawTexturedModalRect(guiLeft + 36, guiTop + 19, 28, 222, fuel, 16);
            }
        } else drawTexturedModalRect(guiLeft, guiTop, 10, 139, 214, 85);
        mc.getTextureManager().bindTexture(INVENTORY);
        if (container.cannon) drawTexturedModalRect(guiLeft + 29, guiTop + 143, 0, 0, 176, 100);
        else drawTexturedModalRect(guiLeft + 30, guiTop + 89, 0, 0, 176, 108);
    }
    @Override protected void drawGuiContainerForegroundLayer(int x, int y) {
        updateButtons();
        String title = I18n.format(container.cannon ? "cogworkbridge.schematic.cannon" : "cogworkbridge.schematic.table");
        fontRenderer.drawString(title, (214 - fontRenderer.getStringWidth(title)) / 2, 5, 0x505050);
        if (!container.cannon) {
            String file = files.isEmpty() ? I18n.format("cogworkbridge.schematic.no_files") : files.get(selected).getFileName().toString();
            fontRenderer.drawString(fontRenderer.trimStringToWidth(file, 132), 50, 28, 0xFFFFFF);
        } else if (container.tile instanceof SchematicannonTile tile) {
            String status = I18n.format("cogworkbridge.schematic.status." + tile.status());
            fontRenderer.drawString(fontRenderer.trimStringToWidth(status, 170), 22, 45, 0x505050);
        }
    }
    private String tooltip(GuiButton button) {
        return switch (button.id) {
            case 0 -> "cogworkbridge.schematic.start";
            case 1 -> "cogworkbridge.schematic.pause";
            case 2 -> "cogworkbridge.schematic.stop";
            case 3, 4, 5, 6 -> "cogworkbridge.schematic.replace." + (button.id - 3);
            case 7 -> "cogworkbridge.schematic.skip_off";
            case 8 -> "cogworkbridge.schematic.replace_tiles_off";
            case 9 -> "cogworkbridge.schematic.settings";
            case 10 -> "gui.done";
            case 11 -> "cogworkbridge.schematic.write";
            case 12 -> "cogworkbridge.schematic.open_folder";
            case 13 -> "cogworkbridge.schematic.refresh";
            default -> "";
        };
    }
    @Override public void drawScreen(int x, int y, float partial) {
        drawDefaultBackground(); super.drawScreen(x, y, partial); renderHoveredToolTip(x, y);
        for (GuiButton button : buttonList) if (button.visible && button.isMouseOver()) {
            drawHoveringText(I18n.format(tooltip(button)), x, y); break;
        }
    }
}
