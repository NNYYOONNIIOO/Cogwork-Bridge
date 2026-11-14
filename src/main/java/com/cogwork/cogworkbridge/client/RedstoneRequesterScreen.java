package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.logistics.packager.RedstoneRequesterConfigPacket;
import com.cogwork.cogworkbridge.content.logistics.packager.RedstoneRequesterContainer;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

public final class RedstoneRequesterScreen extends GuiContainer {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("create", "textures/gui/requester.png");
    private static final ResourceLocation INVENTORY = new ResourceLocation("create", "textures/gui/player_inventory.png");
    private final RedstoneRequesterContainer requester;
    private GuiTextField address;
    private LogisticsAddressSuggestions suggestions;
    private boolean partial;
    public RedstoneRequesterScreen(RedstoneRequesterContainer requester) { super(requester); this.requester = requester; xSize = 238; ySize = 232; }
    @Override public void initGui() {
        super.initGui(); partial = requester.requester().allowsPartial();
        address = new GuiTextField(0, fontRenderer, guiLeft + 55, guiTop + 68, 110, 10);
        address.setEnableBackgroundDrawing(false); address.setTextColor(0x555555); address.setMaxStringLength(25);
        address.setText(requester.requester().address());
        suggestions = new LogisticsAddressSuggestions(address);
        buttonList.clear(); buttonList.add(new CreateIconButton(0, guiLeft + 202, guiTop + 95, 0, 1));
        buttonList.add(new CreateIconButton(1, guiLeft + 12, guiTop + 95, 2, 12));
        buttonList.add(new CreateIconButton(2, guiLeft + 30, guiTop + 95, 3, 12));
        updateButtons(); Keyboard.enableRepeatEvents(true);
    }
    private void updateButtons() { buttonList.get(1).enabled = !partial; buttonList.get(2).enabled = partial; }
    private void send() { ModNetwork.CHANNEL.sendToServer(new RedstoneRequesterConfigPacket(requester.windowId, address.getText(), partial, requester)); }
    @Override protected void actionPerformed(net.minecraft.client.gui.GuiButton button) {
        if (button.id == 0) { send(); mc.player.closeScreen(); return; }
        if (button.id == 1 || button.id == 2) { partial = button.id == 1; updateButtons(); send(); }
    }
    @Override protected void keyTyped(char typed, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) { send(); mc.player.closeScreen(); return; }
        if (suggestions.key(key) || address.textboxKeyTyped(typed, key)) return;
        super.keyTyped(typed, key);
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException { if (suggestions.click(x, y, button)) return; address.mouseClicked(x, y, button); super.mouseClicked(x, y, button); }
    @Override public void drawScreen(int x, int y, float partial) { super.drawScreen(x, y, partial); suggestions.draw(); }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput(); int wheel = Mouse.getEventDWheel(); if (wheel == 0) return;
        int x = Mouse.getEventX() * width / mc.displayWidth - guiLeft;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1 - guiTop;
        if (y < 28 || y >= 44 || x < 27 || x >= 207 || (x - 27) % 20 >= 16) return;
        int slot = (x - 27) / 20; if (requester.ghostItem(slot).isEmpty()) return;
        requester.setClientAmount(slot, requester.amount(slot) + (wheel > 0 ? 1 : -1) * (isShiftKeyDown() ? 10 : 1)); send();
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1); mc.getTextureManager().bindTexture(BACKGROUND);
        drawTexturedModalRect(guiLeft + 3, guiTop, 16, 16, 232, 120);
        mc.getTextureManager().bindTexture(INVENTORY); drawTexturedModalRect(guiLeft - 3, guiTop + 124, 0, 0, 176, 108);
        address.drawTextBox();
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("tile.cogworkbridge.redstone_requester.name");
        fontRenderer.drawString(title, 117 - fontRenderer.getStringWidth(title) / 2, 4, 0x3d3c48);
        for (int i = 0; i < 9; i++) {
            ItemStack item = requester.ghostItem(i);
            if (!item.isEmpty()) itemRender.renderItemOverlayIntoGUI(fontRenderer, item, 27 + i * 20, 28, Integer.toString(requester.amount(i)));
        }
    }
    @Override public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        for (var button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        super.onGuiClosed();
    }
}
