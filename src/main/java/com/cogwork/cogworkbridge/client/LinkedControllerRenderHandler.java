package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.commons.lang3.tuple.Pair;
import javax.vecmath.Matrix4f;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class LinkedControllerRenderHandler {
    private static IBakedModel powered, button;
    private static final ThreadLocal<RenderContext> CONTEXT = new ThreadLocal<>();
    private static final float[] buttons = new float[6], previousButtons = new float[6];
    private static final float[][] OFFSETS = {{2, 8}, {6, 8}, {4, 10}, {4, 6}, {3, 3}, {5, 3}};
    private LinkedControllerRenderHandler() { }
    private static IModel model(String name) {
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", "item/linked_controller/" + name),
                "Could not load linked controller " + name);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String part : new String[]{"powered", "button"})
            for (ResourceLocation texture : model(part).getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        IModel poweredModel = model("powered"), buttonModel = model("button");
        powered = poweredModel.bake(poweredModel.getDefaultState(), DefaultVertexFormats.ITEM, sprite -> atlas.getAtlasSprite(sprite.toString()));
        button = buttonModel.bake(buttonModel.getDefaultState(), DefaultVertexFormats.ITEM, sprite -> atlas.getAtlasSprite(sprite.toString()));
        var location = new net.minecraft.client.renderer.block.model.ModelResourceLocation(Reference.MOD_ID + ":linked_controller", "inventory");
        IBakedModel original = event.getModelRegistry().getObject(location);
        if (original != null && !(original instanceof ControllerModel))
            event.getModelRegistry().putObject(location, new ControllerModel(original));
        AllItems.LINKED_CONTROLLER.setTileEntityItemStackRenderer(new ControllerRenderer());
        CONTEXT.remove();
        java.util.Arrays.fill(buttons, 0); java.util.Arrays.fill(previousButtons, 0);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || mc.isGamePaused()) return;
        boolean active = mc.player != null && LinkedControllerClientEvents.controlling();
        for (int i = 0; i < buttons.length; i++) {
            previousButtons[i] = buttons[i];
            if (!active) { buttons[i] = previousButtons[i] = 0; continue; }
            float target = (LinkedControllerClientEvents.pressedMask() & 1 << i) != 0 ? 1F : 0F;
            buttons[i] += (target - buttons[i]) * .4F;
        }
    }
    private record RenderContext(ItemCameraTransforms.TransformType type, IBakedModel original) { }
    private static final class ControllerModel extends BakedModelWrapper<IBakedModel> {
        ControllerModel(IBakedModel original) { super(original); }
        @Override public boolean isBuiltInRenderer() { return true; }
        @Override public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
            Pair<? extends IBakedModel, Matrix4f> pose = originalModel.handlePerspective(type);
            CONTEXT.set(new RenderContext(type, pose.getLeft()));
            return Pair.of(this, pose.getRight());
        }
    }
    private static final class ControllerRenderer extends TileEntityItemStackRenderer {
        @Override public void renderByItem(ItemStack stack) {
            RenderContext context = CONTEXT.get();
            if (context == null || context.original() == null) return;
            Minecraft mc = Minecraft.getMinecraft();
            var player = mc.player;
            var type = context.type();
            boolean active = false;
            float partial = mc.getRenderPartialTicks();
            GlStateManager.pushMatrix();
            try {
                // Vanilla already applied the original item's perspective and moved its origin by -0.5.
                // Return to its centre before posing both the body and every button identically.
                GlStateManager.translate(.5F, .5F, .5F);
                if (player != null) {
                    boolean rightHanded = player.getPrimaryHand() == EnumHandSide.RIGHT;
                    var main = rightHanded ? ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND : ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND;
                    var off = rightHanded ? ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND : ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND;
                    boolean noControllerInMain = player.getHeldItemMainhand().getItem() != AllItems.LINKED_CONTROLLER;
                    if (type == main || type == off && noControllerInMain) {
                        float equip = LinkedControllerClientEvents.equip(partial);
                        float sign = type == ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND ? -1F : 1F;
                        GlStateManager.translate(0, equip / 4F, equip / 4F * sign);
                        GlStateManager.rotate(-30F * equip * sign, 0, 1, 0);
                        GlStateManager.rotate(-30F * equip, 0, 0, 1);
                        active = true;
                    } else if (type == ItemCameraTransforms.TransformType.GUI)
                        active = stack == player.getHeldItemMainhand() || noControllerInMain && stack == player.getHeldItemOffhand();
                    active &= LinkedControllerClientEvents.controlling() && !LinkedControllerClientEvents.inLectern();
                }
                active &= powered != null && button != null;
                mc.getRenderItem().renderItem(stack, active ? powered : context.original());
                if (!active) return;
                for (int i = 0; i < OFFSETS.length; i++) {
                    GlStateManager.pushMatrix();
                    try {
                        float depression = previousButtons[i] + (buttons[i] - previousButtons[i]) * partial;
                        GlStateManager.translate(OFFSETS[i][0] / 16F, -.75F / 16F * depression, OFFSETS[i][1] / 16F);
                        mc.getRenderItem().renderItem(stack, button);
                    } finally { GlStateManager.popMatrix(); }
                }
            } finally {
                GlStateManager.popMatrix();
                CONTEXT.remove();
            }
        }
    }
}
