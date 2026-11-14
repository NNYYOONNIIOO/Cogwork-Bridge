package com.cogwork.cogworkbridge.compat.jei;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.client.render.FastStateRendering;
import com.cogwork.cogworkbridge.client.render.GuiLightingState;
import com.cogwork.cogworkbridge.content.kinetics.deployer.DeployerBlock;
import com.cogwork.cogworkbridge.content.processing.AssemblyLineRecipes;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/** JEI view for the implemented assembly lines. */
public final class AssemblyLineJEI {
    private static final String UID = "cogworkbridge.assembly_line";
    private static final ResourceLocation WIDGETS = new ResourceLocation("create", "textures/gui/jei/widgets.png");
    private static final int STEP_WIDTH = 25, STEP_MARGIN = 3;
    private AssemblyLineJEI() { }

    public static void categories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new Category(registration.getJeiHelpers().getGuiHelper()));
    }

    public static void register(IModRegistry registry) {
        registry.handleRecipes(Wrapper.class, recipe -> recipe, UID);
        List<Wrapper> recipes = new ArrayList<>();
        for (AssemblyLineRecipes.Recipe recipe : AssemblyLineRecipes.all()) recipes.add(new Wrapper(recipe));
        registry.addRecipes(recipes, UID);
        registry.addRecipeCatalyst(new ItemStack(AllBlocks.DEPLOYER), UID);
        registry.addRecipeCatalyst(new ItemStack(AllBlocks.MECHANICAL_PRESS), UID);
        registry.addRecipeCatalyst(new ItemStack(AllBlocks.SPOUT), UID);
    }

    private static int start(AssemblyLineRecipes.Recipe recipe) {
        return (180 - (recipe.steps().size() * (STEP_WIDTH + STEP_MARGIN) - STEP_MARGIN)) / 2;
    }

    private static String describe(AssemblyLineRecipes.Step step) {
        return switch (step.operation()) {
            case DEPLOY -> I18n.format("tooltip.cogworkbridge.assembly.deploy", step.items().get(0).getDisplayName());
            case FILL -> I18n.format("tooltip.cogworkbridge.assembly.fill", step.fluid().getLocalizedName());
            case PRESS -> I18n.format("tooltip.cogworkbridge.assembly.press");
        };
    }

    private static final class Wrapper implements IRecipeWrapper {
        private final AssemblyLineRecipes.Recipe recipe;
        private Wrapper(AssemblyLineRecipes.Recipe recipe) { this.recipe = recipe; }

        @Override public void getIngredients(IIngredients ingredients) {
            List<List<ItemStack>> inputs = new ArrayList<>();
            inputs.add(recipe.input());
            inputs.add(java.util.Collections.singletonList(recipe.transitional()));
            for (int loop = 0; loop < recipe.loops(); loop++)
                for (AssemblyLineRecipes.Step step : recipe.steps()) if (!step.items().isEmpty()) inputs.add(step.items());
            ingredients.setInputLists(VanillaTypes.ITEM, inputs);
            List<ItemStack> outputs = new ArrayList<>();
            for (AssemblyLineRecipes.Output output : recipe.outputs()) outputs.add(output.stack());
            ingredients.setOutputs(VanillaTypes.ITEM, outputs);
            List<FluidStack> fluids = new ArrayList<>();
            for (int loop = 0; loop < recipe.loops(); loop++)
                for (AssemblyLineRecipes.Step step : recipe.steps()) if (step.fluid() != null) fluids.add(step.fluid());
            if (!fluids.isEmpty()) ingredients.setInputs(VanillaTypes.FLUID, fluids);
        }

        @Override public void drawInfo(Minecraft mc, int width, int height, int mouseX, int mouseY) {
            boolean randomOutput = recipe.outputs().size() > 1;
            int shift = randomOutput ? -7 : 0;
            mc.getTextureManager().bindTexture(WIDGETS);
            GlStateManager.color(1, 1, 1, 1);
            Gui.drawModalRectWithCustomSizedTexture(52 + shift, 94, 19, 0, 71, 10, 256, 256);
            if (randomOutput) {
                Gui.drawModalRectWithCustomSizedTexture(150 + shift, 90, 20, 156, 18, 18, 256, 256);
                mc.fontRenderer.drawString("?", 156 + shift, 95, 0xefefef);
            }
            if (recipe.loops() > 1) {
                mc.fontRenderer.drawString("x" + recipe.loops(), 102 + shift, 104, 0x888888);
            }
            int left = start(recipe);
            String[] romans = {"I", "II", "III", "IV", "V", "VI"};
            for (int i = 0; i < recipe.steps().size(); i++) {
                int center = left + i * (STEP_WIDTH + STEP_MARGIN) + STEP_WIDTH / 2;
                String numeral = i < romans.length ? romans[i] : Integer.toString(i + 1);
                mc.fontRenderer.drawString(numeral, center - mc.fontRenderer.getStringWidth(numeral) / 2, 2, 0x888888);
                drawMachine(recipe.steps().get(i), center, i);
            }
        }

        @Override public List<String> getTooltipStrings(int mouseX, int mouseY) {
            List<String> tooltip = new ArrayList<>();
            if (mouseY >= 5 && mouseY < 84) {
                int relative = mouseX - start(recipe);
                int step = relative / (STEP_WIDTH + STEP_MARGIN);
                if (relative >= 0 && step < recipe.steps().size() && relative % (STEP_WIDTH + STEP_MARGIN) < STEP_WIDTH) {
                    tooltip.add(I18n.format("tooltip.cogworkbridge.assembly.step", step + 1));
                    tooltip.add("\u00a72" + describe(recipe.steps().get(step)));
                }
            }
            if (recipe.loops() > 1 && mouseX >= 55 && mouseX < 120 && mouseY >= 92 && mouseY < 115)
                tooltip.add(I18n.format("tooltip.cogworkbridge.assembly.repeat", recipe.loops()));
            if (recipe.outputs().size() > 1 && mouseX >= 143 && mouseX < 161 && mouseY >= 90 && mouseY < 108) {
                tooltip.add(I18n.format("tooltip.cogworkbridge.assembly.junk"));
                for (int i = 1; i < recipe.outputs().size(); i++) {
                    AssemblyLineRecipes.Output output = recipe.outputs().get(i);
                    tooltip.add("\u00a77" + output.stack().getDisplayName() + ": "
                            + String.format(java.util.Locale.ROOT, "%.2f%%", output.chance() * 100F));
                }
            }
            return tooltip;
        }
    }

    private static final class Category implements IRecipeCategory<Wrapper> {
        private final IDrawable background, slot, chanceSlot, icon;
        private Category(IGuiHelper helper) {
            background = helper.createBlankDrawable(180, 115);
            slot = helper.createDrawable(WIDGETS, 0, 0, 18, 18);
            chanceSlot = helper.createDrawable(WIDGETS, 20, 156, 18, 18);
            icon = helper.createDrawableIngredient(new ItemStack(AllItems.PRECISION_MECHANISM));
        }
        @Override public String getUid() { return UID; }
        @Override public String getTitle() { return I18n.format("jei.cogworkbridge.assembly_line"); }
        @Override public String getModName() { return Reference.MOD_NAME; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override public void setRecipe(IRecipeLayout layout, Wrapper wrapper, IIngredients ingredients) {
            AssemblyLineRecipes.Recipe recipe = wrapper.recipe;
            boolean randomOutput = recipe.outputs().size() > 1;
            int shift = randomOutput ? -7 : 0;
            layout.getItemStacks().init(0, true, 27 + shift, 91);
            layout.getItemStacks().setBackground(0, slot);
            layout.getItemStacks().set(0, recipe.input());
            layout.getItemStacks().init(99, true, 80 + shift, 91);
            layout.getItemStacks().setBackground(99, slot);
            layout.getItemStacks().set(99, recipe.transitional());
            int itemSlot = 1, fluidSlot = 0;
            for (int i = 0; i < recipe.steps().size(); i++) {
                AssemblyLineRecipes.Step step = recipe.steps().get(i);
                int x = start(recipe) + i * (STEP_WIDTH + STEP_MARGIN) + 4;
                if (!step.items().isEmpty()) {
                    layout.getItemStacks().init(itemSlot, true, x, 15);
                    layout.getItemStacks().setBackground(itemSlot, slot);
                    layout.getItemStacks().set(itemSlot++, step.items());
                }
                if (step.fluid() != null) {
                    layout.getFluidStacks().init(fluidSlot, true, x, 15, 16, 16,
                            Math.max(1000, step.fluid().amount), false, null);
                    layout.getFluidStacks().setBackground(fluidSlot, slot);
                    layout.getFluidStacks().set(fluidSlot++, step.fluid());
                }
            }
            layout.getItemStacks().init(100, false, 132 + shift, 91);
            layout.getItemStacks().setBackground(100, randomOutput ? chanceSlot : slot);
            layout.getItemStacks().set(100, recipe.outputs().get(0).stack());
            layout.getItemStacks().addTooltipCallback((index, input, stack, tooltip) -> {
                if (index == 100 && randomOutput)
                    tooltip.add(I18n.format("jei.cogworkbridge.chance", Math.round(recipe.outputs().get(0).chance() * 100F)));
            });
        }
        @Override public void drawExtras(Minecraft mc) { }
    }

    private static void drawMachine(AssemblyLineRecipes.Step step, int center, int index) {
        try (GuiLightingState ignored = new GuiLightingState()) {
            GlStateManager.pushMatrix();
            try {
                GlStateManager.enableDepth(); GlStateManager.disableCull(); GlStateManager.enableRescaleNormal();
                GlStateManager.color(1, 1, 1, 1);
                RenderHelper.enableGUIStandardItemLighting();
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
                GlStateManager.translate(center, 54, 100);
                GlStateManager.scale(15F, -15F, 15F);
                GlStateManager.rotate(15.5F, 1, 0, 0);
                GlStateManager.rotate(-22.5F, 0, 1, 0);
                GlStateManager.translate(-.5D, -.5D, -.5D);
                float angle = Minecraft.getSystemTime() / 12.5F;
                double movement = .3D * (1D + Math.sin(Minecraft.getSystemTime() / 500D - index));
                switch (step.operation()) {
                    case DEPLOY -> {
                        draw(AllBlocks.DEPLOYER.getDefaultState().withProperty(DeployerBlock.FACING, EnumFacing.DOWN));
                        rotate(AllBlocks.SHAFT.getDefaultState().withProperty(KineticBlock.AXIS, EnumFacing.Axis.Z), angle);
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(0, -movement, 0);
                        GlStateManager.translate(.5D, .5D, .5D); GlStateManager.rotate(90F, 1, 0, 0); GlStateManager.translate(-.5D, -.5D, -.5D);
                        draw(part(AllBlocks.DEPLOYER_POLE)); draw(part(AllBlocks.DEPLOYER_HAND_HOLDING));
                        GlStateManager.popMatrix();
                    }
                    case PRESS -> {
                        draw(AllBlocks.MECHANICAL_PRESS.getDefaultState());
                        rotate(AllBlocks.SHAFT.getDefaultState().withProperty(KineticBlock.AXIS, EnumFacing.Axis.Z), angle);
                        GlStateManager.pushMatrix(); GlStateManager.translate(0, -movement, 0);
                        draw(part(AllBlocks.MECHANICAL_PRESS_HEAD)); GlStateManager.popMatrix();
                    }
                    case FILL -> {
                        IBlockState state = AllBlocks.SPOUT.getDefaultState();
                        draw(state);
                        com.cogwork.cogworkbridge.client.render.FluidSpoutRenderer.renderParts(state, (float) movement);
                    }
                }
                GlStateManager.translate(0, -2D, 0);
                draw(AllBlocks.DEPOT.getDefaultState());
            } finally { GlStateManager.popMatrix(); }
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    private static IBlockState part(RenderPartBlock block) {
        return block.getDefaultState().withProperty(RenderPartBlock.FACING, EnumFacing.UP);
    }
    private static void draw(IBlockState state) { FastStateRendering.getInstance().renderFast(state); }
    private static void rotate(IBlockState state, float angle) {
        GlStateManager.pushMatrix(); GlStateManager.translate(.5D, .5D, .5D);
        GlStateManager.rotate(angle, 0, 0, 1); GlStateManager.translate(-.5D, -.5D, -.5D);
        draw(state); GlStateManager.popMatrix();
    }
}
