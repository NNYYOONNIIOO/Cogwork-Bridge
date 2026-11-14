package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.logistics.funnel.FunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.funnel.BrassFunnelConfigPacket;
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

/** Quantity board styled with the same value-settings texture as Create. */
public final class BrassFunnelConfigScreen extends GuiScreen {
    private static final ResourceLocation SETTINGS = new ResourceLocation("create", "textures/gui/value_settings.png");
    private static final int BAR_WIDTH = 150;
    private final BlockPos pos;
    private int amount;
    private boolean exact;
    private int initialMouseX;
    private int initialMouseY;
    private boolean pointerMoved;
    private final int xSize = 238, ySize = 74;
    private int guiLeft, guiTop;

    public BrassFunnelConfigScreen(BlockPos pos) {
        this.pos = pos.toImmutable();
        if (Minecraft.getMinecraft().world.getTileEntity(pos) instanceof FunnelTileEntity funnel) {
            amount = funnel.getAmount();
            exact = funnel.isExact();
        } else amount = 1;
    }

    @Override public void initGui() {
        guiLeft = (width - xSize) / 2;
        guiTop = (height - ySize) / 2;
        int x = coordinate(amount), y = rowTop() + (exact ? 16 : 5);
        Mouse.setCursorPosition(x * mc.displayWidth / Math.max(1, width),
                (height - 1 - y) * mc.displayHeight / Math.max(1, height));
        Mouse.poll();
        initialMouseX = Mouse.getX() * width / Math.max(1, mc.displayWidth);
        initialMouseY = height - 1 - Mouse.getY() * height / Math.max(1, mc.displayHeight);
    }

    private int barLeft() { return guiLeft + 74; }
    private int rowTop() { return guiTop + 23; }
    private int coordinate(int amount) { return barLeft() + Math.round((amount - 1) * BAR_WIDTH / 63F); }

    private void send(int amount, boolean exact) {
        amount = Math.max(1, Math.min(64, amount));
        this.amount = amount;
        this.exact = exact;
        ModNetwork.CHANNEL.sendToServer(new BrassFunnelConfigPacket(pos, amount, exact));
    }

    private void updateSelection(int mouseX, int mouseY) {
        if (!pointerMoved && mouseX == initialMouseX && mouseY == initialMouseY) return;
        pointerMoved = true;
        int selected = 1 + Math.round(Math.max(0, Math.min(BAR_WIDTH, mouseX - barLeft())) * 63F / BAR_WIDTH);
        boolean selectedExact = mouseY >= rowTop() + 11;
        if (selected != amount || selectedExact != exact) send(selected, selectedExact);
    }

    @Override public void updateScreen() {
        int x = Mouse.getX() * width / Math.max(1, mc.displayWidth);
        int y = height - 1 - Mouse.getY() * height / Math.max(1, mc.displayHeight);
        updateSelection(x, y);
        if (!KineticMachineConfigClientEvents.useKeyDown(mc)) mc.displayGuiScreen(null);
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && mouseY >= rowTop() && mouseY < rowTop() + 22
                && mouseX >= guiLeft + 4 && mouseX <= barLeft() + BAR_WIDTH + 4) {
            boolean exact = mouseY >= rowTop() + 11;
            int amount = mouseX < barLeft() ? this.amount
                    : 1 + Math.round(Math.max(0, Math.min(BAR_WIDTH, mouseX - barLeft())) * 63F / BAR_WIDTH);
            send(amount, exact);
            pointerMoved = true;
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        updateSelection(mouseX, mouseY);
    }

    @Override protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state - 100 == mc.gameSettings.keyBindUseItem.getKeyCode()) {
            updateSelection(mouseX, mouseY);
            mc.displayGuiScreen(null);
        }
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; }
        super.keyTyped(typedChar, keyCode);
    }

    @Override public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int change = (wheel > 0 ? 1 : -1) * (isShiftKeyDown() ? 16 : 1);
            send(amount + change, exact);
        }
        super.handleMouseInput();
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableBlend();
        mc.getTextureManager().bindTexture(SETTINGS);
        stretch(guiLeft, guiTop, xSize, ySize, 80, 9, 1, 1);
        int top = rowTop();
        drawFrame(barLeft() - 4, top - 3, BAR_WIDTH + 8, 27);
        stretch(barLeft() - 1, top, BAR_WIDTH + 2, 21, 75, 9, 1, 1);
        for (int row = 0; row < 2; row++) {
            int y = top + row * 11;
            mc.getTextureManager().bindTexture(SETTINGS);
            stretch(guiLeft + 4, y, 64, 11, 0, 31, 161, 11);
            stretch(barLeft(), y + 1, BAR_WIDTH, 8, 7, 0, 249, 8);
            for (int value : new int[]{1, 16, 32, 48, 64})
                drawTexturedModalRect(coordinate(value) - 3, y + 1, 0, 0, 7, 8);
            String label = I18n.format(row == 0 ? "cogworkbridge.logistics.up_to" : "cogworkbridge.logistics.exact");
            fontRenderer.drawString(fontRenderer.trimStringToWidth(label, 58), guiLeft + 7, y + 1, 0x442000);
        }
        int selectedX = coordinate(amount);
        int selectedY = top + (exact ? 11 : 0) - 2;
        String value = (exact ? "=" : "≤") + amount;
        int textWidth = fontRenderer.getStringWidth(value) + 4;
        int x = selectedX - textWidth / 2;
        mc.getTextureManager().bindTexture(SETTINGS);
        drawTexturedModalRect(x - 3, selectedY, 0, 9, 3, 14);
        stretch(x, selectedY, textWidth, 14, 4, 9, 56, 14);
        drawTexturedModalRect(x + textWidth, selectedY, 61, 9, 3, 14);
        fontRenderer.drawString(value, x + 2, selectedY + 3, 0x442000);
        String title = I18n.format("gui.cogworkbridge.funnel.amount_title");
        fontRenderer.drawString(title, guiLeft + (xSize - fontRenderer.getStringWidth(title)) / 2, guiTop + 7, 0xDDDDDD);
        String hint = I18n.format("gui.cogworkbridge.kinetic.release_to_confirm",
                mc.gameSettings.keyBindUseItem.getDisplayName());
        fontRenderer.drawString(hint, guiLeft + (xSize - fontRenderer.getStringWidth(hint)) / 2, guiTop + 55, 0xDDDDDD);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawFrame(int x, int y, int w, int h) {
        drawTexturedModalRect(x, y, 65, 9, 4, 4);
        drawTexturedModalRect(x + w - 4, y, 70, 9, 4, 4);
        drawTexturedModalRect(x, y + h - 4, 65, 19, 4, 4);
        drawTexturedModalRect(x + w - 4, y + h - 4, 70, 19, 4, 4);
        stretch(x, y + 4, 3, h - 8, 65, 14, 3, 4);
        stretch(x + w - 3, y + 4, 3, h - 8, 71, 14, 3, 4);
        stretch(x + 4, y, w - 8, 3, 0, 24, 256, 3);
        stretch(x + 4, y + h - 3, w - 8, 3, 0, 27, 256, 3);
    }

    private static void stretch(int x, int y, int w, int h, int u, int v, int sw, int sh) {
        if (w > 0 && h > 0) drawScaledCustomSizeModalRect(x, y, u, v, sw, sh, w, h, 256F, 256F);
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
