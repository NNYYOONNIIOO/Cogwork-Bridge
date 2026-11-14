package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionBreakPacket;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionInteractPacket;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionMiningPacket;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionMiningProgressPacket;
import com.cogwork.cogworkbridge.content.contraptions.glue.GlueSelectionRules;
import com.cogwork.cogworkbridge.content.contraptions.glue.client.SuperGlueSelectionHandler;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.EnumHand;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.AxisAlignedBB;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Routes left-click mining to a contraption when its real per-block shape is
 * closer than the first world block. The server remains authoritative.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ContraptionInteractionClientEvents {

    private static ContraptionMiningSession miningSession;
    private static boolean miningHeld;

    private ContraptionInteractionClientEvents() {
    }

    @SubscribeEvent
    public static void mouse(MouseEvent event) {
        if (event.getButton() != 0 && event.getButton() != 1) {
            return;
        }
        if (event.getButton() == 1) {
            rightClick(event);
            return;
        }
        if (!event.isButtonstate()) {
            Minecraft mc = Minecraft.getMinecraft();
            int attackKey = mc.gameSettings.keyBindAttack.getKeyCode();
            if (miningHeld && attackKey < 0
                    && attackKey == event.getButton() - 100) {
                event.setCanceled(true);
                releaseMining();
            }
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        WorldClient world = mc.world;
        if (event.isCanceled() || player == null || world == null
                || mc.playerController == null || mc.currentScreen != null
                || mc.gameSettings.keyBindAttack.getKeyCode() != event.getButton() - 100
                || !player.capabilities.allowEdit) {
            return;
        }
        ContraptionHit hit = traceIgnoringScreen(mc, player, world);
        if (hit != null) {
            cancelVanillaMining(mc);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            miningHeld = true;
            beginMining(mc, hit);
            event.setCanceled(true);
            return;
        }
        releaseMining();
    }

    public static boolean tickMining() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || miningSession == null) {
            return false;
        }
        ContraptionHit current = traceIgnoringScreen(mc, mc.player, mc.world);
        if (!miningSession.matches(current)
                || miningSession.toolChanged(mc.player)) {
            clearMiningSession();
            return true;
        }
        BearingContraptionEntity target = miningSession.findEntity(mc.player);
        if (target == null) {
            clearMiningSession();
            return true;
        }
        miningSession = miningSession.moved(target, current);
        BlockPos dynamicPos = dynamicWorldPos(miningSession, target);
        ContraptionMiningController.damage(miningSession, target, dynamicPos);
        sendMiningUpdate(miningSession, false);
        return true;
    }

    private static void rightClick(MouseEvent event) {
        if (!event.isButtonstate() || event.isCanceled()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        WorldClient world = mc.world;
        if (player == null || world == null || mc.currentScreen != null
                || !player.capabilities.allowEdit
                || SuperGlueSelectionHandler.isGlue(
                player.getHeldItemMainhand())) {
            return;
        }
        if (shouldUseVanillaBlockPlacement(mc, player, world)) {
            return;
        }
        ContraptionHit hit = traceIgnoringScreen(mc, player, world);
        if (hit == null) {
            return;
        }
        net.minecraft.block.state.IBlockState carriedState = hit.entity.getInteractionWorld()
                .getBlockState(hit.hit.localPos());
        if (carriedState.getBlock() instanceof com.cogwork.cogworkbridge.content.contraptions.elevator.RedstoneContactBlock contact
                && contact.isElevator()
                && player.getHeldItemMainhand().getItem() != com.cogwork.cogworkbridge.registry.AllItems.WRENCH) {
            mc.displayGuiScreen(new ElevatorContactScreen(hit.entity, hit.hit.localPos()));
            event.setCanceled(true);
            return;
        }
        float partialTicks = mc.getRenderPartialTicks();
        Vec3d traceStart = player.getPositionEyes(partialTicks);
        Vec3d traceEnd = traceStart.add(player.getLook(partialTicks).scale(
                mc.playerController.getBlockReachDistance()));
        ModNetwork.CHANNEL.sendToServer(new ContraptionInteractPacket(
                hit.entity.getEntityId(), hit.hit.localPos(),
                hit.hit.side(), EnumHand.MAIN_HAND,
                player.isSneaking() && !player.getHeldItemMainhand().isEmpty(),
                (float) hit.entity.getPartialAngle(
                        partialTicks),
                hit.hit.worldHit(),
                traceStart, traceEnd,
                hit.hit.hitX(), hit.hit.hitY(), hit.hit.hitZ()));
        event.setCanceled(true);
    }

    private static boolean shouldUseVanillaBlockPlacement(
            Minecraft mc, EntityPlayerSP player, WorldClient world) {
        ItemStack stack = player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof ItemBlock)) {
            return false;
        }
        Vec3d start = player.getPositionEyes(1.0F);
        Vec3d look = player.getLook(1.0F);
        double reach = mc.playerController.getBlockReachDistance();
        Vec3d end = start.add(look.x * reach, look.y * reach, look.z * reach);
        RayTraceResult worldHit = world.rayTraceBlocks(start, end, false, false, true);
        if (worldHit == null || worldHit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return false;
        }
        BlockPos target = GlueSelectionRules.placementTarget(
                world, worldHit.getBlockPos(), worldHit.sideHit);
        return target != null && world.isBlockLoaded(target)
                && GlueSelectionRules.isReplaceable(world, target);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.playerController == null
                || mc.currentScreen != null || !mc.inGameHasFocus) {
            releaseMining();
            return;
        }
        if (!mc.player.capabilities.allowEdit) {
            releaseMining();
            return;
        }
        if (miningHeld) {
            if (!isAttackInputDown(mc)) {
                releaseMining();
                return;
            }
            if (miningSession != null) {
                tickMining();
                return;
            }
            if (ContraptionMiningController.waitForNextBlock()) {
                return;
            }
            if (!beginMiningAtCrosshair(mc)) {
                releaseMining();
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), true);
            }
            return;
        }
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) {
            return;
        }
        ContraptionHit hit = traceIgnoringScreen(mc, mc.player, mc.world);
        if (hit == null) {
            return;
        }
        cancelVanillaMining(mc);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        miningHeld = true;
        beginMining(mc, hit);
    }

    @SubscribeEvent
    public static void worldUnload(
            net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        releaseMining();
        ClientContraptionMiningProgress.clear();
    }

    @SubscribeEvent
    public static void worldUnload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            releaseMining();
            ClientContraptionMiningProgress.clear();
        }
    }

    public static ContraptionHit currentHit() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null
                || mc.currentScreen != null) {
            return null;
        }
        return traceIgnoringScreen(mc, mc.player, mc.world);
    }

    public static void serverMiningResult(int playerId, int sequence, int entityId,
                                          BlockPos localPos, int result) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.player.getEntityId() != playerId
                || miningSession == null || miningSession.sequence() != sequence
                || miningSession.entityId() != entityId
                || !miningSession.localPos().equals(localPos)) {
            return;
        }
        miningSession = null;
        if (result == ContraptionMiningProgressPacket.COMPLETED) {
            ContraptionMiningController.completed();
        } else {
            ContraptionMiningController.stop();
        }
    }

    public static boolean isCurrentMining(int sequence, int entityId, BlockPos localPos) {
        return miningSession != null && miningSession.sequence() == sequence
                && miningSession.entityId() == entityId
                && miningSession.localPos().equals(localPos);
    }

    @SubscribeEvent
    public static void renderSelection(RenderWorldLastEvent event) {
        ContraptionHit hit = currentHit();
        if (hit == null) {
            return;
        }
        AxisAlignedBB box = hit.entity.localCollisionBoxToWorld(
                hit.hit.localPos(), event.getPartialTicks());
        if (box == null) {
            return;
        }
        net.minecraft.entity.Entity view = Minecraft.getMinecraft()
                .getRenderViewEntity();
        if (view == null) {
            return;
        }
        double partial = event.getPartialTicks();
        double cameraX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partial;
        double cameraY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partial;
        double cameraZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partial;
        AxisAlignedBB relative = box.offset(-cameraX, -cameraY, -cameraZ)
                .grow(0.002D);
        net.minecraft.client.renderer.RenderGlobal.drawSelectionBoundingBox(
                relative, 0.0F, 0.0F, 0.0F, 0.4F);
    }

    private static ContraptionHit traceIgnoringScreen(
            Minecraft mc, EntityPlayerSP player, WorldClient world) {
        float partialTicks = mc.getRenderPartialTicks();
        Vec3d start = player.getPositionEyes(partialTicks);
        Vec3d look = player.getLook(partialTicks);
        double reach = mc.playerController.getBlockReachDistance();
        Vec3d end = start.add(look.x * reach, look.y * reach, look.z * reach);
        RayTraceResult worldHit = player.world.rayTraceBlocks(
                start, end, false, false, true);
        double worldDistance = worldHit == null
                ? Double.POSITIVE_INFINITY : start.distanceTo(worldHit.hitVec);
        ContraptionHit best = null;
        double bestDistance = Math.min(reach, worldDistance);
        ContraptionHit stalledBest = null;
        double stalledDistance = Double.POSITIVE_INFINITY;
        /*
         * The entity bounds only follow the cached contraption pose and are
         * not a reliable broad phase for every carried block. A rotating
         * contraption can have a reachable member outside that union while a
         * rendered block is already under the crosshair. Query all loaded
         * contraptions, then let each one perform its exact per-block trace.
         */
        for (BearingContraptionEntity entity :
                world.getEntities(BearingContraptionEntity.class,
                        candidate -> candidate != null && !candidate.isDead
                                && candidate.hasContraptionData())) {
            BearingContraptionEntity.ContraptionHit hit =
                    entity.rayTrace(start, end, partialTicks);
            if (hit == null) {
                continue;
            }
            double distance = start.distanceTo(hit.worldHit());
            if (entity.isStalled()) {
                if (distance < reach
                        && distance <= worldDistance + SELECTION_TOLERANCE
                        && distance < stalledDistance) {
                    stalledBest = new ContraptionHit(entity, hit);
                    stalledDistance = distance;
                }
                continue;
            }
            if (distance < reach
                    && distance <= worldDistance + SELECTION_TOLERANCE
                    && distance < bestDistance + SELECTION_TOLERANCE) {
                bestDistance = distance;
                best = new ContraptionHit(entity, hit);
            }
        }
        return stalledBest != null ? stalledBest : best;
    }

    private static final double SELECTION_TOLERANCE = 0.08D;

    private static void sendCompletedBreak(ContraptionMiningSession session) {
        ContraptionMiningController.completed();
        ModNetwork.CHANNEL.sendToServer(new ContraptionBreakPacket(
                session.entityId(), session.localPos(), session.side(),
                session.interactionAngle(), session.worldHit()));
        miningSession = null;
    }

    private static void releaseMining() {
        clearMiningSession();
        ContraptionMiningController.reset();
        miningHeld = false;
    }

    private static void clearMiningSession() {
        Minecraft mc = Minecraft.getMinecraft();
        if (miningSession != null) {
            if (mc.player != null) {
                ClientContraptionMiningProgress.clearSession(
                        mc.player.getEntityId(), miningSession.sequence());
            }
            sendMiningUpdate(miningSession, true);
        }
        ContraptionMiningController.stop();
        miningSession = null;
    }

    private static void cancelVanillaMining(Minecraft mc) {
        if (mc.playerController != null && mc.playerController.getIsHittingBlock()) {
            mc.playerController.resetBlockRemoving();
        }
    }

    private static boolean beginMiningAtCrosshair(Minecraft mc) {
        ContraptionHit hit = traceIgnoringScreen(mc, mc.player, mc.world);
        if (hit == null) {
            return false;
        }
        beginMining(mc, hit);
        return true;
    }

    private static void beginMining(Minecraft mc, ContraptionHit hit) {
        miningSession = ContraptionMiningSession.from(hit);
        if (ContraptionMiningController.click(mc.playerController)) {
            sendCompletedBreak(miningSession);
        } else {
            sendMiningUpdate(miningSession, false);
        }
    }

    private static void sendMiningUpdate(ContraptionMiningSession session, boolean cancel) {
        Minecraft mc = Minecraft.getMinecraft();
        if (session == null || mc.player == null || mc.world == null
                || mc.getConnection() == null) {
            return;
        }
        ModNetwork.CHANNEL.sendToServer(new ContraptionMiningPacket(
                session.sequence(), session.entityId(), session.localPos(), session.side(),
                session.interactionAngle(), session.worldHit(), cancel));
    }

    private static boolean isAttackInputDown(Minecraft mc) {
        int key = mc.gameSettings.keyBindAttack.getKeyCode();
        if (key < 0) {
            int button = key + 100;
            return Mouse.isCreated() && button >= 0
                    && button < Mouse.getButtonCount() && Mouse.isButtonDown(button);
        }
        return Keyboard.isCreated() && key > 0 && key < 256
                && Keyboard.isKeyDown(key);
    }

    private static BlockPos dynamicWorldPos(
            ContraptionMiningSession session,
            BearingContraptionEntity target) {
        BlockPos dynamicPos = new BlockPos(target.localBlockCenterToWorldAtAngle(
                session.localPos(), session.interactionAngle()));
        return dynamicPos;
    }

    public record ContraptionHit(BearingContraptionEntity entity,
                                 BearingContraptionEntity.ContraptionHit hit) {
    }
}
