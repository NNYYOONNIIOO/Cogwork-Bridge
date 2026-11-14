package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.client.ClientContraptionMiningProgress;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.MechanicalBearingBlock;
import com.cogwork.cogworkbridge.content.contraptions.bearing.MechanicalBearingTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.motor.CreativeMotorBlock;
import com.cogwork.cogworkbridge.content.kinetics.motor.CreativeMotorTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunkClient;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Draws vanilla breaking textures inside the contraption's existing transform. */
public final class ContraptionBreakOverlayRenderer {

    private static final Set<net.minecraft.block.Block> FAILED_BLOCKS =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private ContraptionBreakOverlayRenderer() {
    }

    public static void render(BearingContraptionEntity entity,
                              MobileChunkClient chunk, float partialTicks) {
        for (ClientContraptionMiningProgress.Entry entry :
                ClientContraptionMiningProgress.forEntity(entity.getEntityId())) {
            if(!entity.isRailwayBlockVisible(entry.localPos()))continue;
            renderStage(chunk, partialTicks, entry.localPos(), entry.stage());
        }
    }

    private static void renderStage(MobileChunkClient chunk, float partialTicks,
                                    BlockPos pos, int stage) {
        Minecraft mc = Minecraft.getMinecraft();
        IBlockState state = chunk.getBlockState(pos);
        World world = chunk.getFakeWorld();
        if (state.getBlock().isAir(state, world, pos)
                || FAILED_BLOCKS.contains(state.getBlock())) {
            return;
        }
        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(
                "minecraft:blocks/destroy_stage_" + stage);
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.5F);
        GlStateManager.doPolygonOffset(-1.0F, -10.0F);
        GlStateManager.enablePolygonOffset();
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(false);
        try {
            if (state.getRenderType() == EnumBlockRenderType.MODEL) {
                drawBlockDamage(mc.getBlockRendererDispatcher(), world, pos, state, sprite);
            }
            TileEntity tile = chunk.getTileEntity(pos);
            if (state.getRenderType() == EnumBlockRenderType.ENTITYBLOCK_ANIMATED
                    && state.getBlock() instanceof KineticBlock block
                    && tile instanceof KineticTileEntity kinetic) {
                drawRotatedDamage(world, pos, state, sprite, block.getAxis(state),
                        RotatingModelRenderer.kineticAngle(kinetic, partialTicks));
            } else if (state.getBlock() instanceof CreativeMotorBlock
                    && tile instanceof CreativeMotorTileEntity motor) {
                EnumFacing facing = state.getValue(CreativeMotorBlock.FACING);
                IBlockState rotor = AllBlocks.CREATIVE_MOTOR_ROTOR.getDefaultState()
                        .withProperty(RenderPartBlock.FACING, facing);
                drawRotatedDamage(world, pos, rotor, sprite, facing.getAxis(),
                        RotatingModelRenderer.kineticAngle(motor, partialTicks));
            } else if (state.getBlock() instanceof MechanicalBearingBlock
                    && tile instanceof MechanicalBearingTileEntity bearing) {
                EnumFacing facing = state.getValue(MechanicalBearingBlock.FACING);
                float angle = bearingAngle(bearing, partialTicks);
                drawRotatedDamage(world, pos, AllBlocks.MECHANICAL_BEARING_ASSEMBLY
                        .getDefaultState().withProperty(RenderPartBlock.FACING, facing),
                        sprite, facing.getAxis(), angle);
                drawRotatedDamage(world, pos, AllBlocks.MECHANICAL_BEARING_SHAFT
                        .getDefaultState().withProperty(RenderPartBlock.FACING, facing),
                        sprite, facing.getAxis(), angle);
                drawRotatedDamage(world, pos, AllBlocks.MECHANICAL_BEARING_TOP
                        .getDefaultState().withProperty(RenderPartBlock.FACING, facing),
                        sprite, facing.getAxis(), angle);
            } else if (state.getRenderType() == EnumBlockRenderType.ENTITYBLOCK_ANIMATED
                    && tile != null) {
                renderTileDamage(world, tile, partialTicks, stage);
            }
        } catch (RuntimeException exception) {
            if (FAILED_BLOCKS.add(state.getBlock())) {
                com.cogwork.cogworkbridge.CogworkBridge.LOGGER.error(
                        "Could not render contraption breaking overlay for {}",
                        state, exception);
            }
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.doPolygonOffset(0.0F, 0.0F);
            GlStateManager.disablePolygonOffset();
            GlStateManager.disableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            RenderHelper.enableStandardItemLighting();
        }
    }

    private static void drawBlockDamage(BlockRendererDispatcher dispatcher, World world,
                                        BlockPos pos, IBlockState state,
                                        TextureAtlasSprite sprite) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.BLOCK);
        buffer.noColor();
        try {
            dispatcher.renderBlockDamage(state, pos, sprite, world);
            tessellator.draw();
        } catch (RuntimeException exception) {
            discardBuffer(buffer);
            throw exception;
        }
    }

    private static void drawRotatedDamage(World world, BlockPos pos, IBlockState state,
                                          TextureAtlasSprite sprite, EnumFacing.Axis axis,
                                          float angle) {
        BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
        IBakedModel model = ForgeHooksClient.getDamageModel(
                dispatcher.getModelForState(state), sprite, state, world, pos);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            switch (axis) {
                case X -> GlStateManager.rotate(angle, 1.0F, 0.0F, 0.0F);
                case Y -> GlStateManager.rotate(angle, 0.0F, 1.0F, 0.0F);
                case Z -> GlStateManager.rotate(angle, 0.0F, 0.0F, 1.0F);
            }
            GlStateManager.translate(-0.5D, -0.5D, -0.5D);
            buffer.begin(7, DefaultVertexFormats.BLOCK);
            buffer.noColor();
            buffer.setTranslation(-pos.getX(), -pos.getY(), -pos.getZ());
            try {
                dispatcher.getBlockModelRenderer().renderModel(
                        world, model, state, pos, buffer, false);
                tessellator.draw();
            } catch (RuntimeException exception) {
                discardBuffer(buffer);
                throw exception;
            }
        } finally {
            buffer.setTranslation(0.0D, 0.0D, 0.0D);
            GlStateManager.popMatrix();
        }
    }

    private static void renderTileDamage(World world, TileEntity tile,
                                         float partialTicks, int stage) {
        TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;
        if (dispatcher.getRenderer(tile) == null) {
            return;
        }
        World previousWorld = dispatcher.world;
        dispatcher.setWorld(world);
        try {
            dispatcher.render(tile, partialTicks, stage);
        } finally {
            dispatcher.setWorld(previousWorld);
        }
    }

    private static void discardBuffer(BufferBuilder buffer) {
        try {
            buffer.finishDrawing();
        } catch (IllegalStateException ignored) {
        }
        buffer.reset();
    }

    private static float bearingAngle(MechanicalBearingTileEntity bearing, float partialTicks) {
        Float phase = bearing.getContraptionRenderPhase();
        if (phase != null) {
            return phase;
        }
        if (!bearing.isAssembled()) {
            return 0.0F;
        }
        Entity entity = bearing.getWorld().getEntityByID(bearing.getContraptionEntityId());
        return entity instanceof BearingContraptionEntity contraption && contraption.hasContraptionData()
                ? contraption.getRenderAngleFromBearing(partialTicks)
                : bearing.getRenderAngle(partialTicks);
    }
}
