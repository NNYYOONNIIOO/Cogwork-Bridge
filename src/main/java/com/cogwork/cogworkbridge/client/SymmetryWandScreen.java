package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.equipment.SymmetryWandItem;
import com.cogwork.cogworkbridge.content.equipment.SymmetryWandConfigPacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import java.io.IOException;

/** Configuration using the upstream symmetry wand panel and two selection rows. */
public final class SymmetryWandScreen extends GuiScreen {
    private static final ResourceLocation PANEL = new ResourceLocation("create", "textures/gui/curiosities.png");
    private static final ResourceLocation ICONS = new ResourceLocation("create", "textures/gui/icons.png");
    private final EnumHand hand;
    private int selectedSlot;
    private int mode;
    private int alignment;
    private boolean initialized;

    public SymmetryWandScreen(EnumHand hand) { this.hand = hand; }

    @Override public void initGui() {
        if (!initialized) {
            ItemStack wand = mc.player.getHeldItem(hand);
            selectedSlot = mc.player.inventory.currentItem;
            mode = SymmetryWandItem.mode(wand);
            alignment = SymmetryWandItem.alignment(wand);
            initialized = true;
        }
    }

    private boolean inRow(int mouseX, int mouseY, int row) {
        int left = (width - 188) / 2 - 20, top = (height - 101) / 2;
        int y = top + (row == 0 ? 21 : 43);
        return mouseX >= left + 45 && mouseX < left + 154 && mouseY >= y && mouseY < y + 18;
    }

    private void change(int row, int delta) {
        if (row == 0) { mode = Math.floorMod(mode + delta, 3); alignment = 0; }
        else if (mode != 2) alignment = Math.floorMod(alignment + delta, 2);
        ItemStack current = mc.player.getHeldItem(hand);
        if (!(current.getItem() instanceof SymmetryWandItem)
                || hand == EnumHand.MAIN_HAND && mc.player.inventory.currentItem != selectedSlot) return;
        SymmetryWandItem.configure(current, mode, alignment);
        ModNetwork.CHANNEL.sendToServer(new SymmetryWandConfigPacket(hand, selectedSlot, mode, alignment));
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = (width - 188) / 2 - 20, top = (height - 101) / 2;
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(PANEL);
        drawTexturedModalRect(left, top, 0, 131, 188, 101);
        String title = I18n.format("item.cogworkbridge.wand_of_symmetry.name");
        fontRenderer.drawString(title, left + (188 - fontRenderer.getStringWidth(title)) / 2, top + 4, 0x592424);
        String[] types = {"gui.cogworkbridge.symmetry.plane", "gui.cogworkbridge.symmetry.cross", "gui.cogworkbridge.symmetry.triple"};
        String orientation = mode == 2 ? "gui.cogworkbridge.symmetry.orthogonal"
                : mode == 1 ? (alignment == 0 ? "gui.cogworkbridge.symmetry.orthogonal" : "gui.cogworkbridge.symmetry.diagonal")
                : alignment == 0 ? "gui.cogworkbridge.symmetry.z" : "gui.cogworkbridge.symmetry.x";
        if (inRow(mouseX, mouseY, 0)) drawRect(left + 45, top + 21, left + 154, top + 39, 0x33ffffff);
        if (inRow(mouseX, mouseY, 1)) drawRect(left + 45, top + 43, left + 154, top + 61, 0x33ffffff);
        fontRenderer.drawStringWithShadow(I18n.format(types[mode]), left + 51, top + 27, 0xffffff);
        fontRenderer.drawStringWithShadow(I18n.format(orientation), left + 51, top + 49, 0xffffff);
        mc.getTextureManager().bindTexture(ICONS);
        GlStateManager.color(1, 1, 1, 1);
        drawTexturedModalRect(left + 155, top + 77, 0, 16, 16, 16);
        if (inRow(mouseX, mouseY, 0)) drawHoveringText(I18n.format("gui.cogworkbridge.symmetry.type"), mouseX, mouseY);
        else if (inRow(mouseX, mouseY, 1)) drawHoveringText(I18n.format("gui.cogworkbridge.symmetry.orientation"), mouseX, mouseY);
    }

    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        int left = (width - 188) / 2 - 20, top = (height - 101) / 2;
        if (x >= left + 155 && x < left + 173 && y >= top + 77 && y < top + 95) {
            mc.displayGuiScreen(null);
        } else if (inRow(x, y, 0)) change(0, button == 1 ? -1 : 1);
        else if (inRow(x, y, 1)) change(1, button == 1 ? -1 : 1);
        else super.mouseClicked(x, y, button);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (inRow(x, y, 0)) change(0, wheel > 0 ? 1 : -1);
        else if (inRow(x, y, 1)) change(1, wheel > 0 ? 1 : -1);
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
