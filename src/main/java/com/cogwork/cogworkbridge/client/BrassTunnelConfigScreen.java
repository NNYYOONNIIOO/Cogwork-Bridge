package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.logistics.tunnel.BrassTunnelConfigPacket;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BrassTunnelTileEntity;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.IOException;

/** Hold-to-adjust seven-way tunnel selection, using Create's value board. */
public final class BrassTunnelConfigScreen extends GuiScreen {
    private static final ResourceLocation SETTINGS = new ResourceLocation("create", "textures/gui/value_settings.png");
    private static final int BAR_WIDTH = 150;
    private final BlockPos pos;
    private int mode, initialMouseX, initialMouseY;
    private boolean pointerMoved;
    private int left, top;

    public BrassTunnelConfigScreen(BlockPos pos) {
        this.pos = pos.toImmutable();
        if (Minecraft.getMinecraft().world.getTileEntity(pos) instanceof BrassTunnelTileEntity tunnel) mode = tunnel.getMode();
    }
    @Override public void initGui() {
        left = (width - 238) / 2; top = (height - 65) / 2;
        int cursorX = left + 74 + Math.round(mode * BAR_WIDTH / 6F);
        int cursorY = top + 30;
        Mouse.setCursorPosition(cursorX * mc.displayWidth / Math.max(1, width),
                cursorY * mc.displayHeight / Math.max(1, height));
        Mouse.poll();
        initialMouseX = Mouse.getX() * width / Math.max(1, mc.displayWidth);
        initialMouseY = height - 1 - Mouse.getY() * height / Math.max(1, mc.displayHeight);
    }
    private void updateSelection(int x, int y) {
        if (!pointerMoved && x == initialMouseX && y == initialMouseY) return;
        pointerMoved = true;
        int next = Math.round(Math.max(0, Math.min(BAR_WIDTH, x - left - 74)) * 6F / BAR_WIDTH);
        if (next == mode) return;
        mode = next;
        ModNetwork.CHANNEL.sendToServer(new BrassTunnelConfigPacket(pos, mode));
    }
    @Override public void updateScreen() {
        updateSelection(Mouse.getX() * width / Math.max(1, mc.displayWidth),
                height - 1 - Mouse.getY() * height / Math.max(1, mc.displayHeight));
        if (!KineticMachineConfigClientEvents.useKeyDown(mc)) mc.displayGuiScreen(null);
    }
    @Override protected void mouseReleased(int x, int y, int button) {
        if (button - 100 == mc.gameSettings.keyBindUseItem.getKeyCode()) {
            updateSelection(x, y);
            mc.displayGuiScreen(null);
        }
    }
    @Override protected void keyTyped(char typed, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; }
        super.keyTyped(typed, key);
    }
    @Override public void drawScreen(int x, int y, float partial) {
        drawDefaultBackground();
        GlStateManager.color(1, 1, 1, 1); GlStateManager.enableBlend();
        mc.getTextureManager().bindTexture(SETTINGS);
        drawScaledCustomSizeModalRect(left, top, 80, 9, 1, 1, 238, 65, 256F, 256F);
        drawScaledCustomSizeModalRect(left + 4, top + 24, 0, 31, 161, 11, 64, 11, 256F, 256F);
        drawScaledCustomSizeModalRect(left + 74, top + 25, 7, 0, 249, 8, BAR_WIDTH, 8, 256F, 256F);
        for (int i = 0; i < 7; i++) drawTexturedModalRect(left + 71 + Math.round(i * BAR_WIDTH / 6F), top + 25, 0, 0, 7, 8);
        int marker = left + 74 + Math.round(mode * BAR_WIDTH / 6F);
        drawTexturedModalRect(marker - 3, top + 22, 0, 9, 3, 14);
        drawScaledCustomSizeModalRect(marker, top + 22, 4, 9, 56, 14, 7, 14, 256F, 256F);
        drawTexturedModalRect(marker + 7, top + 22, 61, 9, 3, 14);
        String value = I18n.format("cogworkbridge.tunnel.mode." + mode);
        fontRenderer.drawString(value, left + 7, top + 25, 0x442000);
        String title = I18n.format("gui.cogworkbridge.tunnel.mode_title");
        fontRenderer.drawString(title, left + (238 - fontRenderer.getStringWidth(title)) / 2, top + 7, 0xDDDDDD);
        String hint = I18n.format("gui.cogworkbridge.kinetic.release_to_confirm", mc.gameSettings.keyBindUseItem.getDisplayName());
        fontRenderer.drawString(hint, left + (238 - fontRenderer.getStringWidth(hint)) / 2, top + 48, 0xDDDDDD);
        super.drawScreen(x, y, partial);
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
