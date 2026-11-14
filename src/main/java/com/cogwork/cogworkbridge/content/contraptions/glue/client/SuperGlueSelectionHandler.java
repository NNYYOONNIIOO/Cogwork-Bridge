package com.cogwork.cogworkbridge.content.contraptions.glue.client;

import com.cogwork.cogworkbridge.content.contraptions.glue.GlueBox;
import com.cogwork.cogworkbridge.content.contraptions.glue.GlueFeedback;
import com.cogwork.cogworkbridge.content.contraptions.glue.GridPos;
import com.cogwork.cogworkbridge.content.contraptions.glue.GlueSelectionPacket;
import com.cogwork.cogworkbridge.content.contraptions.glue.ClientGlueAreas;
import com.cogwork.cogworkbridge.content.contraptions.glue.GlueRemovalPacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * Two-click selection state machine. This mirrors Create's client flow while
 * keeping the actual glue area authoritative on the server.
 */
public final class SuperGlueSelectionHandler {

    private BlockPos firstPos;
    private BlockPos hoveredPos;
    private net.minecraft.util.EnumFacing hoveredSide;
    private GlueBox selectionBox;
    private GlueBox confirmedBox;
    private int confirmedTicks;

    public void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        WorldClient world = mc.world;
        if (player == null || world == null || mc.currentScreen != null) {
            return;
        }
        if (confirmedTicks > 0) {
            confirmedTicks--;
        }
        if (!isGlue(player.getHeldItemMainhand())) {
            discard(false);
            return;
        }

        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            hoveredPos = null;
            return;
        }
        BlockPos hovered = hit.getBlockPos();
        hoveredSide = hit.sideHit;
        if (firstPos != null && !GlueBox.withinSelectionDistance(grid(firstPos), grid(hovered))) {
            hoveredPos = null;
            selectionBox = null;
            return;
        }
        if (!hovered.equals(hoveredPos)) {
            hoveredPos = hovered;
            if (firstPos != null) {
                selectionBox = GlueBox.span(grid(firstPos), grid(hovered));
            }
        }
    }

    public boolean onMouseInput(boolean attack) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null
                || !isGlue(player.getHeldItemMainhand())
                || !player.capabilities.allowEdit) {
            return false;
        }

        if (attack) {
            Minecraft client = Minecraft.getMinecraft();
            if (client.player == null || client.world == null) {
                return false;
            }
            net.minecraft.util.math.Vec3d start = client.player.getPositionEyes(1.0F);
            net.minecraft.util.math.Vec3d look = client.player.getLook(1.0F);
            net.minecraft.util.math.Vec3d end = start.add(look.x * 32.0D,
                    look.y * 32.0D, look.z * 32.0D);
            Integer entityId = ClientGlueAreas.findByRay(start, end);
            if (entityId == null) {
                return false;
            }
            ModNetwork.CHANNEL.sendToServer(new GlueRemovalPacket(entityId));
            return true;
        }
        if (player.isSneaking()) {
            if (firstPos != null) {
                discard(true);
                return true;
            }
            return false;
        }
        if (firstPos == null && hoveredPos != null && hoveredSide != null && mc.world != null) {
            var state = mc.world.getBlockState(hoveredPos);
            if (state.getBlock() instanceof com.cogwork.cogworkbridge.content.contraptions.chassis.ChassisBlock chassis
                    && chassis.acceptsGlue(mc.world, hoveredPos, state, hoveredSide)) return false;
        }
        if (hoveredPos == null) {
            return false;
        }
        if (firstPos == null) {
            firstPos = hoveredPos;
            selectionBox = GlueBox.span(grid(firstPos), grid(hoveredPos));
            GlueFeedback.playSelection(player.world, hoveredPos, hitSide(), 0.85F);
            player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.super_glue.first_pos"), true);
            return true;
        }

        if (selectionBox == null) {
            return false;
        }
        ModNetwork.CHANNEL.sendToServer(new GlueSelectionPacket(
                firstPos, hoveredPos, EnumHand.MAIN_HAND));
        GlueFeedback.playConfirmation(player.world, hoveredPos, hitSide());
        confirm(selectionBox);
        return true;
    }

    public void confirm(GlueBox box) {
        confirmedBox = box;
        confirmedTicks = 40;
        discard(false);
    }

    public void discard(boolean showMessage) {
        if (showMessage) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                mc.player.sendStatusMessage(
                        new TextComponentTranslation("cogworkbridge.super_glue.abort"), true);
            }
        }
        firstPos = null;
        hoveredPos = null;
        hoveredSide = null;
        selectionBox = null;
    }

    public GlueBox getSelectionBox() {
        return selectionBox;
    }

    public GlueBox getConfirmedBox() {
        return confirmedTicks > 0 ? confirmedBox : null;
    }

    public static boolean isGlue(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == AllItems.SUPER_GLUE;
    }

    private net.minecraft.util.EnumFacing hitSide() {
        return hoveredSide == null
                ? net.minecraft.util.EnumFacing.UP : hoveredSide;
    }

    public static GridPos grid(BlockPos pos) {
        return new GridPos(pos.getX(), pos.getY(), pos.getZ());
    }
}
