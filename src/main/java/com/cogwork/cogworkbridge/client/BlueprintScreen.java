package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.equipment.BlueprintContainer;
import com.cogwork.cogworkbridge.content.equipment.BlueprintEditPacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import java.awt.Rectangle;

public final class BlueprintScreen extends GuiContainer {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("create", "textures/gui/curiosities_2.png");
    private static final ResourceLocation INVENTORY = new ResourceLocation("create", "textures/gui/player_inventory.png");
    private final BlueprintContainer blueprint;
    public BlueprintScreen(BlueprintContainer container) { super(container); blueprint = container; xSize = 179; ySize = 221; }
    @Override public void initGui() {
        super.initGui();
        buttonList.add(new CreateIconButton(0, guiLeft + 117, guiTop + 85, 1, 0));
        buttonList.add(new CreateIconButton(1, guiLeft + 146, guiTop + 85, 0, 1));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) ModNetwork.CHANNEL.sendToServer(new BlueprintEditPacket(blueprint.windowId, -1, ItemStack.EMPTY));
        if (button.id == 1) mc.player.closeScreen();
    }
    @Override public void onGuiClosed() {
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        super.onGuiClosed();
    }
    public Rectangle getJeiGhostArea(int slot) {
        var target = blueprint.getSlot(slot);
        return new Rectangle(guiLeft + target.xPos, guiTop + target.yPos, 16, 16);
    }
    public void acceptJeiItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= 11 || stack.isEmpty()) return;
        ModNetwork.CHANNEL.sendToServer(new BlueprintEditPacket(blueprint.windowId, slot, stack));
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        GlStateManager.color(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(BACKGROUND); drawTexturedModalRect(guiLeft, guiTop, 0, 109, 179, 109);
        mc.getTextureManager().bindTexture(INVENTORY); drawTexturedModalRect(guiLeft + 2, guiTop + 113, 0, 0, 176, 108);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("item.cogworkbridge.crafting_blueprint.name"), 15, 4, 0xffffff);
    }
    @Override public void drawScreen(int x, int y, float partial) {
        drawDefaultBackground(); super.drawScreen(x, y, partial); renderHoveredToolTip(x, y);
    }
}
