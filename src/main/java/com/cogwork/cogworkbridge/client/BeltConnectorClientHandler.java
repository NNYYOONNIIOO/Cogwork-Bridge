package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltConnectorItem;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class BeltConnectorClientHandler {
    private static final String FIRST = "CogworkBridgeBeltFirst";
    private static final int MAX_LENGTH = 20;

    private BeltConnectorClientHandler() { }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        for (EnumHand hand : EnumHand.values()) {
            ItemStack held = mc.player.getHeldItem(hand);
            if (held.getItem() != AllItems.BELT_CONNECTOR || !held.hasTagCompound()) continue;
            NBTTagCompound root = held.getTagCompound();
            if (root == null || !root.hasKey(FIRST, 10)) continue;
            NBTTagCompound selection = root.getCompoundTag(FIRST);
            if (selection.getInteger("Dimension") != mc.world.provider.getDimension()) continue;

            BlockPos start = new BlockPos(selection.getInteger("X"), selection.getInteger("Y"),
                    selection.getInteger("Z"));
            if (!mc.world.isBlockLoaded(start)
                    || !(mc.world.getBlockState(start).getBlock() instanceof ShaftBlock)) continue;

            RayTraceResult hit = mc.objectMouseOver;
            if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.sideHit == null) {
                if (mc.world.rand.nextInt(50) == 0) {
                    double x = start.getX() + 0.5D + (mc.world.rand.nextDouble() - 0.5D) * 0.5D;
                    double y = start.getY() + 0.5D + (mc.world.rand.nextDouble() - 0.5D) * 0.5D;
                    double z = start.getZ() + 0.5D + (mc.world.rand.nextDouble() - 0.5D) * 0.5D;
                    mc.world.spawnParticle(EnumParticleTypes.REDSTONE, x, y, z, 0.3D, 0.9D, 0.5D);
                }
                return;
            }

            BlockPos selected = hit.getBlockPos();
            if (mc.world.getBlockState(selected).getBlock().isReplaceable(mc.world, selected)) return;
            if (!(mc.world.getBlockState(selected).getBlock() instanceof ShaftBlock)) {
                selected = selected.offset(hit.sideHit);
            }
            if (selected.distanceSq(start) >= MAX_LENGTH * MAX_LENGTH) return;

            EnumFacing.Axis axis = mc.world.getBlockState(start).getValue(KineticBlock.AXIS);
            BlockPos projected = switch (axis) {
                case X -> new BlockPos(start.getX(), selected.getY(), selected.getZ());
                case Y -> new BlockPos(selected.getX(), start.getY(), selected.getZ());
                case Z -> new BlockPos(selected.getX(), selected.getY(), start.getZ());
            };
            boolean canConnect = BeltConnectorItem.canConnect(mc.world, mc.player, start, selected);
            Vec3d from = new Vec3d(start.getX() + 0.5D, start.getY() + 0.5D, start.getZ() + 0.5D);
            Vec3d to = new Vec3d(projected.getX() + 0.5D, projected.getY() + 0.5D,
                    projected.getZ() + 0.5D);
            Vec3d diff = to.subtract(from);
            double length = Math.max(Math.abs(diff.x), Math.max(Math.abs(diff.y), Math.abs(diff.z)));
            if (length == 0) return;
            Vec3d step = diff.scale(1D / length);
            double red = canConnect ? 0.3D : 0.9D;
            double green = canConnect ? 0.9D : 0.3D;
            for (double distance = 0; distance < length; distance += 1D / 16D) {
                if (mc.world.rand.nextInt(10) != 0) continue;
                Vec3d position = from.add(step.scale(distance));
                mc.world.spawnParticle(EnumParticleTypes.REDSTONE, position.x, position.y, position.z,
                        red, green, 0.5D);
            }
            return;
        }
    }
}
