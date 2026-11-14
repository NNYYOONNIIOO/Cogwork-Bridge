package com.cogwork.cogworkbridge.content.trains.track;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.decoration.SeatEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;

/** One controller and shared cargo, with a clipped carriage body in each traversed dimension. */
@Mod.EventBusSubscriber(modid=Reference.MOD_ID)
public final class TrainDimensionTransfer {
    private TrainDimensionTransfer() { }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        List<RailwayCarriageEntity> owners=new ArrayList<>();
        // Entity creation and transfer stay outside world/entity iteration.
        for(WorldServer level:DimensionManager.getWorlds())for(Entity entity:new ArrayList<>(level.loadedEntityList))
            if(entity instanceof RailwayCarriageEntity car&&!car.isDead&&!car.isPortalProxy()&&car.portalPassage!=null)owners.add(car);
        Set<UUID> transferred=new HashSet<>();
        for(var owner:owners)update(owner,transferred);
    }
    private static void update(RailwayCarriageEntity owner,Set<UUID> transferred) {
        var passage=owner.portalPassage;var body=owner.body();
        if(body==null||body.getContraption()==null)return;
        var server=owner.world.getMinecraftServer();
        WorldServer target=server==null?null:server.getWorld(passage.targetDimension());
        Vec3d at=passage.map(owner.getPositionVector());
        if(target==null||!target.isBlockLoaded(new BlockPos(at))){owner.portalFailed();return;}
        RailwayCarriageEntity copy=owner.portalCopy();
        BearingContraptionEntity other=copy==null?owner.savedPortalBody(target):copy.body();
        if(copy!=null&&other==null){owner.portalFailed();return;}
        if(copy==null) {
            if(!ForgeHooks.onTravelToDimension(owner,passage.targetDimension())
                    ||!ForgeHooks.onTravelToDimension(body,passage.targetDimension())||!body.preparePortablePickup()) {
                owner.portalFailed();return;
            }
            copy=new RailwayCarriageEntity(target);
            copy.readFromNBT(owner.writeToNBT(new NBTTagCompound()));copy.dimension=passage.targetDimension();
            if(other==null) {
                other=new BearingContraptionEntity(target);
                NBTTagCompound data=body.writeToNBT(new NBTTagCompound());data.setUniqueId("UUID",UUID.randomUUID());
                other.readFromNBT(data);other.dimension=passage.targetDimension();
            }
            copy.initializePortalCopy(owner,other);other.setRailwayPortalPose(copy);
            setCutoff(body,other,passage);other.shareRailwayPortalData(body);
            if(!target.spawnEntity(copy)){other.discardRailwayPortalCopy();owner.portalFailed();return;}
            if(!target.loadedEntityList.contains(other)&&!target.spawnEntity(other)) {
                copy.setDead();other.discardRailwayPortalCopy();owner.portalFailed();return;
            }
            owner.rememberPortalBody(other);
        } else {
            copy.copyPortalPosition(owner);other.shareRailwayPortalData(body);setCutoff(body,other,passage);
            other.moveRailwayPortalCopy();
            if(other.isStalled()){body.setStalled(true);owner.portalFailed();return;}
        }
        var parts=other.synchronizeRailwayPortalChildren(body);
        for(var part:parts)if(part.copy()==null){owner.portalFailed();return;}
        for(var part:parts)if(!moveSide(owner,copy,part.source(),part.copy(),passage,transferred)
                ||!moveSide(copy,owner,part.copy(),part.source(),passage.reverse(),transferred)){owner.portalFailed();return;}
        boolean sourceWhole=true,targetWhole=true;
        for(var part:parts)for(var snapshot:part.source().getContraption().blocks().values()) {
            BlockPos local=part.source().localPos(snapshot);sourceWhole&=part.source().isRailwayBlockVisible(local);targetWhole&=part.copy().isRailwayBlockVisible(local);
        }
        if(targetWhole&&owner.bogeysInDimension(passage.targetDimension()))promote(owner,copy,body,other);
        else if(sourceWhole&&owner.bogeysInDimension(passage.sourceDimension())
                &&passage.sourceDistance(owner.getPositionVector())<-1.5
                &&new Vec3d(owner.motionX,owner.motionY,owner.motionZ).dotProduct(new Vec3d(passage.sourceDirection().getDirectionVec()))<-.001
                &&parts.stream().noneMatch(part->hasSeatPassengers(part.copy()))) {
            other.discardRailwayPortalCopy();copy.setDead();body.clearRailwayPortalSlice();owner.clearPortal();
        }
    }
    private static void setCutoff(BearingContraptionEntity body,BearingContraptionEntity copy,RailwayPortals.Passage passage) {
        applyCutoff(body,passage,copy);
    }
    public static void refreshSourceCutoff(RailwayCarriageEntity owner,BearingContraptionEntity body) {
        if(owner.portalPassage!=null)applyCutoff(body,owner.portalPassage,null);
    }
    private static void applyCutoff(BearingContraptionEntity body,RailwayPortals.Passage passage,BearingContraptionEntity copy) {
        Vec3d normal=new Vec3d(passage.sourceDirection().getDirectionVec());EnumFacing localDirection=EnumFacing.SOUTH;double best=-Double.MAX_VALUE;
        for(EnumFacing direction:EnumFacing.HORIZONTALS) {
            double dot=body.localFacingVectorToWorld(direction).dotProduct(normal);
            if(dot>best){best=dot;localDirection=direction;}
        }
        Vec3d localPlane=body.worldPointToLocal(passage.source()).subtract(new Vec3d(body.getAnchor()).add(.5,.5,.5));
        int seam=(int)Math.floor(localPlane.dotProduct(new Vec3d(localDirection.getDirectionVec()))+.5);
        body.setRailwayPortalSlice(localDirection,seam,false);if(copy!=null)copy.setRailwayPortalSlice(localDirection,seam,true);
    }
    private static boolean moveSide(RailwayCarriageEntity source,RailwayCarriageEntity destination,
            BearingContraptionEntity body,BearingContraptionEntity other,RailwayPortals.Passage passage,Set<UUID> transferred) {
        List<SeatEntity> seats=new ArrayList<>();List<Entity> standing=new ArrayList<>();
        for(Entity entity:new ArrayList<>(body.world.loadedEntityList)) {
            if(entity instanceof SeatEntity seat&&!seat.isDead&&seat.belongsTo(body))seats.add(seat);
            else if(entity instanceof EntityLivingBase&&entity.isEntityAlive()&&!entity.isRiding()&&body.wasRailwayPassenger(entity))standing.add(entity);
        }
        for(SeatEntity seat:seats) {
            if(passage.sourceDistance(body.localBlockCenterToWorld(seat.localPosition()))<0)continue;
            for(Entity rider:new ArrayList<>(seat.getPassengers())) {
                if(transferred.contains(rider.getUniqueID()))continue;
                SeatEntity newSeat=findSeat(other,seat.localPosition());boolean created=newSeat==null;
                if(created){newSeat=new SeatEntity(other.world);newSeat.bind(other,seat.localPosition());if(!other.world.spawnEntity(newSeat))return false;}
                Vec3d seatPosition=newSeat.getPositionVector().add(0,newSeat.getMountedYOffset()+rider.getYOffset(),0);
                Entity moved=transferPassenger(rider,(WorldServer)other.world,seatPosition,passage);
                if(moved==null){if(created)newSeat.setDead();return false;}
                moved.startRiding(newSeat,true);transferred.add(moved.getUniqueID());destination.resumeDriverAfterTransfer();
            }
            if(seat.getPassengers().isEmpty())seat.setDead();
        }
        for(Entity rider:standing) {
            if(transferred.contains(rider.getUniqueID())||passage.sourceDistance(rider.getPositionVector())<0)continue;
            Entity moved=transferPassenger(rider,(WorldServer)other.world,passage.map(rider.getPositionVector()),passage);
            if(moved==null)return false;
            transferred.add(moved.getUniqueID());other.rememberRailwayPassenger(moved);moved.onGround=true;destination.resumeDriverAfterTransfer();
        }
        return true;
    }
    private static SeatEntity findSeat(BearingContraptionEntity body,BlockPos local) {
        for(Entity entity:body.world.loadedEntityList)if(entity instanceof SeatEntity seat&&!seat.isDead&&seat.belongsTo(body,local))return seat;
        return null;
    }
    private static boolean hasSeatPassengers(BearingContraptionEntity body) {
        for(Entity entity:body.world.loadedEntityList)if(entity instanceof SeatEntity seat&&seat.belongsTo(body)&&!seat.getPassengers().isEmpty())return true;
        return false;
    }
    private static void promote(RailwayCarriageEntity owner,RailwayCarriageEntity copy,BearingContraptionEntity body,BearingContraptionEntity other) {
        // Keep the already spawned destination body and its seats, including their entity IDs.
        NBTTagCompound state=owner.writeToNBT(new NBTTagCompound());Vec3d at=copy.getPositionVector();
        copy.readFromNBT(state);copy.dimension=copy.world.provider.getDimension();copy.setPosition(at.x,at.y,at.z);
        copy.transferredFrom(owner);copy.clearPortal();copy.attach(other);other.promoteRailwayPortalCopy();
        owner.markPortalReplacement();body.discardRailwayPortalCopy();owner.setDead();
        RailwayNetworkData.get(copy.world).updateOccupation(copy);copy.resumeDriverAfterTransfer();
    }
    private static Entity transferPassenger(Entity entity,WorldServer target,Vec3d at,RailwayPortals.Passage passage) {
        record Passenger(Entity original,Entity moved,int parent,Vec3d position,float yaw,Vec3d velocity) { }
        List<Entity> originals=new ArrayList<>();List<Integer> parents=new ArrayList<>();
        List<Passenger> plans=new ArrayList<>();originals.add(entity);parents.add(-1);
        for(int index=0;index<originals.size();index++) {
            if(originals.size()>128)return null;
            Entity original=originals.get(index);
            if(!ForgeHooks.onTravelToDimension(original,target.provider.getDimension()))return null;
            Entity moved=original;
            if(!(original instanceof EntityPlayerMP)) {
                var type=net.minecraft.entity.EntityList.getKey(original);if(type==null)return null;
                moved=net.minecraft.entity.EntityList.createEntityByIDFromName(type,target);if(moved==null)return null;
                NBTTagCompound snapshot=original.writeToNBT(new NBTTagCompound());snapshot.removeTag("Passengers");moved.readFromNBT(snapshot);
            }
            Vec3d direction=passage.mapVector(new Vec3d(-Math.sin(Math.toRadians(original.rotationYaw)),0,Math.cos(Math.toRadians(original.rotationYaw))));
            float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
            Vec3d position=at.add(passage.mapVector(original.getPositionVector().subtract(entity.getPositionVector())));
            Vec3d velocity=passage.mapVector(new Vec3d(original.motionX,original.motionY,original.motionZ));
            plans.add(new Passenger(original,moved,parents.get(index),position,yaw,velocity));
            for(Entity child:new ArrayList<>(original.getPassengers())){originals.add(child);parents.add(index);}
        }
        List<Entity> spawned=new ArrayList<>();
        for(var plan:plans)if(!(plan.original instanceof EntityPlayerMP)) {
            Entity moved=plan.moved;Vec3d position=plan.position;
            moved.dimension=target.provider.getDimension();moved.setLocationAndAngles(position.x,position.y,position.z,plan.yaw,plan.original.rotationPitch);
            if(!target.spawnEntity(moved)){spawned.forEach(Entity::setDead);return null;}
            spawned.add(moved);
        }
        // All replacement entities are accepted before detaching any original passenger.
        for(var plan:plans) {
            plan.original.dismountRidingEntity();Vec3d position=plan.position;
            if(plan.original instanceof EntityPlayerMP player) {
                player.getServer().getPlayerList().transferPlayerToDimension(player,target.provider.getDimension(),
                        (world,moved,oldYaw)->moved.setLocationAndAngles(position.x,position.y,position.z,plan.yaw,moved.rotationPitch));
                player.connection.setPlayerLocation(position.x,position.y,position.z,plan.yaw,player.rotationPitch);
            } else {plan.original.world.removeEntity(plan.original);plan.original.setDead();}
            if (plan.moved instanceof EntityPlayerMP player) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.award(player, "train_portal");
            plan.moved.fallDistance=0;plan.moved.timeUntilPortal=plan.moved.getPortalCooldown();
            plan.moved.motionX=plan.velocity.x;plan.moved.motionY=plan.velocity.y;plan.moved.motionZ=plan.velocity.z;
        }
        for(var plan:plans)if(plan.parent>=0)plan.moved.startRiding(plans.get(plan.parent).moved,true);
        return plans.get(0).moved;
    }
}
