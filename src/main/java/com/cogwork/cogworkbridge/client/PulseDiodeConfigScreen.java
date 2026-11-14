package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.redstone.PulseDiodeConfigPacket;
import com.cogwork.cogworkbridge.content.redstone.PulseDiodeTileEntity;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;
import java.io.IOException;

/** Three upstream units: ticks, seconds and minutes; release to confirm. */
public final class PulseDiodeConfigScreen extends GuiScreen {
    private static final ResourceLocation SETTINGS = new ResourceLocation("create", "textures/gui/value_settings.png");
    private final BlockPos pos;
    private int row, value, initialX, initialY;
    private boolean moved, finished;
    public PulseDiodeConfigScreen(BlockPos pos) {
        this.pos = pos.toImmutable();
        int delay = Minecraft.getMinecraft().world.getTileEntity(pos) instanceof PulseDiodeTileEntity tile ? tile.getDelay() : 2;
        row = delay > 1200 ? 2 : delay > 60 ? 1 : 0;
        value = Math.max(1, Math.min(60, delay / multiplier(row)));
    }
    private static int multiplier(int row) { return row == 0 ? 1 : row == 1 ? 20 : 1200; }
    private int left() { return (width - 238) / 2; }
    private int top() { return (height - 88) / 2; }
    private int coordinate(int v) {
        v = Math.max(0, Math.min(60, v));
        return left() + 74 + (Math.max(1, v) - 1) / 10 * 4 + v * 2 + 1
                + (v % 10 == 0 ? 2 : 0) + (v > 0 ? 4 : 0);
    }
    @Override public void initGui() {
        int x = coordinate(value);
        int y = top() + 23 + row * 11 + 5;
        Mouse.setCursorPosition(x * mc.displayWidth / Math.max(1, width), y * mc.displayHeight / Math.max(1, height));
        Mouse.poll(); initialX = Mouse.getX() * width / Math.max(1, mc.displayWidth); initialY = height - 1 - Mouse.getY() * height / Math.max(1, mc.displayHeight);
    }
    private void select(int x, int y) {
        if (!moved && x == initialX && y == initialY) return;
        moved = true;
        int nextRow = Math.max(0, Math.min(2, Math.round((y - (top() + 28)) / 11F)));
        int nextValue = 0, distance = Integer.MAX_VALUE, step = isShiftKeyDown() ? 10 : 1;
        for (int candidate = 0; candidate <= 60; candidate += step) {
            int diff = Math.abs(x - coordinate(candidate));
            if (diff < distance) { distance = diff; nextValue = candidate; }
        }
        row = nextRow; value = nextValue;
    }
    private void finish(boolean confirm) {
        if (finished) return;
        finished = true;
        if (confirm) ModNetwork.CHANNEL.sendToServer(new PulseDiodeConfigPacket(pos, Math.max(2, Math.max(1, value) * multiplier(row))));
        mc.displayGuiScreen(null);
    }
    @Override public void updateScreen() {
        select(Mouse.getX() * width / Math.max(1, mc.displayWidth), height - 1 - Mouse.getY() * height / Math.max(1, mc.displayHeight));
        if (!KineticMachineConfigClientEvents.useKeyDown(mc)) finish(true);
    }
    @Override protected void mouseReleased(int x, int y, int button) {
        if (button - 100 == mc.gameSettings.keyBindUseItem.getKeyCode()) { select(x, y); finish(true); }
    }
    @Override protected void keyTyped(char typed, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) { finish(false); return; }
        super.keyTyped(typed, key);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        value = Math.max(0, Math.min(60, value + (wheel > 0 ? 1 : -1) * (isShiftKeyDown() ? 10 : 1)));
        Mouse.setCursorPosition(coordinate(value) * mc.displayWidth / Math.max(1, width),
                (top() + 28 + row * 11) * mc.displayHeight / Math.max(1, height));
        moved = true;
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        select(mouseX, mouseY);
        GlStateManager.color(1, 1, 1, 1); GlStateManager.enableBlend();
        mc.getTextureManager().bindTexture(SETTINGS);
        drawScaledCustomSizeModalRect(left(), top(), 80, 9, 1, 1, 238, 88, 256, 256);
        String title = I18n.format("gui.cogworkbridge.pulse.interval");
        fontRenderer.drawString(title, left() + (238 - fontRenderer.getStringWidth(title)) / 2, top() + 7, 0xdddddd);
        String hint = I18n.format("gui.cogworkbridge.kinetic.release_to_confirm", mc.gameSettings.keyBindUseItem.getDisplayName());
        fontRenderer.drawString(hint, left() + (238 - fontRenderer.getStringWidth(hint)) / 2, top() + 68, 0xdddddd);
        frame(left() + 1, top() + 20, 69, 38);
        frame(left() + 70, top() + 20, 159, 38);
        String[] labels = {I18n.format("gui.cogworkbridge.pulse.ticks"), I18n.format("gui.cogworkbridge.pulse.seconds"), I18n.format("gui.cogworkbridge.pulse.minutes")};
        for (int i = 0; i < 3; i++) {
            int y = top() + 23 + i * 11;
            mc.getTextureManager().bindTexture(SETTINGS);
            drawScaledCustomSizeModalRect(left() + 4, y, 0, 31, 161, 11, 64, 11, 256, 256);
            drawScaledCustomSizeModalRect(left() + 74, y + 1, 7, 0, 249, 8, 150, 8, 256, 256);
            for (int milestone = 0; milestone <= 60; milestone += 10)
                drawTexturedModalRect(coordinate(milestone) - 3, y + 1, 0, 0, 7, 8);
            fontRenderer.drawString(labels[i], left() + 7, y + 1, 0x442000);
        }
        int x = coordinate(value);
        String text = row == 0 ? Math.max(2, value) + "t" : row == 1 ? "0:" + (value < 10 ? "0" : "") + Math.max(1, value) : Math.max(1, value) + ":00";
        int cursorWidth = fontRenderer.getStringWidth(text) + 4, cursorLeft = x - cursorWidth / 2, cursorTop = top() + 21 + row * 11;
        mc.getTextureManager().bindTexture(SETTINGS);
        drawTexturedModalRect(cursorLeft - 3, cursorTop, 0, 9, 3, 14);
        drawScaledCustomSizeModalRect(cursorLeft, cursorTop, 4, 9, 56, 14, cursorWidth, 14, 256, 256);
        drawTexturedModalRect(cursorLeft + cursorWidth, cursorTop, 61, 9, 3, 14);
        fontRenderer.drawString(text, cursorLeft + 2, cursorTop + 3, 0x442000);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    private void frame(int x, int y, int w, int h) {
        drawTexturedModalRect(x, y, 65, 9, 4, 4); drawTexturedModalRect(x + w - 4, y, 70, 9, 4, 4);
        drawTexturedModalRect(x, y + h - 4, 65, 19, 4, 4); drawTexturedModalRect(x + w - 4, y + h - 4, 70, 19, 4, 4);
        drawScaledCustomSizeModalRect(x, y + 4, 65, 14, 3, 4, 3, h - 8, 256, 256);
        drawScaledCustomSizeModalRect(x + w - 3, y + 4, 71, 14, 3, 4, 3, h - 8, 256, 256);
        drawScaledCustomSizeModalRect(x + 4, y, 0, 24, 256, 3, w - 8, 3, 256, 256);
        drawScaledCustomSizeModalRect(x + 4, y + h - 3, 0, 27, 256, 3, w - 8, 3, 256, 256);
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
