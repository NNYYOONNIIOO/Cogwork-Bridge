package com.cogwork.cogworkbridge.content.trains.track;

import com.cogwork.cogworkbridge.content.advancement.AllAdvancements;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-side train contacts, using the bogey centreline rather than a carriage's enclosing box. */
public final class TrainCollision {
    private TrainCollision() { }

    record Segment(World world, Vec3d start, Vec3d end) { }
    record Contact(RailwayCarriageEntity train, World world, Vec3d point) { }

    static void tick(RailwayCarriageEntity train) {
        if (train.isCrashed() || train.speed() == 0D
                && train.motionX * train.motionX + train.motionY * train.motionY + train.motionZ * train.motionZ < 1E-10D) return;
        Contact contact = findContact(train);
        if (contact == null) return;
        double combinedSpeed = Math.max(Math.abs(train.speed()), Math.sqrt(train.motionX * train.motionX + train.motionY * train.motionY + train.motionZ * train.motionZ))
                + Math.abs(contact.train.speed());
        train.crash();
        contact.train.crash();
        // The upstream collision explosion affects entities, but does not destroy blocks.
        if (combinedSpeed > .2D)
            contact.world.newExplosion(null, contact.point.x, contact.point.y, contact.point.z,
                    (float) Math.min(3D * combinedSpeed, 5D), false, false);
    }

    @Nullable static Contact findContact(RailwayCarriageEntity train) {
        List<Segment> segments = segments(train);
        Map<UUID, List<Segment>> others = new HashMap<>();
        Map<World,List<RailwayCarriageEntity>> candidates=new HashMap<>();
        for (Segment segment : segments) {
            Set<UUID> examined=new HashSet<>();
            for (RailwayCarriageEntity other : candidates.computeIfAbsent(segment.world,TrainDimensionTransfer::loaded)) {
                if (other.isDead||train.trainId().equals(other.trainId())) continue;
                RailwayCarriageEntity controller = other.trainController();
                if (controller == null || !other.hasCollisionBody() || !examined.add(controller.trainId())) continue;
                // Include all carriages and coupling gaps, including present portal slices, without loading chunks.
                for (Segment otherSegment : others.computeIfAbsent(controller.trainId(), id -> segments(controller))) {
                    Vec3d point = intersection(segment, otherSegment);
                    if (point != null) return new Contact(controller, segment.world, point);
                }
            }
        }
        return null;
    }

    private static List<Segment> segments(RailwayCarriageEntity train) {
        List<Segment> result = new ArrayList<>();
        List<RailwayCarriageEntity> carriages = train.trainCarriages();
        if (carriages == null) return result;
        RailwayCarriageEntity previous = null;
        for (RailwayCarriageEntity carriage : carriages) {
            for (RailwayCarriageEntity copy : carriage.dimensionalCopies()) result.addAll(copy.collisionSegments());
            // Coupling gaps are occupied by the same train too, as in Train.findCollidingTrain.
            if (previous != null) result.addAll(previous.couplingSegments(carriage));
            previous = carriage;
        }
        return result;
    }

    /** Intersect XZ centrelines with upstream's 1/8-block collinear tolerance and height separation. */
    @Nullable private static Vec3d intersection(Segment a, Segment b) {
        if (a.world != b.world) return null;
        Vec3d u = a.end.subtract(a.start), v = b.end.subtract(b.start), w = b.start.subtract(a.start);
        double determinant = u.x * v.z - u.z * v.x;
        double ta, tb;
        if (Math.abs(determinant) > 1E-8D) {
            ta = (w.x * v.z - w.z * v.x) / determinant;
            tb = (w.x * u.z - w.z * u.x) / determinant;
            if (ta < 0D || ta > 1D || tb < 0D || tb > 1D) return null;
        } else {
            double lengthSquared = u.x * u.x + u.z * u.z;
            if (lengthSquared < 1E-10D) return null;
            if (Math.abs(w.x * u.z - w.z * u.x) > .125D * Math.sqrt(lengthSquared)) return null;
            double t0 = (w.x * u.x + w.z * u.z) / lengthSquared;
            Vec3d end = b.end.subtract(a.start);
            double t1 = (end.x * u.x + end.z * u.z) / lengthSquared;
            double lo = Math.max(0D, Math.min(t0, t1)), hi = Math.min(1D, Math.max(t0, t1));
            if (lo > hi) return null;
            ta = (lo + hi) * .5D;
            Vec3d p = a.start.add(u.scale(ta)).subtract(b.start);
            double otherLength = v.x * v.x + v.z * v.z;
            tb = otherLength < 1E-10D ? 0D : Math.max(0D, Math.min(1D, (p.x * v.x + p.z * v.z) / otherLength));
        }
        Vec3d point = a.start.add(u.scale(ta));
        return Math.abs(point.y - (b.start.y + v.y * tb)) < 3D ? point : null;
    }

    /** Called before the ordinary contraption push, so the victim's original speed is still available. */
    public static void hitEntity(BearingContraptionEntity part, Entity entity,
                                 List<AxisAlignedBB> oldBoxes, List<AxisAlignedBB> newBoxes) {
        if (part.world.isRemote || !(entity instanceof EntityLivingBase living) || !living.isEntityAlive()
                || !entity.onGround || entity.noClip) return;
        BearingContraptionEntity root = part;
        while (root.getParentContraption() != null) root = root.getParentContraption();
        if (!(root.getMountedCart() instanceof RailwayCarriageEntity carriage)) return;
        RailwayCarriageEntity train = carriage.trainController();
        if (train == null || train.isCrashed() || train.damageGraceTicks() > 0) return;
        if (living instanceof EntityPlayer player && (player.capabilities.isCreativeMode || player.isSpectator())) return;
        List<RailwayCarriageEntity> cars = train.trainCarriages();
        if (cars == null) return;
        for (RailwayCarriageEntity car : cars) for (RailwayCarriageEntity copy : car.dimensionalCopies()) {
            BearingContraptionEntity body = copy.body();
            if (body == null || body.world != entity.world) continue;
            for (BearingContraptionEntity piece : body.railwayPortalParts())
                if (piece.carriesSeatedPassenger(entity) || piece.wasRailwayPassenger(entity)) return;
        }
        // A swept AABB alone would hit entities in empty space beside a diagonal train.
        AxisAlignedBB bounds = living.getEntityBoundingBox();
        Vec3d contactMovement = null;
        for (int i = 0; i < oldBoxes.size(); i++) {
            AxisAlignedBB before = oldBoxes.get(i), after = newBoxes.get(i);
            if (!before.union(after).intersects(bounds)) continue;
            Vec3d centre = new Vec3d((before.minX + before.maxX) * .5D,
                    (before.minY + before.maxY) * .5D, (before.minZ + before.maxZ) * .5D);
            Vec3d end = new Vec3d((after.minX + after.maxX) * .5D,
                    (after.minY + after.maxY) * .5D, (after.minZ + after.maxZ) * .5D);
            Vec3d boxMotion = end.subtract(centre);
            if (boxMotion.lengthSquared() < .35D * .35D) continue;
            AxisAlignedBB expanded = bounds.grow(Math.max(before.maxX - before.minX, after.maxX - after.minX) * .5D,
                    Math.max(before.maxY - before.minY, after.maxY - after.minY) * .5D,
                    Math.max(before.maxZ - before.minZ, after.maxZ - after.minZ) * .5D);
            if (expanded.contains(centre) || expanded.calculateIntercept(centre, end) != null) {
                contactMovement = boxMotion; break;
            }
        }
        if (contactMovement == null) return;
        Vec3d relative = contactMovement.subtract(living.motionX, living.motionY, living.motionZ);
        double impact = Math.sqrt(relative.lengthSquared());
        if (impact <= .35D) return;
        long now = entity.world.getTotalWorldTime();
        String key = "CogworkTrainImpact";
        // Several blocks/carriages can contact one entity in the same tick; never multiply the hit.
        if (entity.getEntityData().hasKey(key) && now - entity.getEntityData().getLong(key) < 10L) return;
        boolean hostile = living instanceof IMob || living.isCreatureType(net.minecraft.entity.EnumCreatureType.MONSTER, false);
        if (hostile) impact *= 2D;
        boolean hurt = living.attackEntityFrom(new RunOverDamageSource(root, train.trainName()), (int) (impact * 16D));
        if (!hurt) return;
        entity.getEntityData().setLong(key, now);
        entity.world.playSound(null, entity.posX, entity.posY, entity.posZ,
                SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.NEUTRAL, 1F, .75F);
        if (!living.isEntityAlive() && hostile)
            AllAdvancements.award(RailwayCarriageEntity.manualDriver(train.world, train.trainId()), "train_roadkill");
        Vec3d horizontal = new Vec3d(contactMovement.x, 0D, contactMovement.z).normalize();
        Vec3d velocity = new Vec3d(living.motionX, living.motionY, living.motionZ)
                .add(horizontal.add(0D, .25D, 0D).scale(impact * 4D)).add(relative);
        if (velocity.lengthSquared() > 9D) velocity = velocity.normalize().scale(3D);
        living.motionX = velocity.x; living.motionY = velocity.y; living.motionZ = velocity.z;
        living.velocityChanged = true;
    }

    private static final class RunOverDamageSource extends EntityDamageSource {
        private final String trainName;
        RunOverDamageSource(Entity source, String trainName) {
            super("cogworkbridge.run_over", source);
            this.trainName = trainName;
        }
        @Override public ITextComponent getDeathMessage(EntityLivingBase victim) {
            return new TextComponentTranslation("death.attack.cogworkbridge.run_over", victim.getDisplayName(),
                    new TextComponentString(trainName));
        }
    }
}
