package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.kinetics.encased.WrenchItem;
import com.cogwork.cogworkbridge.content.kinetics.encased.WrenchRotationOptions;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.List;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class WrenchRotationClientEvents {
    public static final KeyBinding ROTATE_MENU = new KeyBinding("key.cogworkbridge.rotate_menu", Keyboard.KEY_NONE,
            "key.categories.cogworkbridge");
    private static int cooldown;
    private WrenchRotationClientEvents() { }

    static void menuClosed() {
        cooldown = 2;
        while (ROTATE_MENU.isPressed()) { }
    }
    private static boolean isHeld() {
        int code = ROTATE_MENU.getKeyCode();
        return code < 0 ? code + 100 >= 0 && code + 100 < Mouse.getButtonCount() && Mouse.isButtonDown(code + 100)
                : code > 0 && code < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(code);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && cooldown > 0 && !isHeld()) cooldown--;
    }
    @SubscribeEvent public static void keyboard(InputEvent.KeyInputEvent event) {
        int code = Keyboard.getEventKey();
        if (code > 0 && Keyboard.getEventKeyState() && ROTATE_MENU.isActiveAndMatches(code)) open(false, code);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void mouse(MouseEvent event) {
        int button = event.getButton();
        if (button < 0 || !event.isButtonstate() || !ROTATE_MENU.isActiveAndMatches(button - 100)) return;
        if (open(true, button - 100)) {
            // The same mouse button may also be bound to attack or use.
            KeyBinding.setKeyBindState(button - 100, false);
            event.setCanceled(true);
        }
    }
    private static boolean open(boolean mouseBinding, int keyCode) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null || mc.world == null || mc.playerController == null
                || mc.player.isSpectator() || cooldown > 0
                || !(mc.player.getHeldItemMainhand().getItem() instanceof WrenchItem)) return false;
        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.sideHit == null) return false;
        IBlockState state = mc.world.getBlockState(hit.getBlockPos());
        List<IProperty<?>> properties = WrenchRotationOptions.properties(mc.world, hit.getBlockPos(), state);
        if (properties.isEmpty()) return false;
        mc.playerController.resetBlockRemoving();
        mc.displayGuiScreen(new WrenchRotationScreen(hit.getBlockPos(), hit.sideHit, state, properties, mouseBinding, keyCode));
        return true;
    }
}
