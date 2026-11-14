package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.deployer.DeployerBlock;
import com.cogwork.cogworkbridge.content.kinetics.deployer.DeployerTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemBlock;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;

public final class DeployerRenderer extends TileEntitySpecialRenderer<DeployerTileEntity> {
    @Override
    public void render(DeployerTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof DeployerBlock)) return;
        renderFilter(tile, state, x, y, z);
        EnumFacing facing = state.getValue(DeployerBlock.FACING);
        EnumFacing.Axis rotationAxis = DeployerBlock.rotationAxis(facing,
                state.getValue(DeployerBlock.AXIS_ALONG_FIRST_COORDINATE));
        float extension = tile.getExtension(partialTicks);
        IBlockState pole = part(AllBlocks.DEPLOYER_POLE);
        IBlockState hand = tile.getMode() == DeployerTileEntity.Mode.PUNCH
                ? part(AllBlocks.DEPLOYER_HAND_PUNCHING)
                : tile.getHeldItem().isEmpty()
                ? part(AllBlocks.DEPLOYER_HAND_POINTING)
                : part(AllBlocks.DEPLOYER_HAND_HOLDING);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        IBlockState shaft = AllBlocks.SHAFT.getDefaultState().withProperty(ShaftBlock.AXIS, rotationAxis);
        RotatingModelRenderer.renderRotated(tile.getWorld(), tile.getPos(), shaft, rotationAxis,
                RotatingModelRenderer.kineticAngle(tile, partialTicks));
        GlStateManager.popMatrix();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        Vec3d offset = new Vec3d(facing.getDirectionVec()).scale(extension);
        GlStateManager.translate(offset.x, offset.y, offset.z);
        orient(state, true);
        FastStateRendering.getInstance().renderFast(pole);
        GlStateManager.popMatrix();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + offset.x, y + offset.y, z + offset.z);
        orient(state, false);
        FastStateRendering.getInstance().renderFast(hand);
        GlStateManager.popMatrix();

        ItemStack held = tile.getHeldItem();
        if (held.isEmpty()) return;
        boolean punching = tile.getMode() == DeployerTileEntity.Mode.PUNCH;
        boolean blockItem = held.getItem() instanceof ItemBlock && Minecraft.getMinecraft().getRenderItem()
                .getItemModelWithOverrides(held, tile.getWorld(), null).isGui3d();
        boolean display = facing == EnumFacing.UP && tile.getSpeed() == 0 && !punching;
        float yRot = switch (facing) {
            case SOUTH -> 180F; case WEST -> 450F; case NORTH -> 360F; case EAST -> 270F; default -> 180F;
        };
        float xRot = facing == EnumFacing.UP ? 90F : facing == EnumFacing.DOWN ? 270F : 0F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + offset.x + .5D, y + offset.y + .5D, z + offset.z + .5D);
        GlStateManager.rotate(yRot, 0, 1, 0);
        if (!display) {
            GlStateManager.rotate(xRot, 1, 0, 0);
            GlStateManager.translate(0, 0, -11D / 16D);
        }
        if (punching) GlStateManager.translate(0, 1D / 8D, -1D / 16D);
        ItemCameraTransforms.TransformType transform;
        if (display) {
            GlStateManager.translate(0, blockItem ? 9D / 16D : 11D / 16D, 0);
            float scale = blockItem ? 1.25F : 1F;
            GlStateManager.scale(scale, scale, scale);
            GlStateManager.rotate((tile.getWorld().getTotalWorldTime() + partialTicks) % 360F, 0, 1, 0);
            transform = ItemCameraTransforms.TransformType.GROUND;
        } else {
            float scale = punching ? .75F : blockItem ? .75F - 1F / 64F : .5F;
            GlStateManager.scale(scale, scale, scale);
            transform = punching ? ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND
                    : ItemCameraTransforms.TransformType.FIXED;
        }
        RenderHelper.enableStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItem(held, transform);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    private static void renderFilter(DeployerTileEntity tile, IBlockState state, double x, double y, double z) {
        ItemStack filter = tile.getFilter();
        if (filter.isEmpty()) return;
        RenderHelper.enableStandardItemLighting();
        for (EnumFacing side : EnumFacing.values()) {
            if (!DeployerBlock.isFilterSide(state, side)) continue;
            Vec3d center = DeployerBlock.filterCenter(state, side);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + center.x, y + center.y, z + center.z);
            if (side == EnumFacing.UP) GlStateManager.rotate(-90F, 1, 0, 0);
            else if (side == EnumFacing.DOWN) GlStateManager.rotate(90F, 1, 0, 0);
            else GlStateManager.rotate(-side.getHorizontalAngle(), 0, 1, 0);
            boolean block = Minecraft.getMinecraft().getRenderItem().getItemModelWithOverrides(filter, tile.getWorld(), null).isGui3d();
            GlStateManager.translate(0, 0, block ? .002D : .035D);
            float scale = .5F * (block ? 1F + 1F / 64F : .5F + 1F / 64F);
            GlStateManager.scale(scale, scale, scale);
            Minecraft.getMinecraft().getRenderItem().renderItem(filter, ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
    }

    private static IBlockState part(RenderPartBlock block) {
        return block.getDefaultState().withProperty(RenderPartBlock.FACING, EnumFacing.UP);
    }

    private static void orient(IBlockState state, boolean axisDirectionMatters) {
        EnumFacing facing = state.getValue(DeployerBlock.FACING);
        float yRot = switch (facing) {
            case SOUTH -> 0.0F;
            case WEST -> 270.0F;
            case NORTH -> 180.0F;
            case EAST -> 90.0F;
            default -> 0.0F;
        };
        float xRot = facing == EnumFacing.UP ? 270.0F
                : facing == EnumFacing.DOWN ? 90.0F : 0.0F;
        boolean alongFirst = state.getValue(DeployerBlock.AXIS_ALONG_FIRST_COORDINATE)
                == EnumFacing.Axis.X;
        float zRot = axisDirectionMatters
                && (alongFirst ^ (facing.getAxis() == EnumFacing.Axis.Z)) ? 90.0F : 0.0F;

        GlStateManager.translate(0.5D, 0.5D, 0.5D);
        GlStateManager.rotate(yRot, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(xRot, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(zRot, 0.0F, 0.0F, 1.0F);
        GlStateManager.translate(-0.5D, -0.5D, -0.5D);
    }
}
