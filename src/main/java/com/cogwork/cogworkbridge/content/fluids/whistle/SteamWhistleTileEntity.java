package com.cogwork.cogworkbridge.content.fluids.whistle;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;

public final class SteamWhistleTileEntity extends CogworkTileEntity implements ITickable {
    private int size=1,pitch; private boolean sounding, powered;
    private float animation,previousAnimation;
    public int size() { return size; } public int pitch() { return pitch; } public boolean sounding() { return sounding; } public boolean powered() { return powered; }
    public float animation(float partial) { return previousAnimation+(animation-previousAnimation)*partial; }
    public void cycleSize() { size=(size+1)%3;changed(); }
    public void extend() {
        for(int i=1;i<=6;i++) { BlockPos p=pos.up(i);
            if(world.getTileEntity(p) instanceof WhistleExtensionTileEntity e) { if(!e.isDouble()){e.setDouble();changed();return;} continue; }
            if(!world.getBlockState(p).getBlock().isReplaceable(world,p)) return;
            world.setBlockState(p,AllBlocks.STEAM_WHISTLE_EXTENSION.getDefaultState(),3);changed();return;
        }
    }
    public void changed() { if(world==null || world.isRemote)return;markDirty();world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3); }
    @Override public void update() {
        if(world==null)return;
        previousAnimation=animation;
        animation = sounding ? animation + (1F - animation) * .5F : Math.max(0F, animation - .4F);
        if(world.isRemote) { CogworkBridge.proxy.updateWhistleAudio(this); if(sounding && world.getTotalWorldTime()%8==0) world.spawnParticle(net.minecraft.util.EnumParticleTypes.CLOUD,pos.getX()+.5,pos.getY()+.8,pos.getZ()+.5,0,.05,0); return; }
        if(!(world.getBlockState(pos).getBlock() instanceof SteamWhistleBlock))return;
        int p=0; for(int i=1;i<=6;i++) { if(!(world.getTileEntity(pos.up(i)) instanceof WhistleExtensionTileEntity e))break;p+=e.isDouble()?2:1;if(!e.isDouble())break; }
        EnumFacing support=SteamWhistleBlock.attachedDirection(world.getBlockState(pos));
        boolean nextPower=world.isBlockPowered(pos);
        boolean next=nextPower && world.getTileEntity(pos.offset(support)) instanceof FluidTankTileEntity tank && tank.isBoiler() && tank.hasBoilerHeat();
        if(next!=sounding || powered!=nextPower || pitch!=p) { sounding=next;powered=nextPower;pitch=p;changed(); }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n) { super.writeToNBT(n);n.setInteger("Size",size);n.setInteger("Pitch",pitch);n.setBoolean("Sounding",sounding);n.setBoolean("Powered",powered);return n; }
    @Override public void readFromNBT(NBTTagCompound n) { super.readFromNBT(n);size=Math.floorMod(n.getInteger("Size"),3);pitch=n.getInteger("Pitch");sounding=n.getBoolean("Sounding");powered=n.getBoolean("Powered"); }
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager net,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
}
