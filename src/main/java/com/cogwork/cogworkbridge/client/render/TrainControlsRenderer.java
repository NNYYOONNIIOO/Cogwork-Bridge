package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.client.TrainControlClient;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraption;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity;
import com.cogwork.cogworkbridge.content.trains.track.TrainControlsBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunkClient;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;

/** Renders the cover and two levers omitted from the upstream open block model. */
@Mod.EventBusSubscriber(modid=Reference.MOD_ID,value=Side.CLIENT)
public final class TrainControlsRenderer {
    private static IBakedModel cover,lever;
    private final Map<BearingContraptionEntity,Controls> controls=new WeakHashMap<>();

    private static IModel model(String part) {
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create","block/controls/train/"+part),
                "Could not load train controls part "+part);
    }
    private static IModel openModel() {
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create","block/controls/block_open"),
                "Could not load open train controls model");
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for(String part:new String[]{"cover","lever"})
            for(ResourceLocation texture:model(part).getTextures())event.getMap().registerSprite(texture);
        for(ResourceLocation texture:openModel().getTextures())event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas=Minecraft.getMinecraft().getTextureMapBlocks();
        cover=model("cover").bake(ModelRotation.X0_Y0,DefaultVertexFormats.BLOCK,r->atlas.getAtlasSprite(r.toString()));
        lever=model("lever").bake(ModelRotation.X0_Y0,DefaultVertexFormats.BLOCK,r->atlas.getAtlasSprite(r.toString()));
        for(EnumFacing facing:EnumFacing.HORIZONTALS) {
            ModelResourceLocation key=new ModelResourceLocation(AllBlocks.TRAIN_CONTROLS.getRegistryName(),
                    "facing="+facing.getName());
            IBakedModel closed=event.getModelRegistry().getObject(key);
            if(closed==null)continue;
            int rotation=facing==EnumFacing.EAST?90:facing==EnumFacing.SOUTH?180:facing==EnumFacing.WEST?270:0;
            IBakedModel open=openModel().bake(ModelRotation.getModelRotation(0,rotation),DefaultVertexFormats.BLOCK,
                    r->atlas.getAtlasSprite(r.toString()));
            event.getModelRegistry().putObject(key,new ControlsModel(closed,open));
        }
    }

    private static final class ControlsModel extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel open;
        private ControlsModel(IBakedModel closed,IBakedModel open){super(closed);this.open=open;}
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state,@Nullable EnumFacing side,long random) {
            Boolean opened=state instanceof IExtendedBlockState extended?extended.getValue(TrainControlsBlock.OPEN):null;
            return (Boolean.TRUE.equals(opened)?open:originalModel).getQuads(state,side,random);
        }
    }

    public void render(BearingContraptionEntity body,MobileChunkClient chunk,float partial) {
        if(MinecraftForgeClient.getRenderPass()==1||!(body.getMountedCart() instanceof RailwayCarriageEntity carriage)
                ||body.getContraption()==null)return;
        Controls cached=controls.computeIfAbsent(body,key->new Controls());
        var structure=body.getContraption();
        if(cached.structure!=structure||cached.version!=structure.contentVersion()||!body.getAnchor().equals(cached.anchor)) {
            cached.structure=structure;cached.version=structure.contentVersion();cached.anchor=body.getAnchor();
            Map<BlockPos,Part> previous=new HashMap<>();
            for(Part part:cached.parts)previous.put(part.pos,part);
            cached.parts.clear();
            for(var snapshot:structure.blocks().values()) {
                BlockPos pos=body.localPos(snapshot);
                if(chunk.getBlockState(pos).getBlock() instanceof TrainControlsBlock)
                    cached.parts.add(previous.getOrDefault(pos,new Part(pos)));
            }
        }
        float lightX=OpenGlHelper.lastBrightnessX,lightY=OpenGlHelper.lastBrightnessY;
        try {
            for(Part part:cached.parts) {
                if(!body.isRailwayBlockVisible(part.pos))continue;
                IBlockState state=chunk.getBlockState(part.pos);
                if(!(state.getBlock() instanceof TrainControlsBlock))continue;
                EnumFacing facing=state.getValue(TrainControlsBlock.FACING);
                boolean active=TrainControlClient.isControlling(carriage.getEntityId(),part.pos);
                Vec3d motion=new Vec3d(body.posX-body.lastTickPosX,body.posY-body.lastTickPosY,body.posZ-body.lastTickPosZ);
                double direction=motion.dotProduct(body.localFacingVectorToWorld(facing.getOpposite()));
                float speed=(float)(Math.min(motion.length(),.5D)*Math.signum(direction));
                part.tick(body.ticksExisted,active,speed,TrainControlClient.inputMask());
                int light=chunk.getCombinedLight(part.pos,0);
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light%65536,light/65536);
                GlStateManager.pushMatrix();
                try {
                    GlStateManager.translate(part.pos.getX()+.5D,part.pos.getY()+.5D,part.pos.getZ()+.5D);
                    // Blockstate Y rotations are clockwise, opposite OpenGL's positive Y rotation.
                    GlStateManager.rotate(180F-facing.getHorizontalAngle(),0,1,0);
                    GlStateManager.translate(-.5D,-.5D,-.5D);
                    FastStateRendering.getInstance().renderFast(cover,state);
                    float equip=lerp(part.previousEquip,part.equip,partial);
                    float yOffset=-.15F+.2F*equip*equip;
                    renderLever(state,yOffset,MathHelper.clamp(lerp(part.previousSpeed,part.speed,partial)*70F-25F,-45F,45F),true);
                    renderLever(state,yOffset,MathHelper.clamp(lerp(part.previousSteering,part.steering,partial)*15F,-45F,45F),false);
                } finally {
                    GlStateManager.popMatrix();
                }
            }
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,lightX,lightY);
        }
    }

    private static void renderLever(IBlockState state,float yOffset,float angle,boolean first) {
        GlStateManager.pushMatrix();
        try {
            // Same pivot, retraction and lever spacing as the 1.21.1 ControlsRenderer.
            GlStateManager.translate(.5D,.75D,.75D);
            GlStateManager.rotate(angle-45F,1,0,0);
            GlStateManager.translate(0,yOffset,0);
            GlStateManager.rotate(45F,1,0,0);
            GlStateManager.translate(-.5D,-.875D,-.6875D);
            GlStateManager.translate(first?0D:.375D,0,0);
            FastStateRendering.getInstance().renderFast(lever,state);
        } finally {
            GlStateManager.popMatrix();
        }
    }
    private static float lerp(float previous,float current,float partial){return previous+(current-previous)*partial;}
    private static final class Controls {
        private BearingContraption structure;
        private long version;
        private BlockPos anchor;
        private final List<Part> parts=new ArrayList<>();
    }
    private static final class Part {
        private final BlockPos pos;
        private int tick=Integer.MIN_VALUE;
        private float equip,previousEquip,speed,previousSpeed,steering,previousSteering;
        private Part(BlockPos pos){this.pos=pos.toImmutable();}
        private void tick(int time,boolean active,float targetSpeed,int input) {
            if(time==tick)return;
            tick=time;previousEquip=equip;previousSpeed=speed;previousSteering=steering;
            equip+=((active?1F:0F)-equip)*.2F;
            speed+=((active?targetSpeed:0F)-speed)*.2F;
            steering+=((active?((input&8)!=0?1F:0F)-((input&4)!=0?1F:0F):0F)-steering)*.2F;
        }
    }
}
