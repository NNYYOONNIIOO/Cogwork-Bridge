package com.cogwork.cogworkbridge.registry;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class AllSounds {
    public static final SoundEvent SANDING_LONG = new SoundEvent(new ResourceLocation("create", "sanding_long"))
            .setRegistryName(Reference.MOD_ID, "sanding_long");
    public static final SoundEvent DESK_BELL = new SoundEvent(new ResourceLocation("create", "desk_bell"))
            .setRegistryName(Reference.MOD_ID, "desk_bell");
    public static final SoundEvent STOCK_LINK = new SoundEvent(new ResourceLocation("create", "stock_link"))
            .setRegistryName(Reference.MOD_ID, "stock_link");
    public static final SoundEvent CARDBOARD_BONK = new SoundEvent(new ResourceLocation("create", "cardboard_bonk"))
            .setRegistryName(Reference.MOD_ID, "cardboard_bonk");
    public static final SoundEvent HAUNTED_BELL = new SoundEvent(new ResourceLocation("create", "haunted_bell_use"))
            .setRegistryName(Reference.MOD_ID, "haunted_bell_use");
    public static final SoundEvent PECULIAR_BELL = new SoundEvent(new ResourceLocation("create", "peculiar_bell_use"))
            .setRegistryName(Reference.MOD_ID, "peculiar_bell_use");
    public static final SoundEvent HAUNTED_BELL_CONVERT = new SoundEvent(new ResourceLocation("create", "haunted_bell_convert"))
            .setRegistryName(Reference.MOD_ID, "haunted_bell_convert");
    public static final SoundEvent POTATO_FIRE = new SoundEvent(new ResourceLocation("create", "fwoomp"))
            .setRegistryName(Reference.MOD_ID, "potato_fire");
    public static final SoundEvent POTATO_HIT = new SoundEvent(new ResourceLocation("create", "potato_hit"))
            .setRegistryName(Reference.MOD_ID, "potato_hit");
    public static final SoundEvent WHISTLE_HIGH = new SoundEvent(new ResourceLocation("create", "whistle_high")).setRegistryName(Reference.MOD_ID, "whistle_high");
    public static final SoundEvent WHISTLE = new SoundEvent(new ResourceLocation("create", "whistle")).setRegistryName(Reference.MOD_ID, "whistle");
    public static final SoundEvent WHISTLE_LOW = new SoundEvent(new ResourceLocation("create", "whistle_low")).setRegistryName(Reference.MOD_ID, "whistle_low");
    private AllSounds() {}
    @SubscribeEvent public static void register(RegistryEvent.Register<SoundEvent> event) {
        event.getRegistry().register(SANDING_LONG);
        event.getRegistry().register(WHISTLE_HIGH); event.getRegistry().register(WHISTLE); event.getRegistry().register(WHISTLE_LOW);
        event.getRegistry().register(DESK_BELL);
        event.getRegistry().register(STOCK_LINK);
        event.getRegistry().register(CARDBOARD_BONK);
        event.getRegistry().register(HAUNTED_BELL);
        event.getRegistry().register(PECULIAR_BELL);
        event.getRegistry().register(HAUNTED_BELL_CONVERT);
        event.getRegistry().register(POTATO_FIRE);
        event.getRegistry().register(POTATO_HIT);
    }
}
