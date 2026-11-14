package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.client.render.FastStateRendering;
import com.cogwork.cogworkbridge.content.equipment.SymmetryWandItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Floating mirror model and particles for configured wands in the hotbar. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class SymmetryPreviewClient {
    private static final String[] MODELS = {"plane", "crossplane", "tripleplane"};
    private static final IBakedModel[] BAKED = new IBakedModel[MODELS.length];

    private SymmetryPreviewClient() { }

    private static IModel model(String name) {
        ResourceLocation location = new ResourceLocation("create", "block/symmetry_effect/" + name);
        return ModelLoaderRegistry.getModelOrLogError(location, "Could not load symmetry model " + location);
    }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        for (String name : MODELS)
            for (ResourceLocation texture : model(name).getTextures()) event.getMap().registerSprite(texture);
    }

    @SubscribeEvent
    public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (int i = 0; i < MODELS.length; i++)
            BAKED[i] = model(MODELS[i]).bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK,
                    sprite -> atlas.getAtlasSprite(sprite.toString()));
    }

    @SubscribeEvent
    public static void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        Entity camera = mc.getRenderViewEntity();
        if (camera == null) return;
        float partial = event.getPartialTicks();
        double cameraX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partial;
        double cameraY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partial;
        double cameraZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partial;
        for (int slot = 0; slot < 9; slot++)
            renderWand(mc, mc.player.inventory.getStackInSlot(slot), cameraX, cameraY, cameraZ, partial);
        renderWand(mc, mc.player.getHeldItemOffhand(), cameraX, cameraY, cameraZ, partial);
    }

    private static void renderWand(Minecraft mc, ItemStack wand, double x, double y, double z, float partial) {
        if (!(wand.getItem() instanceof SymmetryWandItem) || !SymmetryWandItem.enabled(wand, mc.world)) return;
        int mode = SymmetryWandItem.mode(wand), alignment = SymmetryWandItem.alignment(wand);
        IBakedModel model = BAKED[mode];
        if (model == null) return;
        BlockPos pos = SymmetryWandItem.origin(wand, mc.player.getPosition());
        double bob = Math.sin((mc.world.getTotalWorldTime() + partial) / 16D) / 5D;
        float rotation = mode == 0 && alignment == 1 ? 90F : mode == 1 && alignment == 1 ? 45F : 0F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(pos.getX() - x + .5D, pos.getY() - y + .2D + bob, pos.getZ() - z + .5D);
        GlStateManager.rotate(rotation, 0, 1, 0);
        GlStateManager.translate(-.5D, 0, -.5D);
        FastStateRendering.getInstance().renderFast(model, Blocks.AIR.getDefaultState());
        GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || mc.world == null || mc.player == null
                || mc.world.getTotalWorldTime() % 10L != 0L) return;
        for (int slot = 0; slot < 9; slot++) particle(mc, mc.player.inventory.getStackInSlot(slot));
        particle(mc, mc.player.getHeldItemOffhand());
    }

    private static void particle(Minecraft mc, ItemStack wand) {
        if (!(wand.getItem() instanceof SymmetryWandItem) || !SymmetryWandItem.enabled(wand, mc.world)) return;
        BlockPos pos = SymmetryWandItem.origin(wand, mc.player.getPosition());
        mc.world.spawnParticle(EnumParticleTypes.END_ROD, pos.getX() + .35D + mc.world.rand.nextDouble() * .3D,
                pos.getY() + .25D, pos.getZ() + .35D + mc.world.rand.nextDouble() * .3D,
                0D, mc.world.rand.nextDouble() / 8D, 0D);
    }
}
