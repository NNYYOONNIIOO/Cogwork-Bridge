package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.trains.track.TrackCurve;
import com.cogwork.cogworkbridge.content.trains.track.TrackTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid=Reference.MOD_ID,value=Side.CLIENT)
public final class TrackCurveRenderer extends TileEntitySpecialRenderer<TrackTileEntity> {
    private static IBakedModel left,right,tie;
    private static IModel model(String name) {
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create","block/track/"+name),
                "Missing railway track " + name);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event){
        for(String part:new String[]{"segment_left","segment_right","tie"})
            for(ResourceLocation texture:model(part).getTextures())event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas=Minecraft.getMinecraft().getTextureMapBlocks();
        left=model("segment_left").bake(ModelRotation.X0_Y0,DefaultVertexFormats.BLOCK,s->atlas.getAtlasSprite(s.toString()));
        right=model("segment_right").bake(ModelRotation.X0_Y0,DefaultVertexFormats.BLOCK,s->atlas.getAtlasSprite(s.toString()));
        tie=model("tie").bake(ModelRotation.X0_Y0,DefaultVertexFormats.BLOCK,s->atlas.getAtlasSprite(s.toString()));
    }
    @Override public void render(TrackTileEntity tile,double x,double y,double z,float partial,int destroy,float alpha) {
        if(tile.getWorld()==null)return;
        Entity camera=Minecraft.getMinecraft().getRenderViewEntity();
        if(camera==null)return;
        Frustum frustum=new Frustum();
        frustum.setPosition(camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial,
                camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial,
                camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial);
        float oldLightX=OpenGlHelper.lastBrightnessX,oldLightY=OpenGlHelper.lastBrightnessY;
        boolean wasLighting=GL11.glIsEnabled(GL11.GL_LIGHTING);
        GlStateManager.disableLighting();
        try {
        for(TrackCurve curve:tile.connections()) {
            if(!renderFrom(tile,curve,frustum))continue;
            int segments=Math.max(2,(int)(curve.length()*2));
            Vec3d previous=curve.point(0), previousNormal=normal(curve.tangent(0));
            for(int i=1;i<=segments;i++) {
                double t=curve.parameterAtFraction(i/(double)segments);
                Vec3d next=curve.point(t), nextNormal=normal(curve.tangent(t));
                Vec3d middleDiff=next.subtract(previous);
                int light=tile.getWorld().getCombinedLight(new BlockPos(next.x,next.y+.25D,next.z),0);
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light & 65535,light >>> 16);
                GlStateManager.color(1,1,1,alpha);
                if(middleDiff.lengthSquared()>1E-10D) {
                    transform(tile,x,y,z,previous,middleDiff);
                    GlStateManager.translate(-.5D,-2D/16D-1D/256D,0);
                    FastStateRendering.getInstance().renderFast(tie,Blocks.AIR.getDefaultState());
                    GlStateManager.popMatrix();
                }
                for(int direction=1;direction>=-1;direction-=2) {
                    Vec3d railStart=previous.add(previousNormal.scale(.965D*direction));
                    Vec3d railEnd=next.add(nextNormal.scale(.965D*direction));
                    Vec3d difference=railEnd.subtract(railStart);
                    double length=difference.length();
                    if(length<.00001D)continue;
                    transform(tile,x,y,z,railStart,difference);
                    GlStateManager.translate(0,-2D/16D-1D/256D,-1D/32D);
                    GlStateManager.scale(1,1,length*(i==segments?2.2D:2.1D));
                    FastStateRendering.getInstance().renderFast(direction==1?left:right,Blocks.AIR.getDefaultState());
                    GlStateManager.popMatrix();
                }
                previous=next;previousNormal=nextNormal;
            }
        }
        } finally {
            if(wasLighting)GlStateManager.enableLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldLightX,oldLightY);
            GlStateManager.color(1,1,1,1);
        }
    }

    @Override public boolean isGlobalRenderer(TrackTileEntity tile){return !tile.connections().isEmpty();}

    /** Draw once from whichever loaded endpoint is on screen. */
    private static boolean renderFrom(TrackTileEntity tile,TrackCurve curve,Frustum frustum) {
        boolean firstLoaded=tile.getWorld().isBlockLoaded(curve.first)
                && tile.getWorld().getTileEntity(curve.first) instanceof TrackTileEntity first
                && first.has(curve.second);
        boolean secondLoaded=tile.getWorld().isBlockLoaded(curve.second)
                && tile.getWorld().getTileEntity(curve.second) instanceof TrackTileEntity second
                && second.has(curve.first);
        if(!firstLoaded)return tile.getPos().equals(curve.second);
        if(!secondLoaded)return tile.getPos().equals(curve.first);
        boolean firstVisible=frustum.isBoundingBoxInFrustum(new AxisAlignedBB(curve.first).grow(1.5D));
        boolean secondVisible=frustum.isBoundingBoxInFrustum(new AxisAlignedBB(curve.second).grow(1.5D));
        return tile.getPos().equals(!firstVisible&&secondVisible?curve.second:curve.first);
    }

    private static Vec3d normal(Vec3d tangent) {
        Vec3d normal=new Vec3d(tangent.z,0,-tangent.x);
        return normal.lengthSquared()<1E-10D?new Vec3d(1,0,0):normal.normalize();
    }

    private static void transform(TrackTileEntity tile,double x,double y,double z,Vec3d point,Vec3d difference) {
        double horizontal=Math.sqrt(difference.x*difference.x+difference.z*difference.z);
        float yaw=(float)Math.toDegrees(Math.atan2(difference.x,difference.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(difference.y,horizontal));
        GlStateManager.pushMatrix();
        GlStateManager.translate(x+point.x-tile.getPos().getX(),y+point.y-tile.getPos().getY()+3D/16D,z+point.z-tile.getPos().getZ());
        GlStateManager.rotate(yaw,0,1,0);
        GlStateManager.rotate(pitch,1,0,0);
    }
}
