package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.fluids.ListFilterContainer;
import com.cogwork.cogworkbridge.content.fluids.ListFilterJeiPacket;
import com.cogwork.cogworkbridge.content.fluids.ListFilterOptionPacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.io.IOException;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** List-filter screen using the upstream backgrounds and control icons. */
public final class ListFilterScreen extends GuiContainer {
    private static final ResourceLocation FILTER = new ResourceLocation("create", "textures/gui/filters.png");
    private static final ResourceLocation INVENTORY = new ResourceLocation("create", "textures/gui/player_inventory.png");
    private static final ResourceLocation WIDGETS = new ResourceLocation("create", "textures/gui/widgets.png");
    private static final ResourceLocation ICONS = new ResourceLocation("create", "textures/gui/icons.png");
    private static final String[] TOOLTIPS = {
            "gui.cogworkbridge.filter.deny", "gui.cogworkbridge.filter.allow",
            "gui.cogworkbridge.filter.respect", "gui.cogworkbridge.filter.ignore",
            "gui.cogworkbridge.filter.clear", "gui.cogworkbridge.filter.confirm"
    };
    private final ListFilterContainer filter;
    private final Map<Integer, ResourceLocation> iconTextures = new HashMap<>();

    public ListFilterScreen(ListFilterContainer filter) {
        super(filter);
        this.filter = filter;
        xSize = 214;
        ySize = 211;
    }

    public Rectangle getJeiGhostArea(int slot) {
        if (slot < 0 || slot >= 18) return new Rectangle(guiLeft, guiTop, 0, 0);
        return new Rectangle(guiLeft + 23 + (slot % 9) * 18,
                guiTop + 25 + (slot / 9) * 18, 18, 18);
    }

    public void acceptJeiItem(int slot, net.minecraft.item.ItemStack stack) {
        if (slot < 0 || slot >= 18 || stack == null || stack.isEmpty()) return;
        net.minecraft.item.ItemStack copy = stack.copy();
        copy.setCount(1);
        filter.applyGhostEntry(slot, copy);
        ModNetwork.CHANNEL.sendToServer(new ListFilterJeiPacket(filter.windowId, slot, copy));
    }

    @Override
    public void initGui() {
        super.initGui();
        guiLeft -= 11;
        guiTop += 5;
        buttonList.clear();
        buttonList.add(new IconButton(0, guiLeft + 18, guiTop + 75, 8, 0));
        buttonList.add(new IconButton(1, guiLeft + 36, guiTop + 75, 9, 0));
        buttonList.add(new IconButton(2, guiLeft + 60, guiTop + 75, 13, 0));
        buttonList.add(new IconButton(3, guiLeft + 78, guiTop + 75, 14, 0));
        buttonList.add(new IconButton(4, guiLeft + 152, guiTop + 75, 1, 0));
        buttonList.add(new IconButton(5, guiLeft + 181, guiTop + 75, 0, 1));
        loadIconTextures();
    }

    private void loadIconTextures() {
        try (var input = mc.getResourceManager().getResource(ICONS).getInputStream()) {
            BufferedImage atlas = ImageIO.read(input);
            if (atlas == null || atlas.getWidth() < 256 || atlas.getHeight() < 32) return;
            for (GuiButton button : buttonList) {
                IconButton icon = (IconButton) button;
                ResourceLocation location = new ResourceLocation("cogworkbridge", "dynamic/list_filter_icon_" + icon.id);
                mc.getTextureManager().deleteTexture(location);
                mc.getTextureManager().loadTexture(location, new DynamicTexture(
                        atlas.getSubimage(icon.iconX * 16, icon.iconY * 16, 16, 16)));
                iconTextures.put(icon.id, location);
            }
        } catch (IOException exception) {
            com.cogwork.cogworkbridge.CogworkBridge.LOGGER.warn("Unable to load list filter icons", exception);
        }
    }

    @Override public void onGuiClosed() {
        for (ResourceLocation location : iconTextures.values()) mc.getTextureManager().deleteTexture(location);
        iconTextures.clear();
        super.onGuiClosed();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 5) {
            mc.player.closeScreen();
            return;
        }
        if (button.id < 0 || button.id > 4) return;
        filter.applyOption(button.id);
        ModNetwork.CHANNEL.sendToServer(new ListFilterOptionPacket(filter.windowId, button.id));
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(FILTER);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, 214, 99);
        mc.getTextureManager().bindTexture(INVENTORY);
        drawTexturedModalRect(guiLeft + 30, guiTop + 103, 0, 0, 176, 108);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("item.cogworkbridge.filter.name");
        fontRenderer.drawString(title, (206 - fontRenderer.getStringWidth(title)) / 2, 4, 0x303030);
        fontRenderer.drawString(I18n.format("container.inventory"), 38, 109, 0x404040);
        renderHeldFilter(222, 47);
    }

    private void renderHeldFilter(int x, int y) {
        net.minecraft.item.ItemStack stack = mc.player.getHeldItemMainhand();
        if (stack.isEmpty()) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 100);
        GlStateManager.scale(4F, 4F, 4F);
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
        for (GuiButton button : buttonList) {
            if (button.isMouseOver() && button.id >= 0 && button.id < TOOLTIPS.length) {
                drawHoveringText(I18n.format(TOOLTIPS[button.id]), mouseX, mouseY);
                break;
            }
        }
    }

    private final class IconButton extends GuiButton {
        private final int iconX;
        private final int iconY;

        private IconButton(int id, int x, int y, int iconX, int iconY) {
            super(id, x, y, 18, 18, "");
            this.iconX = iconX;
            this.iconY = iconY;
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            if (!visible) return;
            hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            boolean selected = switch (id) {
                case 0 -> filter.isBlacklist();
                case 1 -> !filter.isBlacklist();
                case 2 -> filter.respectsData();
                case 3 -> !filter.respectsData();
                default -> false;
            };
            minecraft.entityRenderer.disableLightmap();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            // Other GUI renderers can change the active unit/binding without updating
            // GlStateManager's cache.  Bind on the actual default unit explicitly.
            GL13.glActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(516, 0.1F);
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            minecraft.getTextureManager().bindTexture(WIDGETS);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,
                    minecraft.getTextureManager().getTexture(WIDGETS).getGlTextureId());
            int backgroundX = hovered ? (Mouse.isButtonDown(0) ? 36 : 18) : selected ? 72 : 0;
            int backgroundY = 0;
            Gui.drawModalRectWithCustomSizedTexture(x, y, backgroundX, backgroundY, 18, 18, 256, 256);
            ResourceLocation iconTexture = iconTextures.getOrDefault(id, ICONS);
            minecraft.getTextureManager().bindTexture(iconTexture);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,
                    minecraft.getTextureManager().getTexture(iconTexture).getGlTextureId());
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            if (iconTextures.containsKey(id))
                Gui.drawModalRectWithCustomSizedTexture(x + 1, y + 1, 0, 0, 16, 16, 16, 16);
            else
                Gui.drawModalRectWithCustomSizedTexture(x + 1, y + 1,
                        iconX * 16, iconY * 16, 16, 16, 256, 256);
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        }
    }
}
