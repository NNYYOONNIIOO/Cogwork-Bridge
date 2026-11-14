package com.cogwork.cogworkbridge.content.schematics;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Bounded creative paste: the server performs a limited amount of work per tick. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class CreativeSchematicPlacement {
    private static final Map<UUID, Job> jobs = new HashMap<>();
    private CreativeSchematicPlacement() { }
    public static void start(EntityPlayerMP player, net.minecraft.item.ItemStack plan) {
        if (!player.capabilities.isCreativeMode || !plan.hasTagCompound() || !plan.getTagCompound().getBoolean("Deployed")) return;
        Path file = SchematicData.serverFile(player.world, plan);
        try {
            if (file == null) return;
            SchematicData data = SchematicData.read(SchematicData.readFile(file));
            if (jobs.size() >= 8 && !jobs.containsKey(player.getUniqueID())) return;
            jobs.put(player.getUniqueID(), new Job(player, plan, data));
            player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schematic.creative_started"), true);
        } catch (IOException ignored) { }
    }
    private static final class Job {
        final UUID owner; final World world; final SchematicData data; final BlockPos origin;
        final Mirror mirror; final Rotation rotation; int cursor, skipped;
        Job(EntityPlayerMP player, net.minecraft.item.ItemStack plan, SchematicData data) {
            owner = player.getUniqueID(); world = player.world; this.data = data; origin = SchematicData.anchor(plan);
            mirror = SchematicData.mirror(plan); rotation = SchematicData.rotation(plan);
        }
        boolean tick() {
            EntityPlayerMP player = world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner);
            if (player == null || player.world != world || !player.capabilities.isCreativeMode) return true;
            int entityEnd = data.blocks.size() + data.entities.size();
            int total = entityEnd + data.containedFluids.size();
            int end = Math.min(cursor + 256, total);
            while (cursor < end) {
                if (cursor >= entityEnd) {
                    SchematicData.Entry entry = data.containedFluids.get(cursor++ - entityEnd);
                    BlockPos at = entry.target(origin, mirror, rotation);
                    if (!allowed(player, at) || !SchematicFluids.placeContained(world, at,
                            entry.transformedState(mirror, rotation), entry.containedFluid, entry.storedFluid)) skipped++;
                    continue;
                }
                if (cursor >= data.blocks.size()) { spawn(player, data.entities.get(cursor++ - data.blocks.size())); continue; }
                SchematicData.Entry entry = data.blocks.get(cursor++);
                if (entry.state.getMaterial() == net.minecraft.block.material.Material.AIR) continue;
                BlockPos at = entry.target(origin, mirror, rotation);
                if (!allowed(player, at)) { skipped++; continue; }
                IBlockState old = world.getBlockState(at);
                if (old.getBlockHardness(world, at) < 0) { skipped++; continue; }
                try {
                    IBlockState state = entry.transformedState(mirror, rotation);
                    if (!world.setBlockState(at, state, 3)) continue;
                    if (entry.tile != null) {
                        TileEntity tile = world.getTileEntity(at);
                        if (tile != null) {
                            NBTTagCompound nbt = entry.tile.copy();
                            nbt.setInteger("x", at.getX()); nbt.setInteger("y", at.getY()); nbt.setInteger("z", at.getZ());
                            tile.readFromNBT(nbt); tile.mirror(mirror); tile.rotate(rotation); tile.markDirty();
                            world.notifyBlockUpdate(at, state, state, 3);
                        }
                    }
                } catch (RuntimeException ex) { skipped++; }
            }
            if (cursor < total) return false;
            player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.schematic.creative_done", skipped), true);
            return true;
        }
        private boolean allowed(EntityPlayerMP player, BlockPos at) {
            return at.getY() >= 0 && at.getY() < world.getHeight() && world.isBlockLoaded(at)
                    && world.getWorldBorder().contains(at) && world.isBlockModifiable(player, at)
                    && player.getDistanceSq(at) <= 256D * 256D;
        }
        private void spawn(EntityPlayerMP player, SchematicData.EntityEntry entry) {
            Vec3d at = entry.target(origin, mirror, rotation); BlockPos pos = new BlockPos(at);
            if (!allowed(player, pos)) { skipped++; return; }
            NBTTagCompound nbt = entry.data.copy(); NBTTagList coords = new NBTTagList();
            coords.appendTag(new NBTTagDouble(at.x)); coords.appendTag(new NBTTagDouble(at.y)); coords.appendTag(new NBTTagDouble(at.z));
            nbt.setTag("Pos", coords); nbt.setUniqueId("UUID", UUID.randomUUID());
            BlockPos hanging = SchematicData.transformed(entry.blockPos, mirror, rotation).add(origin);
            nbt.setInteger("TileX", hanging.getX()); nbt.setInteger("TileY", hanging.getY()); nbt.setInteger("TileZ", hanging.getZ());
            try {
                Entity entity = EntityList.createEntityFromNBT(nbt, world);
                if (entity == null) { skipped++; return; }
                float yaw = entity.getMirroredYaw(mirror); yaw += entity.rotationYaw - entity.getRotatedYaw(rotation);
                entity.setLocationAndAngles(at.x, at.y, at.z, yaw, entity.rotationPitch);
                if (!world.spawnEntity(entity)) skipped++;
            } catch (RuntimeException ex) { skipped++; }
        }
    }
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event) {
        if (event.side.isClient() || event.phase != TickEvent.Phase.END) return;
        Iterator<Job> it = jobs.values().iterator();
        while (it.hasNext()) { Job job = it.next(); if (job.world == event.world && job.tick()) it.remove(); }
    }
    @SubscribeEvent public static void unload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) jobs.values().removeIf(job -> job.world == event.getWorld());
    }
}
