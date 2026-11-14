package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.client.render.FastStateRendering;
import com.cogwork.cogworkbridge.content.equipment.BacktankBlock;
import com.cogwork.cogworkbridge.content.equipment.BacktankItem;
import com.cogwork.cogworkbridge.content.equipment.CardboardArmorItem;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class EquipmentRenderEvents {
    private static final Set<RenderPlayer> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<RenderPlayer> BOX_RENDERERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<net.minecraft.client.renderer.entity.RenderLivingBase<?>> LIVING_INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void renderBox(RenderPlayerEvent.Pre event) {
        if (!CardboardArmorItem.isHiding(event.getEntityPlayer())) {
            // RenderPlayer is shared and survives world changes. Never retain a hidden
            // model from a cancelled disguise render; vanilla reapplies skin-part flags.
            if (BOX_RENDERERS.remove(event.getRenderer())) event.getRenderer().getMainModel().setVisible(true);
            return;
        }
        BOX_RENDERERS.add(event.getRenderer());
        event.setCanceled(true);
        AbstractClientPlayer player = (AbstractClientPlayer) event.getEntityPlayer();
        if (player == Minecraft.getMinecraft().player && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0) return;
        int index = Math.floorMod(player.getUniqueID().hashCode(), AllItems.CARDBOARD_PACKAGES.length);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean rescale = GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int alphaFunction = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        float alphaReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        java.nio.FloatBuffer color = org.lwjgl.BufferUtils.createFloatBuffer(4);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(event.getX(), event.getY() + .4D, event.getZ());
            GlStateManager.rotate(180F - player.rotationYaw, 0, 1, 0);
            GlStateManager.scale(1.5F, 1.5F, 1.5F);
            GlStateManager.color(1, 1, 1, 1);
            // Entity lighting is already installed. Installing GUI item lighting here
            // leaks light state because cancelling Pre bypasses the native cleanup.
            Minecraft.getMinecraft().getRenderItem().renderItem(new ItemStack(AllItems.CARDBOARD_PACKAGES[index]), ItemCameraTransforms.TransformType.FIXED);
        } finally {
            GlStateManager.popMatrix();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (rescale) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal();
            if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            GlStateManager.alphaFunc(alphaFunction, alphaReference);
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            GlStateManager.depthMask(depthMask);
            GlStateManager.bindTexture(texture);
            GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
        }
    }
    @SubscribeEvent public static void clearDisguiseRenderers(net.minecraftforge.event.world.WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) return;
        for (RenderPlayer renderer : BOX_RENDERERS) renderer.getMainModel().setVisible(true);
        BOX_RENDERERS.clear();
    }
    @SubscribeEvent
    public static void installBacktankLayer(RenderPlayerEvent.Pre event) {
        RenderPlayer renderer = event.getRenderer();
        if (!INSTALLED.add(renderer)) return;
        renderer.addLayer(new LayerRenderer<AbstractClientPlayer>() {
            @Override public void doRenderLayer(AbstractClientPlayer player, float swing, float swingAmount, float partial,
                                                float age, float yaw, float pitch, float scale) {
                ItemStack stack = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
                if (!(stack.getItem() instanceof BacktankItem)) return;
                boolean netherite = stack.getItem() == AllItems.NETHERITE_BACKTANK;
                GlStateManager.pushMatrix();
                renderer.getMainModel().bipedBody.postRender(scale);
                GlStateManager.translate(-.5D, 10D / 16D, 1D);
                GlStateManager.scale(1D, -1D, -1D);
                FastStateRendering.getInstance().renderFast((netherite ? AllBlocks.NETHERITE_BACKTANK : AllBlocks.COPPER_BACKTANK)
                        .getDefaultState().withProperty(BacktankBlock.FACING, EnumFacing.SOUTH));
                FastStateRendering.getInstance().renderFast((netherite ? AllBlocks.NETHERITE_BACKTANK_SHAFT : AllBlocks.COPPER_BACKTANK_SHAFT)
                        .getDefaultState().withProperty(RenderPartBlock.FACING, EnumFacing.UP));
                FastStateRendering.getInstance().renderFast((netherite ? AllBlocks.NETHERITE_BACKTANK_COGS : AllBlocks.COPPER_BACKTANK_COGS)
                        .getDefaultState().withProperty(RenderPartBlock.FACING, EnumFacing.UP));
                GlStateManager.popMatrix();
            }
            @Override public boolean shouldCombineTextures() { return false; }
        });
    }
    @SubscribeEvent
    public static void installMobBacktankLayer(RenderLivingEvent.Pre<?> event) {
        var renderer = event.getRenderer();
        if (event.getEntity() instanceof net.minecraft.entity.player.EntityPlayer || !(renderer.getMainModel() instanceof net.minecraft.client.model.ModelBiped)
                || !LIVING_INSTALLED.add(renderer)) return;
        renderer.addLayer(new LayerRenderer<net.minecraft.entity.EntityLivingBase>() {
            @Override public void doRenderLayer(net.minecraft.entity.EntityLivingBase entity, float swing, float swingAmount, float partial,
                                                float age, float yaw, float pitch, float scale) {
                ItemStack stack = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
                if (!(stack.getItem() instanceof BacktankItem)) return;
                boolean netherite = stack.getItem() == AllItems.NETHERITE_BACKTANK;
                GlStateManager.pushMatrix();
                ((net.minecraft.client.model.ModelBiped) renderer.getMainModel()).bipedBody.postRender(scale);
                GlStateManager.translate(-.5D, 10D / 16D, 1D); GlStateManager.scale(1D, -1D, -1D);
                FastStateRendering.getInstance().renderFast((netherite ? AllBlocks.NETHERITE_BACKTANK : AllBlocks.COPPER_BACKTANK)
                        .getDefaultState().withProperty(BacktankBlock.FACING, EnumFacing.SOUTH));
                FastStateRendering.getInstance().renderFast((netherite ? AllBlocks.NETHERITE_BACKTANK_SHAFT : AllBlocks.COPPER_BACKTANK_SHAFT)
                        .getDefaultState().withProperty(RenderPartBlock.FACING, EnumFacing.UP));
                FastStateRendering.getInstance().renderFast((netherite ? AllBlocks.NETHERITE_BACKTANK_COGS : AllBlocks.COPPER_BACKTANK_COGS)
                        .getDefaultState().withProperty(RenderPartBlock.FACING, EnumFacing.UP));
                GlStateManager.popMatrix();
            }
            @Override public boolean shouldCombineTextures() { return false; }
        });
    }
}
