package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleTileEntity;
import com.cogwork.cogworkbridge.registry.AllSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.util.SoundCategory;

import java.util.Map;
import java.util.WeakHashMap;

public final class WhistleAudio {
    private static final Map<SteamWhistleTileEntity, Loop> LOOPS = new WeakHashMap<>();

    private WhistleAudio() { }

    public static void update(SteamWhistleTileEntity tile) {
        Loop loop = LOOPS.get(tile);
        if (!tile.sounding()) {
            if (loop != null) loop.fadeOut();
            LOOPS.remove(tile);
            return;
        }

        SoundHandler sounds = Minecraft.getMinecraft().getSoundHandler();
        long now = tile.getWorld().getTotalWorldTime();
        boolean needsRetry = loop != null && now - loop.startedAt > 20 && !sounds.isSoundPlaying(loop);
        if (loop == null || loop.done || loop.octave != tile.size() || needsRetry) {
            if (loop != null) loop.fadeOut();
            loop = new Loop(tile, now);
            LOOPS.put(tile, loop);
            sounds.playSound(loop);
        }
        loop.keepAlive = 2;
        loop.pitchValue = (float) Math.pow(2, -tile.pitch() / 12D);
    }

    private static final class Loop extends MovingSound {
        private final SteamWhistleTileEntity source;
        private final int octave;
        private final long startedAt;
        private boolean active = true, done;
        private int keepAlive = 2;
        private float pitchValue;

        private Loop(SteamWhistleTileEntity tile, long startedAt) {
            super(tile.size() == 0 ? AllSounds.WHISTLE_HIGH
                    : tile.size() == 1 ? AllSounds.WHISTLE : AllSounds.WHISTLE_LOW, SoundCategory.BLOCKS);
            source = tile;
            octave = tile.size();
            this.startedAt = startedAt;
            repeat = true;
            repeatDelay = 0;
            // 1.12 rejects a sound with zero volume before its first update.
            volume = .05F;
            pitch = pitchValue = (float) Math.pow(2, -tile.pitch() / 12D);
            xPosF = tile.getPos().getX() + .5F;
            yPosF = tile.getPos().getY() + .5F;
            zPosF = tile.getPos().getZ() + .5F;
        }

        private void fadeOut() { active = false; }

        @Override public void update() {
            if (--keepAlive <= 0 || source.isInvalid() || source.getWorld() == null
                    || Minecraft.getMinecraft().world != source.getWorld() || !source.sounding()) active = false;
            pitch = pitchValue;
            volume = Math.max(0F, Math.min(1F, volume + (active ? .25F : -.25F)));
            if (!active && volume == 0F) {
                donePlaying = true;
                done = true;
                if (LOOPS.get(source) == this) LOOPS.remove(source);
            }
        }
    }
}
