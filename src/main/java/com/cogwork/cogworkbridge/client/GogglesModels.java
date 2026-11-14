package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.opengl.GL11;
import javax.vecmath.Matrix4f;

/** Uses the normal item icon except when the goggles are equipped on a head. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class GogglesModels {
    private static final ResourceLocation HEAD = new ResourceLocation("create", "block/goggles");
    private static final ThreadLocal<IBakedModel> HEAD_RENDER_MODEL = new ThreadLocal<>();
    private static final HeadRenderer HEAD_RENDERER = new HeadRenderer();
    private GogglesModels() { }

    private static IModel headModel() {
        return ModelLoaderRegistry.getModelOrLogError(HEAD, "Could not load goggles head model " + HEAD);
    }

    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (ResourceLocation texture : headModel().getTextures()) event.getMap().registerSprite(texture);
    }

    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        ModelResourceLocation key = new ModelResourceLocation(AllItems.GOGGLES.getRegistryName(), "inventory");
        IBakedModel icon = event.getModelRegistry().getObject(key);
        if (icon == null) return;
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        IModel source = headModel();
        IBakedModel head = source.bake(source.getDefaultState(), DefaultVertexFormats.ITEM,
                sprite -> atlas.getAtlasSprite(sprite.toString()));
        event.getModelRegistry().putObject(key, new Model(icon, head));
        AllItems.GOGGLES.setTileEntityItemStackRenderer(HEAD_RENDERER);
    }

    private static final class Model extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel head;
        private final IBakedModel renderedHead;
        private Model(IBakedModel icon, IBakedModel head) {
            super(icon);
            this.head = head;
            this.renderedHead = new HeadModel(head);
        }
        @Override public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
            if (type != ItemCameraTransforms.TransformType.HEAD) return originalModel.handlePerspective(type);
            Pair<? extends IBakedModel, Matrix4f> pose = head.handlePerspective(type);
            HEAD_RENDER_MODEL.set(pose.getLeft());
            return Pair.of(renderedHead, pose.getRight());
        }
    }

    private static final class HeadModel extends BakedModelWrapper<IBakedModel> {
        private HeadModel(IBakedModel model) { super(model); }
        @Override public boolean isBuiltInRenderer() { return true; }
    }

    private static final class HeadRenderer extends TileEntityItemStackRenderer {
        @Override public void renderByItem(ItemStack stack) {
            IBakedModel model = HEAD_RENDER_MODEL.get();
            if (model == null) return;
            // Entity layers disable culling. The upstream lens has coincident front and
            // back faces; drawing both darkens its translucent texture twice.
            boolean wasCulled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            GlStateManager.pushMatrix();
            try {
                GlStateManager.enableCull();
                GlStateManager.cullFace(GlStateManager.CullFace.BACK);
                // RenderItem has already translated by -0.5 before invoking us.
                GlStateManager.translate(.5F, .5F, .5F);
                Minecraft.getMinecraft().getRenderItem().renderItem(stack, model);
            } finally {
                if (!wasCulled) GlStateManager.disableCull();
                GlStateManager.popMatrix();
                HEAD_RENDER_MODEL.remove();
            }
        }
    }
}
