package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.MovingWorldAssemblyInteractor;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunk;
import com.cogwork.cogworkbridge.vendor.movingworld.common.entity.EntityMovingWorld;
import com.cogwork.cogworkbridge.vendor.movingworld.common.entity.MovingWorldCapabilities;
import com.cogwork.cogworkbridge.vendor.movingworld.common.entity.MovingWorldHandlerCommon;
import com.cogwork.cogworkbridge.content.contraptions.transform.ContraptionTransform;
import com.cogwork.cogworkbridge.content.contraptions.transform.NestedContraptionPose;
import com.cogwork.cogworkbridge.content.contraptions.glue.SuperGlueEntity;
import com.cogwork.cogworkbridge.content.decoration.SeatEntity;
import com.cogwork.cogworkbridge.content.fluids.FluidTankBlock;
import com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import com.cogwork.cogworkbridge.network.ModNetwork;

/**
 * A lightweight, server-authoritative rotating block snapshot.
 *
 * <p>The entity anchor follows Create's controlled-contraption convention: the
 * integer position of the block in front of the bearing. Rendering and collision
 * rotate around the centre of that anchor block, while the bearing tile remains
 * the authority for the snapshot and its restoration.
 */
public class BearingContraptionEntity extends EntityMovingWorld {

    private static final float CLIENT_CORRECTION_RATE = 0.2F;
    private static final float CLIENT_MAX_CORRECTION_PER_TICK = 1.5F;

    private static final DataParameter<BlockPos> DATA_BEARING_POS =
            EntityDataManager.createKey(
                    BearingContraptionEntity.class, DataSerializers.BLOCK_POS);
    private static final DataParameter<EnumFacing> DATA_FACING =
            EntityDataManager.createKey(
                    BearingContraptionEntity.class, DataSerializers.FACING);
    private static final DataParameter<Float> DATA_ANGLE =
            EntityDataManager.createKey(
                    BearingContraptionEntity.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> DATA_SPEED =
            EntityDataManager.createKey(
                    BearingContraptionEntity.class, DataSerializers.FLOAT);
    private static final DataParameter<Boolean> DATA_STALLED =
            EntityDataManager.createKey(
                    BearingContraptionEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> DATA_ELEVATOR =
            EntityDataManager.createKey(BearingContraptionEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Float> DATA_VERTICAL_OFFSET =
            EntityDataManager.createKey(BearingContraptionEntity.class, DataSerializers.FLOAT);
    private static final DataParameter<BlockPos> DATA_ELEVATOR_CONTROLLER =
            EntityDataManager.createKey(BearingContraptionEntity.class, DataSerializers.BLOCK_POS);
    private static final DataParameter<EnumFacing> DATA_LINEAR_DIRECTION =
            EntityDataManager.createKey(BearingContraptionEntity.class, DataSerializers.FACING);
    private static final DataParameter<Boolean> DATA_IS_FLOOR_ELEVATOR =
            EntityDataManager.createKey(BearingContraptionEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<com.google.common.base.Optional<java.util.UUID>> DATA_CART =
            EntityDataManager.createKey(BearingContraptionEntity.class, DataSerializers.OPTIONAL_UNIQUE_ID);
    private static final DataParameter<Float> DATA_CART_X = EntityDataManager.createKey(BearingContraptionEntity.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> DATA_CART_Y = EntityDataManager.createKey(BearingContraptionEntity.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> DATA_CART_Z = EntityDataManager.createKey(BearingContraptionEntity.class, DataSerializers.FLOAT);
    private static final DataParameter<Boolean> DATA_PORTAL = EntityDataManager.createKey(BearingContraptionEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> DATA_PORTAL_COPY = EntityDataManager.createKey(BearingContraptionEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<EnumFacing> DATA_PORTAL_AXIS = EntityDataManager.createKey(BearingContraptionEntity.class, DataSerializers.FACING);
    private static final DataParameter<Integer> DATA_PORTAL_SEAM = EntityDataManager.createKey(BearingContraptionEntity.class, DataSerializers.VARINT);
    private static final DataParameter<com.google.common.base.Optional<java.util.UUID>> DATA_PARENT =
            EntityDataManager.createKey(
                    BearingContraptionEntity.class, DataSerializers.OPTIONAL_UNIQUE_ID);
    private static final java.util.Map<BlockPos, BearingContraptionEntity> ACTIVE =
            new java.util.HashMap<>();
    private static final java.util.Map<Integer, NBTTagCompound> PENDING_CLIENT_SNAPSHOTS =
            new java.util.HashMap<>();
    private final MovingWorldCapabilities capabilities =
            new BearingMovingWorldCapabilities(this);
    private final MovingWorldAssemblyInteractor assemblyInteractor =
            new BearingMovingWorldInteractor();

    private int bearingX;
    private int bearingY;
    private int bearingZ;
    private BearingContraption contraption;
    private EnumFacing facing = EnumFacing.NORTH;
    private float angle;
    private float speed;
    private BlockPos anchor;
    private float previousAngle;
    private int poseSequence;
    private int lastClientPoseSequence = -1;
    private float serverTargetAngle;
    private boolean clientPoseInitialized;
    private boolean clientTargetStalled;
    private int contraptionTileSyncTicks;
    private final MovingWorldHandlerCommon handler;
    private int missingContraptionTicks;
    private long carriedPropertiesTick = Long.MIN_VALUE;
    private final java.util.Map<java.util.UUID, CarriedBreath> carriedBreaths =
            new java.util.HashMap<>();
    private long lightSignatureTick = Long.MIN_VALUE;
    private int cachedWorldLightSignature;
    private boolean ownedByBearing;
    private BlockPos rotaryController;
    private java.util.UUID mountedCart;
    private java.util.UUID secondaryMountedCart;
    private net.minecraft.entity.item.EntityMinecart loadedMountedCart;
    private net.minecraft.entity.item.EntityMinecart loadedSecondaryCart;
    private double cartCouplingLength;
    private Vec3d acceptedSecondaryCartPosition;
    private float cartX, cartY, cartZ;
    private float cartInitialYaw = Float.NaN;
    private int cartRotationMode;
    private boolean cartTurning;
    private boolean elevator;
    private BlockPos elevatorController = BlockPos.ORIGIN;
    private float verticalOffset;
    private EnumFacing linearDirection = EnumFacing.UP;
    private boolean floorElevator;
    private boolean suppressElevatorCollision;
    private boolean clientElevatorPoseInitialized;
    private java.util.List<AxisAlignedBB> pendingClientElevatorBoxes;
    private double pendingClientElevatorX, pendingClientElevatorY, pendingClientElevatorZ;
    private boolean pendingClientMountedMovement;
    private float pendingClientMountedAngle;
    private double pendingClientMountedPivotX, pendingClientMountedPivotZ;
    private int fluidInterfaceHoldTicks;
    private Vec3d actorStepTranslation;
    private Vec3d actorTickTranslation;
    private Vec3d lastLinearMotion = Vec3d.ZERO;
    private long lastLinearMotionTick = Long.MIN_VALUE;
    private final java.util.Map<Entity, LinearPassengerSupport> linearPassengerSupports = new java.util.WeakHashMap<>();
    private final java.util.Set<BlockPos> railwayPortalBlocks=new java.util.HashSet<>();
    private BearingContraptionEntity railwayPortalStorageOwner;
    private record LinearPassengerSupport(double relativeTop, long tick) {}
    private boolean linearActorBlocking;
    private int interactionSessionDepth;
    private boolean restoringSnapshot;
    private final NestedBearingManager nestedBearings =
            new NestedBearingManager(this);
    private final ContraptionWorldProperties worldProperties =
            new ContraptionWorldProperties(this);
    private ContraptionWorld carriedWorld;
    private MobileChunk collisionCacheChunk;
    private long collisionCacheVersion = Long.MIN_VALUE;
    private long collisionCacheTick = Long.MIN_VALUE;
    private java.util.List<CollisionPose> collisionCachePose = java.util.List.of();
    private long bottomYCacheVersion = Long.MIN_VALUE;
    private int bottomYCache = Integer.MIN_VALUE;
    private java.util.List<AxisAlignedBB> cachedCollisionBoxes = java.util.List.of();
    private BearingContraptionEntity parentContraption;
    private BlockPos parentLocalBearingPos;
    private java.util.UUID parentContraptionUuid;
    private NestedContraptionPose parentPose;
    private boolean actorControlsInitialized, allActorsDisabled;
    private final java.util.Set<String> disabledActorTypes = new java.util.HashSet<>();
    private final java.util.Set<String> enabledActorExceptions = new java.util.HashSet<>();

    public BearingContraptionEntity(World world) {
        super(world);
        handler = world.isRemote
                ? new BearingMovingWorldHandlerClient(this)
                : new BearingMovingWorldHandlerServer(this);
        noClip = true;
        ignoreFrustumCheck = true;
        setSize(1.0F, 1.0F);
        setEntityBoundingBox(new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
    }

    public BearingContraptionEntity(World world, BlockPos bearingPos, EnumFacing facing,
                                    BearingContraption contraption) {
        this(world, bearingPos, facing, contraption, false);
    }

    private BearingContraptionEntity(World world, BlockPos bearingPos, EnumFacing facing,
                                     BearingContraption contraption, boolean elevator) {
        this(world);
        this.elevator = elevator;
        this.bearingX = bearingPos.getX();
        this.bearingY = bearingPos.getY();
        this.bearingZ = bearingPos.getZ();
        this.facing = facing;
        this.contraption = contraption;
        readActorControlsFromContraption();
        this.anchor = bearingPos.offset(facing);
        setRiderDestination(EnumFacing.NORTH, BlockPos.ORIGIN);
        syncPoseToDataManager();
        registerActive();
        syncEntityPosition();
        populateMobileChunk(contraption);
        updateBounds();
    }

    /** A pulley structure retains its capture anchor; its world pose translates vertically. */
    public static BearingContraptionEntity createElevator(World world, BlockPos controller,
                                                           BlockPos captureAnchor, BearingContraption contraption) {
        return createLinear(world, controller, captureAnchor, EnumFacing.UP, contraption, true);
    }

    public static BearingContraptionEntity createLinear(World world, BlockPos controller,
            BlockPos captureAnchor, EnumFacing direction, BearingContraption contraption, boolean floorElevator) {
        BearingContraptionEntity entity = new BearingContraptionEntity(world, captureAnchor.up(), EnumFacing.DOWN, contraption, true);
        entity.elevatorController = controller.toImmutable();
        entity.linearDirection = direction;
        entity.floorElevator = floorElevator;
        entity.syncPoseToDataManager();
        return entity;
    }

    public static BearingContraptionEntity createRotary(World world, BlockPos controller,
            BlockPos captureAnchor, EnumFacing direction, BearingContraption contraption) {
        BearingContraptionEntity entity = new BearingContraptionEntity(world,
                captureAnchor.offset(direction.getOpposite()), direction, contraption);
        entity.rotaryController = controller.toImmutable();
        return entity;
    }

    public static BearingContraptionEntity createMounted(World world, BlockPos captureAnchor,
            BearingContraption contraption, net.minecraft.entity.item.EntityMinecart cart,
            @javax.annotation.Nullable net.minecraft.entity.item.EntityMinecart secondaryCart,
            int rotationMode, boolean eastWest) {
        BearingContraptionEntity entity = new BearingContraptionEntity(world, captureAnchor.up(), EnumFacing.DOWN, contraption, true);
        entity.mountedCart = cart.getUniqueID();
        entity.loadedMountedCart = cart;
        if (secondaryCart != null) {
            entity.secondaryMountedCart = secondaryCart.getUniqueID();
            entity.loadedSecondaryCart = secondaryCart;
            entity.acceptedSecondaryCartPosition = secondaryCart.getPositionVector();
            double dx = secondaryCart.posX - cart.posX, dz = secondaryCart.posZ - cart.posZ;
            entity.cartCouplingLength = Math.sqrt(dx * dx + dz * dz);
            entity.cartInitialYaw = mountedYaw(dx, dz);
        }
        entity.cartRotationMode = Math.floorMod(rotationMode, 3);
        double dx = cart.motionX, dz = cart.motionZ;
        if (secondaryCart == null) entity.cartInitialYaw = dx * dx + dz * dz > 1.0E-8D
                ? mountedYaw(dx, dz) : eastWest ? mountedYaw(-1D, 0D) : mountedYaw(0D, -1D);
        if (cart instanceof com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity railway
                && railway.heading() != null) {
            Vec3d heading=railway.heading();entity.cartInitialYaw=mountedYaw(heading.x,heading.z);
        }
        entity.syncPoseToDataManager();
        return entity;
    }
    private static float mountedYaw(double x, double z) {
        return (float) (270D + Math.toDegrees(Math.atan2(z, x)));
    }

    public boolean isMountedTo(java.util.UUID cart) {
        return cart != null && (cart.equals(mountedCart) || cart.equals(secondaryMountedCart));
    }
    @javax.annotation.Nullable
    public net.minecraft.entity.item.EntityMinecart getOtherMountedCart(java.util.UUID cart) {
        if (cart == null || secondaryMountedCart == null) return null;
        return findMountedCart(cart.equals(mountedCart) ? secondaryMountedCart : cart.equals(secondaryMountedCart) ? mountedCart : null);
    }
    public boolean hasCoupledCart() { return secondaryMountedCart != null; }
    @javax.annotation.Nullable
    public net.minecraft.entity.item.EntityMinecart getMountedCart() {
        return findMountedCart(mountedCart);
    }
    /** Reset both interpolation endpoints after a railway dimension transition. */
    public void setRailwayPortalPose(com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity cart) {
        mountedCart=cart.getUniqueID();loadedMountedCart=cart;
        secondaryMountedCart=null;loadedSecondaryCart=null;
        cartX=(float)(cart.posX-getAnchor().getX()-.5D);
        cartY=(float)(cart.posY-getAnchor().getY()+1D);
        cartZ=(float)(cart.posZ-getAnchor().getZ()-.5D);
        Vec3d heading=cart.heading();
        if(heading!=null){angle=cartInitialYaw-mountedYaw(heading.x,heading.z);previousAngle=angle;}
        setStalled(false);syncPoseToDataManager();syncEntityPosition();updateBounds();
        lastTickPosX=prevPosX=posX;lastTickPosY=prevPosY=posY;lastTickPosZ=prevPosZ=posZ;
    }
    /** The same block-layer cutoff is used by meshes, TESRs, ray tracing and collision. */
    public boolean isRailwayInPortal() { return parentContraption!=null?parentContraption.isRailwayInPortal():dataManager.get(DATA_PORTAL); }
    public boolean isRailwayPortalCopy() { return parentContraption!=null?parentContraption.isRailwayPortalCopy():dataManager.get(DATA_PORTAL_COPY); }
    public int railwayPortalRenderKey() {
        if(parentContraption!=null&&parentContraption.isRailwayInPortal())return java.util.Objects.hash(parentContraption.railwayPortalRenderKey(),getAngle(),parentContraption.getAngle());
        return isRailwayInPortal()?java.util.Objects.hash(dataManager.get(DATA_PORTAL_AXIS),dataManager.get(DATA_PORTAL_SEAM),isRailwayPortalCopy()):0;
    }
    public boolean isRailwayBlockVisible(BlockPos local) {
        if(parentContraption!=null&&parentContraption.isRailwayInPortal()) {
            Vec3d point=parentContraption.worldPointToLocal(localBlockCenterToWorld(local)).subtract(new Vec3d(parentContraption.getAnchor()));
            return parentContraption.isRailwayBlockVisible(new BlockPos(point));
        }
        if (!isRailwayInPortal()) return true;
        EnumFacing direction=dataManager.get(DATA_PORTAL_AXIS);
        int coordinate=local.getX()*direction.getXOffset()+local.getY()*direction.getYOffset()+local.getZ()*direction.getZOffset();
        int seam=dataManager.get(DATA_PORTAL_SEAM);
        return isRailwayPortalCopy()?coordinate>seam:coordinate<seam;
    }
    public void setRailwayPortalSlice(EnumFacing direction,int seam,boolean copy) {
        int before=railwayPortalRenderKey();
        dataManager.set(DATA_PORTAL,true);dataManager.set(DATA_PORTAL_AXIS,direction);
        dataManager.set(DATA_PORTAL_SEAM,seam);dataManager.set(DATA_PORTAL_COPY,copy);
        if(before!=railwayPortalRenderKey()) {collisionCacheTick=Long.MIN_VALUE;updateBounds();}
    }
    public void clearRailwayPortalSlice() {
        dataManager.set(DATA_PORTAL,false);dataManager.set(DATA_PORTAL_COPY,false);
        collisionCacheTick=Long.MIN_VALUE;updateBounds();
    }
    private void writeRailwayPortalSlice(NBTTagCompound tag) {
        tag.setBoolean("RailwayPortal",isRailwayInPortal());tag.setBoolean("RailwayPortalCopy",isRailwayPortalCopy());
        tag.setByte("RailwayPortalAxis",(byte)dataManager.get(DATA_PORTAL_AXIS).getIndex());tag.setInteger("RailwayPortalSeam",dataManager.get(DATA_PORTAL_SEAM));
    }
    private void readRailwayPortalSlice(NBTTagCompound tag) {
        if(tag.getBoolean("RailwayPortal"))setRailwayPortalSlice(EnumFacing.byIndex(tag.getByte("RailwayPortalAxis")),tag.getInteger("RailwayPortalSeam"),tag.getBoolean("RailwayPortalCopy"));
        else clearRailwayPortalSlice();
    }
    /** Reuse the live server tiles: the other side is never an independently extractable inventory. */
    public void shareRailwayPortalData(BearingContraptionEntity owner) {
        if(world.isRemote||owner==this||owner.contraption==null)return;
        owner.refreshRailwayPortalBlocks();
        if(railwayPortalBlocks.isEmpty()&&contraption!=null)
            for(var snapshot:contraption.blocks().values())railwayPortalBlocks.add(localPos(snapshot));
        contraption=owner.contraption;
        MobileChunk source=owner.getMobileChunk(), target=getMobileChunk();
        if(source==null||target==null)return;
        java.util.Set<BlockPos> remaining=new java.util.HashSet<>(railwayPortalBlocks);
        railwayPortalBlocks.clear();
        for(var snapshot:contraption.blocks().values()) {
            BlockPos local=localPos(snapshot);remaining.remove(local);
            railwayPortalBlocks.add(local);
            if(!target.getBlockState(local).equals(snapshot.state())) {
                target.chunkTileEntityMap.remove(local); // Never invalidate a tile shared with the owner.
                target.setBlockState(local,snapshot.state());
            }
            TileEntity live=source.chunkTileEntityMap.get(local);
            if(live!=null)target.chunkTileEntityMap.put(local,live);
            else target.chunkTileEntityMap.remove(local);
        }
        for(BlockPos local:remaining) {
            target.chunkTileEntityMap.remove(local);
            if(contraption.snapshotAtLocal(local).isEmpty())target.setBlockState(local,Blocks.AIR.getDefaultState());
        }
        target.updatableTiles.clear();
        railwayPortalStorageOwner=owner;
    }
    private void refreshRailwayPortalBlocks() {
        if(contraption==null||getMobileChunk()==null)return;
        java.util.Set<BlockPos> previous=new java.util.HashSet<>(railwayPortalBlocks);
        railwayPortalBlocks.clear();
        for(var snapshot:contraption.blocks().values()) {
            BlockPos local=localPos(snapshot);railwayPortalBlocks.add(local);previous.remove(local);
            if(!getMobileChunk().getBlockState(local).equals(snapshot.state()))
                setBlockInMobileChunk(local,snapshot.state(),contraption.tileDataAt(snapshot.pos()));
        }
        for(BlockPos removed:previous)setBlockInMobileChunk(removed,Blocks.AIR.getDefaultState(),null);
    }
    /** Drop the inactive chunk without invalidating live tiles owned by the other side. */
    public void discardRailwayPortalCopy() {
        nestedBearings.discardRailwayChildren();
        MobileChunk chunk=getMobileChunk();
        if(chunk!=null){chunk.chunkTileEntityMap.clear();chunk.updatableTiles.clear();}
        discardWithoutRestore();
    }
    public void promoteRailwayPortalCopy() {
        clearRailwayPortalSlice();
        railwayPortalStorageOwner=null;setStalled(false);
        for(BearingContraptionEntity child:nestedBearings.railwayChildren())child.promoteRailwayPortalCopy();
        railwayPortalBlocks.clear();
        MobileChunk chunk=getMobileChunk();
        if(chunk!=null)for(TileEntity tile:chunk.chunkTileEntityMap.values()) {
            tile.setWorld(chunk.getFakeWorld());
            if(tile instanceof net.minecraft.util.ITickable&&!chunk.updatableTiles.contains(tile))chunk.updatableTiles.add(tile);
        }
        collisionCacheTick=Long.MIN_VALUE;updateBounds();
    }
    /** Move the destination slice with exactly the same rigid pose, without snapping each frame. */
    public void moveRailwayPortalCopy() {updateMountedCart();resolveEntityCollisions();}
    public record RailwayPortalPart(BearingContraptionEntity source,BearingContraptionEntity copy) { }
    public java.util.List<BearingContraptionEntity> railwayPortalParts() {
        var result=new java.util.ArrayList<BearingContraptionEntity>();result.add(this);
        for(var child:nestedBearings.railwayChildren())result.addAll(child.railwayPortalParts());
        return result;
    }
    /** Nested structures keep their local pose and share each corresponding live storage as well. */
    public java.util.List<RailwayPortalPart> synchronizeRailwayPortalChildren(BearingContraptionEntity source) {
        var pairs=new java.util.ArrayList<RailwayPortalPart>();pairs.add(new RailwayPortalPart(source,this));
        for(var child:source.nestedBearings.railwayChildren()) {
            BlockPos local=child.parentLocalBearingPos;
            if(local==null)continue;
            BearingContraptionEntity copy=nestedBearings.railwayChildAt(local);
            if(copy==null||copy.isDead) {
                copy=new BearingContraptionEntity(world,this,local,child.facing,child.contraption,
                        new NestedContraptionPose(local,child.facing,child.angle,0,false));
                copy.shareRailwayPortalData(child);
                if(!world.spawnEntity(copy)){copy.discardRailwayPortalCopy();pairs.add(new RailwayPortalPart(child,null));return pairs;}
                nestedBearings.registerRailwayChild(local,copy);
            } else {
                copy.setParentContraption(this,local);copy.shareRailwayPortalData(child);copy.refreshNestedPosition();
            }
            pairs.addAll(copy.synchronizeRailwayPortalChildren(child));
        }
        return pairs;
    }
    public boolean wasRailwayPassenger(Entity entity) {
        LinearPassengerSupport support=linearPassengerSupports.get(entity);
        return support!=null&&world.getTotalWorldTime()-support.tick()<=3
                ||isRailwayInPortal()&&entity.onGround&&hasPortalFloor(entity,0,0,0);
    }
    private BearingContraptionEntity railwayAuthoritative() {
        if(!isRailwayPortalCopy())return this;
        BearingContraptionEntity root=this;
        java.util.ArrayDeque<BlockPos> path=new java.util.ArrayDeque<>();
        while(root.parentContraption!=null){path.addFirst(root.parentLocalBearingPos);root=root.parentContraption;}
        if(!(root.getMountedCart() instanceof com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity car))return null;
        var owner=car.portalOwner();BearingContraptionEntity result=owner==null?null:owner.body();
        for(BlockPos local:path){if(result==null)return null;result=result.nestedBearings.railwayChildAt(local);}
        return result;
    }
    public boolean isRailwayPortalDataReady() {
        if(world.isRemote||!isRailwayPortalCopy())return true;
        BearingContraptionEntity owner=railwayAuthoritative();
        return owner!=null&&!owner.isDead&&railwayPortalStorageOwner==owner&&contraption==owner.contraption;
    }
    private void commitRailwayPortalInteraction() {
        BearingContraptionEntity owner=railwayAuthoritative();
        if(owner==null||owner==this)return;
        owner.refreshRailwayPortalBlocks();owner.syncContraptionTileData();
        if(owner.getMobileChunk()!=null)for(TileEntity tile:owner.getMobileChunk().chunkTileEntityMap.values())tile.setWorld(owner.getMobileChunk().getFakeWorld());
        owner.sendSnapshotToTrackingPlayers();
    }
    private boolean hasPortalFloor(Entity entity,double dx,double dy,double dz) {
        if(!isRailwayInPortal()||contraption==null)return false;
        Vec3d point=worldPointToLocal(entity.getPositionVector().add(dx,dy,dz)).subtract(new Vec3d(getAnchor()));
        BlockPos foot=new BlockPos(point.add(0,-.01,0));
        ContraptionWorld localWorld=carriedWorld();if(localWorld==null)return false;
        for(BlockPos local:BlockPos.getAllInBox(foot.add(-1,-1,-1),foot.add(1,0,1))) {
            IBlockState state=localWorld.getBlockState(local);
            AxisAlignedBB box=state.getCollisionBoundingBox(localWorld,local);
            if(box==null)continue;box=box.offset(local);
            if(point.x>=box.minX-.25&&point.x<=box.maxX+.25&&point.z>=box.minZ-.25&&point.z<=box.maxZ+.25
                    &&Math.abs(point.y-box.maxY)<.3)return true;
        }
        return false;
    }
    public void rememberRailwayPassenger(Entity entity) {
        linearPassengerSupports.put(entity,new LinearPassengerSupport(entity.getEntityBoundingBox().minY-posY,world.getTotalWorldTime()));
    }
    public int getMountedRotationMode() { return cartRotationMode; }
    public static boolean hasActiveAt(BlockPos bearingPos) {
        BearingContraptionEntity existing = ACTIVE.get(bearingPos);
        return existing != null && !existing.isDead;
    }

    /** Flush every live tile, including nested bearing children, before pickup. */
    public boolean preparePortablePickup() {
        if (world.isRemote || isDead || contraption == null || !canReleaseNestedChildren()) return false;
        ContraptionGuiProxy.closeForStructureTransition(this);
        syncContraptionTileData();
        if (isDead || contraption == null || getMobileChunk() == null) return false;
        for (BlockPos local : new java.util.ArrayList<>(getMobileChunk().chunkTileEntityMap.keySet())) {
            persistTile(local);
        }
        return nestedBearings.preparePortablePickup();
    }

    /** Restore the saved pose without changing its heading on the first tick. */
    public void setPortableMountedAngle(float savedAngle) {
        setAngle(savedAngle);
        previousAngle = angle;
        if (Float.isFinite(cartInitialYaw)) cartInitialYaw += angle;
        syncPoseToDataManager();
    }
    @javax.annotation.Nullable
    private net.minecraft.entity.item.EntityMinecart findMountedCart(java.util.UUID id) {
        if (world == null || id == null) return null;
        for (Entity candidate : world.loadedEntityList)
            if (!candidate.isDead && id.equals(candidate.getUniqueID())
                    && candidate instanceof net.minecraft.entity.item.EntityMinecart cart) return cart;
        return null;
    }
    /** The minecart's own inventory is part of a mounted structure's storage. */
    @javax.annotation.Nullable
    public net.minecraftforge.items.IItemHandler getMountedItemHandler(boolean secondary) {
        net.minecraft.entity.item.EntityMinecart candidate = findMountedCart(secondary ? secondaryMountedCart : mountedCart);
        if (candidate != null) {
            net.minecraftforge.items.IItemHandler handler = candidate.getCapability(
                    net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (handler != null) return handler;
            if (candidate instanceof net.minecraft.inventory.IInventory inventory)
                return new net.minecraftforge.items.wrapper.InvWrapper(inventory);
            return null;
        }
        return null;
    }
    private double translationX() { return mountedCart == null ? linearDirection.getXOffset() * verticalOffset : cartX; }
    private double translationY() { return mountedCart == null ? linearDirection.getYOffset() * verticalOffset : cartY; }
    private double translationZ() { return mountedCart == null ? linearDirection.getZOffset() * verticalOffset : cartZ; }

    public boolean isElevator() { return elevator && floorElevator; }
    public boolean isLinearContraption() { return elevator; }
    public BlockPos getElevatorController() { return elevatorController; }
    /** Transfer an advancing gantry's ownership to the shaft currently under it. */
    public void transferLinearController(BlockPos controller) {
        if (world.isRemote || !elevator || mountedCart != null || controller == null) return;
        elevatorController = controller.toImmutable();
        dataManager.set(DATA_ELEVATOR_CONTROLLER, elevatorController);
    }
    public float getVerticalOffset() { return verticalOffset; }
    public boolean isElevatorCollisionSuppressed() { return suppressElevatorCollision; }

    /** Previous point of the current actor sweep, including linear translation. */
    public Vec3d actorStepStart(Vec3d point, float fromAngle) {
        Vec3d start = localToWorld(point, fromAngle);
        return actorStepTranslation == null ? start : start.subtract(actorStepTranslation);
    }

    /** Full-tick velocity used for actor work speed, independent of sweep subdivision. */
    public Vec3d actorTickMovement(Vec3d point, float fromAngle, float bearingSpeed) {
        if (actorTickTranslation != null) {
            Vec3d before = localToWorld(point, fromAngle);
            return actorTickTranslation.add(localToWorld(point, fromAngle + bearingSpeed * .3F).subtract(before));
        }
        if (elevator && world != null && lastLinearMotionTick != Long.MIN_VALUE
                && world.getTotalWorldTime() - lastLinearMotionTick <= 1) return lastLinearMotion;
        Vec3d before = localToWorld(point, fromAngle);
        return localToWorld(point, fromAngle + bearingSpeed * .3F).subtract(before);
    }

    public boolean hasMotion() {
        if (isStalled()) return false;
        if (speed != 0F) return true;
        return world != null && lastLinearMotionTick != Long.MIN_VALUE
                && world.getTotalWorldTime() - lastLinearMotionTick <= 1
                && lastLinearMotion.lengthSquared() > 1.0E-12D;
    }
    public boolean isLinearActorBlocking() { return linearActorBlocking; }

    private void rememberLinearMotion(double dx, double dy, double dz) {
        lastLinearMotion = new Vec3d(dx, dy, dz);
        lastLinearMotionTick = world.getTotalWorldTime();
    }

    /** Advances in short slices using the same collision shapes as bearing structures. */
    public boolean moveElevator(float requestedOffset) {
        return moveLinear(requestedOffset);
    }

    public boolean moveLinear(float requestedOffset) {
        if (world.isRemote || !elevator || contraption == null || isDead) return false;
        linearActorBlocking = false;
        if (fluidInterfaceHoldTicks > 0) {
            fluidInterfaceHoldTicks--;
            setStalled(true);
            rememberLinearMotion(0, 0, 0);
            return true;
        }
        float start = verticalOffset;
        float difference = requestedOffset - start;
        java.util.List<AxisAlignedBB> oldBoxes = collisionBoxes();
        int steps = Math.max(1, (int) Math.ceil(Math.abs(difference) / .125F));
        java.util.List<AxisAlignedBB> shapes = movingCollisionShapes();
        Vec3d tickMotion = new Vec3d(linearDirection.getDirectionVec()).scale(difference);
        for (int i = 1; i <= steps; i++) {
            float last = verticalOffset;
            verticalOffset = start + difference * i / steps;
            Vec3d stepMotion = new Vec3d(linearDirection.getDirectionVec()).scale(verticalOffset - last);
            if (stepMotion.lengthSquared() > 1.0E-12D && tickLinearActors(stepMotion, tickMotion, angle, angle)) {
                verticalOffset = last;
                linearActorBlocking = true;
                setStalled(true);
                break;
            }
            if (collidesAt(0F, shapes, new java.util.HashMap<>())) {
                verticalOffset = last;
                setStalled(true);
                break;
            }
            setStalled(false);
        }
        float moved = verticalOffset - start;
        rememberLinearMotion(linearDirection.getXOffset() * moved,
                linearDirection.getYOffset() * moved, linearDirection.getZOffset() * moved);
        if (Math.abs(moved) > 1.0E-6F) {
            dataManager.set(DATA_VERTICAL_OFFSET, verticalOffset);
            collisionCacheTick = Long.MIN_VALUE;
            syncEntityPosition();
            updateBounds();
            carryLinearEntities(oldBoxes, moved);
        }
        return Math.abs(requestedOffset - verticalOffset) < 1.0E-4F;
    }

    private boolean tickLinearActors(Vec3d stepMotion, Vec3d tickMotion, float fromAngle, float toAngle) {
        if(isRailwayInPortal())return false;
        actorStepTranslation = stepMotion;
        actorTickTranslation = tickMotion;
        boolean blocked = false;
        float angularSpeed = (toAngle - fromAngle) / .3F;
        try {
            for (var drill : movingDrills()) blocked |= drill.tickMovingStep(this, fromAngle, toAngle, angularSpeed);
            for (var saw : movingSaws()) blocked |= saw.tickMovingStep(this, fromAngle, toAngle, angularSpeed);
            for (var plough : movingPloughs()) blocked |= plough.tickMovingStep(this, fromAngle, toAngle, angularSpeed);
            for (var harvester : movingHarvesters()) harvester.tickMovingStep(this, fromAngle, toAngle);
            for (var roller : movingRollers()) blocked |= roller.tickMovingStep(this, fromAngle, toAngle, angularSpeed);
        } finally {
            actorStepTranslation = null;
            actorTickTranslation = null;
        }
        return blocked;
    }

    private void carryLinearEntities(java.util.List<AxisAlignedBB> oldBoxes, float moved) {
        double dx = linearDirection.getXOffset() * moved;
        double dy = linearDirection.getYOffset() * moved;
        double dz = linearDirection.getZOffset() * moved;
        carryTranslatedEntities(oldBoxes, dx, dy, dz);
    }

    private void carryTranslatedEntities(java.util.List<AxisAlignedBB> oldBoxes, double dx, double dy, double dz) {
        carryTransformedEntities(oldBoxes, dx, dy, dz, 0F, 0D, 0D);
    }

    /** Move supported entities with both translation and the carriage's turn. */
    private void carryTransformedEntities(java.util.List<AxisAlignedBB> oldBoxes, double dx, double dy, double dz,
                                          float turn, double pivotX, double pivotZ) {
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) < 1E-7D && Math.abs(turn) < 1E-5F) return;
        double radians = Math.toRadians(turn), cos = Math.cos(radians), sin = Math.sin(radians);
        java.util.List<AxisAlignedBB> destinationBoxes = new java.util.ArrayList<>(oldBoxes.size());
        AxisAlignedBB sweep = null;
        double searchMargin = mountedCart == null ? .2D : Math.min(2D, Math.hypot(dx, dz) + .35D);
        for (AxisAlignedBB box : oldBoxes) {
            AxisAlignedBB destination = transformedMovingBox(box, dx, dy, dz, pivotX, pivotZ, cos, sin);
            destinationBoxes.add(destination);
            AxisAlignedBB swept = box.union(destination).grow(searchMargin, .2D, searchMargin);
            sweep = sweep == null ? swept : sweep.union(swept);
        }
        if(isRailwayInPortal())for(var entry:new java.util.ArrayList<>(linearPassengerSupports.entrySet()))
            if(world.getTotalWorldTime()-entry.getValue().tick()<=2) {
                AxisAlignedBB bounds=entry.getKey().getEntityBoundingBox().grow(searchMargin,.3,searchMargin);
                sweep=sweep==null?bounds:sweep.union(bounds);
            }
        if (sweep == null) return;
        for (Entity passenger : world.getEntitiesWithinAABBExcludingEntity(this, sweep)) {
            if (!passenger.isEntityAlive() || passenger.noClip || passenger instanceof BearingContraptionEntity
                    || isMountedTo(passenger.getUniqueID()) || carriesSeatedPassenger(passenger)) continue;
            if (world.isRemote && (!(passenger instanceof net.minecraft.entity.player.EntityPlayer player) || !player.isUser())) continue;
            AxisAlignedBB bounds = passenger.getEntityBoundingBox();
            double pushX = 0D, pushY = 0D, pushZ = 0D;
            boolean supported = false;
            double supportTop = Double.NEGATIVE_INFINITY;
            LinearPassengerSupport previousSupport = linearPassengerSupports.get(passenger);
            long now = world.getTotalWorldTime();
            if (passenger.motionY > .001D) {
                // A deliberate jump must be allowed to leave the platform.
                linearPassengerSupports.remove(passenger);
                previousSupport = null;
            }
            for (int index = 0; index < oldBoxes.size(); index++) {
                AxisAlignedBB box = oldBoxes.get(index);
                boolean overlap = bounds.maxX > box.minX + 1E-5D && bounds.minX < box.maxX - 1E-5D
                        && bounds.maxZ > box.minZ + 1E-5D && bounds.minZ < box.maxZ - 1E-5D;
                double relativeTop = box.maxY - (posY - dy);
                boolean sameRecentFloor = previousSupport != null && now >= previousSupport.tick()
                        && now - previousSupport.tick() <= 2L
                        && Math.abs(previousSupport.relativeTop() - relativeTop) < .125D;
                boolean elevatorSupport = isElevator() && dy > 0D && sameRecentFloor
                        && bounds.minY >= box.maxY - Math.max(1D, dy * 2D + .25D)
                        && bounds.minY <= box.maxY + .125D;
                double graceX = Math.min(1.25D, Math.abs(dx) + .2D);
                double graceZ = Math.min(1.25D, Math.abs(dz) + .2D);
                boolean trainSupport = mountedCart != null && sameRecentFloor
                        && bounds.maxX > box.minX - graceX && bounds.minX < box.maxX + graceX
                        && bounds.maxZ > box.minZ - graceZ && bounds.minZ < box.maxZ + graceZ
                        && bounds.minY >= box.maxY - .25D - Math.max(0D, dy)
                        && bounds.minY <= box.maxY + .25D + Math.max(0D, -dy);
                if ((overlap || trainSupport) && passenger.motionY <= .001D
                        && (Math.abs(bounds.minY - box.maxY) <= .125D || elevatorSupport || trainSupport)) {
                    if (!supported || box.maxY > supportTop) {
                        pushY = dy > 0D ? Math.max(dy, box.maxY + dy - bounds.minY) : dy;
                        supportTop = box.maxY;
                    }
                    supported = true;
                    continue;
                }
                AxisAlignedBB shifted = destinationBoxes.get(index);
                if (!shifted.intersects(bounds) || box.intersects(bounds)) continue;
                if (dx > 0) pushX = Math.max(pushX, shifted.maxX - bounds.minX);
                if (dx < 0) pushX = Math.min(pushX, shifted.minX - bounds.maxX);
                if (dy > 0) pushY = Math.max(pushY, shifted.maxY - bounds.minY);
                if (dy < 0) pushY = Math.min(pushY, shifted.minY - bounds.maxY);
                if (dz > 0) pushZ = Math.max(pushZ, shifted.maxZ - bounds.minZ);
                if (dz < 0) pushZ = Math.min(pushZ, shifted.minZ - bounds.maxZ);
            }
            if(!supported&&previousSupport!=null&&now-previousSupport.tick()<=2&&hasPortalFloor(passenger,dx,dy,dz)) {
                supported=true;supportTop=posY-dy+previousSupport.relativeTop();pushY=dy;
            }
            if (supported) {
                double relativeX = passenger.posX - pivotX, relativeZ = passenger.posZ - pivotZ;
                pushX = dx + relativeX * cos + relativeZ * sin - relativeX;
                pushZ = dz - relativeX * sin + relativeZ * cos - relativeZ;
            }
            if (Math.abs(pushX) + Math.abs(pushY) + Math.abs(pushZ) < 1E-7D) continue;
            double beforeY = passenger.posY;
            suppressElevatorCollision = true;
            try { passenger.move(MoverType.SELF, pushX, pushY, pushZ); }
            finally { suppressElevatorCollision = false; }
            if (supported && Math.abs(passenger.posY - beforeY - pushY) < 1E-4D) {
                passenger.onGround = true;
                if (isElevator() || mountedCart != null)
                    linearPassengerSupports.put(passenger, new LinearPassengerSupport(supportTop - (posY - dy), now));
            }
            // Player position is controlled on the client; a velocity packet would cancel walking.
            if (!((isElevator() || mountedCart != null) && passenger instanceof net.minecraft.entity.player.EntityPlayer))
                passenger.velocityChanged = true;
            passenger.fallDistance = 0F;
        }
    }

    private static AxisAlignedBB transformedMovingBox(AxisAlignedBB box, double dx, double dy, double dz,
                                                       double pivotX, double pivotZ, double cos, double sin) {
        if (Math.abs(sin) < 1E-10D && Math.abs(cos - 1D) < 1E-10D) return box.offset(dx, dy, dz);
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{box.minX, box.maxX}) for (double z : new double[]{box.minZ, box.maxZ}) {
            double relativeX = x - pivotX, relativeZ = z - pivotZ;
            double rotatedX = pivotX + relativeX * cos + relativeZ * sin + dx;
            double rotatedZ = pivotZ - relativeX * sin + relativeZ * cos + dz;
            minX = Math.min(minX, rotatedX); maxX = Math.max(maxX, rotatedX);
            minZ = Math.min(minZ, rotatedZ); maxZ = Math.max(maxZ, rotatedZ);
        }
        return new AxisAlignedBB(minX, box.minY + dy, minZ, maxX, box.maxY + dy, maxZ);
    }

    public void applyPendingClientElevatorMovement() {
        if (!world.isRemote || pendingClientElevatorBoxes == null) return;
        java.util.List<AxisAlignedBB> boxes = pendingClientElevatorBoxes;
        pendingClientElevatorBoxes = null;
        if (pendingClientMountedMovement) {
            pendingClientMountedMovement = false;
            carryTransformedEntities(boxes, pendingClientElevatorX, pendingClientElevatorY, pendingClientElevatorZ,
                    net.minecraft.util.math.MathHelper.wrapDegrees(angle - pendingClientMountedAngle),
                    pendingClientMountedPivotX, pendingClientMountedPivotZ);
        } else carryTranslatedEntities(boxes, pendingClientElevatorX, pendingClientElevatorY, pendingClientElevatorZ);
    }

    public BearingContraptionEntity(World world,
                                    BearingContraptionEntity parent,
                                    BlockPos bearingLocal,
                                    EnumFacing facing,
                                    BearingContraption contraption,
                                    NestedContraptionPose pose) {
        this(world);
        this.parentContraption = parent;
        this.parentLocalBearingPos = bearingLocal.toImmutable();
        this.parentContraptionUuid = parent.getUniqueID();
        this.parentPose = pose;
        this.bearingX = bearingLocal.getX();
        this.bearingY = bearingLocal.getY();
        this.bearingZ = bearingLocal.getZ();
        this.facing = facing;
        this.contraption = contraption;
        readActorControlsFromContraption();
        this.anchor = bearingLocal.offset(facing);
        this.angle = pose.angle();
        this.previousAngle = angle;
        this.speed = pose.speed();
        setRiderDestination(EnumFacing.NORTH, BlockPos.ORIGIN);
        syncPoseToDataManager();
        syncEntityPosition();
        populateMobileChunk(contraption);
        updateBounds();
    }

    public BearingContraption getContraption() {
        return contraption;
    }

    /** A seat mount already positions its riders, including on nested structures. */
    public boolean carriesSeatedPassenger(Entity passenger) {
        if (passenger == null) return false;
        for (Entity mount = passenger.getRidingEntity(); mount != null; mount = mount.getRidingEntity()) {
            if (!(mount instanceof SeatEntity seat)) continue;
            if (seat.belongsTo(this)) return true;
            BearingContraptionEntity owner = seat.getBoundContraption();
            for (int depth = 0; owner != null && depth <= NestedBearingManager.MAX_DEPTH; depth++) {
                if (owner == this) return true;
                owner = owner.parentContraption;
            }
            return false;
        }
        return false;
    }

    public BearingContraptionEntity getParentContraption() {
        return parentContraption;
    }

    public boolean isNested() {
        return parentContraptionUuid != null;
    }

    public int getNestedDepth() {
        int depth = 0;
        BearingContraptionEntity current = parentContraption;
        while (current != null && depth < NestedBearingManager.MAX_DEPTH + 1) {
            depth++;
            current = current.parentContraption;
        }
        return depth;
    }

    public void setParentContraption(
            BearingContraptionEntity parent, BlockPos bearingLocal) {
        if (parent == this || parent == null
                || parent.getNestedDepth() >= NestedBearingManager.MAX_DEPTH) {
            return;
        }
        if (parentContraption != parent
                || !bearingLocal.equals(parentLocalBearingPos)) {
            ACTIVE.remove(getBearingPos(), this);
            bearingX = bearingLocal.getX();
            bearingY = bearingLocal.getY();
            bearingZ = bearingLocal.getZ();
            anchor = bearingLocal.offset(facing);
            if (contraption != null) {
                contraption = contraption.translatedTo(anchor);
            }
        }
        parentContraption = parent;
        parentLocalBearingPos = bearingLocal.toImmutable();
        parentContraptionUuid = parent.getUniqueID();
        parentPose = new NestedContraptionPose(
                parentLocalBearingPos, facing, angle, speed, isStalled());
        syncPoseToDataManager();
        syncEntityPosition();
    }

    public void bindParentFromUuid() {
        if (parentContraption != null || parentContraptionUuid == null) {
            return;
        }
        parentContraption = findByUuid(world, parentContraptionUuid);
    }

    public BlockPos getParentLocalBearingPos() {
        return parentLocalBearingPos;
    }

    public java.util.UUID getParentContraptionUuid() {
        return parentContraptionUuid;
    }

    public NestedContraptionPose getParentPose() {
        return parentPose;
    }

    public NestedContraptionPose getParentPose(float parentAngle) {
        if (parentContraption == null || parentLocalBearingPos == null) {
            return null;
        }
        return new NestedContraptionPose(
                parentLocalBearingPos, parentContraption.facing,
                parentAngle, parentContraption.speed,
                parentContraption.isStalled());
    }

    private ContraptionTransform.Point localToRootRaw(
            ContraptionTransform.Point localPoint, float localAngle) {
        if (parentContraption == null || parentLocalBearingPos == null) {
            return new NestedContraptionPose(
                    getBearingPos(), facing, localAngle, speed, isStalled())
                    .localToParent(localPoint).add(translationX(), translationY(), translationZ());
        }
        ContraptionTransform.Point inParent = new NestedContraptionPose(
                parentLocalBearingPos, facing, localAngle, speed, isStalled())
                .localToParent(localPoint);
        BlockPos parentAnchor = parentContraption.getAnchor();
        return parentContraption.localToRootRaw(
                inParent.add(parentAnchor.getX(), parentAnchor.getY(),
                        parentAnchor.getZ()), parentContraption.angle);
    }

    private ContraptionTransform.Point rootToLocalRaw(
            ContraptionTransform.Point rootPoint, float localAngle) {
        if (parentContraption == null || parentLocalBearingPos == null) {
            return new NestedContraptionPose(
                    getBearingPos(), facing, localAngle, speed, isStalled())
                    .parentToLocal(rootPoint.add(-translationX(), -translationY(), -translationZ()));
        }
        ContraptionTransform.Point inParent =
                parentContraption.rootToLocalRaw(
                        rootPoint, parentContraption.angle);
        BlockPos parentAnchor = parentContraption.getAnchor();
        return new NestedContraptionPose(
                parentLocalBearingPos, facing, localAngle, speed, isStalled())
                .parentToLocal(inParent.add(-parentAnchor.getX(),
                        -parentAnchor.getY(), -parentAnchor.getZ()));
    }

    private ContraptionTransform.Point compositeLocalToWorldRaw(
            ContraptionTransform.Point localPoint, float localAngle) {
        ContraptionTransform.Point root = localToRootRaw(localPoint, localAngle);
        return root;
    }

    private Vec3d compositeWorldToLocal(Vec3d worldPoint, float localAngle) {
        ContraptionTransform.Point local = rootToLocalRaw(
                new ContraptionTransform.Point(
                        worldPoint.x, worldPoint.y, worldPoint.z),
                localAngle);
        return new Vec3d(local.x(), local.y(), local.z());
    }

    private ContraptionTransform.Point compositeWorldToLocalRaw(
            ContraptionTransform.Point worldPoint, float localAngle) {
        return rootToLocalRaw(worldPoint, localAngle);
    }

    public java.util.List<ContraptionTransform.Point> renderTransformChain(
            float partialTicks) {
        java.util.List<ContraptionTransform.Point> chain =
                new java.util.ArrayList<>();
        BearingContraptionEntity current = this;
        while (current != null) {
            ContraptionTransform.Point pivot = current.getRotationPivot();
            if (current.parentContraption == null && current.elevator)
                pivot = pivot.add(current.translationX(), current.translationY(), current.translationZ());
            chain.add(0, pivot);
            current = current.parentContraption;
        }
        return chain;
    }

    public java.util.List<Float> renderAngleChain(float partialTicks) {
        java.util.List<Float> chain = new java.util.ArrayList<>();
        BearingContraptionEntity current = this;
        while (current != null) {
            chain.add(0, current.getRenderAngle(partialTicks));
            current = current.parentContraption;
        }
        return chain;
    }

    public java.util.List<ContraptionTransform.Axis> renderAxisChain() {
        java.util.List<ContraptionTransform.Axis> chain =
                new java.util.ArrayList<>();
        BearingContraptionEntity current = this;
        while (current != null) {
            chain.add(0, current.getRotationAxis());
            current = current.parentContraption;
        }
        return chain;
    }

    private ContraptionTransform.Box transformLocalBoxToWorld(
            ContraptionTransform.Box localBox, float localAngle) {
        return ContraptionTransform.transformBox(localBox,
                point -> compositeLocalToWorldRaw(
                        point.add(getAnchor().getX(), getAnchor().getY(),
                                getAnchor().getZ()), localAngle));
    }

    public boolean hasContraptionData() {
        return !isDead && contraption != null && !contraption.blocks().isEmpty();
    }

    public void prepareNestedChildren() {
        nestedBearings.prepareForCapture();
    }

    public void sendNestedSnapshots() {
        nestedBearings.sendSnapshots();
    }

    void refreshNestedPosition() {
        syncEntityPosition();
        updateBounds();
    }

    boolean canReleaseNestedChildren() {
        return nestedBearings.canReleaseChildren();
    }

    void persistNestedBearingTile(BlockPos localPos) {
        persistTile(localPos);
    }

    void persistNestedForSave() {
        if (!world.isRemote && contraption != null && !isRailwayPortalCopy()) {
            persistCarriedFluidTanks();
            nestedBearings.persistForSave();
        }
    }

    void reorientForRelease(BlockPos bearingPos, EnumFacing newFacing,
                            EnumFacing.Axis axis, int turns) {
        reorientNested(bearingPos, newFacing, axis, turns, null);
    }

    void reorientWithinParent(BearingContraptionEntity parent, BlockPos bearingPos,
                              EnumFacing newFacing, EnumFacing.Axis axis, int turns) {
        reorientNested(bearingPos, newFacing, axis, turns, parent);
    }

    private void reorientNested(BlockPos bearingPos, EnumFacing newFacing,
                                EnumFacing.Axis axis, int turns,
                                BearingContraptionEntity newParent) {
        persistCarriedFluidTanks();
        nestedBearings.reorientChildren(axis, turns);
        EnumFacing positiveAxis = EnumFacing.getFacingFromAxis(
                EnumFacing.AxisDirection.POSITIVE, facing.getAxis());
        boolean reverse = BearingContraption.rotateFacing(positiveAxis, axis, turns)
                .getAxisDirection() == EnumFacing.AxisDirection.NEGATIVE;
        BlockPos newAnchor = bearingPos.offset(newFacing);
        BearingContraption rotated = contraption.rotatedTo(newAnchor, axis, turns);
        clearMobileChunk();
        bearingX = bearingPos.getX();
        bearingY = bearingPos.getY();
        bearingZ = bearingPos.getZ();
        facing = newFacing;
        anchor = newAnchor;
        contraption = rotated;
        if (reverse) {
            angle = normalizeAngle(-angle);
            previousAngle = normalizeAngle(-previousAngle);
            speed = -speed;
        }
        parentContraption = newParent;
        parentContraptionUuid = newParent == null ? null : newParent.getUniqueID();
        parentLocalBearingPos = newParent == null ? null : bearingPos.toImmutable();
        parentPose = newParent == null ? null : new NestedContraptionPose(
                parentLocalBearingPos, facing, angle, speed, isStalled());
        if (newParent == null) {
            ownedByBearing = true;
            registerActive();
        }
        populateMobileChunk(contraption);
        syncPoseToDataManager();
        syncEntityPosition();
        updateBounds();
    }

    public static void queueClientSnapshot(int entityId, NBTTagCompound snapshot) {
        PENDING_CLIENT_SNAPSHOTS.put(entityId, snapshot.copy());
    }

    public void restoreAndDiscard() {
        restoreSnapshotAndDiscard();
    }

    /** Preflight all carriages before a station restores any of them. */
    public boolean canRestoreMounted(java.util.Set<BlockPos> claimed) {
        if (world.isRemote || isDead || restoringSnapshot || mountedCart == null || contraption == null
                || !nestedBearings.canReleaseChildren()) return false;
        if (Math.abs(cartX - Math.round(cartX)) > .125D || Math.abs(cartY - Math.round(cartY)) > .125D
                || Math.abs(cartZ - Math.round(cartZ)) > .125D
                || Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(angle - Math.round(angle / 90F) * 90F)) > 2F)
            return false;
        BlockPos settlement = getAnchor().add(Math.round(cartX), Math.round(cartY), Math.round(cartZ));
        BearingContraption check = contraption.rotatedTo(settlement, EnumFacing.Axis.Y,
                Math.round((float) ContraptionTransform.quarterTurn(angle) / 90F));
        java.util.Set<BlockPos> local = new java.util.HashSet<>();
        for (BearingContraption.Snapshot snapshot : check.blocks().values()) {
            if (snapshot.state().getBlock() == com.cogwork.cogworkbridge.registry.AllBlocks.MINECART_ANCHOR) continue;
            BlockPos target = snapshot.pos();
            if (!world.isBlockLoaded(target) || claimed.contains(target) || !local.add(target)
                    || !world.getBlockState(target).getBlock().isReplaceable(world, target)) return false;
        }
        claimed.addAll(local);
        return true;
    }

    private void restoreSnapshotAndDiscard() {
        restoreSnapshotAndDiscard(mountedCart != null
                ? getAnchor().add(Math.round(cartX), Math.round(cartY), Math.round(cartZ))
                : elevator ? getAnchor().offset(linearDirection, Math.round(verticalOffset)) : getAnchor());
    }

    private void restoreSnapshotAndDiscard(BlockPos settlementAnchor) {
        if (world.isRemote || isDead || restoringSnapshot) {
            return;
        }
        if (elevator && contraption != null) {
            LinearContraptionController controller = mountedCart == null && world.isBlockLoaded(elevatorController)
                    && world.getTileEntity(elevatorController) instanceof LinearContraptionController c ? c : null;
            BearingContraption check = mountedCart == null ? contraption : contraption.rotatedTo(settlementAnchor,
                    EnumFacing.Axis.Y, Math.round((float) ContraptionTransform.quarterTurn(angle) / 90F));
            for (BearingContraption.Snapshot snapshot : check.blocks().values()) {
                BlockPos target = mountedCart == null ? settlementAnchor.add(snapshot.pos().subtract(getAnchor())) : snapshot.pos();
                if (mountedCart != null && snapshot.state().getBlock() == com.cogwork.cogworkbridge.registry.AllBlocks.MINECART_ANCHOR) continue;
                if (controller != null && controller.ignoresStructureBlock(target)) continue;
                if (!world.isBlockLoaded(target)) return;
                IBlockState existing = world.getBlockState(target);
                if (!existing.getBlock().isReplaceable(world, target)) return;
            }
        }
        boolean missingAnchor = contraption != null
                && !contraption.contains(getAnchor());
        if (missingAnchor) {
            for (BearingContraptionEntity current = this; current != null;
                 current = current.parentContraption) {
                if (current.isNested() && current.parentContraption == null) {
                    return;
                }
            }
        }
        ContraptionGuiProxy.closeForStructureTransition(this);
        BearingContraption restoring = contraption;
        if (restoring == null) {
            clearMobileChunk();
            ACTIVE.remove(getBearingPos(), this);
            setDead();
            return;
        }

        if (!nestedBearings.canReleaseChildren()) {
            return;
        }

        restoringSnapshot = true;
        boolean restored = false;
        try {
            nestedBearings.releaseChildren();
            persistCarriedFluidTanks();
            // Prevent world callbacks from replaying this snapshot.
            contraption = null;
            ACTIVE.remove(getBearingPos(), this);
            float settledAngle = (float) ContraptionTransform.quarterTurn(angle);
            EnumFacing.Axis settlementAxis = facing.getAxis();
            for (BearingContraptionEntity current = this;
                 missingAnchor && current.parentContraption != null;
                 current = current.parentContraption) {
                BearingContraptionEntity parent = current.parentContraption;
                EnumFacing.Axis parentAxis = parent.facing.getAxis();
                int turns = Math.floorMod(Math.round((float)
                        ContraptionTransform.quarterTurn(parent.angle) / 90.0F), 4);
                QuarterTurnGrid.GridPos3 rotated = QuarterTurnGrid.rotate(
                        new QuarterTurnGrid.GridPos3(settlementAnchor.getX(),
                                settlementAnchor.getY(), settlementAnchor.getZ()),
                        QuarterTurnGrid.Axis.valueOf(parentAxis.name()), turns);
                settlementAnchor = parent.getAnchor().add(
                        rotated.x(), rotated.y(), rotated.z());
                restoring = restoring.rotatedTo(settlementAnchor, parentAxis, turns);
                EnumFacing positiveAxis = EnumFacing.getFacingFromAxis(
                        EnumFacing.AxisDirection.POSITIVE, settlementAxis);
                EnumFacing rotatedAxis = BearingContraption.rotateFacing(
                        positiveAxis, parentAxis, turns);
                settlementAxis = rotatedAxis.getAxis();
                if (rotatedAxis.getAxisDirection() == EnumFacing.AxisDirection.NEGATIVE) {
                    settledAngle = -settledAngle;
                }
            }
            LinearContraptionController restoreController = elevator && mountedCart == null && world.isBlockLoaded(elevatorController)
                    && world.getTileEntity(elevatorController) instanceof LinearContraptionController c ? c : null;
            java.util.Set<BlockPos> mountedAnchors = new java.util.HashSet<>();
            if (mountedCart != null) {
                BearingContraption check = restoring.rotatedTo(settlementAnchor, EnumFacing.Axis.Y, Math.round(settledAngle / 90F));
                for (BearingContraption.Snapshot snapshot : check.blocks().values())
                    if (snapshot.state().getBlock() == com.cogwork.cogworkbridge.registry.AllBlocks.MINECART_ANCHOR)
                        mountedAnchors.add(snapshot.pos());
            }
            java.util.List<SuperGlueEntity> restoredGlue =
                    restoring.restore(world, settlementAnchor, settlementAxis, settledAngle,
                            target -> mountedAnchors.contains(target) || restoreController != null && restoreController.ignoresStructureBlock(target));
            clearMobileChunk();
            for (SuperGlueEntity glue : restoredGlue) {
                if (glue.isEntityAlive()) {
                    ModNetwork.CHANNEL.sendToAllAround(
                            new com.cogwork.cogworkbridge.content.contraptions.glue.GlueAreaUpdatePacket(
                                    glue.getEntityId(), glue.getGlueBox(),
                                    glue.getGlueVolume()),
                            new NetworkRegistry.TargetPoint(world.provider.getDimension(),
                                    glue.posX, glue.posY, glue.posZ, 96.0D));
                }
            }
            restored = true;
            if (elevator && mountedCart == null && world.isBlockLoaded(elevatorController)
                    && world.getTileEntity(elevatorController) instanceof LinearContraptionController controller)
                controller.onContraptionRestored(getUniqueID());
            if (rotaryController != null && world.isBlockLoaded(rotaryController)
                    && world.getTileEntity(rotaryController) instanceof RotaryContraptionController controller)
                controller.onRotaryContraptionRestored(getUniqueID());
        } finally {
            restoringSnapshot = false;
            if (restored) {
                setDead();
            }
        }
    }

    public void discardWithoutRestore() {
        if (world.isRemote) {
            return;
        }
        ContraptionGuiProxy.closeForStructureTransition(this);
        nestedBearings.discardChildren();
        contraption = null;
        ACTIVE.remove(getBearingPos(), this);
        clearMobileChunk();
        setDead();
    }

    public float getAngle() {
        return angle;
    }

    public float getPreviousAngle() {
        return previousAngle;
    }

    public void setAngle(float angle) {
        this.previousAngle = this.angle;
        this.angle = angle % 360.0F;
        if (!world.isRemote) {
            dataManager.set(DATA_ANGLE, this.angle);
        }
        updateBounds();
    }

    public void setAngleFromServer(float angle) {
        if (!world.isRemote) {
            setAngle(angle);
        }
    }

    public void sendPoseToTrackingPlayers() {
        if (!world.isRemote) {
            poseSequence++;
            ModNetwork.CHANNEL.sendToAllAround(
                    new ContraptionAnglePacket(getEntityId(), poseSequence,
                            previousAngle, angle, speed, isStalled()),
                    new NetworkRegistry.TargetPoint(world.provider.getDimension(),
                            posX, posY, posZ, 96.0D));
        }
    }

    /** Applies ordered server pose samples without resetting the client clock. */
    public void applyServerCorrection(int sequence, float serverPreviousAngle,
                                      float serverAngle, float serverSpeed,
                                      boolean stalled) {
        if (!world.isRemote || sequence <= lastClientPoseSequence) {
            return;
        }
        lastClientPoseSequence = sequence;
        float normalizedTarget = normalizeAngle(serverAngle);
        if (!clientPoseInitialized) {
            clientPoseInitialized = true;
            previousAngle = normalizeAngle(serverPreviousAngle);
            angle = normalizedTarget;
        } else if (stalled) {
            previousAngle = normalizedTarget;
            angle = normalizedTarget;
        }
        serverTargetAngle = normalizedTarget;
        speed = serverSpeed;
        clientTargetStalled = stalled;
        dataManager.set(DATA_STALLED, stalled);
        updateBounds();
    }

    public void beginRotation() {
        this.previousAngle = 0.0F;
        this.angle = 0.0F;
        if (!world.isRemote) {
            dataManager.set(DATA_ANGLE, this.angle);
        }
        updateBounds();
    }

    public float getRenderAngle(float partialTicks) {
        double interpolated = ContraptionTransform.interpolate(
                previousAngle, angle, partialTicks);
        return (float) ContraptionTransform.normalizeNear(previousAngle, interpolated);
    }

    /** Uses the authoritative entity pose for rendering as well as collision. */
    public float getRenderAngleFromBearing(float partialTicks) {
        return getRenderAngle(partialTicks);
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
        if (!world.isRemote) {
            dataManager.set(DATA_SPEED, speed);
        }
    }

    /**
     * Traces the contraption in its unrotated grid, then evaluates the actual
     * per-block collision shape. This yields the same hit block and hit point
     * as the rendered structure without relying on an enclosing AABB.
     */
    public ContraptionHit rayTrace(Vec3d start, Vec3d end) {
        return rayTrace(start, end, interactionAngle());
    }

    public ContraptionHit rayTrace(Vec3d start, Vec3d end, float partialTicks) {
        return rayTraceAtAngle(start, end, (float) getPartialAngle(partialTicks));
    }

    public ContraptionHit rayTraceAtAngle(Vec3d start, Vec3d end,
                                          float tracedAngle) {
        if (contraption == null || start == null || end == null) {
            return null;
        }
        ContraptionTransform.Point pivot = getRotationPivot();
        ContraptionTransform.Axis axis = getRotationAxis();
        Vec3d localStart = compositeWorldToLocal(start, tracedAngle);
        Vec3d localEnd = compositeWorldToLocal(end, tracedAngle);
        AxisAlignedBB bounds = new AxisAlignedBB(localStart, localEnd).grow(1.0D);
        ContraptionBlockAccess contraptionWorld = new ContraptionBlockAccess(
                contraption, world, pivot, axis, tracedAngle);
        ContraptionHit best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (BearingContraption.Snapshot snapshot : contraption.blocks().values()) {
            BlockPos local = localPos(snapshot);
            if(!isRailwayBlockVisible(local))continue;
            if (!bounds.intersects(snapshotBounds(snapshot, local, contraptionWorld))) {
                continue;
            }
            AxisAlignedBB shape = snapshot.state().getCollisionBoundingBox(
                    contraptionWorld, local);
            if (shape == null) {
                shape = snapshot.state().getBlock().getBoundingBox(
                        snapshot.state(), contraptionWorld, local);
            }
            AxisAlignedBB absoluteShape = shape.offset(snapshot.pos());
            RayTraceResult clip = absoluteShape.calculateIntercept(localStart, localEnd);
            if (clip == null || clip.hitVec == null) {
                continue;
            }
            double distance = localStart.squareDistanceTo(clip.hitVec);
            if (distance >= bestDistance) {
                continue;
            }
            ContraptionTransform.Point worldHitPoint = compositeLocalToWorldRaw(
                    new ContraptionTransform.Point(
                            clip.hitVec.x, clip.hitVec.y, clip.hitVec.z),
                    tracedAngle);
            Vec3d worldHit = new Vec3d(
                    worldHitPoint.x(), worldHitPoint.y(), worldHitPoint.z());
            Vec3d localHit = clip.hitVec.subtract(
                    snapshot.pos().getX(), snapshot.pos().getY(),
                    snapshot.pos().getZ());
            bestDistance = distance;
            best = new ContraptionHit(local.toImmutable(), snapshot,
                    clip.sideHit, worldHit,
                    (float) localHit.x, (float) localHit.y,
                    (float) localHit.z);
        }
        return best;
    }

    private static AxisAlignedBB snapshotBounds(
            BearingContraption.Snapshot snapshot, BlockPos local,
            ContraptionBlockAccess contraptionWorld) {
        AxisAlignedBB box = snapshot.state().getCollisionBoundingBox(
                contraptionWorld, local);
        if (box == null) {
            box = snapshot.state().getBlock().getBoundingBox(
                    snapshot.state(), contraptionWorld, local);
        }
        return box.offset(snapshot.pos());
    }

    public Vec3d worldPointToLocal(Vec3d worldPoint) {
        if (contraption == null || worldPoint == null) {
            return worldPoint;
        }
        return compositeWorldToLocal(worldPoint, interactionAngle());
    }

    public Vec3d worldPointToLocal(Vec3d worldPoint, float degrees) {
        if (contraption == null || worldPoint == null) {
            return worldPoint;
        }
        return compositeWorldToLocal(worldPoint, degrees);
    }

    public Vec3d localToWorld(Vec3d localPoint) {
        ContraptionTransform.Point result = compositeLocalToWorldRaw(
                new ContraptionTransform.Point(
                        localPoint.x, localPoint.y, localPoint.z),
                interactionAngle());
        return new Vec3d(result.x(), result.y(), result.z());
    }

    public Vec3d localFacingVectorToWorld(EnumFacing localFacing) {
        Vec3d origin = localToWorld(Vec3d.ZERO);
        Vec3d endpoint = localToWorld(new Vec3d(
                localFacing.getXOffset(),
                localFacing.getYOffset(),
                localFacing.getZOffset()));
        return endpoint.subtract(origin).normalize();
    }

    private float interactionAngle() {
        return angle;
    }

    /**
     * Returns the centre of a block at its current rotated pose.
     */
    public Vec3d localBlockCenterToWorld(BlockPos localPos) {
        return localToWorld(new Vec3d(
                getAnchor().getX() + localPos.getX() + 0.5D,
                getAnchor().getY() + localPos.getY() + 0.5D,
                getAnchor().getZ() + localPos.getZ() + 0.5D));
    }

    public Vec3d localBlockCenterToWorldAtAngle(
            BlockPos localPos, double degrees) {
        ContraptionTransform.Point result = compositeLocalToWorldRaw(
                new ContraptionTransform.Point(
                        getAnchor().getX() + localPos.getX() + 0.5D,
                        getAnchor().getY() + localPos.getY() + 0.5D,
                        getAnchor().getZ() + localPos.getZ() + 0.5D),
                (float) degrees);
        return new Vec3d(result.x(), result.y(), result.z());
    }

    public double getPartialAngle(float partialTicks) {
        return ContraptionTransform.interpolate(
                previousAngle, angle, partialTicks);
    }

    public Vec3d localToWorld(Vec3d localPoint, double degrees) {
        ContraptionTransform.Point result = compositeLocalToWorldRaw(
                new ContraptionTransform.Point(
                        localPoint.x, localPoint.y, localPoint.z),
                (float) degrees);
        return new Vec3d(result.x(), result.y(), result.z());
    }

    public Vec3d localBlockCenterToRenderWorld(BlockPos localPos,
                                               float partialTicks) {
        ContraptionTransform.Point result = compositeLocalToWorldRaw(
                new ContraptionTransform.Point(
                        getAnchor().getX() + localPos.getX() + 0.5D,
                        getAnchor().getY() + localPos.getY() + 0.5D,
                        getAnchor().getZ() + localPos.getZ() + 0.5D),
                (float) getPartialAngle(partialTicks));
        return new Vec3d(result.x(), result.y(), result.z());
    }

    public AxisAlignedBB localCollisionBoxToWorld(
            BlockPos localPos, float partialTicks) {
        return localCollisionBoxToWorld(localPos,
                getPartialAngle(partialTicks));
    }

    public AxisAlignedBB localCollisionBoxToWorld(
            BlockPos localPos, double degrees) {
        if (contraption == null || !isRailwayBlockVisible(localPos)) {
            return null;
        }
        BearingContraption.Snapshot snapshot =
                contraption.snapshotAtLocal(localPos).orElse(null);
        if (snapshot == null) {
            return null;
        }
        ContraptionBlockAccess access = new ContraptionBlockAccess(
                contraption, world, getRotationPivot(),
                getRotationAxis(), (float) degrees);
        AxisAlignedBB shape = collisionShape(snapshot, access, localPos);
        if (shape == null) {
            return null;
        }
        ContraptionTransform.Box local = new ContraptionTransform.Box(
                localPos.getX() + shape.minX,
                localPos.getY() + shape.minY,
                localPos.getZ() + shape.minZ,
                localPos.getX() + shape.maxX,
                localPos.getY() + shape.maxY,
                localPos.getZ() + shape.maxZ);
        ContraptionTransform.Box rotated =
                transformLocalBoxToWorld(local, (float) degrees);
        return new AxisAlignedBB(
                rotated.minX(), rotated.minY(), rotated.minZ(),
                rotated.maxX(), rotated.maxY(), rotated.maxZ());
    }

    /**
     * Full-level shape for source blocks used by the world property bridge.
     * Fluid blocks deliberately use their full source volume here even though
     * entity movement still uses their non-solid collision shape.
     */
    public AxisAlignedBB localBlockShapeToWorld(
            BlockPos localPos, double degrees) {
        if (contraption == null) {
            return null;
        }
        BearingContraption.Snapshot snapshot =
                contraption.snapshotAtLocal(localPos).orElse(null);
        if (snapshot == null) {
            return null;
        }
        ContraptionBlockAccess access = new ContraptionBlockAccess(
                contraption, world, getRotationPivot(),
                getRotationAxis(), (float) degrees);
        AxisAlignedBB shape = snapshot.state().getBoundingBox(
                access, localPos);
        ContraptionTransform.Box local = new ContraptionTransform.Box(
                localPos.getX() + shape.minX,
                localPos.getY() + shape.minY,
                localPos.getZ() + shape.minZ,
                localPos.getX() + shape.maxX,
                localPos.getY() + shape.maxY,
                localPos.getZ() + shape.maxZ);
        ContraptionTransform.Box rotated =
                transformLocalBoxToWorld(local, (float) degrees);
        return new AxisAlignedBB(
                rotated.minX(), rotated.minY(), rotated.minZ(),
                rotated.maxX(), rotated.maxY(), rotated.maxZ());
    }

    public Vec3d localBlockCenterToWorld(BlockPos localPos, float partialTicks) {
        return localBlockCenterToRenderWorld(localPos, partialTicks);
    }

    /**
     * Returns the rotated world-space collision box used by ray tracing and
     * movement checks. The returned box is the AABB enclosing the rotated
     * shape, matching Minecraft's collision contract.
     */
    public AxisAlignedBB localCollisionBoxToWorld(BlockPos localPos) {
        return localCollisionBoxToWorld(localPos, interactionAngle());
    }

    private AxisAlignedBB collisionShape(
            BearingContraption.Snapshot snapshot,
            ContraptionBlockAccess access, BlockPos queriedLocalPos) {
        return collisionShape(snapshot, snapshot.state(), access,
                queriedLocalPos);
    }

    private AxisAlignedBB collisionShape(
            BearingContraption.Snapshot snapshot, IBlockState state,
            ContraptionBlockAccess access, BlockPos queriedLocalPos) {
        AxisAlignedBB shape = state.getCollisionBoundingBox(
                access, queriedLocalPos);
        if (shape == null) {
            shape = state.getBlock().getBoundingBox(state, access, queriedLocalPos);
        }
        if (shape != null
                && state.getBlock() instanceof com.cogwork.cogworkbridge.content.logistics.funnel.FunnelBlock
                && queriedLocalPos.getY() == bottomContraptionY()
                && shape.minY < 0 && shape.maxY > 0) {
            shape = new AxisAlignedBB(shape.minX, 0, shape.minZ,
                    shape.maxX, shape.maxY, shape.maxZ);
        }
        return shape;
    }

    private int bottomContraptionY() {
        if (contraption == null || contraption.blocks().isEmpty()) return Integer.MIN_VALUE;
        long version = contraption.contentVersion();
        if (version == bottomYCacheVersion) return bottomYCache;
        int minY = Integer.MAX_VALUE;
        for (BearingContraption.Snapshot member : contraption.blocks().values()) {
            minY = Math.min(minY, member.pos().getY() - getAnchor().getY());
        }
        bottomYCacheVersion = version;
        bottomYCache = minY;
        return bottomYCache;
    }

    /**
     * Returns a representative point on the exposed side of the block closest
     * to the blast centre, used for block hooks that expect a world position.
     */
    public BlockPos explosionReference(BlockPos localPos, Vec3d origin) {
        Vec3d center = localBlockCenterToWorld(localPos);
        Vec3d direction = origin.subtract(center);
        if (direction.lengthSquared() < 1.0E-8D) {
            return new BlockPos(center);
        }
        Vec3d surface = center.add(direction.normalize().scale(0.45D));
        return new BlockPos(surface);
    }

    public boolean isStalled() {
        return dataManager.get(DATA_STALLED);
    }

    /**
     * Applies each carried block's entity contact hooks using the exact
     * rotated world AABB. Walking hooks use the entity-supported block face
     * represented by the contact shape rather than the unrotated grid slot.
     */
    public void tickCarriedBlockProperties() {
        if (world.isRemote || contraption == null || contraption.blocks().isEmpty()) {
            return;
        }
        if (carriedPropertiesTick == world.getTotalWorldTime()) {
            return;
        }
        carriedPropertiesTick = world.getTotalWorldTime();
        updateBounds();
        AxisAlignedBB search = getEntityBoundingBox().grow(2.0D);
        java.util.List<Entity> entities =
                world.getEntitiesWithinAABBExcludingEntity(this, search);
        float currentAngle = interactionAngle();
        ContraptionWorld localWorld = carriedWorld();
        if (localWorld == null) {
            return;
        }
        tickCarriedMaterialContacts(localWorld, entities);
        AxisAlignedBB localSearch = localBoxForWorld(search);
        for (Entity entity : entities) {
            if (!entity.isEntityAlive() || entity.noClip
                    || entity instanceof BearingContraptionEntity) {
                continue;
            }
            AxisAlignedBB localEntity = localBoxForWorld(
                    entity.getEntityBoundingBox());
            AxisAlignedBB query = localEntity.intersect(localSearch);
            BlockPos min = new BlockPos(
                    (int) Math.floor(query.minX) - 1,
                    (int) Math.floor(query.minY) - 1,
                    (int) Math.floor(query.minZ) - 1);
            BlockPos max = new BlockPos(
                    (int) Math.floor(query.maxX) + 1,
                    (int) Math.floor(query.maxY) + 1,
                    (int) Math.floor(query.maxZ) + 1);
            for (BlockPos local : BlockPos.getAllInBox(min, max)) {
                IBlockState liveState = localWorld.getBlockState(local);
                if (liveState.getBlock().isAir(liveState, localWorld, local)) {
                    continue;
                }
                BearingContraption.Snapshot snapshot =
                        contraption.snapshotAtLocal(local).orElse(null);
                if (snapshot == null) {
                    continue;
                }
                try {
                    boolean touchingCell = intersectsLocalShape(
                            entity.getEntityBoundingBox().shrink(0.001D),
                            new AxisAlignedBB(local));
                    AxisAlignedBB shape = liveState.getCollisionBoundingBox(
                            localWorld, local);
                    AxisAlignedBB worldShape = null;
                    if (shape != null) {
                        ContraptionTransform.Box localBox =
                                new ContraptionTransform.Box(
                                        local.getX() + shape.minX,
                                        local.getY() + shape.minY,
                                        local.getZ() + shape.minZ,
                                        local.getX() + shape.maxX,
                                        local.getY() + shape.maxY,
                                        local.getZ() + shape.maxZ);
                        ContraptionTransform.Box rotated =
                                transformLocalBoxToWorld(localBox, currentAngle);
                        worldShape = new AxisAlignedBB(
                                rotated.minX(), rotated.minY(), rotated.minZ(),
                                rotated.maxX(), rotated.maxY(), rotated.maxZ());
                    }
                    AxisAlignedBB feet = entity.getEntityBoundingBox();
                    feet = new AxisAlignedBB(feet.minX, feet.minY - 0.08D,
                            feet.minZ, feet.maxX, feet.minY + 0.01D, feet.maxZ);
                    boolean supported = worldShape != null
                            && isStandingOn(entity, worldShape, shape)
                            && intersectsLocalShape(feet, shape.offset(local));
                    if (!touchingCell && !supported) {
                        continue;
                    }
                    applyCarriedFallenUpon(
                            localWorld, local, liveState, entity, supported);
                    if (touchingCell) {
                        liveState.getBlock().onEntityCollision(
                                localWorld, local, liveState, entity);
                    }
                    if (supported) {
                        entity.onGround = true;
                        liveState.getBlock().onEntityWalk(
                                localWorld, local, entity);
                    }
                } catch (RuntimeException exception) {
                    if (com.cogwork.cogworkbridge.util.LogOnce.first("contraption-contact", liveState.getBlock(), exception)) {
                        com.cogwork.cogworkbridge.CogworkBridge.LOGGER.error(
                                "Carried block {} failed its entity contact hook at {}",
                                liveState, local, exception);
                    }
                }
            }
        }
        long currentTick = world.getTotalWorldTime();
        carriedBreaths.values().removeIf(breath -> breath.tick() < currentTick);
    }

    private boolean intersectsLocalShape(AxisAlignedBB worldBox,
                                         AxisAlignedBB localShape) {
        return intersectsLocalShape(worldBox, localShape,
                collisionFrame(interactionAngle()));
    }

    private CollisionFrame collisionFrame(float candidateAngle) {
        BlockPos anchor = getAnchor();
        Vec3d origin = localToWorldAtAngle(new Vec3d(
                anchor.getX(), anchor.getY(), anchor.getZ()), candidateAngle);
        Vec3d[] axes = {
                localToWorldAtAngle(new Vec3d(anchor.getX() + 1, anchor.getY(), anchor.getZ()), candidateAngle).subtract(origin),
                localToWorldAtAngle(new Vec3d(anchor.getX(), anchor.getY() + 1, anchor.getZ()), candidateAngle).subtract(origin),
                localToWorldAtAngle(new Vec3d(anchor.getX(), anchor.getY(), anchor.getZ() + 1), candidateAngle).subtract(origin)
        };
        return new CollisionFrame(origin, axes);
    }

    private Vec3d localToWorldAtAngle(Vec3d point, float candidateAngle) {
        ContraptionTransform.Point transformed = compositeLocalToWorldRaw(
                new ContraptionTransform.Point(point.x, point.y, point.z), candidateAngle);
        return new Vec3d(transformed.x(), transformed.y(), transformed.z());
    }

    private record CollisionFrame(Vec3d origin, Vec3d[] axes) {
    }

    private static boolean intersectsLocalShape(AxisAlignedBB worldBox,
                                                 AxisAlignedBB localShape,
                                                 CollisionFrame frame) {
        Vec3d origin = frame.origin();
        Vec3d[] axes = frame.axes();
        Vec3d localCenter = new Vec3d(
                (localShape.minX + localShape.maxX) * 0.5D,
                (localShape.minY + localShape.maxY) * 0.5D,
                (localShape.minZ + localShape.maxZ) * 0.5D);
        Vec3d center = origin.add(axes[0].scale(localCenter.x))
                .add(axes[1].scale(localCenter.y))
                .add(axes[2].scale(localCenter.z));
        Vec3d delta = center.subtract(worldBox.getCenter());
        double[] worldHalf = {(worldBox.maxX - worldBox.minX) * 0.5D,
                (worldBox.maxY - worldBox.minY) * 0.5D,
                (worldBox.maxZ - worldBox.minZ) * 0.5D};
        double[] localHalf = {(localShape.maxX - localShape.minX) * 0.5D,
                (localShape.maxY - localShape.minY) * 0.5D,
                (localShape.maxZ - localShape.minZ) * 0.5D};
        Vec3d[] worldAxes = {new Vec3d(1, 0, 0), new Vec3d(0, 1, 0),
                new Vec3d(0, 0, 1)};
        for (Vec3d axis : worldAxes) {
            if (separates(axis, delta, worldHalf, axes, localHalf)) return false;
        }
        for (Vec3d axis : axes) {
            if (separates(axis, delta, worldHalf, axes, localHalf)) return false;
            for (Vec3d worldAxis : worldAxes) {
                if (separates(axis.crossProduct(worldAxis), delta,
                        worldHalf, axes, localHalf)) return false;
            }
        }
        return true;
    }

    private static boolean separates(Vec3d axis, Vec3d delta, double[] worldHalf,
                                     Vec3d[] localAxes, double[] localHalf) {
        if (axis.lengthSquared() < 1.0E-12D) return false;
        double radius = worldHalf[0] * Math.abs(axis.x)
                + worldHalf[1] * Math.abs(axis.y)
                + worldHalf[2] * Math.abs(axis.z);
        for (int i = 0; i < 3; i++) {
            radius += localHalf[i] * Math.abs(axis.dotProduct(localAxes[i]));
        }
        return Math.abs(delta.dotProduct(axis)) > radius + 1.0E-8D;
    }

    /** Maps a world-space entity box through the complete contraption transform. */
    private AxisAlignedBB localBoxForWorld(AxisAlignedBB worldBox) {
        float angle = interactionAngle();
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{worldBox.minX, worldBox.maxX}) {
            for (double y : new double[]{worldBox.minY, worldBox.maxY}) {
                for (double z : new double[]{worldBox.minZ, worldBox.maxZ}) {
                    ContraptionTransform.Point local = compositeWorldToLocalRaw(
                            new ContraptionTransform.Point(x, y, z), angle);
                    minX = Math.min(minX, local.x());
                    minY = Math.min(minY, local.y());
                    minZ = Math.min(minZ, local.z());
                    maxX = Math.max(maxX, local.x());
                    maxY = Math.max(maxY, local.y());
                    maxZ = Math.max(maxZ, local.z());
                }
            }
        }
        BlockPos anchor = getAnchor();
        return new AxisAlignedBB(minX - anchor.getX(), minY - anchor.getY(),
                minZ - anchor.getZ(), maxX - anchor.getX(),
                maxY - anchor.getY(), maxZ - anchor.getZ());
    }

    private void tickCarriedMaterialContacts(ContraptionWorld localWorld,
                                             java.util.List<Entity> entities) {
        for (Entity entity : entities) {
            if (!entity.isEntityAlive() || entity.noClip
                    || entity instanceof BearingContraptionEntity) {
                continue;
            }
            AxisAlignedBB localEntityBox =
                    localBoxForWorld(entity.getEntityBoundingBox());
            AxisAlignedBB fluidBox = localEntityBox.grow(0.0D, -0.4D, 0.0D)
                    .shrink(0.001D);
            localWorld.handleMaterialAcceleration(
                    fluidBox,
                    net.minecraft.block.material.Material.WATER, entity);
            localWorld.handleMaterialAcceleration(
                    fluidBox,
                    net.minecraft.block.material.Material.LAVA, entity);
            tickCarriedWaterBreathing(localWorld, entity);
            if (localWorld.isMaterialInBB(
                    localEntityBox.grow(-0.1D, -0.4D, -0.1D)
                            .shrink(0.001D),
                    net.minecraft.block.material.Material.LAVA)
                    && !entity.isImmuneToFire()
                    && !entity.isEntityInvulnerable(
                            net.minecraft.util.DamageSource.LAVA)) {
                entity.attackEntityFrom(
                        net.minecraft.util.DamageSource.LAVA, 4.0F);
                entity.setFire(15);
            }
        }
    }

    private void tickCarriedWaterBreathing(
            ContraptionWorld localWorld, Entity entity) {
        if (!(entity instanceof net.minecraft.entity.EntityLivingBase living)
                || living.canBreatheUnderwater()
                || living instanceof net.minecraft.entity.player.EntityPlayer
                && ((net.minecraft.entity.player.EntityPlayer) living)
                .capabilities.disableDamage
                || living.isPotionActive(
                        net.minecraft.init.MobEffects.WATER_BREATHING)) {
            return;
        }
        double eyeY = entity.posY + entity.getEyeHeight();
        AxisAlignedBB headBox = new AxisAlignedBB(
                entity.posX - entity.width * 0.25D,
                eyeY - 0.1D,
                entity.posZ - entity.width * 0.25D,
                entity.posX + entity.width * 0.25D,
                eyeY + 0.1D,
                entity.posZ + entity.width * 0.25D);
        AxisAlignedBB localHead = localBoxForWorld(headBox);
        if (!localWorld.isMaterialInBB(
                localHead, net.minecraft.block.material.Material.WATER)) {
            return;
        }
        if (living.isInsideOfMaterial(net.minecraft.block.material.Material.WATER)) {
            return;
        }
        long tick = world.getTotalWorldTime();
        CarriedBreath previous = carriedBreaths.get(living.getUniqueID());
        int air = previous != null && previous.tick() >= tick - 1
                ? previous.air() : living.getAir();
        int respiration = net.minecraft.enchantment.EnchantmentHelper
                .getRespirationModifier(living);
        if (respiration <= 0 || living.getRNG().nextInt(respiration + 1) == 0) {
            air--;
            if (air <= -20) {
                air = 0;
                if (!living.isEntityInvulnerable(
                        net.minecraft.util.DamageSource.DROWN)) {
                    living.attackEntityFrom(
                            net.minecraft.util.DamageSource.DROWN, 2.0F);
                }
            }
        }
        living.setAir(air);
        carriedBreaths.put(living.getUniqueID(), new CarriedBreath(tick, air));
    }

    private record CarriedBreath(long tick, int air) {
    }

    /** Applies world-facing properties at the contraption's current pose. */
    private void tickVisualWorldProperties() {
        worldProperties.tick();
    }

    /** Samples the occupied cells at vanilla's randomTickSpeed rate. */
    private void tickCarriedRandomBlocks() {
        if (contraption == null || contraption.blocks().isEmpty()) {
            return;
        }
        java.util.Random random = world.rand;
        java.util.Map<Long, java.util.List<BearingContraption.Snapshot>> sections =
                new java.util.HashMap<>();
        for (BearingContraption.Snapshot snapshot : contraption.blocks().values()) {
            BlockPos local = snapshot.pos().subtract(getAnchor());
            long section = (((long) (local.getX() >> 4)) & 0x3FFFFFL) << 42
                    | (((long) (local.getZ() >> 4)) & 0x3FFFFFL) << 20
                    | (((long) (local.getY() >> 4)) & 0xFFFFFL);
            sections.computeIfAbsent(section, ignored -> new java.util.ArrayList<>())
                    .add(snapshot);
        }
        for (java.util.List<BearingContraption.Snapshot> section : sections.values()) {
            int samples = Math.max(0, world.getGameRules().getInt("randomTickSpeed"));
            for (int i = 0; i < samples; i++) {
                int selected = random.nextInt(4096);
                if (selected < section.size()) {
                    tickCarriedRandomBlock(section.get(selected), random, 0);
                }
            }
        }
    }

    private void tickCarriedDisplayBlocks() {
        if (!world.isRemote || contraption == null
                || contraption.blocks().isEmpty()
                || (world.getTotalWorldTime() & 3L) != 0L) {
            return;
        }
        ContraptionWorld localWorld = carriedWorld();
        if (localWorld == null) {
            return;
        }
        for (BearingContraption.Snapshot snapshot : contraption.blocks().values()) {
            BlockPos local = snapshot.pos().subtract(getAnchor());
            if (contraption.snapshotAtLocal(local).isEmpty()) {
                continue;
            }
            IBlockState liveState = localWorld.getBlockState(local);
            try {
                liveState.getBlock().randomDisplayTick(
                        liveState, localWorld, local,
                        new java.util.Random(world.rand.nextLong()));
            } catch (RuntimeException exception) {
                if (com.cogwork.cogworkbridge.util.LogOnce.first("contraption-display-tick", liveState.getBlock(), exception)) {
                    com.cogwork.cogworkbridge.CogworkBridge.LOGGER.error(
                            "Carried block {} failed its display tick at {}",
                            liveState, local, exception);
                }
            }
        }
    }

    private void tickCarriedRandomBlock(BearingContraption.Snapshot snapshot,
                                        java.util.Random random, int depth) {
        if (depth >= 8 || contraption == null) {
            return;
        }
        BlockPos local = snapshot.pos().subtract(getAnchor());
        if (contraption.snapshotAtLocal(local).isEmpty()) {
            return;
        }
        try {
            ContraptionWorld localWorld = carriedWorld();
            if (localWorld == null) {
                return;
            }
            IBlockState liveState = localWorld.getBlockState(local);
            if (liveState.getBlock().getTickRandomly()) {
                liveState.getBlock().randomTick(
                        localWorld, local, liveState, random);
            }
        } catch (RuntimeException exception) {
            if (com.cogwork.cogworkbridge.util.LogOnce.first("contraption-random-tick", snapshot.state().getBlock(), exception)) {
                com.cogwork.cogworkbridge.CogworkBridge.LOGGER.error(
                        "Carried block {} failed its random tick at {}",
                        snapshot.state(), local, exception);
            }
        }
    }

    private static boolean isStandingOn(Entity entity, AxisAlignedBB worldShape,
                                        AxisAlignedBB localShape) {
        double touching = Math.abs(entity.getEntityBoundingBox().minY
                - worldShape.maxY);
        double allowed = 0.08D + Math.abs(localShape.maxY - localShape.minY) * 0.01D;
        if (touching > allowed) {
            return false;
        }
        return entity.getEntityBoundingBox().maxX > worldShape.minX
                && entity.getEntityBoundingBox().minX < worldShape.maxX
                && entity.getEntityBoundingBox().maxZ > worldShape.minZ
                && entity.getEntityBoundingBox().minZ < worldShape.maxZ;
    }

    private void applyCarriedFallenUpon(ContraptionWorld localWorld,
                                        BlockPos localPos,
                                        IBlockState state, Entity entity,
                                        boolean supported) {
        if (!supported || entity.fallDistance <= 0.0F) {
            return;
        }
        float fallDistance = entity.fallDistance;
        entity.fallDistance = 0.0F;
        state.getBlock().onFallenUpon(
                localWorld, localPos, entity, fallDistance);
    }

    public void setStalled(boolean stalled) {
        if (!world.isRemote && dataManager.get(DATA_STALLED) != stalled) {
            dataManager.set(DATA_STALLED, stalled);
        }
        if (stalled) {
            propagateStallToChildren(true);
        }
    }

    /**
     * Freezes every descendant when a parent cannot complete its scheduled
     * movement. Clearing the parent's stall does not resume a child that is
     * independently blocked.
     */
    public void propagateStallToChildren(boolean stalled) {
        if (world == null || world.isRemote || !stalled) {
            return;
        }
        nestedBearings.propagateStallToChildren();
    }

    /**
     * Advances only when every rotated block clears the real world. The
     * candidate is split into one-degree slices to prevent tunnelling.
     */
    public boolean advanceToward(float requestedAngle, float speed) {
        if (world.isRemote || contraption == null) {
            return false;
        }
        if (fluidInterfaceHoldTicks > 0) {
            fluidInterfaceHoldTicks--;
            setStalled(true);
            return false;
        }
        java.util.List<com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillTileEntity> drills =
                movingDrills();
        java.util.List<com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawTileEntity> saws =
                movingSaws();
        java.util.List<com.cogwork.cogworkbridge.content.contraptions.actors.plough.MechanicalPloughTileEntity> ploughs =
                movingPloughs();
        java.util.List<com.cogwork.cogworkbridge.content.contraptions.actors.harvester.MechanicalHarvesterTileEntity> harvesters =
                movingHarvesters();
        java.util.List<com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerTileEntity> rollers =
                movingRollers();
        if (!contraption.contains(getAnchor())) {
            drills.forEach(com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillTileEntity::stopMovingBreaker);
            saws.forEach(com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawTileEntity::stopMovingBreaker);
            ploughs.forEach(com.cogwork.cogworkbridge.content.contraptions.actors.plough.MechanicalPloughTileEntity::stopMoving);
            harvesters.forEach(com.cogwork.cogworkbridge.content.contraptions.actors.harvester.MechanicalHarvesterTileEntity::stopMoving);
            rollers.forEach(com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerTileEntity::stopMoving);
            setStalled(true);
            return false;
        }
        if (parentContraption != null && getParentChainStalled()) {
            drills.forEach(com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillTileEntity::stopMovingBreaker);
            saws.forEach(com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawTileEntity::stopMovingBreaker);
            ploughs.forEach(com.cogwork.cogworkbridge.content.contraptions.actors.plough.MechanicalPloughTileEntity::stopMoving);
            harvesters.forEach(com.cogwork.cogworkbridge.content.contraptions.actors.harvester.MechanicalHarvesterTileEntity::stopMoving);
            rollers.forEach(com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerTileEntity::stopMoving);
            setStalled(true);
            return false;
        }
        if (speed == 0.0F) {
            drills.forEach(com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillTileEntity::stopMovingBreaker);
            saws.forEach(com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawTileEntity::stopMovingBreaker);
            ploughs.forEach(com.cogwork.cogworkbridge.content.contraptions.actors.plough.MechanicalPloughTileEntity::stopMoving);
            harvesters.forEach(com.cogwork.cogworkbridge.content.contraptions.actors.harvester.MechanicalHarvesterTileEntity::stopMoving);
            rollers.forEach(com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerTileEntity::stopMoving);
        }
        double requestedDelta = ContraptionTransform.shortestDelta(angle,
                requestedAngle);
        int steps = Math.max(1, (int) Math.ceil(Math.abs(requestedDelta)));
        float step = (float) (requestedDelta / steps);
        float candidate = angle;
        java.util.List<AxisAlignedBB> localShapes = movingCollisionShapes();
        java.util.Map<BlockPos, java.util.List<AxisAlignedBB>> worldShapes =
                new java.util.HashMap<>();
        for (int i = 0; i < steps; i++) {
            float next = (float) ContraptionTransform.normalize(candidate + step);
            boolean drilling = false;
            if (speed != 0.0F) {
                for (com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillTileEntity drill : drills) {
                    drilling |= drill.tickMovingStep(this, candidate, next, speed);
                    if (drill.consumeWorldChanged()) {
                        worldShapes.clear();
                    }
                }
                for (com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawTileEntity saw : saws) {
                    drilling |= saw.tickMovingStep(this, candidate, next, speed);
                    if (saw.consumeWorldChanged()) {
                        worldShapes.clear();
                    }
                }
                for (com.cogwork.cogworkbridge.content.contraptions.actors.plough.MechanicalPloughTileEntity plough : ploughs) {
                    drilling |= plough.tickMovingStep(this, candidate, next, speed);
                    if (plough.consumeWorldChanged()) {
                        worldShapes.clear();
                    }
                }
                for (com.cogwork.cogworkbridge.content.contraptions.actors.harvester.MechanicalHarvesterTileEntity harvester : harvesters) {
                    harvester.tickMovingStep(this, candidate, next);
                    if (harvester.consumeWorldChanged()) {
                        worldShapes.clear();
                    }
                }
                for (com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerTileEntity roller : rollers) {
                    drilling |= roller.tickMovingStep(this, candidate, next, speed);
                    if (roller.consumeWorldChanged()) {
                        worldShapes.clear();
                    }
                }
            }
            if (drilling) {
                if (Math.abs(ContraptionTransform.shortestDelta(angle, candidate)) > 1.0E-5D) {
                    setAngle(candidate);
                }
                setStalled(true);
                return false;
            }
            if (collidesAt(next, localShapes, worldShapes)) {
                float remaining = step;
                for (int refinement = 0; refinement < 6; refinement++) {
                    remaining *= 0.5F;
                    float midpoint = (float) ContraptionTransform.normalize(
                            candidate + remaining);
                    if (!collidesAt(midpoint, localShapes, worldShapes)) {
                        candidate = midpoint;
                    }
                }
                if (Math.abs(ContraptionTransform.shortestDelta(angle, candidate))
                        > 1.0E-5D) {
                    setAngle(candidate);
                }
                setStalled(true);
                return false;
            }
            candidate = next;
        }
        setStalled(false);
        setAngle(candidate);
        return true;
    }

    public void holdForFluidInterface() {
        if (!world.isRemote && !isDead) {
            fluidInterfaceHoldTicks = Math.max(fluidInterfaceHoldTicks, 2);
        }
    }

    /** Actor controls affect the work carried by this structure, not the bearing itself. */
    public boolean isActorDisabled(Block block) {
        // The elevator contact must not disable itself when it powers the
        // floor selector above it. Other actor filters still apply normally.
        if (isElevator() && block == com.cogwork.cogworkbridge.registry.AllBlocks.REDSTONE_CONTACT) return false;
        if (mountedCart != null && cartRotationMode == 1 && cartTurning) return true;
        ensureActorControls();
        return actorDisabled(new ItemStack(block));
    }

    private static String actorKey(ItemStack stack) {
        return stack.isEmpty() || stack.getItem().getRegistryName() == null ? "" : stack.getItem().getRegistryName().toString();
    }

    private boolean actorDisabled(ItemStack stack) {
        String key = actorKey(stack);
        if (key.isEmpty()) return allActorsDisabled && enabledActorExceptions.isEmpty();
        return allActorsDisabled ? !enabledActorExceptions.contains(key) : disabledActorTypes.contains(key);
    }

    private void ensureActorControls() {
        if (actorControlsInitialized || getMobileChunk() == null) return;
        actorControlsInitialized = true;
        for (TileEntity tile : getMobileChunk().chunkTileEntityMap.values()) {
            // Upstream elevator controls are floor selectors and do not seed
            // the list of disabled actors when the cabin is assembled.
            if (isElevator()) break;
            if (!(tile instanceof com.cogwork.cogworkbridge.content.contraptions.controls.ContraptionControlsTileEntity controls)
                    || !controls.isDisabled()) continue;
            String key = actorKey(controls.getFilter());
            if (key.isEmpty()) allActorsDisabled = true; else disabledActorTypes.add(key);
        }
        if (allActorsDisabled) disabledActorTypes.clear();
        saveActorControlsToContraption();
    }

    public void toggleActorControl(ItemStack filter) {
        ensureActorControls();
        boolean disable = !actorDisabled(filter);
        setActorControl(filter, disable);
    }

    public void setActorControl(ItemStack filter, boolean disable) {
        ensureActorControls();
        String key = actorKey(filter);
        if (key.isEmpty()) {
            allActorsDisabled = disable;
            disabledActorTypes.clear(); enabledActorExceptions.clear();
        } else if (allActorsDisabled) {
            if (disable) enabledActorExceptions.remove(key); else enabledActorExceptions.add(key);
        } else {
            if (disable) disabledActorTypes.add(key); else disabledActorTypes.remove(key);
        }
        saveActorControlsToContraption();
        if (getMobileChunk() == null) return;
        for (TileEntity tile : getMobileChunk().chunkTileEntityMap.values())
            if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.controls.ContraptionControlsTileEntity controls)
                controls.setMovingDisabled(actorDisabled(controls.getFilter()));
    }

    private void writeActorControls(NBTTagCompound tag) {
        if (!actorControlsInitialized) return;
        tag.setBoolean("ActorControlInitialized", true);
        tag.setBoolean("AllActorsDisabled", allActorsDisabled);
        net.minecraft.nbt.NBTTagList disabled = new net.minecraft.nbt.NBTTagList();
        for (String key : disabledActorTypes) disabled.appendTag(new net.minecraft.nbt.NBTTagString(key));
        tag.setTag("DisabledActorTypes", disabled);
        net.minecraft.nbt.NBTTagList exceptions = new net.minecraft.nbt.NBTTagList();
        for (String key : enabledActorExceptions) exceptions.appendTag(new net.minecraft.nbt.NBTTagString(key));
        tag.setTag("EnabledActorExceptions", exceptions);
    }

    private void readActorControls(NBTTagCompound tag) {
        actorControlsInitialized = tag.getBoolean("ActorControlInitialized");
        allActorsDisabled = tag.getBoolean("AllActorsDisabled");
        disabledActorTypes.clear(); enabledActorExceptions.clear();
        net.minecraft.nbt.NBTTagList disabled = tag.getTagList("DisabledActorTypes", 8);
        for (int i = 0; i < disabled.tagCount(); i++) disabledActorTypes.add(disabled.getStringTagAt(i));
        net.minecraft.nbt.NBTTagList exceptions = tag.getTagList("EnabledActorExceptions", 8);
        for (int i = 0; i < exceptions.tagCount(); i++) enabledActorExceptions.add(exceptions.getStringTagAt(i));
    }

    private void readActorControlsFromContraption() {
        if (contraption == null) return;
        NBTTagCompound data = contraption.actorControls();
        if (data != null) readActorControls(data);
    }

    private void saveActorControlsToContraption() {
        if (contraption == null || world.isRemote || !actorControlsInitialized) return;
        NBTTagCompound data = new NBTTagCompound();
        writeActorControls(data);
        contraption.setActorControls(data);
        syncSavedContraption();
    }

    private java.util.List<com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillTileEntity> movingDrills() {
        if (getMobileChunk() == null) {
            return java.util.List.of();
        }
        java.util.List<com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillTileEntity> drills =
                new java.util.ArrayList<>();
        for (TileEntity tile : getMobileChunk().chunkTileEntityMap.values()) {
            if (tile instanceof com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillTileEntity drill) {
                if (isActorDisabled(com.cogwork.cogworkbridge.registry.AllBlocks.MECHANICAL_DRILL)) drill.stopMovingBreaker(); else drills.add(drill);
            }
        }
        return drills;
    }

    private java.util.List<com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawTileEntity> movingSaws() {
        if (getMobileChunk() == null) {
            return java.util.List.of();
        }
        java.util.List<com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawTileEntity> saws =
                new java.util.ArrayList<>();
        for (TileEntity tile : getMobileChunk().chunkTileEntityMap.values()) {
            if (tile instanceof com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawTileEntity saw) {
                if (isActorDisabled(com.cogwork.cogworkbridge.registry.AllBlocks.MECHANICAL_SAW)) saw.stopMovingBreaker(); else saws.add(saw);
            }
        }
        return saws;
    }

    private java.util.List<com.cogwork.cogworkbridge.content.contraptions.actors.plough.MechanicalPloughTileEntity> movingPloughs() {
        if (getMobileChunk() == null) {
            return java.util.List.of();
        }
        java.util.List<com.cogwork.cogworkbridge.content.contraptions.actors.plough.MechanicalPloughTileEntity> ploughs =
                new java.util.ArrayList<>();
        for (TileEntity tile : getMobileChunk().chunkTileEntityMap.values()) {
            if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.actors.plough.MechanicalPloughTileEntity plough) {
                if (isActorDisabled(com.cogwork.cogworkbridge.registry.AllBlocks.MECHANICAL_PLOUGH)) plough.stopMoving(); else ploughs.add(plough);
            }
        }
        return ploughs;
    }

    private java.util.List<com.cogwork.cogworkbridge.content.contraptions.actors.harvester.MechanicalHarvesterTileEntity> movingHarvesters() {
        if (getMobileChunk() == null) {
            return java.util.List.of();
        }
        java.util.List<com.cogwork.cogworkbridge.content.contraptions.actors.harvester.MechanicalHarvesterTileEntity> harvesters =
                new java.util.ArrayList<>();
        for (TileEntity tile : getMobileChunk().chunkTileEntityMap.values()) {
            if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.actors.harvester.MechanicalHarvesterTileEntity harvester) {
                if (isActorDisabled(com.cogwork.cogworkbridge.registry.AllBlocks.MECHANICAL_HARVESTER)) harvester.stopMoving(); else harvesters.add(harvester);
            }
        }
        return harvesters;
    }

    private java.util.List<com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerTileEntity> movingRollers() {
        if (getMobileChunk() == null) {
            return java.util.List.of();
        }
        java.util.List<com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerTileEntity> rollers =
                new java.util.ArrayList<>();
        for (TileEntity tile : getMobileChunk().chunkTileEntityMap.values()) {
            if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerTileEntity roller) {
                if (isActorDisabled(com.cogwork.cogworkbridge.registry.AllBlocks.MECHANICAL_ROLLER)) roller.stopMoving(); else rollers.add(roller);
            }
        }
        return rollers;
    }

    private boolean getParentChainStalled() {
        BearingContraptionEntity current = parentContraption;
        while (current != null) {
            if (current.isStalled()) {
                return true;
            }
            current = current.parentContraption;
        }
        return false;
    }

    private java.util.List<AxisAlignedBB> movingCollisionShapes() {
        java.util.List<AxisAlignedBB> shapes = new java.util.ArrayList<>();
        ContraptionWorld localWorld = carriedWorld();
        ContraptionBlockAccess fallback = localWorld == null
                ? new ContraptionBlockAccess(contraption, world, this) : null;
        for (BearingContraption.Snapshot snapshot : contraption.blocks().values()) {
            BlockPos local = snapshot.pos().subtract(getAnchor());
            if(!isRailwayBlockVisible(local))continue;
            if (localWorld != null) {
                IBlockState state = localWorld.getBlockState(local);
                state.addCollisionBoxToList(localWorld, local,
                        new AxisAlignedBB(local).grow(4.0D), shapes, null, false);
            } else {
                AxisAlignedBB shape = snapshot.state().getCollisionBoundingBox(fallback, local);
                if (shape != null) shapes.add(shape.offset(local));
            }
        }
        return shapes;
    }

    /** Finds a block in a carried tool's leading volume before structure collision stalls it. */
    public BlockPos findToolTarget(BlockPos localPos, EnumFacing facing, float candidateAngle,
                                   BlockPos previousTarget,
                                   java.util.function.BiPredicate<BlockPos, IBlockState> accepts) {
        double minX = localPos.getX(), minY = localPos.getY(), minZ = localPos.getZ();
        double maxX = minX + 1.0D, maxY = minY + 1.0D, maxZ = minZ + 1.0D;
        switch (facing) {
            case EAST -> { minX += 0.5D; maxX += 0.15D; }
            case WEST -> { minX -= 0.15D; maxX -= 0.5D; }
            case UP -> { minY += 0.5D; maxY += 0.15D; }
            case DOWN -> { minY -= 0.15D; maxY -= 0.5D; }
            case SOUTH -> { minZ += 0.5D; maxZ += 0.15D; }
            case NORTH -> { minZ -= 0.15D; maxZ -= 0.5D; }
        }
        AxisAlignedBB localVolume = new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
        ContraptionTransform.Box worldBounds = transformLocalBoxToWorld(new ContraptionTransform.Box(
                minX, minY, minZ, maxX, maxY, maxZ), candidateAngle);
        AxisAlignedBB bounds = new AxisAlignedBB(worldBounds.minX(), worldBounds.minY(), worldBounds.minZ(),
                worldBounds.maxX(), worldBounds.maxY(), worldBounds.maxZ());
        CollisionFrame frame = collisionFrame(candidateAngle);
        BlockPos anchor = getAnchor();
        Vec3d tip = localToWorld(new Vec3d(anchor.getX() + localPos.getX() + 0.5D + facing.getXOffset() * 0.65D,
                anchor.getY() + localPos.getY() + 0.5D + facing.getYOffset() * 0.65D,
                anchor.getZ() + localPos.getZ() + 0.5D + facing.getZOffset() * 0.65D), candidateAngle);
        BlockPos closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (BlockPos candidate : BlockPos.getAllInBox(new BlockPos(bounds.minX, bounds.minY, bounds.minZ),
                new BlockPos(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if ((!isNested() && candidate.equals(getBearingPos())) || !world.isBlockLoaded(candidate)) continue;
            IBlockState state = world.getBlockState(candidate);
            if (!accepts.test(candidate, state)) continue;
            AxisAlignedBB obstacle = state.getCollisionBoundingBox(world, candidate);
            obstacle = obstacle == null ? new AxisAlignedBB(candidate) : obstacle.offset(candidate);
            if (!obstacle.intersects(bounds) || !intersectsLocalShape(obstacle, localVolume, frame)) continue;
            if (candidate.equals(previousTarget)) return candidate.toImmutable();
            double distance = tip.squareDistanceTo(candidate.getX() + 0.5D,
                    candidate.getY() + 0.5D, candidate.getZ() + 0.5D);
            if (distance < closestDistance) {
                closest = candidate.toImmutable();
                closestDistance = distance;
            }
        }
        return closest;
    }

    private boolean collidesAt(float candidateAngle,
                               java.util.List<AxisAlignedBB> localShapes,
                               java.util.Map<BlockPos, java.util.List<AxisAlignedBB>> worldShapes) {
        CollisionFrame frame = collisionFrame(candidateAngle);
        for (AxisAlignedBB localShape : localShapes) {
            ContraptionTransform.Box localBox = new ContraptionTransform.Box(
                    localShape.minX, localShape.minY, localShape.minZ,
                    localShape.maxX, localShape.maxY, localShape.maxZ);
            ContraptionTransform.Box worldShape =
                    transformLocalBoxToWorld(localBox, candidateAngle);
            if (intersectsWorld(localShape, worldShape, frame, worldShapes)) {
                return true;
            }
        }
        return false;
    }

    private boolean intersectsWorld(AxisAlignedBB localShape,
                                    ContraptionTransform.Box shape,
                                    CollisionFrame frame,
                                    java.util.Map<BlockPos, java.util.List<AxisAlignedBB>> worldShapes) {
        BlockPos min = new BlockPos(
                (int) Math.floor(shape.minX()) - 1,
                (int) Math.floor(shape.minY()) - 1,
                (int) Math.floor(shape.minZ()) - 1);
        BlockPos max = new BlockPos(
                (int) Math.floor(shape.maxX()) + 1,
                (int) Math.floor(shape.maxY()) + 1,
                (int) Math.floor(shape.maxZ()) + 1);
        AxisAlignedBB moving = new AxisAlignedBB(
                shape.minX(), shape.minY(), shape.minZ(),
                shape.maxX(), shape.maxY(), shape.maxZ())
                .grow(-1.0E-5D);
        for (BlockPos candidate : BlockPos.getAllInBox(min, max)) {
            if (!elevator && parentContraption == null && candidate.equals(getBearingPos())) {
                continue;
            }
            if (elevator && candidate.equals(elevatorController)
                    && world.getTileEntity(elevatorController) instanceof LinearContraptionController controller
                    && controller.ignoresStructureBlock(candidate)) continue;
            java.util.List<AxisAlignedBB> obstacles = worldShapes.get(candidate);
            if (obstacles == null) {
                if (!world.isBlockLoaded(candidate)) {
                    AxisAlignedBB unloaded = new AxisAlignedBB(candidate);
                    if (unloaded.intersects(moving)
                            && intersectsLocalShape(unloaded.grow(-1.0E-5D),
                            localShape, frame)) {
                        return true;
                    }
                    continue;
                }
                IBlockState state = world.getBlockState(candidate);
                if (state.getBlock() == net.minecraft.init.Blocks.AIR) {
                    obstacles = java.util.List.of();
                } else {
                    obstacles = new java.util.ArrayList<>();
                    state.addCollisionBoxToList(world, candidate,
                            new AxisAlignedBB(candidate).grow(4.0D), obstacles, null, false);
                }
                worldShapes.put(candidate.toImmutable(), obstacles);
            }
            for (AxisAlignedBB other : obstacles) {
                if (other.intersects(moving)
                        && intersectsLocalShape(other.grow(-1.0E-5D), localShape, frame)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String removeLocalBlock(BlockPos localPos) {
        if (world.isRemote || contraption == null) {
            return null;
        }
        BearingContraption.RemovalResult removal = removeLocalBlockInternal(localPos);
        if (removal == null) {
            return null;
        }
        BearingContraption.Snapshot removed = removal.removedBlock();
        return removed.state().getBlock().getRegistryName() + ":"
                + removed.state().getBlock().getMetaFromState(removed.state());
    }

    public BearingContraption.Snapshot removeLocalBlockSnapshot(BlockPos localPos) {
        if (world.isRemote || contraption == null) {
            return null;
        }
        BearingContraption.RemovalResult removal = removeLocalBlockInternal(localPos);
        return removal == null ? null : removal.removedBlock();
    }

    private BearingContraption.RemovalResult removeLocalBlockInternal(
            BlockPos localPos) {
        java.util.List<ItemStack> removedContents = new java.util.ArrayList<>();
        if (contraption.snapshotAtLocal(localPos).isPresent()) {
            ContraptionWorld localWorld = carriedWorld();
            if (localWorld != null && localWorld.getTileEntity(localPos) instanceof FluidTankTileEntity tank) {
                tank.prepareForContraptionRemoval(localWorld);
            }
            if (localWorld != null && localWorld.getTileEntity(localPos) instanceof
                    com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeTileEntity pipe) {
                ItemStack filter = pipe.getFilterItem();
                if (filter.getItem() instanceof com.cogwork.cogworkbridge.content.fluids.ListFilterItem) {
                    removedContents.add(filter);
                }
            }
            if (localWorld != null && localWorld.getTileEntity(localPos) instanceof
                    com.cogwork.cogworkbridge.content.fluids.ItemDrainTileEntity drain
                    && !drain.getHeldItemStack().isEmpty()) {
                removedContents.add(drain.getHeldItemStack().copy());
            }
            if (localWorld != null && localWorld.getTileEntity(localPos) instanceof
                    com.cogwork.cogworkbridge.content.logistics.depot.DepotTileEntity depot) {
                for (int slot = 0; slot < depot.getSlots(); slot++) {
                    ItemStack stack = depot.getStackInSlot(slot);
                    if (!stack.isEmpty()) removedContents.add(stack.copy());
                }
            }
        }
        persistCarriedFluidTanks();
        BearingContraption.RemovalResult removal =
                contraption.removeAtLocal(localPos);
        if (removal == null) {
            if (com.cogwork.cogworkbridge.util.LogOnce.first("contraption-remove-missing", BearingContraptionEntity.class)) {
                com.cogwork.cogworkbridge.CogworkBridge.LOGGER.warn(
                        "Contraption remove {}: local {} absent root={}",
                        getEntityId(), localPos, contraption.anchor());
            }
            return null;
        }
        if (!removedContents.isEmpty()) spawnDrop(localPos, removedContents);
        IBlockState air = net.minecraft.init.Blocks.AIR.getDefaultState();
        setBlockInMobileChunk(localPos, air, null);
        worldProperties.markDirty();
        sendBlockRemoved(localPos);
        for (BearingContraption.Snapshot snapshot : removal.detachedBlocks()) {
            BlockPos detachedLocal = contraption.localPos(snapshot);
            setBlockInMobileChunk(detachedLocal, air, null);
            sendBlockRemoved(detachedLocal);
        }
        worldProperties.tick();

        if (!contraption.contains(getAnchor())) {
            disassembleMissingAnchor();
            return removal;
        }

        float settledAngle = (float) ContraptionTransform.quarterTurn(angle);
        contraption.restoreDetached(
                world, getAnchor(), facing.getAxis(), settledAngle, removal);
        updateBounds();
        if (contraption.isEmpty()) {
            clearMobileChunk();
            MechanicalBearingTileEntity bearing = bearingTile();
            if (bearing != null) {
                bearing.onContraptionEmptied();
            }
            contraption = null;
            setDead();
        } else {
            MechanicalBearingTileEntity bearing = bearingTile();
            if (bearing != null) {
                bearing.syncSavedContraption(contraption);
            }
        }
        return removal;
    }

    private void sendBlockRemoved(BlockPos localPos) {
        ModNetwork.CHANNEL.sendToAllAround(
                new ContraptionBlockChangedPacket(
                        getEntityId(), localPos, -1, null),
                new NetworkRegistry.TargetPoint(world.provider.getDimension(),
                        posX, posY, posZ, 96.0D));
    }

    public void spawnDrop(BlockPos localPos, java.util.List<ItemStack> drops) {
        if (world.isRemote || drops.isEmpty()) {
            return;
        }
        Vec3d worldPoint = localToWorld(new Vec3d(
                getAnchor().getX() + localPos.getX() + 0.5D,
                getAnchor().getY() + localPos.getY() + 0.5D,
                getAnchor().getZ() + localPos.getZ() + 0.5D));
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) {
                continue;
            }
            net.minecraft.entity.item.EntityItem item =
                    new net.minecraft.entity.item.EntityItem(world,
                            worldPoint.x, worldPoint.y, worldPoint.z,
                            stack.copy());
            item.setDefaultPickupDelay();
            world.spawnEntity(item);
        }
    }

    public void applyClientBlockChange(BlockPos localPos, IBlockState state,
                                       NBTTagCompound tileData) {
        if (!world.isRemote || contraption == null) {
            return;
        }
        if (state.getBlock() == net.minecraft.init.Blocks.AIR) {
            contraption.removeSingleAtLocal(localPos);
            setBlockInMobileChunk(localPos, state, null);
            if (contraption.isEmpty()) {
                clearMobileChunk();
                contraption = null;
                setDead();
                return;
            }
        } else {
            NBTTagCompound tile = tileData == null
                    ? null : tileData.copy();
            contraption.replaceAtLocal(localPos, state, tile);
            setBlockInMobileChunk(localPos, state, tile);
        }
        updateBounds();
    }

    /**
     * Persists every block state and TileEntity mutated by one player
     * interaction, then sends only the positions that actually changed.
     */
    public boolean commitInteractionChanges() {
        if (world.isRemote || contraption == null) {
            return false;
        }
        MobileChunk mobileChunk = getMobileChunk();
        if (mobileChunk == null) {
            return false;
        }
        java.util.Set<BlockPos> changedPositions = new java.util.HashSet<>();
        changedPositions.addAll(mobileChunk.drainDirtyTilePositions());
        if (mobileChunk instanceof
                com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunkServer serverChunk) {
            changedPositions.addAll(serverChunk.getBlockQueue());
            changedPositions.addAll(serverChunk.getTileQueue());
        }

        java.util.Set<BlockPos> changedTilePositions =
                new java.util.HashSet<>();
        java.util.Set<BlockPos> removedPositions =
                new java.util.HashSet<>();
        java.util.List<BearingContraption.RemovalResult> removals =
                new java.util.ArrayList<>();
        java.util.List<BlockPos> orderedPositions =
                new java.util.ArrayList<>(changedPositions);
        if (orderedPositions.remove(BlockPos.ORIGIN)) {
            orderedPositions.add(0, BlockPos.ORIGIN);
        }
        for (BlockPos localPos : orderedPositions) {
            BearingContraption.Snapshot before =
                    contraption.snapshotAtLocal(localPos).orElse(null);
            IBlockState current = mobileChunk.getBlockState(localPos);
            TileEntity tile = mobileChunk.getTileEntity(localPos);
            NBTTagCompound tileData = tile == null ? null
                    : tile.writeToNBT(new NBTTagCompound());

            if (current.getBlock() != Blocks.AIR) {
                contraption.replaceAtLocal(localPos, current, tileData);
                changedTilePositions.add(localPos);
            } else if (before != null) {
                BearingContraption.RemovalResult removal =
                        contraption.removeAtLocal(localPos);
                if (removal != null) {
                    removals.add(removal);
                }
            }
        }
        for (BearingContraption.RemovalResult removal : removals) {
            for (BearingContraption.Snapshot detached :
                    removal.detachedBlocks()) {
                BlockPos detachedLocal = contraption.localPos(detached);
                setBlockInMobileChunk(detachedLocal,
                        Blocks.AIR.getDefaultState(), null);
                removedPositions.add(detachedLocal);
            }
        }

        if (mobileChunk instanceof
                com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunkServer serverChunk) {
            serverChunk.getBlockQueue().clear();
            serverChunk.getTileQueue().clear();
        }

        boolean changed = !changedPositions.isEmpty();
        if (!changed) {
            return false;
        }

        syncSavedContraption();
        updateBounds();
        float settledAngle = (float) ContraptionTransform.quarterTurn(angle);
        for (BearingContraption.RemovalResult removal : removals) {
            contraption.restoreDetached(world, getAnchor(), facing.getAxis(),
                    settledAngle, removal);
        }
        java.util.Set<BlockPos> syncedPositions =
                new java.util.HashSet<>(changedPositions);
        syncedPositions.addAll(changedTilePositions);
        syncedPositions.addAll(removedPositions);
        for (BlockPos localPos : syncedPositions) {
            BearingContraption.Snapshot snapshot =
                    contraption.snapshotAtLocal(localPos).orElse(null);
            int stateId = snapshot == null ? -1
                    : Block.getStateId(snapshot.state());
            NBTTagCompound tile = snapshot == null ? null
                    : contraption.tileDataAt(getAnchor().add(localPos));
            ModNetwork.CHANNEL.sendToAllAround(
                    new ContraptionBlockChangedPacket(
                            getEntityId(), localPos, stateId, tile),
                    new NetworkRegistry.TargetPoint(
                            world.provider.getDimension(),
                            posX, posY, posZ, 96.0D));
        }
        if (!contraption.contains(getAnchor())) {
            disassembleMissingAnchor();
        }
        return true;
    }

    private void disassembleMissingAnchor() {
        if (world.isRemote || contraption == null
                || contraption.contains(getAnchor())) {
            return;
        }
        setStalled(true);
        syncSavedContraption();
        MechanicalBearingTileEntity bearing = bearingTile();
        BearingContraptionEntity parent = parentContraption;
        BlockPos parentBearingPos = parentLocalBearingPos;
        restoreSnapshotAndDiscard();
        if (isDead && bearing != null) {
            bearing.onContraptionEmptied();
            if (parent != null && parentBearingPos != null) {
                parent.persistAndSyncInteractionBlock(parentBearingPos);
            }
        }
    }

    public boolean startInteractionSession() {
        if (world.isRemote || contraption == null) {
            return false;
        }
        if(isRailwayPortalCopy()) {
            if(!isRailwayPortalDataReady())return false;
            if(getMobileChunk()!=null)for(TileEntity tile:getMobileChunk().chunkTileEntityMap.values())tile.setWorld(getMobileChunk().getFakeWorld());
        }
        return interactionSessionDepth++ == 0;
    }

    public void finishInteractionSession() {
        if (interactionSessionDepth > 0) {
            interactionSessionDepth--;
            if(interactionSessionDepth==0)commitRailwayPortalInteraction();
        }
    }

    private void setBlockInMobileChunk(BlockPos localPos, IBlockState state,
                                       NBTTagCompound tileData) {
        MobileChunk mobileChunk = getMobileChunk();
        if (mobileChunk == null) {
            return;
        }
        TileEntity existing = mobileChunk.chunkTileEntityMap.get(localPos);
        if (world.isRemote && tileData != null && state.equals(mobileChunk.getBlockState(localPos))
                && existing instanceof com.cogwork.cogworkbridge.content.fluids.PortableFluidInterfaceTileEntity portable
                && existing.writeToNBT(new NBTTagCompound()).getString("id").equals(tileData.getString("id"))) {
            portable.handleContraptionUpdate(tileData);
            return;
        }
        if (world.isRemote && tileData != null && state.equals(mobileChunk.getBlockState(localPos))
                && existing instanceof com.cogwork.cogworkbridge.content.logistics.portable.PortableStorageInterfaceTileEntity portable
                && existing.writeToNBT(new NBTTagCompound()).getString("id").equals(tileData.getString("id"))) {
            portable.handleContraptionUpdate(tileData);
            return;
        }
        if (world.isRemote && tileData != null
                && state.equals(mobileChunk.getBlockState(localPos))
                && (existing instanceof com.cogwork.cogworkbridge.content.kinetics.deployer.DeployerTileEntity
                || existing instanceof com.cogwork.cogworkbridge.content.processing.press.MechanicalPressTileEntity
                || existing instanceof com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity)
                && existing.writeToNBT(new NBTTagCompound()).getString("id").equals(tileData.getString("id"))) {
            ((com.cogwork.cogworkbridge.kinetic.KineticTileEntity) existing).handleContraptionUpdate(tileData);
            mobileChunk.setChunkModified();
            return;
        }
        // Keep the carried tile entity alive when only its serialized state
        // changed. Recreating every funnel, tunnel, belt or machine resets
        // client-side animation counters (and loses third-party capabilities
        // held by the existing instance) on every contraption update.
        if (world.isRemote && tileData != null
                && state.equals(mobileChunk.getBlockState(localPos))
                && existing != null
                && existing.writeToNBT(new NBTTagCompound()).getString("id")
                        .equals(tileData.getString("id"))) {
            existing.readFromNBT(tileData);
            existing.setPos(localPos);
            existing.setWorld(mobileChunk.getFakeWorld());
            bindContraptionRenderContext(existing, state, localPos);
            existing.validate();
            mobileChunk.setChunkModified();
            return;
        }
        TileEntity old = mobileChunk.chunkTileEntityMap.remove(localPos);
        if (old != null) {
            if (old instanceof net.minecraft.util.ITickable) {
                mobileChunk.updatableTiles.remove(old);
            }
            old.invalidate();
        }
        if (mobileChunk instanceof com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunkClient client) {
            client.normalTESRS.remove(localPos);
            client.fastTESRS.remove(localPos);
        }
        mobileChunk.setBlockState(localPos, state);
        if (tileData != null) {
            if (state.getBlock() instanceof FluidTankBlock) {
                tileData = tileData.copy();
                FluidTankTileEntity.relocateTag(tileData, localPos);
            }
            TileEntity tile = TileEntity.create(mobileChunk.getFakeWorld(), tileData);
            if (tile != null) {
                tile.setPos(localPos);
                tile.setWorld(mobileChunk.getFakeWorld());
                bindContraptionRenderContext(tile, state, localPos);
                tile.validate();
                mobileChunk.setTileEntity(localPos, tile);
            }
        }
        mobileChunk.setChunkModified();
    }

    public record ContraptionHit(BlockPos localPos,
                                 BearingContraption.Snapshot snapshot,
                                 EnumFacing side,
                                 Vec3d worldHit,
                                 float hitX, float hitY, float hitZ) {
    }

    public java.util.List<AxisAlignedBB> collisionBoxes() {
        if (contraption == null) {
            return java.util.List.of();
        }
        MobileChunk chunk = getMobileChunk();
        long version = chunk == null ? -1L : chunk.getModificationVersion();
        java.util.List<CollisionPose> pose = new java.util.ArrayList<>();
        for (BearingContraptionEntity current = this; current != null;
             current = current.parentContraption) {
            pose.add(new CollisionPose(current, current.getAnchor(),
                    current.getFacing(), current.interactionAngle(), current.verticalOffset,
                    current.translationX(), current.translationY(), current.translationZ()));
        }
        long tick = world.getTotalWorldTime();
        if (collisionCacheChunk == chunk && collisionCacheVersion == version
                && collisionCacheTick == tick && collisionCachePose.equals(pose)) {
            return cachedCollisionBoxes;
        }
        java.util.List<AxisAlignedBB> result = new java.util.ArrayList<>();
        ContraptionTransform.Point pivot = getRotationPivot();
        float currentAngle = interactionAngle();
        ContraptionBlockAccess contraptionWorld = new ContraptionBlockAccess(
                contraption, world, pivot, getRotationAxis(), currentAngle);
        for (BearingContraption.Snapshot snapshot : contraption.blocks().values()) {
            BlockPos local = snapshot.pos().subtract(getAnchor());
            if(!isRailwayBlockVisible(local))continue;
            AxisAlignedBB box = collisionShape(snapshot, contraptionWorld, local);
            if (box == null) {
                continue;
            }
            ContraptionTransform.Box localBox = new ContraptionTransform.Box(
                    local.getX() + box.minX,
                    local.getY() + box.minY,
                    local.getZ() + box.minZ,
                    local.getX() + box.maxX,
                    local.getY() + box.maxY,
                    local.getZ() + box.maxZ);
            ContraptionTransform.Box rotated =
                    transformLocalBoxToWorld(localBox, currentAngle);
            result.add(new AxisAlignedBB(
                    rotated.minX(), rotated.minY(), rotated.minZ(),
                    rotated.maxX(), rotated.maxY(), rotated.maxZ()));
        }
        collisionCacheChunk = chunk;
        collisionCacheVersion = version;
        collisionCacheTick = tick;
        collisionCachePose = pose;
        cachedCollisionBoxes = java.util.List.copyOf(result);
        return cachedCollisionBoxes;
    }

    private record CollisionPose(BearingContraptionEntity entity, BlockPos anchor,
                                 EnumFacing facing, float angle, float verticalOffset,
                                 double translationX, double translationY, double translationZ) {
    }

    /**
     * Resolves world entities against the current local block collision shapes.
     *
     * <p>Each entity's swept bounds are transformed into the contraption's
     * unrotated local grid. The nearest contact is then transformed back into
     * world space, matching the renderer's pivot and axis.</p>
     */
    public void resolveEntityCollisions() {
        if (world.isRemote || contraption == null || contraption.blocks().isEmpty()) {
            return;
        }
        java.util.List<AxisAlignedBB> localShapes = movingCollisionShapes();
        if (localShapes.isEmpty()) {
            return;
        }
        double delta = ContraptionTransform.shortestDelta(previousAngle, angle);
        AxisAlignedBB search = sweptCollisionBounds(localShapes, delta);
        for (Entity candidate : world.getEntitiesWithinAABBExcludingEntity(this, search)) {
            if (!candidate.isEntityAlive() || candidate.noClip
                    || candidate == this
                    || candidate instanceof net.minecraft.entity.player.EntityPlayer
                    || candidate instanceof BearingContraptionEntity
                    || carriesSeatedPassenger(candidate)) {
                continue;
            }
            if (Math.abs(delta) > 1.0E-5D) {
                pushEntityAlongSweep(candidate, localShapes, delta);
            }
            if (intersectsCurrentShape(candidate.getEntityBoundingBox())) {
                resolveEntityCollision(candidate, localShapes);
            }
        }
    }

    /** Resolves only the local client player, whose position is client-authoritative. */
    public void resolveClientEntityCollisions(Entity player) {
        if (!world.isRemote || contraption == null || contraption.blocks().isEmpty()
                || player == null || !player.isEntityAlive() || player.noClip || carriesSeatedPassenger(player)) {
            return;
        }
        java.util.List<AxisAlignedBB> localShapes = movingCollisionShapes();
        if (localShapes.isEmpty()) {
            return;
        }
        double delta = ContraptionTransform.shortestDelta(previousAngle, angle);
        AxisAlignedBB playerBounds = player.getEntityBoundingBox();
        if (!sweptCollisionBounds(localShapes, delta).intersects(playerBounds)) {
            return;
        }
        if (Math.abs(delta) > 1.0E-5D) {
            pushEntityAlongSweep(player, localShapes, delta);
        }
        if (intersectsCurrentShape(player.getEntityBoundingBox())) {
            resolveEntityCollision(player, localShapes);
        }
    }

    private AxisAlignedBB sweptCollisionBounds(
            java.util.List<AxisAlignedBB> localShapes, double delta) {
        int steps = Math.max(1, (int) Math.ceil(Math.abs(delta)));
        AxisAlignedBB bounds = null;
        for (int step = 0; step <= steps; step++) {
            float sampleAngle = (float) (previousAngle + delta * step / steps);
            for (AxisAlignedBB localShape : localShapes) {
                ContraptionTransform.Box worldShape = transformLocalBoxToWorld(
                        new ContraptionTransform.Box(localShape.minX,
                                localShape.minY, localShape.minZ,
                                localShape.maxX, localShape.maxY,
                                localShape.maxZ), sampleAngle);
                AxisAlignedBB box = new AxisAlignedBB(worldShape.minX(),
                        worldShape.minY(), worldShape.minZ(),
                        worldShape.maxX(), worldShape.maxY(), worldShape.maxZ());
                bounds = bounds == null ? box : bounds.union(box);
            }
        }
        return bounds == null ? getEntityBoundingBox().grow(0.5D)
                : bounds.grow(0.05D);
    }

    private void pushEntityAlongSweep(Entity entity,
                                     java.util.List<AxisAlignedBB> localShapes,
                                     double delta) {
        int steps = Math.max(1, (int) Math.ceil(Math.abs(delta)));
        AxisAlignedBB entityBounds = entity.getEntityBoundingBox();
        for (AxisAlignedBB localShape : localShapes) {
            for (int step = 1; step <= steps; step++) {
                float fromAngle = (float) (previousAngle
                        + delta * (step - 1) / steps);
                float toAngle = (float) (previousAngle
                        + delta * step / steps);
                if (intersectsLocalShape(entityBounds, localShape,
                        collisionFrame(fromAngle))
                        || !intersectsLocalShape(entityBounds, localShape,
                        collisionFrame(toAngle))) {
                    continue;
                }

                Vec3d correction = minimumCollisionTranslation(
                        entityBounds, localShape, collisionFrame(toAngle));
                if (correction == null) {
                    continue;
                }
                entity.move(MoverType.SHULKER_BOX,
                        correction.x, correction.y, correction.z);
                entity.velocityChanged = true;
                return;
            }
        }
    }

    private boolean intersectsCurrentShape(AxisAlignedBB bounds) {
        for (AxisAlignedBB collisionBox : collisionBoxes()) {
            if (collisionBox.intersects(bounds)) {
                return true;
            }
        }
        return false;
    }

    private void resolveEntityCollision(Entity entity,
                                        java.util.List<AxisAlignedBB> localShapes) {
        CollisionFrame frame = collisionFrame(interactionAngle());
        for (int pass = 0; pass < 4; pass++) {
            Vec3d correction = null;
            for (AxisAlignedBB shape : localShapes) {
                Vec3d candidate = minimumCollisionTranslation(
                        entity.getEntityBoundingBox(), shape, frame);
                if (candidate != null && (correction == null
                        || candidate.lengthSquared() < correction.lengthSquared())) {
                    correction = candidate;
                }
            }
            if (correction == null) {
                break;
            }
            double x = entity.posX;
            double y = entity.posY;
            double z = entity.posZ;
            entity.move(MoverType.SHULKER_BOX,
                    correction.x, correction.y, correction.z);
            entity.velocityChanged = true;
            if (Math.abs(entity.posX - x) + Math.abs(entity.posY - y)
                    + Math.abs(entity.posZ - z) < 1.0E-8D) {
                break;
            }
        }
    }

    private static Vec3d minimumCollisionTranslation(AxisAlignedBB worldBox,
                                                      AxisAlignedBB localShape,
                                                      CollisionFrame frame) {
        Vec3d[] localAxes = frame.axes();
        Vec3d localCenter = localShape.getCenter();
        Vec3d center = frame.origin().add(localAxes[0].scale(localCenter.x))
                .add(localAxes[1].scale(localCenter.y))
                .add(localAxes[2].scale(localCenter.z));
        Vec3d delta = worldBox.getCenter().subtract(center);
        double[] worldHalf = {(worldBox.maxX - worldBox.minX) * 0.5D,
                (worldBox.maxY - worldBox.minY) * 0.5D,
                (worldBox.maxZ - worldBox.minZ) * 0.5D};
        double[] localHalf = {(localShape.maxX - localShape.minX) * 0.5D,
                (localShape.maxY - localShape.minY) * 0.5D,
                (localShape.maxZ - localShape.minZ) * 0.5D};
        Vec3d[] worldAxes = {new Vec3d(1, 0, 0), new Vec3d(0, 1, 0),
                new Vec3d(0, 0, 1)};
        java.util.List<Vec3d> axes = new java.util.ArrayList<>(15);
        java.util.Collections.addAll(axes, worldAxes);
        for (Vec3d localAxis : localAxes) {
            axes.add(localAxis);
            for (Vec3d worldAxis : worldAxes) {
                axes.add(localAxis.crossProduct(worldAxis));
            }
        }
        Vec3d bestAxis = null;
        double smallest = Double.POSITIVE_INFINITY;
        for (Vec3d candidate : axes) {
            double length = Math.sqrt(candidate.lengthSquared());
            if (length < 1.0E-8D) {
                continue;
            }
            Vec3d axis = candidate.scale(1.0D / length);
            double radius = worldHalf[0] * Math.abs(axis.x)
                    + worldHalf[1] * Math.abs(axis.y)
                    + worldHalf[2] * Math.abs(axis.z);
            for (int i = 0; i < 3; i++) {
                radius += localHalf[i] * Math.abs(axis.dotProduct(localAxes[i]));
            }
            double distance = delta.dotProduct(axis);
            double overlap = radius - Math.abs(distance);
            if (overlap <= 1.0E-8D) {
                return null;
            }
            if (overlap < smallest) {
                smallest = overlap;
                bestAxis = distance < 0.0D ? axis.scale(-1.0D) : axis;
            }
        }
        return bestAxis == null ? null : bestAxis.scale(smallest + 1.0E-4D);
    }

    public EnumFacing getFacing() {
        return facing;
    }

    public BlockPos getBearingPos() {
        return new BlockPos(bearingX, bearingY, bearingZ);
    }

    public BlockPos getAnchor() {
        return anchor == null ? getBearingPos().offset(facing) : anchor;
    }

    private BlockPos canonicalAnchor() {
        return getBearingPos().offset(facing).toImmutable();
    }

    private void alignContraptionAnchor() {
        if (contraption != null) {
            contraption.alignAnchor(canonicalAnchor());
        }
    }

    /** The single geometric pivot used by rendering, collision and lighting. */
    public ContraptionTransform.Point getRotationPivot() {
        BlockPos anchorPos = getAnchor();
        return new ContraptionTransform.Point(
                anchorPos.getX() + 0.5D,
                anchorPos.getY() + 0.5D,
                anchorPos.getZ() + 0.5D);
    }

    public ContraptionTransform.Axis getRotationAxis() {
        return ContraptionTransform.from(facing.getAxis());
    }

    ContraptionTransform.Point worldUpInLocalCoordinates() {
        ContraptionTransform.Point up = parentContraption == null
                ? new ContraptionTransform.Point(0.0D, 1.0D, 0.0D)
                : parentContraption.worldUpInLocalCoordinates();
        return up.rotate(getRotationAxis(), -angle);
    }

    public BlockPos localPos(BearingContraption.Snapshot snapshot) {
        return contraption.localPos(snapshot, getAnchor());
    }

    @Override
    public void onUpdate() {
        if(!world.isRemote)bindParentFromUuid();
        if(!world.isRemote&&isRailwayPortalCopy()) {
            lastTickPosX=posX;lastTickPosY=posY;lastTickPosZ=posZ;
            onEntityUpdate();return;
        }
        if(!world.isRemote&&getMountedCart() instanceof com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity railway&&railway.awaitingPortalTransfer())return;
        double previousLinearX = posX, previousLinearY = posY, previousLinearZ = posZ;
        boolean hadClientLinearPose = world.isRemote && elevator && clientElevatorPoseInitialized;
        if (world.isRemote) {
            float oldOffset = verticalOffset;
            float oldX = cartX, oldY = cartY, oldZ = cartZ;
            java.util.List<AxisAlignedBB> oldBoxes = elevator && clientElevatorPoseInitialized
                    ? collisionBoxes() : java.util.List.of();
            elevator = dataManager.get(DATA_ELEVATOR);
            elevatorController = dataManager.get(DATA_ELEVATOR_CONTROLLER);
            linearDirection = dataManager.get(DATA_LINEAR_DIRECTION);
            floorElevator = dataManager.get(DATA_IS_FLOOR_ELEVATOR);
            verticalOffset = dataManager.get(DATA_VERTICAL_OFFSET);
            mountedCart = dataManager.get(DATA_CART).orNull();
            cartX = dataManager.get(DATA_CART_X);
            cartY = dataManager.get(DATA_CART_Y);
            cartZ = dataManager.get(DATA_CART_Z);
            if (elevator && clientElevatorPoseInitialized && mountedCart != null) {
                double dx = cartX - oldX, dy = cartY - oldY, dz = cartZ - oldZ;
                if (dx != 0D || dy != 0D || dz != 0D) {
                    rememberLinearMotion(dx, dy, dz);
                    collisionCacheTick = Long.MIN_VALUE;
                    syncEntityPosition();
                }
                // Move the local player after its own movement tick, as for elevators.
                pendingClientElevatorBoxes = oldBoxes;
                pendingClientElevatorX = dx; pendingClientElevatorY = dy; pendingClientElevatorZ = dz;
                pendingClientMountedMovement = true;
                pendingClientMountedAngle = angle;
                pendingClientMountedPivotX = getAnchor().getX() + .5D + oldX;
                pendingClientMountedPivotZ = getAnchor().getZ() + .5D + oldZ;
            } else if (elevator && mountedCart == null && clientElevatorPoseInitialized && oldOffset != verticalOffset) {
                float movement = verticalOffset - oldOffset;
                rememberLinearMotion(linearDirection.getXOffset() * movement,
                        linearDirection.getYOffset() * movement, linearDirection.getZOffset() * movement);
                collisionCacheTick = Long.MIN_VALUE;
                syncEntityPosition();
                if (isElevator()) {
                    pendingClientMountedMovement = false;
                    pendingClientElevatorBoxes = oldBoxes;
                    pendingClientElevatorX = linearDirection.getXOffset() * movement;
                    pendingClientElevatorY = linearDirection.getYOffset() * movement;
                    pendingClientElevatorZ = linearDirection.getZOffset() * movement;
                } else carryLinearEntities(oldBoxes, movement);
            }
            clientElevatorPoseInitialized = elevator;
        } else if (mountedCart != null) {
            updateMountedCart();
            if (isDead) return;
        }
        bindParentFromUuid();
        holdAnchor();
        super.onUpdate();
        holdAnchor();
        if (hadClientLinearPose) {
            // The synchronized pose is applied before the MovingWorld tick.
            // Retain the old position for RenderManager's partial-tick lerp.
            lastTickPosX = previousLinearX;
            lastTickPosY = previousLinearY;
            lastTickPosZ = previousLinearZ;
        }
        bindParentFromUuid();
        applyPendingClientSnapshot();
        if (parentContraptionUuid != null && parentContraption == null) {
            return;
        }
        syncBearingPose();
        alignContraptionAnchor();
        if (!world.isRemote && contraption != null
                && !contraption.contains(getAnchor())) {
            disassembleMissingAnchor();
            return;
        }
        updateClientPoseClock();
        tickVisualWorldProperties();
        tickCarriedDisplayBlocks();
        if (!world.isRemote) {
            if(!isRailwayInPortal())tickCarriedBlockProperties();
            resolveEntityCollisions();
            if(!isRailwayInPortal()){tickCarriedRandomBlocks();tickScheduledCarriedBlocks();}
        }
        if (!world.isRemote && (rotationPointMissing() || nestedBearingMissing())) {
            restoreSnapshotAndDiscard();
            return;
        }
        if (!world.isRemote && elevator && mountedCart == null && world.isBlockLoaded(elevatorController)
                && !(world.getTileEntity(elevatorController) instanceof LinearContraptionController)) {
            restoreSnapshotAndDiscard();
            return;
        }
        if (!world.isRemote && rotaryController != null && world.isBlockLoaded(rotaryController)
                && !(world.getTileEntity(rotaryController) instanceof RotaryContraptionController)) {
            restoreSnapshotAndDiscard();
            return;
        }
        if (world.isRemote) {
            spawnParticles(getHorizontalVelocity());
            if (contraption == null && hasMovingWorldSnapshot()) {
                contraption = BearingContraption.readFromNBT(getMovingWorldNBT());
                populateMobileChunk(contraption);
            }
            if (contraption == null) {
                applyPendingClientSnapshot();
            }
        } else {
            syncContraptionTileData();
            if(!isRailwayInPortal())nestedBearings.tick();
        }
        if (!world.isRemote && contraption == null) {
            missingContraptionTicks++;
            if (missingContraptionTicks >= 20) {
                setDead();
                return;
            }
        } else {
            missingContraptionTicks = 0;
        }
        if (!world.isRemote && contraption != null && contraption.isEmpty()) {
            setDead();
        }
        updateBounds();
    }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch,
                                             int increments, boolean teleport) {
        // Linear contraptions already use the server's synchronized offset;
        // MovingWorld's independent position interpolation fights that pose.
        if (elevator || dataManager.get(DATA_ELEVATOR)) return;
        super.setPositionAndRotationDirect(x, y, z, yaw, pitch, increments, teleport);
    }

    private void syncContraptionTileData() {
        if (contraption == null || getMobileChunk() == null) {
            return;
        }
        // Tile ticks and scheduled/random block updates can add or remove
        // virtual blocks without a player interaction. Commit those changes
        // through the same snapshot/connectivity path used by block use.
        if (getMobileChunk() instanceof
                com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunkServer server
                && !server.getBlockQueue().isEmpty()) {
            commitInteractionChanges();
            if (contraption == null || isDead) return;
        }
        java.util.Set<BlockPos> changedPositions = new java.util.HashSet<>();
        for (BlockPos localPos : getMobileChunk().drainDirtyTilePositions()) {
            if (persistTile(localPos)) {
                changedPositions.add(localPos);
            }
        }
        if (getMobileChunk() instanceof
                com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunkServer serverChunk) {
            for (BlockPos localPos : serverChunk.getBlockQueue()) {
                if (persistTile(localPos)) {
                    changedPositions.add(localPos);
                }
            }
            for (BlockPos localPos : serverChunk.getTileQueue()) {
                if (persistTile(localPos)) {
                    changedPositions.add(localPos);
                }
            }
            serverChunk.getBlockQueue().clear();
            serverChunk.getTileQueue().clear();
        }
        if (++contraptionTileSyncTicks >= 20) {
            contraptionTileSyncTicks = 0;
            for (java.util.Map.Entry<BlockPos, TileEntity> entry :
                    getMobileChunk().chunkTileEntityMap.entrySet()) {
                if (persistTile(entry.getKey())) {
                    changedPositions.add(entry.getKey());
                }
            }
        }
        if (!changedPositions.isEmpty()) {
            syncSavedContraption();
            sendChangedBlocksToTrackingPlayers(changedPositions);
        }
    }

    private void tickScheduledCarriedBlocks() {
        if (contraption == null || contraption.blocks().isEmpty()) {
            return;
        }
        ContraptionWorld localWorld = carriedWorld();
        if (localWorld != null) {
            localWorld.tickScheduledUpdates();
        }
    }

    private ContraptionWorld carriedWorld() {
        MobileChunk mobileChunk = getMobileChunk();
        if (isDead || contraption == null || mobileChunk == null) {
            carriedWorld = null;
            return null;
        }
        int poseKey = getLightPoseKey();
        int contentKey = getLightContentKey();
        if (carriedWorld == null || carriedWorld.movingChunk() != mobileChunk) {
            ContraptionBlockAccess access = new ContraptionBlockAccess(
                    contraption, world, this);
            carriedWorld = new ContraptionWorld(world, access, mobileChunk);
            carriedWorld.refreshAccess(access, poseKey, contentKey);
        } else if (carriedWorld.poseKey() != poseKey
                || carriedWorld.contentKey() != contentKey) {
            ContraptionBlockAccess access = new ContraptionBlockAccess(
                    contraption, world, this);
            carriedWorld.refreshAccess(access, poseKey, contentKey);
        }
        return carriedWorld;
    }

    public ContraptionWorld getContraptionLocalWorld() {
        if(!isRailwayPortalDataReady())return null;
        return carriedWorld();
    }

    public int contraptionWorldLightSignature() {
        if (world.isRemote && contraption != null
                && (lightSignatureTick == Long.MIN_VALUE
                || world.getTotalWorldTime() - lightSignatureTick >= 5L)) {
            lightSignatureTick = world.getTotalWorldTime();
            int signature = 1;
            for (BearingContraption.Snapshot snapshot : contraption.blocks().values()) {
                BlockPos local = snapshot.pos().subtract(getAnchor());
                BlockPos sample = new BlockPos(localBlockCenterToWorld(local));
                int sky = 0;
                int block = 0;
                if (world.isBlockLoaded(sample)) {
                    sky = world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY, sample);
                    block = world.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, sample);
                }
                signature = 31 * signature + local.hashCode();
                signature = 31 * signature + (sky << 20 | block << 4);
            }
            cachedWorldLightSignature = signature;
        }
        return cachedWorldLightSignature;
    }

    void refreshCarriedWorldProperties() {
        worldProperties.markDirty();
        worldProperties.tick();
    }

    int getLightPoseKey() {
        return java.util.Objects.hash(
                getAnchor(), Float.floatToIntBits(interactionAngle()), Float.floatToIntBits(verticalOffset), linearDirection);
    }

    int getLightContentKey() {
        if (contraption == null) {
            return 0;
        }
        return 31 * System.identityHashCode(contraption)
                + Long.hashCode(contraption.contentVersion());
    }

    void invalidateMovingChunkLight(int oldContent, boolean force) {
        if (world == null || contraption == null) {
            return;
        }
        MobileChunk mobileChunk = getMobileChunk();
        if (mobileChunk == null
                || (!force && oldContent == getLightContentKey())) {
            return;
        }
        mobileChunk.invalidateLightCache();
    }

    private void sendChangedBlocksToTrackingPlayers(
            java.util.Set<BlockPos> changedPositions) {
        for (BlockPos localPos : changedPositions) {
            BearingContraption.Snapshot snapshot =
                    contraption.snapshotAtLocal(localPos).orElse(null);
            int stateId = snapshot == null ? -1
                    : Block.getStateId(snapshot.state());
            NBTTagCompound tile = snapshot == null ? null
                    : contraption.tileDataAt(getAnchor().add(localPos));
            ModNetwork.CHANNEL.sendToAllAround(
                    new ContraptionBlockChangedPacket(
                            getEntityId(), localPos, stateId, tile),
                    new NetworkRegistry.TargetPoint(
                            world.provider.getDimension(),
                            posX, posY, posZ, 96.0D));
        }
    }

    /** Capture live fluid before a snapshot is saved, rotated, or restored. */
    private void persistCarriedFluidTanks() {
        if (world.isRemote || contraption == null || getMobileChunk() == null) {
            return;
        }
        MobileChunk chunk = getMobileChunk();
        for (java.util.Map.Entry<BlockPos, TileEntity> entry : chunk.chunkTileEntityMap.entrySet()) {
            TileEntity tile = entry.getValue();
            if (!(tile instanceof FluidTankTileEntity) || tile.isInvalid()) {
                continue;
            }
            BlockPos local = entry.getKey();
            contraption.persistBlockData(local, chunk.getBlockState(local),
                    tile.writeToNBT(new NBTTagCompound()));
        }
    }

    private boolean persistTile(BlockPos localPos) {
        MobileChunk mobileChunk = getMobileChunk();
        if (mobileChunk == null) {
            return false;
        }
        IBlockState state = mobileChunk.getBlockState(localPos);
        TileEntity tile = mobileChunk.getTileEntity(localPos);
        NBTTagCompound data = tile == null
                ? null : tile.writeToNBT(new NBTTagCompound());
        boolean blockDataChanged = contraption.persistBlockData(
                localPos, state, data);
        if (blockDataChanged) {
            refreshCarriedWorldPropertyLights();
        }
        if (tile != null) {
            bindContraptionRenderContext(tile, state, localPos);
        }
        return blockDataChanged;
    }

    /**
     * A container can keep a TileEntity open across many world ticks. Persist
     * and mirror that one carried block immediately when its slots or fields
     * change instead of waiting for the periodic structure scan.
     */
    public void persistAndSyncInteractionBlock(BlockPos localPos) {
        if (world.isRemote || contraption == null || localPos == null) {
            return;
        }
        MobileChunk mobileChunk = getMobileChunk();
        if (mobileChunk == null) {
            return;
        }
        BlockPos local = localPos.toImmutable();
        IBlockState state = mobileChunk.getBlockState(local);
        TileEntity tile = mobileChunk.getTileEntity(local);
        NBTTagCompound tileData = tile == null ? null
                : tile.writeToNBT(new NBTTagCompound());
        if (contraption.persistBlockData(local, state, tileData)) {
            refreshCarriedWorldPropertyLights();
        }
        if (tile != null) {
            bindContraptionRenderContext(tile, state, local);
        }
        syncSavedContraption();
        syncInteractionBlock(local);
        commitRailwayPortalInteraction();
    }

    private void refreshCarriedWorldPropertyLights() {
        refreshCarriedWorldProperties();
    }

    private void syncSavedContraption() {
        MechanicalBearingTileEntity bearing = bearingTile();
        if (bearing != null && contraption != null) {
            bearing.syncSavedContraption(contraption);
        }
    }

    private void updateClientPoseClock() {
        if (!world.isRemote || !clientPoseInitialized || clientTargetStalled) {
            return;
        }
        float uncorrected = clientAdvance(angle, speed);
        float targetAfterTick = clientAdvance(serverTargetAngle, speed);
        float error = (float) ContraptionTransform.shortestDelta(
                uncorrected, targetAfterTick);
        float next = uncorrected;
        if (Math.abs(error) <= 0.01F) {
            next = serverTargetAngle;
        } else {
            float correction = Math.max(-CLIENT_MAX_CORRECTION_PER_TICK,
                    Math.min(CLIENT_MAX_CORRECTION_PER_TICK,
                            error * CLIENT_CORRECTION_RATE));
            next = (float) ContraptionTransform.normalize(
                    next + correction);
        }
        previousAngle = angle;
        angle = normalizeAngle(next);
        updateBounds();
    }

    private static float clientAdvance(float currentAngle, float currentSpeed) {
        return normalizeAngle((float) ContraptionTransform.advance(
                currentAngle, currentSpeed));
    }

    private static float normalizeAngle(float value) {
        return (float) ContraptionTransform.normalize(value);
    }

    public void applyPendingClientSnapshot() {
        if (!world.isRemote) {
            return;
        }
        NBTTagCompound pending = PENDING_CLIENT_SNAPSHOTS.get(getEntityId());
        if (pending != null) {
            acceptClientSnapshot(pending);
        }
    }

    static void clearPendingClientSnapshot(int entityId) {
        PENDING_CLIENT_SNAPSHOTS.remove(entityId);
    }

    static void clearPendingClientSnapshots() {
        PENDING_CLIENT_SNAPSHOTS.clear();
    }

    private NBTTagCompound getMovingWorldNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        writeMovingWorldNBT(tag);
        return tag.getCompoundTag("BearingContraption");
    }

    private boolean hasMovingWorldSnapshot() {
        NBTTagCompound tag = new NBTTagCompound();
        writeMovingWorldNBT(tag);
        return tag.hasKey("BearingContraption");
    }

    private MechanicalBearingTileEntity bearingTile() {
        if (elevator) return null;
        if (isNested()) {
            if (parentContraption == null || parentLocalBearingPos == null
                    || parentContraption.getMovingWorldChunk() == null) {
                return null;
            }
            TileEntity tile = parentContraption.getMovingWorldChunk()
                    .getTileEntity(parentLocalBearingPos);
            return tile instanceof MechanicalBearingTileEntity bearing
                    ? bearing : null;
        }
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(getBearingPos());
        return tile instanceof MechanicalBearingTileEntity
                ? (MechanicalBearingTileEntity) tile : null;
    }

    private boolean rotationPointMissing() {
        if (restoringSnapshot || isNested()
                || contraption == null || !ownedByBearing
                || world == null || !world.isBlockLoaded(getBearingPos())) {
            return false;
        }
        IBlockState state = world.getBlockState(getBearingPos());
        return !(state.getBlock() instanceof MechanicalBearingBlock)
                || !(world.getTileEntity(getBearingPos())
                instanceof MechanicalBearingTileEntity);
    }

    private boolean nestedBearingMissing() {
        if (restoringSnapshot || !isNested() || contraption == null
                || parentContraption == null || parentLocalBearingPos == null
                || parentContraption.getMovingWorldChunk() == null) {
            return false;
        }
        TileEntity tile = parentContraption.getMovingWorldChunk()
                .getTileEntity(parentLocalBearingPos);
        return !(tile instanceof MechanicalBearingTileEntity);
    }

    private void syncBearingPose() {
        if (world == null || isNested() || elevator) {
            return;
        }
        net.minecraft.block.state.IBlockState state = world.getBlockState(getBearingPos());
        if (state.getBlock() instanceof MechanicalBearingBlock
                && state.getPropertyKeys().contains(MechanicalBearingBlock.FACING)) {
            EnumFacing current = state.getValue(MechanicalBearingBlock.FACING);
            if (current != facing) {
                facing = current;
                anchor = canonicalAnchor();
                if (!world.isRemote) {
                    dataManager.set(DATA_FACING, facing);
                }
                updateBounds();
            }
        }
        if (anchor == null || !anchor.equals(canonicalAnchor())) {
            anchor = canonicalAnchor();
        }
        alignContraptionAnchor();
    }

    @Override
    public void entityInit() {
        // EntityMovingWorld initializes its data manager and MovingWorld storage.
        super.entityInit();
        dataManager.register(DATA_BEARING_POS, BlockPos.ORIGIN);
        dataManager.register(DATA_FACING, EnumFacing.NORTH);
        dataManager.register(DATA_ANGLE, 0.0F);
        dataManager.register(DATA_SPEED, 0.0F);
        dataManager.register(DATA_STALLED, false);
        dataManager.register(DATA_ELEVATOR, false);
        dataManager.register(DATA_VERTICAL_OFFSET, 0F);
        dataManager.register(DATA_ELEVATOR_CONTROLLER, BlockPos.ORIGIN);
        dataManager.register(DATA_LINEAR_DIRECTION, EnumFacing.UP);
        dataManager.register(DATA_IS_FLOOR_ELEVATOR, false);
        dataManager.register(DATA_CART, com.google.common.base.Optional.absent());
        dataManager.register(DATA_CART_X, 0F);
        dataManager.register(DATA_CART_Y, 0F);
        dataManager.register(DATA_CART_Z, 0F);
        dataManager.register(DATA_PORTAL,false);dataManager.register(DATA_PORTAL_COPY,false);
        dataManager.register(DATA_PORTAL_AXIS,EnumFacing.SOUTH);dataManager.register(DATA_PORTAL_SEAM,0);
        dataManager.register(DATA_PARENT, com.google.common.base.Optional.absent());
    }

    @Override
    public void setVelocity(double x, double y, double z) {
        super.setVelocity(0.0D, 0.0D, 0.0D);
    }

    @Override
    public void applyEntityCollision(Entity entity) {
        holdAnchor();
    }

    @Override
    public boolean isPushedByWater() {
        return false;
    }

    @Override
    public void initMovingWorld() {
    }

    @Override
    public void initMovingWorldClient() {
    }

    @Override
    public void initMovingWorldCommon() {
    }

    @Override
    public MovingWorldHandlerCommon getHandler() {
        return handler;
    }

    @Override
    public MovingWorldCapabilities getCapabilities() {
        return capabilities;
    }

    @Override
    public void setCapabilities(MovingWorldCapabilities capabilities) {
        // A bearing always uses the non-ridable capabilities created above.
    }

    @Override
    public MovingWorldAssemblyInteractor getAssemblyInteractor() {
        return assemblyInteractor;
    }

    @Override
    public void setAssemblyInteractor(MovingWorldAssemblyInteractor interactor) {
        // The assembly policy is fixed for bearing snapshots.
    }

    @Override
    public void handleControl(double horizontalVelocity) {
        // Contraptions receive their motion from the bearing, not a rider.
    }

    @Override
    public boolean isBraking() {
        return false;
    }

    @Override
    public void writeMovingWorldNBT(NBTTagCompound tag) {
        if (contraption != null) {
            persistNestedForSave();
            tag.setTag("BearingContraption", contraption.writeToNBT(new NBTTagCompound()));
        }
    }

    @Override
    public void readMovingWorldNBT(NBTTagCompound tag) {
        // EntityMovingWorld invokes this from its constructor, before the
        // subclass fields and ACTIVE bookkeeping exist. The complete bearing
        // payload is loaded after super.readEntityFromNBT below.
    }

    @Override
    public void writeMovingWorldSpawnData(ByteBuf data) {
        // MovingWorld already owns the spawn payload boundary. Extra data
        // written here is consumed by its own readCompressed call and causes
        // readerIndex overruns before the entity reaches the client.
    }

    @Override
    public void readMovingWorldSpawnData(ByteBuf data) {
        restorePoseFromDataManager();
        recoverClientContraptionFromSpawnChunk();
        updateBounds();
    }

    public NBTTagCompound snapshotNbt() {
        persistNestedForSave();
        NBTTagCompound tag = new NBTTagCompound();
        writeActorControls(tag);
        writeParentIdentity(tag);
        tag.setInteger("BearingX", bearingX);
        tag.setInteger("BearingY", bearingY);
        tag.setInteger("BearingZ", bearingZ);
        tag.setByte("Facing", (byte) facing.getIndex());
        tag.setInteger("AnchorX", getAnchor().getX());
        tag.setInteger("AnchorY", getAnchor().getY());
        tag.setInteger("AnchorZ", getAnchor().getZ());
        tag.setFloat("Angle", angle);
        tag.setFloat("PreviousAngle", previousAngle);
        tag.setFloat("Speed", speed);
        tag.setBoolean("Elevator", elevator);
        tag.setLong("ElevatorController", elevatorController.toLong());
        tag.setFloat("VerticalOffset", verticalOffset);
        tag.setByte("LinearDirection", (byte) linearDirection.getIndex());
        tag.setBoolean("FloorElevator", floorElevator);
        if (mountedCart != null) tag.setUniqueId("MountedCart", mountedCart);
        tag.setFloat("CartX", cartX); tag.setFloat("CartY", cartY); tag.setFloat("CartZ", cartZ);
        writeRailwayPortalSlice(tag);
        if (contraption != null) {
            tag.setTag("Contraption", contraption.writeToNBT(new NBTTagCompound()));
        }
        return tag;
    }

    private void writeParentIdentity(NBTTagCompound tag) {
        if (parentContraptionUuid != null) {
            tag.setUniqueId("ParentContraption", parentContraptionUuid);
        }
        if (parentLocalBearingPos != null) {
            tag.setInteger("ParentLocalX", parentLocalBearingPos.getX());
            tag.setInteger("ParentLocalY", parentLocalBearingPos.getY());
            tag.setInteger("ParentLocalZ", parentLocalBearingPos.getZ());
        }
        if (parentPose != null) {
            NBTTagCompound poseTag = new NBTTagCompound();
            poseTag.setInteger("LocalX", parentPose.bearingLocal().getX());
            poseTag.setInteger("LocalY", parentPose.bearingLocal().getY());
            poseTag.setInteger("LocalZ", parentPose.bearingLocal().getZ());
            poseTag.setByte("Facing", (byte) parentPose.facing().getIndex());
            poseTag.setFloat("Angle", parentPose.angle());
            poseTag.setFloat("Speed", parentPose.speed());
            poseTag.setBoolean("Stalled", parentPose.stalled());
            tag.setTag("ParentPose", poseTag);
        }
    }

    public void sendSnapshotToTrackingPlayers() {
        if (world.isRemote || contraption == null) {
            return;
        }
        ModNetwork.CHANNEL.sendToAllAround(
                new ContraptionSnapshotPacket(getEntityId(), snapshotNbt()),
                new NetworkRegistry.TargetPoint(world.provider.getDimension(),
                        posX, posY, posZ, 96.0D));
    }

    public void acceptClientSnapshot(NBTTagCompound tag) {
        if (!world.isRemote || tag == null || !tag.hasKey("Contraption")) {
            return;
        }
        syncParentSnapshot(tag);
        bindParentFromUuid();
        applyClientSnapshotData(tag);
        PENDING_CLIENT_SNAPSHOTS.remove(getEntityId());
    }

    private void recoverClientContraptionFromSpawnChunk() {
        if (!world.isRemote || hasContraptionData()) {
            return;
        }
        MobileChunk mobileChunk = getMobileChunk();
        if (mobileChunk == null || mobileChunk.getBlockCount() == 0) {
            return;
        }
        clearClientTileCache(mobileChunk);
        BearingContraption recovered = BearingContraption.fromLocalChunk(
                mobileChunk, getAnchor());
        if (!recovered.blocks().isEmpty()) {
            contraption = recovered;
            populateTileEntities(contraption);
            if (mobileChunk instanceof com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunkClient clientChunk) {
                clientChunk.getRenderer().markDirty();
            }
            updateBounds();
        }
    }

    private void applyClientSnapshotData(NBTTagCompound tag) {
        readRailwayPortalSlice(tag);
        readActorControls(tag);
        if (tag.hasKey("Elevator")) {
            elevator = tag.getBoolean("Elevator");
            elevatorController = BlockPos.fromLong(tag.getLong("ElevatorController"));
            verticalOffset = tag.getFloat("VerticalOffset");
            linearDirection = tag.hasKey("LinearDirection") ? EnumFacing.byIndex(tag.getByte("LinearDirection")) : EnumFacing.UP;
            floorElevator = tag.hasKey("FloorElevator") ? tag.getBoolean("FloorElevator") : elevator;
            mountedCart = tag.hasUniqueId("MountedCart") ? tag.getUniqueId("MountedCart") : null;
            cartX = tag.getFloat("CartX"); cartY = tag.getFloat("CartY"); cartZ = tag.getFloat("CartZ");
        }
        bearingX = tag.getInteger("BearingX");
        bearingY = tag.getInteger("BearingY");
        bearingZ = tag.getInteger("BearingZ");
        facing = EnumFacing.byIndex(tag.getByte("Facing"));
        if (tag.hasKey("Angle")) {
            angle = tag.getFloat("Angle");
            previousAngle = tag.getFloat("PreviousAngle");
            speed = tag.getFloat("Speed");
            serverTargetAngle = angle;
        }
        anchor = canonicalAnchor();
        contraption = BearingContraption.readFromNBT(tag.getCompoundTag("Contraption"));
        if (!tag.hasKey("ActorControlInitialized")) readActorControlsFromContraption();
        alignContraptionAnchor();
        clearClientTileCache(getMobileChunk());
        clearMobileChunk();
        populateMobileChunk(contraption);
        if (getMobileChunk() instanceof com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunkClient clientChunk) {
            clientChunk.getRenderer().markDirty();
        }
        updateBounds();
    }

    private void syncPoseToDataManager() {
        if (world.isRemote) {
            return;
        }
        dataManager.set(DATA_BEARING_POS, getBearingPos());
        dataManager.set(DATA_FACING, facing);
        dataManager.set(DATA_ANGLE, angle);
        dataManager.set(DATA_SPEED, speed);
        dataManager.set(DATA_ELEVATOR, elevator);
        dataManager.set(DATA_VERTICAL_OFFSET, verticalOffset);
        dataManager.set(DATA_ELEVATOR_CONTROLLER, elevatorController);
        dataManager.set(DATA_LINEAR_DIRECTION, linearDirection);
        dataManager.set(DATA_IS_FLOOR_ELEVATOR, floorElevator);
        dataManager.set(DATA_CART, com.google.common.base.Optional.fromNullable(mountedCart));
        dataManager.set(DATA_CART_X, cartX);
        dataManager.set(DATA_CART_Y, cartY);
        dataManager.set(DATA_CART_Z, cartZ);
        dataManager.set(DATA_PARENT,
                com.google.common.base.Optional.fromNullable(parentContraptionUuid));
    }

    public void syncParentSnapshot(NBTTagCompound tag) {
        if (tag == null) {
            return;
        }
        java.util.UUID oldParent = parentContraptionUuid;
        parentContraptionUuid = tag.hasUniqueId("ParentContraption")
                ? tag.getUniqueId("ParentContraption") : null;
        if (!java.util.Objects.equals(oldParent, parentContraptionUuid)) {
            parentContraption = null;
        }
        if (tag.hasKey("ParentLocalX")) {
            parentLocalBearingPos = new BlockPos(
                    tag.getInteger("ParentLocalX"),
                    tag.getInteger("ParentLocalY"),
                    tag.getInteger("ParentLocalZ"));
        } else {
            parentLocalBearingPos = null;
        }
        if (tag.hasKey("ParentPose")) {
            NBTTagCompound poseTag = tag.getCompoundTag("ParentPose");
            parentPose = new NestedContraptionPose(
                    new BlockPos(poseTag.getInteger("LocalX"),
                            poseTag.getInteger("LocalY"),
                            poseTag.getInteger("LocalZ")),
                    EnumFacing.byIndex(poseTag.getByte("Facing")),
                    poseTag.getFloat("Angle"), poseTag.getFloat("Speed"),
                    poseTag.getBoolean("Stalled"));
        }
    }

    private void restorePoseFromDataManager() {
        if (!world.isRemote) {
            return;
        }
        BlockPos syncedBearingPos = dataManager.get(DATA_BEARING_POS);
        EnumFacing syncedFacing = dataManager.get(DATA_FACING);
        bearingX = syncedBearingPos.getX();
        bearingY = syncedBearingPos.getY();
        bearingZ = syncedBearingPos.getZ();
        facing = syncedFacing;
        elevator = dataManager.get(DATA_ELEVATOR);
        elevatorController = dataManager.get(DATA_ELEVATOR_CONTROLLER);
        verticalOffset = dataManager.get(DATA_VERTICAL_OFFSET);
        linearDirection = dataManager.get(DATA_LINEAR_DIRECTION);
        floorElevator = dataManager.get(DATA_IS_FLOOR_ELEVATOR);
        mountedCart = dataManager.get(DATA_CART).orNull();
        cartX = dataManager.get(DATA_CART_X);
        cartY = dataManager.get(DATA_CART_Y);
        cartZ = dataManager.get(DATA_CART_Z);
        parentContraptionUuid = dataManager.get(DATA_PARENT).orNull();
        parentLocalBearingPos = parentContraptionUuid == null
                ? null : syncedBearingPos.toImmutable();
        bindParentFromUuid();
        anchor = getBearingPos().offset(facing);
        angle = dataManager.get(DATA_ANGLE) % 360.0F;
        previousAngle = angle;
        speed = dataManager.get(DATA_SPEED);
        syncEntityPosition();
    }

    private static void clearClientTileCache(MobileChunk chunk) {
        if (chunk instanceof com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunkClient client) {
            client.normalTESRS.clear();
            client.fastTESRS.clear();
        }
    }

    @Override
    public float getXRenderScale() {
        return 1.0F;
    }

    @Override
    public float getYRenderScale() {
        return 1.0F;
    }

    @Override
    public float getZRenderScale() {
        return 1.0F;
    }

    private void populateMobileChunk(BearingContraption source) {
        MobileChunk mobileChunk = getMobileChunk();
        if (mobileChunk == null || source == null) {
            return;
        }
        BlockPos sourceAnchor = source.anchor();
        for (BearingContraption.Snapshot snapshot : source.blocks().values()) {
            BlockPos local = snapshot.pos().subtract(sourceAnchor);
            if (mobileChunk.hasPosition(local)) {
                continue;
            }
            mobileChunk.addBlockWithState(local, snapshot.state());
            NBTTagCompound tileData = source.tileDataAt(snapshot.pos());
            if (tileData == null) {
                continue;
            }
            relocateTileTag(tileData, snapshot.state(), local);
            TileEntity tile = TileEntity.create(mobileChunk.getFakeWorld(), tileData);
            if (tile != null) {
                tile.setPos(local);
                tile.setWorld(mobileChunk.getFakeWorld());
                bindContraptionRenderContext(tile, snapshot.state(), local);
                tile.validate();
                mobileChunk.setTileEntity(local, tile);
            }
        }
        // MovingWorld stores collision boxes in a HashBiMap keyed both ways.
        // Identical block boxes (for example two full cubes) make that put()
        // throw IllegalArgumentException. Create computes fully rotated
        // per-block collision boxes itself, so the pathfinding cache is
        // intentionally left empty here.
    }

    private void populateTileEntities(BearingContraption source) {
        MobileChunk mobileChunk = getMobileChunk();
        if (mobileChunk == null || source == null) {
            return;
        }
        BlockPos sourceAnchor = source.anchor();
        for (BearingContraption.Snapshot snapshot : source.blocks().values()) {
            NBTTagCompound tileData = source.tileDataAt(snapshot.pos());
            if (tileData == null) {
                continue;
            }
            BlockPos local = snapshot.pos().subtract(sourceAnchor);
            relocateTileTag(tileData, snapshot.state(), local);
            TileEntity tile = TileEntity.create(mobileChunk.getFakeWorld(), tileData);
            if (tile != null) {
                tile.setWorld(mobileChunk.getFakeWorld());
                tile.setPos(local);
                bindContraptionRenderContext(tile, snapshot.state(), local);
                tile.validate();
                mobileChunk.setTileEntity(local, tile);
            }
        }
    }

    private static void relocateTileTag(NBTTagCompound tag, IBlockState state, BlockPos position) {
        if (state.getBlock() instanceof FluidTankBlock) {
            FluidTankTileEntity.relocateTag(tag, position);
        } else {
            tag.setInteger("x", position.getX());
            tag.setInteger("y", position.getY());
            tag.setInteger("z", position.getZ());
        }
    }

    private static void bindContraptionRenderContext(
            TileEntity tile, IBlockState state, BlockPos localPos) {
        if (tile instanceof com.cogwork.cogworkbridge.kinetic.KineticTileEntity kinetic) {
            kinetic.setContraptionRenderContext(state, localPos);
        }
    }

    public void clearMobileChunk() {
        worldProperties.clear();
        if (!world.isRemote) {
            ContraptionGuiProxy.closeForStructureTransition(this);
        }
        if (carriedWorld != null) {
            carriedWorld.clearScheduledUpdates();
            carriedWorld = null;
        }
        MobileChunk mobileChunk = getMobileChunk();
        if (mobileChunk == null) {
            return;
        }
        mobileChunk.clearAllBlocks();
    }

    public MobileChunk getMovingWorldChunk() {
        return getMobileChunk();
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        writeRailwayPortalSlice(compound);
        persistNestedForSave();
        super.writeEntityToNBT(compound);
        writeActorControls(compound);
        compound.setInteger("BearingX", bearingX);
        compound.setInteger("BearingY", bearingY);
        compound.setInteger("BearingZ", bearingZ);
        compound.setByte("Facing", (byte) facing.getIndex());
        compound.setFloat("Angle", angle);
        compound.setFloat("PreviousAngle", angle);
        compound.setFloat("Speed", speed);
        compound.setBoolean("Elevator", elevator);
        if (rotaryController != null) compound.setLong("RotaryController", rotaryController.toLong());
        if (mountedCart != null) compound.setUniqueId("MountedCart", mountedCart);
        if (secondaryMountedCart != null) {
            compound.setUniqueId("SecondaryMountedCart", secondaryMountedCart);
            compound.setDouble("CartCouplingLength", cartCouplingLength);
        }
        if (Float.isFinite(cartInitialYaw)) compound.setFloat("CartInitialYaw", cartInitialYaw);
        compound.setInteger("CartRotationMode", cartRotationMode);
        compound.setFloat("CartX", cartX); compound.setFloat("CartY", cartY); compound.setFloat("CartZ", cartZ);
        compound.setLong("ElevatorController", elevatorController.toLong());
        compound.setFloat("VerticalOffset", verticalOffset);
        compound.setByte("LinearDirection", (byte) linearDirection.getIndex());
        compound.setBoolean("FloorElevator", floorElevator);
        compound.setInteger("AnchorX", getAnchor().getX());
        compound.setInteger("AnchorY", getAnchor().getY());
        compound.setInteger("AnchorZ", getAnchor().getZ());
        if (contraption != null) {
            compound.setTag("Contraption", contraption.writeToNBT(new NBTTagCompound()));
        }
        if (parentContraptionUuid != null) {
            compound.setUniqueId("ParentContraption", parentContraptionUuid);
        }
        if (parentLocalBearingPos != null) {
            compound.setInteger("ParentLocalX", parentLocalBearingPos.getX());
            compound.setInteger("ParentLocalY", parentLocalBearingPos.getY());
            compound.setInteger("ParentLocalZ", parentLocalBearingPos.getZ());
        }
        if (parentPose != null) {
            NBTTagCompound poseTag = new NBTTagCompound();
            poseTag.setInteger("LocalX", parentPose.bearingLocal().getX());
            poseTag.setInteger("LocalY", parentPose.bearingLocal().getY());
            poseTag.setInteger("LocalZ", parentPose.bearingLocal().getZ());
            poseTag.setByte("Facing", (byte) parentPose.facing().getIndex());
            poseTag.setFloat("Angle", parentPose.angle());
            poseTag.setFloat("Speed", parentPose.speed());
            poseTag.setBoolean("Stalled", parentPose.stalled());
            compound.setTag("ParentPose", poseTag);
        }
        nestedBearings.writeToNBT(compound);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        readActorControls(compound);
        bearingX = compound.getInteger("BearingX");
        bearingY = compound.getInteger("BearingY");
        bearingZ = compound.getInteger("BearingZ");
        facing = EnumFacing.byIndex(compound.getByte("Facing"));
        angle = compound.getFloat("Angle");
        previousAngle = compound.hasKey("PreviousAngle")
                ? compound.getFloat("PreviousAngle") : angle;
        speed = compound.getFloat("Speed");
        elevator = compound.getBoolean("Elevator");
        rotaryController = compound.hasKey("RotaryController") ? BlockPos.fromLong(compound.getLong("RotaryController")) : null;
        mountedCart = compound.hasUniqueId("MountedCart") ? compound.getUniqueId("MountedCart") : null;
        secondaryMountedCart = compound.hasUniqueId("SecondaryMountedCart") ? compound.getUniqueId("SecondaryMountedCart") : null;
        cartCouplingLength = Math.max(0D, compound.getDouble("CartCouplingLength"));
        cartInitialYaw = compound.hasKey("CartInitialYaw") ? compound.getFloat("CartInitialYaw") : Float.NaN;
        cartRotationMode = Math.floorMod(compound.getInteger("CartRotationMode"), 3);
        cartX = compound.getFloat("CartX"); cartY = compound.getFloat("CartY"); cartZ = compound.getFloat("CartZ");
        elevatorController = BlockPos.fromLong(compound.getLong("ElevatorController"));
        verticalOffset = compound.getFloat("VerticalOffset");
        linearDirection = compound.hasKey("LinearDirection") ? EnumFacing.byIndex(compound.getByte("LinearDirection")) : EnumFacing.UP;
        floorElevator = compound.hasKey("FloorElevator") ? compound.getBoolean("FloorElevator") : elevator;
        if (compound.hasKey("AnchorX")) {
            anchor = new BlockPos(compound.getInteger("AnchorX"),
                    compound.getInteger("AnchorY"), compound.getInteger("AnchorZ"));
        }
        if (compound.hasKey("Contraption")) {
            contraption = readContraption(compound.getCompoundTag("Contraption"));
            if (!compound.hasKey("ActorControlInitialized")) readActorControlsFromContraption();
        }
        anchor = canonicalAnchor();
        alignContraptionAnchor();
        parentContraptionUuid = compound.hasUniqueId("ParentContraption")
                ? compound.getUniqueId("ParentContraption") : null;
        if (compound.hasKey("ParentLocalX")) {
            parentLocalBearingPos = new BlockPos(
                    compound.getInteger("ParentLocalX"),
                    compound.getInteger("ParentLocalY"),
                    compound.getInteger("ParentLocalZ"));
        }
        if (compound.hasKey("ParentPose")) {
            NBTTagCompound poseTag = compound.getCompoundTag("ParentPose");
            BlockPos poseLocal = new BlockPos(
                    poseTag.getInteger("LocalX"),
                    poseTag.getInteger("LocalY"),
                    poseTag.getInteger("LocalZ"));
            parentPose = new NestedContraptionPose(
                    poseLocal,
                    EnumFacing.byIndex(poseTag.getByte("Facing")),
                    poseTag.getFloat("Angle"), poseTag.getFloat("Speed"),
                    poseTag.getBoolean("Stalled"));
        }
        nestedBearings.readFromNBT(compound);
        bindParentFromUuid();
        syncPoseToDataManager();
        if (parentContraptionUuid == null) {
            registerActive();
        }
        populateMobileChunk(contraption);
        readRailwayPortalSlice(compound);
        updateBounds();
    }

    private static BearingContraption readContraption(NBTTagCompound compound) {
        // Snapshot state is fully rebuilt on the server; the spawn payload only
        // needs the display data. The client keeps the serialized block list for
        // the renderer, while the bearing tile remains the authority.
        return BearingContraption.readFromNBT(compound);
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 0;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox() {
        return null;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        updateBounds();
        return getEntityBoundingBox().grow(2.0D);
    }

    private void updateBounds() {
        if (contraption == null || contraption.blocks().isEmpty()) {
            return;
        }
        AxisAlignedBB union = null;
        for (AxisAlignedBB box : collisionBoxes()) {
            union = union == null ? box : union.union(box);
        }
        if (union == null) {
            setEntityBoundingBox(new AxisAlignedBB(
                    posX - 0.5D, posY - 0.5D, posZ - 0.5D,
                    posX + 0.5D, posY + 0.5D, posZ + 0.5D));
        } else {
            setEntityBoundingBox(union.grow(0.05D));
        }
    }

    private void syncEntityPosition() {
        if (parentContraption != null) {
            ContraptionTransform.Point local = new ContraptionTransform.Point(
                    getAnchor().getX(), getAnchor().getY(), getAnchor().getZ());
            ContraptionTransform.Point world = localToRootRaw(local, angle);
            setPosition(world.x(), world.y(), world.z());
            return;
        }
        if (parentContraptionUuid != null) {
            return;
        }
        double targetX = getAnchor().getX() + translationX();
        double targetY = getAnchor().getY() + translationY();
        double targetZ = getAnchor().getZ() + translationZ();
        if (Math.abs(posX - targetX) > 1.0E-7D
                || Math.abs(posY - targetY) > 1.0E-7D
                || Math.abs(posZ - targetZ) > 1.0E-7D) {
            setPosition(targetX, targetY, targetZ);
        }
    }

    private void holdAnchor() {
        motionX = 0.0D;
        motionY = 0.0D;
        motionZ = 0.0D;
        if (anchor != null) {
            syncEntityPosition();
        }
    }

    private void updateMountedCart() {
        if (world == null || mountedCart == null || isDead) return;
        if (loadedMountedCart != null && loadedMountedCart.isDead) {
            restoreSnapshotAndDiscard();
            return;
        }
        if (loadedSecondaryCart != null && loadedSecondaryCart.isDead) {
            restoreSnapshotAndDiscard();
            return;
        }
        net.minecraft.entity.item.EntityMinecart minecart = null;
        for (Entity candidate : world.loadedEntityList) {
            if (mountedCart.equals(candidate.getUniqueID())
                    && candidate instanceof net.minecraft.entity.item.EntityMinecart found && !found.isDead) {
                minecart = found;
                break;
            }
        }
        if (minecart == null) return; // The minecart's chunk may be temporarily unloaded.
        loadedMountedCart = minecart;
        net.minecraft.entity.item.EntityMinecart secondary = findMountedCart(secondaryMountedCart);
        if (secondaryMountedCart != null && secondary == null) {
            minecart.motionX = minecart.motionY = minecart.motionZ = 0D;
            setStalled(true);
            return;
        }
        if (secondary != null) {
            loadedSecondaryCart = secondary;
            if (acceptedSecondaryCartPosition == null) acceptedSecondaryCartPosition = secondary.getPositionVector();
            com.cogwork.cogworkbridge.content.contraptions.mounted.MountedCartCoupling.tick(
                    minecart, secondary, cartCouplingLength);
        }
        float nextX = (float) (minecart.posX - (getAnchor().getX() + .5D));
        float nextY = (float) (minecart.posY - (getAnchor().getY() - 1D));
        float nextZ = (float) (minecart.posZ - (getAnchor().getZ() + .5D));
        double dx = nextX - cartX, dy = nextY - cartY, dz = nextZ - cartZ;
        if (fluidInterfaceHoldTicks > 0) {
            fluidInterfaceHoldTicks--;
            stopMountedCart(minecart);
            if (secondary != null) stopSecondaryMountedCart(secondary, true);
            rememberLinearMotion(0, 0, 0);
            setStalled(true);
            return;
        }
        double vx = minecart.motionX, vz = minecart.motionZ;
        float nextAngle = angle;
        cartTurning = false;
        Vec3d railwayHeading = minecart instanceof com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity railway
                ? railway.heading() : null;
        if (secondary != null || railwayHeading != null || cartRotationMode != 2 && vx * vx + vz * vz > 1.0E-8D) {
            float heading = secondary != null ? mountedYaw(secondary.posX - minecart.posX, secondary.posZ - minecart.posZ)
                    : railwayHeading != null ? mountedYaw(railwayHeading.x,railwayHeading.z) : mountedYaw(vx, vz);
            if (!Float.isFinite(cartInitialYaw)) cartInitialYaw = heading + angle;
            float target = cartInitialYaw - heading;
            float difference = net.minecraft.util.math.MathHelper.wrapDegrees(target - angle);
            float step = net.minecraft.util.math.MathHelper.clamp(difference, -12F, 12F);
            nextAngle = angle + step;
            cartTurning = Math.abs(difference) > 1F;
        }
        float angularDelta = net.minecraft.util.math.MathHelper.wrapDegrees(nextAngle - angle);
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) < 1.0E-7D && Math.abs(angularDelta) < 1.0E-5F) {
            if (secondary != null) acceptedSecondaryCartPosition = secondary.getPositionVector();
            if (speed != 0F) { setSpeed(0F); sendPoseToTrackingPlayers(); }
            previousAngle = angle;
            return;
        }
        java.util.List<AxisAlignedBB> oldBoxes = collisionBoxes();
        cartX = nextX; cartY = nextY; cartZ = nextZ;
        if(minecart instanceof com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity railway&&!isRailwayPortalCopy())
            com.cogwork.cogworkbridge.content.trains.track.TrainDimensionTransfer.refreshSourceCutoff(railway,this);
        Vec3d translation = new Vec3d(dx, dy, dz);
        boolean actorBlocking = !isRailwayInPortal()&&tickLinearActors(translation, translation, angle, nextAngle);
        if (actorBlocking || collidesAt(nextAngle, movingCollisionShapes(), new java.util.HashMap<>())) {
            cartX -= dx; cartY -= dy; cartZ -= dz;
            if(minecart instanceof com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity railway&&!isRailwayPortalCopy())
                com.cogwork.cogworkbridge.content.trains.track.TrainDimensionTransfer.refreshSourceCutoff(railway,this);
            setStalled(true);
            if (actorBlocking) {
                // Leave the attempted position available for the next breaker tick.
                minecart.motionX = minecart.motionY = minecart.motionZ = 0D;
                minecart.velocityChanged = true;
            } else stopMountedCart(minecart);
            if (secondary != null) stopSecondaryMountedCart(secondary, !actorBlocking);
            rememberLinearMotion(0, 0, 0);
            return;
        }
        setStalled(false);
        if (secondary != null) acceptedSecondaryCartPosition = secondary.getPositionVector();
        rememberLinearMotion(dx, dy, dz);
        setSpeed(angularDelta / .3F);
        setAngle(nextAngle);
        syncEntityPosition();
        updateBounds();
        carryTransformedEntities(oldBoxes, dx, dy, dz, angularDelta,
                getAnchor().getX() + .5D + cartX - dx, getAnchor().getZ() + .5D + cartZ - dz);
        syncPoseToDataManager();
        sendPoseToTrackingPlayers();
    }

    private void stopMountedCart(net.minecraft.entity.item.EntityMinecart minecart) {
        minecart.setPosition(getAnchor().getX() + .5D + cartX,
                getAnchor().getY() - 1D + cartY, getAnchor().getZ() + .5D + cartZ);
        minecart.motionX = minecart.motionY = minecart.motionZ = 0D;
        minecart.velocityChanged = true;
    }

    private void stopSecondaryMountedCart(net.minecraft.entity.item.EntityMinecart minecart, boolean restorePosition) {
        if (restorePosition && acceptedSecondaryCartPosition != null)
            minecart.setPosition(acceptedSecondaryCartPosition.x, acceptedSecondaryCartPosition.y, acceptedSecondaryCartPosition.z);
        minecart.motionX = minecart.motionY = minecart.motionZ = 0D;
        minecart.velocityChanged = true;
    }

    @Override
    public void setDead() {
        ContraptionGuiProxy.closeForStructureTransition(this);
        worldProperties.clear();
        if (world.isRemote) {
            clearPendingClientSnapshot(getEntityId());
        }
        if (carriedWorld != null) {
            carriedWorld.clearScheduledUpdates();
            carriedWorld = null;
        }
        if (!world.isRemote && !restoringSnapshot) {
            if (parentContraptionUuid != null) {
                nestedBearings.detachForEntityUnload();
            } else {
                nestedBearings.discardChildren();
            }
        }
        super.setDead();
        ACTIVE.remove(getBearingPos(), this);
    }

    public void markOwnedByBearing() {
        ownedByBearing = true;
    }

    public java.util.Optional<BlockPos> localPositionAtWorldPoint(Vec3d worldPoint) {
        if (contraption == null || worldPoint == null) {
            return java.util.Optional.empty();
        }
        ContraptionTransform.Point local = compositeWorldToLocalRaw(
                new ContraptionTransform.Point(
                        worldPoint.x, worldPoint.y, worldPoint.z),
                interactionAngle());
        return java.util.Optional.of(new BlockPos(
                (int) Math.floor(local.x()),
                (int) Math.floor(local.y()),
                (int) Math.floor(local.z())).subtract(getAnchor()));
    }

    public World getInteractionWorld() {
        MobileChunk mobileChunk = getMobileChunk();
        return mobileChunk == null ? world : mobileChunk.getFakeWorld();
    }

    public void syncInteractionBlock(BlockPos localPos) {
        if (world.isRemote || contraption == null) {
            return;
        }
        BearingContraption.Snapshot snapshot =
                contraption.snapshotAtLocal(localPos).orElse(null);
        int stateId = snapshot == null ? -1
                : Block.getStateId(snapshot.state());
        NBTTagCompound tile = snapshot == null ? null
                : snapshot.tileData();
        ModNetwork.CHANNEL.sendToAllAround(
                new ContraptionBlockChangedPacket(
                        getEntityId(), localPos, stateId, tile),
                new NetworkRegistry.TargetPoint(
                        world.provider.getDimension(),
                        posX, posY, posZ, 96.0D));
    }

    private void registerActive() {
        if (elevator || parentContraptionUuid != null || parentContraption != null) {
            return;
        }
        BlockPos key = getBearingPos();
        BearingContraptionEntity previous = ACTIVE.put(key, this);
        if (previous != null && previous != this && !previous.isDead) {
            previous.setDead();
        }
    }

    public static BearingContraptionEntity findByUuid(
            World world, java.util.UUID uuid) {
        if (world == null || uuid == null) {
            return null;
        }
        for (BearingContraptionEntity candidate :
                world.getEntities(BearingContraptionEntity.class,
                        ignored -> true)) {
            if (!candidate.isDead && uuid.equals(candidate.getUniqueID())) {
                return candidate;
            }
        }
        return null;
    }
}
