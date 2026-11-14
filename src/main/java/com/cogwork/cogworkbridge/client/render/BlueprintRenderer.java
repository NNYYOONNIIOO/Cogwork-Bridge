package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.equipment.BlueprintEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import javax.annotation.Nullable;

public final class BlueprintRenderer extends Render<BlueprintEntity> {
    public BlueprintRenderer(RenderManager manager) { super(manager); }
    @Override public void doRender(BlueprintEntity entity, double x, double y, double z, float yaw, float partial) {
        GlStateManager.pushMatrix(); GlStateManager.translate(x, y, z);
        switch (entity.facing()) {
            case DOWN -> GlStateManager.rotate(180, 1, 0, 0);
            case NORTH -> GlStateManager.rotate(-90, 1, 0, 0);
            case SOUTH -> GlStateManager.rotate(90, 1, 0, 0);
            case WEST -> GlStateManager.rotate(90, 0, 0, 1);
            case EAST -> GlStateManager.rotate(-90, 0, 0, 1);
            case UP -> { }
        }
        if (entity.facing().getAxis() == EnumFacing.Axis.Y) GlStateManager.rotate(-entity.orientation().getHorizontalAngle(), 0, 1, 0);
        GlStateManager.translate(-.5D, 0, -.5D);
        RenderPartBlock model = entity.size() == 3 ? AllBlocks.BLUEPRINT_LARGE : entity.size() == 2 ? AllBlocks.BLUEPRINT_MEDIUM : AllBlocks.BLUEPRINT_MODEL;
        FastStateRendering.getInstance().renderFast(model.getDefaultState().withProperty(RenderPartBlock.FACING, EnumFacing.UP));
        for (int row = 0; row < entity.size(); row++) for (int col = 0; col < entity.size(); col++) {
            int section = col + row * entity.size();
            for (int layer = 0; layer < 2; layer++) {
                if (entity.display(section, layer).isEmpty()) continue;
                GlStateManager.pushMatrix();
                GlStateManager.translate(.5D + col - (entity.size() - 1) * .5D + (layer == 0 ? 0 : .16D), .09D + layer * .01D,
                        .5D + row - (entity.size() - 1) * .5D + (layer == 0 ? 0 : .16D));
                GlStateManager.rotate(90F, 1, 0, 0);
                float scale = layer == 0 ? .42F : .26F; GlStateManager.scale(scale, scale, scale);
                Minecraft.getMinecraft().getRenderItem().renderItem(entity.display(section, layer), ItemCameraTransforms.TransformType.FIXED);
                GlStateManager.popMatrix();
            }
        }
        GlStateManager.popMatrix();
    }
    @Nullable @Override protected ResourceLocation getEntityTexture(BlueprintEntity entity) { return null; }
}
