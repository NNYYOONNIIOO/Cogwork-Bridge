package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.equipment.ToolboxActionPacket;
import com.cogwork.cogworkbridge.content.equipment.ToolboxContainer;
import com.cogwork.cogworkbridge.content.equipment.ToolboxTileEntity;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public final class ToolboxScreen extends GuiContainer {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("create", "textures/gui/toolbox.png");
    private static final ResourceLocation INVENTORY = new ResourceLocation("create", "textures/gui/player_inventory.png");
    private final ToolboxContainer container;

    public ToolboxScreen(ToolboxContainer container) { super(container); this.container = container; xSize = 218; ySize = 255; }
    @Override public void initGui() {
        super.initGui();
        buttonList.add(new CreateIconButton(0, guiLeft + 185, guiTop + 147, 0, 1));
        buttonList.add(new CreateIconButton(1, guiLeft + 111, guiTop + 69, 11, 3));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.player.closeScreen();
        if (button.id == 1) ModNetwork.CHANNEL.sendToServer(new ToolboxActionPacket(2, container.toolbox().getPos(), 0, mc.player.inventory.currentItem));
    }
    @Override public void onGuiClosed() {
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        super.onGuiClosed();
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(BACKGROUND);
        drawTexturedModalRect(guiLeft + 30, guiTop, 0, 0, 188, 171);
        mc.getTextureManager().bindTexture(INVENTORY);
        drawTexturedModalRect(guiLeft, guiTop + 147, 0, 0, 176, 108);
        ToolboxTileEntity box = container.toolbox();
        for (int i = 0; i < 8; i++) {
            var slot = container.getSlot(i * 4);
            ItemStack filter = box.filter(i);
            if (slot.getStack().isEmpty() && !filter.isEmpty()) {
                GlStateManager.pushMatrix(); GlStateManager.enableDepth();
                itemRender.renderItemAndEffectIntoGUI(filter, guiLeft + slot.xPos, guiTop + slot.yPos);
                GlStateManager.popMatrix();
            }
        }
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(container.toolbox().getDisplayName().getFormattedText(), 45, 4, 0x592424);
        for (int i = 0; i < 8; i++) {
            int count = container.toolbox().count(i);
            if (count <= 0) continue;
            var slot = container.getSlot(i * 4);
            String text = Integer.toString(count);
            fontRenderer.drawStringWithShadow(text, slot.xPos + 17 - fontRenderer.getStringWidth(text), slot.yPos + 9, 0xffffff);
        }
    }
    @Override public void drawScreen(int x, int y, float partial) {
        drawDefaultBackground(); super.drawScreen(x, y, partial); renderHoveredToolTip(x, y);
        if (x >= guiLeft + 111 && x < guiLeft + 129 && y >= guiTop + 69 && y < guiTop + 87)
            drawHoveringText(I18n.format("gui.cogworkbridge.toolbox.deposit"), x, y);
    }
}
