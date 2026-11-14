package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.trains.track.TrackBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.*;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.model.*;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.*;

@Mod.EventBusSubscriber(modid=Reference.MOD_ID,value=Side.CLIENT)
public final class PortalTrackModels {
    private static IModel source(){return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create","block/track/teleport"),"Missing portal track model");}
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event){for(var texture:source().getTextures())event.getMap().registerSprite(texture);}
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        Map<EnumFacing,IBakedModel> portals=new EnumMap<>(EnumFacing.class);var atlas=Minecraft.getMinecraft().getTextureMapBlocks();
        for(EnumFacing face:EnumFacing.HORIZONTALS) {
            int rotation=switch(face){case NORTH->180;case EAST->270;case WEST->90;default->0;};
            portals.put(face,source().bake(ModelRotation.getModelRotation(0,rotation),DefaultVertexFormats.BLOCK,s->atlas.getAtlasSprite(s.toString())));
        }
        for(var key:new ArrayList<>(event.getModelRegistry().getKeys()))if(key instanceof ModelResourceLocation model&&model.getNamespace().equals(Reference.MOD_ID)
                &&model.getPath().equals("track")&&!model.getVariant().equals("inventory")) {
            IBakedModel original=event.getModelRegistry().getObject(model);if(original!=null)event.getModelRegistry().putObject(model,new Model(original,portals));
        }
    }
    private static final class Model extends BakedModelWrapper<IBakedModel> {
        private final Map<EnumFacing,IBakedModel> portals;
        Model(IBakedModel original,Map<EnumFacing,IBakedModel> portals){super(original);this.portals=portals;}
        @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed) {
            if(state instanceof IExtendedBlockState extended) {
                var model=portals.get(extended.getValue(TrackBlock.PORTAL_FACE));if(model!=null)return model.getQuads(extended.getClean(),side,seed);
            }
            return originalModel.getQuads(state,side,seed);
        }
    }
}
