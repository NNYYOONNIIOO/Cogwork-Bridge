package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.client.render.FastStateRendering;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.*;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class MinecartCouplingClient {
    private record Pair(UUID first, UUID second) {
        static Pair of(UUID a, UUID b) { return a.compareTo(b) <= 0 ? new Pair(a, b) : new Pair(b, a); }
    }
    private static final Set<Pair> LINKS = new HashSet<>();
    private static UUID selection;
    private static World selectionWorld;
    private static IBakedModel attachment, ring, connector;
    private MinecartCouplingClient() { }

    public static void update(UUID first, UUID second, boolean present) {
        Pair pair = Pair.of(first, second);
        if (present) LINKS.add(pair); else LINKS.remove(pair);
    }
    private static IModel model(String part) {
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", "entity/minecart_coupling/" + part),
                "Missing minecart coupling " + part);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String part : new String[]{"attachment", "ring", "connector"})
            for (ResourceLocation sprite : model(part).getTextures()) event.getMap().registerSprite(sprite);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        attachment = model("attachment").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, s -> atlas.getAtlasSprite(s.toString()));
        ring = model("ring").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, s -> atlas.getAtlasSprite(s.toString()));
        connector = model("connector").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, s -> atlas.getAtlasSprite(s.toString()));
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interact(PlayerInteractEvent.EntityInteract event) { select(event, event.getTarget()); }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interactAt(PlayerInteractEvent.EntityInteractSpecific event) { select(event, event.getTarget()); }
    private static void select(PlayerInteractEvent event, Entity target) {
        if (!event.getWorld().isRemote || !(target instanceof EntityMinecart cart)
                || event.getItemStack().getItem() != AllItems.MINECART_COUPLING) return;
        if (event.getEntityPlayer().isSneaking()) selection = null;
        else if (selection == null || selectionWorld != event.getWorld() || selection.equals(cart.getUniqueID())) {
            selection = cart.getUniqueID(); selectionWorld = event.getWorld();
        } else selection = null;
        event.setCancellationResult(EnumActionResult.SUCCESS); event.setCanceled(true);
    }
    @SubscribeEvent public static void unload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) { LINKS.clear(); selection = null; selectionWorld = null; }
    }
    @SubscribeEvent public static void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;
        Map<UUID, EntityMinecart> carts = new HashMap<>();
        for (Entity entity : mc.world.loadedEntityList)
            if (entity instanceof EntityMinecart cart && !cart.isDead) carts.put(cart.getUniqueID(), cart);
        double x = mc.getRenderManager().viewerPosX, y = mc.getRenderManager().viewerPosY, z = mc.getRenderManager().viewerPosZ;
        float partial = event.getPartialTicks();
        for (Pair pair : LINKS) {
            EntityMinecart a = carts.get(pair.first), b = carts.get(pair.second);
            if (a == null || b == null) continue;
            Vec3d first = position(a, partial), second = position(b, partial);
            Vec3d delta = second.subtract(first);
            double distance = delta.length();
            if (distance < 1.0E-4D || distance > 48D) continue;
            Vec3d direction = delta.scale(1D / distance);
            Vec3d start = first.add(direction.scale(.78D)), end = second.subtract(direction.scale(.78D));
            Vec3d shaft = end.subtract(start);
            double length = shaft.length();
            float yaw = (float) -Math.toDegrees(Math.atan2(shaft.z, shaft.x));
            float pitch = (float) Math.toDegrees(Math.atan2(shaft.y, Math.sqrt(shaft.x * shaft.x + shaft.z * shaft.z)));
            GlStateManager.pushMatrix(); GlStateManager.translate(start.x - x, start.y - y, start.z - z);
            GlStateManager.rotate(yaw, 0, 1, 0); GlStateManager.rotate(pitch, 0, 0, 1);
            FastStateRendering.getInstance().renderFast(attachment, Blocks.AIR.getDefaultState());
            FastStateRendering.getInstance().renderFast(ring, Blocks.AIR.getDefaultState());
            GlStateManager.pushMatrix(); GlStateManager.scale(length, 1, 1);
            FastStateRendering.getInstance().renderFast(connector, Blocks.AIR.getDefaultState()); GlStateManager.popMatrix();
            GlStateManager.translate(length, 0, 0); GlStateManager.rotate(180F, 0, 1, 0);
            FastStateRendering.getInstance().renderFast(attachment, Blocks.AIR.getDefaultState());
            FastStateRendering.getInstance().renderFast(ring, Blocks.AIR.getDefaultState()); GlStateManager.popMatrix();
        }
        if (selectionWorld != mc.world || selection == null
                || mc.player.getHeldItemMainhand().getItem() != AllItems.MINECART_COUPLING
                && mc.player.getHeldItemOffhand().getItem() != AllItems.MINECART_COUPLING) {
            selection = null; return;
        }
        EntityMinecart selected = carts.get(selection);
        if (selected == null) return;
        Vec3d now = position(selected, partial);
        AxisAlignedBB box = selected.getEntityBoundingBox().offset(
                now.x - selected.posX - x, now.y - .375D - selected.posY - y, now.z - selected.posZ - z).grow(.08D);
        GlStateManager.pushMatrix(); GlStateManager.disableTexture2D(); GlStateManager.enableBlend();
        GlStateManager.glLineWidth(3F); RenderGlobal.drawSelectionBoundingBox(box, .7F, 1F, .7F, .9F);
        GlStateManager.glLineWidth(1F); GlStateManager.enableTexture2D(); GlStateManager.disableBlend(); GlStateManager.popMatrix();
    }
    private static Vec3d position(Entity entity, float partial) {
        return new Vec3d(entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partial,
                entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partial + .375D,
                entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partial);
    }
}
