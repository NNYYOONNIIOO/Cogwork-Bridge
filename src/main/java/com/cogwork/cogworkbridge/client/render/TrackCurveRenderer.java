package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.trains.track.TrackCurve;
import com.cogwork.cogworkbridge.content.trains.track.TrackTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
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
    private static final Map<TrackCurve, CurveMesh> MESHES = new WeakHashMap<>();
    private static List<BakedQuad> leftQuads = List.of(), rightQuads = List.of(), tieQuads = List.of();
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
        IBakedModel left=model("segment_left").bake(ModelRotation.X0_Y0,DefaultVertexFormats.BLOCK,s->atlas.getAtlasSprite(s.toString()));
        IBakedModel right=model("segment_right").bake(ModelRotation.X0_Y0,DefaultVertexFormats.BLOCK,s->atlas.getAtlasSprite(s.toString()));
        IBakedModel tie=model("tie").bake(ModelRotation.X0_Y0,DefaultVertexFormats.BLOCK,s->atlas.getAtlasSprite(s.toString()));
        leftQuads = quads(left); rightQuads = quads(right); tieQuads = quads(tie);
        MESHES.clear();
    }
    @Override public void render(TrackTileEntity tile,double x,double y,double z,float partial,int destroy,float alpha) {
        if(tile.getWorld()==null)return;
        Entity camera=Minecraft.getMinecraft().getRenderViewEntity();
        if(camera==null)return;
        Frustum frustum = tile.getWorld() == Minecraft.getMinecraft().world ? new Frustum() : null;
        if (frustum != null) frustum.setPosition(camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial,
                camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial,
                camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial);
        boolean wasLighting=GL11.glIsEnabled(GL11.GL_LIGHTING);
        GlStateManager.disableLighting();
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        try {
            GlStateManager.color(1,1,1,1);
            for(TrackCurve curve:tile.connections()) {
                if(!renderFrom(tile,curve,frustum))continue;
                CurveMesh mesh = MESHES.computeIfAbsent(curve, CurveMesh::new);
                if (frustum != null && !frustum.isBoundingBoxInFrustum(mesh.bounds)) continue;
                GlStateManager.pushMatrix();
                try {
                    GlStateManager.translate(x + curve.first.getX() - tile.getPos().getX(),
                            y + curve.first.getY() - tile.getPos().getY(),
                            z + curve.first.getZ() - tile.getPos().getZ());
                    mesh.render(tile.getWorld(), alpha);
                } finally {
                    GlStateManager.popMatrix();
                }
            }
        } finally {
            if(wasLighting)GlStateManager.enableLighting();
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
        if (frustum == null) return tile.getPos().equals(curve.first);
        boolean firstVisible=frustum.isBoundingBoxInFrustum(new AxisAlignedBB(curve.first).grow(1.5D));
        boolean secondVisible=frustum.isBoundingBoxInFrustum(new AxisAlignedBB(curve.second).grow(1.5D));
        return tile.getPos().equals(!firstVisible&&secondVisible?curve.second:curve.first);
    }

    private static Vec3d normal(Vec3d tangent) {
        Vec3d normal=new Vec3d(tangent.z,0,-tangent.x);
        return normal.lengthSquared()<1E-10D?new Vec3d(1,0,0):normal.normalize();
    }

    private static List<BakedQuad> quads(IBakedModel model) {
        List<BakedQuad> result = new ArrayList<>();
        for (EnumFacing side : EnumFacing.VALUES) result.addAll(model.getQuads(Blocks.AIR.getDefaultState(), side, 0L));
        result.addAll(model.getQuads(Blocks.AIR.getDefaultState(), null, 0L));
        return List.copyOf(result);
    }

    /** One immutable transformed mesh per connection; only light and alpha change while rendering. */
    private static final class CurveMesh {
        private static final int STRIDE = DefaultVertexFormats.BLOCK.getIntegerSize();
        private final int[] vertices, segmentOffsets, lights;
        private final BlockPos[] samples;
        private final AxisAlignedBB bounds;
        private WeakReference<World> lightWorld = new WeakReference<>(null);
        private long lightTick = Long.MIN_VALUE;
        private int vertexAlpha = -1;

        private CurveMesh(TrackCurve curve) {
            int segments = Math.max(2, (int) (curve.length() * 2));
            List<int[]> pieces = new ArrayList<>(segments * 3);
            segmentOffsets = new int[segments + 1];
            samples = new BlockPos[segments];
            lights = new int[segments];
            java.util.Arrays.fill(lights, -1);
            int total = 0;
            Vec3d previous = curve.point(0), previousNormal = normal(curve.tangent(0));
            for (int i = 1; i <= segments; i++) {
                double t = curve.parameterAtFraction(i / (double) segments);
                Vec3d next = curve.point(t), nextNormal = normal(curve.tangent(t));
                samples[i - 1] = new BlockPos(next.x, next.y + .25D, next.z);
                Vec3d middle = next.subtract(previous);
                if (middle.lengthSquared() > 1E-10D) {
                    int[] data = transform(tieQuads, curve.first, previous, middle, -.5D, 0D, 1D);
                    pieces.add(data); total += data.length;
                }
                for (int direction = 1; direction >= -1; direction -= 2) {
                    Vec3d start = previous.add(previousNormal.scale(.965D * direction));
                    Vec3d end = next.add(nextNormal.scale(.965D * direction));
                    Vec3d difference = end.subtract(start);
                    double length = difference.length();
                    if (length < .00001D) continue;
                    int[] data = transform(direction == 1 ? leftQuads : rightQuads, curve.first, start, difference,
                            0D, -1D / 32D, length * (i == segments ? 2.2D : 2.1D));
                    pieces.add(data); total += data.length;
                }
                segmentOffsets[i] = total;
                previous = next; previousNormal = nextNormal;
            }
            vertices = new int[total];
            int at = 0;
            for (int[] piece : pieces) {
                System.arraycopy(piece, 0, vertices, at, piece.length);
                at += piece.length;
            }
            AxisAlignedBB box = new AxisAlignedBB(curve.start.x, curve.start.y, curve.start.z,
                    curve.finish.x, curve.finish.y, curve.finish.z);
            for (Vec3d point : new Vec3d[]{curve.control1, curve.control2}) {
                box = box.union(new AxisAlignedBB(point.x, point.y, point.z, point.x, point.y, point.z));
            }
            bounds = box.grow(2D);
        }

        private void render(World world, float alpha) {
            long tick = world.getTotalWorldTime();
            World previousWorld = lightWorld.get();
            if (lightTick != tick || previousWorld != world) {
                for (int segment = 0; segment < samples.length; segment++) {
                    int light = world.isBlockLoaded(samples[segment], false)
                            ? world.getCombinedLight(samples[segment], 0) : 0xF000F0;
                    if (lights[segment] == light) continue;
                    lights[segment] = light;
                    for (int vertex = segmentOffsets[segment]; vertex < segmentOffsets[segment + 1]; vertex += STRIDE) {
                        vertices[vertex + 6] = light;
                    }
                }
                lightTick = tick;
                if (previousWorld != world) lightWorld = new WeakReference<>(world);
            }
            int a = Math.max(0, Math.min(255, Math.round(alpha * 255F)));
            if (vertexAlpha != a) {
                for (int vertex = 0; vertex < vertices.length; vertex += STRIDE) vertices[vertex + 3] = a << 24 | 0xFFFFFF;
                vertexAlpha = a;
            }
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            buffer.addVertexData(vertices);
            Tessellator.getInstance().draw();
        }

        private static int[] transform(List<BakedQuad> quads, BlockPos origin, Vec3d point, Vec3d difference,
                                       double offsetX, double offsetZ, double scaleZ) {
            int[] data = new int[quads.size() * 4 * STRIDE];
            double yaw = Math.atan2(difference.x, difference.z);
            double pitch = -Math.atan2(difference.y, Math.hypot(difference.x, difference.z));
            double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
            int at = 0;
            for (BakedQuad quad : quads) {
                int[] source = quad.getVertexData();
                System.arraycopy(source, 0, data, at, source.length);
                for (int vertex = 0; vertex < 4; vertex++) {
                    int index = at + vertex * STRIDE;
                    double x = Float.intBitsToFloat(data[index]) + offsetX;
                    double y = Float.intBitsToFloat(data[index + 1]) - 2D / 16D - 1D / 256D;
                    double z = Float.intBitsToFloat(data[index + 2]) * scaleZ + offsetZ;
                    double ry = cp * y - sp * z, rz = sp * y + cp * z;
                    data[index] = Float.floatToRawIntBits((float) (point.x - origin.getX() + cy * x + sy * rz));
                    data[index + 1] = Float.floatToRawIntBits((float) (point.y - origin.getY() + 3D / 16D + ry));
                    data[index + 2] = Float.floatToRawIntBits((float) (point.z - origin.getZ() - sy * x + cy * rz));
                }
                at += source.length;
            }
            return data;
        }
    }
}
