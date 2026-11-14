package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.trains.track.RailwayTargetTile;
import com.cogwork.cogworkbridge.content.trains.track.TrackBlock;
import com.cogwork.cogworkbridge.content.trains.track.TrackShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
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
public final class RailwayDeviceRenderer {
    private static final String[] PARTS={"track_signal/indicator_on","track_signal/indicator_off",
            "track_station/flag_on","track_station/flag_off","track_station/flag_assemble"};
    private static final Map<String,IBakedModel> MODELS=new HashMap<>();
    private RailwayDeviceRenderer() { }
    private static IModel model(String part){
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create","block/"+part),"Missing railway model "+part);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event){
        for(String part:PARTS)for(ResourceLocation texture:model(part).getTextures())event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event){
        var atlas=Minecraft.getMinecraft().getTextureMapBlocks();MODELS.clear();
        for(String part:PARTS)MODELS.put(part,model(part).bake(ModelRotation.X0_Y0,DefaultVertexFormats.BLOCK,
                s->atlas.getAtlasSprite(s.toString())));
    }
    public static final class Signal extends TileEntitySpecialRenderer<RailwayTargetTile.Signal> {
        @Override public void render(RailwayTargetTile.Signal tile,double x,double y,double z,float partial,int destroy,float alpha){
            GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);
            FastStateRendering.getInstance().renderFast(MODELS.get(tile.isRed()?"track_signal/indicator_on":"track_signal/indicator_off"),Blocks.AIR.getDefaultState());
            GlStateManager.popMatrix();
        }
    }
    public static final class Observer extends TileEntitySpecialRenderer<RailwayTargetTile.Observer> {
        @Override public void render(RailwayTargetTile.Observer tile,double x,double y,double z,float partial,int destroy,float alpha){
            ItemStack filter=tile.filter();if(filter.isEmpty()||tile.getWorld()==null)return;
            Minecraft mc=Minecraft.getMinecraft();
            boolean block=mc.getRenderItem().getItemModelWithOverrides(filter,tile.getWorld(),null).isGui3d();
            RenderHelper.enableStandardItemLighting();GlStateManager.pushMatrix();
            GlStateManager.translate(x+.5D,y+1.006D,z+.5D);GlStateManager.rotate(-90F,1,0,0);
            GlStateManager.translate(0,0,block?.002D:.035D);
            float scale=block?.30F:.20F;GlStateManager.scale(scale,scale,scale);
            mc.getRenderItem().renderItem(filter,ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();RenderHelper.disableStandardItemLighting();
        }
    }
    public static final class Station extends TileEntitySpecialRenderer<RailwayTargetTile.Station> {
        @Override public void render(RailwayTargetTile.Station tile,double x,double y,double z,float partial,int destroy,float alpha){
            if(!tile.hasValidTarget())return;
            TrackShape shape=tile.getWorld().getBlockState(tile.target()).getValue(TrackBlock.SHAPE);
            int sign=tile.front()?1:-1;
            EnumFacing facing=EnumFacing.getFacingFromVector(shape.x*sign,0,shape.z*sign);
            BlockPos delta=tile.target().subtract(tile.getPos());EnumFacing right=facing.rotateY();
            boolean flipped=delta.getX()*right.getXOffset()+delta.getZ()*right.getZOffset()>0;
            String part=tile.assembling()?"track_station/flag_assemble":tile.trainPresent()?"track_station/flag_on":"track_station/flag_off";
            GlStateManager.pushMatrix();GlStateManager.translate(x+.5D,y+.5D,z+.5D);
            GlStateManager.rotate(-facing.getHorizontalAngle()-90F,0,1,0);
            GlStateManager.translate(1D/512D,9.5D/16D,flipped?14D/16D-1D/512D:2D/16D+1D/512D);
            GlStateManager.translate(-.5D,-.5D,-.5D);
            GlStateManager.rotate((flipped?1F:-1F)*((tile.assembling()||tile.trainPresent()?90F:0F)+270F),1,0,0);
            FastStateRendering.getInstance().renderFast(MODELS.get(part),Blocks.AIR.getDefaultState());GlStateManager.popMatrix();
        }
    }
}
