package com.cogwork.cogworkbridge.client.render;
import com.cogwork.cogworkbridge.content.fluids.whistle.*;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
public final class WhistleExtensionRenderer extends TileEntitySpecialRenderer<WhistleExtensionTileEntity> {
    @Override public void render(WhistleExtensionTileEntity t,double x,double y,double z,float partial,int destroy,float alpha){
        if(t.getWorld()==null)return;var root=WhistleExtensionBlock.root(t.getWorld(),t.getPos());
        if(!(t.getWorld().getTileEntity(root) instanceof SteamWhistleTileEntity w))return;
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);int size=w.size();
        FastStateRendering.getInstance().renderFast(AllBlocks.WHISTLE_EXTENSION_PART[size*3+(t.isDouble()?1:0)].getDefaultState());
        if(t.isDouble() && !(t.getWorld().getTileEntity(t.getPos().up()) instanceof WhistleExtensionTileEntity)) FastStateRendering.getInstance().renderFast(AllBlocks.WHISTLE_EXTENSION_PART[size*3+2].getDefaultState());
        GlStateManager.popMatrix();
    }
}
