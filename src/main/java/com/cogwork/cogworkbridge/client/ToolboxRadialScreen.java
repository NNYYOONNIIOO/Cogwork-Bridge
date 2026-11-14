package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.equipment.ToolboxActionPacket;
import com.cogwork.cogworkbridge.content.equipment.ToolboxTileEntity;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.List;

/** The four states of the upstream toolbox radial menu, using its original widget atlas. */
public final class ToolboxRadialScreen extends GuiScreen {
    private static final ResourceLocation WIDGETS = new ResourceLocation("create", "textures/gui/widgets.png");
    private static final ResourceLocation ICONS = new ResourceLocation("create", "textures/gui/icons.png");
    private static final int DEPOSIT = -7, UNEQUIP = -5;
    private enum State { SELECT_BOX, SELECT_ITEM, SELECT_ITEM_UNEQUIP, DETACH }

    private final List<ToolboxTileEntity> boxes;
    private State state;
    private ToolboxTileEntity selected;
    private int hovered = -1, hotbar, scrollSlot, ticksOpen;
    private boolean scrollMode, cancelled;

    public ToolboxRadialScreen(List<ToolboxTileEntity> boxes, NBTTagCompound link) {
        this.boxes = boxes;
        if (link.hasUniqueId("Id")) {
            Minecraft minecraft = Minecraft.getMinecraft();
            BlockPos pos = BlockPos.fromLong(link.getLong("Pos"));
            if (minecraft.player != null && minecraft.world != null
                    && link.getInteger("Dimension") == minecraft.player.dimension
                    && minecraft.world.isBlockLoaded(pos) && minecraft.player.getDistanceSq(pos) < 100D
                    && minecraft.world.getTileEntity(pos) instanceof ToolboxTileEntity box
                    && !box.isInvalid() && box.identity().equals(link.getUniqueId("Id"))) {
                selected = box;
            }
            state = selected == null ? State.DETACH : State.SELECT_ITEM_UNEQUIP;
            scrollSlot = Math.floorMod(link.getInteger("Compartment"), 8);
        } else if (boxes.size() == 1) {
            selected = boxes.get(0);
            state = State.SELECT_ITEM;
        } else {
            state = State.SELECT_BOX;
        }
    }

    @Override public void initGui() { hotbar = mc.player.inventory.currentItem; }
    @Override public boolean doesGuiPauseGame() { return false; }

    private boolean keyHeld() {
        return ToolboxClientEvents.isToolbeltHeld();
    }

    @Override public void updateScreen() {
        ticksOpen++;
        if (!keyHeld()) mc.displayGuiScreen(null);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        float fade = Math.max(1F / 512F, Math.min(1F, (ticksOpen + partial) / 10F));
        int backgroundAlpha = Math.round(102F * Math.min(1F, (ticksOpen + partial) / 20F));
        drawRect(0, 0, width, height, backgroundAlpha << 24);
        int cx = width / 2, cy = height / 2;
        double dx = mouseX - cx, dy = mouseY - cy, distance = dx * dx + dy * dy;
        hovered = -1;
        if (distance > 25D && distance < 10000D)
            hovered = Math.floorMod((int) Math.floor((Math.toDegrees(Math.atan2(dy, dx)) + 157.5D) / 45D), 8);
        if (scrollMode && distance > 150D) scrollMode = false;
        if (state == State.SELECT_ITEM_UNEQUIP && distance <= 150D) hovered = UNEQUIP;

        String tip = null;
        int tipColor = 0xffffff;
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);

        if (state == State.DETACH) {
            tip = I18n.format("gui.cogworkbridge.toolbox.out_of_range");
            if (dx > -20D && dx < 20D && dy > -80D && dy < -20D) hovered = UNEQUIP;
            drawWidget(cx - 12, cy - 12, 52, 98, 22, 22);
            drawItem(new ItemStack(Item.getItemFromBlock(AllBlocks.TOOLBOXES[12])), cx - 9, cy - 9);
            int y = cy + Math.round(-40F + 10F * (1F - fade) * (1F - fade));
            drawWidget(cx - 12, y - 12, 0, 68, 22, 22);
            drawIcon(cx - 9, y - 9, 8, 10);
            if (hovered == UNEQUIP && !scrollMode) {
                drawHighlight(cx, y);
                tip = I18n.format("gui.cogworkbridge.toolbox.detach");
                tipColor = 0xffaa00;
            }
        } else {
            if (dx > 60D && dx < 100D && dy > -20D && dy < 20D) hovered = DEPOSIT;
            int depositX = cx + Math.round(80F - 5F * (1F - fade) * (1F - fade));
            drawWidget(depositX - 12, cy - 12, 0, 68, 22, 22);
            drawIcon(depositX - 9, cy - 9, 11, 3);
            if (hovered == DEPOSIT && !scrollMode) {
                drawHighlight(depositX, cy);
                tip = I18n.format(state == State.SELECT_BOX
                        ? "gui.cogworkbridge.toolbox.deposit_all" : "gui.cogworkbridge.toolbox.deposit_box");
                tipColor = 0xffaa00;
            }

            float radius = 40F - 10F * (1F - fade) * (1F - fade);
            for (int slot = 0; slot < 8; slot++) {
                double angle = -3D * Math.PI / 4D + slot * Math.PI / 4D;
                int x = cx + (int) Math.round(Math.cos(angle) * radius);
                int y = cy + (int) Math.round(Math.sin(angle) * radius);
                ItemStack stack = ItemStack.EMPTY;
                boolean active = false;
                if (state == State.SELECT_BOX) {
                    if (slot < boxes.size()) {
                        stack = new ItemStack(Item.getItemFromBlock(boxes.get(slot).getBlockType()));
                        active = true;
                    }
                } else if (selected != null) {
                    stack = selected.filter(slot);
                    active = !stack.isEmpty() && selected.count(slot) > 0;
                }
                drawWidget(x - 12, y - 12, stack.isEmpty() ? 27 : active ? 0 : 52,
                        stack.isEmpty() || !active ? 98 : 68, 22, 22);
                if (!stack.isEmpty()) drawItem(stack, x - 9, y - 9);
                if (slot == (scrollMode ? scrollSlot : hovered) && active) {
                    drawHighlight(x, y);
                    tip = state == State.SELECT_BOX ? boxes.get(slot).getDisplayName().getFormattedText() : stack.getDisplayName();
                    tipColor = 0xffffff;
                }
            }

            if (state == State.SELECT_ITEM_UNEQUIP) {
                drawWidget(cx - 12, cy - 12, 0, 68, 22, 22);
                drawIcon(cx - 9, cy - 9, scrollMode ? 3 : 8, scrollMode ? 1 : 4);
                if (!scrollMode && hovered == UNEQUIP) {
                    drawHighlight(cx, cy);
                    tip = I18n.format("gui.cogworkbridge.toolbox.unequip", mc.player.getHeldItemMainhand().getDisplayName());
                    tipColor = 0xffaa00;
                }
            }
        }

        int alpha = Math.min(255, (int) (fade * 255F));
        if (tip != null && alpha > 8) {
            int x = cx - fontRenderer.getStringWidth(tip) / 2;
            fontRenderer.drawString(tip, x, height - 72, (alpha << 24) | tipColor);
        }
        GlStateManager.color(1, 1, 1, 1);
        super.drawScreen(mouseX, mouseY, partial);
    }

    private void drawWidget(int x, int y, int u, int v, int w, int h) {
        mc.getTextureManager().bindTexture(WIDGETS);
        GlStateManager.color(1, 1, 1, 1);
        Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, w, h, 256, 256);
    }
    private void drawHighlight(int x, int y) { drawWidget(x - 13, y - 13, 27, 67, 24, 24); }
    private void drawIcon(int x, int y, int column, int row) {
        mc.getTextureManager().bindTexture(ICONS);
        GlStateManager.color(1, 1, 1, 1);
        Gui.drawModalRectWithCustomSizedTexture(x, y, column * 16, row * 16, 16, 16, 256, 256);
    }
    private void drawItem(ItemStack stack, int x, int y) {
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableBlend();
    }
    private boolean selectable(int slot) {
        if (slot < 0 || slot >= 8) return false;
        if (state == State.SELECT_BOX) return slot < boxes.size();
        return selected != null && !selected.filter(slot).isEmpty() && selected.count(slot) > 0;
    }
    private int selection() { return scrollMode ? scrollSlot : hovered; }

    @Override public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && state != State.DETACH) {
            int x = Mouse.getEventX() * width / mc.displayWidth - width / 2;
            int y = height - Mouse.getEventY() * height / mc.displayHeight - 1 - height / 2;
            if (x * x + y * y <= 150) {
                scrollMode = true;
                int direction = wheel > 0 ? -1 : 1;
                for (int i = 0; i < 8; i++) {
                    scrollSlot = Math.floorMod(scrollSlot + direction, 8);
                    if (selectable(scrollSlot)) break;
                }
            }
        }
        super.handleMouseInput();
    }

    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        int chosen = selection();
        if (button == 0) {
            if (chosen == DEPOSIT && state != State.DETACH) { mc.displayGuiScreen(null); return; }
            if (state == State.SELECT_BOX && selectable(chosen)) {
                selected = boxes.get(chosen);
                state = State.SELECT_ITEM;
                hovered = -1;
                return;
            }
            if (chosen == UNEQUIP || selectable(chosen)) { mc.displayGuiScreen(null); return; }
        } else if (button == 1) {
            if (state == State.SELECT_ITEM && boxes.size() > 1) {
                state = State.SELECT_BOX;
                selected = null;
                hovered = -1;
                return;
            }
            if (state == State.SELECT_ITEM_UNEQUIP && chosen == UNEQUIP) {
                if (boxes.size() > 1) {
                    ModNetwork.CHANNEL.sendToServer(new ToolboxActionPacket(1, BlockPos.ORIGIN, 0, hotbar));
                    state = State.SELECT_BOX;
                    selected = null;
                    hovered = -1;
                } else mc.displayGuiScreen(null);
                return;
            }
        }
        super.mouseClicked(x, y, button);
    }

    @Override protected void keyTyped(char typed, int code) throws IOException {
        if (code == Keyboard.KEY_ESCAPE) { cancelled = true; mc.displayGuiScreen(null); return; }
        for (int i = 0; i < 8; i++) {
            if (mc.gameSettings.keyBindsHotbar[i].getKeyCode() != code || !selectable(i)) continue;
            scrollMode = true;
            scrollSlot = i;
            mouseClicked(0, 0, 0);
            return;
        }
        super.keyTyped(typed, code);
    }

    @Override public void onGuiClosed() {
        ToolboxClientEvents.menuClosed();
        if (cancelled || mc.player == null) return;
        int chosen = selection();
        if (chosen == DEPOSIT && state != State.DETACH) {
            if (state == State.SELECT_BOX) {
                for (ToolboxTileEntity box : boxes)
                    ModNetwork.CHANNEL.sendToServer(new ToolboxActionPacket(2, box.getPos(), 0, hotbar));
            } else if (selected != null)
                ModNetwork.CHANNEL.sendToServer(new ToolboxActionPacket(2, selected.getPos(), 0, hotbar));
        } else if (chosen == UNEQUIP && (state == State.SELECT_ITEM_UNEQUIP || state == State.DETACH)) {
            ModNetwork.CHANNEL.sendToServer(new ToolboxActionPacket(1, BlockPos.ORIGIN, 0, hotbar));
        } else if (state != State.SELECT_BOX && state != State.DETACH && selectable(chosen)) {
            ModNetwork.CHANNEL.sendToServer(new ToolboxActionPacket(0, selected.getPos(), chosen, hotbar));
        }
    }
}
