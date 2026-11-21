package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.compat.jecharacters.CreativeSearchCompat;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Adds click-to-focus to Forge's built-in per-tab creative search. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class CreativeTabSearchEvents {
    private static GuiContainerCreative trackedScreen;
    private static GuiTextField trackedField;
    private static int trackedTab = -1;

    private CreativeTabSearchEvents() { }

    private static boolean observe(GuiContainerCreative gui) {
        int tab = gui.getSelectedTabIndex();
        GuiTextField field = gui.searchField;
        boolean ours = tab == ModCreativeTab.INSTANCE.getIndex();
        if (gui != trackedScreen || field != trackedField || tab != trackedTab) {
            if (ours && field != null) {
                field.setCanLoseFocus(true);
                field.setFocused(false);
                gui.clearSearch = false;
            }
            trackedScreen = gui;
            trackedField = field;
            trackedTab = tab;
        }
        return ours && field != null;
    }

    @SubscribeEvent
    public static void onOpen(GuiOpenEvent event) {
        if (event.getGui() != trackedScreen) {
            trackedScreen = null;
            trackedField = null;
            trackedTab = -1;
        }
    }

    @SubscribeEvent
    public static void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof GuiContainerCreative gui) observe(gui);
    }

    @SubscribeEvent
    public static void onDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (event.getGui() instanceof GuiContainerCreative gui) observe(gui);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiContainerCreative gui) || !observe(gui)
                || !Mouse.getEventButtonState()) return;
        int button = Mouse.getEventButton();
        if (button != 0 && button != 1) return;
        Minecraft mc = Minecraft.getMinecraft();
        int x = Mouse.getEventX() * gui.width / mc.displayWidth;
        int y = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;
        GuiTextField field = gui.searchField;
        boolean inside = x >= field.x && x < field.x + field.width
                && y >= field.y - 2 && y < field.y + field.height + 2;
        if (!inside) {
            field.setFocused(false);
            return;
        }
        field.setFocused(true);
        field.mouseClicked(x, Math.max(field.y, Math.min(field.y + field.height - 1, y)), button);
        gui.clearSearch = false;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void afterMouse(GuiScreenEvent.MouseInputEvent.Post event) {
        if (event.getGui() instanceof GuiContainerCreative gui) observe(gui);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyboard(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiContainerCreative gui) || !observe(gui)) return;
        GuiTextField field = gui.searchField;
        gui.clearSearch = false;
        int key = Keyboard.getEventKey();
        char character = Keyboard.getEventCharacter();
        if (!field.isFocused() || key == Keyboard.KEY_ESCAPE
                || !(key == 0 && character >= ' ' || Keyboard.getEventKeyState())) return;
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            field.setFocused(false);
        } else {
            String previous = field.getText();
            field.textboxKeyTyped(character, key);
            if (!previous.equals(field.getText()) && !CreativeSearchCompat.updateSearch(gui)) gui.updateCreativeSearch();
        }
        event.setCanceled(true);
    }
}
