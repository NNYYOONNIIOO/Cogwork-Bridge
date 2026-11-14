package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.chassis.ChassisConnectivity;
import com.cogwork.cogworkbridge.content.contraptions.chassis.ChassisTileEntity;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Shows the chassis' captured blocks while its wrench setting is inspected. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ChassisRangePreview {
    private static World cachedWorld;
    private static BlockPos cachedPos;
    private static long cachedTick = Long.MIN_VALUE;
    private static boolean cachedBulk;
    private static Set<BlockPos> selected = Set.of();

    private ChassisRangePreview() { }

    @SubscribeEvent
    public static void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null || mc.currentScreen != null
                || mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK
                || (mc.player.getHeldItemMainhand().getItem() != AllItems.WRENCH
                && mc.player.getHeldItemOffhand().getItem() != AllItems.WRENCH)) return;
        BlockPos pos = mc.objectMouseOver.getBlockPos();
        TileEntity tile = mc.world.getTileEntity(pos);
        if (!(tile instanceof ChassisTileEntity chassis)) return;
        boolean bulk = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        long tick = mc.world.getTotalWorldTime();
        if (cachedWorld != mc.world || !pos.equals(cachedPos) || cachedBulk != bulk
                || tick < cachedTick || tick - cachedTick >= 5) {
            cachedWorld = mc.world;
            cachedPos = pos.toImmutable();
            cachedBulk = bulk;
            cachedTick = tick;
            selected = collect(mc.world, chassis, bulk);
        }
        Entity camera = mc.getRenderViewEntity();
        if (camera == null || selected.isEmpty()) return;
        float partial = event.getPartialTicks();
        double cameraX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partial;
        double cameraY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partial;
        double cameraZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partial;
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        float width = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.depthMask(false);
            GL11.glLineWidth(3.0F);
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            for (BlockPos block : selected) {
                double x0 = block.getX() - cameraX - 0.003D, x1 = x0 + 1.006D;
                double y0 = block.getY() - cameraY - 0.003D, y1 = y0 + 1.006D;
                double z0 = block.getZ() - cameraZ - 0.003D, z1 = z0 + 1.006D;
                for (double y : new double[]{y0, y1}) for (double z : new double[]{z0, z1})
                    line(buffer, x0, y, z, x1, y, z);
                for (double x : new double[]{x0, x1}) for (double z : new double[]{z0, z1})
                    line(buffer, x, y0, z, x, y1, z);
                for (double x : new double[]{x0, x1}) for (double y : new double[]{y0, y1})
                    line(buffer, x, y, z0, x, y, z1);
            }
            tessellator.draw();
        } finally {
            GL11.glLineWidth(width);
            GlStateManager.depthMask(depthMask);
            if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (!blend) GlStateManager.disableBlend();
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    private static Set<BlockPos> collect(World world, ChassisTileEntity chassis, boolean bulk) {
        Set<BlockPos> blocks = new LinkedHashSet<>();
        List<ChassisTileEntity> group = bulk ? chassis.collectChassisGroup() : List.of(chassis);
        ChassisConnectivity.Access access = new ChassisConnectivity.Access() {
            @Override public IBlockState state(BlockPos pos) {
                return world.isBlockLoaded(pos) ? world.getBlockState(pos) : null;
            }
            @Override public NBTTagCompound tileData(BlockPos pos) {
                TileEntity tile = world.getTileEntity(pos);
                return tile == null ? null : tile.writeToNBT(new NBTTagCompound());
            }
            @Override public boolean terminal(BlockPos pos) { return false; }
        };
        for (ChassisTileEntity member : group) {
            ChassisConnectivity.collect(access, member.getPos(), (first, second) -> {
                if (!(world.getTileEntity(second) instanceof ChassisTileEntity)) blocks.add(second);
            });
        }
        return blocks;
    }

    private static void line(BufferBuilder buffer, double x0, double y0, double z0,
                             double x1, double y1, double z1) {
        buffer.pos(x0, y0, z0).color(1F, 1F, 1F, .8F).endVertex();
        buffer.pos(x1, y1, z1).color(1F, 1F, 1F, .8F).endVertex();
    }
}
