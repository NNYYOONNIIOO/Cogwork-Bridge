package com.cogwork.cogworkbridge.compat.ponder;

import java.util.Set;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.render.GlStateGuard;
import net.createmod.ponder.api.element.InputElementBuilder;
import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.PonderElementBase;
import net.createmod.ponder.foundation.instruction.ShowElementInstruction;
import net.createmod.ponder.render.SceneProjection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;

/** Input hints for our imported scenes; Legacy's stock hint only draws action colour markers. */
final class PonderInputs extends PonderElementBase implements InputElementBuilder, PonderOverlayElement {
    private final Vec3d location;
    private final Pointing direction;
    private ItemStack stack = ItemStack.EMPTY;
    private ScreenElement icon;
    private PonderGuiTextures mouse;
    private boolean sneaking;
    private boolean control;

    private PonderInputs(Vec3d location, Pointing direction) {
        this.location = location;
        this.direction = direction;
    }

    static boolean handles(String op) {
        return Set.of("showControls", "showCenteredScrollInput", "showScrollInput",
                "showRepeaterScrollInput", "showFilterSlotInput").contains(op);
    }

    static PonderInputs show(PonderProgram program, String op, Object[] args) {
        Vec3d location = PonderValues.vec(args[0]);
        Pointing direction;
        int duration;
        if (op.equals("showControls")) {
            direction = (Pointing) program.convert(args[1], Pointing.class);
            duration = (int) PonderValues.num(args[2]);
        } else {
            EnumFacing side = op.equals("showRepeaterScrollInput") ? EnumFacing.UP
                    : args.length == 2 ? EnumFacing.UP : PonderValues.direction(args[1]);
            if (op.equals("showCenteredScrollInput") || op.equals("showRepeaterScrollInput"))
                location = location.add(.5, .5, .5);
            direction = side == EnumFacing.WEST ? Pointing.LEFT : side == EnumFacing.EAST ? Pointing.RIGHT
                    : side == EnumFacing.DOWN || side == EnumFacing.NORTH ? Pointing.UP : Pointing.DOWN;
            duration = (int) PonderValues.num(args[args.length - 1]);
        }
        PonderInputs input = new PonderInputs(location, direction);
        if (op.equals("showFilterSlotInput")) input.rightClick();
        else if (!op.equals("showControls")) input.scroll();
        program.scene.addInstruction(new ShowElementInstruction(input, duration, true));
        return input;
    }

    @Override public InputElementBuilder withItem(ItemStack value) { stack = value == null ? ItemStack.EMPTY : value.copy(); return this; }
    @Override public InputElementBuilder leftClick() { mouse = PonderGuiTextures.ICON_LMB; return this; }
    @Override public InputElementBuilder rightClick() { mouse = PonderGuiTextures.ICON_RMB; return this; }
    @Override public InputElementBuilder scroll() { mouse = PonderGuiTextures.ICON_SCROLL; return this; }
    @Override public InputElementBuilder showing(ScreenElement value) { icon = value; return this; }
    @Override public InputElementBuilder whileSneaking() { sneaking = true; return this; }
    @Override public InputElementBuilder whileCTRL() { control = true; return this; }

    @Override public void render(PonderScene scene, int mouseX, int mouseY, float partialTicks) {
        SceneProjection.ProjectedPoint point = SceneProjection.project(location);
        if (!point.visible) return;
        Minecraft mc = Minecraft.getMinecraft();
        String modifiers = sneaking ? mc.gameSettings.keyBindSneak.getDisplayName() : "";
        if (control) modifiers += (modifiers.isEmpty() ? "" : " + ") + "Ctrl";
        int slots = (mouse == null ? 0 : 1) + (icon == null && stack.isEmpty() ? 0 : 1);
        int width = Math.max(24, 8 + slots * 20 + (modifiers.isEmpty() ? 0 : mc.fontRenderer.getStringWidth(modifiers) + 4));
        int x = Math.round(point.x) - width / 2, y = Math.round(point.y) - 32;
        switch (direction) {
            case UP -> y = Math.round(point.y) + 8;
            case LEFT -> { x = Math.round(point.x) + 8; y = Math.round(point.y) - 12; }
            case RIGHT -> { x = Math.round(point.x) - width - 8; y = Math.round(point.y) - 12; }
            default -> { }
        }
        try (GlStateGuard ignored = GlStateGuard.capture()) {
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.disableLighting();
            Gui.drawRect(x - 1, y - 1, x + width + 1, y + 25, 0xff7fcde0);
            Gui.drawRect(x, y, x + width, y + 24, 0xee15191d);
            GlStateManager.color(1, 1, 1, 1);
            int cursor = x + 4;
            if (mouse != null) { mouse.render(cursor, y + 4); cursor += 20; }
            if (icon != null) icon.render(cursor, y + 4);
            else if (!stack.isEmpty()) GuiGameElement.of(stack).render(cursor + 8, y + 12);
            if (icon != null || !stack.isEmpty()) cursor += 20;
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
            GlStateManager.color(1, 1, 1, 1);
            if (!modifiers.isEmpty()) mc.fontRenderer.drawStringWithShadow(modifiers, cursor + 2, y + 8, 0xffffff);
        }
    }
}
