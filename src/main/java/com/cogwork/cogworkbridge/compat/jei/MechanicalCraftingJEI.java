package com.cogwork.cogworkbridge.compat.jei;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.client.render.FastStateRendering;
import com.cogwork.cogworkbridge.client.render.GuiLightingState;
import com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes;
import com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class MechanicalCraftingJEI {
    private static final String UID = "cogworkbridge.mechanical_crafting";
    private static final ResourceLocation WIDGETS = new ResourceLocation("create", "textures/gui/jei/widgets.png");

    private MechanicalCraftingJEI() { }

    public static void categories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new Category(registration.getJeiHelpers().getGuiHelper()));
    }

    public static void register(IModRegistry registry) {
        registry.handleRecipes(Wrapper.class, recipe -> recipe, UID);
        List<Wrapper> recipes = new ArrayList<>();
        for (var recipe : UpstreamProcessingRecipes.mechanicalCrafting()) recipes.add(new Wrapper(recipe));
        registry.addRecipes(recipes, UID);
        registry.addRecipeCatalyst(new ItemStack(AllBlocks.MECHANICAL_CRAFTER), UID);
    }

    private static final class Wrapper implements IRecipeWrapper {
        private final UpstreamProcessingRecipes.MechanicalRecipe recipe;
        private Wrapper(UpstreamProcessingRecipes.MechanicalRecipe recipe) { this.recipe = recipe; }
        @Override public void getIngredients(IIngredients ingredients) {
            List<List<ItemStack>> inputs = new ArrayList<>();
            for (List<ItemStack> slot : recipe.ingredients()) if (!slot.isEmpty()) inputs.add(slot);
            ingredients.setInputLists(VanillaTypes.ITEM, inputs);
            ingredients.setOutput(VanillaTypes.ITEM, recipe.result().copy());
        }
        @Override public void drawInfo(Minecraft mc, int width, int height, int mouseX, int mouseY) {
            int count = 0;
            for (List<ItemStack> ingredient : recipe.ingredients()) if (!ingredient.isEmpty()) count++;
            mc.fontRenderer.drawString(Integer.toString(count), 142, 39, 0xFFFFFF);
        }
    }

    private static final class Category implements IRecipeCategory<Wrapper> {
        private final IDrawable background, slot, arrow;
        private Category(IGuiHelper helper) {
            background = helper.createBlankDrawable(177, 110);
            slot = helper.createDrawable(WIDGETS, 0, 0, 18, 18);
            arrow = helper.createDrawable(WIDGETS, 0, 21, 18, 14);
        }
        @Override public String getUid() { return UID; }
        @Override public String getTitle() { return I18n.format("jei.cogworkbridge.mechanical_crafting"); }
        @Override public String getModName() { return Reference.MOD_NAME; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return null; }

        @Override public void setRecipe(IRecipeLayout layout, Wrapper wrapper, IIngredients ingredients) {
            var recipe = wrapper.recipe;
            float scale = Math.min(1F, 96F / (19F * Math.max(recipe.width(), recipe.height())));
            int size = Math.max(1, Math.round(16F * scale));
            float step = 19F * scale;
            int left = Math.round(53F - recipe.width() * step / 2F);
            int top = Math.round(53F - recipe.height() * step / 2F);
            ScaledIngredient renderer = new ScaledIngredient(scale);
            IDrawable background = new ScaledSlot(slot, scale);
            int index = 0;
            for (int y = 0; y < recipe.height(); y++) for (int x = 0; x < recipe.width(); x++) {
                List<ItemStack> options = recipe.ingredients().get(y * recipe.width() + x);
                if (options.isEmpty()) continue;
                int px = left + Math.round(x * step), py = top + Math.round(y * step);
                layout.getItemStacks().init(index, true, renderer, px, py, size, size, 0, 0);
                layout.getItemStacks().setBackground(index, background);
                layout.getItemStacks().set(index, options);
                index++;
            }
            layout.getItemStacks().init(index, false, 134, 81);
            layout.getItemStacks().setBackground(index, slot);
            layout.getItemStacks().set(index, recipe.result());
        }

        @Override public void drawExtras(Minecraft mc) {
            arrow.draw(mc, 128, 59);
            try (GuiLightingState ignored = new GuiLightingState()) {
                GlStateManager.pushMatrix();
                try {
                GlStateManager.enableDepth(); GlStateManager.enableRescaleNormal(); GlStateManager.disableCull();
                GlStateManager.color(1F, 1F, 1F, 1F);
                RenderHelper.enableGUIStandardItemLighting();
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
                GlStateManager.translate(142, 38, 100); GlStateManager.scale(22F, -22F, 22F);
                GlStateManager.rotate(12.5F, 1, 0, 0); GlStateManager.rotate(22.5F, 0, 1, 0);
                GlStateManager.translate(-.5D, -.5D, -.5D);
                FastStateRendering.getInstance().renderFast(AllBlocks.MECHANICAL_CRAFTER.getDefaultState()
                        .withProperty(MechanicalCrafterBlock.FACING, EnumFacing.SOUTH));
                GlStateManager.translate(.5D, .5D, .5D);
                GlStateManager.rotate(Minecraft.getSystemTime() / 12.5F, 0, 0, 1);
                GlStateManager.translate(-.5D, -.5D, -.5D);
                FastStateRendering.getInstance().renderFast(AllBlocks.COGWHEEL_SHAFTLESS.getDefaultState()
                        .withProperty(RenderPartBlock.FACING, EnumFacing.SOUTH));
                } finally {
                    GlStateManager.popMatrix();
                }
            }
        }
    }

    private static final class ScaledIngredient implements IIngredientRenderer<ItemStack> {
        private final float scale;
        private ScaledIngredient(float scale) { this.scale = scale; }
        @Override public void render(Minecraft mc, int x, int y, ItemStack stack) {
            if (stack == null || stack.isEmpty()) return;
            try (GuiLightingState ignored = new GuiLightingState()) {
                RenderHelper.enableGUIStandardItemLighting();
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
                GlStateManager.pushMatrix();
                try {
                    GlStateManager.translate(x, y, 0);
                    GlStateManager.scale(scale, scale, scale);
                    mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
                    mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, stack, 0, 0, null);
                } finally {
                    GlStateManager.popMatrix();
                }
            }
        }
        @Override public List<String> getTooltip(Minecraft mc, ItemStack stack, ITooltipFlag flag) {
            return stack.getTooltip(mc.player, flag);
        }
    }

    private static final class ScaledSlot implements IDrawable {
        private final IDrawable slot;
        private final float scale;
        private ScaledSlot(IDrawable slot, float scale) { this.slot = slot; this.scale = scale; }
        @Override public int getWidth() { return Math.max(1, Math.round(slot.getWidth() * scale)); }
        @Override public int getHeight() { return Math.max(1, Math.round(slot.getHeight() * scale)); }
        @Override public void draw(Minecraft mc, int x, int y) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0);
            GlStateManager.scale(scale, scale, scale);
            slot.draw(mc, 0, 0);
            GlStateManager.popMatrix();
        }
    }
}
