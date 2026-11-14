package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.schematics.SchematicData;
import com.cogwork.cogworkbridge.content.schematics.SchematicEditPacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.IOException;

/** The same coordinate and placement settings panel used by Create's schematic. */
public final class SchematicEditScreen extends GuiScreen {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("create", "textures/gui/schematics.png");
    private static final String[] ROTATIONS = {"none", "cw90", "cw180", "cw270"};
    private static final String[] MIRRORS = {"none", "left_right", "front_back"};
    private final GuiTextField[] coordinates = new GuiTextField[3];
    private int slot, left, top, rotation, mirror;
    private String fileId, title;

    @Override public void initGui() {
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        slot = mc.player.inventory.currentItem;
        ItemStack stack = mc.player.inventory.getStackInSlot(slot);
        fileId = stack.hasTagCompound() ? stack.getTagCompound().getString("FileId") : "";
        title = stack.hasTagCompound() ? stack.getTagCompound().getString("Name") : "";
        if (title.isEmpty()) title = I18n.format("item.cogworkbridge.schematic.name");
        left = (width - 192) / 2 - 6; top = (height - 123) / 2;
        BlockPos anchor = stack.hasTagCompound() && stack.getTagCompound().getBoolean("Deployed")
                ? SchematicData.anchor(stack) : mc.player.getPosition();
        int[] values = {anchor.getX(), anchor.getY(), anchor.getZ()};
        for (int i = 0; i < coordinates.length; i++) {
            GuiTextField field = new GuiTextField(i, fontRenderer, left + 50 + i * 40, top + 28, 34, 10);
            field.setEnableBackgroundDrawing(false); field.setTextColor(0xffffff); field.setMaxStringLength(6);
            field.setValidator(value -> value.isEmpty() || value.equals("-") || value.matches("-?[0-9]+"));
            field.setText(Integer.toString(values[i]));
            coordinates[i] = field;
        }
        rotation = SchematicData.rotation(stack).ordinal();
        mirror = SchematicData.mirror(stack).ordinal();
        buttonList.clear();
        buttonList.add(new CreateIconButton(0, left + 159, top + 99, 0, 1));
        Keyboard.enableRepeatEvents(true);
    }
    @Override public void updateScreen() { for (GuiTextField field : coordinates) field.updateCursorCounter(); }
    @Override protected void actionPerformed(GuiButton button) { if (button.id == 0) mc.displayGuiScreen(null); }
    @Override protected void keyTyped(char c, int key) throws IOException {
        if (isKeyComboCtrlV(key)) {
            String[] parts = getClipboardString().replace(" ", "").split(",", -1);
            if (parts.length == 3) {
                boolean valid = true;
                for (String part : parts) valid &= part.matches("-?[0-9]{1,6}") && part.length() <= 6;
                if (valid) {
                    for (int i = 0; i < 3; i++) coordinates[i].setText(parts[i]);
                    return;
                }
            }
        }
        if (key == Keyboard.KEY_TAB) {
            for (int i = 0; i < coordinates.length; i++) if (coordinates[i].isFocused()) {
                coordinates[i].setFocused(false); coordinates[(i + 1) % 3].setFocused(true); return;
            }
            coordinates[0].setFocused(true); return;
        }
        for (GuiTextField field : coordinates) if (field.textboxKeyTyped(c, key)) return;
        super.keyTyped(c, key);
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        for (GuiTextField field : coordinates) field.mouseClicked(x, y, button);
        super.mouseClicked(x, y, button);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (wheel == 0 || x < left + 45 || x >= left + 163) return;
        int change = wheel > 0 ? 1 : -1;
        if (y >= top + 45 && y < top + 63) rotation = Math.floorMod(rotation + change, ROTATIONS.length);
        else if (y >= top + 67 && y < top + 85) mirror = Math.floorMod(mirror + change, MIRRORS.length);
    }
    @Override public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        try {
            if (coordinates[0] == null || mc.player == null || mc.player.inventory.currentItem != slot) return;
            int x = Integer.parseInt(coordinates[0].getText());
            int y = Integer.parseInt(coordinates[1].getText());
            int z = Integer.parseInt(coordinates[2].getText());
            ModNetwork.CHANNEL.sendToServer(new SchematicEditPacket(slot, fileId, x, y, z, rotation, mirror));
        } catch (NumberFormatException ignored) { }
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        GlStateManager.color(1, 1, 1, 1); mc.getTextureManager().bindTexture(BACKGROUND);
        drawTexturedModalRect(left, top, 10, 8, 192, 123);
        String clipped = fontRenderer.trimStringToWidth(title, 175);
        fontRenderer.drawString(clipped, left + (184 - fontRenderer.getStringWidth(clipped)) / 2, top + 4, 0x505050);
        fontRenderer.drawStringWithShadow(I18n.format("cogworkbridge.schematic.rotation." + ROTATIONS[rotation]), left + 50, top + 50, 0xffffff);
        fontRenderer.drawStringWithShadow(I18n.format("cogworkbridge.schematic.mirror." + MIRRORS[mirror]), left + 50, top + 72, 0xffffff);
        for (GuiTextField field : coordinates) field.drawTextBox();
        GlStateManager.pushMatrix(); GlStateManager.translate(left + 198, top + 83, 0); GlStateManager.scale(3, 3, 3);
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(AllItems.SCHEMATIC), 0, 0);
        RenderHelper.disableStandardItemLighting(); GlStateManager.popMatrix();
        super.drawScreen(mouseX, mouseY, partial);
        if (mouseX >= left + 45 && mouseX < left + 163) {
            if (mouseY >= top + 45 && mouseY < top + 63) drawHoveringText(I18n.format("cogworkbridge.schematic.rotate"), mouseX, mouseY);
            else if (mouseY >= top + 67 && mouseY < top + 85) drawHoveringText(I18n.format("cogworkbridge.schematic.mirror"), mouseX, mouseY);
        }
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
