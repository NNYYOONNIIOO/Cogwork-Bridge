package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.ITickable;
import net.minecraftforge.items.ItemHandlerHelper;
import java.util.UUID;

public final class LecternControllerTileEntity extends CogworkTileEntity implements ITickable {
    private ItemStack controller=ItemStack.EMPTY;private int originalMeta;private UUID user;
    public ItemStack controller(){return controller;} public boolean usedBy(EntityPlayer p){return p!=null && p.getUniqueID().equals(user);}     public void setup(ItemStack stack,int meta){controller=stack.copy();controller.setCount(1);originalMeta=meta;changed();}
    public void swap(EntityPlayer p,net.minecraft.util.EnumHand hand){ItemStack next=p.getHeldItem(hand).copy();next.setCount(1);ItemStack previous=controller;controller=next;if(!p.capabilities.isCreativeMode)p.getHeldItem(hand).shrink(1);if(!previous.isEmpty())ItemHandlerHelper.giveItemToPlayer(p,previous);changed();}
    public boolean inRange(EntityPlayer p){return p!=null && !p.isDead && !p.isSpectator() && p.getPositionEyes(1F).squareDistanceTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<4D;}
    public void start(EntityPlayer p){if(world.isRemote||user!=null||controller.isEmpty()||!inRange(p))return;user=p.getUniqueID();changed();}
    public void stop(EntityPlayer p){if(user==null || p!=null && !usedBy(p))return;RedstoneLinkNetwork.clearRemote(world,user);user=null;changed();}
    public void restoreLectern(){Block b=LecternControllerBlock.lectern();if(b!=null && b!=net.minecraft.init.Blocks.AIR)world.setBlockState(pos,b.getStateFromMeta(originalMeta),3);}
    public void dropController(){stop(null);if(!controller.isEmpty()){Block.spawnAsEntity(world,pos,controller);controller=ItemStack.EMPTY;markDirty();}}
    private void changed(){markDirty();if(world!=null && !world.isRemote)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
    @Override public void update(){if(world==null)return;if(world.isRemote){CogworkBridge.proxy.updateLecternControl(this);return;}if(user!=null){EntityPlayer p=world.getPlayerEntityByUUID(user);if(!inRange(p))stop(null);}}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);if(!controller.isEmpty())n.setTag("Controller",controller.writeToNBT(new NBTTagCompound()));n.setInteger("LecternMeta",originalMeta);if(user!=null)n.setUniqueId("User",user);return n;}
    @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);controller=n.hasKey("Controller",10)?new ItemStack(n.getCompoundTag("Controller")):ItemStack.EMPTY;originalMeta=n.getInteger("LecternMeta");user=n.hasUniqueId("User")?n.getUniqueId("User"):null;}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager net,SPacketUpdateTileEntity p){readFromNBT(p.getNbtCompound());}
    @Override public void invalidate(){if(world!=null&&!world.isRemote&&user!=null)RedstoneLinkNetwork.clearRemote(world,user);super.invalidate();}
}
