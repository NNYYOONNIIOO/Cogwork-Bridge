package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock;
import com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Draws the baked upstream blade and the independently turning drive shaft. */
public final class MechanicalSawRenderer extends TileEntitySpecialRenderer<MechanicalSawTileEntity> {
    @Override
    public void render(MechanicalSawTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof MechanicalSawBlock saw)) return;
        EnumFacing facing = state.getValue(MechanicalSawBlock.FACING);
        EnumFacing.Axis axis = saw.getAxis(state);
        if (facing == EnumFacing.UP) {
            var filter = tile.getTileData().hasKey("TutorialFilter", 10)
                    ? new net.minecraft.item.ItemStack(tile.getTileData().getCompoundTag("TutorialFilter")) : tile.getFilter();
            if (!filter.isEmpty()) {
                var visual = tile.getTileData().getCompoundTag("TutorialState");
                boolean first = axis == EnumFacing.Axis.X;
                boolean flipped = visual.hasKey("flipped") ? visual.getString("flipped").equals("true") : tile.isFlipped();
                var center = MechanicalSawBlock.filterCenter(state, flipped);
                GlStateManager.pushMatrix();
                GlStateManager.translate(x + center.x, y + center.y, z + center.z);
                GlStateManager.rotate((first ? 90 : 0) + (flipped ? 0 : 180), 0, 1, 0);
                GlStateManager.rotate(90, 1, 0, 0);
                GlStateManager.scale(.4, .4, .4);
                net.minecraft.client.renderer.RenderHelper.enableStandardItemLighting();
                net.minecraft.client.Minecraft.getMinecraft().getRenderItem().renderItem(filter,
                        net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.FIXED);
                net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
                GlStateManager.popMatrix();
            }
        }
        BlockPos renderPos = tile.getContraptionRenderPos() != null
                ? tile.getContraptionRenderPos() : tile.getPos();
        float speed = tile.getSpeed();
        if (tile.getWorld() instanceof FakeWorld fakeWorld
                && fakeWorld.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity structure
                && structure.getSpeed() != 0.0F) {
            speed = structure.getSpeed();
        }
        IBlockState shaft = facing.getAxis() == EnumFacing.Axis.Y
                ? AllBlocks.SHAFT.getDefaultState().withProperty(KineticBlock.AXIS, axis)
                : AllBlocks.CREATIVE_MOTOR_ROTOR.getDefaultState()
                        .withProperty(RenderPartBlock.FACING, facing.getOpposite());
        RenderPartBlock blade = speed == 0.0F ? AllBlocks.MECHANICAL_SAW_BLADE
                : speed > 0.0F ? AllBlocks.MECHANICAL_SAW_BLADE_ACTIVE
                : AllBlocks.MECHANICAL_SAW_BLADE_REVERSED;
        IBlockState bladeState = blade.getDefaultState().withProperty(RenderPartBlock.FACING, facing);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, shaft, axis,
                RotatingModelRenderer.kineticAngle(tile, partialTicks));
        if (facing.getAxis() == EnumFacing.Axis.Y && axis == EnumFacing.Axis.X) {
            GlStateManager.translate(0.5D, 0.5D, 0.5D);
            GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(-0.5D, -0.5D, -0.5D);
        }
        FastStateRendering.getInstance().renderFast(bladeState);
        GlStateManager.popMatrix();
        if (facing == EnumFacing.UP && !tile.displayItem().isEmpty()) {
            var stack = tile.displayItem();
            var direction = tile.itemDirection();
            float offset = tile.displayProgress(partialTicks) - .5F;
            var mc = net.minecraft.client.Minecraft.getMinecraft();
            boolean block = mc.getRenderItem().getItemModelWithOverrides(stack, tile.getWorld(), null).isGui3d();
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + .5D + direction.getXOffset() * offset, y + (block ? .925D : 13D / 16D),
                    z + .5D + direction.getZOffset() * offset);
            GlStateManager.scale(.5F, .5F, .5F);
            GlStateManager.rotate(90F, 1, 0, 0);
            net.minecraft.client.renderer.RenderHelper.enableStandardItemLighting();
            mc.getRenderItem().renderItem(stack, net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.FIXED);
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }
    }
}
