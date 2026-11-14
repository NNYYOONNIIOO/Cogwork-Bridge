package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.logistics.tunnel.BeltTunnelBlock;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BeltTunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BrassTunnelTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

public final class TunnelRenderer extends TileEntitySpecialRenderer<BeltTunnelTileEntity> {
    @Override public void render(BeltTunnelTileEntity tunnel, double x, double y, double z, float partial, int stage, float alpha) {
        if (tunnel.getWorld() == null) return;
        IBlockState state = tunnel.getWorld().getBlockState(tunnel.getPos());
        if (!(state.getBlock() instanceof BeltTunnelBlock)) return;
        GlStateManager.pushMatrix(); GlStateManager.translate(x, y, z);
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            if (side.getAxis() == state.getValue(BeltTunnelBlock.AXIS)
                    || BeltTunnelBlock.hasExit(tunnel.getWorld(), tunnel.getPos().down(), side))
                LogisticsFlapRenderer.render(AllBlocks.BELT_TUNNEL_FLAP.getDefaultState(), side, tunnel.flapValue(side, partial), true, 0);
        }
        GlStateManager.popMatrix();
        if (!(tunnel instanceof BrassTunnelTileEntity brass)) return;
        ItemStack[] filters = brass.filters();
        GlStateManager.pushMatrix(); GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
        RenderHelper.enableStandardItemLighting();
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            ItemStack filter = filters[side.getIndex()]; if (filter.isEmpty()) continue;
            GlStateManager.pushMatrix(); GlStateManager.rotate(-side.getHorizontalAngle(), 0, 1, 0);
            GlStateManager.translate(0, 13 / 16.0D - 0.5D, 15.5D / 16D - 0.5D);
            FunnelRenderer.renderFilter(filter, tunnel.getWorld(), side, 0F);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting(); GlStateManager.popMatrix();
    }
}
