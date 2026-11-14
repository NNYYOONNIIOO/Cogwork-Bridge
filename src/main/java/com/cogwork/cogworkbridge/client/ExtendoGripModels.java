package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector4f;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Combines Create's separate hand, cog and linkage models with the item grip. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ExtendoGripModels {
    private static final String[] PARTS = {"cog", "thin_short", "wide_short", "thin_long", "wide_long",
            "hand", "hand_holding", "hand_punching"};
    private ExtendoGripModels() { }

    private static IModel part(String name) {
        ResourceLocation path = new ResourceLocation("create", name.equals("hand")
                ? "block/deployer/hand_pointing" : name.startsWith("hand_")
                ? "block/deployer/" + name : "item/extendo_grip/" + name);
        return ModelLoaderRegistry.getModelOrLogError(path, "Could not load extendo grip part " + path);
    }

    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String name : PARTS)
            for (ResourceLocation texture : part(name).getTextures()) event.getMap().registerSprite(texture);
    }

    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        ModelResourceLocation key = new ModelResourceLocation(AllItems.EXTENDO_GRIP.getRegistryName(), "inventory");
        IBakedModel base = event.getModelRegistry().getObject(key);
        if (base == null) return;
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        Map<String, IBakedModel> parts = new HashMap<>();
        for (String name : PARTS) parts.put(name, part(name).bake(TRSRTransformation.identity(),
                DefaultVertexFormats.ITEM, texture -> atlas.getAtlasSprite(texture.toString())));

        List<BakedQuad> resting = buildParts(parts, .25F * .25F * .25F, "hand", 0F);
        event.getModelRegistry().putObject(key, new Model(base, parts, resting, false));
    }

    private static List<BakedQuad> buildParts(Map<String, IBakedModel> parts, float extension,
                                               String hand, float cogAngle) {
        float half = (24F + 132F * extension) / 2F;
        float opposite = 180F - 2F * half;
        List<BakedQuad> added = new ArrayList<>();
        Matrix4f basePose = identity();
        translate(basePose, 0, 1F / 16F, -7F / 16F);
        scale(basePose, 1, 1, 1 + extension);

        Matrix4f first = new Matrix4f(basePose);
        rotateX(first, -half); addPart(added, parts.get("thin_short"), first, 0, .5F, .5F);
        translate(first, 0, 5.5F / 16F, 0); rotateX(first, -opposite);
        addPart(added, parts.get("wide_long"), first, 0, .5F, .5F);
        translate(first, 0, 11F / 16F, 0); rotateX(first, opposite);
        Matrix4f lastFirst = new Matrix4f(first); translate(lastFirst, 0, .5F / 16F, 0);
        addPart(added, parts.get("thin_short"), lastFirst, 0, .5F, .5F);

        Matrix4f second = new Matrix4f(basePose);
        rotateX(second, -180F + half); addPart(added, parts.get("wide_short"), second, 0, .5F, .5F);
        translate(second, 0, 5.5F / 16F, 0); rotateX(second, opposite);
        addPart(added, parts.get("thin_long"), second, 0, .5F, .5F);
        translate(second, 0, 11F / 16F, 0); rotateX(second, -opposite);
        translate(second, 0, .5F / 16F, 0); addPart(added, parts.get("wide_short"), second, 0, .5F, .5F);
        translate(second, 0, 5.5F / 16F, 0); rotateX(second, 180F - half); rotateY(second, 180);
        translate(second, 0, 0, -4F / 16F); scale(second, 1, 1, 1 / (1 + extension));
        addPart(added, parts.get(hand), second, 0, 0, 0);
        Matrix4f cog = identity();
        translate(cog, 0, 1F / 16F, 0); rotateZ(cog, cogAngle); translate(cog, 0, -1F / 16F, 0);
        addPart(added, parts.get("cog"), cog, 0, 0, 0);
        return Collections.unmodifiableList(added);
    }

    private static void addPart(List<BakedQuad> result, IBakedModel model, Matrix4f pose, float ox, float oy, float oz) {
        Matrix4f transform = identity();
        translate(transform, .5F, .5F, .5F);
        transform.mul(pose);
        translate(transform, ox - .5F, oy - .5F, oz - .5F);
        for (int side = -1; side < 6; side++) {
            for (BakedQuad quad : model.getQuads(null, side < 0 ? null : EnumFacing.byIndex(side), 0)) {
                int[] data = quad.getVertexData().clone();
                int stride = data.length / 4;
                for (int v = 0; v < 4; v++) {
                    int i = v * stride;
                    Vector4f p = new Vector4f(Float.intBitsToFloat(data[i]), Float.intBitsToFloat(data[i + 1]),
                            Float.intBitsToFloat(data[i + 2]), 1);
                    transform.transform(p);
                    data[i] = Float.floatToRawIntBits(p.x); data[i + 1] = Float.floatToRawIntBits(p.y);
                    data[i + 2] = Float.floatToRawIntBits(p.z);
                    if (stride > 6) {
                        int packed = data[i + 6];
                        Vector4f normal = new Vector4f((byte) packed, (byte) (packed >> 8), (byte) (packed >> 16), 0);
                        transform.transform(normal);
                        float length = (float) Math.sqrt(normal.x * normal.x + normal.y * normal.y + normal.z * normal.z);
                        if (length > 0) data[i + 6] = (packed & 0xff000000)
                                | ((Math.round(normal.x / length * 127) & 255))
                                | ((Math.round(normal.y / length * 127) & 255) << 8)
                                | ((Math.round(normal.z / length * 127) & 255) << 16);
                    }
                }
                result.add(new BakedQuad(data, quad.getTintIndex(), quad.getFace(), quad.getSprite(),
                        quad.shouldApplyDiffuseLighting(), quad.getFormat()));
            }
        }
    }
    private static Matrix4f identity() { Matrix4f matrix = new Matrix4f(); matrix.setIdentity(); return matrix; }
    private static void translate(Matrix4f matrix, float x, float y, float z) {
        Matrix4f next = identity(); next.m03 = x; next.m13 = y; next.m23 = z; matrix.mul(next);
    }
    private static void scale(Matrix4f matrix, float x, float y, float z) {
        Matrix4f next = identity(); next.m00 = x; next.m11 = y; next.m22 = z; matrix.mul(next);
    }
    private static void rotateX(Matrix4f matrix, float degrees) {
        Matrix4f next = identity(); next.rotX((float) Math.toRadians(degrees)); matrix.mul(next);
    }
    private static void rotateY(Matrix4f matrix, float degrees) {
        Matrix4f next = identity(); next.rotY((float) Math.toRadians(degrees)); matrix.mul(next);
    }
    private static void rotateZ(Matrix4f matrix, float degrees) {
        Matrix4f next = identity(); next.rotZ((float) Math.toRadians(degrees)); matrix.mul(next);
    }
    private static final class Model extends BakedModelWrapper<IBakedModel> {
        private final Map<String, IBakedModel> models;
        private final List<BakedQuad> resting;
        private final boolean firstPerson;
        private Model(IBakedModel base, Map<String, IBakedModel> models, List<BakedQuad> resting, boolean firstPerson) {
            super(base); this.models = models; this.resting = resting; this.firstPerson = firstPerson;
        }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            List<BakedQuad> original = originalModel.getQuads(state, side, random);
            if (side != null) return original;
            float extension = ExtendoGripRenderHandler.extension();
            Minecraft mc = Minecraft.getMinecraft();
            float time = mc.player == null ? 0F : mc.player.ticksExisted + mc.getRenderPartialTicks();
            List<BakedQuad> parts = firstPerson
                    ? buildParts(models, extension, ExtendoGripRenderHandler.holdingBlock() ? "hand_holding" : "hand_punching",
                            -2F * time + 360F * extension) : resting;
            List<BakedQuad> all = new ArrayList<>(original.size() + parts.size());
            all.addAll(original); all.addAll(parts); return all;
        }
        @Override public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
            boolean held = type == ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND
                    || type == ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND
                    || ExtendoGripRenderHandler.renderingThirdPerson()
                    && (type == ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND
                    || type == ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND);
            return Pair.of(new Model(originalModel, models, resting, held), originalModel.handlePerspective(type).getRight());
        }
    }
}
