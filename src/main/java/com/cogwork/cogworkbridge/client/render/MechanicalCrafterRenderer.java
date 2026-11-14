package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterBlock;
import com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterDirection;
import com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity;
import com.cogwork.cogworkbridge.client.MechanicalCrafterModels;
import com.cogwork.cogworkbridge.client.MechanicalCrafterModels.Part;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class MechanicalCrafterRenderer extends TileEntitySpecialRenderer<MechanicalCrafterTileEntity> {
    @Override
    public void render(MechanicalCrafterTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof MechanicalCrafterBlock)) return;

        EnumFacing facing = state.getValue(MechanicalCrafterBlock.FACING);
        BlockPos renderPos = tile.getContraptionRenderPos() == null ? tile.getPos() : tile.getContraptionRenderPos();
        IBlockState cog = AllBlocks.COGWHEEL_SHAFTLESS.getDefaultState()
                .withProperty(RenderPartBlock.FACING, facing);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, cog, facing.getAxis(),
                RotatingModelRenderer.kineticAngle(tile, partialTicks));

        if (MechanicalCrafterBlock.hasValidTarget(tile.getWorld(), tile.getPos(), state)) {
            renderOriented(state, tile.getPointing(), Part.BELT_FRAME);
            renderOriented(state, tile.getPointing(), Part.BELT);
        } else {
            renderOriented(state, tile.getPointing(), Part.ARROW);
        }
        if (tile.getInput().getItem() == AllItems.CRAFTER_SLOT_COVER) {
            renderOriented(state, tile.getPointing(), Part.LID);
        }
        GlStateManager.popMatrix();

        boolean animating = tile.hasCraftAnimation();
        float stage = animating ? tile.getCraftAnimationStage(partialTicks) : 0.0F;
        float craftPhase = stage - tile.getCraftExportStages();
        float pausedScale = animating ? 1.0F - MathHelper.clamp(craftPhase * 2.0F - 1.0F, 0.0F, 1.0F) : 1.0F;
        if (pausedScale > 0.0F) for (MechanicalCrafterTileEntity.PausedIngredient ingredient : tile.getPausedIngredients()) {
            renderIngredient(ingredient.stack(), facing, x, y, z, ingredient.renderOffset(), pausedScale, 0.0F);
        }
        ItemStack stack = tile.getInput();
        float itemScale = 1.0F;
        float spin = 0.0F;
        Vec3d offset = Vec3d.ZERO;
        if (animating) {
            if (craftPhase >= 1.0F) {
                if (!tile.isCraftAnimationPrimary()) return;
                stack = tile.getCraftPreview();
                float progress = MathHelper.clamp(craftPhase - 1.0F, 0.0F, 1.0F);
                float early = MathHelper.clamp(progress * 2.0F, 0.0F, 1.0F);
                float late = MathHelper.clamp(progress * 2.0F - 1.0F, 0.0F, 1.0F);
                spin = early * 720.0F;
                itemScale = early * 1.125F * (1.0F + (1.0F - late) * 0.125F);
            } else {
                offset = tile.getCraftItemOffset(stage);
                itemScale = 1.0F - MathHelper.clamp(craftPhase * 2.0F - 1.0F, 0.0F, 1.0F);
            }
        }
        if (stack.isEmpty() || stack.getItem() == AllItems.CRAFTER_SLOT_COVER || itemScale <= 0.0F) return;

        renderIngredient(stack, facing, x, y, z, offset, itemScale, spin);
    }

    private static void renderIngredient(ItemStack stack, EnumFacing facing, double x, double y, double z,
                                         Vec3d offset, float itemScale, float spin) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D + facing.getXOffset() * 0.58D + offset.x,
                y + 0.5D + facing.getYOffset() * 0.58D + offset.y,
                z + 0.5D + facing.getZOffset() * 0.58D + offset.z);
        if (facing.getAxis() == EnumFacing.Axis.Y) {
            GlStateManager.rotate(facing == EnumFacing.UP ? 90.0F : -90.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(-EnumFacing.NORTH.getHorizontalAngle(), 0.0F, 1.0F, 0.0F);
        } else {
            GlStateManager.rotate(-facing.getHorizontalAngle(), 0.0F, 1.0F, 0.0F);
        }
        if (spin != 0.0F) GlStateManager.rotate(spin, 0.0F, 0.0F, 1.0F);
        GlStateManager.translate(0.0D, 0.0D, -1.0D / 256.0D);
        GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(0.5F * itemScale, 0.5F * itemScale, 0.5F * itemScale);
        RenderHelper.enableStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.FIXED);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    private static void renderOriented(IBlockState state, MechanicalCrafterDirection pointing, Part part) {
        if (MechanicalCrafterModels.get(part) == null) return;
        EnumFacing facing = state.getValue(MechanicalCrafterBlock.FACING);
        float xRotation = MechanicalCrafterModels.pointingRotation(pointing);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.5D, 0.5D, 0.5D);
        if (facing.getAxis() == EnumFacing.Axis.Y) {
            GlStateManager.rotate(facing == EnumFacing.UP ? 90.0F : -90.0F, 1.0F, 0.0F, 0.0F);
        }
        GlStateManager.rotate(MechanicalCrafterModels.facingRotation(facing), 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(xRotation, 1.0F, 0.0F, 0.0F);
        GlStateManager.translate(-0.5D, -0.5D, -0.5D);
        FastStateRendering.getInstance().renderFast(MechanicalCrafterModels.get(part), state);
        GlStateManager.popMatrix();
    }
}
