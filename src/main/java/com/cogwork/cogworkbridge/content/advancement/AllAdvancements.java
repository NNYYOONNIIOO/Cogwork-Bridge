package com.cogwork.cogworkbridge.content.advancement;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import javax.annotation.Nullable;

/** Server-only progression; machine awards belong to their actual placer. */
public final class AllAdvancements {
    private AllAdvancements() { }
    public static void award(@Nullable EntityPlayer player, String id) {
        if (!(player instanceof EntityPlayerMP p) || p instanceof FakePlayer || p.world.isRemote) return;
        if (!p.getServerWorld().getMinecraftServer().isCallingFromMinecraftThread()) {
            p.getServerWorld().addScheduledTask(() -> award(p, id));
            return;
        }
        Advancement advancement = p.getServerWorld().getMinecraftServer().getAdvancementManager()
                .getAdvancement(new ResourceLocation(Reference.MOD_ID, id));
        if (advancement == null || p.getAdvancements().getProgress(advancement).isDone()) return;
        for (String criterion : advancement.getCriteria().keySet()) p.getAdvancements().grantCriterion(advancement, criterion);
    }
    @Nullable public static EntityPlayer owner(World world, BlockPos pos) {
        if (world == null || world.isRemote) return null;
        AdvancementOwnerData data = AdvancementOwnerData.get(world.getTileEntity(pos));
        World serverWorld = world instanceof com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld fake ? fake.getParentWorld() : world;
        if (data == null || data.owner == null || serverWorld.getMinecraftServer() == null) return null;
        return serverWorld.getMinecraftServer().getPlayerList().getPlayerByUUID(data.owner);
    }
    public static void awardAt(World world, BlockPos pos, String id) { award(owner(world, pos), id); }
    public static void awardNear(World world, BlockPos pos, String id, int range) {
        EntityPlayer p = owner(world, pos);
        BlockPos location = world instanceof com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld c ? c.worldPosition(pos) : pos;
        if (p != null && p.dimension == world.provider.getDimension() && p.getDistanceSqToCenter(location) <= range * range) award(p, id);
    }
    public static void awardAction(World world, BlockPos pos, @Nullable EntityPlayer actor, String id) {
        if (world == null || world.isRemote) return;
        if (actor instanceof FakePlayer && actor.getEntityData().hasUniqueId("CogworkDeployerOwner") && actor.getServer() != null)
            award(actor.getServer().getPlayerList().getPlayerByUUID(actor.getEntityData().getUniqueId("CogworkDeployerOwner")), id);
        else award(actor, id);
    }
    public static void registerOwner(World world, BlockPos pos, EntityPlayer player) {
        if (world == null || world.isRemote || !(player instanceof EntityPlayerMP) || player instanceof FakePlayer) return;
        TileEntity tile = world.getTileEntity(pos);
        AdvancementOwnerData data = AdvancementOwnerData.get(tile);
        if (data != null) { data.owner = player.getUniqueID(); tile.markDirty(); }
    }
    public static void count(TileEntity tile, String key, int amount, int target, String id) {
        AdvancementOwnerData data = AdvancementOwnerData.get(tile);
        if (data == null || tile.getWorld() == null || tile.getWorld().isRemote) return;
        if (data.add(key, amount) >= target) { awardAt(tile.getWorld(), tile.getPos(), id); data.add(key, -target); }
        tile.markDirty();
    }
    public static void food(TileEntity tile, ItemStack output) {
        if (output.isEmpty() || output.getItem().getRegistryName() == null) return;
        int bit = switch (output.getItem().getRegistryName().getPath()) {
            case "chocolate_glazed_berries", "chocolate_berries" -> 1;
            case "honeyed_apple" -> 2; case "sweet_roll" -> 4; default -> 0;
        };
        AdvancementOwnerData data = AdvancementOwnerData.get(tile);
        if (data != null && bit != 0) {
            if (data.bits("Foods", bit) == 7) awardAt(tile.getWorld(), tile.getPos(), "foods");
            tile.markDirty();
        }
    }
    public static void checkPlayerEquipment(EntityPlayer p) {
        if (p.getEntityData().hasKey("CogworkEjectorStart")) {
            long elapsed = p.world.getTotalWorldTime() - p.getEntityData().getLong("CogworkEjectorTime");
            if (p.getDistanceSq(net.minecraft.nbt.NBTUtil.getPosFromTag(p.getEntityData().getCompoundTag("CogworkEjectorStart"))) > 900D) {
                award(p, "ejector_maxed"); p.getEntityData().removeTag("CogworkEjectorStart");
            } else if (elapsed > 600 || elapsed > 3 && p.onGround) p.getEntityData().removeTag("CogworkEjectorStart");
        }
        if (p.ticksExisted % 20 != 0) return;
        if (com.cogwork.cogworkbridge.content.equipment.CardboardArmorItem.isHiding(p)) award(p, "cardboard_armor");
        boolean main = p.getHeldItemMainhand().getItem() == AllItems.EXTENDO_GRIP;
        boolean off = p.getHeldItemOffhand().getItem() == AllItems.EXTENDO_GRIP;
        if (main || off) award(p, "extendo_grip");
        if (main && off) award(p, "extendo_grip_dual");
        if (p.getItemStackFromSlot(EntityEquipmentSlot.HEAD).getItem() == AllItems.GOGGLES) {
            net.minecraft.util.math.Vec3d eye = p.getPositionEyes(1);
            net.minecraft.util.math.RayTraceResult hit = p.world.rayTraceBlocks(eye, eye.add(p.getLookVec().scale(6)), false, false, false);
            if (hit != null && hit.getBlockPos() != null && p.world.getTileEntity(hit.getBlockPos()) instanceof KineticTileEntity gauge
                    && gauge.getBlockType().getRegistryName() != null && "stressometer".equals(gauge.getBlockType().getRegistryName().getPath())
                    && gauge.getSpeed() != 0) {
                awardAt(p.world, gauge.getPos(), "stressometer");
                if (gauge.getNetworkCapacity() > 0 && gauge.getNetworkStress() / gauge.getNetworkCapacity() >= 1F) awardAt(p.world, gauge.getPos(), "stressometer_maxed");
            }
        }
    }
    public static void checkPoweredMachine(KineticTileEntity tile) {
        World world = tile.getWorld();
        if (world == null || world.isRemote || tile.getSpeed() == 0F || world.getTotalWorldTime() % 20L != 0L) return;
        ResourceLocation name = tile.getBlockType().getRegistryName();
        if (name == null || !Reference.MOD_ID.equals(name.getNamespace())) return;
        String id = switch (name.getPath()) {
            case "encased_fan" -> "encased_fan"; case "mechanical_pump" -> "mechanical_pump_0";
            case "deployer" -> "deployer"; case "mechanical_crafter" -> "mechanical_crafter"; default -> null;
        };
        if (id != null) awardAt(world, tile.getPos(), id);
        if ("mechanical_crafter".equals(id) && Math.abs(tile.getSpeed()) < 5) awardAt(world, tile.getPos(), "crafter_lazy_000");
    }
    public static void assembled(World world, BlockPos pos, com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraption contraption) {
        for (var snapshot : contraption.blocks().values()) {
            var block = snapshot.state().getBlock();
            if (block instanceof com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillBlock
                    || block instanceof com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock
                    || block instanceof com.cogwork.cogworkbridge.content.contraptions.actors.harvester.MechanicalHarvesterBlock) {
                awardAt(world, pos, "contraption_actors"); return;
            }
        }
    }
}
