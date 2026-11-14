package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.processing.basin.BasinTileEntity;
import net.minecraft.block.properties.IProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelFluid;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.items.IItemHandler;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Renders contents with Forge-baked fluid quads and normal item models. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class BasinRenderer extends TileEntitySpecialRenderer<BasinTileEntity> {
    private static final Map<Fluid, IBakedModel> FLUID_MODELS = new HashMap<>();
    private static final IExtendedBlockState FLUID_STATE = fluidState();

    @SubscribeEvent
    public static void stitchFluids(TextureStitchEvent.Pre event) {
        for (Fluid fluid : FluidRegistry.getRegisteredFluids().values()) {
            registerSprite(event.getMap(), fluid.getStill());
            registerSprite(event.getMap(), fluid.getFlowing());
            registerSprite(event.getMap(), fluid.getOverlay());
        }
    }

    @SubscribeEvent
    public static void clearModels(ModelBakeEvent event) {
        FLUID_MODELS.clear();
        FluidTankRenderer.clearFluidModels();
        GlassFluidPipeRenderer.clearFluidModels();
    }

    private static void registerSprite(TextureMap atlas, ResourceLocation sprite) {
        if (sprite != null) atlas.registerSprite(sprite);
    }

    private static IExtendedBlockState fluidState() {
        List<IUnlistedProperty<?>> properties = new ArrayList<>();
        for (IUnlistedProperty<?> corner : BlockFluidBase.LEVEL_CORNERS) properties.add(corner);
        properties.add(BlockFluidBase.FLOW_DIRECTION);
        for (IUnlistedProperty<?> side : BlockFluidBase.SIDE_OVERLAYS) properties.add(side);
        IExtendedBlockState state = (IExtendedBlockState) new ExtendedBlockState(Blocks.AIR,
                new IProperty<?>[0], properties.toArray(new IUnlistedProperty<?>[0])).getBaseState();
        for (var corner : BlockFluidBase.LEVEL_CORNERS) state = state.withProperty(corner, 1.0F);
        return state;
    }

    @Override
    public void render(BasinTileEntity basin, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        int amount = 0;
        FluidStack topFluid = null;
        for (IFluidTankProperties tank : basin.getFluidHandler().getTankProperties()) {
            FluidStack fluid = tank.getContents();
            if (fluid != null && fluid.amount > 0) {
                amount += fluid.amount;
                topFluid = fluid;
            }
        }
        double surface = 0.125D + 0.75D * Math.min(1.0D, amount / 2000.0D);
        if (topFluid != null) renderFluid(topFluid, x, y + surface, z);
        double itemLevel = 0.2D + Math.max(0.125D, Math.min(0.6D, surface - 0.3D));
        renderItems(basin, x, y, z, itemLevel, amount > 0, partialTicks);
    }

    private static void renderFluid(FluidStack stack, double x, double level, double z) {
        Fluid fluid = stack.getFluid();
        if (fluid.getStill() == null || fluid.getFlowing() == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        IBakedModel model = FLUID_MODELS.computeIfAbsent(fluid, key -> new ModelFluid(key).bake(
                TRSRTransformation.identity(), DefaultVertexFormats.ITEM,
                texture -> mc.getTextureMapBlocks().getAtlasSprite(texture.toString())));
        EnumFacing face = fluid.isLighterThanAir() ? EnumFacing.DOWN : EnumFacing.UP;
        List<BakedQuad> quads = model.getQuads(FLUID_STATE, face, 0L);
        if (quads == null || quads.isEmpty()) return;
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.125D, level - (face == EnumFacing.UP ? 1.0D : 0.0D), z + 0.125D);
        GlStateManager.scale(0.75D, 1.0D, 0.75D);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);
        int color = fluid.getColor(stack);
        float red = ((color >>> 16) & 255) / 255F, green = ((color >>> 8) & 255) / 255F, blue = (color & 255) / 255F;
        for (BakedQuad quad : quads) {
            buffer.addVertexData(quad.getVertexData());
            for (int vertex = 1; vertex <= 4; vertex++) buffer.putColorMultiplier(red, green, blue, vertex);
        }
        tessellator.draw();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static void renderItems(BasinTileEntity basin, double x, double y, double z,
                                    double level, boolean hasFluid, float partialTicks) {
        List<ItemStack> stacks = new ArrayList<>();
        addStacks(stacks, basin.getInputItems());
        addStacks(stacks, basin.getOutputItems());
        if (stacks.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        Random random = new Random(basin.getPos().toLong());
        double time = basin.getWorld() == null ? 0 : basin.getWorld().getTotalWorldTime() + partialTicks;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + .5D, y + level, z + .5D);
        GlStateManager.enableRescaleNormal();
        for (int i = 0; i < stacks.size(); i++) {
            GlStateManager.pushMatrix();
            double angle = 360D * (stacks.size() - i) / stacks.size();
            if (stacks.size() > 1) {
                GlStateManager.translate(Math.cos(Math.toRadians(angle)) / 8D, 0,
                        Math.sin(Math.toRadians(angle)) / 8D);
            }
            if (hasFluid) GlStateManager.translate(0, (Math.sin(time / 12D + angle) + 1.5D) / 32D, 0);
            GlStateManager.rotate((float) angle + 35F, 0, 1, 0);
            GlStateManager.rotate(65F, 1, 0, 0);
            ItemStack stack = stacks.get(i);
            int copies = Math.min(9, 1 + stack.getCount() / 8);
            for (int copy = 0; copy < copies; copy++) {
                GlStateManager.pushMatrix();
                GlStateManager.translate((random.nextFloat() - .5D) / 8D,
                        (random.nextFloat() - .5D) / 8D, (random.nextFloat() - .5D) / 8D);
                mc.getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.GROUND);
                GlStateManager.popMatrix();
            }
            GlStateManager.popMatrix();
        }
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
    }

    private static void addStacks(List<ItemStack> result, IItemHandler items) {
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) result.add(stack);
        }
    }
}
