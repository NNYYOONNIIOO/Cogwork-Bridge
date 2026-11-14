package com.cogwork.cogworkbridge.compat.jei;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.kinetics.saw.SawRecipes;
import com.cogwork.cogworkbridge.content.kinetics.deployer.DeployerTransportRecipes;
import com.cogwork.cogworkbridge.content.processing.casing.CasingApplication;
import com.cogwork.cogworkbridge.content.processing.millstone.MillstoneRecipes;
import com.cogwork.cogworkbridge.content.processing.mixer.MixingRecipes;
import com.cogwork.cogworkbridge.content.processing.press.PressingRecipes;
import com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes;
import com.cogwork.cogworkbridge.client.render.FastStateRendering;
import com.cogwork.cogworkbridge.client.BlazeBurnerModels;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.AllItems;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumFacing;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.lwjgl.opengl.GL11;

/** Recipe views use the same lookup functions as the machines. */
public final class ProcessingJEI {
    private static final ResourceLocation WIDGETS = new ResourceLocation("create", "textures/gui/jei/widgets.png");
    private static final String PRESS = "cogworkbridge.pressing", COMPACT = "cogworkbridge.compacting", MIX = "cogworkbridge.mixing", SAW = "cogworkbridge.cutting", CRUSH = "cogworkbridge.crushing", MILL = "cogworkbridge.milling", CASING = "cogworkbridge.casing", DEPLOY = "cogworkbridge.deploying", SPLASH = "cogworkbridge.splashing", FILL = "cogworkbridge.filling", EMPTY = "cogworkbridge.emptying", SMOKE = "cogworkbridge.smoking", BLAST = "cogworkbridge.blasting";
    private static final String POLISH = "cogworkbridge.sandpaper_polishing";
    private ProcessingJEI() { }
    public static void categories(IRecipeCategoryRegistration registration) {
        IGuiHelper helper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new Category(helper, PRESS),
                new Category(helper, COMPACT),
                new Category(helper, MIX),
                new Category(helper, SAW),
                new Category(helper, MILL),
                new Category(helper, CRUSH),
                new Category(helper, CASING),
                new Category(helper, POLISH),
                new Category(helper, DEPLOY),
                new Category(helper, SPLASH),
                new Category(helper, SMOKE),
                new Category(helper, BLAST),
                new Category(helper, FILL),
                new Category(helper, EMPTY));
    }
    public static void register(IModRegistry registry) {
        for (String id : new String[]{PRESS, COMPACT, MIX, SAW, MILL, CRUSH, CASING, POLISH, DEPLOY, SPLASH, SMOKE, BLAST, FILL, EMPTY}) registry.handleRecipes(Recipe.class, r -> r, id);
        List<Recipe> press = new ArrayList<>(), mixing = new ArrayList<>(), cutting = new ArrayList<>(), milling = new ArrayList<>(), casing = new ArrayList<>();
        List<ItemStack> knownItems = new ArrayList<>(registry.getIngredientRegistry().getAllIngredients(VanillaTypes.ITEM));
        List<ItemStack> potionInputs = com.cogwork.cogworkbridge.content.fluids.PotionMixingRecipes.potionVariants(knownItems);
        List<ItemStack> brewingIngredients = new ArrayList<>(knownItems);
        brewingIngredients.addAll(registry.getIngredientRegistry().getPotionIngredients());
        List<ItemStack> inputs = new ArrayList<>(Arrays.asList(new ItemStack(Items.REEDS), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT),
                new ItemStack(Blocks.COBBLESTONE), new ItemStack(Blocks.GRAVEL), new ItemStack(Blocks.SANDSTONE), new ItemStack(Blocks.STONE, 1, 5),
                new ItemStack(Blocks.STONE, 1, 1), new ItemStack(Blocks.WOOL), new ItemStack(AllItems.PULP)));
        for (String ore : new String[]{"ingotIron", "ingotGold", "ingotCopper", "ingotBrass"}) inputs.addAll(OreDictionary.getOres(ore));
        for (ItemStack stack : inputs) {
            ItemStack output = PressingRecipes.result(stack);
            if (!output.isEmpty()) press.add(new Recipe(Collections.singletonList(stack.copy()), output, false));
            MillstoneRecipes.Recipe mill = MillstoneRecipes.find(stack);
            if (mill != null) milling.add(new Recipe(Collections.singletonList(stack.copy()), mill.result().copy(), false));
        }
        List<ItemStack> nuggets = new ArrayList<>(OreDictionary.getOres("nuggetIron")); nuggets.addAll(OreDictionary.getOres("nuggetZinc"));
        for (ItemStack nugget : nuggets) {
            ItemStack andesite = new ItemStack(Blocks.STONE, 1, 5);
            ItemStack output = MixingRecipes.result(andesite, nugget, false);
            if (!output.isEmpty()) mixing.add(new Recipe(Arrays.asList(andesite, nugget.copy()), output, false));
        }
        for (ItemStack copper : OreDictionary.getOres("ingotCopper")) for (ItemStack zinc : OreDictionary.getOres("ingotZinc")) {
            ItemStack output = MixingRecipes.result(copper, zinc, true);
            if (!output.isEmpty()) mixing.add(new Recipe(Arrays.asList(copper.copy(), zinc.copy()), output, true));
        }
        String[] woods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
        for (int i = 0; i < woods.length; i++) {
            ItemStack log = new ItemStack(i < 4 ? Blocks.LOG : Blocks.LOG2, 1, i < 4 ? i : i - 4);
            ItemStack result = SawRecipes.result(log);
            if (!result.isEmpty()) cutting.add(new Recipe(Collections.singletonList(log), result, false));
            for (String kind : new String[]{"log", "wood"}) {
                Item wood = ForgeRegistries.ITEMS.getValue(new ResourceLocation("farmers_future_delight", "stripped_" + woods[i] + "_" + kind));
                if (wood == null) continue;
                ItemStack stripped = new ItemStack(wood), plank = SawRecipes.result(stripped);
                if (!plank.isEmpty()) cutting.add(new Recipe(Collections.singletonList(stripped), plank, false));
            }
        }
        List<ItemStack> strippedLogs = new ArrayList<>(OreDictionary.getOres("stripped_logs"));
        if (!strippedLogs.isEmpty()) {
            List<ItemStack> mats = new ArrayList<>(OreDictionary.getOres("ingotCopper"));
            mats.addAll(OreDictionary.getOres("ingotBrass"));
            mats.add(new ItemStack(AllItems.ANDESITE_ALLOY));
            for (ItemStack material : mats) {
                net.minecraft.block.Block resultBlock = CasingApplication.casing(material);
                if (resultBlock == null) continue;
                Recipe application = new Recipe(Arrays.asList(strippedLogs.get(0).copy(), material.copy()), new ItemStack(resultBlock), false);
                application.alternatives = strippedLogs;
                application.application = true;
                casing.add(application);
            }
        }
        Recipe railwayCasing = new Recipe(Arrays.asList(new ItemStack(AllBlocks.BRASS_CASING), new ItemStack(AllItems.STURDY_SHEET)),
                new ItemStack(com.cogwork.cogworkbridge.registry.BlockFamily.get("railway_casing")), false);
        railwayCasing.heldAlternatives = new ArrayList<>(OreDictionary.getOres("plateObsidian"));
        railwayCasing.application = true;
        casing.add(railwayCasing);
        Recipe pulp = new Recipe(Arrays.asList(new ItemStack(Items.REEDS, 4)), new ItemStack(AllItems.PULP), false);
        pulp.fluid = new FluidStack(FluidRegistry.WATER, 250);
        pulp.alternatives = new ArrayList<>();
        pulp.alternatives.add(new ItemStack(Items.REEDS, 4));
        for (ItemStack sapling : OreDictionary.getOres("treeSapling")) {
            ItemStack four = sapling.copy(); four.setCount(4); pulp.alternatives.add(four);
        }
        Item bamboo = ForgeRegistries.ITEMS.getValue(new ResourceLocation("farmers_future_delight", "bamboo"));
        if (bamboo != null) pulp.alternatives.add(new ItemStack(bamboo, 4));
        mixing.add(pulp);
        Recipe lava = new Recipe(Collections.singletonList(new ItemStack(Blocks.COBBLESTONE)), ItemStack.EMPTY, true);
        lava.fluidOutput = new FluidStack(FluidRegistry.LAVA, 50); lava.superheated = true;
        lava.alternatives = new ArrayList<>(OreDictionary.getOres("cobblestone"));
        if (lava.alternatives.isEmpty()) lava.alternatives.add(new ItemStack(Blocks.COBBLESTONE));
        mixing.add(lava);
        List<Recipe> deploying = new ArrayList<>();
        for (ItemStack plank : OreDictionary.getOres("plankWood")) {
            for (ItemStack input : Arrays.asList(new ItemStack(AllBlocks.SHAFT), new ItemStack(AllBlocks.COGWHEEL))) {
                ItemStack output = DeployerTransportRecipes.result(input, plank);
                if (!output.isEmpty()) deploying.add(new Recipe(Arrays.asList(input, plank.copy()), output, false));
            }
        }
        for (var upstream : UpstreamProcessingRecipes.all("deploying")) {
            if (upstream.outputs().size() != 1 || upstream.outputs().get(0).chance() < 1F) continue;
            ItemStack input = upstream.inputs().get(0), held = upstream.heldItems().get(0);
            if (UpstreamProcessingRecipes.find("deploying", input, held) != upstream
                    || !ItemStack.areItemsEqual(DeployerTransportRecipes.result(input, held), upstream.outputs().get(0).stack())) continue;
            deploying.removeIf(old -> old.inputs.size() == 2 && ItemStack.areItemsEqual(old.inputs.get(0), input)
                    && ItemStack.areItemsEqual(old.inputs.get(1), held));
            Recipe recipe = new Recipe(Arrays.asList(input.copy(), held.copy()), upstream.outputs().get(0).stack().copy(), false);
            recipe.alternatives = upstream.inputs(); recipe.heldAlternatives = upstream.heldItems(); recipe.keepHeld = upstream.keepHeld();
            deploying.add(recipe);
        }
        List<Recipe> polishing = new ArrayList<>();
        addUpstream(polishing, "sandpaper_polishing");
        for (ItemStack input : com.cogwork.cogworkbridge.content.equipment.SandPaperItem.registeredInputs()) {
            ItemStack output = com.cogwork.cogworkbridge.content.equipment.SandPaperItem.polish(input);
            if (!output.isEmpty() && polishing.stream().noneMatch(recipe -> inputGroups(recipe).get(0).stream()
                    .anyMatch(option -> OreDictionary.itemMatches(option, input, false))))
                polishing.add(new Recipe(Collections.singletonList(input), output, false));
        }
        List<Recipe> crushing = new ArrayList<>(), splashing = new ArrayList<>();
        addUpstream(press, "pressing"); addUpstream(milling, "milling"); addUpstream(cutting, "cutting");
        addUpstream(crushing, "crushing"); addUpstream(splashing, "splashing");
        if (crushing.isEmpty()) {
            Recipe gravel = new Recipe(Collections.singletonList(new ItemStack(Blocks.GRAVEL)), new ItemStack(Blocks.SAND), false);
            gravel.outputs.add(new UpstreamProcessingRecipes.Output(new ItemStack(Items.FLINT), .1F));
            gravel.outputs.add(new UpstreamProcessingRecipes.Output(new ItemStack(Items.CLAY_BALL), .05F));
            crushing.add(gravel);
        }
        List<Recipe> smoking = new ArrayList<>(), blasting = new ArrayList<>();
        for (var smelting : net.minecraft.item.crafting.FurnaceRecipes.instance().getSmeltingList().entrySet()) {
            Recipe recipe = new Recipe(Collections.singletonList(smelting.getKey().copy()), smelting.getValue().copy(), false);
            (smelting.getKey().getItem() instanceof net.minecraft.item.ItemFood || smelting.getValue().getItem() instanceof net.minecraft.item.ItemFood ? smoking : blasting).add(recipe);
        }
        List<Recipe> filling = new ArrayList<>();
        Recipe grass = new Recipe(Collections.singletonList(new ItemStack(Blocks.DIRT)), new ItemStack(Blocks.GRASS), false);
        grass.fluid = new FluidStack(FluidRegistry.WATER, 500);
        filling.add(grass);
        Recipe waterBucket = new Recipe(Collections.singletonList(new ItemStack(Items.BUCKET)), new ItemStack(Items.WATER_BUCKET), false);
        waterBucket.fluid = new FluidStack(FluidRegistry.WATER, 1000); filling.add(waterBucket);
        Recipe lavaBucket = new Recipe(Collections.singletonList(new ItemStack(Items.BUCKET)), new ItemStack(Items.LAVA_BUCKET), false);
        lavaBucket.fluid = new FluidStack(FluidRegistry.LAVA, 1000); filling.add(lavaBucket);
        Recipe bottle = new Recipe(Collections.singletonList(new ItemStack(Items.GLASS_BOTTLE)),
                net.minecraft.potion.PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), net.minecraft.init.PotionTypes.WATER), false);
        bottle.fluid = new FluidStack(FluidRegistry.WATER, 250); filling.add(bottle);
        if (FluidRegistry.getFluid("milk") != null) {
            Recipe milkBucket = new Recipe(Collections.singletonList(new ItemStack(Items.BUCKET)), new ItemStack(Items.MILK_BUCKET), false);
            milkBucket.fluid = new FluidStack(FluidRegistry.getFluid("milk"), 1000); filling.add(milkBucket);
        }
        for (var upstream : UpstreamProcessingRecipes.mixing()) {
            if (upstream.id().endsWith("andesite_alloy.json") || upstream.id().endsWith("andesite_alloy_from_zinc.json")
                    || upstream.id().endsWith("brass_ingot.json") || upstream.id().endsWith("cardboard_pulp.json")
                    || upstream.id().endsWith("lava_from_cobble.json")) continue;
            List<ItemStack> inputsForRecipe = new ArrayList<>();
            for (List<ItemStack> group : upstream.items()) inputsForRecipe.add(group.get(0).copy());
            Recipe recipe = new Recipe(inputsForRecipe, upstream.itemOutput().copy(), upstream.heat() > 0);
            recipe.itemGroups = upstream.items(); recipe.fluids = upstream.fluids();
            recipe.fluidOutput = upstream.fluidOutput() == null ? null : upstream.fluidOutput().copy();
            recipe.superheated = upstream.heat() >= 2; mixing.add(recipe);
        }
        for (var brewing : com.cogwork.cogworkbridge.content.fluids.PotionMixingRecipes.previews(brewingIngredients, potionInputs)) {
            Recipe recipe = new Recipe(Collections.singletonList(brewing.ingredient()), ItemStack.EMPTY, true);
            recipe.fluid = brewing.input();
            recipe.fluidOutput = brewing.output();
            mixing.add(recipe);
        }
        for (var upstream : UpstreamProcessingRecipes.all("filling")) {
            if (upstream.outputs().size() != 1 || upstream.outputs().get(0).chance() < 1F) continue;
            ItemStack input = upstream.inputs().get(0), output = upstream.outputs().get(0).stack();
            filling.removeIf(old -> old.fluid != null && old.fluid.isFluidEqual(upstream.fluid())
                    && old.fluid.amount == upstream.fluid().amount && upstream.matches(old.inputs.get(0)));
            Recipe recipe = new Recipe(Collections.singletonList(input.copy()), output.copy(), false);
            recipe.alternatives = upstream.inputs(); recipe.fluid = upstream.fluid().copy(); filling.add(recipe);
        }
        java.util.Set<String> containerRecipes = new java.util.HashSet<>();
        for (Recipe recipe : filling) if (recipe.fluid != null)
            containerRecipes.add(containerKey(recipe.inputs.get(0), recipe.fluid));
        for (ItemStack potion : potionInputs) {
            FluidStack fluid = com.cogwork.cogworkbridge.content.fluids.PotionFluid.fromPotion(potion, 250);
            ItemStack bottleItem = new ItemStack(Items.GLASS_BOTTLE);
            var filled = com.cogwork.cogworkbridge.content.fluids.SpoutRecipes.preview(bottleItem, fluid);
            if (filled == null || !containerRecipes.add(containerKey(bottleItem, filled.fluid()))) continue;
            Recipe recipe = new Recipe(Collections.singletonList(bottleItem), filled.item(), false);
            recipe.fluid = filled.fluid();
            filling.add(recipe);
        }
        for (ItemStack input : registry.getIngredientRegistry().getAllIngredients(VanillaTypes.ITEM)) {
            try {
                if (!com.cogwork.cogworkbridge.content.fluids.SpoutRecipes.isContainer(input)) continue;
                for (FluidStack fluid : registry.getIngredientRegistry().getAllIngredients(VanillaTypes.FLUID)) {
                    var filled = com.cogwork.cogworkbridge.content.fluids.SpoutRecipes.preview(input, fluid);
                    if (filled == null || !containerRecipes.add(containerKey(input, filled.fluid()))) continue;
                    ItemStack one = input.copy(); one.setCount(1);
                    Recipe recipe = new Recipe(Collections.singletonList(one), filled.item(), false);
                    recipe.fluid = filled.fluid(); filling.add(recipe);
                }
            } catch (RuntimeException ignored) { /* Ignore individual incompatible Forge fluid handlers. */ }
        }
        for (Recipe recipe : mixing) recipe.mixing = true;
        List<Recipe> emptying = new ArrayList<>();
        for (ItemStack stack : registry.getIngredientRegistry().getAllIngredients(VanillaTypes.ITEM)) {
            try {
                var drained = com.cogwork.cogworkbridge.content.fluids.ItemDrainRecipes.preview(stack);
                if (drained == null) continue;
                ItemStack input = stack.copy(); input.setCount(1);
                Recipe recipe = new Recipe(Collections.singletonList(input), drained.container(), false);
                recipe.fluidOutput = drained.fluid(); emptying.add(recipe);
            } catch (RuntimeException ignored) { /* An incompatible container must not break JEI registration. */ }
        }
        for (ItemStack potion : potionInputs) {
            var drained = com.cogwork.cogworkbridge.content.fluids.ItemDrainRecipes.preview(potion);
            if (drained == null) continue;
            Recipe recipe = new Recipe(Collections.singletonList(potion.copy()), drained.container(), false);
            recipe.fluidOutput = drained.fluid();
            emptying.add(recipe);
        }
        List<Recipe> compacting = compactingRecipes();
        for (var upstream : UpstreamProcessingRecipes.compacting()) {
            List<ItemStack> compactInputs = new ArrayList<>();
            for (List<ItemStack> group : upstream.items()) compactInputs.add(group.get(0).copy());
            Recipe recipe = new Recipe(compactInputs, upstream.itemOutput().copy(), upstream.heat() > 0);
            recipe.itemGroups = upstream.items(); recipe.fluids = upstream.fluids();
            recipe.fluidOutput = upstream.fluidOutput() == null ? null : upstream.fluidOutput().copy();
            recipe.superheated = upstream.heat() >= 2; recipe.mixing = true; compacting.add(recipe);
        }
        for (List<Recipe> recipes : Arrays.asList(press, mixing, cutting, milling, casing, deploying, crushing,
                splashing, compacting, filling, emptying, smoking, blasting, polishing)) {
            recipes.removeIf(ProcessingJEI::isAssemblyIntermediateRecipe);
            mergeOreVariants(recipes);
        }
        registry.addRecipes(filling, FILL); registry.addRecipes(emptying, EMPTY);
        registry.addRecipes(splashing, SPLASH); registry.addRecipes(smoking, SMOKE); registry.addRecipes(blasting, BLAST);
        registry.addRecipes(crushing, CRUSH);
        registry.addRecipes(polishing, POLISH);
        registry.addRecipes(compacting, COMPACT);
        registry.addRecipes(press, PRESS); registry.addRecipes(mixing, MIX); registry.addRecipes(cutting, SAW); registry.addRecipes(milling, MILL); registry.addRecipes(casing, CASING); registry.addRecipes(deploying, DEPLOY);
        registry.addRecipeCatalyst(new ItemStack(AllBlocks.MECHANICAL_PRESS), PRESS);
        registry.addRecipeCatalyst(new ItemStack(AllBlocks.MECHANICAL_PRESS), COMPACT);
        registry.addRecipeCatalyst(new ItemStack(AllBlocks.BASIN), COMPACT);
        registry.addRecipeCatalyst(new ItemStack(AllBlocks.MECHANICAL_CRAFTER), mezz.jei.api.recipe.VanillaRecipeCategoryUid.CRAFTING);
        registry.addRecipeCatalyst(new ItemStack(AllBlocks.MECHANICAL_MIXER), MIX);
        registry.addRecipeCatalyst(new ItemStack(AllBlocks.MECHANICAL_SAW), SAW);
        registry.addRecipeCatalyst(new ItemStack(AllBlocks.MILLSTONE), MILL);
        registry.addRecipeCatalyst(new ItemStack(AllBlocks.CRUSHING_WHEEL), CRUSH);
        registry.addRecipeCatalyst(new ItemStack(AllItems.ANDESITE_ALLOY), CASING);
        registry.addRecipeCatalyst(new ItemStack(AllBlocks.DEPLOYER), CASING);
        registry.addRecipeCatalyst(new ItemStack(AllItems.SAND_PAPER), POLISH);
        registry.addRecipeCatalyst(new ItemStack(AllItems.RED_SAND_PAPER), POLISH);
        registry.addRecipeCatalyst(new ItemStack(AllBlocks.DEPLOYER), DEPLOY);
        registry.addRecipeCatalyst(new ItemStack(AllBlocks.SPOUT), FILL);
        registry.addRecipeCatalyst(new ItemStack(AllBlocks.ITEM_DRAIN), EMPTY);
        for (String id : new String[]{SPLASH, SMOKE, BLAST}) registry.addRecipeCatalyst(new ItemStack(AllBlocks.ENCASED_FAN), id);
    }
    private static boolean isAssemblyIntermediateRecipe(Recipe recipe) {
        for (ItemStack input : recipe.inputs) if (isAssemblyIntermediate(input)) return true;
        for (ItemStack input : recipe.alternatives) if (isAssemblyIntermediate(input)) return true;
        for (ItemStack input : recipe.heldAlternatives) if (isAssemblyIntermediate(input)) return true;
        for (List<ItemStack> group : recipe.itemGroups)
            for (ItemStack input : group) if (isAssemblyIntermediate(input)) return true;
        for (UpstreamProcessingRecipes.Output output : recipe.outputs)
            if (isAssemblyIntermediate(output.stack())) return true;
        return false;
    }

    private static boolean isAssemblyIntermediate(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() == AllItems.UNPROCESSED_OBSIDIAN_SHEET
                || stack.getItem() == AllItems.INCOMPLETE_PRECISION_MECHANISM);
    }
    private static String containerKey(ItemStack input, FluidStack fluid) {
        ItemStack one = input.copy(); one.setCount(1);
        return one.serializeNBT().toString() + ":" + fluid.writeToNBT(new net.minecraft.nbt.NBTTagCompound());
    }
    private static List<Recipe> compactingRecipes() {
        List<Recipe> result = new ArrayList<>();
        for (net.minecraft.item.crafting.IRecipe recipe : net.minecraft.item.crafting.CraftingManager.REGISTRY) {
            if (!(recipe instanceof net.minecraft.item.crafting.ShapedRecipes) && !(recipe instanceof net.minecraft.item.crafting.ShapelessRecipes)) continue;
            var ingredients = recipe.getIngredients();
            int count = ingredients.size(), size = count == 4 ? 2 : count == 9 ? 3 : 0;
            if (size == 0 || ingredients.get(0).getMatchingStacks().length == 0) continue;
            for (ItemStack sample : ingredients.get(0).getMatchingStacks()) {
                if (sample.isEmpty() || sample.getMetadata() == OreDictionary.WILDCARD_VALUE || sample.getItem().hasContainerItem(sample)) continue;
                boolean uniform = true;
                for (var ingredient : ingredients) if (!ingredient.apply(sample)) { uniform = false; break; }
                if (!uniform) continue;
                net.minecraft.inventory.Container container = new net.minecraft.inventory.Container() {
                    @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) { return false; }
                };
                net.minecraft.inventory.InventoryCrafting grid = new net.minecraft.inventory.InventoryCrafting(container, 3, 3);
                for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                    ItemStack one = sample.copy(); one.setCount(1); grid.setInventorySlotContents(y * 3 + x, one);
                }
                try {
                    if (net.minecraft.item.crafting.CraftingManager.findMatchingRecipe(grid, null) != recipe) continue;
                    ItemStack output = recipe.getCraftingResult(grid);
                    if (output.isEmpty()) continue;
                    ItemStack input = sample.copy(); input.setCount(count);
                    result.add(new Recipe(Collections.singletonList(input), output.copy(), false));
                } catch (RuntimeException ignored) { /* Mod recipes that require a world cannot be resolved here. */ }
            }
        }
        return result;
    }
    private static void addUpstream(List<Recipe> destination, String type) {
        for (var upstream : UpstreamProcessingRecipes.all(type)) {
            List<ItemStack> options = new ArrayList<>();
            for (ItemStack input : upstream.inputs()) if (UpstreamProcessingRecipes.find(type, input) == upstream) options.add(input);
            if (options.isEmpty()) continue;
            // Replace previous single-result fallback entries for the same input.
            destination.removeIf(old -> old.inputs.size() == 1 && upstream.matches(old.inputs.get(0))
                    && UpstreamProcessingRecipes.find(type, old.inputs.get(0)) == upstream);
            Recipe recipe = new Recipe(Collections.singletonList(options.get(0).copy()), upstream.outputs().get(0).stack().copy(), false);
            recipe.alternatives = options;
            recipe.outputs.clear(); recipe.outputs.addAll(upstream.outputs());
            destination.add(recipe);
        }
    }
    private static void mergeOreVariants(List<Recipe> recipes) {
        java.util.Map<String, List<Recipe>> groups = new java.util.HashMap<>();
        List<Recipe> merged = new ArrayList<>();
        for (Recipe other : recipes) {
            StringBuilder key = new StringBuilder().append(other.inputs.size()).append(':').append(other.heated)
                    .append(':').append(other.superheated).append(':').append(other.keepHeld)
                    .append(':').append(other.application).append(':').append(other.mixing);
            for (var output : other.outputs) key.append('|').append(output.chance()).append(':')
                    .append(output.stack().writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
            key.append('|').append(fluidKey(other.fluid)).append('|').append(fluidKey(other.fluidOutput));
            for (FluidStack fluid : other.fluids) key.append('|').append(fluidKey(fluid));
            List<Recipe> candidates = groups.computeIfAbsent(key.toString(), unused -> new ArrayList<>());
            boolean combined = false;
            for (Recipe base : candidates) {
                if (!sameOutputs(base, other) || !sameFluid(base.fluid, other.fluid)
                        || !sameFluid(base.fluidOutput, other.fluidOutput) || base.fluids.size() != other.fluids.size()) continue;
                boolean fluidsMatch = true;
                for (int f = 0; f < base.fluids.size(); f++) if (!sameFluid(base.fluids.get(f), other.fluids.get(f))) fluidsMatch = false;
                if (!fluidsMatch) continue;
                List<List<ItemStack>> a = inputGroups(base), b = inputGroups(other);
                int different = -1;
                for (int slot = 0; slot < a.size(); slot++) {
                    if (sameGroup(a.get(slot), b.get(slot))) continue;
                    if (different != -1 || !sameOre(a.get(slot), b.get(slot))) { different = -2; break; }
                    different = slot;
                }
                if (different == -2) continue;
                if (different >= 0) {
                    for (ItemStack stack : b.get(different)) if (!containsStack(a.get(different), stack)) a.get(different).add(stack.copy());
                    base.itemGroups = a;
                }
                combined = true;
                break;
            }
            if (!combined) { candidates.add(other); merged.add(other); }
        }
        recipes.clear(); recipes.addAll(merged);
    }
    private static String fluidKey(FluidStack fluid) {
        return fluid == null ? "" : fluid.writeToNBT(new net.minecraft.nbt.NBTTagCompound()).toString();
    }
    private static List<List<ItemStack>> inputGroups(Recipe recipe) {
        List<List<ItemStack>> groups = new ArrayList<>();
        for (int i = 0; i < recipe.inputs.size(); i++) {
            List<ItemStack> group = !recipe.itemGroups.isEmpty() ? recipe.itemGroups.get(i)
                    : i == 0 && !recipe.alternatives.isEmpty() ? recipe.alternatives
                    : i == 1 && !recipe.heldAlternatives.isEmpty() ? recipe.heldAlternatives : Collections.singletonList(recipe.inputs.get(i));
            groups.add(new ArrayList<>(group));
        }
        return groups;
    }
    private static boolean sameOutputs(Recipe a, Recipe b) {
        if (a.outputs.size() != b.outputs.size()) return false;
        for (int i = 0; i < a.outputs.size(); i++)
            if (a.outputs.get(i).chance() != b.outputs.get(i).chance()
                    || !ItemStack.areItemStacksEqual(a.outputs.get(i).stack(), b.outputs.get(i).stack())) return false;
        return true;
    }
    private static boolean sameFluid(FluidStack a, FluidStack b) {
        return a == null ? b == null : b != null && a.amount == b.amount && a.isFluidEqual(b);
    }
    private static boolean containsStack(List<ItemStack> group, ItemStack stack) {
        for (ItemStack option : group) if (ItemStack.areItemStacksEqual(option, stack)) return true;
        return false;
    }
    private static boolean sameGroup(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (ItemStack stack : a) if (!containsStack(b, stack)) return false;
        return true;
    }
    private static boolean sameOre(List<ItemStack> a, List<ItemStack> b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        for (int ore : OreDictionary.getOreIDs(a.get(0))) {
            boolean all = true;
            for (List<ItemStack> group : Arrays.asList(a, b)) for (ItemStack stack : group) {
                boolean found = false;
                for (int id : OreDictionary.getOreIDs(stack)) if (id == ore) { found = true; break; }
                if (!found || stack.getCount() != a.get(0).getCount() || stack.hasTagCompound()) all = false;
            }
            if (all) return true;
        }
        return false;
    }
    private static final class Recipe implements IRecipeWrapper {
        private final List<ItemStack> inputs;
        private final List<UpstreamProcessingRecipes.Output> outputs = new ArrayList<>();
        private final boolean heated;
        private List<ItemStack> alternatives = Collections.emptyList();
        private List<ItemStack> heldAlternatives = Collections.emptyList();
        private List<List<ItemStack>> itemGroups = Collections.emptyList();
        private List<FluidStack> fluids = Collections.emptyList();
        private boolean keepHeld, superheated, mixing, application;
        private FluidStack fluid, fluidOutput;
        private IRecipeLayout layout;
        Recipe(List<ItemStack> inputs, ItemStack output, boolean heated) {
            this.inputs = inputs; this.heated = heated;
            if (!output.isEmpty()) outputs.add(new UpstreamProcessingRecipes.Output(output, 1F));
        }
        List<ItemStack> inputs() { return inputs; }
        @Override public void getIngredients(IIngredients ingredients) {
            List<List<ItemStack>> groups = new ArrayList<>();
            for (int i = 0; i < inputs.size(); i++) groups.add(!itemGroups.isEmpty() ? itemGroups.get(i)
                    : i == 0 && !alternatives.isEmpty() ? alternatives
                    : i == 1 && !heldAlternatives.isEmpty() ? heldAlternatives : Collections.singletonList(inputs.get(i)));
            if (!groups.isEmpty()) ingredients.setInputLists(VanillaTypes.ITEM, groups);
            List<ItemStack> allOutputs = new ArrayList<>();
            for (var output : outputs) allOutputs.add(output.stack());
            if (!allOutputs.isEmpty()) ingredients.setOutputs(VanillaTypes.ITEM, allOutputs);
            List<FluidStack> fluidInputs = new ArrayList<>(fluids);
            if (fluid != null) fluidInputs.add(fluid);
            if (!fluidInputs.isEmpty()) ingredients.setInputs(VanillaTypes.FLUID, fluidInputs);
            if (fluidOutput != null) ingredients.setOutput(VanillaTypes.FLUID, fluidOutput);
        }
        @Override public void drawInfo(Minecraft minecraft, int width, int height, int mouseX, int mouseY) {
            if (application) {
                mezz.jei.api.gui.IGuiIngredient<ItemStack> ingredient = layout == null ? null : layout.getItemStacks().getGuiIngredients().get(0);
                ItemStack shown = ingredient == null ? null : ingredient.getDisplayedIngredient();
                if (shown != null && !shown.isEmpty()) {
                    if (shown.getItem() instanceof net.minecraft.item.ItemBlock) {
                        net.minecraft.block.Block block = net.minecraft.block.Block.getBlockFromItem(shown.getItem());
                        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
                        boolean rescale = GL11.glIsEnabled(32826);
                        float oldX = OpenGlHelper.lastBrightnessX, oldY = OpenGlHelper.lastBrightnessY;
                        GlStateManager.pushMatrix();
                        try {
                            GlStateManager.enableDepth(); GlStateManager.enableRescaleNormal();
                            RenderHelper.enableGUIStandardItemLighting();
                            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
                            GlStateManager.translate(74, 51, 100); GlStateManager.scale(20F, -20F, 20F);
                            GlStateManager.rotate(15.5F, 1, 0, 0); GlStateManager.rotate(-22.5F, 0, 1, 0);
                            GlStateManager.translate(-.5D, -.5D, -.5D);
                            FastStateRendering.getInstance().renderFast(block.getStateFromMeta(
                                    shown.getMetadata() == OreDictionary.WILDCARD_VALUE ? 0 : shown.getMetadata()));
                        } finally {
                            GlStateManager.popMatrix();
                            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, oldX, oldY);
                            RenderHelper.disableStandardItemLighting();
                            if (lighting) RenderHelper.enableStandardItemLighting();
                            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
                            if (rescale) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal();
                        }
                    }
                }
            }
            if (mixing) {
                minecraft.getTextureManager().bindTexture(WIDGETS);
                net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(4, 80, 0, heated ? 201 : 221, 169, 19, 256, 256);
                minecraft.fontRenderer.drawString(I18n.format(superheated ? "jei.cogworkbridge.superheated" : heated ? "jei.cogworkbridge.heated" : "jei.cogworkbridge.no_heat"), 9, 86, superheated ? 0xFF5555 : 0xAA5500);
                if (heated) drawHeater(superheated);
            }
        }
    }

    private static void drawHeater(boolean superheated) {
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), rescale = GL11.glIsEnabled(32826);
        float oldX = OpenGlHelper.lastBrightnessX, oldY = OpenGlHelper.lastBrightnessY;
        IBlockState state = AllBlocks.BLAZE_BURNER.getDefaultState();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableDepth(); GlStateManager.disableCull(); GlStateManager.enableRescaleNormal();
            GlStateManager.color(1, 1, 1, 1); RenderHelper.enableGUIStandardItemLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
            GlStateManager.translate(86, 36, 100); GlStateManager.scale(23F, -23F, 23F);
            GlStateManager.rotate(15.5F, 1, 0, 0); GlStateManager.rotate(-22.5F, 0, 1, 0);
            GlStateManager.translate(-.5D, -3.15D, -.5D);
            FastStateRendering.getInstance().renderFast(state);
            FastStateRendering.getInstance().renderFast(BlazeBurnerModels.part(superheated
                    ? BlazeBurnerModels.Part.SUPER : BlazeBurnerModels.Part.ACTIVE), state);
            GlStateManager.pushMatrix();
            double bob = Math.sin(Minecraft.getSystemTime() / 800D) / 16D;
            GlStateManager.translate(0, bob, 0);
            FastStateRendering.getInstance().renderFast(BlazeBurnerModels.part(superheated
                    ? BlazeBurnerModels.Part.SUPER_RODS_LARGE : BlazeBurnerModels.Part.RODS_LARGE), state);
            GlStateManager.popMatrix();
            drawHeaterFlame(state, superheated);
        } finally {
            GlStateManager.popMatrix();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, oldX, oldY);
            RenderHelper.disableStandardItemLighting(); if (lighting) RenderHelper.enableStandardItemLighting();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (rescale) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal();
            if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    private static void drawHeaterFlame(IBlockState state, boolean superheated) {
        net.minecraft.client.renderer.texture.TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        net.minecraft.client.renderer.texture.TextureAtlasSprite original = atlas.getAtlasSprite(BlazeBurnerModels.FLAME_TEXTURE.toString());
        net.minecraft.client.renderer.texture.TextureAtlasSprite target = atlas.getAtlasSprite((superheated
                ? BlazeBurnerModels.SUPER_FLAME_SCROLL : BlazeBurnerModels.FLAME_SCROLL).toString());
        boolean scrolling = original != atlas.getMissingSprite() && target != atlas.getMissingSprite();
        int previousTexture = GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        int previousMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        if (scrolling) {
            double tick = Minecraft.getSystemTime() / 50D, speed = 1D / 32D + (superheated ? 4D : 3D) / 64D;
            double u = speed * tick / 2D, v = speed * tick;
            GlStateManager.matrixMode(GL11.GL_TEXTURE); GlStateManager.pushMatrix();
            GlStateManager.translate(target.getMinU() - original.getMinU() + (u - Math.floor(u)) * (target.getMaxU() - target.getMinU()) / 2D,
                    target.getMinV() - original.getMinV() + (v - Math.floor(v)) * (target.getMaxV() - target.getMinV()) / 2D, 0);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        }
        try { FastStateRendering.getInstance().renderFast(BlazeBurnerModels.part(BlazeBurnerModels.Part.FLAME), state); }
        finally {
            if (scrolling) { GlStateManager.matrixMode(GL11.GL_TEXTURE); GlStateManager.popMatrix(); GlStateManager.matrixMode(previousMode); }
            GlStateManager.setActiveTexture(previousTexture);
        }
    }
    private static final class Category implements IRecipeCategory<Recipe> {
        private final String id;
        private final IDrawable background, slot, chanceSlot, arrow, shortArrow, downArrow, shadow;
        Category(IGuiHelper helper, String id) {
            this.id = id;
            background = helper.createBlankDrawable(177, id.equals(POLISH) ? 55 : id.equals(DEPLOY) ? 120 : id.equals(CRUSH) ? 110 : id.equals(MIX) || id.equals(COMPACT) ? 100 : 80);
            slot = helper.createDrawable(WIDGETS, 0, 0, 18, 18);
            chanceSlot = helper.createDrawable(WIDGETS, 20, 156, 18, 18);
            arrow = helper.createDrawable(WIDGETS, 19, 0, 71, 10);
            shortArrow = helper.createDrawable(WIDGETS, 19, 10, 42, 10);
            downArrow = helper.createDrawable(WIDGETS, 0, 21, 18, 14);
            shadow = helper.createDrawable(WIDGETS, 0, 56, 52, 11);
        }
        @Override public String getUid() { return id; }
        @Override public String getTitle() { return I18n.format("jei." + id); }
        @Override public String getModName() { return Reference.MOD_NAME; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return null; }
        @Override public void setRecipe(IRecipeLayout layout, Recipe recipe, IIngredients ingredients) {
            recipe.layout = layout;
            for (int i = 0; i < recipe.inputs().size(); i++) {
                int x = 27, y = 51;
                if (id.equals(MIX) || id.equals(COMPACT)) { x = 17 + i % 3 * 19; y = 51 - i / 3 * 19; }
                else if (id.equals(EMPTY)) { x = 27; y = 8; }
                else if (id.equals(CRUSH)) { x = 51; y = 3; }
                else if (id.equals(MILL)) { x = 15; y = 9; }
                else if (id.equals(SAW)) { x = 44; y = 5; }
                else if ((id.equals(DEPLOY) || id.equals(CASING)) && i == 1) { x = 51; y = 5; }
                else if (id.equals(CASING)) { x = 27; y = 38; }
                else if (id.equals(POLISH)) { x = 27; y = 29; }
                else if (i > 0) x += i * 19;
                layout.getItemStacks().init(i, true, x, y);
                layout.getItemStacks().setBackground(i, slot);
                if (!recipe.itemGroups.isEmpty()) layout.getItemStacks().set(i, recipe.itemGroups.get(i));
                else if (i == 0 && !recipe.alternatives.isEmpty()) layout.getItemStacks().set(i, recipe.alternatives);
                else if (i == 1 && !recipe.heldAlternatives.isEmpty()) layout.getItemStacks().set(i, recipe.heldAlternatives);
                else layout.getItemStacks().set(i, recipe.inputs().get(i));
            }
            List<FluidStack> fluidInputs = new ArrayList<>(recipe.fluids);
            if (recipe.fluid != null) fluidInputs.add(recipe.fluid);
            for (int i = 0; i < fluidInputs.size(); i++) {
                FluidStack fluid = fluidInputs.get(i);
                int position = recipe.inputs().size() + i;
                int x = id.equals(MIX) || id.equals(COMPACT) ? 17 + position % 3 * 19 : 51 + i * 19;
                int y = id.equals(MIX) || id.equals(COMPACT) ? 51 - position / 3 * 19 : 5;
                layout.getFluidStacks().init(i, true, x, y, 16, 16, Math.max(1000, fluid.amount), false, null);
                layout.getFluidStacks().setBackground(i, slot);
                layout.getFluidStacks().set(i, fluid);
            }
            if (recipe.fluidOutput != null) {
                layout.getFluidStacks().init(100, false, id.equals(EMPTY) ? 132 : 139, id.equals(EMPTY) ? 8 : 51,
                        16, 16, Math.max(1000, recipe.fluidOutput.amount), false, null);
                layout.getFluidStacks().setBackground(100, slot);
                layout.getFluidStacks().set(100, recipe.fluidOutput);
            }
            layout.getFluidStacks().addTooltipCallback((index, input, fluid, tooltip) -> {
                if (!com.cogwork.cogworkbridge.content.fluids.PotionFluid.isPotionFluid(fluid)) return;
                List<String> effects = new ArrayList<>();
                net.minecraft.potion.PotionUtils.addPotionTooltip(
                        com.cogwork.cogworkbridge.content.fluids.PotionFluid.toBottle(fluid), effects, 1F);
                tooltip.addAll(Math.min(1, tooltip.size()), effects);
            });
            for (int i = 0; i < recipe.outputs.size(); i++) {
                int x = 132 + (i % 2) * 19, y = 51 - (i / 2) * 19;
                if (recipe.outputs.size() == 1) x = 139;
                if (id.equals(CASING)) { x = 132; y = 38; }
                else if (id.equals(POLISH)) { x = 132; y = 29; }
                else if (id.equals(CRUSH)) { x = 88 - recipe.outputs.size() * 19 / 2 + i * 19; y = 86; }
                else if (id.equals(MILL)) { x = recipe.outputs.size() == 1 ? 139 : 133 + (i % 2) * 19; y = 27 - (i / 2) * 19; }
                else if (id.equals(SAW)) { x = 118 + (i % 2) * 19; y = 48 - (i / 2) * 19; }
                else if (id.equals(EMPTY)) { x = 132; y = 27; }
                else if (id.equals(DEPLOY) && recipe.outputs.size() > 6) { x = 112 + i % 3 * 19; y = 55 + i / 3 * 19; }
                var output = recipe.outputs.get(i);
                layout.getItemStacks().init(10 + i, false, x, y);
                layout.getItemStacks().setBackground(10 + i, output.chance() < 1F ? chanceSlot : slot);
                layout.getItemStacks().set(10 + i, output.stack());
            }
            layout.getItemStacks().addTooltipCallback((index, input, stack, tooltip) -> {
                if (input && index == 1 && recipe.keepHeld) tooltip.add(I18n.format("jei.cogworkbridge.held_not_consumed"));
                int n = index - 10;
                if (!input && n >= 0 && n < recipe.outputs.size() && recipe.outputs.get(n).chance() < 1F) {
                    float chance = recipe.outputs.get(n).chance() * 100F;
                    tooltip.add(I18n.format("jei.cogworkbridge.chance", String.format(java.util.Locale.ROOT, chance == Math.round(chance) ? "%.0f" : "%.2f", chance)));
                }
            });
        }
        @Override public void drawExtras(Minecraft mc) {
            if (id.equals(POLISH)) {
                shadow.draw(mc, 61, 21);
                arrow.draw(mc, 52, 32);
                GlStateManager.pushMatrix();
                GlStateManager.translate(72F, (float) (Math.sin(Minecraft.getSystemTime() / 140D) * 2D), 0F);
                GlStateManager.scale(2F, 2F, 2F);
                mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(AllItems.SAND_PAPER), 0, 0);
                GlStateManager.popMatrix();
                return;
            }
            if (id.equals(CRUSH)) downArrow.draw(mc, 72, 7);
            else if (id.equals(MILL)) { downArrow.draw(mc, 43, 4); shortArrow.draw(mc, 85, 32); }
            else if (id.equals(SAW) || id.equals(DEPLOY) || id.equals(CASING) || id.equals(FILL) || id.equals(EMPTY)) downArrow.draw(mc, 70, 6);
            else arrow.draw(mc, 52, 54);
            if (id.equals(MILL)) shadow.draw(mc, 32, 40);
            else if (id.equals(PRESS)) shadow.draw(mc, 61, 41);
            else if (!id.equals(CRUSH)) shadow.draw(mc, id.equals(SAW) ? 55 : 61, id.equals(MIX) ? 67 : id.equals(SAW) ? 55 : 58);
            // JEI can enter this category with world lighting or a tinted colour still active.
            boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            boolean rescale = GL11.glIsEnabled(32826 /* GL_RESCALE_NORMAL */);
            float oldLightX = OpenGlHelper.lastBrightnessX, oldLightY = OpenGlHelper.lastBrightnessY;
            GlStateManager.pushMatrix();
            try {
                GlStateManager.color(1F, 1F, 1F, 1F);
                GlStateManager.enableDepth();
                GlStateManager.disableCull();
                GlStateManager.enableRescaleNormal();
                RenderHelper.enableGUIStandardItemLighting();
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
                if (!id.equals(CASING)) drawMachine(mc);
            } finally {
                GlStateManager.popMatrix();
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, oldLightX, oldLightY);
                RenderHelper.disableStandardItemLighting();
                if (lighting) RenderHelper.enableStandardItemLighting();
                if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
                if (rescale) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal();
                if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            }
        }
        private void drawMachine(Minecraft mc) {
            float tick = (float) (Minecraft.getSystemTime() / 50D), angle = tick * 4F;
            GlStateManager.translate(id.equals(CRUSH) ? 64 : id.equals(MILL) ? 57 : id.equals(SAW) ? 84 : 86,
                    id.equals(CRUSH) ? 57 : id.equals(MILL) ? 39 : 36, 100);
            float scale = id.equals(CRUSH) || id.equals(MILL) ? 22F : id.equals(DEPLOY) ? 20F : id.equals(SAW) ? 25F : 23F;
            GlStateManager.scale(scale, -scale, scale);
            GlStateManager.rotate(id.equals(CRUSH) ? 0F : 15.5F, 1, 0, 0);
            GlStateManager.rotate(id.equals(SAW) ? -112.5F : -22.5F, 0, 1, 0);
            GlStateManager.translate(-.5D, -.5D, -.5D);
            if (id.equals(CRUSH)) {
                // The wheels face the viewer and are separated across their rims, not along their axle.
                IBlockState wheel = AllBlocks.CRUSHING_WHEEL.getDefaultState().withProperty(KineticBlock.AXIS, EnumFacing.Axis.Z);
                rotatePart(wheel, -angle, EnumFacing.Axis.Z);
                GlStateManager.translate(2D, 0, 0); rotatePart(wheel, angle, EnumFacing.Axis.Z); return;
            }
            if (id.equals(MILL)) {
                draw(AllBlocks.MILLSTONE.getDefaultState());
                rotatePart(part(AllBlocks.MILLSTONE_INNER), angle * 2, EnumFacing.Axis.Y); return;
            }
            if (id.equals(PRESS) || id.equals(COMPACT)) {
                draw(AllBlocks.MECHANICAL_PRESS.getDefaultState());
                rotatePart(AllBlocks.SHAFT.getDefaultState().withProperty(KineticBlock.AXIS, EnumFacing.Axis.Z), angle, EnumFacing.Axis.Z);
                GlStateManager.pushMatrix();
                GlStateManager.translate(0, -.5D * (1D + Math.sin(tick / 12D)), 0);
                draw(part(AllBlocks.MECHANICAL_PRESS_HEAD)); GlStateManager.popMatrix();
                if (id.equals(COMPACT)) { GlStateManager.translate(0, -1.65D, 0); draw(AllBlocks.BASIN.getDefaultState()); }
                return;
            }
            if (id.equals(MIX)) {
                draw(AllBlocks.MECHANICAL_MIXER.getDefaultState());
                rotatePart(part(AllBlocks.COGWHEEL_SHAFTLESS), angle * 2, EnumFacing.Axis.Y);
                double offset = .5D + (Math.sin(tick / 32D) + 1D) / 5D;
                GlStateManager.translate(0, -offset, 0);
                draw(part(AllBlocks.MECHANICAL_MIXER_POLE)); rotatePart(part(AllBlocks.MECHANICAL_MIXER_HEAD), angle * 4, EnumFacing.Axis.Y);
                GlStateManager.translate(0, offset - 1.65D, 0); draw(AllBlocks.BASIN.getDefaultState()); return;
            }
            if (id.equals(SAW)) {
                draw(AllBlocks.MECHANICAL_SAW.getDefaultState()
                        .withProperty(com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock.FACING, EnumFacing.UP)
                        .withProperty(com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock.SHAFT_AXIS, EnumFacing.Axis.X));
                rotatePart(AllBlocks.SHAFT.getDefaultState().withProperty(KineticBlock.AXIS, EnumFacing.Axis.X), -angle, EnumFacing.Axis.X);
                GlStateManager.pushMatrix();
                GlStateManager.translate(.5D, .5D, .5D); GlStateManager.rotate(90F, 0, 1, 0); GlStateManager.translate(-.5D, -.5D, -.5D);
                draw(part(AllBlocks.MECHANICAL_SAW_BLADE_ACTIVE)); GlStateManager.popMatrix(); return;
            }
            if (id.equals(DEPLOY)) {
                draw(AllBlocks.DEPLOYER.getDefaultState().withProperty(com.cogwork.cogworkbridge.content.kinetics.deployer.DeployerBlock.FACING, EnumFacing.DOWN));
                rotatePart(AllBlocks.SHAFT.getDefaultState().withProperty(KineticBlock.AXIS, EnumFacing.Axis.Z), angle, EnumFacing.Axis.Z);
                float cycle = tick % 30F;
                float extension = cycle < 10F ? cycle / 10F : cycle < 20F ? (20F - cycle) / 10F : 0F;
                GlStateManager.pushMatrix(); GlStateManager.translate(0, -extension * .85D, 0);
                drawDownwardDeployerParts(); GlStateManager.popMatrix();
                GlStateManager.translate(0, -2D, 0); draw(AllBlocks.DEPOT.getDefaultState()); return;
            }
            if (id.equals(FILL)) { draw(AllBlocks.SPOUT.getDefaultState()); return; }
            if (id.equals(EMPTY)) { draw(AllBlocks.ITEM_DRAIN.getDefaultState()); return; }
            draw(AllBlocks.ENCASED_FAN.getDefaultState().withProperty(com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanBlock.FACING, EnumFacing.SOUTH));
            rotatePart(AllBlocks.ENCASED_FAN_PROPELLER.getDefaultState().withProperty(RenderPartBlock.FACING, EnumFacing.NORTH), angle * 16, EnumFacing.Axis.Z);
        }
        private static void drawDownwardDeployerParts() {
            GlStateManager.pushMatrix();
            GlStateManager.translate(.5D, .5D, .5D); GlStateManager.rotate(90F, 1, 0, 0); GlStateManager.translate(-.5D, -.5D, -.5D);
            draw(part(AllBlocks.DEPLOYER_POLE)); draw(part(AllBlocks.DEPLOYER_HAND_HOLDING));
            GlStateManager.popMatrix();
        }
        private static IBlockState part(RenderPartBlock block) { return block.getDefaultState().withProperty(RenderPartBlock.FACING, EnumFacing.UP); }
        private static void draw(IBlockState state) { FastStateRendering.getInstance().renderFast(state); }
        private static void rotatePart(IBlockState state, float angle, EnumFacing.Axis axis) {
            GlStateManager.pushMatrix(); GlStateManager.translate(.5D, .5D, .5D);
            GlStateManager.rotate(angle, axis == EnumFacing.Axis.X ? 1 : 0, axis == EnumFacing.Axis.Y ? 1 : 0, axis == EnumFacing.Axis.Z ? 1 : 0);
            GlStateManager.translate(-.5D, -.5D, -.5D); draw(state); GlStateManager.popMatrix();
        }
    }
}
