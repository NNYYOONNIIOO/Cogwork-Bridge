package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelConfigPacket;
import com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelConnectionPacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import java.io.IOException;

public final class FactoryPanelScreen extends GuiScreen {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("create", "textures/gui/factory_gauge.png");
    private final BlockPos pos; private final int slot;
    private GuiTextField count, address, output, inputAmount;
    private LogisticsAddressSuggestions suggestions;
    private boolean crafting;
    public FactoryPanelScreen(BlockPos pos, int slot) { this.pos = pos.toImmutable(); this.slot = slot; }
    @Override public void initGui() {
        int x = (width - 200) / 2, y = (height - 160) / 2;
        int value = 0, result = 1; String destination = "";
        if (mc.world.getTileEntity(pos) instanceof FactoryPanelTileEntity panel) { value = panel.target(slot); destination = panel.address(slot); result = panel.recipeOutput(slot); crafting = panel.crafting(slot); }
        count = new GuiTextField(0, fontRenderer, x + 146, y + 98, 42, 12); count.setMaxStringLength(4); count.setText(Integer.toString(value));
        output = new GuiTextField(2, fontRenderer, x + 160, y + 70, 28, 12); output.setMaxStringLength(2); output.setText(Integer.toString(result));
        address = new GuiTextField(1, fontRenderer, x + 36, y + 109, 108, 10); address.setMaxStringLength(25); address.setText(destination);
        suggestions = new LogisticsAddressSuggestions(address);
        inputAmount = new GuiTextField(3, fontRenderer, x + 8, y + 80, 21, 12); inputAmount.setMaxStringLength(2); inputAmount.setText("1");
        buttonList.clear(); buttonList.add(new CreateIconButton(0, x + 167, y + 135, 0, 1));
        buttonList.add(new net.minecraft.client.gui.GuiButton(1, x + 31, y + 47, 20, 18, "+"));
        buttonList.add(new net.minecraft.client.gui.GuiButton(2, x + 31, y + 67, 20, 18, "x")); Keyboard.enableRepeatEvents(true);
        buttonList.add(new net.minecraft.client.gui.GuiButton(3, x + 31, y + 27, 23, 18, crafting ? "3x3" : "-"));
    }
    private void send() { send(false); }
    private void send(boolean clearPromises) {
        try { ModNetwork.CHANNEL.sendToServer(new FactoryPanelConfigPacket(pos, slot, Integer.parseInt(count.getText()), address.getText(), Integer.parseInt(output.getText()), crafting, clearPromises)); }
        catch (NumberFormatException ignored) { }
    }
    @Override protected void actionPerformed(net.minecraft.client.gui.GuiButton button) {
        if (button.id == 0) { send(); mc.displayGuiScreen(null); }
        if (button.id == 1) {
            try {
                int amount = Integer.parseInt(inputAmount.getText());
                if (amount < 1 || amount > 64) return;
                send(); FactoryPanelConnectionClient.begin(pos, slot, amount); mc.displayGuiScreen(null);
            } catch (NumberFormatException ignored) { }
        }
        if (button.id == 2) ModNetwork.CHANNEL.sendToServer(new FactoryPanelConnectionPacket(pos, slot, pos, 0, 0));
        if (button.id == 3) { crafting = !crafting; button.displayString = crafting ? "3x3" : "-"; }
    }
    @Override protected void keyTyped(char ch, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) { send(); mc.displayGuiScreen(null); return; }
        if (suggestions.key(key) || count.textboxKeyTyped(ch, key) || address.textboxKeyTyped(ch, key) || output.textboxKeyTyped(ch, key) || inputAmount.textboxKeyTyped(ch, key)) return; super.keyTyped(ch, key);
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (suggestions.click(x, y, button)) return;
        int left = (width - 200) / 2, top = (height - 160) / 2;
        if (button == 0 && x >= left + 68 && x < left + 84 && y >= top + 136 && y < top + 152) { send(true); return; }
        count.mouseClicked(x, y, button); address.mouseClicked(x, y, button); output.mouseClicked(x, y, button); inputAmount.mouseClicked(x, y, button); super.mouseClicked(x, y, button);
    }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); for (var button : buttonList) if (button instanceof CreateIconButton icon) icon.release(); super.onGuiClosed(); }
    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground(); int x = (width - 200) / 2, y = (height - 160) / 2;
        GlStateManager.color(1, 1, 1, 1); mc.getTextureManager().bindTexture(BACKGROUND);
        drawTexturedModalRect(x, y, 32, 0, 192, 96); drawTexturedModalRect(x, y + 96, 32, 176, 200, 64);
        String title = I18n.format("tile.cogworkbridge.factory_gauge.name"); fontRenderer.drawString(title, x + (194 - fontRenderer.getStringWidth(title)) / 2, y + 5, 0x3d3c48);
        count.drawTextBox(); address.drawTextBox(); output.drawTextBox(); inputAmount.drawTextBox();
        if (mc.world.getTileEntity(pos) instanceof FactoryPanelTileEntity panel) {
            fontRenderer.drawString(I18n.format("cogworkbridge.factory.stock", panel.current(slot)), x + 18, y + 126, 0x592424);
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            var entries = panel.inputs(slot);
            for (int i = 0; i < Math.min(9, entries.size()); i++) {
                var connection = entries.get(i);
                if (!(mc.world.getTileEntity(connection.pos()) instanceof FactoryPanelTileEntity source)) continue;
                var item = source.filter(connection.slot()); if (item.isEmpty()) continue;
                int itemX = x + 68 + i % 3 * 20, itemY = y + 28 + i / 3 * 20;
                mc.getRenderItem().renderItemAndEffectIntoGUI(item, itemX, itemY);
                mc.getRenderItem().renderItemOverlayIntoGUI(fontRenderer, item, itemX, itemY, Integer.toString(connection.amount()));
            }
            var result = panel.filter(slot);
            if (!result.isEmpty()) mc.getRenderItem().renderItemAndEffectIntoGUI(result, x + 160, y + 48);
            var parcel = new net.minecraft.item.ItemStack(com.cogwork.cogworkbridge.registry.AllItems.CARDBOARD_PACKAGES[0]);
            mc.getRenderItem().renderItemAndEffectIntoGUI(parcel, x + 68, y + 136);
            mc.getRenderItem().renderItemOverlayIntoGUI(fontRenderer, parcel, x + 68, y + 136, Integer.toString(panel.promised(slot)));
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        }
        super.drawScreen(mouseX, mouseY, partial);
        if (mouseX >= x + 68 && mouseX < x + 84 && mouseY >= y + 136 && mouseY < y + 152)
            drawHoveringText(I18n.format("cogworkbridge.factory.clear_promises"), mouseX, mouseY);
        suggestions.draw();
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
