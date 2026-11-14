package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.steam.SteamEngineBlock;
import com.cogwork.cogworkbridge.content.kinetics.steam.SteamEngineTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.steam.PoweredShaftTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

public final class SteamEngineRenderer extends TileEntitySpecialRenderer<SteamEngineTileEntity> {
    @Override public void render(SteamEngineTileEntity engine, double x, double y, double z, float partial, int destroy, float alpha) {
        if (engine.getWorld() == null) return;
        IBlockState state = engine.getWorld().getBlockState(engine.getPos());
        if (!(state.getBlock() instanceof SteamEngineBlock)) return;
        EnumFacing facing = state.getValue(SteamEngineBlock.FACING);
        var target = engine.getWorld().getTileEntity(engine.shaftPos());
        if (!(target instanceof PoweredShaftTileEntity shaft)) return;
        IBlockState shaftState = engine.getWorld().getBlockState(engine.shaftPos());
        EnumFacing.Axis axis = shaftState.getValue(KineticBlock.AXIS);
        double angle = Math.toRadians(RotatingModelRenderer.kineticAngle(shaft, partial));
        if (axis != EnumFacing.Axis.Y && (facing.getAxis() == EnumFacing.Axis.X ^ facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE)) angle = -angle;
        if (axis == EnumFacing.Axis.X && facing == EnumFacing.DOWN) angle = -angle;
        double piston = .375D * Math.sin(angle) - Math.sqrt(.875D * .875D - .375D * .375D * Math.cos(angle) * Math.cos(angle));
        double distance = Math.abs(piston - .375D * Math.sin(angle));
        double linkage = Math.acos(Math.max(-1, Math.min(1, distance / .875D))) * (Math.cos(angle) >= 0 ? 1 : -1);
        boolean roll = facing.getAxis().isHorizontal() && axis == EnumFacing.Axis.Y || facing.getAxis() == EnumFacing.Axis.Y && axis == EnumFacing.Axis.Z;
        GlStateManager.pushMatrix(); GlStateManager.translate(x,y,z); GlStateManager.translate(.5,.5,.5);
        if (facing.getAxis().isHorizontal()) GlStateManager.rotate(modelYaw(facing), 0,1,0);
        GlStateManager.rotate(facing == EnumFacing.UP ? 0 : facing == EnumFacing.DOWN ? 180 : 90, 1,0,0);
        if (roll) GlStateManager.rotate(-90,0,1,0);
        GlStateManager.translate(-.5,-.5,-.5);
        GlStateManager.pushMatrix(); GlStateManager.translate(0,piston + 1.25,0);
        FastStateRendering.getInstance().renderFast(AllBlocks.STEAM_ENGINE_PISTON.getDefaultState()); GlStateManager.popMatrix();
        GlStateManager.pushMatrix(); GlStateManager.translate(0,1 + piston + 1.25,0); GlStateManager.translate(0,.25,.5);
        GlStateManager.rotate((float)Math.toDegrees(linkage),1,0,0); GlStateManager.translate(0,-.25,-.5);
        FastStateRendering.getInstance().renderFast(AllBlocks.STEAM_ENGINE_LINKAGE.getDefaultState()); GlStateManager.popMatrix();
        GlStateManager.pushMatrix(); GlStateManager.translate(0,2,0); GlStateManager.translate(.5,.5,.5);
        GlStateManager.rotate((float)-Math.toDegrees(angle + Math.PI/2),1,0,0); GlStateManager.translate(-.5,-.5,-.5);
        FastStateRendering.getInstance().renderFast(AllBlocks.STEAM_ENGINE_CONNECTOR.getDefaultState()); GlStateManager.popMatrix();
        GlStateManager.popMatrix();
    }

    private static float modelYaw(EnumFacing facing) {
        return switch (facing) {
            case NORTH -> 180.0F;
            case SOUTH -> 0.0F;
            case EAST -> 90.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }
}
