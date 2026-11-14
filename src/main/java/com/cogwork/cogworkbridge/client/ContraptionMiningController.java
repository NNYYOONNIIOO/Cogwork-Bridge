package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import net.minecraft.block.SoundType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;

/** Tracks mining of the selected local block independently of vanilla world mining. */
public final class ContraptionMiningController {

    private static int soundTicks;
    private static int hitDelay;

    private ContraptionMiningController() {
    }

    public static boolean click(PlayerControllerMP controller) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null || controller == null) {
            return false;
        }
        stop();
        player.swingArm(EnumHand.MAIN_HAND);
        boolean immediate = controller.getCurrentGameType() == GameType.CREATIVE
                || controller.getCurrentGameType() == GameType.SPECTATOR;
        if (immediate) {
            hitDelay = 5;
        }
        return immediate;
    }

    public static void damage(ContraptionMiningSession session,
                              BearingContraptionEntity target,
                              BlockPos dynamicPos) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null || target == null) {
            return;
        }
        playHitSound(player, session, target, dynamicPos);
        soundTicks++;
        player.swingArm(EnumHand.MAIN_HAND);
    }

    public static void completed() {
        stop();
        hitDelay = 5;
    }

    public static boolean waitForNextBlock() {
        if (hitDelay <= 0) {
            return false;
        }
        hitDelay--;
        return true;
    }

    public static void stop() {
        soundTicks = 0;
    }

    public static void reset() {
        stop();
        hitDelay = 0;
    }

    private static void playHitSound(EntityPlayerSP player,
                                     ContraptionMiningSession session,
                                     BearingContraptionEntity target,
                                     BlockPos dynamicPos) {
        if (soundTicks % 4 != 0) {
            return;
        }
        SoundType sound = session.snapshot().state().getBlock()
                .getSoundType(session.snapshot().state(),
                        target.getInteractionWorld(), session.localPos(), player);
        player.world.playSound(null, dynamicPos, sound.getHitSound(),
                SoundCategory.NEUTRAL, (sound.getVolume() + 1.0F) / 8.0F,
                sound.getPitch() * 0.5F);
    }
}
