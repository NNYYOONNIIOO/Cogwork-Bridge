package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.logistics.funnel.FunnelBlock;
import com.cogwork.cogworkbridge.content.logistics.funnel.FunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.content.logistics.depot.DepotTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

public final class FunnelRenderer extends TileEntitySpecialRenderer<FunnelTileEntity> {
    @Override public void render(FunnelTileEntity tile, double x, double y, double z, float partialTicks,
                                 int destroyStage, float alpha) {
        if (tile.getWorld() == null) return;
        ItemStack filter = tile.getFilter();
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof FunnelBlock funnel)) return;
        EnumFacing facing = state.getValue(FunnelBlock.FACING);
        if (facing.getAxis().isHorizontal()) {
            TileEntity surface = tile.getWorld().getTileEntity(tile.getPos().down());
            boolean belt = surface instanceof BeltTileEntity
                    || tile.getWorld().getTileEntity(tile.getPos().down()) instanceof DepotTileEntity;
            double flapOffset = 1 / 16D;
            if (surface instanceof BeltTileEntity beltTile) {
                EnumFacing.Axis pathAxis = beltTile.step().getX() == 0 ? EnumFacing.Axis.Z : EnumFacing.Axis.X;
                flapOffset = facing.getAxis() == pathAxis ? (tile.isExtended() ? -8 / 16D : 0) : 2 / 16D;
            }
            GlStateManager.pushMatrix(); GlStateManager.translate(x, y, z);
            LogisticsFlapRenderer.render((belt ? AllBlocks.BELT_FUNNEL_FLAP : AllBlocks.FUNNEL_FLAP).getDefaultState(),
                    facing, tile.flapValue(partialTicks), false, flapOffset);
            GlStateManager.popMatrix();
        }
        if (filter.isEmpty() || !funnel.isBrass()) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y + 0.5D, z + 0.5D);
        RenderHelper.enableStandardItemLighting();
        for (EnumFacing direction : EnumFacing.HORIZONTALS) {
            if (facing.getAxis().isHorizontal() && direction != facing) continue;
            GlStateManager.pushMatrix();
            GlStateManager.rotate(-direction.getHorizontalAngle(), 0, 1, 0);
            if (facing.getAxis().isHorizontal()) {
                TileEntity surface = tile.getWorld().getTileEntity(tile.getPos().down());
                boolean sameAxis = surface instanceof BeltTileEntity belt
                        && (belt.step().getX() != 0 ? EnumFacing.Axis.X : EnumFacing.Axis.Z) == facing.getAxis();
                double filterY = sameAxis ? (tile.isExtended() ? 15.5D : 13D) : 12.2D;
                double filterZ = sameAxis ? (tile.isExtended() ? 13D : 7.5D) : 8.55D;
                GlStateManager.translate(0, filterY / 16.0D - 0.5D, filterZ / 16.0D - 0.5D);
                ItemStack display = filter.copy();
                if (tile.getAmount() > 1) display.setCount(tile.getAmount());
                renderFilter(display, tile.getWorld(), direction,
                        sameAxis && tile.isExtended() ? 90F : !sameAxis ? -22.5F : 0F);
            } else {
                GlStateManager.translate(0, (facing == EnumFacing.DOWN ? 14 : 2) / 16.0D - 0.5D,
                        15.5D / 16.0D - 0.5D);
                ItemStack display = filter.copy();
                if (tile.getAmount() > 1) display.setCount(tile.getAmount());
                renderFilter(display, tile.getWorld(), direction, 0F);
            }
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    /** Render a filter on a value slot after translating to that slot's centre. */
    static void renderFilter(ItemStack display, World world, EnumFacing side, float tilt) {
        GlStateManager.rotate(side.getHorizontalAngle(), 0, 1, 0);
        GlStateManager.rotate(side.getHorizontalAngle() + 180.0F
                + (side.getAxis() == EnumFacing.Axis.X ? 180.0F : 0.0F), 0, 1, 0);
        if (tilt != 0F) GlStateManager.rotate(tilt, 1, 0, 0);
        boolean blockItem = Minecraft.getMinecraft().getRenderItem()
                .getItemModelWithOverrides(display, world, null).isGui3d();
        float itemScale = 0.5F * (blockItem ? 1.0F + 1F / 64F : 0.5F + 1F / 64F);
        GlStateManager.scale(itemScale, itemScale, itemScale);
        if (!blockItem) GlStateManager.translate(0, 0, -0.15D);
        Minecraft.getMinecraft().getRenderItem().renderItem(display, ItemCameraTransforms.TransformType.FIXED);
    }
}
