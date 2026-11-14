package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import java.io.IOException;

public final class SchematicPromptScreen extends GuiScreen {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("create", "textures/gui/schematics_2.png");
    private static final int PANEL_WIDTH = 213, PANEL_HEIGHT = 79;
    private GuiTextField name;
    private int left, top;

    @Override public void initGui() {
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        left = (width - PANEL_WIDTH) / 2; top = (height - PANEL_HEIGHT) / 2;
        name = new GuiTextField(0, fontRenderer, left + 49, top + 28, 131, 10);
        name.setEnableBackgroundDrawing(false); name.setTextColor(0xFFFFFF);
        name.setMaxStringLength(35); name.setFocused(true);
        buttonList.clear();
        buttonList.add(new CreateIconButton(0, left + 7, top + 55, 1, 0));
        buttonList.add(new CreateIconButton(1, left + 158, top + 55, 0, 1));
        buttonList.add(new CreateIconButton(2, left + 180, top + 55, 0, 8));
        Keyboard.enableRepeatEvents(true);
    }
    @Override public void updateScreen() { name.updateCursorCounter(); }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) SchematicClient.select(true, null, null);
        if (button.id == 1 || button.id == 2) SchematicClient.save(name.getText(), button.id == 2);
        mc.displayGuiScreen(null);
    }
    @Override protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { actionPerformed(buttonList.get(1)); return; }
        if (!name.textboxKeyTyped(c, key)) super.keyTyped(c, key);
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        name.mouseClicked(x, y, button); super.mouseClicked(x, y, button);
    }
    @Override public void onGuiClosed() {
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        Keyboard.enableRepeatEvents(false);
    }
    private void drawIcon(ItemStack stack, int x, int y, float scale) {
        GlStateManager.pushMatrix(); GlStateManager.translate(x, y, 0); GlStateManager.scale(scale, scale, scale);
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
        RenderHelper.disableStandardItemLighting(); GlStateManager.popMatrix();
    }
    @Override public void drawScreen(int x, int y, float partial) {
        drawDefaultBackground();
        GlStateManager.color(1, 1, 1, 1); mc.getTextureManager().bindTexture(BACKGROUND);
        drawTexturedModalRect(left, top, 0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        String title = I18n.format("cogworkbridge.schematic.prompt.title");
        fontRenderer.drawString(title, left + (PANEL_WIDTH - 8 - fontRenderer.getStringWidth(title)) / 2, top + 4, 0x505050);
        drawIcon(new ItemStack(AllItems.SCHEMATIC), left + 22, top + 24, 1F);
        drawIcon(new ItemStack(AllItems.SCHEMATIC_AND_QUILL), left + PANEL_WIDTH + 6, top + PANEL_HEIGHT - 38, 3F);
        name.drawTextBox(); super.drawScreen(x, y, partial);
        for (GuiButton button : buttonList) if (button.visible && button.isMouseOver()) {
            String key = button.id == 0 ? "cogworkbridge.schematic.prompt.discard"
                    : button.id == 1 ? "cogworkbridge.schematic.prompt.save" : "cogworkbridge.schematic.prompt.convert";
            drawHoveringText(I18n.format(key), x, y); break;
        }
    }
}
