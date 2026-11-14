package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.fluids.FluidPipeTileEntity;
import com.cogwork.cogworkbridge.content.fluids.GlassFluidPipeBlock;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.ModelFluid;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GlassFluidPipeRenderer extends TileEntitySpecialRenderer<FluidPipeTileEntity> {
    private static final Map<Fluid, IBakedModel> MODELS = new HashMap<>();
    private static final IExtendedBlockState FLUID_STATE = fluidState();

    static void clearFluidModels() { MODELS.clear(); }

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
    public void render(FluidPipeTileEntity tile, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        if (tile.getWorld() == null) return;
        if (tile.bracket().present()) {
            GlStateManager.pushMatrix(); GlStateManager.translate(x, y, z);
            FastStateRendering.getInstance().renderFast(tile.bracket().model());
            GlStateManager.popMatrix();
        }
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof GlassFluidPipeBlock)) return;
        EnumFacing.Axis axis = state.getValue(GlassFluidPipeBlock.AXIS);
        for (EnumFacing side : EnumFacing.VALUES) {
            if (side.getAxis() != axis) continue;
            FluidPipeTileEntity.FlowView flow = tile.getFlow(side, partialTicks);
            if (flow == null || flow.progress() <= 0.0F) continue;
            float progress = Math.min(1.0F, flow.progress());
            float length = progress * 0.5F;
            boolean positive = side.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE;
            float start = flow.inbound() ? positive ? 1.0F - length : 0.0F
                    : positive ? 0.5F : 0.5F - length;
            renderFlow(flow.fluid(), axis, x, y, z, start, length);
        }
    }

    private static void renderFlow(FluidStack stack, EnumFacing.Axis axis, double x, double y, double z,
                                   float start, float length) {
        if (stack == null || stack.getFluid().getStill() == null || stack.getFluid().getFlowing() == null) return;
        Fluid fluid = stack.getFluid();
        Minecraft mc = Minecraft.getMinecraft();
        IBakedModel model = MODELS.computeIfAbsent(fluid, key -> new ModelFluid(key).bake(
                TRSRTransformation.identity(), DefaultVertexFormats.ITEM,
                texture -> mc.getTextureMapBlocks().getAtlasSprite(texture.toString())));
        int color = fluid.getColor(stack);
        float red = ((color >>> 16) & 255) / 255.0F;
        float green = ((color >>> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + (axis == EnumFacing.Axis.X ? start : 0.375D),
                y + (axis == EnumFacing.Axis.Y ? start : 0.375D),
                z + (axis == EnumFacing.Axis.Z ? start : 0.375D));
        GlStateManager.scale(axis == EnumFacing.Axis.X ? length : 0.25D,
                axis == EnumFacing.Axis.Y ? length : 0.25D,
                axis == EnumFacing.Axis.Z ? length : 0.25D);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);
        for (EnumFacing side : EnumFacing.values()) {
            for (BakedQuad quad : model.getQuads(FLUID_STATE, side, 0L)) {
                buffer.addVertexData(quad.getVertexData());
                for (int vertex = 1; vertex <= 4; vertex++) buffer.putColorMultiplier(red, green, blue, vertex);
            }
        }
        tessellator.draw();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }
}
