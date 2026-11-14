package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.chassis.ChassisBlock;
import com.cogwork.cogworkbridge.content.decoration.ConnectedTextureData;
import com.cogwork.cogworkbridge.content.kinetics.encased.EncasedCogwheelBlock;
import com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterBlock;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Applies the player's upstream Create connected-texture sheets to baked models. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ConnectedTextureModels {
    private static final String[] TEXTURES = {"andesite_casing", "brass_casing", "copper_casing",
            "andesite_encased_cogwheel_side", "brass_encased_cogwheel_side", "crafter_side",
            "linear_chassis_side", "secondary_linear_chassis_side", "linear_chassis_end",
            "linear_chassis_end_sticky", "tunnel/brass_tunnel_top", "railway_casing", "railway_casing_side"};

    private ConnectedTextureModels() { }

    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String name : TEXTURES) event.getMap().registerSprite(new ResourceLocation("create", "block/" + name + "_connected"));
        for (String name : new String[]{"framed_glass", "horizontal_framed_glass", "vertical_framed_glass", "ornate_iron_window", "industrial_iron_window", "weathered_iron_window_1", "weathered_iron_window_2", "weathered_iron_window_3", "weathered_iron_window_4"})
            event.getMap().registerSprite(new ResourceLocation("create", "block/palettes/" + name + "_connected"));
        for (String wood : com.cogwork.cogworkbridge.registry.BuildingPalette.WINDOWS)
            event.getMap().registerSprite(new ResourceLocation("create", "block/palettes/" + wood + "_window_connected"));
        for (String stone : com.cogwork.cogworkbridge.registry.BuildingPalette.STONES)
            for (String kind : new String[]{"layered", "pillar", "cap"})
                event.getMap().registerSprite(new ResourceLocation("create", "block/palettes/stone_types/" + kind + "/" + stone + "_cut_" + kind + "_connected"));
        for (String stage : new String[]{"", "exposed_", "weathered_", "oxidized_"}) for (String roof : new String[]{"tiles", "shingles"})
            event.getMap().registerSprite(new ResourceLocation("create", "block/copper/" + stage + "copper_" + roof + "_top_connected"));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void bake(ModelBakeEvent event) {
        Set<ResourceLocation> blocks = new HashSet<>();
        for (Block block : new Block[]{AllBlocks.ANDESITE_CASING, AllBlocks.BRASS_CASING, AllBlocks.COPPER_CASING,
                AllBlocks.SHAFT_ENCASED_ANDESITE, AllBlocks.SHAFT_ENCASED_BRASS,
                AllBlocks.COGWHEEL_ENCASED_ANDESITE, AllBlocks.COGWHEEL_ENCASED_BRASS,
                AllBlocks.LARGE_COGWHEEL_ENCASED_ANDESITE, AllBlocks.LARGE_COGWHEEL_ENCASED_BRASS,
                AllBlocks.GEARBOX, AllBlocks.VERTICAL_GEARBOX, AllBlocks.ENCASED_FLUID_PIPE,
                AllBlocks.LINEAR_CHASSIS, AllBlocks.SECONDARY_LINEAR_CHASSIS,
                AllBlocks.MECHANICAL_CRAFTER, AllBlocks.BRASS_TUNNEL}) blocks.add(block.getRegistryName());
        blocks.add(com.cogwork.cogworkbridge.registry.BlockFamily.get("railway_casing").getRegistryName());
        for (Block block : com.cogwork.cogworkbridge.registry.BuildingPalette.items()) {
            if (block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Glass
                    || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Pane
                    || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Pillar
                    || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.WeatheringPaletteBlocks.Cube
                    || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.WeatheringPaletteBlocks.Stairs
                    || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.WeatheringPaletteBlocks.Slab
                    || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Layered) blocks.add(block.getRegistryName());
        }
        for (String stage : com.cogwork.cogworkbridge.registry.BuildingPalette.COPPER_STAGES)
            for (String roof : new String[]{"tile", "shingle"})
                blocks.add(com.cogwork.cogworkbridge.registry.BlockFamily.get(stage + "copper_" + roof + "_slab_double").getRegistryName());
        for (ResourceLocation key : new ArrayList<>(event.getModelRegistry().getKeys())) {
            if (!(key instanceof ModelResourceLocation location) || location.getVariant().equals("inventory")
                    || !blocks.contains(new ResourceLocation(location.getNamespace(), location.getPath()))) continue;
            IBakedModel model = event.getModelRegistry().getObject(location);
            if (model != null) event.getModelRegistry().putObject(location, new Model(model));
        }
    }

    private enum Type { OMNI, HORIZONTAL, HORIZONTAL_KRYPPERS, VERTICAL, RECTANGLE, ROOF, ROOF_STAIR }

    private static final class Model extends BakedModelWrapper<IBakedModel> {
        private Model(IBakedModel original) { super(original); }

        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            List<BakedQuad> quads = originalModel.getQuads(state, side, random);
            if (!(state instanceof IExtendedBlockState extended)) return quads;
            Long context = extended.getValue(ConnectedTextureData.CONTEXT);
            if (context == null || quads.isEmpty()) return quads;
            TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
            List<BakedQuad> changed = null;
            boolean pane = state.getBlock() instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Pane;
            for (int i = 0; i < quads.size(); i++) {
                BakedQuad quad = quads.get(i);
                EnumFacing face = quad.getFace();
                if (face == null) continue;
                int part = pane ? panePart(quad) : 0;
                int above = (int) ((context >>> ConnectedTextureData.PANE_ABOVE_SHIFT) & 31L);
                int below = (int) ((context >>> ConnectedTextureData.PANE_BELOW_SHIFT) & 31L);
                if (pane && side != null && side.getAxis().isVertical()
                        && ((side == EnumFacing.UP ? above : below) & part) != 0) {
                    if (changed == null) changed = new ArrayList<>(quads);
                    changed.set(i, null);
                    continue;
                }
                TextureAtlasSprite source = quad.getSprite();
                String name = source.getIconName();
                if (!name.startsWith("create:block/")) continue;
                String texture = name.substring("create:block/".length());
                Type type = type(state, face, texture);
                if (type == null) continue;
                String targetName = targetTexture(state, face, texture, random);
                TextureAtlasSprite target = atlas.getAtlasSprite("create:block/" + targetName + "_connected");
                if (target == atlas.getMissingSprite()) continue;
                int bits = (int) ((context >>> (face.getIndex() * 8)) & 255L);
                if (pane && face.getAxis().isHorizontal()) {
                    if ((above & part) == 0) bits &= ~(ConnectedTextureData.UP | ConnectedTextureData.TOP_LEFT | ConnectedTextureData.TOP_RIGHT);
                    if ((below & part) == 0) bits &= ~(ConnectedTextureData.DOWN | ConnectedTextureData.BOTTOM_LEFT | ConnectedTextureData.BOTTOM_RIGHT);
                }
                int index = type == Type.ROOF_STAIR ? roofStairIndex(state) : index(type, bits);
                int sheet = type == Type.OMNI ? 8 : type == Type.RECTANGLE || type == Type.ROOF || type == Type.ROOF_STAIR ? 4 : 2;
                int[] data = quad.getVertexData().clone();
                int stride = data.length / 4;
                float width = source.getMaxU() - source.getMinU(), height = source.getMaxV() - source.getMinV();
                if (width == 0 || height == 0) continue;
                for (int vertex = 0; vertex < 4; vertex++) {
                    int uv = vertex * stride + 4;
                    float u = (Float.intBitsToFloat(data[uv]) - source.getMinU()) / width * 16F;
                    float v = (Float.intBitsToFloat(data[uv + 1]) - source.getMinV()) / height * 16F;
                    data[uv] = Float.floatToRawIntBits(target.getInterpolatedU((u + index % sheet * 16F) / sheet));
                    data[uv + 1] = Float.floatToRawIntBits(target.getInterpolatedV((v + index / sheet * 16F) / sheet));
                }
                if (changed == null) changed = new ArrayList<>(quads);
                changed.set(i, new BakedQuad(data, quad.getTintIndex(), face, target, quad.shouldApplyDiffuseLighting(), quad.getFormat()));
            }
            if (changed != null) changed.removeIf(java.util.Objects::isNull);
            return changed == null ? quads : changed;
        }
    }

    /** Identify the center post or one of the four arms from the baked quad. */
    private static int panePart(BakedQuad quad) {
        int[] vertices = quad.getVertexData();
        int stride = vertices.length / 4;
        float x = 0, z = 0;
        for (int i = 0; i < 4; i++) {
            x += Float.intBitsToFloat(vertices[i * stride]);
            z += Float.intBitsToFloat(vertices[i * stride + 2]);
        }
        x /= 4; z /= 4;
        EnumFacing arm = x < .4374F ? EnumFacing.WEST : x > .5626F ? EnumFacing.EAST
                : z < .4374F ? EnumFacing.NORTH : z > .5626F ? EnumFacing.SOUTH : null;
        return arm == null ? ConnectedTextureData.PANE_CENTER : 1 << arm.getHorizontalIndex();
    }

    @Nullable private static Type type(IBlockState state, EnumFacing face, String texture) {
        Block block = state.getBlock();
        if (block instanceof com.cogwork.cogworkbridge.content.decoration.palette.WeatheringPaletteBlocks.Cube
                || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.WeatheringPaletteBlocks.Stairs
                || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.WeatheringPaletteBlocks.Slab
                || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.WeatheringPaletteBlocks.DoubleSlab) {
            if (face != EnumFacing.UP || !texture.startsWith("copper/") || !texture.endsWith("copper_roof_top")) return null;
            return block instanceof com.cogwork.cogworkbridge.content.decoration.palette.WeatheringPaletteBlocks.Stairs
                    && state.getValue(net.minecraft.block.BlockStairs.HALF) == net.minecraft.block.BlockStairs.EnumHalf.BOTTOM ? Type.ROOF_STAIR : Type.ROOF;
        }
        if (block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Layered
                || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Pillar) {
            if (texture.contains("/stone_types/cap/")) return Type.OMNI;
            if (texture.contains("/stone_types/layered/")) return Type.HORIZONTAL_KRYPPERS;
            if (texture.contains("/stone_types/pillar/")) return Type.RECTANGLE;
            return null;
        }
        if (block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Glass
                || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Pane) {
            if (!texture.startsWith("palettes/")) return null;
            String id = block.getRegistryName().getPath();
            if (id.startsWith("framed_glass")) return texture.equals("palettes/framed_glass") ? Type.OMNI : null;
            if (id.startsWith("horizontal_framed_glass")) return texture.equals("palettes/framed_glass") ? (face.getAxis().isVertical() ? Type.OMNI : Type.HORIZONTAL_KRYPPERS) : null;
            if (id.startsWith("vertical_framed_glass")) return texture.equals("palettes/framed_glass") && face.getAxis().isHorizontal() ? Type.VERTICAL : null;
            if (id.startsWith("weathered_iron_window") || id.startsWith("industrial_iron_window")) return texture.endsWith("_window") ? Type.RECTANGLE : null;
            if (id.contains("_window") && texture.endsWith("_window")) return Type.VERTICAL;
            return null;
        }
        if (block instanceof ChassisBlock chassis && !chassis.isRadial()) {
            if (texture.equals("linear_chassis_side") || texture.equals("secondary_linear_chassis_side")
                    || texture.equals("linear_chassis_end") || texture.equals("linear_chassis_end_sticky")) return Type.OMNI;
            return null;
        }
        if (block == AllBlocks.BRASS_TUNNEL)
            return face == EnumFacing.UP && texture.equals("tunnel/brass_tunnel_top") ? Type.VERTICAL : null;
        if (block instanceof MechanicalCrafterBlock) {
            EnumFacing.Axis facingAxis = state.getValue(MechanicalCrafterBlock.FACING).getAxis();
            if (face.getAxis() == facingAxis) return texture.equals("brass_casing") ? Type.OMNI : null;
            if (!texture.equals("crafter_side")) return null;
            return face.getAxis().isVertical() && facingAxis != EnumFacing.Axis.X ? Type.HORIZONTAL : Type.VERTICAL;
        }
        if (block instanceof EncasedCogwheelBlock cog && !cog.isLarge() && face.getAxis() != state.getValue(KineticBlock.AXIS)) {
            if (!texture.equals("andesite_encased_cogwheel_side") && !texture.equals("brass_encased_cogwheel_side")) return null;
            EnumFacing.Axis axis = state.getValue(KineticBlock.AXIS);
            return axis == EnumFacing.Axis.X || axis == EnumFacing.Axis.Z && face.getAxis() == EnumFacing.Axis.X
                    ? Type.VERTICAL : Type.HORIZONTAL;
        }
        return texture.equals("andesite_casing") || texture.equals("brass_casing") || texture.equals("copper_casing")
                || texture.equals("railway_casing") || texture.equals("railway_casing_side")
                ? Type.OMNI : null;
    }

    private static String targetTexture(IBlockState state, EnumFacing face, String texture, long random) {
        String id = state.getBlock().getRegistryName().getPath();
        if (state.getBlock() instanceof xy177.farmersfuturedelight.common.block.IWeatheringCopper && texture.endsWith("copper_roof_top"))
            return texture.substring(0, texture.length() - "copper_roof_top".length()) + (id.contains("shingle") ? "copper_shingles_top" : "copper_tiles_top");
        if (texture.equals("palettes/framed_glass")) {
            if (id.startsWith("horizontal_framed_glass") && face.getAxis().isHorizontal()) return "palettes/horizontal_framed_glass";
            if (id.startsWith("vertical_framed_glass")) return "palettes/vertical_framed_glass";
        }
        if (texture.equals("palettes/weathered_iron_window")) return texture + "_" + (1 + (int) Math.floorMod(random, 4));
        return texture;
    }

    private static int roofStairIndex(IBlockState state) {
        net.minecraft.block.BlockStairs.EnumShape shape = state.getValue(net.minecraft.block.BlockStairs.SHAPE);
        EnumFacing facing = state.getValue(net.minecraft.block.BlockStairs.FACING);
        if (shape == net.minecraft.block.BlockStairs.EnumShape.OUTER_LEFT || shape == net.minecraft.block.BlockStairs.EnumShape.INNER_LEFT)
            facing = facing.rotateYCCW();
        int type = shape == net.minecraft.block.BlockStairs.EnumShape.STRAIGHT ? 0
                : shape == net.minecraft.block.BlockStairs.EnumShape.INNER_LEFT || shape == net.minecraft.block.BlockStairs.EnumShape.INNER_RIGHT ? 1 : 2;
        int[][] mapping = {{1, 6, 9, 4}, {14, 12, 13, 15}, {2, 10, 8, 0}};
        return mapping[type][facing.getHorizontalIndex()];
    }

    private static int index(Type type, int bits) {
        boolean u = (bits & ConnectedTextureData.UP) != 0, d = (bits & ConnectedTextureData.DOWN) != 0;
        boolean l = (bits & ConnectedTextureData.LEFT) != 0, r = (bits & ConnectedTextureData.RIGHT) != 0;
        if (type == Type.HORIZONTAL) return (r ? 1 : 0) + (l ? 2 : 0);
        if (type == Type.HORIZONTAL_KRYPPERS) return !r && !l ? 0 : !r ? 3 : !l ? 2 : 1;
        if (type == Type.VERTICAL) return (u ? 1 : 0) + (d ? 2 : 0);
        if (type == Type.RECTANGLE) return (l && r ? 2 : l ? 3 : r ? 1 : 0) + (u && d ? 1 : u ? 2 : d ? 0 : 3) * 4;
        boolean tl = (bits & ConnectedTextureData.TOP_LEFT) != 0, tr = (bits & ConnectedTextureData.TOP_RIGHT) != 0;
        boolean bl = (bits & ConnectedTextureData.BOTTOM_LEFT) != 0, br = (bits & ConnectedTextureData.BOTTOM_RIGHT) != 0;
        if (type == Type.ROOF) {
            boolean upDrops = d && !u && (l || r), downDrops = !d && u && (l || r);
            boolean leftDrops = !l && r && (u || d), rightDrops = l && !r && (u || d);
            if (upDrops) return leftDrops ? (br ? 0 : 5) : rightDrops ? (bl ? 2 : 5) : 1;
            if (downDrops) return leftDrops ? (tr ? 8 : 5) : rightDrops ? (tl ? 10 : 5) : 9;
            if (leftDrops) return 4;
            if (rightDrops) return 6;
            if (!u || !d || !l || !r) return 5;
            if (bl && tr) { if (tl && !br) return 12; if (br && !tl) return 15; if (!br && !tl) return 7; }
            if (br && tl) { if (tr && !bl) return 13; if (bl && !tr) return 14; if (!bl && !tr) return 11; }
            return 5;
        }
        int x = (u ? 1 : 0) + (d ? 2 : 0), y = (l ? 1 : 0) + (r ? 2 : 0);
        int borders = (u ? 0 : 1) + (d ? 0 : 1) + (l ? 0 : 1) + (r ? 0 : 1);
        if (borders == 0) {
            x += (tr ? 1 : 0) + (tl ? 2 : 0);
            y += (br ? 2 : 0) + (bl ? 1 : 0);
        } else if (borders == 1) {
            if (!r && (tl || bl)) { y = 4; x = -1 + (bl ? 1 : 0) + (tl ? 2 : 0); }
            if (!l && (tr || br)) { y = 5; x = -1 + (br ? 1 : 0) + (tr ? 2 : 0); }
            if (!d && (tl || tr)) { y = 6; x = -1 + (tl ? 1 : 0) + (tr ? 2 : 0); }
            if (!u && (bl || br)) { y = 7; x = -1 + (bl ? 1 : 0) + (br ? 2 : 0); }
        } else if (borders == 2 && (u && l && tl || d && l && bl || u && r && tr || d && r && br)) x += 3;
        return x + 8 * y;
    }
}
