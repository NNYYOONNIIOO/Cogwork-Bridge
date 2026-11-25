package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import javax.annotation.Nullable;

/** Persistent target shared by stations, signals and observers. */
public abstract class RailwayTargetTile extends TileEntity {
    @Nullable private BlockPos target;
    private boolean front;
    protected abstract String kind();
    protected String targetName() { return ""; }
    @Nullable public BlockPos target() { return target; }
    public boolean front() { return front; }
    public boolean hasValidTarget() {
        return world!=null && target!=null && world.isBlockLoaded(target)
                && world.getBlockState(target).getBlock() instanceof TrackBlock
                && !world.getBlockState(target).getValue(TrackBlock.SHAPE).junction();
    }
    public void bind(BlockPos target,boolean front) {
        this.target=target.toImmutable();this.front=front;changed();
    }
    protected void changed() {
        markDirty();
        if(world==null || world.isRemote)return;
        if(target==null)RailwayNetworkData.get(world).removeTarget(pos);
        else RailwayNetworkData.get(world).setTarget(pos,target,kind(),front,targetName());
        world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);
    }
    public void removeFromNetwork() {
        if(world!=null&&!world.isRemote)RailwayNetworkData.get(world).removeTarget(pos);
    }
    @Override public void onLoad() {
        super.onLoad();
        if(world!=null&&!world.isRemote && target!=null)
            RailwayNetworkData.get(world).setTarget(pos,target,kind(),front,targetName());
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if(target!=null){tag.setLong("TargetTrack",target.toLong());tag.setBoolean("TargetFront",front);}
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);target=tag.hasKey("TargetTrack")?BlockPos.fromLong(tag.getLong("TargetTrack")):null;
        front=tag.getBoolean("TargetFront");
    }
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager network,SPacketUpdateTileEntity packet){
        readFromNBT(packet.getNbtCompound());
        if(world!=null&&world.isRemote)world.markBlockRangeForRenderUpdate(pos,pos);
    }

    public static final class Station extends RailwayTargetTile implements net.minecraft.util.ITickable {
        private String name="Track Station";
        private boolean assembling;
        private boolean trainPresent;
        @Override protected String kind(){return "station";}
        @Override protected String targetName(){return name;}
        public String name(){return name;}
        public boolean assembling(){return assembling;}
        public boolean trainPresent(){return trainPresent;}
        @Nullable public RailwayCarriageEntity stoppedTrain() {
            if(!hasValidTarget())return null;
            TrackShape shape=world.getBlockState(target()).getValue(TrackBlock.SHAPE);
            if(shape!=TrackShape.XO&&shape!=TrackShape.ZO)return null;
            int sign=front()?1:-1;BlockPos rail=target().add(shape.x*sign,0,shape.z*sign);
            net.minecraft.util.math.Vec3d center=new net.minecraft.util.math.Vec3d(rail.getX()+.5D,rail.getY(),rail.getZ()+.5D);
            RailwayNetworkData graph=RailwayNetworkData.get(world);
            for(java.util.UUID id:graph.carriagesAt(rail)) {
                RailwayCarriageEntity carriage=TrainDimensionTransfer.loaded(world,id);
                if(carriage!=null&&Math.abs(carriage.speed())<.005D
                        && carriage.hasBogeyAt(center) && carriage.body()!=null) {
                    RailwayCarriageEntity leader=carriage.trainController();
                    if(leader!=null&&leader.trainCarriages()!=null)return leader;
                }
            }
            return null;
        }
        @Override public void update() {
            if(world==null||world.isRemote||(world.getTotalWorldTime()+pos.hashCode())%5!=0)return;
            RailwayCarriageEntity train=stoppedTrain();boolean present=train!=null;
            if(train!=null)com.cogwork.cogworkbridge.content.logistics.port.MailboxNetwork.get(world).exchange(world,pos,train);
            if(trainPresent==present)return;
            trainPresent=present;changed();world.updateComparatorOutputLevel(pos,getBlockType());
        }
        public boolean disassemble(net.minecraft.entity.player.EntityPlayer player) {
            RailwayCarriageEntity train=stoppedTrain();
            if(train==null)return false;
            java.util.List<RailwayCarriageEntity> carriages=train.trainCarriages();
            if(carriages==null){message(player,"carriages_unavailable");return false;}
            java.util.Set<BlockPos> claimed=new java.util.HashSet<>();
            for(RailwayCarriageEntity carriage:carriages) {
                var body=carriage.body();
                if(body==null||!body.canRestoreMounted(claimed)){message(player,"disassembly_blocked");return false;}
            }
            for(RailwayCarriageEntity carriage:carriages) {
                var body=carriage.body();body.restoreAndDiscard();
                if(!body.isDead){message(player,"disassembly_blocked");return false;}
            }
            net.minecraft.item.ItemStack schedule=train.removeSchedule();
            if(!schedule.isEmpty())net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(player,schedule);
            for(RailwayCarriageEntity carriage:carriages)carriage.setDead();
            trainPresent=false;changed();return true;
        }
        /** Straight tracks available for bogey placement while assembling. */
        public java.util.List<BlockPos> assemblyTracks() {
            java.util.List<BlockPos> result=new java.util.ArrayList<>();
            if(!assembling||!hasValidTarget()||target()==null)return result;
            TrackShape shape=world.getBlockState(target()).getValue(TrackBlock.SHAPE);
            if(shape!=TrackShape.XO&&shape!=TrackShape.ZO)return result;
            int sign=front()?1:-1,dx=shape.x*sign,dz=shape.z*sign;
            for(int i=1;i<128;i++) {
                BlockPos rail=target().add(dx*i,0,dz*i);
                if(!world.isBlockLoaded(rail)||!(world.getBlockState(rail).getBlock() instanceof TrackBlock)
                        ||world.getBlockState(rail).getValue(TrackBlock.SHAPE)!=shape)break;
                result.add(rail);
            }
            return result;
        }
        /** Use the same three-block bogey spacing rule for highlighting and clicks. */
        public java.util.List<BlockPos> assemblySlots() {
            java.util.List<BlockPos> tracks=assemblyTracks(),result=new java.util.ArrayList<>();
            java.util.List<Integer> occupied=new java.util.ArrayList<>();
            for(int i=0;i<tracks.size();i++)
                if(world.getBlockState(tracks.get(i).up()).getBlock() instanceof BogeyBlock)occupied.add(i);
            for(int i=0;i<tracks.size();i++) {
                if(occupied.isEmpty()&&i!=0||occupied.size()>=20)continue;
                boolean near=false;
                for(int existing:occupied)if(Math.abs(existing-i)<3){near=true;break;}
                if(!near)result.add(tracks.get(i));
            }
            return result;
        }
        /** The assembly area follows the straight track away from the selected station point. */
        public boolean tryBogey(net.minecraft.entity.player.EntityPlayer player,net.minecraft.util.EnumHand hand,BlockPos clicked) {
            if(!assembling||!hasValidTarget()||target()==null)return false;
            TrackShape shape=world.getBlockState(target()).getValue(TrackBlock.SHAPE);
            if(shape!=TrackShape.XO&&shape!=TrackShape.ZO)return false;
            int sign=front()?1:-1;
            int dx=shape.x*sign,dz=shape.z*sign;
            if(!assemblyTracks().contains(clicked))return false;
            if(!assemblySlots().contains(clicked)) {
                for(int offset=-1;offset<=1;offset++) {
                    BlockPos bogeyPos=clicked.add(dx*offset,1,dz*offset);
                    if(!(world.getBlockState(bogeyPos).getBlock() instanceof BogeyBlock old))continue;
                    if(!world.isRemote&&player.canPlayerEdit(bogeyPos,net.minecraft.util.EnumFacing.UP,player.getHeldItem(hand))) {
                        BogeyBlock next=old.isLarge()?com.cogwork.cogworkbridge.registry.AllBlocks.SMALL_BOGEY
                                :com.cogwork.cogworkbridge.registry.AllBlocks.LARGE_BOGEY;
                        world.setBlockState(bogeyPos,next.getDefaultState().withProperty(BogeyBlock.AXIS,
                                shape==TrackShape.XO?net.minecraft.util.EnumFacing.Axis.X:net.minecraft.util.EnumFacing.Axis.Z),3);
                    }
                    return true;
                }
                return false;
            }
            BlockPos bogeyPos=clicked.up();
            net.minecraft.item.ItemStack held=player.getHeldItem(hand);
            if(!player.capabilities.isCreativeMode && held.getItem()!=net.minecraft.item.Item.getItemFromBlock(
                    com.cogwork.cogworkbridge.registry.BlockFamily.get("railway_casing"))) {
                if(!world.isRemote)message(player,"requires_casing");return false;
            }
            if(!player.canPlayerEdit(bogeyPos,net.minecraft.util.EnumFacing.UP,held)
                    ||world.getBlockState(bogeyPos).getBlockHardness(world,bogeyPos)<0)return false;
            if(!world.isRemote) {
                net.minecraft.block.state.IBlockState bogey=com.cogwork.cogworkbridge.registry.AllBlocks.SMALL_BOGEY.getDefaultState()
                        .withProperty(BogeyBlock.AXIS,shape==TrackShape.XO?net.minecraft.util.EnumFacing.Axis.X:net.minecraft.util.EnumFacing.Axis.Z);
                if(!world.isAirBlock(bogeyPos))world.destroyBlock(bogeyPos,true);
                if(world.setBlockState(bogeyPos,bogey,3)&&!player.capabilities.isCreativeMode)held.shrink(1);
            }
            return true;
        }
        public void configure(String name,boolean assembling) {
            String next=name==null?"":name.trim();
            if(next.length()>25)next=next.substring(0,25);
            this.name=next;
            if(assembling && hasValidTarget()) {
                TrackShape shape=world.getBlockState(target()).getValue(TrackBlock.SHAPE);
                this.assembling=shape==TrackShape.XO||shape==TrackShape.ZO;
            } else this.assembling=false;
            changed();
        }
        public boolean assemble(net.minecraft.entity.player.EntityPlayerMP player) {
            if(world==null||world.isRemote)return false;
            if(!assembling||!hasValidTarget()){message(player,"invalid_station");return false;}
            TrackShape shape=world.getBlockState(target()).getValue(TrackBlock.SHAPE);
            if(shape!=TrackShape.XO&&shape!=TrackShape.ZO)return false;
            int sign=front()?1:-1,dx=shape.x*sign,dz=shape.z*sign;
            java.util.List<BlockPos> bogeys=new java.util.ArrayList<>();
            java.util.List<BlockPos> rails=assemblyTracks();
            for(BlockPos rail:rails) {
                if(world.getBlockState(rail.up()).getBlock() instanceof BogeyBlock)bogeys.add(rail.up());
            }
            if(bogeys.isEmpty()||!bogeys.get(0).equals(target().add(dx,1,dz))) {
                message(player,"no_bogey");return false;
            }
            if(bogeys.size()>20){message(player,"too_many_bogeys");return false;}
            for(int i=0;i<bogeys.size();i++) {
                if(world.getBlockState(bogeys.get(i)).getValue(BogeyBlock.AXIS)
                        !=(shape==TrackShape.XO?net.minecraft.util.EnumFacing.Axis.X:net.minecraft.util.EnumFacing.Axis.Z)) {
                    message(player,"bogey_axis");return false;
                }
                if(i>0&&bogeys.get(i).distanceSq(bogeys.get(i-1))<9D){message(player,"bogeys_too_close");return false;}
            }
            record CarriagePlan(BlockPos anchor,BlockPos second,
                    com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraption structure) { }
            java.util.List<CarriagePlan> plans=new java.util.ArrayList<>();
            java.util.Set<BlockPos> claimed=new java.util.HashSet<>();
            boolean hasControls=false;
            for(int i=0;i<bogeys.size();) {
                BlockPos anchor=bogeys.get(i);
                var structure=com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraption.captureMounted(
                        world,anchor.down(),anchor,positions->{});
                if(structure==null||structure.isEmpty()){message(player,"capture_failed");return false;}
                if(structure.blocks().size()<=1){message(player,"nothing_attached");return false;}
                for(BlockPos position:structure.blocks().keySet())
                    if(!claimed.add(position)){message(player,"bogey_order");return false;}
                java.util.List<BlockPos> connected=new java.util.ArrayList<>();
                for(var snapshot:structure.blocks().values()) {
                    var state=snapshot.state();
                    if(state.getBlock() instanceof BogeyBlock)connected.add(snapshot.pos());
                    if(!(state.getBlock() instanceof TrainControlsBlock))continue;
                    net.minecraft.util.EnumFacing facing=state.getValue(TrainControlsBlock.FACING);
                    if(facing.getAxis()!=(shape==TrackShape.XO?net.minecraft.util.EnumFacing.Axis.X:net.minecraft.util.EnumFacing.Axis.Z)) {
                        message(player,"sideways_controls");return false;
                    }
                    if(facing.getXOffset()==dx&&facing.getZOffset()==dz)hasControls=true;
                }
                if(connected.size()<1||connected.size()>2||!connected.contains(anchor)) {
                    message(player,"bogey_order");return false;
                }
                BlockPos second=null;
                if(connected.size()==2) {
                    if(i+1>=bogeys.size()||!connected.contains(bogeys.get(i+1))) {
                        message(player,"bogey_order");return false;
                    }
                    second=bogeys.get(++i);
                }
                plans.add(new CarriagePlan(anchor,second,structure));
                i++;
            }
            if(!hasControls){message(player,"no_controls");return false;}
            RailwayNetworkData graph=RailwayNetworkData.get(world);
            for(BlockPos rail:rails)graph.update(world,rail);
            TrackCursor first=TrackCursor.atTrack(graph,bogeys.get(0).down(),shape,front());
            if(first==null){message(player,"no_track");return false;}
            BlockPos origin=bogeys.get(0),last=bogeys.get(bogeys.size()-1);
            double span=(last.getX()-origin.getX())*dx+(last.getZ()-origin.getZ())*dz;
            TrainPath path=TrainPath.create(world,graph,first,span);
            if(path==null){message(player,"no_track");return false;}
            java.util.List<RailwayCarriageEntity> carriages=new java.util.ArrayList<>();
            java.util.List<com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity> bodies=new java.util.ArrayList<>();
            java.util.List<com.cogwork.cogworkbridge.content.contraptions.bearing.CarriedBearingAssembly> children=new java.util.ArrayList<>();
            java.util.List<Double> offsets=new java.util.ArrayList<>(),secondOffsets=new java.util.ArrayList<>();
            java.util.Set<BlockPos> allCarried=new java.util.HashSet<>();
            for(CarriagePlan plan:plans) {
                var carried=com.cogwork.cogworkbridge.content.contraptions.bearing.CarriedBearingAssembly.prepare(world,plan.structure(),
                        positions->com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionGuiProxy.closeCapturedBlock(
                                world.getMinecraftServer(),positions,world.provider.getDimension()));
                for(BlockPos position:carried.claimedPositions())
                    if(!allCarried.add(position)){message(player,"bogey_order");return false;}
                children.add(carried);
            }
            for(CarriagePlan plan:plans) {
                TrackCursor cursor=TrackCursor.atTrack(graph,plan.anchor().down(),shape,front());
                if(cursor==null){discardAssembly(carriages,bodies);message(player,"no_track");return false;}
                RailwayCarriageEntity carriage=new RailwayCarriageEntity(world);carriage.initialize(cursor);
                if(!world.spawnEntity(carriage)){discardAssembly(carriages,bodies);message(player,"capture_failed");return false;}
                carriages.add(carriage);
                var structure=plan.structure();
                var body=com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity.createMounted(
                        world,plan.anchor(),structure,carriage,null,0,shape==TrackShape.XO);
                bodies.add(body);carriage.attach(body);body.prepareNestedChildren();
                if(!world.spawnEntity(body)){discardAssembly(carriages,bodies);message(player,"capture_failed");return false;}
                offsets.add((double)((plan.anchor().getX()-origin.getX())*dx+(plan.anchor().getZ()-origin.getZ())*dz));
                secondOffsets.add(plan.second()==null?-1D:(double)((plan.second().getX()-origin.getX())*dx
                        +(plan.second().getZ()-origin.getZ())*dz));
            }
            RailwayCarriageEntity.configureTrain(carriages,path,offsets,secondOffsets);
            com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "train");
            if (carriages.size() >= 6) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "long_train");
            for (var plan : plans) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.assembled(world, pos, plan.structure());
            for(int i=0;i<plans.size();i++) {
                var structure=plans.get(i).structure();var body=bodies.get(i);
                com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionGuiProxy.closeCapturedBlock(
                        world.getMinecraftServer(),structure.blocks().keySet(),world.provider.getDimension());
                com.cogwork.cogworkbridge.content.decoration.SeatBlock.transferOccupants(body,structure);
                children.get(i).removeFromWorld();structure.removeFromWorld(world,true);
                body.sendSnapshotToTrackingPlayers();body.sendNestedSnapshots();
            }
            assembling=false;changed();return true;
        }
        private static void discardAssembly(java.util.List<RailwayCarriageEntity> carriages,
                java.util.List<com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity> bodies) {
            for(var body:bodies)body.discardWithoutRestore();
            for(var carriage:carriages)carriage.setDead();
        }
        private static void message(net.minecraft.entity.player.EntityPlayer player,String reason) {
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("cogworkbridge.train."+reason),true);
        }
        @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
            super.writeToNBT(tag);tag.setString("StationName",name);tag.setBoolean("Assembling",assembling);tag.setBoolean("TrainPresent",trainPresent);return tag;
        }
        @Override public void readFromNBT(NBTTagCompound tag) {
            super.readFromNBT(tag);name=tag.hasKey("StationName")?tag.getString("StationName"):"Track Station";assembling=tag.getBoolean("Assembling");trainPresent=tag.getBoolean("TrainPresent");
        }
    }
    public static final class Signal extends RailwayTargetTile implements net.minecraft.util.ITickable {
        private boolean crossSignal;
        private int signalState;
        @Override protected String kind(){return "signal";}
        public boolean crossSignal(){return crossSignal;}
        public boolean isRed(){return signalState==1;}
        public boolean isGreen(){return signalState==2;}
        @Override public void update(){
            if(world==null||world.isRemote||(world.getTotalWorldTime()+pos.hashCode())%5!=0)return;
            int state=!hasValidTarget()?0:world.isBlockPowered(pos)
                    ||RailwayNetworkData.get(world).signalBlocked(world,target(),front(),crossSignal)?1:2;
            if(state!=signalState){signalState=state;changed();world.updateComparatorOutputLevel(pos,getBlockType());}
        }
        public void cycle(){crossSignal=!crossSignal;changed();}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setBoolean("CrossSignal",crossSignal);tag.setInteger("SignalState",signalState);return tag;}
        @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);crossSignal=tag.getBoolean("CrossSignal");signalState=tag.getInteger("SignalState");}
    }
    public static final class Observer extends RailwayTargetTile implements net.minecraft.util.ITickable {
        private net.minecraft.item.ItemStack filter=net.minecraft.item.ItemStack.EMPTY;
        private int poweredTicks;
        private String observedTrainName="";
        public String observedTrainName() { return observedTrainName; }
        @Override protected String kind(){return "observer";}
        public boolean powered(){return poweredTicks>0;}
        public net.minecraft.item.ItemStack filter(){return filter.copy();}
        public void setFilter(net.minecraft.entity.player.EntityPlayer player,net.minecraft.util.EnumHand hand){
            net.minecraft.item.ItemStack held=player.getHeldItem(hand),old=filter;
            filter=held.isEmpty()?net.minecraft.item.ItemStack.EMPTY:held.copy();if(!filter.isEmpty())filter.setCount(1);
            if(!player.capabilities.isCreativeMode&&com.cogwork.cogworkbridge.content.fluids.ListFilterItem.isFilterItem(held))held.shrink(1);
            if(com.cogwork.cogworkbridge.content.fluids.ListFilterItem.isFilterItem(old))
                net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(player,old);
            changed();
        }
        @Override public void update(){
            if(world==null||world.isRemote)return;
            boolean was=poweredTicks>0;
            boolean matching=false;
            if(hasValidTarget()) {
                RailwayNetworkData graph=RailwayNetworkData.get(world);
                for(java.util.UUID id:graph.carriagesAt(target())) {
                    RailwayCarriageEntity carriage=TrainDimensionTransfer.loaded(world,id);
                    if(carriage!=null&&carriage.hasCargo(filter)){matching=true;
                        var leader=carriage.trainController();String name=leader==null?"":leader.trainName();
                        if(!name.equals(observedTrainName)){observedTrainName=name;changed();}break;}
                }
            }
            if(matching)poweredTicks=8;else if(poweredTicks>0)poweredTicks--;
            if(was!=powered()){
                changed();world.notifyNeighborsOfStateChange(pos,getBlockType(),true);
            }
        }
        @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){
            super.writeToNBT(tag);tag.setTag("Filter",filter.serializeNBT());tag.setInteger("PowerTicks",poweredTicks);tag.setString("ObservedTrainName",observedTrainName);return tag;
        }
        @Override public void readFromNBT(NBTTagCompound tag){
            super.readFromNBT(tag);filter=tag.hasKey("Filter",10)?new net.minecraft.item.ItemStack(tag.getCompoundTag("Filter")):net.minecraft.item.ItemStack.EMPTY;
            poweredTicks=Math.max(0,Math.min(8,tag.getInteger("PowerTicks")));
            observedTrainName=tag.getString("ObservedTrainName");
        }
    }
}
