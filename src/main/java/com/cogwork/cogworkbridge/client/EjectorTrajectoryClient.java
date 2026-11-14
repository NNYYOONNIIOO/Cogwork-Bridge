package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.ejector.EjectorBlock;
import com.cogwork.cogworkbridge.content.logistics.ejector.EjectorItem;
import com.cogwork.cogworkbridge.content.logistics.ejector.EjectorTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.glue.client.SuperGlueOutlineRenderer;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Shows the same ballistic path while choosing a target or inspecting with a wrench. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class EjectorTrajectoryClient {
    private EjectorTrajectoryClient() { }

    private record Preview(BlockPos origin, BlockPos target, EnumFacing facing,
                           EjectorTileEntity.LaunchTrajectory trajectory, boolean valid) { }

    private static Preview preview(Minecraft mc) {
        if (mc.world == null || mc.player == null || mc.currentScreen != null) return null;
        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.sideHit == null) return null;
        BlockPos selected = hit.getBlockPos();
        for (EnumHand hand : EnumHand.values()) {
            ItemStack held = mc.player.getHeldItem(hand);
            if (held.getItem() == AllItems.WRENCH) {
                TileEntity tile = mc.world.getTileEntity(selected);
                if (tile instanceof EjectorTileEntity ejector
                        && mc.world.getBlockState(selected).getBlock() instanceof EjectorBlock) {
                    EnumFacing facing = mc.world.getBlockState(selected).getValue(EjectorBlock.FACING);
                    return new Preview(selected, ejector.targetPosition(), facing, ejector.trajectory(), true);
                }
            }
            if (!(held.getItem() instanceof EjectorItem)) continue;
            BlockPos target = EjectorItem.selectedTarget(held, mc.world.provider.getDimension());
            if (target == null) continue;
            BlockPos origin = mc.world.getBlockState(selected).getBlock().isReplaceable(mc.world, selected)
                    ? selected : selected.offset(hit.sideHit);
            BlockPos diff = target.subtract(origin);
            int dx = diff.getX(), dz = diff.getZ(), vertical = diff.getY();
            boolean valid = (dx == 0 || dz == 0) && Math.abs(vertical) <= 32;
            if (Math.abs(dx) >= Math.abs(dz)) dz = 0; else dx = 0;
            int distance = Math.abs(dx) + Math.abs(dz);
            if (distance < 1 || distance > 32 || Math.abs(vertical) > 32) return null;
            EnumFacing facing = dx > 0 ? EnumFacing.EAST : dx < 0 ? EnumFacing.WEST
                    : dz > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
            return new Preview(origin, target, facing, EjectorTileEntity.trajectory(distance, vertical), valid);
        }
        return null;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        Preview preview = preview(mc);
        if (preview == null) return;
        double duration = preview.trajectory().duration();
        int segments = Math.max(2, (int) (duration / 3D) + 1);
        double spacing = duration / segments;
        double offset = (mc.world.getTotalWorldTime() / 3D) % spacing;
        for (int i = 0; i <= segments; i++) {
            Vec3d point = preview.trajectory().position(offset + i * spacing, preview.facing(), preview.origin());
            mc.world.spawnParticle(EnumParticleTypes.REDSTONE, point.x, point.y, point.z,
                    preview.valid() ? 0.62D : 1D, preview.valid() ? 0.87D : 0.44D, 0.45D);
        }
    }

    @SubscribeEvent
    public static void renderTarget(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        Preview preview = preview(mc);
        Entity camera = mc.getRenderViewEntity();
        if (preview == null || camera == null) return;
        if (mc.player.getHeldItemMainhand().getItem() != AllItems.WRENCH
                && mc.player.getHeldItemOffhand().getItem() != AllItems.WRENCH) return;
        double p = event.getPartialTicks();
        double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * p;
        double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * p;
        double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * p;
        AxisAlignedBB target = new AxisAlignedBB(preview.target()).grow(0.004D).offset(-x, -y, -z);
        SuperGlueOutlineRenderer.renderFrame(target, 1F, 0.65F, 0.15F, 0.8F, 1F / 16F);
    }
}
