package com.cogwork.cogworkbridge.client.render;
import com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleBlock;
import com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
public final class SteamWhistleRenderer extends TileEntitySpecialRenderer<SteamWhistleTileEntity> {
    @Override public void render(SteamWhistleTileEntity t,double x,double y,double z,float partial,int destroy,float alpha){
        if(t.getWorld()==null || !(t.getWorld().getBlockState(t.getPos()).getBlock() instanceof SteamWhistleBlock))return;
        var state=t.getWorld().getBlockState(t.getPos());EnumFacing facing=state.getValue(SteamWhistleBlock.FACING);boolean wall=state.getValue(SteamWhistleBlock.WALL);
        GlStateManager.pushMatrix();GlStateManager.translate(x+.5,y,z+.5);GlStateManager.rotate(switch(facing){case NORTH->180F;case EAST->270F;case WEST->90F;default->0F;},0,1,0);GlStateManager.translate(-.5,0,-.5);
        FastStateRendering.getInstance().renderFast(AllBlocks.WHISTLE_BASE[t.size()*4+(wall?2:0)+(t.powered()?1:0)].getDefaultState());
        float offset=t.animation(partial);
        if(t.sounding() && t.animation(1F)>.5F) {
            float progress=(t.getWorld().getTotalWorldTime()+partial)/8F;
            offset-=(float)Math.sin(progress*2F*Math.PI*(4-t.size()))/16F;
        }
        GlStateManager.translate(0,offset*.25F,0);FastStateRendering.getInstance().renderFast(AllBlocks.WHISTLE_MOUTH[t.size()].getDefaultState());GlStateManager.popMatrix();
    }
}
