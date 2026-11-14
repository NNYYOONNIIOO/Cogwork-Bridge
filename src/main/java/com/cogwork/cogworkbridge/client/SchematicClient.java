package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.schematics.*;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class SchematicClient {
    private static BlockPos first, second;
    private static final Map<String, SchematicData> CACHE = new LinkedHashMap<String, SchematicData>(8, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, SchematicData> e) { return size() > 4; }
    };
    private static final Map<String, Transfer> DOWNLOADS = new LinkedHashMap<String, Transfer>(8, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Transfer> entry) { return size() > 4; }
    };
    private static final Map<String, Long> REQUESTS = new LinkedHashMap<String, Long>(8, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> entry) { return size() > 8; }
    };
    private static final class Transfer {
        final int total;
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        long progressAt = System.currentTimeMillis();
        Transfer(int total) { this.total = total; }
    }
    private SchematicClient() { }
    public static Path directory() { return Minecraft.getMinecraft().gameDir.toPath().resolve("schematics"); }
    public static void select(boolean clear, @Nullable BlockPos hit, @Nullable EnumFacing face) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        if (clear) {
            first = second = null;
            mc.player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schematic.selection_reset"), true); return;
        }
        if (second != null) { mc.displayGuiScreen(new SchematicPromptScreen()); return; }
        if (hit == null) { mc.player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schematic.no_target"), true); return; }
        // Horizontal and vertical faces both select the targeted block.
        if (first == null) { first = hit; mc.player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schematic.first"), true); }
        else { second = hit; mc.player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schematic.second"), true); }
    }
    public static void save(String suggested, boolean convertImmediately) {
        Minecraft mc = Minecraft.getMinecraft();
        if (first == null || second == null || mc.world == null || mc.player == null) return;
        BlockPos min = new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
        BlockPos size = new BlockPos(Math.abs(first.getX() - second.getX()) + 1, Math.abs(first.getY() - second.getY()) + 1, Math.abs(first.getZ() - second.getZ()) + 1);
        long volume = (long) size.getX() * size.getY() * size.getZ();
        if (size.getX() > SchematicData.MAX_AXIS || size.getY() > SchematicData.MAX_AXIS || size.getZ() > SchematicData.MAX_AXIS || volume > SchematicData.MAX_BLOCKS) {
            mc.player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schematic.too_large"), true); return;
        }
        for (int x = min.getX() >> 4; x <= (min.getX() + size.getX() - 1) >> 4; x++)
            for (int z = min.getZ() >> 4; z <= (min.getZ() + size.getZ() - 1) >> 4; z++)
                if (!mc.world.isBlockLoaded(new BlockPos(x << 4, min.getY(), z << 4))) {
                    mc.player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schematic.unloaded"), true); return;
                }
        try {
            byte[] bytes = SchematicData.capture(mc.world, min, size, mc.player.getName());
            Files.createDirectories(directory());
            String name = suggested.trim().replaceAll("[^\\p{L}\\p{N}_ -]", "_").trim();
            if (name.isEmpty()) name = "Schematic";
            if (name.length() > 64) name = name.substring(0, 64);
            Path out = directory().resolve(name + ".nbt");
            for (int i = 1; Files.exists(out); i++) out = directory().resolve(name + "_" + i + ".nbt");
            Files.write(out, bytes, StandardOpenOption.CREATE_NEW);
            first = second = null;
            mc.player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schematic.saved", out.getFileName().toString()), true);
            if (convertImmediately) ModNetwork.CHANNEL.sendToServer(new SchematicInstantPacket(
                    out.getFileName().toString(), min, size, mc.player.inventory.currentItem));
        } catch (IOException | RuntimeException ex) {
            mc.player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schematic.invalid"), true);
        }
    }
    public static void receive(String id, int total, int offset, byte[] bytes) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (id.length() != 36 || total < 1 || total > SchematicData.MAX_COMPRESSED
                    || bytes.length == 0 || bytes.length > SchematicData.CHUNK || offset < 0
                    || offset > total || bytes.length > total - offset) return;
            Transfer transfer = DOWNLOADS.get(id);
            if (offset == 0) {
                transfer = new Transfer(total);
                DOWNLOADS.put(id, transfer);
            }
            if (transfer == null || transfer.total != total || transfer.data.size() != offset) {
                DOWNLOADS.remove(id); REQUESTS.remove(id); return;
            }
            transfer.data.write(bytes, 0, bytes.length);
            transfer.progressAt = System.currentTimeMillis();
            if (transfer.data.size() == total) {
                try { CACHE.put(id, SchematicData.read(transfer.data.toByteArray())); }
                catch (IOException ignored) { }
                DOWNLOADS.remove(id); REQUESTS.remove(id);
            }
        });
    }
    public static void request(ItemStack stack) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!stack.hasTagCompound() || mc.player == null) return;
        String id = stack.getTagCompound().getString("FileId");
        if (id.length() != 36 || CACHE.containsKey(id)) return;
        long now = System.currentTimeMillis();
        Transfer transfer = DOWNLOADS.get(id);
        if (transfer != null && now - transfer.progressAt < 10000) return;
        Long requestedAt = REQUESTS.get(id);
        if (requestedAt != null && now - requestedAt < 3000) return;
        DOWNLOADS.remove(id); REQUESTS.put(id, now);
        ModNetwork.CHANNEL.sendToServer(new SchematicActionPacket(3, 0, mc.player.inventory.currentItem));
    }
    @SubscribeEvent public static void scroll(net.minecraftforge.client.event.MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.isCanceled() || event.getDwheel() == 0 || mc.currentScreen != null || mc.player == null
                || mc.player.getHeldItemMainhand().getItem() != AllItems.SCHEMATIC_AND_QUILL
                || first == null || second == null || !net.minecraft.client.gui.GuiScreen.isCtrlKeyDown()) return;
        int minX = Math.min(first.getX(), second.getX()), maxX = Math.max(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY()), maxY = Math.max(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ()), maxZ = Math.max(first.getZ(), second.getZ());
        AxisAlignedBB box = new AxisAlignedBB(minX, minY, minZ, maxX + 1D, maxY + 1D, maxZ + 1D).grow(.35D);
        Vec3d eye = mc.player.getPositionEyes(1F);
        RayTraceResult hit = box.calculateIntercept(eye, eye.add(mc.player.getLook(1F).scale(75D)));
        if (hit == null || hit.sideHit == null) return;
        int amount = event.getDwheel() > 0 ? 1 : -1;
        if (box.contains(eye)) amount = -amount;
        switch (hit.sideHit) {
            case WEST -> minX = Math.min(maxX, minX - amount);
            case EAST -> maxX = Math.max(minX, maxX + amount);
            case DOWN -> minY = Math.min(maxY, minY - amount);
            case UP -> maxY = Math.max(minY, maxY + amount);
            case NORTH -> minZ = Math.min(maxZ, minZ - amount);
            case SOUTH -> maxZ = Math.max(minZ, maxZ + amount);
        }
        long sx = (long) maxX - minX + 1, sy = (long) maxY - minY + 1, sz = (long) maxZ - minZ + 1;
        if (sx <= SchematicData.MAX_AXIS && sy <= SchematicData.MAX_AXIS && sz <= SchematicData.MAX_AXIS
                && sx * sy * sz <= SchematicData.MAX_BLOCKS) {
            first = new BlockPos(minX, minY, minZ); second = new BlockPos(maxX, maxY, maxZ);
            mc.player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schematic.dimensions", sx, sy, sz), true);
        }
        event.setCanceled(true);
    }
    @SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) return;
        first = second = null; CACHE.clear(); REQUESTS.clear(); DOWNLOADS.clear();
        SchematicPreview.clear();
    }
    @SubscribeEvent public static void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        ItemStack held = mc.player.getHeldItemMainhand();
        double x = mc.getRenderManager().viewerPosX, y = mc.getRenderManager().viewerPosY, z = mc.getRenderManager().viewerPosZ;
        if (held.getItem() == AllItems.SCHEMATIC_AND_QUILL && first != null) {
            BlockPos end = second != null ? second
                    : mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK
                    ? mc.objectMouseOver.getBlockPos() : first;
            AxisAlignedBB box = new AxisAlignedBB(Math.min(first.getX(), end.getX()), Math.min(first.getY(), end.getY()), Math.min(first.getZ(), end.getZ()),
                    Math.max(first.getX(), end.getX()) + 1, Math.max(first.getY(), end.getY()) + 1, Math.max(first.getZ(), end.getZ()) + 1).offset(-x, -y, -z);
            GlStateManager.pushMatrix(); GlStateManager.disableTexture2D(); GlStateManager.enableBlend(); GlStateManager.disableDepth();
            GL11.glLineWidth(3F); RenderGlobal.drawSelectionBoundingBox(box, .35F, .62F, .95F, .9F);
            GL11.glLineWidth(1F); GlStateManager.enableDepth(); GlStateManager.enableTexture2D(); GlStateManager.disableBlend(); GlStateManager.popMatrix();
        }
        if (held.getItem() != AllItems.SCHEMATIC || !held.hasTagCompound()) return;
        request(held);
        if (!held.getTagCompound().getBoolean("Deployed")) return;
        SchematicData data = CACHE.get(held.getTagCompound().getString("FileId"));
        if (data == null) return;
        BlockPos anchor = SchematicData.anchor(held);
        Mirror mirror = SchematicData.mirror(held); Rotation rotation = SchematicData.rotation(held);
        GlStateManager.pushMatrix(); GlStateManager.translate(anchor.getX() - x, anchor.getY() - y, anchor.getZ() - z);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D(); GlStateManager.enableAlpha(); GlStateManager.alphaFunc(GL11.GL_GREATER, .01F);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.depthMask(false); GlStateManager.disableLighting();
        try { SchematicPreview.render(data, mirror, rotation); }
        finally {
            GlStateManager.color(1F, 1F, 1F, 1F); GlStateManager.alphaFunc(GL11.GL_GREATER, .1F);
            GlStateManager.enableLighting(); GlStateManager.depthMask(true); GlStateManager.disableBlend(); GlStateManager.popMatrix();
        }
    }
}
