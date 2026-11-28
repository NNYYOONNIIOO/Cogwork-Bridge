package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.client.render.FastStateRendering;
import com.cogwork.cogworkbridge.content.trains.track.BogeyBlock;
import com.cogwork.cogworkbridge.content.trains.track.RailwayTargetItem;
import com.cogwork.cogworkbridge.content.trains.track.RailwayTargetTile;
import com.cogwork.cogworkbridge.content.trains.track.TrackBlock;
import com.cogwork.cogworkbridge.content.trains.track.TrackShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Upstream rail overlays for target selection, bound devices and bogey assembly. */
@Mod.EventBusSubscriber(modid=Reference.MOD_ID,value=Side.CLIENT)
public final class StationAssemblyPreview {
    private static final String[] PARTS={"station","signal","signal_dual","observer","assembling"};
    private static final Map<String,IBakedModel> MODELS=new HashMap<>();
    private StationAssemblyPreview() { }
    private static IModel model(String name) {
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create","block/track_overlay/"+name),
                "Missing railway overlay "+name);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for(String name:PARTS)for(ResourceLocation texture:model(name).getTextures())event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas=Minecraft.getMinecraft().getTextureMapBlocks();MODELS.clear();
        for(String name:PARTS)MODELS.put(name,model(name).bake(ModelRotation.X0_Y0,DefaultVertexFormats.BLOCK,
                texture->atlas.getAtlasSprite(texture.toString())));
    }
    @SubscribeEvent public static void render(RenderWorldLastEvent event) {
        Minecraft mc=Minecraft.getMinecraft();
        Entity camera=mc.getRenderViewEntity();
        if(mc.world==null||mc.player==null||camera==null)return;
        float partial=event.getPartialTicks();
        double x=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial;
        double y=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial;
        double z=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial;
        boolean lighting=GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        float lightX=OpenGlHelper.lastBrightnessX,lightY=OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();GlStateManager.translate(-x,-y,-z);
        GlStateManager.enableTexture2D();GlStateManager.disableLighting();GlStateManager.enableDepth();
        GlStateManager.enableAlpha();GlStateManager.depthMask(true);GlStateManager.color(1,1,1,1);
        try {
            for(TileEntity tile:mc.world.loadedTileEntityList) {
                if(!(tile instanceof RailwayTargetTile target)||!target.hasValidTarget()
                        ||target.getDistanceSq(camera.posX,camera.posY,camera.posZ)>256D*256D)continue;
                if(target instanceof RailwayTargetTile.Station station&&station.assembling()) {
                    List<BlockPos> tracks=station.assemblyTracks();
                    Set<BlockPos> available=new HashSet<>(station.assemblySlots());
                    for(BlockPos rail:tracks) {
                        boolean occupied=mc.world.getBlockState(rail.up()).getBlock() instanceof BogeyBlock;
                        if(occupied||available.contains(rail))draw(mc,rail,target.front(),"assembling",occupied?0xCAFF96:0x96B5FF,1F);
                    }
                } else {
                    String kind=target instanceof RailwayTargetTile.Station?"station":target instanceof RailwayTargetTile.Observer?"observer"
                            :((RailwayTargetTile.Signal)target).crossSignal()?"signal_dual":"signal";
                    draw(mc,target.target(),target.front(),kind,0xFFFFFF,1F);
                }
            }
            if(mc.currentScreen==null&&!mc.player.isSneaking())selection(mc);
        } finally {
            GlStateManager.popMatrix();GlStateManager.depthMask(depthMask);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,lightX,lightY);
            if(texture)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();
            if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
            if(alpha)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();
            GlStateManager.color(1,1,1,1);
        }
    }
    private static void selection(Minecraft mc) {
        ItemStack held=mc.player.getHeldItemMainhand();
        if(!(held.getItem() instanceof RailwayTargetItem))held=mc.player.getHeldItemOffhand();
        if(!(held.getItem() instanceof RailwayTargetItem item))return;
        BlockPos pos=RailwayTargetItem.selectedTrack(held,mc.world);
        boolean front=RailwayTargetItem.selectedFront(held);
        if(pos==null) {
            RayTraceResult hit=mc.objectMouseOver;
            if(hit==null||hit.typeOfHit!=RayTraceResult.Type.BLOCK)return;
            pos=hit.getBlockPos();IBlockState state=mc.world.getBlockState(pos);
            if(!(state.getBlock() instanceof TrackBlock))return;
            TrackShape shape=state.getValue(TrackBlock.SHAPE);
            front=shape.x*mc.player.getLookVec().x+shape.z*mc.player.getLookVec().z<0;
        }
        draw(mc,pos,front,item.kind(),0xFFFFFF,1F+1F/16F);
    }
    private static void draw(Minecraft mc,BlockPos pos,boolean front,String part,int color,float scale) {
        if(!mc.world.isBlockLoaded(pos))return;
        IBlockState state=mc.world.getBlockState(pos);
        if(!(state.getBlock() instanceof TrackBlock))return;
        TrackShape shape=state.getValue(TrackBlock.SHAPE);if(shape.junction())return;
        int sign=front?1:-1;
        double yaw=Math.toDegrees(Math.atan2(shape.x*sign,shape.z*sign));
        double pitch=-Math.toDegrees(Math.atan2(shape.y*sign,Math.sqrt(shape.x*shape.x+shape.z*shape.z)));
        int light=mc.world.getCombinedLight(pos,0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>>16);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(pos.getX()+.5D,pos.getY()+.501D,pos.getZ()+.5D);
            GlStateManager.rotate((float)yaw,0,1,0);GlStateManager.rotate((float)pitch,1,0,0);
            GlStateManager.translate(-.5D,-.5D+(shape.slope()?7D/16D:0D),-.5D+(shape.slope()?sign*2.5D/16D:0D));
            GlStateManager.translate(.5D,0,.5D);GlStateManager.scale(scale,scale,scale);GlStateManager.translate(-.5D,0,-.5D);
            FastStateRendering.getInstance().renderTinted(MODELS.get(part),Blocks.AIR.getDefaultState(),color);
        } finally { GlStateManager.popMatrix(); }
    }
}
