package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.client.render.FastStateRendering;
import com.cogwork.cogworkbridge.client.render.GuiLightingState;
import com.cogwork.cogworkbridge.content.kinetics.encased.WrenchItem;
import com.cogwork.cogworkbridge.content.kinetics.encased.WrenchRotationOptions;
import com.cogwork.cogworkbridge.content.kinetics.encased.WrenchRotationPacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.io.IOException;
import java.util.List;

/** Radial selector for the target block's persistent orientation properties. */
public final class WrenchRotationScreen extends GuiScreen {
    private static final double INNER = 50, OUTER = 110;
    private static final ResourceLocation PONDER_WIDGETS = new ResourceLocation("create", "textures/gui/ponder_widgets.png");
    private static final ResourceLocation ICONS = new ResourceLocation("create", "textures/gui/icons.png");
    private final BlockPos pos;
    private final EnumFacing face;
    private final IBlockState original;
    private final List<IProperty<?>> properties;
    private final boolean mouseBinding;
    private final int openKey;
    private List<IBlockState> choices;
    private int propertyIndex, selected;
    private KineticTileEntity previewTile;
    private boolean finished;

    public WrenchRotationScreen(BlockPos pos, EnumFacing face, IBlockState state, List<IProperty<?>> properties,
                                boolean mouseBinding, int openKey) {
        this.pos = pos.toImmutable();
        this.face = face;
        this.original = state;
        this.properties = properties;
        this.mouseBinding = mouseBinding;
        this.openKey = openKey;
    }
    @Override public void initGui() {
        selectProperty();
        if (previewTile == null && mc.world != null) {
            TileEntity originalTile = mc.world.getTileEntity(pos);
            if (originalTile instanceof KineticTileEntity) {
                try {
                    TileEntity copy = TileEntity.create(mc.world, originalTile.writeToNBT(new NBTTagCompound()));
                    if (copy instanceof KineticTileEntity kinetic) previewTile = kinetic;
                } catch (RuntimeException ignored) {
                    // The ordinary baked model remains available if a tile cannot be copied.
                }
            }
        }
    }
    @Override public boolean doesGuiPauseGame() { return false; }

    private void selectProperty() {
        choices = WrenchRotationOptions.states(mc.world, pos, original, properties.get(propertyIndex));
        selected = Math.max(0, choices.indexOf(original));
    }
    @Override public void updateScreen() {
        if (mc.player == null || mc.world == null || !mc.world.isBlockLoaded(pos)
                || !(mc.player.getHeldItemMainhand().getItem() instanceof WrenchItem)
                || !mc.world.getBlockState(pos).equals(original)) {
            cancel();
            return;
        }
        // Also covers a release lost when a screen is opened during an input event.
        if (!mouseBinding && (openKey <= 0 || openKey >= Keyboard.KEYBOARD_SIZE || !Keyboard.isKeyDown(openKey))) submit();
    }
    private void chooseAt(int mouseX, int mouseY) {
        double dx = mouseX - width / 2D, dy = mouseY - height / 2D;
        if (dx * dx + dy * dy <= (INNER - 5) * (INNER - 5) || choices.isEmpty()) return;
        double sector = 2D * Math.PI / choices.size();
        selected = Math.floorMod((int) Math.floor((Math.atan2(dy, dx) + Math.PI / 2D + sector / 2D) / sector), choices.size());
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        chooseAt(mouseX, mouseY);
        int cx = width / 2, cy = height / 2;
        drawSectors(cx, cy);
        drawDirectionIndicator(cx, cy, mouseX, mouseY);
        for (int i = 0; i < choices.size(); i++) {
            double angle = -Math.PI / 2D + i * Math.PI * 2D / choices.size();
            int x = (int) Math.round(cx + Math.cos(angle) * (INNER + OUTER) / 2D);
            int y = (int) Math.round(cy + Math.sin(angle) * (INNER + OUTER) / 2D);
            drawBlock(choices.get(i), x, y, partialTicks);
            if (i == selected) {
                String value = WrenchRotationOptions.valueName(choices.get(i), properties.get(propertyIndex));
                String key = "gui.cogworkbridge.rotate_menu.value." + value;
                drawCenteredString(fontRenderer, I18n.hasKey(key) ? I18n.format(key) : value, x, y + 15, 0xffffff);
            }
        }
        drawCenterStreak(cx, cy);
        drawCenteredString(fontRenderer, I18n.format("gui.cogworkbridge.rotate_menu.currently"), cx, cy - 13, 0xffffff);
        drawCenteredString(fontRenderer, I18n.format("gui.cogworkbridge.rotate_menu.changing"), cx, cy - 3, 0xffffff);
        drawCenteredString(fontRenderer, propertyLabel(properties.get(propertyIndex)), cx, cy + 7, 0xffffff);
        if (properties.size() > 1) {
            if (propertyIndex > 0) {
                drawPropertyIcon(cx, cy - 46, true);
                drawCenteredString(fontRenderer, propertyLabel(properties.get(propertyIndex - 1)), cx, cy - 30, 0xdddddd);
            }
            if (propertyIndex + 1 < properties.size()) {
                drawPropertyIcon(cx, cy + 30, false);
                drawCenteredString(fontRenderer, propertyLabel(properties.get(propertyIndex + 1)), cx, cy + 22, 0xdddddd);
            }
        }
        GlStateManager.color(1, 1, 1, 1);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    private String propertyLabel(IProperty<?> property) {
        String name = property.getName();
        if (name.contains("axis")) name = "axis";
        else if (name.contains("facing")) name = "facing";
        String key = "gui.cogworkbridge.rotate_menu.property." + name;
        return I18n.hasKey(key) ? I18n.format(key) : name;
    }
    private void drawSectors(int cx, int cy) {
        if (choices.size() < 2) return;
        try (OverlayState ignored = new OverlayState()) {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            double sector = 2D * Math.PI / choices.size();
            for (int i = 0; i < choices.size(); i++) {
                double center = -Math.PI / 2D + i * sector;
                boolean active = i == selected;
                float red = active ? .9F : 1F, green = active ? .9F : 1F, blue = active ? .6F : 1F;
                drawSector(buffer, tessellator, cx, cy, center, sector, INNER, OUTER,
                        red, green, blue, active ? .125F : .05F, active ? .45F : .3F);
                drawSector(buffer, tessellator, cx, cy, center, sector, INNER - 3, INNER - 2,
                        red, green, blue, .5F, .5F);
                if (active) {
                    drawSector(buffer, tessellator, cx, cy, center, sector, OUTER + 2, OUTER + 3,
                            red, green, blue, .45F, .45F);
                }
                drawSectorBoundary(buffer, tessellator, cx, cy, center - sector / 2D);
            }
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    private static void drawSector(BufferBuilder buffer, Tessellator tessellator, int cx, int cy,
                                   double center, double angleSize, double inner, double outer,
                                   float red, float green, float blue, float innerAlpha, float outerAlpha) {
        buffer.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int part = 0; part <= 24; part++) {
            double angle = center - angleSize / 2D + part * angleSize / 24D;
            double cosine = Math.cos(angle), sine = Math.sin(angle);
            buffer.pos(cx + cosine * inner, cy + sine * inner, 0).color(red, green, blue, innerAlpha).endVertex();
            buffer.pos(cx + cosine * outer, cy + sine * outer, 0).color(red, green, blue, outerAlpha).endVertex();
        }
        tessellator.draw();
    }

    private static void drawSectorBoundary(BufferBuilder buffer, Tessellator tessellator,
                                           int cx, int cy, double angle) {
        double nx = Math.cos(angle), ny = Math.sin(angle);
        double tx = -ny * .25D, ty = nx * .25D;
        double[] radii = {INNER - 5, INNER + 20, OUTER + 10};
        float[] alpha = {.15F, .5F, .15F};
        buffer.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < radii.length; i++) {
            double x = cx + nx * radii[i], y = cy + ny * radii[i];
            buffer.pos(x - tx, y - ty, 0).color(1F, 1F, 1F, alpha[i]).endVertex();
            buffer.pos(x + tx, y + ty, 0).color(1F, 1F, 1F, alpha[i]).endVertex();
        }
        tessellator.draw();
    }
    private void drawBlock(IBlockState state, int x, int y, float partialTicks) {
        try (GuiLightingState ignored = new GuiLightingState()) {
            GlStateManager.pushMatrix();
            try {
                GlStateManager.enableDepth();
                GlStateManager.enableBlend();
                GlStateManager.disableCull();
                GlStateManager.enableRescaleNormal();
                GlStateManager.color(1, 1, 1, 1);
                RenderHelper.enableGUIStandardItemLighting();
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
                GlStateManager.translate(x, y, 100);
                GlStateManager.scale(24F, -24F, 24F);
                float pitch = mc.player.prevRotationPitch + (mc.player.rotationPitch - mc.player.prevRotationPitch) * partialTicks;
                float yaw = mc.player.prevRotationYaw + (mc.player.rotationYaw - mc.player.prevRotationYaw) * partialTicks;
                GlStateManager.rotate(pitch, 1F, 0F, 0F);
                GlStateManager.rotate(yaw + 180F, 0F, 1F, 0F);
                GlStateManager.translate(-.5, -.5, -.5);
                TileEntitySpecialRenderer<KineticTileEntity> renderer = previewTile == null ? null
                        : TileEntityRendererDispatcher.instance.getRenderer(previewTile);
                if (state.getRenderType() == EnumBlockRenderType.MODEL || renderer == null) {
                    IBlockState preview = state.getBlock().getExtendedState(state, mc.world, pos);
                    FastStateRendering.getInstance().renderFast(preview);
                }
                if (renderer != null) {
                    previewTile.setContraptionRenderContext(state, null);
                    renderer.render(previewTile, 0D, 0D, 0D, partialTicks, -1, 1F);
                }
            } catch (RuntimeException ignoredModel) {
                // A third-party model can require world-only data; the menu remains usable.
            } finally {
                GlStateManager.popMatrix();
            }
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    private void drawPropertyIcon(int cx, int y, boolean up) {
        GlStateManager.color(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(PONDER_WIDGETS);
        Gui.drawModalRectWithCustomSizedTexture(cx - 14, y, 16, 64, 16, 16, 256, 256);
        mc.getTextureManager().bindTexture(ICONS);
        Gui.drawModalRectWithCustomSizedTexture(cx - 1, y, (up ? 6 : 5) * 16, 0, 16, 16, 256, 256);
    }

    private static void drawCenterStreak(int cx, int cy) {
        try (OverlayState ignored = new OverlayState()) {
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            int[] offsets = {-65, -49, -32, 0, 32, 49, 65};
            float[] alpha = {0F, .05F, .4F, .5F, .4F, .05F, 0F};
            buffer.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i < offsets.length; i++) {
                buffer.pos(cx + offsets[i], cy - 16, 0).color(0F, 0F, 0F, alpha[i]).endVertex();
                buffer.pos(cx + offsets[i], cy + 16, 0).color(0F, 0F, 0F, alpha[i]).endVertex();
            }
            Tessellator.getInstance().draw();
        }
    }

    private static void drawDirectionIndicator(int cx, int cy, int mouseX, int mouseY) {
        double dx = mouseX - cx, dy = mouseY - cy;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance <= INNER - 5) return;
        double nx = dx / distance, ny = dy / distance;
        double x = cx + nx * (INNER + 3), y = cy + ny * (INNER + 3);
        double tx = -ny, ty = nx;
        try (OverlayState ignored = new OverlayState()) {
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
            buffer.pos(x, y, 0).color(.8F, .8F, .8F, .75F).endVertex();
            for (int offset = -5; offset <= 5; offset += 10)
                buffer.pos(x - nx * 5 + tx * offset, y - ny * 5 + ty * offset, 0).color(.8F, .8F, .8F, .4F).endVertex();
            Tessellator.getInstance().draw();
        }
    }

    /** Keep Minecraft's GL cache in sync while drawing translucent GUI geometry. */
    private static final class OverlayState implements AutoCloseable {
        private final boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        private final boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final int shade = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

        private OverlayState() {
            GlStateManager.disableTexture2D();
            GlStateManager.disableAlpha();
            GlStateManager.disableDepth();
            GlStateManager.disableCull();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
        }

        @Override public void close() {
            GlStateManager.shadeModel(shade);
            GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            if (alphaTest) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        }
    }
    @Override public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && properties.size() > 1) {
            int next = propertyIndex + (wheel > 0 ? -1 : 1);
            if (next >= 0 && next < properties.size()) { propertyIndex = next; selectProperty(); }
        }
        super.handleMouseInput();
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (button == 1) { cancel(); return; }
        if (button == 0 && mouseBinding) { chooseAt(x, y); submit(); return; }
        super.mouseClicked(x, y, button);
    }
    @Override public void handleKeyboardInput() throws IOException {
        if (!mouseBinding && Keyboard.getEventKey() == openKey && !Keyboard.getEventKeyState()) {
            submit();
            return;
        }
        super.handleKeyboardInput();
    }
    @Override protected void keyTyped(char typed, int code) throws IOException {
        if (code == Keyboard.KEY_ESCAPE) { cancel(); return; }
        super.keyTyped(typed, code);
    }
    private void submit() {
        if (finished) return;
        finished = true;
        if (choices != null && selected >= 0 && selected < choices.size() && !choices.get(selected).equals(original))
            ModNetwork.CHANNEL.sendToServer(new WrenchRotationPacket(pos, original, face, properties.get(propertyIndex), choices.get(selected)));
        mc.displayGuiScreen(null);
    }
    private void cancel() { if (!finished) { finished = true; mc.displayGuiScreen(null); } }
    @Override public void onGuiClosed() { finished = true; WrenchRotationClientEvents.menuClosed(); }
}
