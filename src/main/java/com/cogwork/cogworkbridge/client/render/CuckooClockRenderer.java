package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.clock.CuckooClockBlock;
import com.cogwork.cogworkbridge.content.kinetics.clock.CuckooClockTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

public final class CuckooClockRenderer extends TileEntitySpecialRenderer<CuckooClockTileEntity> {
    @Override public void render(CuckooClockTileEntity tile,double x,double y,double z,float partial,int stage,float alpha) {
        if(tile.getWorld()==null) return;
        IBlockState s=tile.getContraptionRenderState()==null?tile.getWorld().getBlockState(tile.getPos()):tile.getContraptionRenderState();
        if(!(s.getBlock() instanceof CuckooClockBlock)) return;
        EnumFacing face=s.getValue(CuckooClockBlock.FACING);
        GlStateManager.pushMatrix(); GlStateManager.translate(x,y,z);
        // This half-shaft model occupies the side opposite to its facing property.
        RotatingModelRenderer.renderRotated(tile.getWorld(),tile.getPos(),AllBlocks.MECHANICAL_BEARING_SHAFT.getDefaultState().withProperty(RenderPartBlock.FACING,face),face.getAxis(),RotatingModelRenderer.kineticAngle(tile,partial));
        GlStateManager.translate(.5,.5,.5);
        GlStateManager.rotate(switch(face){case NORTH -> -90F;case EAST -> 180F;case SOUTH -> 90F;default -> 0F;},0,1,0);
        GlStateManager.translate(-.5,-.5,-.5);
        hand(AllBlocks.CUCKOO_HOUR,tile.hour(partial)); hand(AllBlocks.CUCKOO_MINUTE,tile.minute(partial));
        float angle=0; int type=tile.animation(); float value=tile.progress(partial); int step=type==3?3:15;
        if(type!=0) for(int phase=30;phase<=60;phase+=step) {
            float local=value-phase; float span=step/3F;
            if(local>=-span && local<0) angle=135F*(local+span)/span;
            else if(local>=0 && local<span) angle=135F;
            else if(local>=span && local<2*span) angle=135F*(2*span-local)/span;
        }
        door(AllBlocks.CUCKOO_LEFT,angle,true); door(AllBlocks.CUCKOO_RIGHT,angle,false);
        if(type!=0 && angle>0) { GlStateManager.pushMatrix(); GlStateManager.translate(.625-angle/135F*.5,0,0); part(type==1?AllBlocks.CUCKOO_PIG:AllBlocks.CUCKOO_CREEPER); GlStateManager.popMatrix(); }
        GlStateManager.popMatrix();
    }
    private void hand(Block part,float angle) { GlStateManager.pushMatrix();GlStateManager.translate(2/16D,6/16D,.5);GlStateManager.rotate(angle,1,0,0);GlStateManager.translate(-2/16D,-6/16D,-.5);part(part);GlStateManager.popMatrix(); }
    private void door(Block part,float angle,boolean left) { double pivot=(left?6:10)/16D;GlStateManager.pushMatrix();GlStateManager.translate(2/16D,0,pivot);GlStateManager.rotate(left?-angle:angle,0,1,0);GlStateManager.translate(-2/16D,0,-pivot);part(part);GlStateManager.popMatrix(); }
    private void part(Block part) { FastStateRendering.getInstance().renderFast(part.getDefaultState()); }
}
