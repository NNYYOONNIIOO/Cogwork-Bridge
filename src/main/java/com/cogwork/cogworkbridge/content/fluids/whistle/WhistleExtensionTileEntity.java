package com.cogwork.cogworkbridge.content.fluids.whistle;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
public final class WhistleExtensionTileEntity extends CogworkTileEntity {
    private boolean doubled;
    public boolean isDouble(){return doubled;}
    public void setDouble(){doubled=true;markDirty();world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
    public void removeHalf(){doubled=false;markDirty();world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setBoolean("Double",doubled);return n;}
    @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);doubled=n.getBoolean("Double");}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager net,SPacketUpdateTileEntity p){readFromNBT(p.getNbtCompound());}
}
