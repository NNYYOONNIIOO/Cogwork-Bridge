package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.equipment.ToolboxLinks;
import com.cogwork.cogworkbridge.content.equipment.ToolboxTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ToolboxClientEvents {
    public static final KeyBinding TOOLBELT = new KeyBinding("key.cogworkbridge.toolbox", Keyboard.KEY_LMENU, "key.categories.cogworkbridge");
    private static int cooldown;
    static boolean isToolbeltHeld() {
        int code = TOOLBELT.getKeyCode();
        return code < 0 ? Mouse.isButtonDown(code + 100) : code > 0 && Keyboard.isKeyDown(code);
    }
    static void menuClosed() {
        cooldown = 2;
        while (TOOLBELT.isPressed()) { }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && cooldown > 0 && !isToolbeltHeld()) cooldown--;
    }
    @SubscribeEvent public static void keyboard(InputEvent.KeyInputEvent event) { openMenu(); }
    @SubscribeEvent public static void mouse(InputEvent.MouseInputEvent event) { openMenu(); }
    private static void openMenu() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.currentScreen != null || mc.player.isSpectator()
                || cooldown > 0) return;
        if (SchematicToolClient.isActive(mc)) { while (TOOLBELT.isPressed()) { } return; }
        if (!TOOLBELT.isPressed()) return;
        List<ToolboxTileEntity> boxes = new ArrayList<>();
        for (TileEntity tile : mc.world.loadedTileEntityList)
            if (tile instanceof ToolboxTileEntity box && !box.isInvalid() && mc.player.getDistanceSq(box.getPos()) < 100D) boxes.add(box);
        boxes.sort(Comparator.comparingDouble(box -> mc.player.getDistanceSq(box.getPos())));
        if (boxes.size() > 8) boxes = new ArrayList<>(boxes.subList(0, 8));
        boxes.sort(Comparator.comparing(ToolboxTileEntity::identity));
        NBTTagCompound link = ToolboxLinks.snapshot(mc.player).getCompoundTag(Integer.toString(mc.player.inventory.currentItem));
        if (!boxes.isEmpty() || link.hasUniqueId("Id")) mc.displayGuiScreen(new ToolboxRadialScreen(boxes, link));
    }
    @SubscribeEvent public static void hotbar(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.HOTBAR) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.gameSettings.hideGUI || mc.player.isSpectator()) return;
        NBTTagCompound links = ToolboxLinks.snapshot(mc.player);
        int left = event.getResolution().getScaledWidth() / 2 - 90, top = event.getResolution().getScaledHeight() - 23;
        mc.getTextureManager().bindTexture(new net.minecraft.util.ResourceLocation("create", "textures/gui/widgets.png"));
        net.minecraft.client.renderer.GlStateManager.color(1, 1, 1, 1);
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        for (int slot = 0; slot < 9; slot++) {
            String key = Integer.toString(slot);
            if (!links.hasKey(key, 10)) continue;
            NBTTagCompound link = links.getCompoundTag(key);
            net.minecraft.util.math.BlockPos pos = net.minecraft.util.math.BlockPos.fromLong(link.getLong("Pos"));
            boolean available = link.getInteger("Dimension") == mc.player.dimension && mc.world.isBlockLoaded(pos)
                    && mc.player.getDistanceSq(pos) < 100D && mc.world.getTileEntity(pos) instanceof ToolboxTileEntity box
                    && link.hasUniqueId("Id") && link.getUniqueId("Id").equals(box.identity());
            boolean selected = mc.player.inventory.currentItem == slot;
            net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(left + 20 * slot - (selected ? 1 : 0), top + (selected ? 1 : 0),
                    available ? selected ? 22 : 20 : 0, selected ? 155 : 130, selected ? 22 : 20, selected ? 22 : 24, 256, 256);
        }
    }
}
