package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.redstone.DetectorBlock;
import com.cogwork.cogworkbridge.content.redstone.DetectorConfigPacket;
import com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.Arrays;

/** Stockpile switch screen using Create's threshold texture and scroll inputs. */
public final class ThresholdSwitchScreen extends GuiScreen {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("create", "textures/gui/threshold_switch.png");
    private final BlockPos pos;
    private int left, top, type, maximum, rawOn = 128, rawOff = 64;
    private boolean inverted, stacks, initialized;

    public ThresholdSwitchScreen(BlockPos pos) { this.pos = pos.toImmutable(); }

    private DetectorTileEntity tile() {
        if (mc.world == null) return null;
        return mc.world.getTileEntity(pos) instanceof DetectorTileEntity detector ? detector : null;
    }

    @Override public void initGui() {
        left = (width - 182) / 2 - 20;
        top = (height - 103) / 2;
        DetectorTileEntity detector = tile();
        if (!initialized && detector != null) {
            rawOn = detector.getOnAbove(); rawOff = detector.getOffBelow();
            inverted = detector.isInverted(); stacks = detector.isInStacks(); initialized = true;
        }
        if (detector != null) { type = detector.targetType(); maximum = detector.getMaximum(); normalizeValues(); }
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        buttonList.clear();
        buttonList.add(new CreateIconButton(0, left + 149, top + 79, 0, 1));
        buttonList.add(new CreateIconButton(1, left + 120, top + 79, 8, 4));
    }

    private void send() {
        if (initialized && mc.player != null)
            ModNetwork.CHANNEL.sendToServer(new DetectorConfigPacket(pos, rawOn, rawOff, inverted, stacks));
    }

    private int unit() { return type == 2 ? 1000 : type == 1 && stacks ? 64 : 1; }
    private boolean inside(int x, int y, int rx, int ry, int width, int height) {
        return x >= left + rx && x < left + rx + width && y >= top + ry && y < top + ry + height;
    }

    private void toggleUnit() {
        if (type != 1) return;
        stacks = !stacks;
        normalizeValues();
        send();
    }

    private boolean normalizeValues() {
        if (type == 0 || maximum <= 0) return false;
        int max = Math.max(1, Math.min(1000000000, maximum));
        int step = Math.min(unit(), max);
        int upperLimit = max / step * step;
        int oldOn = rawOn, oldOff = rawOff;
        rawOn = Math.max(step, Math.min(upperLimit, rawOn / step * step));
        rawOff = Math.max(0, Math.min(rawOn - step, rawOff / step * step));
        return rawOn != oldOn || rawOff != oldOff;
    }

    private void adjust(boolean upper, int direction) {
        if (type == 0 || maximum <= 0) return;
        int max = Math.max(1, Math.min(1000000000, maximum));
        int step = Math.min(unit(), max);
        int increment = step * (isShiftKeyDown() ? 10 : 1);
        normalizeValues();
        if (upper) {
            rawOn = (int) Math.max(1, Math.min((long) max, (long) rawOn + (long) direction * increment));
            if (rawOff >= rawOn) rawOff = Math.max(0, rawOn - Math.min(step, rawOn));
        } else {
            rawOff = (int) Math.max(0, Math.min((long) max - 1, (long) rawOff + (long) direction * increment));
            if (rawOn <= rawOff) rawOn = Math.min(max, rawOff + Math.min(step, max - rawOff));
        }
        normalizeValues();
        send();
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.displayGuiScreen(null);
        else if (button.id == 1) { inverted = !inverted; send(); }
    }

    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if ((button == 0 || button == 1) && type == 1 && inside(x, y, 100, 23, 52, 42)) {
            toggleUnit();
            return;
        }
        super.mouseClicked(x, y, button);
    }

    @Override public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int x = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
            int y = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
            if (type == 1 && inside(x, y, 100, 23, 52, 42)) { toggleUnit(); return; }
            int fieldWidth = type == 1 ? 48 : 103;
            if (inside(x, y, 48, 23, fieldWidth, 18)) { adjust(true, wheel > 0 ? 1 : -1); return; }
            if (inside(x, y, 48, 47, fieldWidth, 18)) { adjust(false, wheel > 0 ? 1 : -1); return; }
        }
        super.handleMouseInput();
    }

    @Override public void updateScreen() {
        DetectorTileEntity detector = tile();
        if (detector != null && (type != detector.targetType() || maximum != detector.getMaximum())) {
            type = detector.targetType(); maximum = detector.getMaximum();
            if (normalizeValues()) send();
        }
    }

    @Override public void onGuiClosed() {
        send();
        for (GuiButton button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        super.onGuiClosed();
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(BACKGROUND);
        drawTexturedModalRect(left, top, 0, 0, 182, 103);
        int inputY = type == 1 ? 105 : 129;
        drawTexturedModalRect(left + 44, top + 21, 0, inputY, 115, 22);
        drawTexturedModalRect(left + 44, top + 45, 0, inputY, 115, 22);

        DetectorTileEntity detector = tile();
        boolean powered = detector != null && detector.isPowered();
        drawTexturedModalRect(left + 20, top + 20 + ((inverted ^ powered) ? 0 : 24), 0, 153, 24, 24);
        String title = I18n.format("gui.cogworkbridge.threshold.title");
        fontRenderer.drawString(title, left + (182 - fontRenderer.getStringWidth(title)) / 2, top + 4, 0x592424);
        fontRenderer.drawStringWithShadow("\u2265 " + value(rawOn), left + 53, top + 28, 0xffffff);
        fontRenderer.drawStringWithShadow("\u2264 " + value(rawOff), left + 53, top + 52, 0xffffff);
        if (type == 1) {
            String suffix = I18n.format(stacks ? "cogworkbridge.threshold.stacks" : "cogworkbridge.threshold.items");
            fontRenderer.drawStringWithShadow(suffix, left + 105, top + 28, 0xffffff);
            fontRenderer.drawStringWithShadow(suffix, left + 105, top + 52, 0xffffff);
        }
        drawTorch(left + 23, top + 24, !inverted);
        drawTorch(left + 23, top + 50, inverted);
        drawTarget();
        drawPreview();
        super.drawScreen(mouseX, mouseY, partial);
        drawTooltip(mouseX, mouseY, detector);
    }

    private String value(int raw) {
        if (type == 0) return "";
        int amount = raw / unit();
        if (type == 2) return amount + " " + I18n.format("cogworkbridge.threshold.buckets");
        if (type == 3) return amount + "y";
        return Integer.toString(amount);
    }

    private void drawTorch(int x, int y, boolean lit) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 1, y + 15, 100);
        GlStateManager.scale(16, -16, 16);
        GlStateManager.rotate(22.5F, 1, 0, 0);
        GlStateManager.rotate(45, 0, 1, 0);
        GlStateManager.translate(-.5, 0, -.5);
        RenderHelper.enableGUIStandardItemLighting();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        mc.getBlockRendererDispatcher().renderBlockBrightness((lit ? Blocks.REDSTONE_TORCH : Blocks.UNLIT_REDSTONE_TORCH).getDefaultState(), 1F);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
    }

    private ItemStack targetStack() {
        if (mc.world == null || !mc.world.isBlockLoaded(pos)) return ItemStack.EMPTY;
        IBlockState self = mc.world.getBlockState(pos);
        if (!(self.getBlock() instanceof DetectorBlock)) return ItemStack.EMPTY;
        BlockPos target = pos.offset(DetectorBlock.target(self));
        if (!mc.world.isBlockLoaded(target)) return ItemStack.EMPTY;
        IBlockState state = mc.world.getBlockState(target);
        Block block = state.getBlock();
        Item item = Item.getItemFromBlock(block);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, 1, block.damageDropped(state));
    }

    private void drawTarget() {
        ItemStack target = targetStack();
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(target.isEmpty() ? new ItemStack(Blocks.BARRIER) : target, left + 13, top + 80);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1, 1, 1, 1);
    }

    private void drawPreview() {
        GlStateManager.pushMatrix();
        GlStateManager.translate(left + 188, top + 47, 100);
        GlStateManager.scale(5F, 5F, 1F);
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(AllBlocks.STOCKPILE_SWITCH), 0, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
    }

    private void drawTooltip(int x, int y, DetectorTileEntity detector) {
        if (inside(x, y, 13, 80, 16, 16)) {
            ItemStack target = targetStack();
            if (target.isEmpty()) drawHoveringText(I18n.format("gui.cogworkbridge.threshold.not_attached"), x, y);
            else if (type == 0) drawHoveringText(Arrays.asList(target.getDisplayName(), I18n.format("gui.cogworkbridge.threshold.incompatible")), x, y);
            else if (detector != null) drawHoveringText(Arrays.asList(target.getDisplayName(), I18n.format("gui.cogworkbridge.threshold.current", detector.getAmount(), detector.getMaximum())), x, y);
        } else if (inside(x, y, 120, 79, 18, 18)) {
            drawHoveringText(I18n.format("gui.cogworkbridge.threshold.invert"), x, y);
        } else if (type == 1 && inside(x, y, 100, 23, 52, 42)) {
            drawHoveringText(I18n.format("gui.cogworkbridge.threshold.measure"), x, y);
        } else if (type != 0 && (inside(x, y, 48, 23, type == 1 ? 48 : 103, 18)
                || inside(x, y, 48, 47, type == 1 ? 48 : 103, 18))) {
            boolean upper = y < top + 45;
            drawHoveringText(Arrays.asList(I18n.format(upper ? "gui.cogworkbridge.threshold.upper" : "gui.cogworkbridge.threshold.lower"),
                    I18n.format("gui.cogworkbridge.sequencer.shift_scroll")), x, y);
        }
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
