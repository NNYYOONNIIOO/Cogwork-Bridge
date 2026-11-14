package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.redstone.LinkedControllerInputPacket;
import com.cogwork.cogworkbridge.content.redstone.LinkedControllerBindPacket;
import com.cogwork.cogworkbridge.content.redstone.RedstoneLinkBlock;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class LinkedControllerClientEvents {
    private static boolean active;
    private static EnumHand hand = EnumHand.MAIN_HAND;
    private static int lastMask, refresh;
    private static BlockPos binding, lectern, stoppedLectern;
    private static int bindingPreviousMask;
    private static float equip, previousEquip;
    private LinkedControllerClientEvents() { }
    static boolean controlling() { return active || binding != null; }
    static boolean inLectern() { return lectern != null; }
    public static void updateLectern(com.cogwork.cogworkbridge.content.redstone.LecternControllerTileEntity tile) {
        Minecraft mc=Minecraft.getMinecraft(); if(mc.player==null)return;
        if(tile.usedBy(mc.player)) {
            if(!controlling() && !tile.getPos().equals(stoppedLectern)){active=true;lectern=tile.getPos().toImmutable();lastMask=0;refresh=0;}
        } else {
            if(lectern!=null && lectern.equals(tile.getPos())) stop();
            if(tile.getPos().equals(stoppedLectern)) stoppedLectern=null;
        }
    }
    static int pressedMask() { return lastMask; }
    static float equip(float partial) { return previousEquip + (equip - previousEquip) * partial; }
    public static void toggle(EnumHand selected) {
        if (active || binding != null) stop(); else { active = true; hand = selected; lastMask = 0; refresh = 0; }
    }
    public static void bind(BlockPos pos, EnumHand selected) {
        if (active || binding != null) { stop(); return; }
        binding = pos.toImmutable();
        hand = selected;
        bindingPreviousMask = controlsMask(Minecraft.getMinecraft());
        Minecraft.getMinecraft().player.sendStatusMessage(new TextComponentTranslation("gui.cogworkbridge.controller.bind"), true);
    }
    private static void stop() {
        if (active) ModNetwork.CHANNEL.sendToServer(lectern==null?new LinkedControllerInputPacket(hand,0):new LinkedControllerInputPacket(lectern,0,true));
        if (lectern!=null) stoppedLectern=lectern;
        active = false; binding = null; lectern=null; lastMask = 0; refresh = 0;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) for (KeyBinding control : controls(mc))
            KeyBinding.setKeyBindState(control.getKeyCode(), pressed(control));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        previousEquip = equip;
        equip += ((controlling() ? 1F : 0F) - equip) * .2F;
        if (!controlling()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) { active = false; binding = null; lectern=null; stoppedLectern=null; return; }
        if (mc.currentScreen != null || mc.player.isSpectator() || Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) { stop(); return; }
        if (lectern != null) {
            if (!mc.world.isBlockLoaded(lectern) || !(mc.world.getTileEntity(lectern) instanceof com.cogwork.cogworkbridge.content.redstone.LecternControllerTileEntity tile) || !tile.usedBy(mc.player) || !tile.inRange(mc.player)) { stop(); return; }
        } else {
            EnumHand heldHand = mc.player.getHeldItemMainhand().getItem() == AllItems.LINKED_CONTROLLER ? EnumHand.MAIN_HAND
                    : mc.player.getHeldItemOffhand().getItem() == AllItems.LINKED_CONTROLLER ? EnumHand.OFF_HAND : null;
            if (heldHand == null) { stop(); return; }
            if (hand != heldHand) { if(active)ModNetwork.CHANNEL.sendToServer(new LinkedControllerInputPacket(hand,0));hand=heldHand;lastMask=0;refresh=0; }
        }
        int mask = controlsMask(mc);
        if (binding != null) {
            if (!mc.world.isBlockLoaded(binding) || !(mc.world.getBlockState(binding).getBlock() instanceof RedstoneLinkBlock)) { stop(); return; }
            int newlyPressed = mask & ~bindingPreviousMask;
            bindingPreviousMask = mask;
            if (newlyPressed != 0) {
                int key = Integer.numberOfTrailingZeros(newlyPressed);
                ModNetwork.CHANNEL.sendToServer(new LinkedControllerBindPacket(binding, key, hand));
                mc.player.sendStatusMessage(new TextComponentTranslation("gui.cogworkbridge.controller.bound_key",
                        GameSettings.getKeyDisplayString(controls(mc)[key].getKeyCode())), true);
                stop();
                return;
            }
        } else if (mask != lastMask || ++refresh >= 5) {
            if (mask != lastMask) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK, .35F,
                    (mask & ~lastMask) != 0 ? .75F : .5F);
            ModNetwork.CHANNEL.sendToServer(lectern==null?new LinkedControllerInputPacket(hand,mask):new LinkedControllerInputPacket(lectern,mask,false));
            lastMask = mask; refresh = 0;
        }
        for (KeyBinding control : controls(mc)) KeyBinding.setKeyBindState(control.getKeyCode(), false);
    }
    static KeyBinding[] controls(Minecraft mc) {
        return new KeyBinding[]{mc.gameSettings.keyBindForward, mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindRight, mc.gameSettings.keyBindJump, mc.gameSettings.keyBindSneak};
    }
    private static int controlsMask(Minecraft mc) {
        int mask = 0;
        KeyBinding[] controls = controls(mc);
        for (int i = 0; i < controls.length; i++) if (pressed(controls[i])) mask |= 1 << i;
        return mask;
    }
    private static boolean pressed(KeyBinding control) {
        int key = control.getKeyCode();
        return key < 0 ? Mouse.isButtonDown(key + 100) : key != 0 && Keyboard.isKeyDown(key);
    }

    @SubscribeEvent public static void preventMovement(InputUpdateEvent event) {
        if (!controlling() || event.getEntityPlayer() != Minecraft.getMinecraft().player) return;
        event.getMovementInput().moveForward = 0;
        event.getMovementInput().moveStrafe = 0;
        event.getMovementInput().jump = false;
        event.getMovementInput().sneak = false;
    }

    @SubscribeEvent public static void cancelPause(GuiOpenEvent event) {
        if ((active || binding != null) && event.getGui() instanceof GuiIngameMenu) {
            event.setCanceled(true);
            stop();
        }
    }

    @SubscribeEvent public static void renderBindingHint(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (binding == null || event.getType() != RenderGameOverlayEvent.ElementType.ALL || mc.gameSettings.hideGUI) return;
        StringBuilder keys = new StringBuilder();
        for (KeyBinding control : controls(mc)) {
            if (keys.length() != 0) keys.append(" / ");
            keys.append(GameSettings.getKeyDisplayString(control.getKeyCode()));
        }
        int x = event.getResolution().getScaledWidth() / 3;
        int y = event.getResolution().getScaledHeight() - 42;
        mc.fontRenderer.drawStringWithShadow(I18n.format("gui.cogworkbridge.controller.bind"), x, y, 0xffd477);
        mc.fontRenderer.drawStringWithShadow(keys.toString(), x, y + 10, 0xffffff);
    }
}
