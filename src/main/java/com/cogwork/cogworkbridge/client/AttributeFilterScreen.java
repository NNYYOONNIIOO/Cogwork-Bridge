package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.fluids.AttributeFilterContainer;
import com.cogwork.cogworkbridge.content.fluids.AttributeFilterItem;
import com.cogwork.cogworkbridge.content.fluids.AttributeFilterOptionPacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.input.Mouse;

public final class AttributeFilterScreen extends GuiContainer {
    private static final ResourceLocation FILTER = new ResourceLocation("create", "textures/gui/filters.png");
    private static final ResourceLocation INVENTORY = new ResourceLocation("create", "textures/gui/player_inventory.png");
    private final AttributeFilterContainer filter;
    private int selection;

    public AttributeFilterScreen(AttributeFilterContainer filter) {
        super(filter);
        this.filter = filter;
        xSize = 241; ySize = 197;
    }
    @Override public void initGui() {
        super.initGui();
        guiLeft -= 11;
        guiTop += 7;
        buttonList.clear();
        buttonList.add(new IconButton(0, guiLeft + 38, guiTop + 61, 10, 0));
        buttonList.add(new IconButton(1, guiLeft + 56, guiTop + 61, 11, 0));
        buttonList.add(new IconButton(2, guiLeft + 74, guiTop + 61, 12, 0));
        buttonList.add(new IconButton(3, guiLeft + 179, guiTop + 61, 1, 0));
        buttonList.add(new IconButton(4, guiLeft + 182, guiTop + 26, 0, 0));
        buttonList.add(new IconButton(5, guiLeft + 200, guiTop + 26, 7, 4));
        buttonList.add(new IconButton(8, guiLeft + 208, guiTop + 61, 0, 1));
    }
    @Override protected void actionPerformed(GuiButton button) throws IOException {
        List<AttributeFilterItem.Attribute> options = AttributeFilterItem.attributesFor(filter.getSample());
        if (button.id == 8) { mc.player.closeScreen(); return; }
        if (button.id >= 0 && button.id <= 5) {
            selection = options.isEmpty() ? 0 : Math.min(selection, options.size() - 1);
            filter.applyOption(button.id, selection);
            ModNetwork.CHANNEL.sendToServer(new AttributeFilterOptionPacket(filter.windowId, button.id, selection));
        }
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(FILTER);
        drawTexturedModalRect(guiLeft, guiTop, 0, 99, 241, 85);
        mc.getTextureManager().bindTexture(INVENTORY);
        drawTexturedModalRect(guiLeft + 43, guiTop + 89, 0, 0, 176, 108);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("item.cogworkbridge.attribute_filter.name");
        fontRenderer.drawString(title, (233 - fontRenderer.getStringWidth(title)) / 2, 4, 0x592424);
        fontRenderer.drawString(I18n.format("container.inventory"), 51, 95, 0x404040);
        List<AttributeFilterItem.Attribute> options = AttributeFilterItem.attributesFor(filter.getSample());
        String text = options.isEmpty() ? I18n.format("gui.cogworkbridge.attribute.reference")
                : label(options.get(Math.min(selection, options.size() - 1)));
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(text, 112), 43, 31, 0xF3EBDE);
        ItemStack selectedIcon = new ItemStack(Items.NAME_TAG);
        mc.getRenderItem().renderItemAndEffectIntoGUI(selectedIcon, 16, 62);
        mc.getRenderItem().renderItemOverlayIntoGUI(fontRenderer, selectedIcon, 16, 62,
                Integer.toString(filter.getSelected().size()));
        ItemStack held = mc.player.getHeldItemMainhand();
        if (!held.isEmpty()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(249, 33, 100);
            GlStateManager.scale(4F, 4F, 4F);
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI(held, 0, 0);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
            int mouseY = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
            if (mouseX >= guiLeft + 39 && mouseX < guiLeft + 176
                    && mouseY >= guiTop + 26 && mouseY < guiTop + 44) {
                List<AttributeFilterItem.Attribute> options = AttributeFilterItem.attributesFor(filter.getSample());
                if (!options.isEmpty()) selection = Math.floorMod(selection + (wheel > 0 ? -1 : 1), options.size());
                return;
            }
        }
        super.handleMouseInput();
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
        if (mouseX >= guiLeft + 16 && mouseX < guiLeft + 34 && mouseY >= guiTop + 61 && mouseY < guiTop + 80) {
            List<String> lines = new ArrayList<>();
            lines.add(I18n.format("gui.cogworkbridge.attribute.selected"));
            for (AttributeFilterItem.Attribute entry : filter.getSelected())
                lines.add((entry.inverted() ? "! " : "- ") + label(entry));
            drawHoveringText(lines, mouseX, mouseY);
        }
    }
    private static String label(AttributeFilterItem.Attribute attribute) {
        return I18n.format("gui.cogworkbridge.attribute." + attribute.type(), attribute.value());
    }

    private final class IconButton extends GuiButton {
        private static final ResourceLocation WIDGETS = new ResourceLocation("create", "textures/gui/widgets.png");
        private static final ResourceLocation ICONS = new ResourceLocation("create", "textures/gui/icons.png");
        private final int iconX;
        private final int iconY;

        private IconButton(int id, int x, int y, int iconX, int iconY) {
            super(id, x, y, 18, 18, "");
            this.iconX = iconX;
            this.iconY = iconY;
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            if (!visible) return;
            boolean hasOptions = !AttributeFilterItem.attributesFor(filter.getSample()).isEmpty();
            boolean canUse = enabled && (id != 4 && id != 5 || hasOptions);
            hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            boolean selected = id == 0 && filter.getMode() == 0
                    || id == 1 && filter.getMode() == 1
                    || id == 2 && filter.getMode() == 2;
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(516, 0.1F);
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            minecraft.getTextureManager().bindTexture(WIDGETS);
            int backgroundX = !canUse ? 90 : hovered ? (Mouse.isButtonDown(0) ? 36 : 18)
                    : selected ? 72 : 0;
            int backgroundY = 0;
            Gui.drawModalRectWithCustomSizedTexture(x, y, backgroundX, backgroundY, 18, 18, 256, 256);
            minecraft.getTextureManager().bindTexture(ICONS);
            GlStateManager.color(canUse ? 1 : 0.55F, canUse ? 1 : 0.55F, canUse ? 1 : 0.55F, 1);
            Gui.drawModalRectWithCustomSizedTexture(x + 1, y + 1,
                    iconX * 16, iconY * 16, 16, 16, 256, 256);
        }
    }
}
