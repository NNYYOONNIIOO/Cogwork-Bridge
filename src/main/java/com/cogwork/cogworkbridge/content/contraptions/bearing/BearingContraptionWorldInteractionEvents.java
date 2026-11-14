package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/** Player cleanup and explosion damage for carried contraption blocks. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class BearingContraptionWorldInteractionEvents {

    private BearingContraptionWorldInteractionEvents() {
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        if (event.getWorld().isRemote
                || !event.getWorld().getGameRules().getBoolean("mobGriefing")) {
            return;
        }
        List<BearingContraptionEntity> contraptions =
                new ArrayList<>(event.getWorld().getEntities(
                        BearingContraptionEntity.class,
                        candidate -> !candidate.isDead
                                && candidate.hasContraptionData()));
        Vec3d origin = event.getExplosion().getPosition();
        for (BearingContraptionEntity entity : contraptions) {
            if (!entity.hasContraptionData()) {
                continue;
            }
            for (BearingContraption.Snapshot snapshot :
                    new ArrayList<>(entity.getContraption().blocks().values())) {
                if (!entity.hasContraptionData()) {
                    break;
                }
                BlockPos local = entity.localPos(snapshot);
                Vec3d center = entity.localBlockCenterToWorld(local);
                if (!canExplosionReach(event, entity, snapshot, local,
                        center, origin)) {
                    continue;
                }
                destroyByExplosion(event, entity, local, snapshot);
            }
        }
    }

    private static boolean canExplosionReach(
            ExplosionEvent.Detonate event, BearingContraptionEntity entity,
            BearingContraption.Snapshot snapshot, BlockPos local,
            Vec3d center, Vec3d origin) {
        AxisAlignedBB shape = entity.localCollisionBoxToWorld(
                local, entity.getAngle());
        double distance = Math.sqrt(origin.squareDistanceTo(center));
        double resistance = snapshot.state().getBlock()
                .getExplosionResistance(
                        event.getWorld(), entity.explosionReference(local, origin),
                        event.getExplosion().getExplosivePlacedBy(),
                        event.getExplosion());
        /*
         * The vanilla blast radius is 2R with a random attenuation ray. Use
         * the enclosing shape only for this broad phase; the actual deletion
         * still goes through the per-block state and resistance checks.
         */
        double radius = explosionRadius(event);
        if (radius <= 0.0D || distance > radius * 2.0D
                || distance > radius && shape != null
                && !shape.grow(1.0D).contains(origin)) {
            return false;
        }
        return resistance < 1000.0F;
    }

    private static double explosionRadius(ExplosionEvent.Detonate event) {
        return event.getExplosion().size;
    }

    static boolean destroyByExplosion(
            ExplosionEvent.Detonate event,
            BearingContraptionEntity entity,
            BlockPos local,
            BearingContraption.Snapshot snapshot) {
        if (!entity.hasContraptionData()
                || !entity.getContraption().snapshotAtLocal(local).isPresent()) {
            return false;
        }
        EntityLivingBase owner = event.getExplosion().getExplosivePlacedBy();
        BlockPos worldReference = entity.explosionReference(
                local, event.getExplosion().getPosition());
        ContraptionBlockAccess access = new ContraptionBlockAccess(
                entity.getContraption(), event.getWorld(),
                entity.getRotationPivot(), entity.getRotationAxis(),
                entity.getAngle());
        ContraptionWorld localWorld = new ContraptionWorld(
                event.getWorld(), access, entity.getMovingWorldChunk());
        if (!canExplosionDestroy(localWorld, local, snapshot.state(),
                event.getExplosion(), owner)) {
            return false;
        }
        if (owner instanceof EntityPlayer player
                && !event.getWorld().isBlockModifiable(player, worldReference)) {
            return false;
        }
        if (owner instanceof EntityPlayer player) {
            BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(
                    event.getWorld(), worldReference, snapshot.state(), player);
            MinecraftForge.EVENT_BUS.post(breakEvent);
            if (breakEvent.isCanceled()) {
                return false;
            }
        }
        BearingContraption.Snapshot changed =
                entity.removeLocalBlockSnapshot(local);
        if (changed == null) {
            return false;
        }
        if (ContraptionInteractionMath.shouldCallBlockExplosionHook()) {
            changed.state().getBlock().onExplosionDestroy(
                    event.getWorld(), worldReference, event.getExplosion());
        }
        return true;
    }

    private static boolean canExplosionDestroy(
            World world, BlockPos pos, IBlockState state,
            net.minecraft.world.Explosion explosion, EntityLivingBase owner) {
        return !state.getBlock().isAir(state, null, null)
                && state.getBlock().getExplosionResistance(
                        world, pos, owner, explosion) < 1000.0F;
    }

}
