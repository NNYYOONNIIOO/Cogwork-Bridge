package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.fluids.FluidPipeBlock;
import com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class FluidPipeModels {
    private static final ResourceLocation CASING = new ResourceLocation("create", "block/fluid_pipe/casing");
    private static final ResourceLocation ENCASED_OPEN = new ResourceLocation("create", "block/encased_fluid_pipe/block_open");
    private static final ResourceLocation ENCASED_FLAT = new ResourceLocation("create", "block/encased_fluid_pipe/block_flat");
    private static final Map<EnumFacing, ResourceLocation> CONNECTIONS = new EnumMap<>(EnumFacing.class);
    private static final Map<EnumFacing, ResourceLocation> RIM_CONNECTORS = new EnumMap<>(EnumFacing.class);
    private static final Map<EnumFacing, ResourceLocation> RIMS = new EnumMap<>(EnumFacing.class);
    private static final Map<EnumFacing, ResourceLocation> DRAINS = new EnumMap<>(EnumFacing.class);
    private static final Map<String, ResourceLocation> CORES = new HashMap<>();

    static {
        for (EnumFacing side : EnumFacing.values()) {
            CONNECTIONS.put(side, new ResourceLocation("create", "block/fluid_pipe/connection/" + side.getName()));
            RIM_CONNECTORS.put(side, new ResourceLocation("create", "block/fluid_pipe/rim_connector/" + side.getName()));
            RIMS.put(side, new ResourceLocation("create", "block/fluid_pipe/rim/" + side.getName()));
            DRAINS.put(side, new ResourceLocation("create", "block/fluid_pipe/drain/" + side.getName()));
        }
        for (EnumFacing.Axis axis : EnumFacing.Axis.values()) {
            for (String shape : new String[]{"lu", "ru", "ld", "rd", "ud", "u", "d", "lr", "l", "r"}) {
                String name = shape + "_" + axis.getName();
                CORES.put(name, new ResourceLocation("create", "block/fluid_pipe/" + name));
            }
        }
    }

    private FluidPipeModels() { }

    private static IModel get(ResourceLocation location) {
        return ModelLoaderRegistry.getModelOrLogError(location, "Could not load fluid pipe model " + location);
    }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        // The generated center variants inherit this texture from core_x/y/z.
        // Forge 1.12 only reports the child model's own texture entries, so
        // registering the children alone leaves their inherited sprite missing.
        event.getMap().registerSprite(new ResourceLocation("create", "block/pipes_connected"));
        List<ResourceLocation> models = new ArrayList<>();
        models.add(CASING);
        models.add(ENCASED_OPEN);
        models.add(ENCASED_FLAT);
        models.addAll(CONNECTIONS.values());
        models.addAll(RIM_CONNECTORS.values());
        models.addAll(RIMS.values());
        models.addAll(DRAINS.values());
        models.addAll(CORES.values());
        for (ResourceLocation modelLocation : models) {
            for (ResourceLocation texture : get(modelLocation).getTextures()) {
                event.getMap().registerSprite(texture);
            }
        }
    }

    @SubscribeEvent
    public static void bake(ModelBakeEvent event) {
        ModelResourceLocation key = new ModelResourceLocation(AllBlocks.FLUID_PIPE.getRegistryName(), "normal");
        IBakedModel original = event.getModelRegistry().getObject(key);
        if (original == null) return;
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        IBakedModel casing = bake(CASING, atlas);
        Map<EnumFacing, IBakedModel> connections = new EnumMap<>(EnumFacing.class);
        Map<EnumFacing, IBakedModel> connectors = new EnumMap<>(EnumFacing.class);
        Map<EnumFacing, IBakedModel> rims = new EnumMap<>(EnumFacing.class);
        Map<EnumFacing, IBakedModel> drains = new EnumMap<>(EnumFacing.class);
        Map<String, IBakedModel> cores = new HashMap<>();
        CONNECTIONS.forEach((side, location) -> connections.put(side, bake(location, atlas)));
        RIM_CONNECTORS.forEach((side, location) -> connectors.put(side, bake(location, atlas)));
        RIMS.forEach((side, location) -> rims.put(side, bake(location, atlas)));
        DRAINS.forEach((side, location) -> drains.put(side, bake(location, atlas)));
        CORES.forEach((name, location) -> cores.put(name, bake(location, atlas)));
        event.getModelRegistry().putObject(key,
                new ConnectedPipeModel(original, casing, connections, connectors, rims, drains, cores));

        for (SmartFluidPipeBlock.Attachment attachment : SmartFluidPipeBlock.Attachment.values()) {
            for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
                String variant = "face=" + attachment.getName() + ",facing=" + facing.getName();
                ModelResourceLocation smartKey = new ModelResourceLocation(AllBlocks.SMART_FLUID_PIPE.getRegistryName(), variant);
                IBakedModel smart = event.getModelRegistry().getObject(smartKey);
                if (smart != null) event.getModelRegistry().putObject(smartKey,
                        new AttachmentModel(smart, connections, connectors, rims, drains));
            }
        }

        ModelResourceLocation encasedKey = new ModelResourceLocation(AllBlocks.ENCASED_FLUID_PIPE.getRegistryName(), "normal");
        IBakedModel encasedOriginal = event.getModelRegistry().getObject(encasedKey);
        if (encasedOriginal == null) return;
        Map<EnumFacing, IBakedModel> open = new EnumMap<>(EnumFacing.class);
        Map<EnumFacing, IBakedModel> flat = new EnumMap<>(EnumFacing.class);
        for (EnumFacing side : EnumFacing.values()) {
            for (ModelRotation rotation : ModelRotation.values()) {
                if (rotation.rotateFace(EnumFacing.SOUTH) != side) continue;
                open.put(side, get(ENCASED_OPEN).bake(rotation, DefaultVertexFormats.BLOCK,
                        sprite -> atlas.getAtlasSprite(sprite.toString())));
                flat.put(side, get(ENCASED_FLAT).bake(rotation, DefaultVertexFormats.BLOCK,
                        sprite -> atlas.getAtlasSprite(sprite.toString())));
                break;
            }
        }
        event.getModelRegistry().putObject(encasedKey, new EncasedPipeModel(encasedOriginal, open, flat));
    }

    private static IBakedModel bake(ResourceLocation location, TextureMap atlas) {
        return get(location).bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK,
                sprite -> atlas.getAtlasSprite(sprite.toString()));
    }

    private static final class ConnectedPipeModel extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel casing;
        private final Map<EnumFacing, IBakedModel> connections;
        private final Map<EnumFacing, IBakedModel> connectors;
        private final Map<EnumFacing, IBakedModel> rims;
        private final Map<EnumFacing, IBakedModel> drains;
        private final Map<String, IBakedModel> cores;

        private ConnectedPipeModel(IBakedModel original, IBakedModel casing,
                                   Map<EnumFacing, IBakedModel> connections,
                                   Map<EnumFacing, IBakedModel> connectors,
                                   Map<EnumFacing, IBakedModel> rims,
                                   Map<EnumFacing, IBakedModel> drains,
                                   Map<String, IBakedModel> cores) {
            super(original);
            this.casing = casing;
            this.connections = connections;
            this.connectors = connectors;
            this.rims = rims;
            this.drains = drains;
            this.cores = cores;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing face, long random) {
            if (!(state instanceof IExtendedBlockState extended)) return originalModel.getQuads(state, face, random);
            List<BakedQuad> quads = new ArrayList<>();
            addAttachments(quads, extended, face, random, connections, connectors, rims, drains);
            for (EnumFacing.Axis axis : EnumFacing.Axis.values()) {
                int count = 0;
                for (EnumFacing direction : EnumFacing.values())
                    if (direction.getAxis() != axis && connected(extended, direction)) count++;
                if (count > 2) {
                    append(quads, casing, state, face, random);
                    break;
                }
            }
            for (EnumFacing.Axis axis : EnumFacing.Axis.values()) {
                String shape = coreShape(extended, axis);
                if (shape != null) append(quads, cores.get(shape + "_" + axis.getName()), state, face, random);
            }
            return quads;
        }
    }

    private static final class AttachmentModel extends BakedModelWrapper<IBakedModel> {
        private final Map<EnumFacing, IBakedModel> connections, connectors, rims, drains;

        private AttachmentModel(IBakedModel original, Map<EnumFacing, IBakedModel> connections,
                                Map<EnumFacing, IBakedModel> connectors, Map<EnumFacing, IBakedModel> rims,
                                Map<EnumFacing, IBakedModel> drains) {
            super(original);
            this.connections = connections;
            this.connectors = connectors;
            this.rims = rims;
            this.drains = drains;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing face, long random) {
            List<BakedQuad> base = originalModel.getQuads(state, face, random);
            if (!(state instanceof IExtendedBlockState extended)) return base;
            List<BakedQuad> quads = new ArrayList<>(base);
            addAttachments(quads, extended, face, random, connections, connectors, rims, drains);
            return quads;
        }
    }

    private static void addAttachments(List<BakedQuad> quads, IExtendedBlockState state,
                                       @Nullable EnumFacing face, long random,
                                       Map<EnumFacing, IBakedModel> connections,
                                       Map<EnumFacing, IBakedModel> connectors,
                                       Map<EnumFacing, IBakedModel> rims, Map<EnumFacing, IBakedModel> drains) {
        for (EnumFacing side : EnumFacing.values()) {
            Integer attachment = state.getValue(FluidPipeBlock.ATTACHMENTS.get(side));
            if (attachment == null) continue;
            if (attachment == FluidPipeBlock.CONNECTION) append(quads, connections.get(side), state, face, random);
            if (attachment == FluidPipeBlock.RIM_CONNECTOR || attachment == FluidPipeBlock.RIM_WITH_CONNECTOR
                    || attachment == FluidPipeBlock.DRAIN_WITH_CONNECTOR)
                append(quads, connectors.get(side), state, face, random);
            if (attachment == FluidPipeBlock.RIM_ONLY || attachment == FluidPipeBlock.RIM_WITH_CONNECTOR)
                append(quads, rims.get(side), state, face, random);
            if (attachment == FluidPipeBlock.DRAIN_ONLY || attachment == FluidPipeBlock.DRAIN_WITH_CONNECTOR)
                append(quads, drains.get(side), state, face, random);
        }
    }

    private static boolean connected(IExtendedBlockState state, EnumFacing direction) {
        return Boolean.TRUE.equals(state.getValue(FluidPipeBlock.CONNECTIONS.get(direction)));
    }

    private static String coreShape(IExtendedBlockState state, EnumFacing.Axis axis) {
        EnumFacing left = axis == EnumFacing.Axis.X ? EnumFacing.SOUTH : EnumFacing.EAST;
        EnumFacing right = axis == EnumFacing.Axis.X ? EnumFacing.NORTH : EnumFacing.WEST;
        EnumFacing up = axis == EnumFacing.Axis.Y ? EnumFacing.SOUTH : EnumFacing.UP;
        EnumFacing down = axis == EnumFacing.Axis.Y ? EnumFacing.NORTH : EnumFacing.DOWN;
        boolean l = connected(state, left), r = connected(state, right);
        boolean u = connected(state, up), d = connected(state, down);
        int count = (l ? 1 : 0) + (r ? 1 : 0) + (u ? 1 : 0) + (d ? 1 : 0);
        if (count == 1) return l ? "l" : r ? "r" : u ? "u" : "d";
        if (count != 2) return null;
        if (l && r) return "lr";
        if (u && d) return "ud";
        return (l ? "l" : "r") + (u ? "u" : "d");
    }

    private static void append(List<BakedQuad> quads, IBakedModel model, IBlockState state,
                               @Nullable EnumFacing face, long random) {
        if (model == null) return;
        List<BakedQuad> source = model.getQuads(state, face, random);
        if (source != null) quads.addAll(source);
    }

    private static final class EncasedPipeModel extends BakedModelWrapper<IBakedModel> {
        private final Map<EnumFacing, IBakedModel> open;
        private final Map<EnumFacing, IBakedModel> flat;

        private EncasedPipeModel(IBakedModel original, Map<EnumFacing, IBakedModel> open,
                                 Map<EnumFacing, IBakedModel> flat) {
            super(original);
            this.open = open;
            this.flat = flat;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing face, long random) {
            if (!(state instanceof IExtendedBlockState extended)) return originalModel.getQuads(state, face, random);
            List<BakedQuad> quads = new ArrayList<>();
            for (EnumFacing side : EnumFacing.values()) {
                boolean connected = Boolean.TRUE.equals(extended.getValue(FluidPipeBlock.CONNECTIONS.get(side)));
                IBakedModel model = (connected ? open : flat).get(side);
                if (model != null) quads.addAll(model.getQuads(state, face, random));
            }
            return quads;
        }
    }
}
