package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.client.render.FastStateRendering;
import com.cogwork.cogworkbridge.content.kinetics.placement.KineticPlacementItemBlock;
import com.cogwork.cogworkbridge.content.kinetics.placement.KineticPlacementPlan;
import com.cogwork.cogworkbridge.content.contraptions.bearing.SailBlock;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.content.decoration.MetalScaffoldingBlock;
import com.cogwork.cogworkbridge.content.decoration.MetalScaffoldingItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResource;
import net.minecraft.entity.Entity;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.EXTBlendColor;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class KineticPlacementClientEvents {
    private static final ResourceLocation INDICATOR =
            new ResourceLocation("create", "textures/gui/placement_indicator.png");
    private static BlockPos indicatorTarget;
    private static int indicatorTicks;
    private static float indicatorAngle;
    private static boolean angleInitialized;
    private static Boolean indicatorTextureAvailable;

    private KineticPlacementClientEvents() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            indicatorTarget = null;
            indicatorTicks = 0;
            angleInitialized = false;
            return;
        }
        KineticPlacementPlan plan = placementPlan(mc);
        if (plan != null && plan.assisted() && plan.valid()) {
            indicatorTarget = plan.target();
            indicatorTicks = Math.min(10, indicatorTicks + 1);
        } else {
            indicatorTicks = Math.max(0, indicatorTicks - 2);
            if (indicatorTicks == 0) {
                indicatorTarget = null;
                angleInitialized = false;
            }
        }
    }

    @SubscribeEvent
    public static void reloadTextures(TextureStitchEvent.Pre event) {
        indicatorTextureAvailable = null;
    }

    @SubscribeEvent
    public static void renderPlacement(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        KineticPlacementPlan plan = placementPlan(mc);
        Entity view = mc.getRenderViewEntity();
        if (plan == null || !plan.valid() || view == null) {
            return;
        }
        double partial = event.getPartialTicks();
        double cameraX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partial;
        double cameraY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partial;
        double cameraZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partial;
        renderPreviewModel(plan, plan.target(), cameraX, cameraY, cameraZ);
    }

    private static KineticPlacementPlan placementPlan(Minecraft mc) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null
                || mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK
                || mc.objectMouseOver.sideHit == null || mc.objectMouseOver.hitVec == null) {
            return null;
        }
        EnumHand hand = EnumHand.MAIN_HAND;
        ItemStack stack = mc.player.getHeldItem(hand);
        Block heldBlock = Block.getBlockFromItem(stack.getItem());
        if (heldBlock == AllBlocks.DEPLOYER) return null;
        if (!(stack.getItem() instanceof KineticPlacementItemBlock) && !(heldBlock instanceof SailBlock)
                && !(heldBlock instanceof com.cogwork.cogworkbridge.content.contraptions.gantry.GantryShaftBlock)
                && !(heldBlock instanceof com.cogwork.cogworkbridge.content.decoration.palette.GirderBlock)
                && !(heldBlock instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Ladder)
                && !(heldBlock instanceof MetalScaffoldingBlock)) {
            hand = EnumHand.OFF_HAND;
            stack = mc.player.getHeldItem(hand);
            heldBlock = Block.getBlockFromItem(stack.getItem());
        }
        BlockPos clicked = mc.objectMouseOver.getBlockPos();
        IBlockState clickedState = mc.world.getBlockState(clicked);
        if (heldBlock instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Ladder ladder
                && clickedState.getBlock() instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Ladder source) {
            BlockPos target = source.placementTarget(mc.world, clicked, clickedState, mc.player, stack, mc.objectMouseOver.sideHit, ladder);
            if (target == null) return null;
            return new KineticPlacementPlan(target, net.minecraft.util.EnumFacing.Axis.Y, true, true,
                    ladder.getDefaultState().withProperty(net.minecraft.block.BlockLadder.FACING, clickedState.getValue(net.minecraft.block.BlockLadder.FACING)));
        }
        if (heldBlock instanceof com.cogwork.cogworkbridge.content.decoration.palette.GirderBlock
                && (clickedState.getBlock() instanceof com.cogwork.cogworkbridge.content.decoration.palette.GirderBlock
                    || clickedState.getBlock() instanceof com.cogwork.cogworkbridge.content.decoration.palette.GirderShaftBlock)) {
            var placement = com.cogwork.cogworkbridge.content.decoration.palette.GirderPlacement.find(mc.world, clicked,
                    clickedState, mc.player, stack, mc.objectMouseOver.sideHit,
                    (float) (mc.objectMouseOver.hitVec.x - clicked.getX()),
                    (float) (mc.objectMouseOver.hitVec.y - clicked.getY()),
                    (float) (mc.objectMouseOver.hitVec.z - clicked.getZ()));
            if (placement == null) return null;
            return new KineticPlacementPlan(placement.pos(), placement.direction().getAxis(), true, true,
                    placement.state().getActualState(mc.world, placement.pos()));
        }
        if (stack.getItem() instanceof MetalScaffoldingItem scaffolding && clickedState.getBlock() == scaffolding.getBlock()) {
            MetalScaffoldingItem.Placement placement = scaffolding.placement(mc.world, clicked, mc.player, hand,
                    mc.objectMouseOver.sideHit,
                    (float) (mc.objectMouseOver.hitVec.x - clicked.getX()),
                    (float) (mc.objectMouseOver.hitVec.y - clicked.getY()),
                    (float) (mc.objectMouseOver.hitVec.z - clicked.getZ()));
            if (placement == null) return null;
            IBlockState preview = placement.state().getActualState(mc.world, placement.target());
            return new KineticPlacementPlan(placement.target(), placement.direction().getAxis(), true, true, preview);
        }
        if (heldBlock instanceof com.cogwork.cogworkbridge.content.contraptions.gantry.GantryShaftBlock gantry) {
            if (!mc.player.isSneaking() && clickedState.getBlock() == gantry) {
                BlockPos target = gantry.placementTarget(mc.world, clicked, clickedState, mc.player, stack, mc.objectMouseOver.sideHit);
                if (target == null) return null;
                return new KineticPlacementPlan(target, gantry.getAxis(clickedState), true, true, clickedState);
            }
            return KineticPlacementItemBlock.preview(mc.world, clicked, mc.objectMouseOver.sideHit,
                    (float) (mc.objectMouseOver.hitVec.x - clicked.getX()),
                    (float) (mc.objectMouseOver.hitVec.y - clicked.getY()),
                    (float) (mc.objectMouseOver.hitVec.z - clicked.getZ()), mc.player, hand, gantry);
        }
        if (heldBlock instanceof SailBlock sail && clickedState.getBlock() instanceof SailBlock && !mc.player.isSneaking()) {
            BlockPos target = SailBlock.adjacentPlacement(mc.world, clicked, clickedState, mc.player, stack, sail,
                    (float) (mc.objectMouseOver.hitVec.x - clicked.getX()),
                    (float) (mc.objectMouseOver.hitVec.y - clicked.getY()),
                    (float) (mc.objectMouseOver.hitVec.z - clicked.getZ()));
            if (target == null) return null;
            return new KineticPlacementPlan(target, clickedState.getValue(SailBlock.FACING).getAxis(), true, true,
                    sail.getDefaultState().withProperty(SailBlock.FACING, clickedState.getValue(SailBlock.FACING)));
        }
        if (!(stack.getItem() instanceof KineticPlacementItemBlock item)) {
            return null;
        }
        if (!mc.player.isSneaking() && item.getBlock() == AllBlocks.SHAFT
                && clickedState.getBlock() instanceof BeltBlock) return null;
        return KineticPlacementItemBlock.preview(mc.world, clicked, mc.objectMouseOver.sideHit,
                (float) (mc.objectMouseOver.hitVec.x - clicked.getX()),
                (float) (mc.objectMouseOver.hitVec.y - clicked.getY()),
                (float) (mc.objectMouseOver.hitVec.z - clicked.getZ()),
                mc.player, hand, item.getBlock());
    }

    @SubscribeEvent
    public static void renderIndicator(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getType() != RenderGameOverlayEvent.ElementType.CROSSHAIRS
                || mc.player == null || mc.world == null || mc.currentScreen != null
                || mc.gameSettings.hideGUI || indicatorTicks == 0 || indicatorTarget == null) {
            return;
        }
        Entity view = mc.getRenderViewEntity();
        KineticPlacementPlan plan = placementPlan(mc);
        if (view == null || plan == null || !plan.assisted() || !plan.valid()) {
            return;
        }

        float partial = event.getPartialTicks();
        Vec3d offset = new Vec3d(plan.target()).add(0.5D, 0.5D, 0.5D)
                .subtract(view.getPositionEyes(partial));
        float yaw = view.prevRotationYaw
                + MathHelper.wrapDegrees(view.rotationYaw - view.prevRotationYaw) * partial;
        double yawRadians = Math.toRadians(yaw);
        Vec3d right = new Vec3d(Math.cos(yawRadians), 0.0D, Math.sin(yawRadians));
        Vec3d forward = view.getLook(partial);
        if (mc.gameSettings.thirdPersonView == 2) {
            right = right.scale(-1.0D);
            forward = forward.scale(-1.0D);
        }
        Vec3d up = forward.crossProduct(right);
        double screenX = offset.dotProduct(right);
        double screenY = offset.dotProduct(up);
        if (screenX * screenX + screenY * screenY > 1.0E-8D) {
            float desired = (float) Math.toDegrees(Math.atan2(screenX, screenY));
            if (!angleInitialized || indicatorTicks < 10) {
                indicatorAngle = desired;
                angleInitialized = true;
            } else {
                indicatorAngle += MathHelper.wrapDegrees(desired - indicatorAngle) * 0.25F;
            }
        }
        int frame = Math.floorMod(Math.round(indicatorAngle / 22.5F), 16);
        float alpha = indicatorTicks / 10.0F;
        drawIndicator(mc, event.getResolution().getScaledWidth() / 2,
                event.getResolution().getScaledHeight() / 2, frame, alpha * alpha);
    }

    private static void drawIndicator(Minecraft mc, int x, int y, int frame, float alpha) {
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int alphaFunction = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        float alphaReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableDepth();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
            GlStateManager.translate(x, y, 0.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
            if (hasIndicatorTexture(mc)) {
                GlStateManager.enableTexture2D();
                mc.getTextureManager().bindTexture(INDICATOR);
                Gui.drawScaledCustomSizeModalRect(-9, -9, 0, frame * 16,
                        16, 16, 18, 18, 16, 256);
            } else {
                drawFallbackIndicator(frame, alpha);
            }
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.bindTexture(boundTexture);
            GlStateManager.alphaFunc(alphaFunction, alphaReference);
            GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (!blend) GlStateManager.disableBlend();
            if (depth) GlStateManager.enableDepth();
            if (!texture) GlStateManager.disableTexture2D();
            else GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static boolean hasIndicatorTexture(Minecraft mc) {
        if (indicatorTextureAvailable == null) {
            try (IResource ignored = mc.getResourceManager().getResource(INDICATOR)) {
                indicatorTextureAvailable = true;
            } catch (IOException exception) {
                indicatorTextureAvailable = false;
            }
        }
        return indicatorTextureAvailable;
    }

    private static void drawFallbackIndicator(int frame, float alpha) {
        GlStateManager.disableTexture2D();
        GlStateManager.rotate(frame * 22.5F, 0.0F, 0.0F, 1.0F);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(0, -9, 0).color(0.8F, 0.8F, 0.8F, alpha).endVertex();
        buffer.pos(-4, -4, 0).color(0.8F, 0.8F, 0.8F, alpha).endVertex();
        buffer.pos(4, -4, 0).color(0.8F, 0.8F, 0.8F, alpha).endVertex();
        tessellator.draw();
    }

    private static void renderPreviewModel(KineticPlacementPlan plan,
                                           BlockPos pos, double cameraX,
                                           double cameraY, double cameraZ) {
        if (plan.previewState() == null) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(pos.getX() - cameraX, pos.getY() - cameraY,
                pos.getZ() - cameraZ);
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
        GL14.glBlendColor(1.0F, 1.0F, 1.0F, plan.valid() ? 0.55F : 0.28F);
        GlStateManager.blendFunc(EXTBlendColor.GL_CONSTANT_ALPHA_EXT,
                EXTBlendColor.GL_ONE_MINUS_CONSTANT_ALPHA_EXT);
        GlStateManager.depthMask(false);
        try {
            FastStateRendering.getInstance().renderFast(plan.previewState());
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

}
