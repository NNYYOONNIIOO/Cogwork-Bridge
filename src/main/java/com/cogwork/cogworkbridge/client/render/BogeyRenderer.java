package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.trains.track.BogeyBlock;
import com.cogwork.cogworkbridge.content.trains.track.BogeyTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid=Reference.MOD_ID,value=Side.CLIENT)
public final class BogeyRenderer extends TileEntitySpecialRenderer<BogeyTile> {
    private static final String[] PARTS={"bogey_frame","bogey_wheel","bogey_drive","bogey_drive_belt",
            "bogey_drive_piston","bogey_drive_wheel","bogey_drive_wheel_pin"};
    private static final Map<String,IBakedModel> MODELS=new HashMap<>();
    private static IModel model(String part) {
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create","block/track/bogey/"+part),
                "Missing bogey model "+part);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event){
        for(String part:PARTS)for(ResourceLocation texture:model(part).getTextures())event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event){
        var atlas=Minecraft.getMinecraft().getTextureMapBlocks();MODELS.clear();
        for(String part:PARTS)MODELS.put(part,model(part).bake(ModelRotation.X0_Y0,DefaultVertexFormats.BLOCK,
                s->atlas.getAtlasSprite(s.toString())));
    }
    private static void part(String name){FastStateRendering.getInstance().renderFast(MODELS.get(name),Blocks.AIR.getDefaultState());}
    @Override public void render(BogeyTile tile,double x,double y,double z,float partial,int destroy,float alpha){
        if(!(tile.getBlockType() instanceof BogeyBlock bogey))return;
        float wheelAngle=tile.wheelAngle(partial);
        GlStateManager.pushMatrix();GlStateManager.translate(x+.5D,y+.5D,z+.5D);
        if(tile.getBlockType().getStateFromMeta(tile.getBlockMetadata()).getValue(BogeyBlock.AXIS)==EnumFacing.Axis.X)
            GlStateManager.rotate(90F,0,1,0);
        // Upstream BogeyStyle places the bogey meshes below the block anchor.
        GlStateManager.translate(0D,-1.5D-1D/128D,0D);
        if(!bogey.isLarge()) {
            part("bogey_frame");
            for(int side:new int[]{-1,1}){
                GlStateManager.pushMatrix();GlStateManager.translate(0,.75D,side);
                GlStateManager.rotate(wheelAngle,1,0,0);part("bogey_wheel");GlStateManager.popMatrix();
            }
        } else {
            part("bogey_drive");part("bogey_drive_belt");
            GlStateManager.pushMatrix();GlStateManager.translate(0,0,.25D*Math.sin(Math.toRadians(wheelAngle)));
            part("bogey_drive_piston");GlStateManager.popMatrix();
            GlStateManager.pushMatrix();GlStateManager.translate(0,1,0);GlStateManager.rotate(wheelAngle,1,0,0);
            part("bogey_drive_wheel");GlStateManager.translate(0,.25D,0);GlStateManager.rotate(-wheelAngle,1,0,0);
            part("bogey_drive_wheel_pin");GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
    }
}
