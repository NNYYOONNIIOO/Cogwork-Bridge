package com.cogwork.cogworkbridge.client.render;
import com.cogwork.cogworkbridge.content.redstone.LecternControllerBlock;
import com.cogwork.cogworkbridge.content.redstone.LecternControllerTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
public final class LecternControllerRenderer extends TileEntitySpecialRenderer<LecternControllerTileEntity> {
    @Override public void render(LecternControllerTileEntity t,double x,double y,double z,float partial,int stage,float alpha){
        if(t.getWorld()==null || t.controller().isEmpty() || !(t.getWorld().getBlockState(t.getPos()).getBlock() instanceof LecternControllerBlock))return;
        var facing=t.getWorld().getBlockState(t.getPos()).getValue(LecternControllerBlock.FACING);
        GlStateManager.pushMatrix();GlStateManager.translate(x+.5,y+1.13,z+.5);GlStateManager.rotate(-facing.getHorizontalAngle(),0,1,0);GlStateManager.rotate(-22.5F,1,0,0);GlStateManager.scale(.55,.55,.55);
        Minecraft.getMinecraft().getRenderItem().renderItem(t.controller(),ItemCameraTransforms.TransformType.FIXED);GlStateManager.popMatrix();
    }
}
