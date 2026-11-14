package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.trains.track.RailwayTargetTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

/** Show available train-assembly track positions while a station's flag is raised. */
@Mod.EventBusSubscriber(modid=Reference.MOD_ID,value=Side.CLIENT)
public final class StationAssemblyPreview {
    private StationAssemblyPreview() { }
    @SubscribeEvent public static void render(RenderWorldLastEvent event) {
        Minecraft mc=Minecraft.getMinecraft();
        Entity camera=mc.getRenderViewEntity();
        if(mc.world==null||mc.player==null||camera==null||mc.currentScreen!=null)return;
        float partial=event.getPartialTicks();
        double x=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial;
        double y=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial;
        double z=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial;
        boolean blending=GL11.glIsEnabled(GL11.GL_BLEND),lighting=GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        float line=GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        GlStateManager.disableTexture2D();GlStateManager.disableLighting();GlStateManager.enableDepth();
        GlStateManager.depthMask(false);GlStateManager.glLineWidth(2F);
        try {
            for(TileEntity tile:mc.world.loadedTileEntityList) {
                if(!(tile instanceof RailwayTargetTile.Station station)||!station.assembling()
                        ||station.getDistanceSq(camera.posX,camera.posY,camera.posZ)>256D*256D)continue;
                for(BlockPos pos:station.assemblySlots()) {
                    AxisAlignedBB outline=new AxisAlignedBB(pos.getX()+.10D-x,pos.getY()+.145D-y,pos.getZ()+.10D-z,
                            pos.getX()+.90D-x,pos.getY()+.16D-y,pos.getZ()+.90D-z);
                    RenderGlobal.drawSelectionBoundingBox(outline,.26F,.70F,1F,.9F);
                }
            }
        } finally {
            GlStateManager.glLineWidth(line);GlStateManager.depthMask(depthMask);
            if(texture)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();
            if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
            if(!blending)GlStateManager.disableBlend();
            GlStateManager.color(1,1,1,1);
        }
    }
}
