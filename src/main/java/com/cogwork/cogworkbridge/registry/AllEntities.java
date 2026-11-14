package com.cogwork.cogworkbridge.registry;

import com.cogwork.cogworkbridge.content.contraptions.glue.GridPos;
import com.cogwork.cogworkbridge.content.contraptions.glue.SuperGlueEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

@Mod.EventBusSubscriber(modid = "cogworkbridge")
public final class AllEntities {

    private AllEntities() {
    }

    public static GridPos gridPosition(BlockPos pos) {
        return new GridPos(pos.getX(), pos.getY(), pos.getZ());
    }

    @SubscribeEvent
    public static void onRegisterEntities(RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry().register(EntityEntryBuilder.<com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity>create()
                .entity(com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity.class)
                .factory(com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity::new)
                .id("cogworkbridge:railway_carriage",6).name("cogworkbridge.railway_carriage").tracker(128,1,true).build());
        event.getRegistry().register(EntityEntryBuilder.<com.cogwork.cogworkbridge.content.equipment.BlueprintEntity>create()
                .entity(com.cogwork.cogworkbridge.content.equipment.BlueprintEntity.class)
                .factory(com.cogwork.cogworkbridge.content.equipment.BlueprintEntity::new)
                .id("cogworkbridge:crafting_blueprint", 5).name("cogworkbridge.crafting_blueprint").tracker(64, 3, false).build());
        event.getRegistry().register(EntityEntryBuilder.<com.cogwork.cogworkbridge.content.equipment.PotatoProjectileEntity>create()
                .entity(com.cogwork.cogworkbridge.content.equipment.PotatoProjectileEntity.class)
                .factory(com.cogwork.cogworkbridge.content.equipment.PotatoProjectileEntity::new)
                .id("cogworkbridge:potato_projectile", 4).name("cogworkbridge.potato_projectile").tracker(64, 1, true).build());
        event.getRegistry().register(EntityEntryBuilder.<com.cogwork.cogworkbridge.content.decoration.SeatEntity>create()
                .entity(com.cogwork.cogworkbridge.content.decoration.SeatEntity.class)
                .factory(com.cogwork.cogworkbridge.content.decoration.SeatEntity::new)
                .id("cogworkbridge:seat", 3).name("cogworkbridge.seat").tracker(64, 3, true).build());
        event.getRegistry().register(EntityEntryBuilder.<com.cogwork.cogworkbridge.content.logistics.packagebox.PackageEntity>create()
                .entity(com.cogwork.cogworkbridge.content.logistics.packagebox.PackageEntity.class)
                .factory(com.cogwork.cogworkbridge.content.logistics.packagebox.PackageEntity::new)
                .id("cogworkbridge:package", 2).name("cogworkbridge.package").tracker(64, 3, true).build());
        event.getRegistry().register(EntityEntryBuilder.<SuperGlueEntity>create()
                .entity(SuperGlueEntity.class)
                .factory(SuperGlueEntity::new)
                .id("cogworkbridge:super_glue", 0)
                .name("cogworkbridge.super_glue")
                .tracker(64, 10, false)
                .build());
        event.getRegistry().register(EntityEntryBuilder.<BearingContraptionEntity>create()
                .entity(BearingContraptionEntity.class)
                .factory(BearingContraptionEntity::new)
                .id("cogworkbridge:bearing_contraption", 1)
                .name("cogworkbridge.bearing_contraption")
                .tracker(64, 1, false)
                .build());
    }
}
