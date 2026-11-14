package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencedGearshiftTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencerConfigPacket;
import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencerInstruction;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.List;

/** The sequencer's five-row editor, using the player's upstream GUI texture. */
public final class SequencedGearshiftScreen extends GuiScreen {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("create", "textures/gui/sequencer.png");
    private static final ResourceLocation WIDGETS = new ResourceLocation("create", "textures/gui/widgets.png");
    private static final ResourceLocation ICONS = new ResourceLocation("create", "textures/gui/icons.png");
    private static final int[] SPEEDS = {2, 1, -1, -2};
    private final BlockPos pos;
    private final List<SequencerInstruction> instructions;
    private final ItemStack preview = new ItemStack(AllBlocks.SEQUENCED_GEARSHIFT);
    private boolean changed;
    private int left;
    private int top;

    public SequencedGearshiftScreen(SequencedGearshiftTileEntity tile) {
        pos = tile.getPos().toImmutable();
        instructions = tile.getInstructions();
    }

    @Override
    public void initGui() {
        left = (width - 173) / 2 - 20;
        top = (height - 161) / 2;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button == 0 && onConfirm(mouseX, mouseY)) {
            mc.displayGuiScreen(null);
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int x = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
            int y = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
            edit(x, y, wheel > 0 ? 1 : -1);
        }
    }

    private void edit(int mouseX, int mouseY, int direction) {
        int row = (mouseY - top - 20) / 22;
        if (mouseY < top + 20 || row < 0 || row >= instructions.size()
                || mouseY >= top + 20 + row * 22 + 18) {
            return;
        }
        int x = mouseX - left;
        SequencerInstruction current = instructions.get(row);
        if (x >= 30 && x < 80) {
            SequencerInstruction.Type[] types = SequencerInstruction.Type.values();
            int next = Math.max(0, Math.min(types.length - 1, current.type().ordinal() + direction));
            SequencerInstruction.Type type = types[next];
            if (type == current.type()) return;
            instructions.set(row, SequencerInstruction.of(type));
            if (type == SequencerInstruction.Type.END) {
                while (instructions.size() > row + 1) {
                    instructions.remove(instructions.size() - 1);
                }
            } else if (instructions.size() == row + 1 && instructions.size() < SequencerInstruction.MAX_STEPS) {
                instructions.add(SequencerInstruction.of(SequencerInstruction.Type.END));
            }
        } else if (x >= 88 && x < 116 && current.type().maxValue > 0) {
            boolean shifted = isShiftKeyDown();
            int shiftStep = switch (current.type()) {
                case TURN_ANGLE -> 45;
                case TURN_DISTANCE -> 5;
                case DELAY -> 20;
                default -> 1;
            };
            int step = shifted ? shiftStep : 1;
            if (current.type() == SequencerInstruction.Type.DELAY) {
                int thresholdValue = current.value() - (direction < 0 ? 1 : 0);
                step = thresholdValue < 20 ? (shifted ? 20 : 1) : (shifted ? 100 : 20);
            }
            int next = current.value() + direction * step;
            if (shifted) next -= Math.floorMod(next, shiftStep);
            instructions.set(row, new SequencerInstruction(current.type(), next, current.speed()));
        } else if (x >= 118 && x < 146 && current.type().moves()) {
            int index = 0;
            while (index < SPEEDS.length && SPEEDS[index] != current.speed()) {
                index++;
            }
            int next = Math.max(0, Math.min(SPEEDS.length - 1, index + direction));
            instructions.set(row, new SequencerInstruction(current.type(), current.value(), SPEEDS[next]));
        } else {
            return;
        }
        changed = true;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(BACKGROUND);
        drawTexturedModalRect(left, top, 0, 0, 173, 161);
        for (int row = 0; row < SequencerInstruction.MAX_STEPS; row++) {
            int y = top + 16 + row * 22;
            SequencerInstruction instruction = row < instructions.size() ? instructions.get(row) : null;
            int textureY = instruction == null ? 104 : switch (instruction.type()) {
                case TURN_ANGLE, TURN_DISTANCE -> 16;
                case DELAY -> 60;
                case AWAIT -> 162;
                case END -> 82;
            };
            drawTexturedModalRect(left, y, 0, textureY, 162, 22);
            if (instruction == null) {
                continue;
            }
            String name = I18n.format("gui.cogworkbridge.sequencer." + instruction.type().name().toLowerCase(java.util.Locale.ROOT));
            fontRenderer.drawString(name, left + 36, y + 9, 0xFFFFFF);
            if (instruction.type().maxValue > 0) {
                String value = formatValue(instruction);
                fontRenderer.drawString(value, left + 102 - fontRenderer.getStringWidth(value) / 2, y + 9, 0xFFFFFF);
            }
            if (instruction.type().moves()) {
                String speed = switch (instruction.speed()) {
                    case 2 -> ">>";
                    case -1 -> "<-";
                    case -2 -> "<<";
                    default -> "->";
                };
                fontRenderer.drawString(speed, left + 126, y + 9, 0xFFFFFF);
            }
        }
        String title = I18n.format("gui.cogworkbridge.sequencer.title");
        fontRenderer.drawString(title, left + 82 - fontRenderer.getStringWidth(title) / 2, top + 5, 0x592424);
        drawConfirm(mouseX, mouseY);
        drawPreview();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawFieldTooltip(mouseX, mouseY);
    }

    private static String formatValue(SequencerInstruction instruction) {
        int value = instruction.value();
        return switch (instruction.type()) {
            case TURN_ANGLE -> value + "\u00b0";
            case TURN_DISTANCE -> value + "m";
            case DELAY -> value >= 20 ? (value / 20) + "s" : value + "t";
            default -> Integer.toString(value);
        };
    }

    private boolean onConfirm(int mouseX, int mouseY) {
        return mouseX >= left + 140 && mouseX < left + 158
                && mouseY >= top + 137 && mouseY < top + 155;
    }

    private void drawConfirm(int mouseX, int mouseY) {
        int x = left + 140;
        int y = top + 137;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(WIDGETS);
        drawTexturedModalRect(x, y, onConfirm(mouseX, mouseY) ? 18 : 0, 0, 18, 18);
        mc.getTextureManager().bindTexture(ICONS);
        drawTexturedModalRect(x + 1, y + 1, 0, 16, 16, 16);
    }

    private void drawPreview() {
        GlStateManager.pushMatrix();
        GlStateManager.translate(left + 179, top + 105, 100.0D);
        GlStateManager.scale(5.0F, 5.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(preview, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    private void drawFieldTooltip(int mouseX, int mouseY) {
        if (onConfirm(mouseX, mouseY)) {
            drawHoveringText(I18n.format("gui.cogworkbridge.sequencer.confirm"), mouseX, mouseY);
            return;
        }
        int row = (mouseY - top - 20) / 22;
        if (mouseY < top + 20 || row < 0 || row >= instructions.size()
                || mouseY >= top + 20 + row * 22 + 18) return;
        int x = mouseX - left;
        SequencerInstruction instruction = instructions.get(row);
        String title;
        String detail;
        if (x >= 30 && x < 80) {
            title = I18n.format("gui.cogworkbridge.sequencer.instruction");
            detail = I18n.format("gui.cogworkbridge.sequencer." + instruction.type().name().toLowerCase(java.util.Locale.ROOT) + ".description");
        } else if (x >= 88 && x < 116 && instruction.type().maxValue > 0) {
            title = I18n.format("gui.cogworkbridge.sequencer." + instruction.type().name().toLowerCase(java.util.Locale.ROOT) + ".value");
            detail = I18n.format("gui.cogworkbridge.sequencer.shift_scroll");
        } else if (x >= 118 && x < 146 && instruction.type().moves()) {
            title = I18n.format("gui.cogworkbridge.sequencer.speed");
            String speed = switch (instruction.speed()) {
                case 2 -> "forward_fast";
                case -1 -> "back";
                case -2 -> "back_fast";
                default -> "forward";
            };
            detail = I18n.format("gui.cogworkbridge.sequencer.speed." + speed);
        } else return;
        drawHoveringText(java.util.Arrays.asList(title, detail,
                I18n.format("gui.cogworkbridge.sequencer.scroll")), mouseX, mouseY);
    }

    @Override
    public void onGuiClosed() {
        if (changed) {
            ModNetwork.CHANNEL.sendToServer(new SequencerConfigPacket(pos, instructions));
        }
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
