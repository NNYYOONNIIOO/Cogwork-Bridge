package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity;
import com.cogwork.cogworkbridge.content.fluids.ItemDrainTileEntity;
import com.cogwork.cogworkbridge.content.fluids.FluidSpoutTileEntity;
import net.minecraft.block.properties.IProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
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
import net.minecraftforge.client.model.ModelFluid;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
public final class FluidTankRenderer extends TileEntitySpecialRenderer<FluidTankTileEntity> {
    private static final Map<Fluid, IBakedModel> MODELS = new HashMap<>();
    private static final IExtendedBlockState FLUID_STATE = createFluidState();

    static void clearFluidModels() { MODELS.clear(); }

    private static IExtendedBlockState createFluidState() {
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
    public void render(FluidTankTileEntity tank, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        if (tank instanceof ItemDrainTileEntity drain) renderItem(drain, x, y, z, partialTicks);
        if (!tank.isController()) return;
        if (!tank.hasWindows()) {
            if (tank.isBoiler()) renderBoiler(tank, x, y, z);
            return;
        }
        FluidStack fluidStack = tank.getFluid();
        if (fluidStack == null || fluidStack.amount <= 0) return;
        Fluid fluid = fluidStack.getFluid();
        double fill = Math.min(1.0D, fluidStack.amount / (double) Math.max(1, tank.getCapacity()));
        boolean drain = tank instanceof ItemDrainTileEntity;
        boolean spout = tank instanceof FluidSpoutTileEntity;
        double inset = drain ? 0.125D : spout ? 2.5D / 16.0D : 1.0D / 16.0D + 1.0D / 128.0D;
        double usableHeight = drain ? 0.75D : spout ? 11.0D / 16.0D : Math.max(0.0D, tank.getHeight() - 0.5D);
        double fluidHeight = usableHeight * fill;
        double base = drain ? 0.0D : spout ? 2.5D / 16.0D : 0.25D;
        if (fluid.isGaseous()) base += usableHeight - fluidHeight;
        renderFluidBox(fluidStack, x + inset, y + base, z + inset,
                (drain || spout ? 1 : tank.getWidth()) - 2 * inset, fluidHeight,
                (drain || spout ? 1 : tank.getWidth()) - 2 * inset);
    }

    private static void renderBoiler(FluidTankTileEntity tank, double x, double y, double z) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + tank.getWidth() / 2D, y + .5D, z + tank.getWidth() / 2D);
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
            GlStateManager.pushMatrix();
            GlStateManager.rotate(-side.getHorizontalAngle() - 90F, 0, 1, 0);
            GlStateManager.translate(-.5D, -.5D, -.5D);
            GlStateManager.translate(tank.getWidth() / 2D - 6D / 16D, 0, 0);
            FastStateRendering.getInstance().renderFast(com.cogwork.cogworkbridge.registry.AllBlocks.BOILER_GAUGE.getDefaultState());
            GlStateManager.translate(0, 6D / 16D, 8D / 16D);
            GlStateManager.rotate(-145F * tank.getBoilerGauge() + 90F, 1, 0, 0);
            GlStateManager.translate(0, -6D / 16D, -8D / 16D);
            FastStateRendering.getInstance().renderFast(com.cogwork.cogworkbridge.registry.AllBlocks.BOILER_GAUGE_DIAL.getDefaultState());
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
    }

    static void renderFluidBox(FluidStack fluidStack, double x, double y, double z,
                               double width, double height, double depth) {
        if (fluidStack == null || fluidStack.amount <= 0 || width <= 0 || height <= 0 || depth <= 0) return;
        Fluid fluid = fluidStack.getFluid();
        if (fluid.getStill() == null || fluid.getFlowing() == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        IBakedModel model = MODELS.computeIfAbsent(fluid, key -> new ModelFluid(key).bake(
                TRSRTransformation.identity(), DefaultVertexFormats.ITEM,
                texture -> mc.getTextureMapBlocks().getAtlasSprite(texture.toString())));
        int color = fluid.getColor(fluidStack);
        float red = ((color >>> 16) & 255) / 255.0F;
        float green = ((color >>> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.scale(width, height, depth);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);
        for (EnumFacing face : EnumFacing.values()) {
            List<BakedQuad> quads = model.getQuads(FLUID_STATE, face, 0L);
            if (quads == null) continue;
            for (BakedQuad quad : quads) {
                buffer.addVertexData(quad.getVertexData());
                for (int vertex = 1; vertex <= 4; vertex++) buffer.putColorMultiplier(red, green, blue, vertex);
            }
        }
        tessellator.draw();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static void renderItem(ItemDrainTileEntity drain, double x, double y, double z, float partialTicks) {
        ItemStack stack = drain.getHeldItemStack();
        if (stack.isEmpty()) return;
        EnumFacing direction = drain.getTravelDirection();
        float offset = drain.getItemPosition(partialTicks) - 0.5F;
        Minecraft mc = Minecraft.getMinecraft();
        boolean blockItem = mc.getRenderItem().getItemModelWithOverrides(stack, drain.getWorld(), null).isGui3d();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D + direction.getXOffset() * offset,
                y + 15.0D / 16.0D, z + 0.5D + direction.getZOffset() * offset);
        GlStateManager.rotate((drain.getWorld() == null ? 0.0F : drain.getWorld().getTotalWorldTime() * 6.0F)
                + offset * 180.0F, direction.getXOffset(), direction.getYOffset(), direction.getZOffset());
        if (!blockItem) GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(blockItem ? 0.46F : 0.5F, blockItem ? 0.46F : 0.5F,
                blockItem ? 0.46F : 0.5F);
        RenderHelper.enableStandardItemLighting();
        mc.getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.FIXED);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
}
