package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.logistics.port.PackagePortConfigPacket;
import com.cogwork.cogworkbridge.content.logistics.port.PackagePortContainer;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import java.io.IOException;

public final class PackagePortScreen extends GuiContainer {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("create", "textures/gui/frogport_and_mailbox.png");
    private static final ResourceLocation INVENTORY = new ResourceLocation("create", "textures/gui/player_inventory.png");
    private final PackagePortContainer container;
    private GuiTextField address;
    private LogisticsAddressSuggestions suggestions;
    private boolean accepts;
    public PackagePortScreen(PackagePortContainer container) { super(container); this.container = container; xSize = 220; ySize = 190; }
    @Override public void initGui() {
        super.initGui(); accepts = container.port().accepts();
        address = new GuiTextField(0, fontRenderer, guiLeft + 30, guiTop - 11, 160, 12);
        address.setMaxStringLength(25); address.setText(container.port().address()); address.setEnableBackgroundDrawing(false); address.setTextColor(0x3d3c48);
        suggestions = new LogisticsAddressSuggestions(address);
        buttonList.clear();
        buttonList.add(new GuiButton(0, guiLeft + 37, guiTop + 58, 122, 18, ""));
        buttonList.add(new CreateIconButton(1, guiLeft + 187, guiTop + 58, 0, 1));
        updateLabel(); Keyboard.enableRepeatEvents(true);
    }
    private void updateLabel() { buttonList.get(0).displayString = I18n.format(accepts ? "cogworkbridge.port.receive" : "cogworkbridge.port.send_only"); }
    private void sendConfig() { ModNetwork.CHANNEL.sendToServer(new PackagePortConfigPacket(container.windowId, address.getText(), accepts)); }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) { accepts = !accepts; updateLabel(); sendConfig(); }
        if (button.id == 1) { sendConfig(); mc.player.closeScreen(); }
    }
    @Override public void onGuiClosed() {
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        Keyboard.enableRepeatEvents(false); super.onGuiClosed();
    }
    @Override protected void keyTyped(char typed, int key) throws IOException {
        if (suggestions.key(key) || address.textboxKeyTyped(typed, key)) { sendConfig(); return; } super.keyTyped(typed, key);
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (suggestions.click(x, y, button)) { sendConfig(); return; }
        address.mouseClicked(x, y, button); super.mouseClicked(x, y, button);
    }
    @Override public void drawScreen(int x, int y, float partial) { super.drawScreen(x, y, partial); suggestions.draw(); }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1); mc.getTextureManager().bindTexture(BACKGROUND);
        var block = container.port().getWorld().getBlockState(container.port().getPos()).getBlock();
        boolean frog = block instanceof com.cogwork.cogworkbridge.content.logistics.port.PackagePortBlock port && port.isFrog();
        drawTexturedModalRect(guiLeft, guiTop - (frog ? 17 : 24), 0, frog ? 0 : 23, 214, frog ? 17 : 24);
        drawTexturedModalRect(guiLeft, guiTop, 0, 47, 220, 82);
        mc.getTextureManager().bindTexture(INVENTORY); drawTexturedModalRect(guiLeft + 30, guiTop + 90, 0, 0, 176, 100);
        address.drawTextBox();
        if(!frog)fontRenderer.drawString(fontRenderer.trimStringToWidth(I18n.format("gui.cogworkbridge.mailbox.station",container.port().stationName().isEmpty()
                ? I18n.format("gui.cogworkbridge.mailbox.unbound") : container.port().stationName()),190),guiLeft+8,guiTop+48,0x3d3c48);
    }
}
