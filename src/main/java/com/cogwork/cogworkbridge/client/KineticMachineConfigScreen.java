package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.kinetics.config.KineticConfigChangePacket;
import com.cogwork.cogworkbridge.content.kinetics.config.KineticMachineConfig;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

public final class KineticMachineConfigScreen extends GuiScreen {
    private static final ResourceLocation VALUE_SETTINGS =
            new ResourceLocation("create", "textures/gui/value_settings.png");
    private static final ResourceLocation ICONS =
            new ResourceLocation("create", "textures/gui/icons.png");
    private static final int ROW_HEIGHT = 11;
    private static final String[] DIRECTION_LABELS = {"\u00a7l\u27f3", "\u00a7l\u27f2"};
    private static final String[] WINDMILL_MODES = {"gui.cogworkbridge.kinetic.clockwise", "gui.cogworkbridge.kinetic.counter_clockwise"};
    private static final String[] BEARING_MODES = {
            "gui.cogworkbridge.kinetic.bearing.rotate_place",
            "gui.cogworkbridge.kinetic.bearing.rotate_return",
            "gui.cogworkbridge.kinetic.bearing.never_place"
    };
    private static final String[] ARM_MODES = {
            "cogworkbridge.arm.mode.0", "cogworkbridge.arm.mode.1", "cogworkbridge.arm.mode.2"
    };
    private static final String[] ROLLER_MODES = {
            "gui.cogworkbridge.kinetic.roller.tunnel_pave",
            "gui.cogworkbridge.kinetic.roller.straight_fill",
            "gui.cogworkbridge.kinetic.roller.wide_fill"
    };
    private static final String[] PISTON_MODES = {
            "cogworkbridge.pulley.mode.0", "cogworkbridge.pulley.mode.1", "cogworkbridge.pulley.mode.2"
    };
    private static final String[] CART_MODES = {
            "cogworkbridge.cart.mode.0", "cogworkbridge.cart.mode.1", "cogworkbridge.cart.mode.2"
    };

    private final BlockPos pos;
    private final KineticMachineConfig config;
    private final int initialValue;
    private int value;
    private int initialMouseX;
    private int initialMouseY;
    private boolean pointerMoved;
    private boolean selectedReverse;
    private boolean finished;

    public KineticMachineConfigScreen(BlockPos pos,
                                      KineticMachineConfig config, int value) {
        this.pos = pos.toImmutable();
        this.config = config;
        this.initialValue = clamp(value);
        this.value = initialValue;
        this.selectedReverse = initialValue < 0;
    }

    @Override
    public void initGui() {
        int cursorX = coordinateForValue(isMode() ? value : Math.abs(value));
        int cursorY = gridTop() + (isMode() || selectedReverse ? 5 : 16);
        int realX = Math.max(0, Math.min(mc.displayWidth - 1,
                cursorX * mc.displayWidth / Math.max(1, width)));
        int realY = Math.max(0, Math.min(mc.displayHeight - 1,
                cursorY * mc.displayHeight / Math.max(1, height)));
        // Cleanroom's LWJGL compatibility layer positions the cursor from the top.
        Mouse.setCursorPosition(realX, realY);
        Mouse.poll();
        initialMouseX = Mouse.getX() * width / Math.max(1, mc.displayWidth);
        initialMouseY = height - 1 - Mouse.getY() * height / Math.max(1, mc.displayHeight);
        pointerMoved = false;
    }

    @Override
    public void updateScreen() {
        if (!KineticMachineConfigClientEvents.useKeyDown(mc)) {
            updateSelection(Mouse.getX() * width / Math.max(1, mc.displayWidth),
                    height - 1 - Mouse.getY() * height / Math.max(1, mc.displayHeight));
            finish(true);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        if (button - 100 == mc.gameSettings.keyBindUseItem.getKeyCode()) {
            updateSelection(mouseX, mouseY);
            finish(true);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            finish(false);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void updateSelection(int mouseX, int mouseY) {
        if (!pointerMoved) {
            if (mouseX == initialMouseX && mouseY == initialMouseY) {
                return;
            }
            pointerMoved = true;
        }
        int offset = Math.max(0, Math.min(barWidth(), mouseX - barLeft()));
        if (isMode()) {
            int last = modeLabels().length - 1;
            value = Math.max(0, Math.min(last, Math.round(offset * (float) last / barWidth())));
            return;
        }
        int speed = closestSpeed(mouseX, isShiftKeyDown());
        selectedReverse = mouseY < gridTop() + ROW_HEIGHT;
        value = selectedReverse ? -speed : speed;
    }

    private int closestSpeed(int mouseX, boolean milestonesOnly) {
        int closest = milestonesOnly ? 1 : Math.max(1, Math.abs(initialValue));
        int distance = Math.abs(coordinateForValue(closest) - mouseX);
        int step = milestonesOnly ? (config == KineticMachineConfig.VALVE_HANDLE ? 45 : 32) : 1;
        int maximum = config == KineticMachineConfig.VALVE_HANDLE ? 180 : 256;
        for (int candidate = milestonesOnly ? step : 1; candidate <= maximum; candidate += step) {
            int nextDistance = Math.abs(coordinateForValue(candidate) - mouseX);
            if (nextDistance < distance || nextDistance == distance
                    && Math.abs(candidate - Math.abs(initialValue)) < Math.abs(closest - Math.abs(initialValue))) {
                closest = candidate;
                distance = nextDistance;
            }
        }
        return closest;
    }

    private void finish(boolean confirm) {
        if (finished) {
            return;
        }
        finished = true;
        if (confirm) {
            ModNetwork.CHANNEL.sendToServer(
                    new KineticConfigChangePacket(pos, config, value));
        }
        mc.displayGuiScreen(null);
    }

    private int clamp(int candidate) {
        return isMode() ? Math.max(0, Math.min(modeLabels().length - 1, candidate))
                : Math.max(config == KineticMachineConfig.VALVE_HANDLE ? -180 : -256,
                    Math.min(config == KineticMachineConfig.VALVE_HANDLE ? 180 : 256, candidate));
    }

    private boolean isMode() {
        return config == KineticMachineConfig.MECHANICAL_BEARING
                || config == KineticMachineConfig.CART_ASSEMBLER
                || config == KineticMachineConfig.MECHANICAL_PISTON
                || config == KineticMachineConfig.MECHANICAL_ROLLER
                || config == KineticMachineConfig.MECHANICAL_ARM || config == KineticMachineConfig.WINDMILL_BEARING
                || config == KineticMachineConfig.STEAM_ENGINE;
    }

    private String[] modeLabels() {
        return config == KineticMachineConfig.WINDMILL_BEARING || config == KineticMachineConfig.STEAM_ENGINE ? WINDMILL_MODES
                : config == KineticMachineConfig.CART_ASSEMBLER ? CART_MODES
                : config == KineticMachineConfig.MECHANICAL_PISTON ? PISTON_MODES
                : config == KineticMachineConfig.MECHANICAL_ROLLER ? ROLLER_MODES
                : config == KineticMachineConfig.MECHANICAL_ARM ? ARM_MODES : BEARING_MODES;
    }

    private int labelWidth() {
        if (isMode()) {
            return 0;
        }
        return Math.max(fontRenderer.getStringWidth(DIRECTION_LABELS[0]),
                fontRenderer.getStringWidth(DIRECTION_LABELS[1]));
    }

    private int barWidth() {
        return isMode() ? 96 : Math.max(96, Math.min(288, width - labelWidth() - 48));
    }

    private int contentWidth() {
        return barWidth() + (isMode() ? 8 : labelWidth() + 22);
    }

    private int contentLeft() {
        return (width - contentWidth()) / 2;
    }

    private int barLeft() {
        return contentLeft() + (isMode() ? 4 : labelWidth() + 18);
    }

    private int gridTop() {
        return (height - (isMode() ? ROW_HEIGHT : 2 * ROW_HEIGHT)) / 2;
    }

    private int coordinateForValue(int selected) {
        if (isMode()) {
            return barLeft() + Math.round(selected * barWidth() / (float) (modeLabels().length - 1));
        }
        int max = config == KineticMachineConfig.VALVE_HANDLE ? 180 : 256;
        int milestone = config == KineticMachineConfig.VALVE_HANDLE ? 45 : 32;
        int value = Math.max(0, Math.min(max, selected));
        int distance = value + (value / milestone) * 4;
        return barLeft() + Math.round(distance * barWidth() / (float) (max + max / milestone * 4));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateSelection(mouseX, mouseY);
        int top = gridTop();
        int rowCount = isMode() ? 1 : 2;
        String title = I18n.format(switch (config) {
            case CREATIVE_MOTOR -> "gui.cogworkbridge.kinetic.creative_motor";
            case SPEED_CONTROLLER -> "gui.cogworkbridge.kinetic.speed_controller";
            case MECHANICAL_BEARING -> "gui.cogworkbridge.kinetic.mechanical_bearing";
            case MECHANICAL_ROLLER -> "gui.cogworkbridge.kinetic.mechanical_roller";
            case MECHANICAL_ARM -> "gui.cogworkbridge.kinetic.mechanical_arm";
            case VALVE_HANDLE -> "gui.cogworkbridge.kinetic.valve_handle";
            case WINDMILL_BEARING -> "gui.cogworkbridge.kinetic.windmill";
            case STEAM_ENGINE -> "gui.cogworkbridge.kinetic.steam_engine";
            case MECHANICAL_PISTON -> "gui.cogworkbridge.kinetic.mechanical_piston";
            case CART_ASSEMBLER -> "gui.cogworkbridge.kinetic.cart_assembler";
        });
        String hint = I18n.format("gui.cogworkbridge.kinetic.release_to_confirm",
                mc.gameSettings.keyBindUseItem.getDisplayName());
        String modeLabel = isMode() ? I18n.format(modeLabels()[value]) : "";
        int textWidth = Math.max(fontRenderer.getStringWidth(title),
                Math.max(fontRenderer.getStringWidth(hint), fontRenderer.getStringWidth(modeLabel)));
        int backgroundWidth = Math.min(width - 8, Math.max(contentWidth() + 20, textWidth + 20));
        int backgroundLeft = (width - backgroundWidth) / 2;
        int backgroundTop = top - 18;
        int backgroundHeight = rowCount * ROW_HEIGHT + (isMode() ? 48 : 34);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        mc.getTextureManager().bindTexture(VALUE_SETTINGS);
        stretch(backgroundLeft, backgroundTop, backgroundWidth, backgroundHeight, 80, 9, 1, 1);
        centeredText(title, width / 2, top - 14, 0xDDDDDD);
        centeredText(hint, width / 2, backgroundTop + backgroundHeight - 12, 0xDDDDDD);

        mc.getTextureManager().bindTexture(VALUE_SETTINGS);
        drawFrame(barLeft() - 4, top - 3, barWidth() + 8, rowCount * ROW_HEIGHT + 5);
        stretch(barLeft() - 1, top, barWidth() + 2, rowCount * ROW_HEIGHT - 1, 75, 9, 1, 1);
        if (isMode()) {
            drawModeRow(top);
            centeredText(modeLabel, width / 2, top + 18, 0xFBDC7D);
        } else {
            drawSpeedRows(top);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSpeedRows(int top) {
        int labelsLeft = contentLeft() + 3;
        drawFrame(contentLeft(), top - 3, labelWidth() + 14, 2 * ROW_HEIGHT + 5);
        for (int row = 0; row < 2; row++) {
            int y = top + row * ROW_HEIGHT;
            mc.getTextureManager().bindTexture(VALUE_SETTINGS);
            stretch(labelsLeft, y, labelWidth() + 8, ROW_HEIGHT, 0, 31, 161, 11);
            stretch(barLeft(), y + 1, barWidth(), 8, 7, 0, 249, 8);
            int markerCount = config == KineticMachineConfig.VALVE_HANDLE ? 4 : 8;
            int markerStep = config == KineticMachineConfig.VALVE_HANDLE ? 45 : 32;
            for (int milestone = 0; milestone <= markerCount; milestone++) {
                drawTexturedModalRect(coordinateForValue(milestone * markerStep) - 3,
                        y + 1, 0, 0, 7, 8);
            }
            String label = DIRECTION_LABELS[row];
            fontRenderer.drawString(label, labelsLeft + 4, y + 1, 0x442000);
        }
        int cursorX = coordinateForValue(Math.abs(value));
        int cursorY = top + (selectedReverse ? 0 : ROW_HEIGHT) - 2;
        String text = Integer.toString(Math.abs(value)) + DIRECTION_LABELS[selectedReverse ? 0 : 1];
        int cursorWidth = fontRenderer.getStringWidth(text) + 4;
        mc.getTextureManager().bindTexture(VALUE_SETTINGS);
        int x = cursorX - cursorWidth / 2;
        drawTexturedModalRect(x - 3, cursorY, 0, 9, 3, 14);
        stretch(x, cursorY, cursorWidth, 14, 4, 9, 56, 14);
        drawTexturedModalRect(x + cursorWidth, cursorY, 61, 9, 3, 14);
        fontRenderer.drawString(text, x + 2, cursorY + 3, 0x442000);
    }

    private void drawModeRow(int top) {
        mc.getTextureManager().bindTexture(VALUE_SETTINGS);
        for (int option = 0; option < modeLabels().length; option++) {
            drawTexturedModalRect(coordinateForValue(option) - 6, top + 1, 75, 14, 13, 8);
        }
        int x = coordinateForValue(value);
        drawTexturedModalRect(x - 11, top - 5, 0, 44, 22, 20);
        mc.getTextureManager().bindTexture(ICONS);
        int iconX = config == KineticMachineConfig.CART_ASSEMBLER ? 192 + value * 16
                : config == KineticMachineConfig.MECHANICAL_ARM ? new int[]{96, 64, 80}[value]
                : (config == KineticMachineConfig.MECHANICAL_ROLLER ? 144 : 96) + value * 16;
        int iconY = config == KineticMachineConfig.MECHANICAL_ROLLER
                || config == KineticMachineConfig.MECHANICAL_ARM ? 64 : 16;
        drawTexturedModalRect(x - 8, top - 3, iconX, iconY, 16, 16);
    }

    private void drawFrame(int x, int y, int frameWidth, int frameHeight) {
        drawTexturedModalRect(x, y, 65, 9, 4, 4);
        drawTexturedModalRect(x + frameWidth - 4, y, 70, 9, 4, 4);
        drawTexturedModalRect(x, y + frameHeight - 4, 65, 19, 4, 4);
        drawTexturedModalRect(x + frameWidth - 4, y + frameHeight - 4, 70, 19, 4, 4);
        stretch(x, y + 4, 3, frameHeight - 8, 65, 14, 3, 4);
        stretch(x + frameWidth - 3, y + 4, 3, frameHeight - 8, 71, 14, 3, 4);
        stretch(x + 4, y, frameWidth - 8, 3, 0, 24, 256, 3);
        stretch(x + 4, y + frameHeight - 3, frameWidth - 8, 3, 0, 27, 256, 3);
    }

    private static void stretch(int x, int y, int width, int height,
                                int textureX, int textureY, int sourceWidth, int sourceHeight) {
        if (width > 0 && height > 0) {
            drawScaledCustomSizeModalRect(x, y, textureX, textureY, sourceWidth,
                    sourceHeight, width, height, 256.0F, 256.0F);
        }
    }

    private void centeredText(String text, int x, int y, int color) {
        fontRenderer.drawString(text, x - fontRenderer.getStringWidth(text) / 2, y, color);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
