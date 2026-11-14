package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.decoration.palette.DoorStateTile;
import com.cogwork.cogworkbridge.content.decoration.palette.PaletteDoor;
import com.cogwork.cogworkbridge.registry.BlockFamily;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

public class SlidingDoorRenderer extends TileEntitySpecialRenderer<DoorStateTile> {
    @Override public void render(DoorStateTile tile, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof PaletteDoor door) || state.getValue(BlockDoor.HALF) != BlockDoor.EnumDoorHalf.LOWER) return;
        state = door.getActualState(state, tile.getWorld(), tile.getPos());
        EnumFacing facing = state.getValue(BlockDoor.FACING);
        float progress = tile.animation(partialTicks);
        float displacement = progress * progress;
        float depth = Math.min(progress * 10F, 1F) / 32F;
        String id = state.getBlock().getRegistryName().getPath();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.translate(facing.getXOffset() * depth, 0, facing.getZOffset() * depth);
        if ((id.equals("andesite_door") || id.equals("copper_door")) && progress > 0.0001F) {
            boolean flip = tile.rightHinge;
            float sign = flip ? -1F : 1F;
            GlStateManager.translate(.5D, -1D / 512D, .5D);
            // Create's AngleHelper reverses horizontal angles for X-axis directions.
            // This also matches the 1.12 blockstate model rotation as the door starts moving.
            EnumFacing rotation = facing.rotateY();
            float horizontalAngle = rotation.getHorizontalAngle();
            if (rotation.getAxis() == EnumFacing.Axis.X) horizontalAngle = -horizontalAngle;
            GlStateManager.rotate(horizontalAngle, 0, 1, 0);
            GlStateManager.translate(-.5D, 0, -.5D);
            for (boolean left : new boolean[]{true, false}) {
                GlStateManager.pushMatrix();
                if (flip) GlStateManager.translate(0, 0, 1);
                GlStateManager.rotate(91F * sign * displacement, 0, 1, 0);
                if (!left) { GlStateManager.translate(0, 0, sign / 2F); GlStateManager.rotate(-181F * sign * displacement, 0, 1, 0); }
                if (flip) GlStateManager.translate(0, 0, -.5D);
                String part = (left ^ flip) ? "left" : "right";
                IBlockState model = BlockFamily.get("render_" + id.substring(0, id.indexOf('_')) + "_door_fold_" + part).getDefaultState();
                FastStateRendering.getInstance().renderFast(model);
                GlStateManager.popMatrix();
            }
        } else {
            EnumFacing movement = tile.rightHinge ? facing.rotateY() : facing.rotateYCCW();
            GlStateManager.translate(movement.getXOffset() * displacement * 13F / 16F, 0, movement.getZOffset() * displacement * 13F / 16F);
            for (BlockDoor.EnumDoorHalf half : BlockDoor.EnumDoorHalf.values()) {
                GlStateManager.pushMatrix();
                if (half == BlockDoor.EnumDoorHalf.UPPER) GlStateManager.translate(0, 1D - 1D / 512D, 0);
                IBlockState model = state.withProperty(BlockDoor.OPEN, false).withProperty(BlockDoor.HALF, half);
                FastStateRendering.getInstance().renderFast(model);
                GlStateManager.popMatrix();
            }
        }
        GlStateManager.popMatrix();
    }
}
