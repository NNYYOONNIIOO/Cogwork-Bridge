package com.cogwork.cogworkbridge.client;

import com.google.common.collect.ImmutableMap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.obj.OBJModel;
import net.minecraftforge.common.model.IModelState;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

/** Reads Create's railway OBJ meshes from the required asset jar. */
final class UpstreamTrackObjModel implements IModel {
    private final IModel model;
    private final Collection<String> groups;
    private UpstreamTrackObjModel(IModel model, Collection<String> groups) {this.model=model;this.groups=groups;}
    private static String path(ResourceLocation location) {
        String path=location.getPath();
        if(path.startsWith("models/"))path=path.substring(7);
        return path.endsWith(".json")?path.substring(0,path.length()-5):path;
    }
    static boolean accepts(ResourceLocation location) {
        if(!"create".equals(location.getNamespace()))return false;
        String p=path(location);
        if(!p.startsWith("block/track/"))return false;
        if(p.startsWith("block/track/bogey/bogey_"))return true;
        return switch(p.substring("block/track/".length())) {
            case "diag","diag_2","ascending","cross_diag","cross_d1_xo","cross_d1_zo","cross_d2_xo","cross_d2_zo",
                    "segment_left","segment_right","tie" -> true;
            default -> false;
        };
    }
    static IModel load(IResourceManager manager,ResourceLocation location)throws Exception {
        String p=path(location);
        OBJModel parsed;
        try(IResource resource=manager.getResource(new ResourceLocation("create","models/"+p+".obj"))) {
            parsed=new OBJModel.Parser(resource,manager).parse();
        }
        IModel model=parsed.process(ImmutableMap.of("flip-v","true"));
        if(p.startsWith("block/track/bogey/")) model=model.retexture(ImmutableMap.<String,String>builder()
                .put("#belt","create:block/bogey/belt").put("#bogey","create:block/bogey/frame")
                .put("#bogey2","create:block/bogey/frame2").put("#piston","create:block/bogey/piston")
                .put("#wheel","create:block/bogey/wheel").put("#axis","create:block/axis")
                .put("#axis_top","create:block/axis_top").build());
        else model=model.retexture(ImmutableMap.of("#0","create:block/standard_track",
                "#1","create:block/standard_track_mip","#2","create:block/standard_track_crossing"));
        return new UpstreamTrackObjModel(model,parsed.getMatLib().getGroups().keySet());
    }
    @Override public Collection<ResourceLocation> getTextures(){return model.getTextures();}
    @Override public IBakedModel bake(IModelState state,VertexFormat format,Function<ResourceLocation,TextureAtlasSprite> texture) {
        OBJModel.OBJState obj=new OBJModel.OBJState(Collections.emptyList(),true,state);
        for(String group:groups)obj.getVisibilityMap().put(group,true);
        return model.bake(obj,format,texture);
    }
}
