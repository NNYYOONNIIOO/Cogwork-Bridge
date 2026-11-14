package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** First-person linkage animation and the item carried at the end of the grip. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ExtendoGripRenderHandler {
    private static float animation, previousAnimation, renderExtension;
    private static boolean holdingBlock;
    private static boolean thirdPerson;
    private static final Set<RenderPlayer> CHECKED_RENDERERS = Collections.newSetFromMap(new IdentityHashMap<>());

    private ExtendoGripRenderHandler() { }

    public static float extension() { return renderExtension; }
    public static boolean holdingBlock() { return holdingBlock; }
    public static boolean renderingThirdPerson() { return thirdPerson; }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        previousAnimation = animation;
        animation *= MathHelper.clamp(animation, .8F, .99F);
        if (Minecraft.getMinecraft().player == null) animation = previousAnimation = 0F;
    }

    @SubscribeEvent public static void renderHand(RenderSpecificHandEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        AbstractClientPlayer player = mc.player;
        if (player == null) return;
        ItemStack main = player.getHeldItemMainhand(), off = player.getHeldItemOffhand();
        boolean mainGrip = main.getItem() == AllItems.EXTENDO_GRIP;
        boolean offGrip = off.getItem() == AllItems.EXTENDO_GRIP;
        if (!mainGrip && !offGrip) return;

        // Draw one complete assembly in the main-hand pass, including the
        // opposite hand's item, and suppress the second vanilla hand pass.
        event.setCanceled(true);
        if (event.getHand() != EnumHand.MAIN_HAND) return;
        ItemStack grip = offGrip ? off : main;
        ItemStack carried = offGrip ? main : off;
        float swing = player.getSwingProgress(event.getPartialTicks());
        if (swing > 0F && 1F - swing > animation) animation = .95F;
        float value = previousAnimation + (animation - previousAnimation) * event.getPartialTicks();
        renderExtension = value * value * value;
        holdingBlock = !carried.isEmpty() && carried.getItem() instanceof ItemBlock
                && mc.getRenderItem().getItemModelWithOverrides(carried, player.world, player).isGui3d();

        boolean right = player.getPrimaryHand() == EnumHandSide.RIGHT;
        float flip = right ? 1F : -1F;
        ItemCameraTransforms.TransformType transform = right
                ? ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND
                : ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND;
        GlStateManager.pushMatrix();
        GlStateManager.translate(flip * .54000005F, -.4F + event.getEquipProgress() * -.15F, -.41999996F);

        if (!player.isInvisible()) {
            GlStateManager.pushMatrix();
            GlStateManager.rotate(flip * 75F, 0, 1, 0);
            GlStateManager.translate(flip * -1F, 3.6F, 3.5F);
            GlStateManager.rotate(flip * 120F, 0, 0, 1);
            GlStateManager.rotate(200F, 1, 0, 0);
            GlStateManager.rotate(flip * -135F, 0, 1, 0);
            GlStateManager.translate(flip * 5.6F, 0, 0);
            GlStateManager.rotate(flip * 40F, 0, 1, 0);
            GlStateManager.translate(flip * .05F, -.3F, -.3F);
            mc.getTextureManager().bindTexture(player.getLocationSkin());
            net.minecraft.client.renderer.entity.Render<?> render = mc.getRenderManager().getEntityRenderObject(player);
            if (render instanceof RenderPlayer renderer) {
                if (right) renderer.renderRightArm(player); else renderer.renderLeftArm(player);
            }
            GlStateManager.popMatrix();
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(flip * -.1F, 0, -.3F);
        mc.getItemRenderer().renderItemSide(player, grip, transform, !right);
        renderCarried(player, grip, carried, transform, right);
        GlStateManager.popMatrix();
        GlStateManager.popMatrix();
    }

    private static void renderCarried(EntityLivingBase player, ItemStack grip, ItemStack carried,
                                      ItemCameraTransforms.TransformType transform, boolean right) {
        if (carried.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        float flip = right ? 1F : -1F;
        IBakedModel model = mc.getRenderItem().getItemModelWithOverrides(grip, player.world, player);
        ForgeHooksClient.handleCameraTransforms(model, transform, !right);
        GlStateManager.translate(flip * -.05F, .15F, -1.2F - renderExtension * 2.25F);
        if (holdingBlock) {
            GlStateManager.rotate(flip * 45F, 0, 1, 0);
            GlStateManager.translate(flip * .15F, -.15F, -.05F);
            GlStateManager.scale(1.25F, 1.25F, 1.25F);
        }
        mc.getItemRenderer().renderItemSide(player, carried, transform, !right);
    }

    @SubscribeEvent public static void installHeldItemLayer(RenderPlayerEvent.Pre event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.getHeldItemMainhand().getItem() != AllItems.EXTENDO_GRIP
                && player.getHeldItemOffhand().getItem() != AllItems.EXTENDO_GRIP) return;
        RenderPlayer renderer = event.getRenderer();
        if (!CHECKED_RENDERERS.add(renderer)) return;
        try {
            List<LayerRenderer<?>> layers = ObfuscationReflectionHelper.getPrivateValue(
                    RenderLivingBase.class, renderer, "field_177097_h");
            for (int i = 0; i < layers.size(); i++) {
                // Keep third-party layers untouched; replace only vanilla's hand layer.
                if (layers.get(i).getClass() == LayerHeldItem.class) {
                    layers.set(i, new GripHeldItemLayer(renderer));
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // The vanilla hand layer remains usable if another renderer hides it.
        }
    }

    private static final class GripHeldItemLayer extends LayerHeldItem {
        GripHeldItemLayer(RenderPlayer renderer) { super(renderer); }

        @Override public void doRenderLayer(EntityLivingBase player, float limbSwing, float limbSwingAmount,
                                             float partial, float age, float yaw, float pitch, float scale) {
            ItemStack main = player.getHeldItemMainhand(), off = player.getHeldItemOffhand();
            boolean mainGrip = main.getItem() == AllItems.EXTENDO_GRIP;
            boolean offGrip = off.getItem() == AllItems.EXTENDO_GRIP;
            if (!mainGrip && !offGrip) {
                super.doRenderLayer(player, limbSwing, limbSwingAmount, partial, age, yaw, pitch, scale);
                return;
            }
            ItemStack grip = offGrip ? off : main, carried = offGrip ? main : off;
            EnumHandSide side = offGrip ? player.getPrimaryHand().opposite() : player.getPrimaryHand();
            boolean right = side == EnumHandSide.RIGHT;
            ItemCameraTransforms.TransformType transform = right
                    ? ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND
                    : ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND;
            float oldExtension = renderExtension;
            boolean oldHolding = holdingBlock;
            float swing = MathHelper.sin(player.getSwingProgress(partial) * (float) Math.PI);
            renderExtension = swing * swing * swing;
            Minecraft mc = Minecraft.getMinecraft();
            holdingBlock = !carried.isEmpty() && carried.getItem() instanceof ItemBlock
                    && mc.getRenderItem().getItemModelWithOverrides(carried, player.world, player).isGui3d();
            thirdPerson = true;
            GlStateManager.pushMatrix();
            try {
                if (livingEntityRenderer.getMainModel().isChild) {
                    GlStateManager.translate(0, .75F, 0);
                    GlStateManager.scale(.5F, .5F, .5F);
                }
                if (player.isSneaking()) GlStateManager.translate(0, .2F, 0);
                translateToHand(side);
                GlStateManager.rotate(-90F, 1, 0, 0);
                GlStateManager.rotate(180F, 0, 1, 0);
                GlStateManager.translate((right ? 1F : -1F) / 16F, .125F, -.625F);
                mc.getItemRenderer().renderItemSide(player, grip, transform, !right);
                renderCarried(player, grip, carried, transform, right);
            } finally {
                GlStateManager.popMatrix();
                thirdPerson = false;
                renderExtension = oldExtension;
                holdingBlock = oldHolding;
            }
        }
    }
}
