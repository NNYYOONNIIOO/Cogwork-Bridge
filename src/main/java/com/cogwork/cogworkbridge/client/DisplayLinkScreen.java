package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.display.DisplayLinkConfigPacket;
import com.cogwork.cogworkbridge.content.display.DisplayLinkTileEntity;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Create's display-link panel, with source selection, label and target line. */
public final class DisplayLinkScreen extends GuiScreen {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("create", "textures/gui/display_link.png");
    private final BlockPos pos;
    private int mode, line, left, top;
    private boolean initialized;
    private boolean alternateFormat;
    private int rateInterval;
    private int namePercent=50,platformChars=3;
    private String savedLabel = "";
    private GuiTextField label;
    private final Map<Integer, String> labels = new HashMap<>();
    private final Map<Integer, Boolean> formats = new HashMap<>();
    private final Map<Integer, Integer> intervals = new HashMap<>();

    public DisplayLinkScreen(BlockPos pos) { this.pos = pos.toImmutable(); }
    private DisplayLinkTileEntity link() {
        if (mc.world == null) return null;
        TileEntity tile = mc.world.getTileEntity(pos);
        return tile instanceof DisplayLinkTileEntity ? (DisplayLinkTileEntity) tile : null;
    }
    @Override public void initGui() {
        if (label != null) savedLabel = label.getText();
        DisplayLinkTileEntity link = link();
        if (!initialized && link != null) {
            mode = link.getMode(); line = link.getTargetLine(); savedLabel = link.getLabel(); alternateFormat = link.getAlternateFormat(); rateInterval = link.getRateInterval(); initialized = true;
            namePercent=link.getTrainNamePercent();platformChars=link.getPlatformChars();
        }
        left = (width - 235) / 2; top = (height - 162) / 2;
        label = new GuiTextField(0, fontRenderer, left + 65, top + 53, 125, 12);
        label.setEnableBackgroundDrawing(false); label.setMaxStringLength(64); label.setText(savedLabel);
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        buttonList.clear(); buttonList.add(new CreateIconButton(0, left + 202, top + 138, 0, 1));
    }
    private void selectMode(int delta) {
        DisplayLinkTileEntity link = link();
        if (link == null) return;
        List<Integer> choices = link.getAvailableModes();
        if (choices.isEmpty()) return;
        labels.put(mode, label.getText()); formats.put(mode, alternateFormat); intervals.put(mode, rateInterval);
        ModNetwork.CHANNEL.sendToServer(new DisplayLinkConfigPacket(pos, mode, line, label.getText(), alternateFormat, rateInterval,namePercent,platformChars));
        int current = choices.indexOf(mode);
        mode = choices.get(Math.floorMod((current < 0 ? 0 : current) + delta, choices.size()));
        label.setText(labels.containsKey(mode) ? labels.get(mode) : link.getLabelFor(mode));
        alternateFormat = formats.containsKey(mode) ? formats.get(mode) : link.getAlternateFormatFor(mode);
        rateInterval = intervals.containsKey(mode) ? intervals.get(mode) : link.getRateIntervalFor(mode);
    }
    private void selectLine(int delta) {
        DisplayLinkTileEntity link = link();
        if (link != null) line = Math.max(0, Math.min(Math.max(0, link.getTargetLineCount() - 1), line + delta));
    }
    private boolean hasFormat() { return mode == 1 || mode == 2 || mode == 3 || mode == 6 || mode == 8
            || mode == 11 || mode == 12 || mode == 14 || mode == 16 || mode == 19; }
    private void selectFormat(int delta) {
        if (mode == 2 || mode == 16) rateInterval = Math.floorMod(rateInterval + delta, mode == 2 ? 5 : 3);
        else alternateFormat = !alternateFormat;
    }
    private boolean in(int x, int y, int rx, int ry, int w, int h) {
        return x >= left + rx && x < left + rx + w && y >= top + ry && y < top + ry + h;
    }
    @Override protected void actionPerformed(GuiButton button) { if (button.id == 0) mc.displayGuiScreen(null); }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        label.mouseClicked(x, y, button);
        if(mode==22&&in(x,y,61,70,135,16)){if(x<left+140)namePercent=Math.max(0,Math.min(72,namePercent+(button==1?-1:1)));else platformChars=Math.floorMod(platformChars+(button==1?-1:1),16);return;}
        if (in(x, y, 61, 26, 135, 16)) { selectMode(button == 1 ? -1 : 1); return; }
        if (hasFormat() && in(x, y, 61, 70, 135, 16)) { selectFormat(button == 1 ? -1 : 1); return; }
        if (in(x, y, 61, 105, 135, 16)) { selectLine(button == 1 ? -1 : 1); return; }
        super.mouseClicked(x, y, button);
    }
    @Override public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int x = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
            int y = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
            if(mode==22&&in(x,y,61,70,135,16)){if(x<left+140)namePercent=Math.max(0,Math.min(72,namePercent+(wheel>0?1:-1)));else platformChars=Math.max(0,Math.min(15,platformChars+(wheel>0?1:-1)));return;}
            if (in(x, y, 61, 26, 135, 16)) { selectMode(wheel > 0 ? -1 : 1); return; }
            if (hasFormat() && in(x, y, 61, 70, 135, 16)) { selectFormat(wheel > 0 ? -1 : 1); return; }
            if (in(x, y, 61, 105, 135, 16)) { selectLine(wheel > 0 ? -1 : 1); return; }
        }
        super.handleMouseInput();
    }
    @Override protected void keyTyped(char typed, int key) throws IOException {
        if (key != Keyboard.KEY_ESCAPE && label.textboxKeyTyped(typed, key)) return;
        super.keyTyped(typed, key);
    }
    @Override public void updateScreen() { label.updateCursorCounter(); }
    @Override public void onGuiClosed() {
        if (initialized && mc.player != null)
            ModNetwork.CHANNEL.sendToServer(new DisplayLinkConfigPacket(pos, mode, line, label == null ? savedLabel : label.getText(), alternateFormat, rateInterval,namePercent,platformChars));
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        super.onGuiClosed();
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(BACKGROUND);
        drawTexturedModalRect(left, top, 0, 0, 235, 162);
        String title = I18n.format("tile.cogworkbridge.display_link.name");
        fontRenderer.drawString(title, left + (235 - fontRenderer.getStringWidth(title)) / 2, top + 4, 0x592424);
        DisplayLinkTileEntity link = link();
        if (link != null) {
            drawBlockIcon(link.getSourcePosition(), left + 37, top + 26);
            if (link.hasTarget()) drawBlockIcon(link.getTargetPosition(), left + 37, top + 105);
            String source = link.getAvailableModes().isEmpty() ? I18n.format("gui.cogworkbridge.display.no_source")
                    : I18n.format("gui.cogworkbridge.display.mode." + mode);
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(source, 127), left + 65, top + 30, 0xeeeeee);
            String target = link.getTargetLineCount() == 0 ? I18n.format("gui.cogworkbridge.display.no_target")
                    : I18n.format(com.cogwork.cogworkbridge.content.display.LecternDisplayTarget.supports(mc.world.getTileEntity(link.getTargetPosition()))
                            ? "gui.cogworkbridge.display.page" : "gui.cogworkbridge.display.line", Math.min(line + 1, link.getTargetLineCount()));
            fontRenderer.drawStringWithShadow(target, left + 65, top + 109, 0xeeeeee);
        }
        label.setVisible(mode != 10 && mode != 11 && mode != 12 && mode != 17 && mode != 19 && mode != 21);
        if (label.getVisible() && label.getText().isEmpty() && !label.isFocused())
            fontRenderer.drawStringWithShadow(I18n.format(mode==22?"gui.cogworkbridge.display.station_filter":mode == 14 ? "gui.cogworkbridge.display.objective" : "gui.cogworkbridge.display.label"), left + 65, top + 53, 0x999999);
        label.drawTextBox();
        if(mode==22)fontRenderer.drawStringWithShadow(I18n.format("gui.cogworkbridge.display.columns",namePercent,platformChars),left+65,top+75,0xeeeeee);
        if (hasFormat())
            fontRenderer.drawStringWithShadow(I18n.format("gui.cogworkbridge.display.format." + mode + "." + (mode == 2 || mode == 16 ? rateInterval : alternateFormat ? 1 : 0)),
                    left + 65, top + 75, 0xeeeeee);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (in(mouseX, mouseY, 61, 26, 135, 16) || in(mouseX, mouseY, 61, 105, 135, 16)
                || hasFormat() && in(mouseX, mouseY, 61, 70, 135, 16))
            drawHoveringText(I18n.format("gui.cogworkbridge.display.scroll"), mouseX, mouseY);
    }
    private void drawBlockIcon(BlockPos at, int x, int y) {
        if (mc.world == null || !mc.world.isBlockLoaded(at)) return;
        Block block = mc.world.getBlockState(at).getBlock();
        Item item = Item.getItemFromBlock(block);
        ItemStack stack = item == Items.AIR ? new ItemStack(net.minecraft.init.Blocks.BARRIER) : new ItemStack(item);
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting(); GlStateManager.color(1, 1, 1, 1);
    }
    @Override public boolean doesGuiPauseGame() { return false; }

}
