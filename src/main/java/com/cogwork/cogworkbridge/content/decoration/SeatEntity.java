package com.cogwork.cogworkbridge.content.decoration;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;

/** Invisible mount used while an entity occupies a seat. */
public final class SeatEntity extends Entity {
    private static final DataParameter<Integer> OWNER = EntityDataManager.createKey(SeatEntity.class, DataSerializers.VARINT);
    private static final DataParameter<BlockPos> LOCAL = EntityDataManager.createKey(SeatEntity.class, DataSerializers.BLOCK_POS);
    private java.util.UUID ownerUuid;
    private BearingContraptionEntity boundOwner;
    private int missingTicks;
    public SeatEntity(World world) {
        super(world);
        setSize(.01F, .01F);
        noClip = true;
        setNoGravity(true);
    }

    @Override protected void entityInit() { dataManager.register(OWNER, -1); dataManager.register(LOCAL, BlockPos.ORIGIN); }
    public void bind(BearingContraptionEntity parent, BlockPos local) {
        boundOwner = parent; ownerUuid = parent.getUniqueID(); dataManager.set(OWNER, parent.getEntityId()); dataManager.set(LOCAL, local.toImmutable());
        follow(parent);
    }
    public boolean belongsTo(BearingContraptionEntity parent) {
        int ownerId = dataManager.get(OWNER);
        return ownerId >= 0 && ownerId == parent.getEntityId();
    }
    public boolean belongsTo(BearingContraptionEntity parent, BlockPos local) { return belongsTo(parent) && dataManager.get(LOCAL).equals(local); }
    public BlockPos localPosition() { return dataManager.get(LOCAL); }
    public BearingContraptionEntity getBoundContraption() {
        int ownerId = dataManager.get(OWNER);
        Entity owner = ownerId < 0 ? null : world.getEntityByID(ownerId);
        return owner instanceof BearingContraptionEntity parent ? parent : null;
    }
    public boolean isStaticSeat() { return ownerUuid == null && dataManager.get(OWNER) < 0; }
    private void follow(BearingContraptionEntity parent) {
        Vec3d base = parent.localBlockCenterToWorld(dataManager.get(LOCAL)).add(parent.localFacingVectorToWorld(EnumFacing.DOWN).scale(.5D));
        setPosition(base.x, base.y, base.z);
    }
    @Override protected void readEntityFromNBT(NBTTagCompound compound) {
        if (compound.hasUniqueId("Contraption")) ownerUuid = compound.getUniqueId("Contraption");
        if (compound.hasKey("Local")) dataManager.set(LOCAL, BlockPos.fromLong(compound.getLong("Local")));
    }
    @Override protected void writeEntityToNBT(NBTTagCompound compound) {
        if (ownerUuid != null) { compound.setUniqueId("Contraption", ownerUuid); compound.setLong("Local", dataManager.get(LOCAL).toLong()); }
    }
    @Override public double getMountedYOffset() { return .25D; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean canBePushed() { return false; }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (dataManager.get(OWNER) >= 0 || ownerUuid != null) {
            Entity owner = world.getEntityByID(dataManager.get(OWNER));
            if (!(owner instanceof BearingContraptionEntity) && !world.isRemote && ownerUuid != null && world instanceof net.minecraft.world.WorldServer server) {
                owner = server.getEntityFromUuid(ownerUuid);
                if (owner instanceof BearingContraptionEntity parent) dataManager.set(OWNER, parent.getEntityId());
            }
            if (owner instanceof BearingContraptionEntity parent) boundOwner = parent;
            if (!world.isRemote && (owner == null || owner.isDead) && boundOwner != null && boundOwner.isDead) {
                Vec3d center = boundOwner.localBlockCenterToWorld(dataManager.get(LOCAL));
                BlockPos restored = new BlockPos(center);
                if (world.getBlockState(restored).getBlock() instanceof SeatBlock) {
                    dataManager.set(OWNER, -1); ownerUuid = null; boundOwner = null; missingTicks = 0;
                    setPosition(restored.getX() + .5D, restored.getY(), restored.getZ() + .5D);
                    return;
                }
            }
            if (owner instanceof BearingContraptionEntity parent && !parent.isDead && parent.getContraption() != null
                    && parent.getContraption().snapshotAtLocal(dataManager.get(LOCAL)).isPresent()) {
                missingTicks = 0; follow(parent);
            } else if (!world.isRemote && ++missingTicks > 40) { removePassengers(); setDead(); }
            if (!world.isRemote && getPassengers().isEmpty()) setDead();
            return;
        }
        if (!world.isRemote && (getPassengers().isEmpty()
                || !(world.getBlockState(new BlockPos(posX, posY, posZ)).getBlock() instanceof SeatBlock))) {
            removePassengers();
            setDead();
        }
    }
}
