package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.logistics.packagebox.PackageFilterContainer;
import com.cogwork.cogworkbridge.content.logistics.packagebox.PackageFilterPacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import java.io.IOException;

public final class PackageFilterScreen extends GuiContainer {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("create", "textures/gui/filters_2.png");
    private static final ResourceLocation INVENTORY = new ResourceLocation("create", "textures/gui/player_inventory.png");
    private final PackageFilterContainer filter;
    private GuiTextField address;
    private LogisticsAddressSuggestions suggestions;
    public PackageFilterScreen(PackageFilterContainer filter) { super(filter); this.filter = filter; xSize = 218; ySize = 191; }
    @Override public void initGui() {
        super.initGui(); guiLeft -= 11; guiTop += 7;
        address = new GuiTextField(0, fontRenderer, guiLeft + 44, guiTop + 28, 129, 9);
        address.setEnableBackgroundDrawing(false); address.setMaxStringLength(25); address.setText(filter.address()); address.setFocused(true);
        suggestions = new LogisticsAddressSuggestions(address);
        buttonList.clear();
        buttonList.add(new CreateIconButton(0, guiLeft + 185, guiTop + 55, 0, 1));
        buttonList.add(new CreateIconButton(1, guiLeft + 156, guiTop + 55, 1, 0));
        Keyboard.enableRepeatEvents(true);
    }
    @Override public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        for (var button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        super.onGuiClosed();
    }
    @Override public void updateScreen() { super.updateScreen(); address.updateCursorCounter(); }
    @Override protected void actionPerformed(net.minecraft.client.gui.GuiButton button) {
        if (button.id == 0) mc.player.closeScreen();
        else if (button.id == 1) {
            address.setText("");
            address.setFocused(true);
            ModNetwork.CHANNEL.sendToServer(new PackageFilterPacket(filter.windowId, ""));
        }
    }
    @Override protected void keyTyped(char ch, int key) throws IOException {
        if (suggestions.key(key)) { ModNetwork.CHANNEL.sendToServer(new PackageFilterPacket(filter.windowId, address.getText())); return; }
        if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) && address.isFocused()) { address.setFocused(false); return; }
        String old = address.getText();
        if (address.textboxKeyTyped(ch, key)) {
            if (!old.equals(address.getText())) ModNetwork.CHANNEL.sendToServer(new PackageFilterPacket(filter.windowId, address.getText()));
            return;
        }
        super.keyTyped(ch, key);
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (suggestions.click(x, y, button)) { ModNetwork.CHANNEL.sendToServer(new PackageFilterPacket(filter.windowId, address.getText())); return; }
        if (button == 1 && x >= address.x && x < address.x + address.width && y >= address.y && y < address.y + address.height) {
            address.setText(""); address.setFocused(true); ModNetwork.CHANNEL.sendToServer(new PackageFilterPacket(filter.windowId, "")); return;
        }
        address.mouseClicked(x, y, button); super.mouseClicked(x, y, button);
    }
    @Override public void drawScreen(int x, int y, float partial) { super.drawScreen(x, y, partial); suggestions.draw(); }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int x, int y) {
        GlStateManager.color(1, 1, 1, 1); mc.getTextureManager().bindTexture(BACKGROUND); drawTexturedModalRect(guiLeft, guiTop, 0, 0, 218, 79);
        mc.getTextureManager().bindTexture(INVENTORY); drawTexturedModalRect(guiLeft + 32, guiTop + 83, 0, 0, 176, 108);
        itemRender.renderItemAndEffectIntoGUI(new ItemStack(AllItems.CARDBOARD_PACKAGES[0]), guiLeft + 16, guiTop + 23);
        address.drawTextBox();
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("item.cogworkbridge.package_filter.name"), 10, 8, 0x4b3021);
    }
}
