package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.fluids.PortableFluidInterfaceBlock;
import com.cogwork.cogworkbridge.content.fluids.PortableFluidInterfaceTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class PortableFluidInterfaceRenderer extends TileEntitySpecialRenderer<PortableFluidInterfaceTileEntity> {
    private static final String[] PARTS = {"block_middle", "block_middle_powered", "block_top"};
    private static final IBakedModel[][] MODELS = new IBakedModel[PARTS.length][6];

    private static IModel model(String part) {
        ResourceLocation id = new ResourceLocation("create", "block/portable_fluid_interface/" + part);
        return ModelLoaderRegistry.getModelOrLogError(id, "Could not load portable fluid interface part " + id);
    }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        for (String part : PARTS) {
            for (ResourceLocation texture : model(part).getTextures()) event.getMap().registerSprite(texture);
        }
    }

    @SubscribeEvent
    public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (int i = 0; i < PARTS.length; i++) {
            IModel part = model(PARTS[i]);
            for (EnumFacing facing : EnumFacing.VALUES) {
                MODELS[i][facing.getIndex()] = part.bake(rotation(facing), DefaultVertexFormats.BLOCK,
                        texture -> atlas.getAtlasSprite(texture.toString()));
            }
        }
    }

    private static ModelRotation rotation(EnumFacing facing) {
        return switch (facing) {
            case DOWN -> ModelRotation.X180_Y0;
            case UP -> ModelRotation.X0_Y0;
            case NORTH -> ModelRotation.X90_Y0;
            case SOUTH -> ModelRotation.X90_Y180;
            case WEST -> ModelRotation.X90_Y270;
            case EAST -> ModelRotation.X90_Y90;
        };
    }

    @Override
    public void render(PortableFluidInterfaceTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof PortableFluidInterfaceBlock)) return;
        EnumFacing facing = state.getValue(PortableFluidInterfaceBlock.FACING);
        int index = facing.getIndex();
        IBakedModel middle = MODELS[tile.isConnected() ? 1 : 0][index];
        IBakedModel top = MODELS[2][index];
        if (middle == null || top == null) return;
        float progress = tile.getExtensionDistance(partialTicks);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.color(1, 1, 1, 1);
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(7, DefaultVertexFormats.ITEM);
        append(buffer, middle, state, facing, progress * 0.5F + 0.375F);
        append(buffer, top, state, facing, progress);
        Tessellator.getInstance().draw();
        GlStateManager.popMatrix();
    }

    private static void append(BufferBuilder buffer, IBakedModel model, IBlockState state,
                               EnumFacing direction, float offset) {
        for (EnumFacing side : EnumFacing.VALUES) {
            append(buffer, model.getQuads(state, side, 0L), direction, offset);
        }
        append(buffer, model.getQuads(state, null, 0L), direction, offset);
    }

    private static void append(BufferBuilder buffer, Iterable<BakedQuad> quads, EnumFacing direction, float offset) {
        for (BakedQuad quad : quads) {
            buffer.addVertexData(quad.getVertexData());
            buffer.putPosition(direction.getXOffset() * offset,
                    direction.getYOffset() * offset, direction.getZOffset() * offset);
            buffer.putColorRGB_F4(1.0F, 1.0F, 1.0F);
            Vec3i normal = quad.getFace() == null ? new Vec3i(0, 1, 0) : quad.getFace().getDirectionVec();
            buffer.putNormal(normal.getX(), normal.getY(), normal.getZ());
        }
    }
}
