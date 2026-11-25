package com.cogwork.cogworkbridge.content.contraptions.bearing;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Use the server UUID index, or a client entity ID once its spawn packet has arrived. */
public final class ContraptionEntityReference {
    private World context;
    private UUID target;
    private int entityId=-1;
    private long lastSearch=Long.MIN_VALUE;

    @Nullable public BearingContraptionEntity get(@Nullable World world,@Nullable UUID id) {
        if(context!=world||!Objects.equals(target,id)) {
            context=world;target=id;entityId=-1;lastSearch=Long.MIN_VALUE;
        }
        if(world==null||id==null)return null;
        if(world instanceof WorldServer server) {
            Entity entity=server.getEntityFromUuid(id);
            return entity instanceof BearingContraptionEntity contraption&&!contraption.isDead?contraption:null;
        }
        if(entityId!=-1) {
            Entity entity=world.getEntityByID(entityId);
            if(entity instanceof BearingContraptionEntity contraption&&!contraption.isDead&&id.equals(entity.getUniqueID()))return contraption;
            entityId=-1;lastSearch=Long.MIN_VALUE;
        }
        long now=world.getTotalWorldTime();
        if(lastSearch!=Long.MIN_VALUE&&now>=lastSearch&&now-lastSearch<5)return null;
        lastSearch=now;
        for(Entity entity:world.loadedEntityList)
            if(entity instanceof BearingContraptionEntity contraption&&!contraption.isDead&&id.equals(entity.getUniqueID())) {
                entityId=entity.getEntityId();return contraption;
            }
        return null;
    }
}
