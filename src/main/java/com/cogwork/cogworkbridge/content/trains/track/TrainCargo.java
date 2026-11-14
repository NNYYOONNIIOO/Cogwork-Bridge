package com.cogwork.cogworkbridge.content.trains.track;

import com.cogwork.cogworkbridge.content.logistics.transport.ContraptionItemStorage;
import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.*;
import java.util.*;

public final class TrainCargo {
    private TrainCargo() { }
    public static List<IItemHandler> items(RailwayCarriageEntity train) {
        List<IItemHandler> result=new ArrayList<>();var cars=train.trainCarriages();
        if(cars!=null)for(var car:cars)if(car.body()!=null)result.add(new ContraptionItemStorage(car.body()));return result;
    }
    public static List<IFluidHandler> fluids(RailwayCarriageEntity train) {
        List<IFluidHandler> result=new ArrayList<>();Set<IFluidHandler> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        var cars=train.trainCarriages();if(cars==null)return result;
        for(var car:cars) {
            var body=car.body();if(body==null||body.getContraption()==null||body.getContraptionLocalWorld()==null)continue;
            for(var snapshot:body.getContraption().blocks().values()) {
                var tile=body.getContraptionLocalWorld().getTileEntity(body.localPos(snapshot));if(tile==null)continue;
                if(tile instanceof com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity tank&&!tank.isController())continue;
                IFluidHandler handler=tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,null);
                if(handler!=null&&seen.add(handler))result.add(handler);
            }
        }return result;
    }
    public static long countItems(RailwayCarriageEntity train,ItemStack filter,boolean stacks) {
        long total=0;for(IItemHandler inv:items(train))for(int i=0;i<inv.getSlots();i++) {
            ItemStack s=inv.getStackInSlot(i);if(s.isEmpty()||!ListFilterItem.matchesItem(filter,s))continue;
            total+=stacks?(s.getCount()==s.getMaxStackSize()?1:0):s.getCount();
        }return total;
    }
    public static boolean hasPackages(RailwayCarriageEntity train) {
        for(var inv:items(train))for(int slot=0;slot<inv.getSlots();slot++)
            if(com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem.isPackage(inv.getStackInSlot(slot)))return true;
        return false;
    }
    public static long countFluids(RailwayCarriageEntity train,ItemStack filter) {
        long total=0;for(IFluidHandler h:fluids(train))for(var tank:h.getTankProperties()) {
            FluidStack fluid=tank.getContents();if(fluid!=null&&ListFilterItem.matchesFluid(filter,fluid))total+=fluid.amount;
        }return total;
    }
    public static long fingerprint(RailwayCarriageEntity train) {
        long hash=1;for(IItemHandler inv:items(train))for(int i=0;i<inv.getSlots();i++)hash=hash*31+inv.getStackInSlot(i).serializeNBT().hashCode();
        for(IFluidHandler h:fluids(train))for(var tank:h.getTankProperties()) { FluidStack f=tank.getContents();hash=hash*31+(f==null?0:f.writeToNBT(new net.minecraft.nbt.NBTTagCompound()).hashCode()); }return hash;
    }
    public static int players(RailwayCarriageEntity train) {
        Set<java.util.UUID> players=new HashSet<>();var cars=train.trainCarriages();if(cars==null)return 0;
        for(var original:cars)for(var car:original.dimensionalCopies())if(car.body()!=null)
            for(var body:car.body().railwayPortalParts())for(var entity:car.world.loadedEntityList)
                if(entity instanceof com.cogwork.cogworkbridge.content.decoration.SeatEntity seat&&seat.belongsTo(body))
                    for(var passenger:seat.getPassengers())if(passenger instanceof net.minecraft.entity.player.EntityPlayer)players.add(passenger.getUniqueID());
        return players.size();
    }
}
