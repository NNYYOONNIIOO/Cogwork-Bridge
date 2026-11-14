package com.cogwork.cogworkbridge.content.trains.track;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/** Server-authoritative track position carrying one moving-world carriage. */
public final class RailwayCarriageEntity extends EntityMinecartEmpty {
    @Nullable private TrackCursor cursor,previousCursor;
    @Nullable private TrackCursor secondBogeyCursor;
    @Nullable private TrackCursor previousSecondBogeyCursor;
    @Nullable private Vec3d expectedPosition;
    @Nullable private UUID bodyId,driver;
    private double speed;
    private int controls;
    private long lastInput;
    @Nullable private TrainSchedule schedule;
    private int scheduleIndex,waitTicks;
    private boolean scheduleWaiting,schedulePaused,scheduleFinished;
    @Nullable private List<RailwayNetworkData.Travel> route;
    @Nullable private List<RailwayNetworkData.Travel> previousRoute;
    @Nullable private RailwayNetworkData.Node destination;
    @Nullable private net.minecraft.util.math.BlockPos destinationStation;
    private int automaticDirection;
    private long nextRouteAttempt;
    private int destinationDimension;
    private boolean portalPending;
    @Nullable RailwayPortals.Passage portalPassage;
    private boolean portalProxy;
    @Nullable private UUID portalBodyId;
    public boolean awaitingPortalTransfer(){return portalPending;}
    public boolean isPortalProxy(){return portalProxy;}
    @Nullable public RailwayCarriageEntity portalOwner() {
        if(!portalProxy)return this;
        Entity owner=findTrainEntity(getUniqueID());return owner instanceof RailwayCarriageEntity c?c:null;
    }
    @Nullable RailwayCarriageEntity portalCopy() {
        if(portalProxy)return portalOwner();
        if(portalPassage==null||world.getMinecraftServer()==null)return null;
        WorldServer other=world.getMinecraftServer().getWorld(portalPassage.targetDimension());
        Entity entity=other==null?null:other.getEntityFromUuid(getUniqueID());
        return entity instanceof RailwayCarriageEntity c&&c.portalProxy&&!c.isDead?c:null;
    }
    void initializePortalCopy(RailwayCarriageEntity owner,BearingContraptionEntity copy) {
        portalProxy=true;portalPassage=owner.portalPassage;bodyId=copy.getUniqueID();portalBodyId=null;
        driver=null;controls=0;trainPath=null;route=null;copyPortalPosition(owner);
    }
    void copyPortalPosition(RailwayCarriageEntity owner) {
        Vec3d at=owner.portalPassage.map(owner.getPositionVector());
        prevPosX=posX;prevPosY=posY;prevPosZ=posZ;
        motionX=at.x-posX;motionY=at.y-posY;motionZ=at.z-posZ;
        setPosition(at.x,at.y,at.z);speed=owner.speed;cursor=owner.cursor;secondBogeyCursor=owner.secondBogeyCursor;
        velocityChanged=true;
    }
    void rememberPortalBody(BearingContraptionEntity copy){portalBodyId=copy.getUniqueID();}
    @Nullable BearingContraptionEntity savedPortalBody(WorldServer level) {
        Entity entity=portalBodyId==null?null:level.getEntityFromUuid(portalBodyId);
        return entity instanceof BearingContraptionEntity body&&!body.isDead?body:null;
    }
    void clearPortal(){portalPassage=null;portalBodyId=null;portalProxy=false;portalPending=false;}
    void markPortalReplacement(){portalPending=true;}
    boolean bogeysInDimension(int dimension) {
        var graph=RailwayNetworkData.get(world);
        return cursor!=null&&cursor.dimension(graph)==dimension
                &&(secondBogeyCursor==null||secondBogeyCursor.dimension(graph)==dimension);
    }
    @Nullable private Vec3d physicalPosition(TrackCursor position,Vec3d at) {
        var graph=RailwayNetworkData.get(world);int dimension=position.dimension(graph);
        if(portalPassage==null) {
            var structure=body();Vec3d direction=previousCursor==null?position.heading(graph):previousCursor.heading(graph);
            double reach=3;
            if(structure!=null&&structure.getContraption()!=null)
                for(var part:structure.railwayPortalParts())for(var snapshot:part.getContraption().blocks().values()) {
                    Vec3d point=part.localBlockCenterToWorld(part.localPos(snapshot)).subtract(getPositionVector());
                    reach=Math.max(reach,Math.hypot(point.x,point.z)+3);
                }
            var candidate=RailwayPortals.nearby(world,getPositionVector(),direction,reach);
            if(candidate!=null&&structure!=null&&structure.getContraption()!=null) {
                double max=-Double.MAX_VALUE;
                for(var part:structure.railwayPortalParts())for(var snapshot:part.getContraption().blocks().values())
                    max=Math.max(max,candidate.sourceDistance(part.localBlockCenterToWorld(part.localPos(snapshot))));
                if(dimension!=world.provider.getDimension()||max>=-1.5)portalPassage=candidate;
            }
        }
        if(dimension==world.provider.getDimension())return at;
        if(portalPassage!=null&&dimension==portalPassage.targetDimension())return portalPassage.reverse().map(at);
        return null;
    }
    private final ScheduleWaitRuntime waitRuntime=new ScheduleWaitRuntime();
    private double throttle=1D;
    private String runTitle="";
    private int ticksInTransit;
    private double routeStartedDistance;
    private int[] transitTimes=new int[TrainSchedule.MAX_ENTRIES];
    { java.util.Arrays.fill(transitTimes,-1); }
    public net.minecraft.util.math.BlockPos stationPosition() { return destinationStation; }
    public World stationWorld() { return world.getMinecraftServer()==null?world:world.getMinecraftServer().getWorld(destinationDimension); }
    public String runTitle() { return runTitle; }
    public String trainName() { return hasCustomName()?getCustomNameTag():"Train "+trainId().toString().substring(0,8); }
    public boolean schedulePaused() { return schedulePaused; }
    public boolean hasSchedule() { return schedule!=null; }
    public String waitingStatus() { return schedule==null||schedulePaused||!scheduleWaiting||scheduleComplete()||!schedule.cyclic&&scheduleIndex==schedule.entries.size()-1?"":waitRuntime.status(schedule.entries.get(scheduleIndex)); }
    public boolean scheduleComplete() { return schedule!=null&&scheduleFinished; }
    public boolean resumeSchedule() {
        if(schedule==null||!schedulePaused||scheduleComplete())return false;
        schedulePaused=false;route=null;nextRouteAttempt=0;return true;
    }
    public boolean validConductorSeat(com.cogwork.cogworkbridge.content.decoration.SeatEntity seat) {
        return conductorDirection(seat,1)||conductorDirection(seat,-1);
    }
    private boolean conductorDirection(com.cogwork.cogworkbridge.content.decoration.SeatEntity seat,int sign) {
        var structure=body();if(structure==null||!seat.belongsTo(structure)||structure.getContraptionLocalWorld()==null)return false;
        var heading=heading();if(heading==null)return false;
        for(var side:net.minecraft.util.EnumFacing.HORIZONTALS) {
            var state=structure.getContraptionLocalWorld().getBlockState(seat.localPosition().offset(side));
            if(state.getBlock() instanceof TrainControlsBlock&&state.getValue(TrainControlsBlock.FACING)==side.getOpposite()
                    &&structure.localFacingVectorToWorld(side).dotProduct(heading)*sign>.5)return true;
        }return false;
    }
    public boolean hasConductor(int sign) {
        var cars=trainCarriages();if(cars==null)return false;
        for(var original:cars)for(var car:original.dimensionalCopies()) {
            var body=car.body();if(body==null||body.getContraptionLocalWorld()==null)continue;
            for(Entity entity:car.world.loadedEntityList)
                if(entity instanceof com.cogwork.cogworkbridge.content.decoration.SeatEntity seat
                        &&seat.getPassengers().stream().anyMatch(p->p instanceof net.minecraft.entity.EntityLivingBase)
                        &&car.conductorDirection(seat,sign))return true;
            if(body.getContraption()!=null)for(var snapshot:body.getContraption().blocks().values()) {
                var local=body.localPos(snapshot);var state=body.getContraptionLocalWorld().getBlockState(local);
                if(!(state.getBlock() instanceof com.cogwork.cogworkbridge.content.processing.burner.BlazeBurnerBlock burner)||!burner.isOccupied())continue;
                for(var side:net.minecraft.util.EnumFacing.HORIZONTALS) {
                    var controls=body.getContraptionLocalWorld().getBlockState(local.offset(side));
                    if(controls.getBlock() instanceof TrainControlsBlock&&controls.getValue(TrainControlsBlock.FACING)==side.getOpposite()
                            &&car.heading()!=null&&body.localFacingVectorToWorld(side).dotProduct(car.heading())*sign>.5)return true;
                }
            }
        }return false;
    }
    @Nullable private UUID trainLeader, driverCarriage;
    private final List<UUID> trainMembers = new ArrayList<>();
    private double trainOffset, secondBogeyOffset = -1D;
    @Nullable private TrainPath trainPath, previousTrainPath;

    public RailwayCarriageEntity(World world) {
        super(world);setSize(.05F,.05F);noClip=true;canBePushed=false;setInvisible(true);setNoGravity(true);
    }
    public void initialize(TrackCursor cursor) {
        this.cursor=cursor;
        Vec3d position=cursor.position(RailwayNetworkData.get(world));
        if(position!=null)setPosition(position.x,position.y,position.z);
        prevPosX=posX;prevPosY=posY;prevPosZ=posZ;
    }
    public void attach(BearingContraptionEntity body){bodyId=body.getUniqueID();}
    public UUID trainId(){return trainLeader == null ? getUniqueID() : trainLeader;}
    @Nullable public RailwayCarriageEntity trainController() {
        if(portalProxy){var owner=portalOwner();return owner==null?null:owner.trainController();}
        if (trainLeader == null || trainLeader.equals(getUniqueID())) return this;
        Entity entity = findTrainEntity(trainLeader);
        return entity instanceof RailwayCarriageEntity carriage && !carriage.isDead ? carriage : null;
    }
    @Nullable public List<RailwayCarriageEntity> trainCarriages() {
        RailwayCarriageEntity leader = trainController();
        if (leader == null) return null;
        if (leader.trainLeader == null) return List.of(leader);
        if (!(world instanceof WorldServer server) || leader.trainMembers.isEmpty()) return null;
        List<RailwayCarriageEntity> result = new ArrayList<>();
        for (UUID id : leader.trainMembers) {
            Entity entity = findTrainEntity(id);
            if (!(entity instanceof RailwayCarriageEntity carriage) || carriage.isDead
                    || !leader.getUniqueID().equals(carriage.trainLeader) || carriage.body() == null) return null;
            result.add(carriage);
        }
        return result;
    }
    public List<RailwayCarriageEntity> dimensionalCopies() {
        RailwayCarriageEntity copy=portalCopy();return copy==null?List.of(this):List.of(this,copy);
    }
    static void configureTrain(List<RailwayCarriageEntity> carriages, TrainPath path,
                               List<Double> offsets, List<Double> secondOffsets) {
        RailwayCarriageEntity leader = carriages.get(0);
        UUID id = leader.getUniqueID();
        leader.trainPath = path;
        for (int i = 0; i < carriages.size(); i++) {
            RailwayCarriageEntity carriage = carriages.get(i);
            carriage.trainLeader = id;
            carriage.trainOffset = offsets.get(i);
            carriage.secondBogeyOffset = secondOffsets.get(i);
            leader.trainMembers.add(carriage.getUniqueID());
        }
        leader.placeTrain(RailwayNetworkData.get(leader.world), carriages, false);
    }
    private Entity findTrainEntity(UUID id) {
        if(world.getMinecraftServer()==null)return null;
        for(WorldServer level:world.getMinecraftServer().worlds){Entity entity=level.getEntityFromUuid(id);if(entity!=null&&!entity.isDead&&! (entity instanceof RailwayCarriageEntity c&&(c.portalPending||c.portalProxy)))return entity;}return null;
    }
    void transferredFrom(RailwayCarriageEntity old) {
        speed=old.speed;portalPending=false;route=old.route;destination=old.destination;automaticDirection=old.automaticDirection;nextRouteAttempt=0;
        driver=old.driver;driverCarriage=old.driverCarriage;controls=old.controls;lastInput=world.getTotalWorldTime();secondBogeyCursor=old.secondBogeyCursor;
        routeStartedDistance=old.routeStartedDistance;previousTrainPath=old.previousTrainPath;
        previousRoute=old.previousRoute;
        expectedPosition=getPositionVector();previousCursor=old.previousCursor;previousSecondBogeyCursor=old.previousSecondBogeyCursor;
    }
    void resumeDriverAfterTransfer() {
        var leader=trainController();if(leader==null||leader.driver==null||!getUniqueID().equals(leader.driverCarriage))return;
        var player=world.getMinecraftServer().getPlayerList().getPlayerByUUID(leader.driver);
        if(player!=null) {
            RailwayCarriageEntity controlled=player.world==world?this:portalCopy();
            if(controlled!=null&&player.world==controlled.world)com.cogwork.cogworkbridge.network.ModNetwork.CHANNEL.sendTo(new TrainControlClientPacket(controlled.getEntityId()),player);
        }
    }
    void portalFailed() {
        portalPending=false;speed=0;motionX=motionY=motionZ=0;
        if(previousCursor!=null)cursor=previousCursor;secondBogeyCursor=previousSecondBogeyCursor;
        RailwayCarriageEntity leader=trainController();
        if(leader!=null){if(leader.previousTrainPath!=null)leader.trainPath=leader.previousTrainPath;leader.previousTrainPath=null;leader.route=null;leader.destination=null;leader.nextRouteAttempt=world.getTotalWorldTime()+40;leader.haltLoadedCarriages();}
    }
    boolean hasBogeyAt(Vec3d position) {
        if(portalPassage!=null||portalProxy)return false;
        if (getPositionVector().squareDistanceTo(position) < .0625D) return true;
        Vec3d second = secondBogeyCursor == null ? null : secondBogeyCursor.position(RailwayNetworkData.get(world));
        if(secondBogeyCursor!=null&&secondBogeyCursor.dimension(RailwayNetworkData.get(world))!=world.provider.getDimension())return false;
        return second != null && second.squareDistanceTo(position) < .0625D;
    }
    private boolean placeTrain(RailwayNetworkData graph, List<RailwayCarriageEntity> carriages, boolean move) {
        if (trainPath == null) return false;
        List<TrackCursor> positions = new ArrayList<>(), secondPositions = new ArrayList<>();
        List<Vec3d> locations = new ArrayList<>();
        for (RailwayCarriageEntity carriage : carriages) {
            TrackCursor primary = trainPath.at(graph, carriage.trainOffset);
            TrackCursor secondary = carriage.secondBogeyOffset < 0D ? null : trainPath.at(graph, carriage.secondBogeyOffset);
            Vec3d location = primary == null ? null : primary.position(graph);
            if (location == null || carriage.secondBogeyOffset >= 0D && secondary == null) return false;
            World level=primary.world(world);if(level==null||!level.isBlockLoaded(new net.minecraft.util.math.BlockPos(location)))return false;
            positions.add(primary); secondPositions.add(secondary); locations.add(location);
        }
        for (int i = 0; i < carriages.size(); i++) {
            RailwayCarriageEntity carriage = carriages.get(i);
            Vec3d location = locations.get(i);
            carriage.prevPosX = carriage.posX; carriage.prevPosY = carriage.posY; carriage.prevPosZ = carriage.posZ;
            carriage.previousCursor = carriage.cursor;
            carriage.previousSecondBogeyCursor=carriage.secondBogeyCursor;
            carriage.cursor = positions.get(i); carriage.secondBogeyCursor = secondPositions.get(i);
            location=carriage.physicalPosition(carriage.cursor,location);if(location==null)return false;
            double dx = location.x - carriage.posX, dy = location.y - carriage.posY, dz = location.z - carriage.posZ;
            carriage.motionX = move ? dx : 0D; carriage.motionY = move ? dy : 0D; carriage.motionZ = move ? dz : 0D;
            carriage.speed = speed;
            carriage.setPosition(location.x, location.y, location.z);
            carriage.expectedPosition = location;
            if (move && Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 1E-8D) carriage.velocityChanged = true;
        }
        return true;
    }
    private void haltLoadedCarriages() {
        speed=motionX=motionY=motionZ=0D;
        UUID id=trainId();
        if(world.getMinecraftServer()!=null)for(WorldServer level:world.getMinecraftServer().worlds)for(Entity entity:level.loadedEntityList)
            if(entity instanceof RailwayCarriageEntity carriage && id.equals(carriage.trainId())) {
                carriage.speed=0D;carriage.motionX=carriage.motionY=carriage.motionZ=0D;
            }
    }
    private void updateTrainOccupation(RailwayNetworkData graph,List<RailwayCarriageEntity> carriages) {
        var server=world.getMinecraftServer();if(server==null)return;
        for(WorldServer level:server.worlds)for(var car:carriages)RailwayNetworkData.get(level).updateOccupation(car);
    }
    @Nullable public BearingContraptionEntity body(){
        if(bodyId==null)return null;
        Entity e=world instanceof WorldServer server?server.getEntityFromUuid(bodyId):null;
        return e instanceof BearingContraptionEntity bearing&&!bearing.isDead?bearing:null;
    }
    @Nullable public Vec3d heading(){
        if(portalProxy) {var owner=portalOwner();Vec3d heading=owner==null?null:owner.heading();return heading==null||portalPassage==null?null:portalPassage.mapVector(heading);}
        if (cursor == null || world.isRemote) return null;
        RailwayNetworkData graph = RailwayNetworkData.get(world);
        if (secondBogeyCursor != null) {
            Vec3d first = cursor.position(graph), second = secondBogeyCursor.position(graph);
            if(portalPassage!=null) {
                if(first!=null&&cursor.dimension(graph)==portalPassage.targetDimension())first=portalPassage.reverse().map(first);
                if(second!=null&&secondBogeyCursor.dimension(graph)==portalPassage.targetDimension())second=portalPassage.reverse().map(second);
            } else if(secondBogeyCursor.dimension(graph)!=cursor.dimension(graph))return null;
            if (first != null && second != null && first.squareDistanceTo(second) > 1E-8D)
                return second.subtract(first).normalize();
        }
        Vec3d direction=cursor.heading(graph);
        return direction!=null&&portalPassage!=null&&cursor.dimension(graph)==portalPassage.targetDimension()?portalPassage.reverse().mapVector(direction):direction;
    }
    public double speed(){return speed;}
    List<RailwayNetworkData.Travel> occupiedTrainPath(RailwayNetworkData graph) {
        return trainPath == null ? List.of() : trainPath.occupiedEdges(graph);
    }
    @Nullable public RailwayNetworkData.Travel currentEdge(RailwayNetworkData graph){return cursor==null?null:cursor.edge(graph);}
    public boolean hasCargo(net.minecraft.item.ItemStack filter) {
        if(filter.isEmpty())return true;
        List<RailwayCarriageEntity> carriages = trainCarriages();
        if (carriages == null) return false;
        for (RailwayCarriageEntity carriage : carriages) if (carriage.hasLocalCargo(filter)) return true;
        return false;
    }
    private boolean hasLocalCargo(net.minecraft.item.ItemStack filter) {
        BearingContraptionEntity structure=body();if(structure==null)return false;
        var inventory=new com.cogwork.cogworkbridge.content.logistics.transport.ContraptionItemStorage(structure);
        for(int i=0;i<inventory.getSlots();i++) {
            net.minecraft.item.ItemStack item=inventory.getStackInSlot(i);
            if(!item.isEmpty()&&com.cogwork.cogworkbridge.content.fluids.ListFilterItem.matchesItem(filter,item))return true;
        }
        return false;
    }
    public boolean startDriving(EntityPlayerMP player) {
        BearingContraptionEntity structure=body();
        RailwayCarriageEntity leader = trainController();
        if(structure==null || leader==null || leader.trainCarriages()==null || leader.driver!=null&&!leader.driver.equals(player.getUniqueID())
                || !structure.getEntityBoundingBox().grow(4D).contains(player.getPositionVector()))return false;
        leader.driver=player.getUniqueID();leader.driverCarriage=getUniqueID();leader.controls=0;
        if(leader.schedule!=null)leader.schedulePaused=true;
        leader.route=null;leader.destination=null;leader.lastInput=world.getTotalWorldTime();return true;
    }
    public boolean input(EntityPlayerMP player,int mask) {
        RailwayCarriageEntity leader = trainController();
        if(leader==null||leader.driver==null||!leader.driver.equals(player.getUniqueID())
                ||!getUniqueID().equals(leader.driverCarriage))return false;
        RailwayCarriageEntity local=player.world==world?this:portalCopy();
        BearingContraptionEntity structure=local==null?null:local.body();
        if(structure==null||!structure.getEntityBoundingBox().grow(4D).contains(player.getPositionVector())||player.isSpectator()){
            leader.driver=null;leader.driverCarriage=null;leader.controls=0;return false;
        }
        leader.controls=mask&15;leader.lastInput=world.getTotalWorldTime();return true;
    }
    public void stopDriving(EntityPlayerMP player){
        RailwayCarriageEntity leader = trainController();
        if(leader!=null&&leader.driver!=null&&leader.driver.equals(player.getUniqueID())){
            leader.driver=null;leader.driverCarriage=null;leader.controls=0;
        }
    }
    public boolean installSchedule(net.minecraft.item.ItemStack stack) {
        RailwayCarriageEntity leader = trainController();
        if (leader == null) return false;
        if (leader != this) return leader.installSchedule(stack);
        if(schedule!=null||stack.getItem()!=com.cogwork.cogworkbridge.registry.AllItems.SCHEDULE)return false;
        TrainSchedule next=TrainSchedule.fromItem(stack);
        if(next.entries.isEmpty())return false;
        schedule=next;scheduleIndex=next.savedProgress;waitTicks=0;scheduleWaiting=schedulePaused=scheduleFinished=false;speed=0D;waitRuntime.reset();throttle=1D;
        java.util.Arrays.fill(transitTimes,-1);ticksInTransit=0;
        route=null;destination=null;return true;
    }
    public net.minecraft.item.ItemStack removeSchedule() {
        RailwayCarriageEntity leader = trainController();
        if (leader == null) return net.minecraft.item.ItemStack.EMPTY;
        if (leader != this) return leader.removeSchedule();
        if(schedule==null)return net.minecraft.item.ItemStack.EMPTY;
        net.minecraft.item.ItemStack stack=new net.minecraft.item.ItemStack(com.cogwork.cogworkbridge.registry.AllItems.SCHEDULE);
        schedule.savedProgress=scheduleFinished?0:scheduleIndex;schedule.save(stack);schedule=null;route=null;destination=null;controls=0;waitRuntime.reset();return stack;
    }
    private boolean plan(RailwayNetworkData graph) {
        if(schedule==null||cursor==null||scheduleIndex>=schedule.entries.size())return false;
        var entry=schedule.entries.get(scheduleIndex);String filter=entry.destination();
        double best=Double.POSITIVE_INFINITY;List<RailwayNetworkData.Travel> chosen=null;
        RailwayNetworkData.Node goal=null;net.minecraft.util.math.BlockPos chosenStation=null;int direction=0,chosenDimension=world.provider.getDimension();
        for(WorldServer level:world.getMinecraftServer().worlds)for(RailwayNetworkData.Target station:RailwayNetworkData.get(level).targets()) {
            var mail=com.cogwork.cogworkbridge.content.logistics.port.MailboxNetwork.get(level);
            if(entry.instruction==TrainSchedule.Instruction.FETCH_PACKAGES&&!mail.offers(station.owner(),filter))continue;
            if(entry.instruction==TrainSchedule.Instruction.DELIVER_PACKAGES&&!mail.accepts(station.owner(),this))continue;
            if(!"station".equals(station.kind())||entry.instruction==TrainSchedule.Instruction.DESTINATION&&!TrainSchedule.matches(filter,station.name()))continue;
            RailwayNetworkData.Node end=RailwayNetworkData.get(level).stationArrival(station);if(end==null)continue;
            for(int sign:new int[]{1,-1}) {
                if(!hasConductor(sign))continue;
                TrackCursor leading = trainPath == null ? cursor : trainPath.at(graph, sign > 0 ? trainPath.span() : 0D);
                if (leading == null) continue;
                RailwayNetworkData.Travel current=leading.edge(graph);if(current==null)continue;
                RailwayNetworkData.Node start=sign>0?leading.to():leading.from();
                List<RailwayNetworkData.Travel> candidate=RailwayPortals.route(world,current.dimension(),start,level.provider.getDimension(),end);
                if(candidate.isEmpty()&&(!start.equals(end)||current.dimension()!=level.provider.getDimension()))continue;
                if(!candidate.isEmpty()) {
                    RailwayNetworkData.Travel first=candidate.get(0);
                    RailwayNetworkData.Node previous=sign>0?leading.from():leading.to();
                    if(first.dimension()==current.dimension()&&first.to().equals(previous)&&first.track().equals(current.track()))continue;
                }
                double distance=current.length()*(sign>0?1D-leading.progress():leading.progress());
                for(RailwayNetworkData.Travel edge:candidate)distance+=edge.length();
                if(distance<best){best=distance;chosen=new ArrayList<>(candidate);goal=end;chosenStation=station.owner();direction=sign;chosenDimension=level.provider.getDimension();}
            }
        }
        if(chosen==null) {
            if(entry.instruction==TrainSchedule.Instruction.FETCH_PACKAGES) {
                boolean available=false;for(WorldServer level:world.getMinecraftServer().worlds)for(var station:RailwayNetworkData.get(level).targets())
                    if(station.kind().equals("station")&&com.cogwork.cogworkbridge.content.logistics.port.MailboxNetwork.get(level).offers(station.owner(),filter))available=true;
                if(!available)advanceSchedule();
            }
            return false;
        }
        route=chosen;destination=goal;destinationStation=chosenStation;destinationDimension=chosenDimension;automaticDirection=direction;
        ticksInTransit=0;routeStartedDistance=best;return true;
    }
    private double routeDistance(RailwayNetworkData graph) {
        if(cursor==null||route==null||destination==null)return Double.POSITIVE_INFINITY;
        TrackCursor leading = trainPath == null ? cursor
                : trainPath.at(graph, automaticDirection > 0 ? trainPath.span() : 0D);
        if (leading == null) return Double.POSITIVE_INFINITY;
        RailwayNetworkData.Travel edge=leading.edge(graph);if(edge==null)return Double.POSITIVE_INFINITY;
        double distance=edge.length()*(automaticDirection>0?1D-leading.progress():leading.progress());
        for(RailwayNetworkData.Travel part:route)distance+=part.length();
        return distance;
    }
    private void advanceSchedule() {
        if(schedule==null)return;
        scheduleIndex++;
        if(scheduleIndex>=schedule.entries.size()) {
            if(schedule.cyclic)scheduleIndex=0;
            else {scheduleIndex=schedule.entries.size()-1;schedulePaused=true;scheduleFinished=true;}
        }
        scheduleWaiting=false;route=null;destination=null;destinationStation=null;nextRouteAttempt=0;waitRuntime.reset();
    }
    boolean legacyCargoFull() { return conditionMet(TrainSchedule.Condition.CARGO_FULL); }
    private void immediateInstructions() {
        if(schedule==null)return;
        for(int guard=0;guard<TrainSchedule.MAX_ENTRIES&&!schedulePaused;guard++) {
            var e=schedule.entries.get(scheduleIndex);
            if(e.instruction==TrainSchedule.Instruction.RENAME) {runTitle=e.text;advanceSchedule();}
            else if(e.instruction==TrainSchedule.Instruction.THROTTLE) {throttle=e.value/100D;advanceSchedule();}
            else if(e.instruction==TrainSchedule.Instruction.DELIVER_PACKAGES&&!TrainCargo.hasPackages(this))advanceSchedule();
            else break;
        }
    }
    private boolean conditionMet(TrainSchedule.Condition condition) {
        if(condition==TrainSchedule.Condition.DELAY)return true;
        if(condition==TrainSchedule.Condition.REDSTONE)
            return destinationStation!=null&&world.isBlockLoaded(destinationStation)&&world.isBlockPowered(destinationStation);
        List<RailwayCarriageEntity> carriages = trainCarriages();
        if (carriages == null) return false;
        if(condition==TrainSchedule.Condition.PLAYER) {
            for (RailwayCarriageEntity carriage : carriages) for(Entity entity:carriage.world.loadedEntityList)
                if(entity instanceof com.cogwork.cogworkbridge.content.decoration.SeatEntity seat)
                        if (carriage.body() != null && seat.belongsTo(carriage.body()))
                            for(Entity passenger:seat.getPassengers())if(passenger instanceof EntityPlayer)return true;
            return false;
        }
        int slots = 0;
        for (RailwayCarriageEntity carriage : carriages) {
            BearingContraptionEntity structure = carriage.body();
            if (structure == null) return false;
            var inventory=new com.cogwork.cogworkbridge.content.logistics.transport.ContraptionItemStorage(structure);
            slots += inventory.getSlots();
            for(int slot=0;slot<inventory.getSlots();slot++) {
                net.minecraft.item.ItemStack item=inventory.getStackInSlot(slot);
                if(condition==TrainSchedule.Condition.CARGO_EMPTY&&!item.isEmpty())return false;
                if(condition==TrainSchedule.Condition.CARGO_FULL&&(item.isEmpty()
                        ||item.getCount()<Math.min(item.getMaxStackSize(),inventory.getSlotLimit(slot))))return false;
            }
        }
        return condition != TrainSchedule.Condition.CARGO_FULL || slots > 0;
    }
    @Override public void onUpdate() {
        if(world.isRemote){super.onUpdate();return;}
        if(portalProxy)return;
        if(portalPending)return;
        if (trainLeader != null && !trainLeader.equals(getUniqueID())) return;
        if(world.getTotalWorldTime()%20==0)RailwayDisplayData.capture(this);
        prevPosX=posX;prevPosY=posY;prevPosZ=posZ;
        if(cursor==null){motionX=motionY=motionZ=0D;return;}
        RailwayNetworkData graph=RailwayNetworkData.get(world);
        List<RailwayCarriageEntity> carriages = null;
        if (trainLeader != null) {
            carriages = trainCarriages();
            if (trainPath == null || carriages == null || !trainPath.usable(world, graph)) {
                haltLoadedCarriages();
                return;
            }
            boolean blocked = false;
            if (previousTrainPath != null) for (RailwayCarriageEntity carriage : carriages) {
                BearingContraptionEntity structure = carriage.body();
                if (carriage.expectedPosition != null
                        && carriage.getPositionVector().squareDistanceTo(carriage.expectedPosition) > 1E-6D
                        || structure != null && structure.isStalled()) { blocked = true; break; }
            }
            if (blocked) {
                trainPath = previousTrainPath; previousTrainPath = null;
                if (previousRoute != null) route = previousRoute;
                speed = 0D;
                placeTrain(graph, carriages, false);
                updateTrainOccupation(graph,carriages);
                return;
            }
        }
        if(expectedPosition!=null&&getPositionVector().squareDistanceTo(expectedPosition)>1E-6D) {
            if(previousCursor!=null)cursor=previousCursor;
            if(previousRoute!=null)route=previousRoute;
            speed=0D;
        }
        Vec3d at=cursor.position(graph);
        World cursorWorld=cursor.world(world);
        if(at==null||cursorWorld==null||!cursorWorld.isBlockLoaded(cursor.track())){speed=0D;motionX=motionY=motionZ=0D;return;}
        if(driver!=null && world.getTotalWorldTime()-lastInput>20){driver=null;driverCarriage=null;controls=0;}
        int direction=((controls&1)!=0?1:0)-((controls&2)!=0?1:0);
        TrackCursor speedCursor = trainPath == null ? cursor : trainPath.at(graph, speed >= 0D ? trainPath.span() : 0D);
        double max=speedCursor!=null&&speedCursor.edge(graph)!=null&&speedCursor.edge(graph).curve()!=null?.525D:1.05D;
        double desired=direction*max;
        if(driver==null&&schedule!=null&&!schedulePaused)immediateInstructions();
        boolean automatic=driver==null&&schedule!=null&&!schedulePaused;
        if(automatic)max*=throttle;
        double remaining=Double.POSITIVE_INFINITY;
        if(automatic) {
            desired=0D;
            if(scheduleWaiting) {
                if(destinationStation!=null&&stationWorld()!=null&&world.getTotalWorldTime()%5==0)
                    com.cogwork.cogworkbridge.content.logistics.port.MailboxNetwork.get(stationWorld()).exchange(stationWorld(),destinationStation,this);
                if(waitRuntime.tick(this,schedule.entries.get(scheduleIndex)))advanceSchedule();
            } else {
                if(route!=null)ticksInTransit=Math.min(Integer.MAX_VALUE-1,ticksInTransit+1);
                if(route==null&&world.getTotalWorldTime()>=nextRouteAttempt) {
                    nextRouteAttempt=world.getTotalWorldTime()+40;
                    plan(graph);
                }
                remaining=routeDistance(graph);
                if(remaining<1E-6D) {
                    if(ticksInTransit>0)transitTimes[scheduleIndex]=transitTimes[scheduleIndex]>0?(transitTimes[scheduleIndex]+ticksInTransit)/2:ticksInTransit;
                    speed=0D;scheduleWaiting=true;waitTicks=0;waitRuntime.reset();
                } else if(Double.isFinite(remaining))
                    desired=automaticDirection*Math.min(max,Math.sqrt(2D*.0075D*remaining));
            }
            if(!hasConductor(automaticDirection==0?1:automaticDirection))desired=0D;
        }
        speed+=Math.max(-.0075D,Math.min(.0075D,desired-speed));
        if(automatic&&!Double.isFinite(remaining))speed=0D;
        if(Math.abs(speed)<1E-5D)speed=0D;
        previousCursor=cursor.copy();
        previousRoute=route==null?null:new ArrayList<>(route);
        int steering=((controls&8)!=0?1:0)-((controls&4)!=0?1:0);
        double requested=automatic&&Double.isFinite(remaining)
                ?Math.copySign(Math.min(Math.abs(speed),remaining),speed):speed;
        if (trainPath != null) {
            previousTrainPath = trainPath.copy();
            double moved = trainPath.advance(world, graph, requested, steering, automatic ? route : null, trainId());
            if (Math.abs(moved-requested)>1E-6D) speed=0D;
            if (!placeTrain(graph, carriages, Math.abs(moved)>1E-8D)) {
                trainPath=previousTrainPath;previousTrainPath=null;speed=0D;
                if(previousRoute!=null)route=previousRoute;
                placeTrain(graph,carriages,false);
            }
            if (Math.abs(moved) <= 1E-8D) previousTrainPath = null;
            updateTrainOccupation(graph,carriages);
            return;
        }
        double moved=cursor.advance(world,graph,requested,steering,automatic?route:null,getUniqueID());
        if(Math.abs(moved-requested)>1E-6D)speed=0D;
        Vec3d next=cursor.position(graph);
        if(next==null){cursor=previousCursor;speed=0D;next=at;}
        next=physicalPosition(cursor,next);
        if(next==null){portalFailed();return;}
        motionX=next.x-posX;motionY=next.y-posY;motionZ=next.z-posZ;
        setPosition(next.x,next.y,next.z);expectedPosition=next;
        if(Math.abs(motionX)+Math.abs(motionY)+Math.abs(motionZ)>1E-8D)velocityChanged=true;
        graph.updateOccupation(this);
    }
    @Override public void setDead() {
        if(!world.isRemote&&!portalProxy&&!portalPending&&trainId().equals(getUniqueID()))RailwayDisplayData.remove(this);
        if(!world.isRemote&&!portalProxy&&world.getMinecraftServer()!=null)for(WorldServer level:world.getMinecraftServer().worlds)RailwayNetworkData.get(level).removeOccupation(getUniqueID());
        super.setDead();
    }
    @Override public boolean canBeCollidedWith(){return false;}
    @Override public boolean processInitialInteract(EntityPlayer player,EnumHand hand){return false;}
    @Override public boolean attackEntityFrom(DamageSource source,float amount){return false;}
    @Override protected void writeEntityToNBT(NBTTagCompound tag){
        super.writeEntityToNBT(tag);if(cursor!=null)tag.setTag("TrackCursor",cursor.write());
        if(bodyId!=null)tag.setUniqueId("CarriageBody",bodyId);
        if(portalPassage!=null)tag.setTag("PortalPassage",portalPassage.write());
        tag.setBoolean("PortalProxy",portalProxy);if(portalBodyId!=null)tag.setUniqueId("PortalBody",portalBodyId);
        if(secondBogeyCursor!=null)tag.setTag("SecondBogeyCursor",secondBogeyCursor.write());
        if (trainLeader != null) {
            tag.setUniqueId("TrainLeader", trainLeader);
            tag.setDouble("TrainOffset", trainOffset);
            tag.setDouble("SecondBogeyOffset", secondBogeyOffset);
            if (trainPath != null) tag.setTag("TrainPath", trainPath.write());
            NBTTagList members = new NBTTagList();
            for (UUID id : trainMembers) {
                NBTTagCompound member = new NBTTagCompound();member.setUniqueId("Id", id);members.appendTag(member);
            }
            tag.setTag("TrainMembers", members);
        }
        if(schedule!=null) {
            tag.setTag("Schedule",schedule.write());tag.setInteger("ScheduleIndex",scheduleIndex);
            tag.setInteger("ScheduleWait",waitTicks);tag.setBoolean("ScheduleWaiting",scheduleWaiting);
            tag.setBoolean("SchedulePaused",schedulePaused);
            tag.setBoolean("ScheduleFinished",scheduleFinished);
            tag.setTag("WaitGroups",waitRuntime.write());
            if(destinationStation!=null)tag.setLong("ScheduleStation",destinationStation.toLong());
            tag.setInteger("ScheduleDimension",destinationDimension);
        }
        tag.setString("RunTitle",runTitle);tag.setDouble("Throttle",throttle);
        tag.setIntArray("TransitTimes",transitTimes);tag.setInteger("TicksInTransit",ticksInTransit);
    }
    @Override protected void readEntityFromNBT(NBTTagCompound tag){
        super.readEntityFromNBT(tag);cursor=tag.hasKey("TrackCursor",10)?TrackCursor.read(tag.getCompoundTag("TrackCursor")):null;
        portalPassage=tag.hasKey("PortalPassage",10)?RailwayPortals.Passage.read(tag.getCompoundTag("PortalPassage")):null;
        portalProxy=tag.getBoolean("PortalProxy");portalBodyId=tag.hasUniqueId("PortalBody")?tag.getUniqueId("PortalBody"):null;
        runTitle=tag.getString("RunTitle");throttle=tag.hasKey("Throttle")?Math.max(0,Math.min(1,tag.getDouble("Throttle"))):1D;
        int[] savedTransits=tag.getIntArray("TransitTimes");java.util.Arrays.fill(transitTimes,-1);
        for(int i=0;i<Math.min(savedTransits.length,transitTimes.length);i++)transitTimes[i]=Math.max(-1,savedTransits[i]);
        ticksInTransit=Math.max(0,tag.getInteger("TicksInTransit"));
        waitRuntime.read(tag.getTagList("WaitGroups",10));
        destinationDimension=tag.hasKey("ScheduleDimension")?tag.getInteger("ScheduleDimension"):world.provider.getDimension();
        bodyId=tag.hasUniqueId("CarriageBody")?tag.getUniqueId("CarriageBody"):null;speed=0D;
        trainLeader=tag.hasUniqueId("TrainLeader")?tag.getUniqueId("TrainLeader"):null;
        trainOffset=tag.getDouble("TrainOffset");
        secondBogeyOffset=tag.hasKey("SecondBogeyOffset")?tag.getDouble("SecondBogeyOffset"):-1D;
        if (!Double.isFinite(trainOffset) || trainOffset < 0D) trainOffset=0D;
        if (!Double.isFinite(secondBogeyOffset)) secondBogeyOffset=-1D;
        trainPath=tag.hasKey("TrainPath",10)?TrainPath.read(tag.getCompoundTag("TrainPath")):null;
        trainMembers.clear();
        NBTTagList members=tag.getTagList("TrainMembers",10);
        for(int i=0;i<members.tagCount()&&i<20;i++) {
            NBTTagCompound member=members.getCompoundTagAt(i);
            if(member.hasUniqueId("Id"))trainMembers.add(member.getUniqueId("Id"));
        }
        secondBogeyCursor=tag.hasKey("SecondBogeyCursor",10)?TrackCursor.read(tag.getCompoundTag("SecondBogeyCursor")):null;previousTrainPath=null;driverCarriage=null;
        schedule=tag.hasKey("Schedule",10)?TrainSchedule.read(tag.getCompoundTag("Schedule")):null;
        if(schedule!=null&&schedule.entries.isEmpty())schedule=null;
        if(schedule!=null) {
            scheduleIndex=Math.max(0,Math.min(schedule.entries.size()-1,tag.getInteger("ScheduleIndex")));
            waitTicks=Math.max(0,Math.min(TrainSchedule.MAX_WAIT,tag.getInteger("ScheduleWait")));
            scheduleWaiting=tag.getBoolean("ScheduleWaiting");schedulePaused=tag.getBoolean("SchedulePaused");
            scheduleFinished=tag.getBoolean("ScheduleFinished");
            destinationStation=tag.hasKey("ScheduleStation")?net.minecraft.util.math.BlockPos.fromLong(tag.getLong("ScheduleStation")):null;
        }
        route=null;destination=null;
    }
    NBTTagList displayPredictions() {
        NBTTagList result=new NBTTagList();if(schedule==null||schedulePaused)return result;
        int time=0;String title=runTitle;
        for(int offset=0;offset<schedule.entries.size();offset++) {
            int index=(scheduleIndex+offset)%schedule.entries.size();if(offset>0&&index==0&&!schedule.cyclic)break;
            var entry=schedule.entries.get(index);
            if(entry.instruction==TrainSchedule.Instruction.RENAME){title=entry.text;continue;}
            if(entry.instruction==TrainSchedule.Instruction.THROTTLE)continue;
            String name=entry.text;
            if(offset==0&&destinationStation!=null&&stationWorld()!=null) {
                for(var station:RailwayNetworkData.get(stationWorld()).targets())if(station.kind().equals("station")&&station.owner().equals(destinationStation)){name=station.name();break;}
                double distance=routeDistance(RailwayNetworkData.get(world));
                time=scheduleWaiting?0:Double.isFinite(distance)?(int)Math.min(Integer.MAX_VALUE/4,distance/Math.max(.05,Math.min(1.05*throttle,.7875))*2):-1;
                if(!scheduleWaiting&&time>=0&&transitTimes[index]>0&&routeStartedDistance>0)
                    time=(time+(int)(transitTimes[index]*Math.min(1,distance/routeStartedDistance)))/2;
            } else {
                if(entry.instruction!=TrainSchedule.Instruction.DESTINATION)continue;
                if(time>=0)time=transitTimes[index]<0?-1:time+transitTimes[index];
            }
            NBTTagCompound p=new NBTTagCompound();p.setString("Destination",name);p.setInteger("Ticks",time);p.setString("Name",trainName());
            String displayTitle=title;
            if(displayTitle.isBlank())for(int j=1;j<schedule.entries.size();j++) {
                var upcoming=schedule.entries.get((index+j)%schedule.entries.size());
                if(upcoming.instruction==TrainSchedule.Instruction.DESTINATION){displayTitle=upcoming.text.replace("*","").trim();break;}
            }
            p.setString("Title",displayTitle);result.appendTag(p);
            int stay=-1;
            for(var group:entry.groups) {int sum=0;boolean known=true;for(var wait:group){if(wait.type!=TrainSchedule.Condition.DELAY){known=false;break;}sum+=wait.value;}if(known){stay=sum;break;}}
            if(stay<0)break;
            if(time>=0)time+=stay;
        }
        return result;
    }
}
