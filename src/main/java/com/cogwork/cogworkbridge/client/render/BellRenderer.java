package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.equipment.BellBlock;
import com.cogwork.cogworkbridge.content.equipment.BellTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

public final class BellRenderer extends TileEntitySpecialRenderer<BellTileEntity> {
    @Override public void render(BellTileEntity bell, double x, double y, double z, float partial, int destroy, float alpha) {
        if (bell.getWorld() == null) return;
        IBlockState state = bell.getWorld().getBlockState(bell.getPos());
        if (!(state.getBlock() instanceof BellBlock block)) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + .5D, y + .5D, z + .5D);
        if (bell.ticks() < 74) {
            float t = (bell.ticks() + partial) / 1.5F;
            float swing = (float) Math.toDegrees(1.2D * Math.sin(t / Math.PI) / (2.5D + t / 3D));
            EnumFacing direction = bell.direction().rotateYCCW();
            GlStateManager.rotate(swing, direction.getXOffset(), 0, direction.getZOffset());
        }
        EnumFacing facing = state.getValue(BellBlock.FACING);
        float angle = facing.getHorizontalAngle();
        if (state.getValue(BellBlock.MOUNT) == BellBlock.Mount.SINGLE_WALL || state.getValue(BellBlock.MOUNT) == BellBlock.Mount.DOUBLE_WALL) angle += 90F;
        GlStateManager.rotate(angle, 0, 1, 0);
        GlStateManager.translate(-.5D, -.5D, -.5D);
        FastStateRendering.getInstance().renderFast((block.isHaunted() ? AllBlocks.HAUNTED_BELL_BODY : AllBlocks.PECULIAR_BELL_BODY)
                .getDefaultState().withProperty(RenderPartBlock.FACING, EnumFacing.UP));
        GlStateManager.popMatrix();
    }
}
