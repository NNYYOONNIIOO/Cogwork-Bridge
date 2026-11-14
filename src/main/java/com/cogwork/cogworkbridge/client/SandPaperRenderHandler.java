package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.equipment.SandPaperItem;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.Collections;

/** Uses the vanilla hand animation and Create's item-space polishing transforms. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class SandPaperRenderHandler {
    private static final ThreadLocal<EntityLivingBase> ACTOR = new ThreadLocal<>();
    private static final ThreadLocal<RenderContext> CONTEXT = new ThreadLocal<>();
    private static final PaperRenderer RENDERER = new PaperRenderer();

    private SandPaperRenderHandler() { }

    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        for (Item item : new Item[]{AllItems.SAND_PAPER, AllItems.RED_SAND_PAPER}) {
            ModelResourceLocation key = new ModelResourceLocation(item.getRegistryName(), "inventory");
            IBakedModel base = event.getModelRegistry().getObject(key);
            if (base == null) continue;
            event.getModelRegistry().putObject(key, new PaperModel(base));
            item.setTileEntityItemStackRenderer(RENDERER);
        }
    }

    @SubscribeEvent public static void renderHand(RenderSpecificHandEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        AbstractClientPlayer player = mc.player;
        if (player == null || !(event.getItemStack().getItem() instanceof SandPaperItem)
                || !player.isHandActive() || player.getActiveHand() != event.getHand()) return;

        // 1.21.1 uses the ordinary use-item animation, but substitutes its own
        // particles and sound. Restrict EAT to this render call in 1.12.2.
        event.setCanceled(true);
        SandPaperItem.setFirstPersonUseRender(true);
        try {
            mc.getItemRenderer().renderItemInFirstPerson(player, event.getPartialTicks(),
                    event.getInterpolatedPitch(), event.getHand(), event.getSwingProgress(),
                    event.getItemStack(), event.getEquipProgress());
        } finally {
            SandPaperItem.setFirstPersonUseRender(false);
        }
    }

    private static final class RenderContext {
        private final ItemCameraTransforms.TransformType type;
        private final IBakedModel model;
        private final EntityLivingBase actor;
        private RenderContext(ItemCameraTransforms.TransformType type, IBakedModel model, EntityLivingBase actor) {
            this.type = type;
            this.model = model;
            this.actor = actor;
        }
    }

    private static final class PaperModel extends BakedModelWrapper<IBakedModel> {
        private final ItemOverrideList overrides = new ItemOverrideList(Collections.emptyList()) {
            @Override public IBakedModel handleItemState(IBakedModel ignored, ItemStack stack,
                                                           @Nullable World world, @Nullable EntityLivingBase entity) {
                ACTOR.set(entity);
                IBakedModel resolved = originalModel.getOverrides().handleItemState(originalModel, stack, world, entity);
                return resolved == originalModel || resolved == null ? PaperModel.this : new PaperModel(resolved);
            }
        };

        private PaperModel(IBakedModel original) { super(original); }
        @Override public boolean isBuiltInRenderer() { return true; }
        @Override public ItemOverrideList getOverrides() { return overrides; }

        @Override public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
            Pair<? extends IBakedModel, Matrix4f> pose = originalModel.handlePerspective(type);
            CONTEXT.set(new RenderContext(type, pose.getLeft(), ACTOR.get()));
            return Pair.of(this, pose.getRight());
        }
    }

    private static final class PaperRenderer extends TileEntityItemStackRenderer {
        @Override public void renderByItem(ItemStack paper) {
            RenderContext context = CONTEXT.get();
            if (context == null || context.model == null) return;
            Minecraft mc = Minecraft.getMinecraft();
            EntityLivingBase actor = context.actor != null ? context.actor : mc.player;
            ItemCameraTransforms.TransformType type = context.type;
            boolean leftHand = type == ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND;
            boolean firstPerson = leftHand || type == ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND;
            int sign = leftHand ? -1 : 1;

            // RenderItem already moved the model origin by -0.5. The upstream
            // custom renderer moves back to the item centre before both layers.
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(.5F, .5F, .5F);
                ItemStack input = actor != null && actor.isHandActive()
                        && ItemStack.areItemStacksEqual(actor.getActiveItemStack(), paper)
                        ? SandPaperItem.polishingInput(paper) : ItemStack.EMPTY;
                if (!input.isEmpty()) {
                    GlStateManager.pushMatrix();
                    try {
                        if (type == ItemCameraTransforms.TransformType.GUI) {
                            GlStateManager.translate(0F, .2F, 1F);
                            GlStateManager.scale(.75F, .75F, .75F);
                        } else {
                            GlStateManager.rotate(sign * 40F, 0F, 1F, 0F);
                        }
                        if (actor != null) {
                            float time = actor.getItemInUseCount() - mc.getRenderPartialTicks() + 1F;
                            if (time / paper.getMaxItemUseDuration() < .8F) {
                                float bobbing = -MathHelper.abs(MathHelper.cos(time / 4F * (float) Math.PI) * .1F);
                                if (type == ItemCameraTransforms.TransformType.GUI)
                                    GlStateManager.translate(bobbing, bobbing, 0F);
                                else GlStateManager.translate(0F, bobbing, 0F);
                            }
                        }
                        mc.getRenderItem().renderItem(input, ItemCameraTransforms.TransformType.GUI);
                    } finally {
                        GlStateManager.popMatrix();
                    }
                }
                if (firstPerson && !input.isEmpty() && actor != null && actor.getItemInUseCount() > 0) {
                    GlStateManager.translate(sign * .5F, 0F, -.25F);
                    GlStateManager.rotate(sign * 40F, 0F, 0F, 1F);
                    GlStateManager.rotate(sign * 10F, 1F, 0F, 0F);
                    GlStateManager.rotate(sign * 90F, 0F, 1F, 0F);
                }
                GlStateManager.enableRescaleNormal();
                GlStateManager.enableBlend();
                mc.getRenderItem().renderItem(paper, context.model);
            } finally {
                GlStateManager.popMatrix();
                CONTEXT.remove();
                ACTOR.remove();
            }
        }
    }
}
