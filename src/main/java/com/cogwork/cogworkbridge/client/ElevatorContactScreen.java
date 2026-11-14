package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.contraptions.elevator.ElevatorContactConfigPacket;
import com.cogwork.cogworkbridge.content.contraptions.elevator.RedstoneContactTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.IOException;

public final class ElevatorContactScreen extends GuiScreen {
    private static final ResourceLocation PANEL = new ResourceLocation("create", "textures/gui/display_link.png");
    private final BlockPos pos;
    private final int contraptionId;
    private GuiTextField shortName, longName;
    private int left, top, mode;
    public ElevatorContactScreen(BlockPos pos) { this.pos = pos.toImmutable(); this.contraptionId = -1; }
    public ElevatorContactScreen(BearingContraptionEntity contraption, BlockPos pos) {
        this.pos = pos.toImmutable(); this.contraptionId = contraption.getEntityId();
    }
    @Override public void initGui() {
        left = (width - 233) / 2; top = (height - 82) / 2;
        net.minecraft.world.World targetWorld = mc.world;
        if (contraptionId >= 0 && mc.world.getEntityByID(contraptionId) instanceof BearingContraptionEntity contraption)
            targetWorld = contraption.getInteractionWorld();
        RedstoneContactTileEntity tile = targetWorld.getTileEntity(pos) instanceof RedstoneContactTileEntity c ? c : null;
        shortName = new GuiTextField(0, fontRenderer, left + 33, top + 30, 30, 10);
        longName = new GuiTextField(1, fontRenderer, left + 63, top + 30, 140, 10);
        shortName.setEnableBackgroundDrawing(false); shortName.setMaxStringLength(4);
        longName.setEnableBackgroundDrawing(false); longName.setMaxStringLength(30);
        if (tile != null) { shortName.setText(tile.getShortName()); longName.setText(tile.getLongName()); mode = tile.getDoorMode(); }
        shortName.setFocused(true);
        buttonList.add(new CreateIconButton(0, left + 200, top + 58, 0, 1));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id != 0) return;
        ModNetwork.CHANNEL.sendToServer(new ElevatorContactConfigPacket(contraptionId, pos, shortName.getText(), longName.getText(), mode));
        mc.displayGuiScreen(null);
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        shortName.mouseClicked(x, y, button); longName.mouseClicked(x, y, button);
        if (x >= left + 58 && x < left + 111 && y >= top + 57 && y < top + 73) {
            mode = Math.floorMod(mode + (button == 1 ? -1 : 1), 6); return;
        }
        super.mouseClicked(x, y, button);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        int x = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
        int y = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
        if (wheel != 0 && x >= left + 58 && x < left + 111 && y >= top + 57 && y < top + 73)
            mode = Math.floorMod(mode + (wheel > 0 ? -1 : 1), 6);
    }
    @Override protected void keyTyped(char typed, int key) throws IOException {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { actionPerformed(buttonList.get(0)); return; }
        if (key != Keyboard.KEY_ESCAPE && (shortName.textboxKeyTyped(typed, key) || longName.textboxKeyTyped(typed, key))) return;
        super.keyTyped(typed, key);
    }
    @Override public void updateScreen() { shortName.updateCursorCounter(); longName.updateCursorCounter(); }
    @Override public void onGuiClosed() {
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        super.onGuiClosed();
    }
    @Override public void drawScreen(int x, int y, float partial) {
        drawDefaultBackground(); GlStateManager.color(1, 1, 1, 1); mc.getTextureManager().bindTexture(PANEL);
        drawTexturedModalRect(left, top, 20, 172, 233, 82);
        String title = I18n.format("gui.cogworkbridge.elevator.title");
        fontRenderer.drawString(title, left + (225 - fontRenderer.getStringWidth(title)) / 2, top + 6, 0x2f3738);
        shortName.drawTextBox(); longName.drawTextBox();
        fontRenderer.drawStringWithShadow(I18n.format("gui.cogworkbridge.elevator.door." + mode), left + 62, top + 61, 0xffffff);
        super.drawScreen(x, y, partial);
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
