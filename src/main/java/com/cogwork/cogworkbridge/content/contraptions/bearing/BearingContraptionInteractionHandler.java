package com.cogwork.cogworkbridge.content.contraptions.bearing;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.util.EnumActionResult;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event;

/** Server-authoritative destruction of blocks carried by a contraption. */
public final class BearingContraptionInteractionHandler {

    private static final double CLIENT_POSE_TOLERANCE = 2.0D;
    private BearingContraptionInteractionHandler() {
    }

    public static void handleBreak(EntityPlayerMP player, int entityId,
                                   BlockPos localPos, EnumFacing side,
                                   float clientAngle,
                                   Vec3d clientHit) {
        BearingContraptionEntity entity = findEntity(player.world, entityId);
        if (entity == null || !belongsToWorld(player, entity)) {
            return;
        }
        if (!player.capabilities.allowEdit) {
            return;
        }
        BearingContraption contraption = entity.getContraption();
        if (contraption == null) {
            return;
        }
        BearingContraption.Snapshot snapshot =
                contraption.snapshotAtLocal(localPos).orElse(null);
        if (snapshot == null) {
            return;
        }
        if (!playerBreakIsReachable(
                player, entity, localPos, side, clientAngle, clientHit)) {
            return;
        }
        float interactionAngle = interactionAngle(
                entity, clientAngle, clientHit);
        destroy(player, entity, localPos, snapshot, interactionAngle);
    }

    public static void handleInteract(EntityPlayerMP player, int entityId,
                                      BlockPos localPos, EnumFacing side,
                                      EnumHand hand, float clientAngle,
                                      Vec3d clientHit, Vec3d clientTraceStart,
                                      Vec3d clientTraceEnd,
                                      float hitX, float hitY,
                                      float hitZ, boolean secondaryUse) {
        BearingContraptionEntity entity = findEntity(player.world, entityId);
        if (entity == null) {
            return;
        }
        if (!belongsToWorld(player, entity)) {
            return;
        }
        if (!player.capabilities.allowEdit) {
            return;
        }
        if (!serverReachAndVisibility(
                player, entity, localPos, side, clientAngle, clientHit,
                clientTraceStart, clientTraceEnd)) {
            return;
        }
        if (player.getHeldItem(hand).getItem() == com.cogwork.cogworkbridge.registry.AllItems.WRENCH
                && com.cogwork.cogworkbridge.content.contraptions.mounted.MinecartContraptionItem
                .pickupFromStructure(player, entity)) {
            player.swingArm(hand);
            return;
        }
        if (!entity.startInteractionSession()) {
            return;
        }
        try {
            BearingContraption contraption = entity.getContraption();
            if (contraption == null) {
                return;
            }
            BearingContraption.Snapshot snapshot =
                    contraption.snapshotAtLocal(localPos).orElse(null);
            if (snapshot == null) {
                return;
            }
            Vec3d worldCenter = entity.localBlockCenterToWorld(localPos);
            BlockPos referencePos = new BlockPos(worldCenter);
            if (!player.world.isBlockModifiable(player, referencePos)) {
                return;
            }

            IBlockState state = snapshot.state();
            if (state.getBlock() instanceof com.cogwork.cogworkbridge.content.decoration.SeatBlock
                    && com.cogwork.cogworkbridge.content.decoration.SeatBlock.dyeColor(player.getHeldItem(hand)) < 0
                    && com.cogwork.cogworkbridge.content.decoration.SeatBlock.sitOnContraption(player, entity, localPos)) {
                player.swingArm(hand);
                return;
            }
            World previousWorld = player.world;
            World interactionWorld = entity.getInteractionWorld();
            boolean opened;
            try {
                player.setWorld(interactionWorld);
                opened = ContraptionGuiProxy.tryOpen(player, entity, localPos,
                        state);
            } finally {
                player.setWorld(previousWorld);
            }
            if (opened) {
                player.swingArm(hand);
                return;
            }
            try {
                // Vanilla and modded block hooks frequently mix the supplied
                // world with player.world. Keep the player and block call in
                // the same virtual-world context for the duration of the use.
                player.setWorld(interactionWorld);
                blockHit(player, entity, localPos, referencePos, state, hand,
                        side, hitX, hitY, hitZ, secondaryUse);
            } finally {
                player.setWorld(previousWorld);
            }
        } finally {
            entity.commitInteractionChanges();
            entity.finishInteractionSession();
        }
    }

    private static boolean blockHit(EntityPlayerMP player,
                                    BearingContraptionEntity entity,
                                    BlockPos localPos, BlockPos referencePos,
                                    IBlockState state,
                                    EnumHand hand, EnumFacing side,
                                    float hitX, float hitY, float hitZ,
                                    boolean secondaryUse) {
        World interactionWorld = entity.getInteractionWorld();
        Vec3d virtualHit = entity.localBlockCenterToWorld(localPos);
        PlayerInteractEvent.RightClickBlock event =
                new PlayerInteractEvent.RightClickBlock(
                        player, hand, referencePos, side, virtualHit);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()
                || event.getUseBlock() == Event.Result.DENY) {
            return false;
        }
        // Let third-party blocks receive their normal activation callback.
        // Their GUI bridge is responsible for translating the carried block;
        // silently vetoing the callback made Mekanism and other machines
        // appear non-interactive even when their tile was valid.
        boolean activated = state.getBlock().onBlockActivated(
                interactionWorld, localPos, state, player, hand,
                side, hitX, hitY, hitZ);
        if (secondaryUse) {
            activated = false;
        }
        if (activated) {
            player.swingArm(hand);
            return true;
        }
        ItemStack stack = player.getHeldItem(hand);
        if (!stack.isEmpty()
                && (event.getUseItem() == Event.Result.ALLOW
                || event.getUseItem() != Event.Result.DENY)) {
            EnumActionResult result = stack.onItemUse(
                    player, interactionWorld, localPos, hand, side,
                    hitX, hitY, hitZ);
            if (result != EnumActionResult.FAIL) {
                entity.syncInteractionBlock(localPos);
            }
            if (result == EnumActionResult.SUCCESS) {
                player.swingArm(hand);
                return true;
            }
        }
        return false;
    }

    private static BearingContraptionEntity findEntity(World world, int entityId) {
        net.minecraft.entity.Entity entity = world.getEntityByID(entityId);
        return entity instanceof BearingContraptionEntity contraption
                && contraption.hasContraptionData() ? contraption : null;
    }

    static void keepCarriedGuiValid(EntityPlayerMP player) {
        ContraptionGuiProxy.closeStale(player);
    }

    private static boolean belongsToWorld(EntityPlayerMP player,
                                          BearingContraptionEntity entity) {
        return player.world == entity.world && !entity.isDead;
    }

    private static boolean serverReachAndVisibility(
            EntityPlayerMP player, BearingContraptionEntity entity,
            BlockPos localPos, EnumFacing side, float clientAngle,
            Vec3d clientHit, Vec3d clientTraceStart,
            Vec3d clientTraceEnd) {
        if (player.world == null || !belongsToWorld(player, entity)||!entity.isRailwayBlockVisible(localPos)) {
            return false;
        }
        Vec3d eyes = player.getPositionEyes(1.0F);
        Vec3d traceOrigin = validClientTraceStart(
                eyes, clientTraceStart) ? clientTraceStart : eyes;
        float tracedAngle = interactionAngle(entity, clientAngle, clientHit);
        AxisAlignedBB target = entity.localCollisionBoxToWorld(
                localPos, tracedAngle);
        if (target == null
                || !ContraptionInteractionMath.intersectsInteractionReach(
                player, eyes.x, eyes.y, eyes.z,
                target.minX, target.minY, target.minZ,
                target.maxX, target.maxY, target.maxZ)) {
            return false;
        }
        Vec3d traceEnd = clientTraceEnd != null
                && eyes.squareDistanceTo(clientTraceEnd) > 0.0D
                ? clientTraceEnd : target.getCenter();
        BearingContraptionEntity.ContraptionHit hit =
                entity.rayTraceAtAngle(traceOrigin, traceEnd, tracedAngle);
        if (hit != null && hit.localPos().equals(localPos)) {
            traceEnd = hit.worldHit();
        } else if (clientHit == null || !insideGrownBox(target, clientHit)) {
            return false;
        } else {
            traceEnd = clientHit;
        }
        /*
         * Upstream resolves block use inside the contraption's own local
         * world. Real-world blocks provide the ray endpoint, but must not
         * veto a dynamic block after the exact carried shape was hit. The
         * source blocks remain collision and world proxies and commonly
         * overlap the rotating structure.
         */
        return ContraptionInteractionMath.withinInteractionReach(player,
                eyes.x - traceEnd.x,
                eyes.y - traceEnd.y,
                eyes.z - traceEnd.z);
    }

    private static boolean validClientTraceStart(
            Vec3d serverEyes, Vec3d clientTraceStart) {
        return clientTraceStart != null
                && serverEyes.squareDistanceTo(clientTraceStart)
                <= CLIENT_POSE_TOLERANCE * CLIENT_POSE_TOLERANCE;
    }

    /**
     * Player mining starts from a rendered client pose while the server has
     * already advanced to its next tick. Re-tracing that pose from the server
     * eye rejects valid clicks whenever interpolation or network position
     * differs by even a small amount. Validate the client's reported selection
     * against the same rendered block shape instead, while retaining the
     * server eye reach and all world protection checks.
     */
    static boolean playerBreakIsReachable(
            EntityPlayerMP player, BearingContraptionEntity entity,
            BlockPos localPos, EnumFacing side, float clientAngle,
            Vec3d clientHit) {
        if (player.world == null || !belongsToWorld(player, entity)
                || clientHit == null || !Float.isFinite(clientAngle)) {
            return false;
        }
        Vec3d eyes = player.getPositionEyes(1.0F);
        double reach = ContraptionInteractionMath.interactionReach(player)
                + PLAYER_POSE_TOLERANCE;
        if (Math.sqrt(eyes.squareDistanceTo(clientHit)) > reach) {
            return false;
        }
        BearingContraptionEntity.ContraptionHit confirmed =
                entity.rayTraceAtAngle(eyes, clientHit, clientAngle);
        return confirmed != null && confirmed.localPos().equals(localPos)
                && ContraptionInteractionMath.withinInteractionReach(player,
                eyes.x - confirmed.worldHit().x,
                eyes.y - confirmed.worldHit().y,
                eyes.z - confirmed.worldHit().z);
    }

    private static boolean insideGrownBox(AxisAlignedBB box, Vec3d point) {
        double epsilon = ContraptionInteractionMath.SELECTION_EPSILON;
        return point.x >= box.minX - epsilon && point.x <= box.maxX + epsilon
                && point.y >= box.minY - epsilon && point.y <= box.maxY + epsilon
                && point.z >= box.minZ - epsilon && point.z <= box.maxZ + epsilon;
    }

    private static float interactionAngle(
            BearingContraptionEntity entity, float clientAngle,
            Vec3d clientHit) {
        return clientHit != null && Float.isFinite(clientAngle)
                ? clientAngle : (float) entity.getAngle();
    }

    private static void destroy(EntityPlayerMP player,
                                BearingContraptionEntity entity,
                                BlockPos localPos,
                                BearingContraption.Snapshot snapshot,
                                float interactionAngle) {
        if (snapshot == null) {
            return;
        }
        IBlockState state = snapshot.state();
        Block block = state.getBlock();
        Vec3d worldHit = entity.localBlockCenterToWorldAtAngle(
                localPos, interactionAngle);
        BlockPos referencePos = new BlockPos(worldHit);
        if (!player.world.isBlockModifiable(player, referencePos)
                || !player.world.canMineBlockBody(player, referencePos)) {
            return;
        }
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(
                player.world, referencePos, state, player);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            return;
        }

        ItemStack tool = player.getHeldItemMainhand();
        int fortune = tool.isEmpty() ? 0 : net.minecraft.enchantment.EnchantmentHelper
                .getEnchantmentLevel(net.minecraft.init.Enchantments.FORTUNE, tool);
        boolean silk = !tool.isEmpty() && net.minecraft.enchantment.EnchantmentHelper
                .getEnchantmentLevel(net.minecraft.init.Enchantments.SILK_TOUCH, tool) > 0;
        boolean canHarvest = player.canHarvestBlock(state);
        NonNullList<ItemStack> drops = NonNullList.create();
        if (canHarvest) {
            block.getDrops(drops, player.world, referencePos, state, fortune);
            if (silk && block.canSilkHarvest(
                    player.world, referencePos, state, player)) {
                drops.clear();
                int meta = block.getItemDropped(
                        state, player.world.rand, fortune)
                        == net.minecraft.item.Item.getItemFromBlock(block)
                        ? block.damageDropped(state) : 0;
                drops.add(new ItemStack(block, 1, meta));
            }
        }
        float dropChance = ForgeEventFactory.fireBlockHarvesting(drops,
                player.world, referencePos, state, fortune,
                1.0F, silk, player);
        if (!player.capabilities.isCreativeMode) {
            drops.removeIf(stack -> player.world.rand.nextFloat() > dropChance);
            tool.onBlockDestroyed(player.world, state, referencePos, player);
            int experience = block.getExpDrop(
                    state, player.world, referencePos, fortune);
            if (experience > 0) {
                player.addExperience(experience);
            }
        }
        state.getBlock().onBlockHarvested(player.world, referencePos, state, player);
        player.world.playEvent(2001, referencePos, Block.getStateId(state));
        entity.removeLocalBlock(localPos);
        if (!player.capabilities.isCreativeMode) {
            entity.spawnDrop(localPos, drops);
        }
    }

    private static final double PLAYER_POSE_TOLERANCE = 1.0D;
}
