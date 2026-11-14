package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.equipment.WorldshaperItem;
import com.cogwork.cogworkbridge.content.equipment.WorldshaperPacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class WorldshaperScreen extends GuiScreen {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("create", "textures/gui/curiosities.png");
    private static final ResourceLocation WIDGETS = new ResourceLocation("create", "textures/gui/widgets.png");
    private static final int[][] TOOLS = {
            {0, 1, 2, 3, 4, 5}, {0, 1, 2, 3, 4, 5}, {0, 1, 2, 3, 4, 5},
            {4, 2, 3}, {2, 3}
    };
    private final EnumHand hand;
    private int slot, shape, tool, widthValue, heightValue, depthValue, pattern, placement, left, top;
    private boolean loaded, diagonal, across;
    public WorldshaperScreen(EnumHand hand) { this.hand = hand; }
    @Override public void initGui() {
        if (!loaded) {
            ItemStack stack = mc.player.getHeldItem(hand); slot = mc.player.inventory.currentItem;
            shape = Math.max(0, Math.min(4, WorldshaperItem.setting(stack, "Shape", 0)));
            tool = WorldshaperItem.setting(stack, "Tool", 0);
            widthValue = WorldshaperItem.setting(stack, "Width", 3); heightValue = WorldshaperItem.setting(stack, "Height", 3);
            depthValue = WorldshaperItem.setting(stack, "Depth", 3);
            pattern = Math.max(0, Math.min(5, WorldshaperItem.setting(stack, "Pattern", 0)));
            placement = Math.max(0, Math.min(2, WorldshaperItem.setting(stack, "Placement", 0)));
            diagonal = WorldshaperItem.flag(stack, "Diagonal"); across = WorldshaperItem.flag(stack, "Across"); loaded = true;
        }
        clampDimensions();
        boolean supported = false;
        for (int candidate : TOOLS[shape]) if (tool == candidate) supported = true;
        if (!supported) tool = TOOLS[shape][0];
        left = (width - 234) / 2 - 10; top = (height - 103) / 2;
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        buttonList.clear();
        for (int i = 0; i < 6; i++) buttonList.add(new CreateIconButton(10 + i, left + 158 + i % 3 * 18, top + 21 + i / 3 * 18,
                i < 4 ? i : i - 4, i < 4 ? 6 : 7));
        int shown = 0;
        for (int i : TOOLS[shape]) {
            buttonList.add(new CreateIconButton(20 + i, left + 7 + shown++ * 18, top + 79, 7 + i, 2));
        }
        if (shape < 3) for (int i = 0; i < 3; i++) buttonList.add(new CreateIconButton(30 + i, left + 136 + i * 18, top + 79, 4 + i, 2));
        else {
            buttonList.add(new CreateIconButton(40, left + 79, top + 79, 2, 7));
            buttonList.add(new CreateIconButton(41, left + 97, top + 79, 3, 7));
        }
        buttonList.add(new CreateIconButton(50, left + 201, top + 79, 0, 1));
        labels();
    }
    private void labels() {
        for (GuiButton button : buttonList) {
            if (button instanceof CreateIconButton icon)
                icon.setSelected(button.id == 10 + pattern || button.id == 20 + tool
                        || shape < 3 && button.id == 30 + placement);
        }
    }
    private void save() {
        ItemStack stack = mc.player.getHeldItem(hand);
        if (!(stack.getItem() instanceof WorldshaperItem) || hand == EnumHand.MAIN_HAND && slot != mc.player.inventory.currentItem) return;
        WorldshaperItem.configure(stack, shape, tool, widthValue, heightValue, depthValue, pattern, placement, diagonal, across);
        ModNetwork.CHANNEL.sendToServer(new WorldshaperPacket(hand, slot, shape, tool, widthValue, heightValue, depthValue, pattern, placement, diagonal, across));
    }
    @Override protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 50) { save(); mc.displayGuiScreen(null); return; }
        if (button.id >= 10 && button.id < 16) pattern = button.id - 10;
        if (button.id >= 20 && button.id < 26) tool = button.id - 20;
        if (button.id >= 30 && button.id < 33) placement = button.id - 30;
        if (button.id == 40) diagonal = !diagonal;
        if (button.id == 41) across = !across;
        labels(); save();
    }
    private void adjust(int id, int delta) {
        int value = (id == 1 ? widthValue : id == 2 ? heightValue : depthValue)
                + delta * (isShiftKeyDown() ? 5 : 1);
        if (isShiftKeyDown()) value -= value % 5;
        if (id == 1) widthValue = value;
        if (id == 2) heightValue = value;
        if (id == 3) depthValue = value;
        clampDimensions();
    }
    private void clampDimensions() {
        widthValue = Math.max(shape == 1 || shape == 2 ? 0 : 1,
                Math.min(widthValue, shape == 1 || shape >= 3 ? 10 : shape == 2 ? 8 : 32));
        heightValue = Math.max(1, Math.min(heightValue, shape == 2 ? 8 : 32));
        depthValue = Math.max(1, Math.min(depthValue, 32));
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int input = hoveredInput(x, y);
        if (input == 0) {
            int next = Math.max(0, Math.min(4, shape + (wheel > 0 ? -1 : 1) * (isShiftKeyDown() ? 5 : 1)));
            if (next != shape) { shape = next; initGui(); save(); }
        } else if (input > 0) {
            adjust(input, wheel > 0 ? 1 : -1);
            save();
        }
    }
    private int paramCount() { return shape == 0 ? 3 : shape == 2 ? 2 : 1; }
    private int hoveredInput(int x, int y) {
        if (x >= left + 56 && x < left + 133 && y >= top + 20 && y < top + 38) return 0;
        if (y >= top + 40 && y < top + 58) {
            for (int i = 0; i < paramCount(); i++)
                if (x >= left + 56 + i * 20 && x < left + 74 + i * 20) return i + 1;
        }
        return -1;
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground(); GlStateManager.color(1, 1, 1, 1); mc.getTextureManager().bindTexture(BACKGROUND);
        Gui.drawModalRectWithCustomSizedTexture(left, top, 0, 0, 234, 103, 256, 256);
        for (int i = paramCount(); i < 3; i++)
            Gui.drawModalRectWithCustomSizedTexture(left + 56 + i * 20, top + 40, 238, 0, 18, 18, 256, 256);
        int hoveredInput = hoveredInput(mouseX, mouseY);
        if (hoveredInput == 0) drawRect(left + 56, top + 20, left + 133, top + 38, 0x22ffffff);
        else if (hoveredInput > 0) {
            int x = left + 56 + (hoveredInput - 1) * 20;
            drawRect(x, top + 40, x + 18, top + 58, 0x22ffffff);
        }
        String title = I18n.format("item.cogworkbridge.handheld_worldshaper.name");
        fontRenderer.drawString(title, left + (234 - fontRenderer.getStringWidth(title)) / 2, top + 4, 0x54214f);
        fontRenderer.drawString(I18n.format("gui.cogworkbridge.worldshaper.tool_section"), left + 7, top + 69, 0x767676);
        if (shape < 3) fontRenderer.drawString(I18n.format("gui.cogworkbridge.worldshaper.placement_section"), left + 136, top + 69, 0x767676);
        fontRenderer.drawStringWithShadow(I18n.format("gui.cogworkbridge.worldshaper.shape." + shape), left + 61, top + 25, 0xffffff);
        int[] values = {widthValue, heightValue, depthValue};
        for (int i = 0; i < paramCount(); i++) {
            String value = Integer.toString(values[i]);
            fontRenderer.drawStringWithShadow(value, left + 65 + i * 20 - fontRenderer.getStringWidth(value) / 2, top + 45, 0xffffff);
        }
        var state = WorldshaperItem.selected(mc.player.getHeldItem(hand));
        if (state != null) itemRender.renderItemAndEffectIntoGUI(new ItemStack(Item.getItemFromBlock(state.getBlock()), 1, state.getBlock().getMetaFromState(state)), left + 20, top + 30);
        super.drawScreen(mouseX, mouseY, partial);
        if (shape >= 3) {
            GlStateManager.color(1, 1, 1, 1);
            mc.getTextureManager().bindTexture(WIDGETS);
            Gui.drawModalRectWithCustomSizedTexture(left + 79, top + 73, diagonal ? 18 : 0, 18, 18, 6, 256, 256);
            Gui.drawModalRectWithCustomSizedTexture(left + 97, top + 73, across ? 18 : 0, 18, 18, 6, 256, 256);
        }
        if (hoveredInput >= 0) { drawInputTooltip(hoveredInput, mouseX, mouseY); return; }
        for (GuiButton button : buttonList) if (button.visible && mouseX >= button.x && mouseX < button.x + button.width && mouseY >= button.y && mouseY < button.y + button.height) {
            String key = button.id >= 10 && button.id < 16 ? "gui.cogworkbridge.worldshaper.pattern." + (button.id - 10)
                    : button.id >= 20 && button.id < 26 ? "gui.cogworkbridge.worldshaper.tool." + (button.id - 20)
                    : button.id >= 30 && button.id < 33 ? "gui.cogworkbridge.worldshaper.placement." + (button.id - 30)
                    : button.id == 40 ? "gui.cogworkbridge.worldshaper.diagonal" : button.id == 41 ? "gui.cogworkbridge.worldshaper.across" : null;
            if (key != null) drawHoveringText(I18n.format(key), mouseX, mouseY);
            break;
        }
    }
    private void drawInputTooltip(int input, int mouseX, int mouseY) {
        List<String> lines = new ArrayList<>();
        if (input == 0) {
            lines.add(I18n.format("gui.cogworkbridge.worldshaper.brush"));
            for (int i = 0; i < 5; i++)
                lines.add((i == shape ? TextFormatting.WHITE + "-> " : TextFormatting.GRAY + "> ")
                        + I18n.format("gui.cogworkbridge.worldshaper.shape." + i));
            lines.add(TextFormatting.DARK_GRAY.toString() + TextFormatting.ITALIC
                    + I18n.format("gui.cogworkbridge.worldshaper.scroll_select"));
        } else {
            String key = input == 1 && shape >= 3 ? "range" : input == 1 && (shape == 1 || shape == 2) ? "radius"
                    : input == 1 ? "width" : input == 2 ? "height" : "depth";
            lines.add(I18n.format("gui.cogworkbridge.worldshaper." + key));
            lines.add(TextFormatting.DARK_GRAY.toString() + TextFormatting.ITALIC
                    + I18n.format("gui.cogworkbridge.worldshaper.scroll_modify"));
            lines.add(TextFormatting.DARK_GRAY.toString() + TextFormatting.ITALIC
                    + I18n.format("gui.cogworkbridge.worldshaper.scroll_fast"));
        }
        drawHoveringText(lines, mouseX, mouseY);
    }
    @Override public void onGuiClosed() {
        if (loaded && mc.player != null) save();
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        super.onGuiClosed();
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
