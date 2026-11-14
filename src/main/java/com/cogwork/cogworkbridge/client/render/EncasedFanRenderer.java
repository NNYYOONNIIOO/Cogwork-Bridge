package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanBlock;
import com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public final class EncasedFanRenderer extends TileEntitySpecialRenderer<EncasedFanTileEntity> {
    @Override
    public void render(EncasedFanTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof EncasedFanBlock)) {
            return;
        }
        EnumFacing facing = state.getValue(EncasedFanBlock.FACING);
        IBlockState shaft = AllBlocks.CREATIVE_MOTOR_ROTOR.getDefaultState()
                .withProperty(RenderPartBlock.FACING, facing.getOpposite());
        IBlockState propeller = AllBlocks.ENCASED_FAN_PROPELLER.getDefaultState()
                .withProperty(RenderPartBlock.FACING, facing.getOpposite());
        BlockPos renderPos = tile.getContraptionRenderPos() != null
                ? tile.getContraptionRenderPos() : tile.getPos();
        float speed = tile.getSpeed() * 5.0F;
        if (speed > 0) {
            speed = MathHelper.clamp(speed, 80.0F, 1280.0F);
        } else if (speed < 0) {
            speed = MathHelper.clamp(speed, -1280.0F, -80.0F);
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, shaft, facing.getAxis(),
                RotatingModelRenderer.kineticAngle(tile, partialTicks));
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, propeller, facing.getAxis(),
                RotatingModelRenderer.kineticAngle(tile.getWorld(), partialTicks, speed));
        GlStateManager.popMatrix();
    }
}
