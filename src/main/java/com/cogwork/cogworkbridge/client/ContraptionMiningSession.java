package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Client-only target information for one contraption block.
 */
public record ContraptionMiningSession(
        int sequence,
        int entityId,
        BlockPos localPos,
        EnumFacing side,
        float interactionAngle,
        Vec3d worldHit,
        net.minecraft.item.ItemStack tool,
        BearingContraption.Snapshot snapshot) {

    private static int nextSequence;

    public static ContraptionMiningSession from(
            ContraptionInteractionClientEvents.ContraptionHit hit) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.player;
        return new ContraptionMiningSession(
                ++nextSequence,
                hit.entity().getEntityId(),
                hit.hit().localPos().toImmutable(),
                hit.hit().side(),
                (float) hit.entity().getPartialAngle(
                        minecraft.getRenderPartialTicks()),
                hit.hit().worldHit(),
                player.getHeldItemMainhand().copy(),
                hit.hit().snapshot());
    }

    public boolean matches(ContraptionInteractionClientEvents.ContraptionHit hit) {
        if (hit == null) {
            return false;
        }
        return entityId == hit.entity().getEntityId()
                && localPos.equals(hit.hit().localPos())
                && snapshot.state().getBlock()
                == hit.hit().snapshot().state().getBlock();
    }

    public boolean toolChanged(EntityPlayerSP player) {
        if (player == null) {
            return false;
        }
        return net.minecraftforge.client.ForgeHooksClient
                .shouldCauseBlockBreakReset(tool, player.getHeldItemMainhand());
    }

    public BearingContraptionEntity findEntity(EntityPlayerSP player) {
        if (player == null || player.world == null) {
            return null;
        }
        net.minecraft.entity.Entity entity = player.world.getEntityByID(entityId);
        if (!(entity instanceof BearingContraptionEntity contraption)
                || !contraption.hasContraptionData()) {
            return null;
        }
        return contraption;
    }

    public ContraptionMiningSession moved(BearingContraptionEntity target,
                                          ContraptionInteractionClientEvents.ContraptionHit hit) {
        return new ContraptionMiningSession(
                sequence, entityId, localPos, hit.hit().side(),
                (float) target.getPartialAngle(
                        Minecraft.getMinecraft().getRenderPartialTicks()),
                hit.hit().worldHit(), tool, hit.hit().snapshot());
    }

}
