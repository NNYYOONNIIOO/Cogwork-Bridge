package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.redstone.LinkedControllerContainer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.util.text.TextFormatting;
import java.util.ArrayList;
import java.util.List;

public final class LinkedControllerScreen extends GuiContainer {
    private static final ResourceLocation PANEL = new ResourceLocation("create", "textures/gui/curiosities_2.png");
    private static final ResourceLocation INVENTORY = new ResourceLocation("create", "textures/gui/player_inventory.png");
    public LinkedControllerScreen(LinkedControllerContainer container) { super(container); xSize = 179; ySize = 221; }
    @Override public void initGui() {
        super.initGui();
        buttonList.add(new CreateIconButton(0, guiLeft + 146, guiTop + 85, 0, 1));
        buttonList.add(new CreateIconButton(1, guiLeft + 117, guiTop + 85, 1, 0));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.player.closeScreen();
        } else if (button.id == 1) {
            for (int slot = 36; slot < 48; slot++)
                mc.playerController.windowClick(inventorySlots.windowId, slot, 0, ClickType.QUICK_MOVE, mc.player);
        }
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(PANEL);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, 179, 109);
        mc.getTextureManager().bindTexture(INVENTORY);
        drawTexturedModalRect(guiLeft + 1, guiTop + 113, 0, 0, 176, 108);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("item.cogworkbridge.linked_controller.name"), 15, 4, 0x592424);
        fontRenderer.drawString(I18n.format("container.inventory"), 9, 117, 0x404040);
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partial);
        if (mc.player.inventory.getItemStack().isEmpty()) {
            for (int i = 36; i < 48; i++) {
                Slot slot = inventorySlots.getSlot(i);
                if (!isPointInRegion(slot.xPos, slot.yPos, 16, 16, mouseX, mouseY)) continue;
                List<String> lines = slot.getHasStack()
                        ? new ArrayList<>(slot.getStack().getTooltip(mc.player, mc.gameSettings.advancedItemTooltips
                                ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL))
                        : new ArrayList<>();
                int index = i - 36;
                String key = GameSettings.getKeyDisplayString(LinkedControllerClientEvents.controls(mc)[index / 2].getKeyCode());
                lines.add(TextFormatting.GOLD + I18n.format("gui.cogworkbridge.controller.frequency_slot", index % 2 + 1, key));
                drawHoveringText(lines, mouseX, mouseY);
                return;
            }
        }
        renderHoveredToolTip(mouseX, mouseY);
    }
    @Override public void onGuiClosed() {
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        super.onGuiClosed();
    }
}
