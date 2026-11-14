package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class SoulPulseClient {
    private static final class Pulse {
        final BlockPos center; final int distance; int age;
        Pulse(BlockPos center, int distance) { this.center = center; this.distance = distance; }
    }
    private static final List<Pulse> PULSES = new ArrayList<>();
    private static final Map<BlockPos, Long> OCCUPIED = new HashMap<>();
    private static World previousWorld;
    public static void add(BlockPos pos, int distance) { PULSES.add(new Pulse(pos.toImmutable(), Math.max(1, Math.min(10, distance)))); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        World world = Minecraft.getMinecraft().world;
        if (world != previousWorld) { PULSES.clear(); OCCUPIED.clear(); previousWorld = world; }
        if (world == null) return;
        Item bell = Item.getItemFromBlock(AllBlocks.HAUNTED_BELL);
        for (EntityPlayer player : world.playerEntities)
            if (player.ticksExisted % 8 == 0 && (player.getHeldItemMainhand().getItem() == bell || player.getHeldItemOffhand().getItem() == bell)) add(player.getPosition(), 3);
        Iterator<Pulse> iterator = PULSES.iterator();
        while (iterator.hasNext()) {
            Pulse pulse = iterator.next();
            if (pulse.age % 6 == 0) spawnLayer(world, pulse, pulse.age / 6 + 1);
            if (++pulse.age >= pulse.distance * 6) iterator.remove();
        }
        if (world.getTotalWorldTime() % 20 == 0) OCCUPIED.values().removeIf(until -> until <= world.getTotalWorldTime());
    }
    private static void spawnLayer(World world, Pulse pulse, int layer) {
        int radius = pulse.distance;
        for (int x = -radius; x <= radius; x++) for (int y = -radius; y <= radius; y++) for (int z = -radius; z <= radius; z++) {
            if (Math.round(Math.sqrt(x * x + y * y + z * z)) != layer) continue;
            BlockPos pos = pulse.center.add(x, y, z);
            if (!world.isBlockLoaded(pos) || OCCUPIED.containsKey(pos) || world.getLightFor(EnumSkyBlock.BLOCK, pos) > 0
                    || !world.isAirBlock(pos) || !world.isSideSolid(pos.down(), EnumFacing.UP)) continue;
            OCCUPIED.put(pos, world.getTotalWorldTime() + 100);
            Minecraft.getMinecraft().effectRenderer.addEffect(new com.cogwork.cogworkbridge.client.particle.SoulParticle(
                    world, pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D));
        }
    }
}
