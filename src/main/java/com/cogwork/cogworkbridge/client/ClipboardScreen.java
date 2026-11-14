package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardData;
import com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardEditPacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.awt.Rectangle;

/** A compact, server-authoritative 12-row clipboard editor. */
public final class ClipboardScreen extends GuiScreen {
    private final BlockPos target;
    private final int slot;
    private final NBTTagCompound data;
    private final boolean readOnly;
    private int page;
    private final String[] lines = new String[ClipboardData.ROWS];
    private final GuiTextField[] fields = new GuiTextField[ClipboardData.ROWS];
    private final ItemStack[] icons = new ItemStack[ClipboardData.ROWS];
    private int checked;
    public ClipboardScreen(BlockPos target, int slot) {
        this.target = target;
        this.slot = slot;
        Minecraft mc = Minecraft.getMinecraft();
        if (target != null && mc.world.getTileEntity(target) instanceof com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardTileEntity clipboard)
            data = clipboard.content();
        else if (slot >= 0 && slot < 9) data = ClipboardData.fromItem(mc.player.inventory.getStackInSlot(slot));
        else data = new NBTTagCompound();
        readOnly = ClipboardData.isReadOnly(data);
        int lastPage = readOnly ? ClipboardData.pageCount(data) - 1 : ClipboardData.MAX_PAGES - 1;
        page = Math.max(0, Math.min(lastPage, data.getInteger("LastPage")));
        String[] initial = ClipboardData.lines(data, page);
        System.arraycopy(initial, 0, lines, 0, lines.length);
        checked = ClipboardData.checked(data, page);
        System.arraycopy(ClipboardData.icons(data, page), 0, icons, 0, icons.length);
    }
    @Override public void initGui() {
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        int left = guiLeft(), top = guiTop();
        for (int i = 0; i < fields.length; i++) {
            fields[i] = new GuiTextField(i, fontRenderer, left + 58 + (icons[i].isEmpty() ? 0 : 16), top + 50 + i * 12, 134, 12);
            fields[i].setMaxStringLength(ClipboardData.MAX_TEXT);
            fields[i].setEnableBackgroundDrawing(false);
            fields[i].setTextColor(0x403020);
            fields[i].setDisabledTextColour(0x403020);
            fields[i].setText(lines[i] == null ? "" : lines[i]);
            fields[i].setEnabled(!readOnly);
        }
        buttonList.add(new PageButton(0, left + 53, top + 229, false));
        buttonList.add(new PageButton(1, left + 176, top + 229, true));
        buttonList.add(new IconButton(2, left + 234, top + 175, 4, 0));
        IconButton clear = new IconButton(3, left + 234, top + 153, 4, 7);
        clear.enabled = !readOnly;
        buttonList.add(clear);
        if (!readOnly) fields[0].setFocused(true);
    }
    @Override protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 2) { mc.displayGuiScreen(null); return; }
        if (button.id == 3) {
            clearChecked();
            return;
        }
        savePage();
        if (button.id == 0) page = Math.max(0, page - 1);
        else if (button.id == 1) page = Math.min(readOnly ? ClipboardData.pageCount(data) - 1 : ClipboardData.MAX_PAGES - 1, page + 1);
        loadPage();
    }
    private void clearChecked() {
        if (readOnly) return;
        int dest = 0;
        for (int row = 0; row < fields.length; row++) {
            if ((checked & (1 << row)) != 0) continue;
            lines[dest] = fields[row].getText();
            icons[dest] = icons[row];
            dest++;
        }
        while (dest < fields.length) {
            lines[dest] = "";
            icons[dest++] = ItemStack.EMPTY;
        }
        checked = 0;
        for (int row = 0; row < fields.length; row++) fields[row].setText(lines[row]);
        savePage();
    }
    private void savePage() {
        for (int i = 0; i < fields.length; i++) lines[i] = fields[i].getText();
        ClipboardData.applyEdit(data, page, lines, checked, icons);
        ModNetwork.CHANNEL.sendToServer(new ClipboardEditPacket(slot, target, page, lines, checked, icons));
    }
    private void loadPage() {
        String[] next = ClipboardData.lines(data, page);
        System.arraycopy(next, 0, lines, 0, lines.length);
        checked = ClipboardData.checked(data, page);
        System.arraycopy(ClipboardData.icons(data, page), 0, icons, 0, icons.length);
        for (int i = 0; i < fields.length; i++) {
            fields[i].setText(lines[i]);
            fields[i].x = guiLeft() + 58 + (icons[i].isEmpty() ? 0 : 16);
        }
    }
    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; }
        for (GuiTextField field : fields) if (field.textboxKeyTyped(typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }
    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int left = guiLeft(), top = guiTop();
        for (int row = 0; row < icons.length; row++) {
            if (!getJeiGhostArea(row).contains(mouseX, mouseY)) continue;
            if (!readOnly && mouseButton == 1) {
                icons[row] = ItemStack.EMPTY;
                return;
            }
            ItemStack held = mc.player.getHeldItemOffhand();
            if (!readOnly && mouseButton == 0 && !held.isEmpty()) {
                acceptJeiItem(row, held);
                return;
            }
        }
        if (mouseButton == 0 && mouseX >= left + 44 && mouseX < left + 55) {
            int row = (mouseY - top - 50) / 12;
            if (mouseY >= top + 50 && row >= 0 && row < ClipboardData.ROWS) checked ^= 1 << row;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        for (GuiTextField field : fields) field.mouseClicked(mouseX, mouseY, mouseButton);
    }
    @Override public void updateScreen() { for (GuiTextField field : fields) field.updateCursorCounter(); }
    @Override public void onGuiClosed() { savePage(); Keyboard.enableRepeatEvents(false); }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = guiLeft(), top = guiTop();
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(new ResourceLocation("create", "textures/gui/clipboard.png"));
        drawTexturedModalRect(left, top, 0, 0, 256, 256);
        String title = I18n.format("book.pageIndicator", page + 1, Math.max(page + 1, ClipboardData.pageCount(data)));
        fontRenderer.drawString(title, left + 150, top + 9, 0xaaaaaa);
        for (int row = 0; row < ClipboardData.ROWS; row++) {
            int yy = top + 50 + row * 12;
            fontRenderer.drawString("\u25a1", left + 45, yy + 1, 0x8d7f6b);
            if ((checked & (1 << row)) != 0) fontRenderer.drawString("\u2714", left + 45, yy, 0x31b25d);
            fields[row].x = left + 58 + (icons[row].isEmpty() ? 0 : 16);
        }
        for (GuiTextField field : fields) field.drawTextBox();
        RenderHelper.enableGUIStandardItemLighting();
        for (int row = 0; row < icons.length; row++) {
            if (icons[row].isEmpty()) continue;
            GlStateManager.pushMatrix();
            GlStateManager.translate(left + 55, top + 49 + row * 12, 100);
            GlStateManager.scale(.75F, .75F, 1F);
            mc.getRenderItem().renderItemAndEffectIntoGUI(icons[row], 0, 0);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
        buttonList.get(0).visible = page > 0;
        buttonList.get(1).visible = page + 1 < ClipboardData.MAX_PAGES
                && (!readOnly || page + 1 < ClipboardData.pageCount(data));
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (buttonList.get(2).isMouseOver()) drawHoveringText(I18n.format("gui.done"), mouseX, mouseY);
        if (buttonList.get(3).isMouseOver()) drawHoveringText(I18n.format("gui.cogworkbridge.clipboard.clear_checked"), mouseX, mouseY);
        for (int row = 0; row < icons.length; row++) {
            if (!icons[row].isEmpty() && getJeiGhostArea(row).contains(mouseX, mouseY)) {
                renderToolTip(icons[row], mouseX, mouseY);
                break;
            }
        }
    }
    public Rectangle getJeiGhostArea(int row) {
        int left = guiLeft(), top = guiTop();
        return new Rectangle(left + 55, top + 49 + row * 12, 12, 12);
    }
    public void acceptJeiItem(int row, ItemStack stack) {
        if (readOnly || row < 0 || row >= icons.length || stack == null || stack.isEmpty()) return;
        icons[row] = stack.copy();
        icons[row].setCount(1);
    }
    private int guiLeft() { return (width - 256) / 2; }
    private int guiTop() { return (height - 256) / 2 - 8; }

    private static final class PageButton extends GuiButton {
        private final boolean forward;
        private PageButton(int id, int x, int y, boolean forward) { super(id, x, y, 23, 13, ""); this.forward = forward; }
        @Override public void drawButton(Minecraft mc, int mx, int my, float partial) {
            if (!visible) return;
            hovered = mx >= x && my >= y && mx < x + width && my < y + height;
            GlStateManager.color(1, 1, 1, 1);
            mc.getTextureManager().bindTexture(new ResourceLocation("minecraft", "textures/gui/book.png"));
            Gui.drawModalRectWithCustomSizedTexture(x, y, hovered ? 23 : 0,
                    forward ? 192 : 205, 23, 13, 256, 256);
        }
    }

    private static final class IconButton extends GuiButton {
        private final int iconX, iconY;
        private IconButton(int id, int x, int y, int iconX, int iconY) {
            super(id, x, y, 18, 18, ""); this.iconX = iconX; this.iconY = iconY;
        }
        @Override public void drawButton(Minecraft mc, int mx, int my, float partial) {
            if (!visible) return;
            hovered = mx >= x && my >= y && mx < x + width && my < y + height;
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D(); GlStateManager.enableAlpha(); GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0); GlStateManager.color(1, 1, 1, 1);
            mc.getTextureManager().bindTexture(new ResourceLocation("create", "textures/gui/widgets.png"));
            int background = !enabled ? 90 : hovered ? (Mouse.isButtonDown(0) ? 36 : 18) : 0;
            Gui.drawModalRectWithCustomSizedTexture(x, y, background, 0, 18, 18, 256, 256);
            mc.getTextureManager().bindTexture(new ResourceLocation("create", "textures/gui/icons.png"));
            Gui.drawModalRectWithCustomSizedTexture(x + 1, y + 1, iconX * 16, iconY * 16, 16, 16, 256, 256);
        }
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
