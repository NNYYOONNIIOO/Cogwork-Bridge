package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.schematics.SchematicActionPacket;
import com.cogwork.cogworkbridge.content.schematics.SchematicData;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Schematic tools share the upstream hold-menu and control-scroll interaction. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class SchematicToolClient {
    public static final KeyBinding TOOL_MENU = new KeyBinding("key.cogworkbridge.schematic_menu", Keyboard.KEY_LMENU,
            "key.categories.cogworkbridge");
    private static final ResourceLocation ICONS = new ResourceLocation("create", "textures/gui/icons.png");
    private static final int MOVE = 0, MOVE_Y = 1, DEPLOY = 2, ROTATE = 3, FLIP = 4, PRINT = 5;
    private static final String[] NAMES = {"move", "move_y", "deploy", "rotate", "flip", "print"};
    private static final int[] ICON_X = {0, 1, 0, 2, 3, 0};
    private static final int[] ICON_Y = {4, 4, 3, 4, 4, 1};
    private static int selected = DEPLOY;
    private SchematicToolClient() { }

    public static boolean isActive(Minecraft mc) {
        if (mc.player == null || mc.world == null || mc.player.isSpectator()) return false;
        ItemStack stack = mc.player.getHeldItemMainhand();
        return stack.getItem() == AllItems.SCHEMATIC && stack.hasTagCompound()
                && stack.getTagCompound().hasKey("FileId", 8);
    }
    private static boolean menuHeld() {
        int key = TOOL_MENU.getKeyCode();
        return key < 0 ? key + 100 >= 0 && key + 100 < Mouse.getButtonCount() && Mouse.isButtonDown(key + 100)
                : key > 0 && key < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(key);
    }
    private static boolean deployed(ItemStack stack) { return stack.getTagCompound().getBoolean("Deployed"); }
    private static void send(Minecraft mc, int action) {
        ModNetwork.CHANNEL.sendToServer(new SchematicActionPacket(action, 0, mc.player.inventory.currentItem));
    }
    private static AxisAlignedBB bounds(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        int[] range = SchematicData.horizontalBounds(tag.getInteger("Width"), tag.getInteger("Depth"),
                SchematicData.mirror(stack), SchematicData.rotation(stack));
        BlockPos anchor = SchematicData.anchor(stack);
        return new AxisAlignedBB(anchor.getX() + range[0], anchor.getY(), anchor.getZ() + range[2],
                anchor.getX() + range[1] + 1D, anchor.getY() + Math.max(1, tag.getInteger("Height")),
                anchor.getZ() + range[3] + 1D);
    }
    private static EnumFacing selectedFace(Minecraft mc, ItemStack stack) {
        Vec3d eyes = mc.player.getPositionEyes(1F);
        RayTraceResult hit = bounds(stack).grow(.001D).calculateIntercept(eyes, eyes.add(mc.player.getLook(1F).scale(75D)));
        return hit == null ? null : hit.sideHit;
    }
    private static int mirrorAction(ItemStack stack, EnumFacing face) {
        boolean x = face.getAxis() == EnumFacing.Axis.X;
        if ((stack.getTagCompound().getInteger("Rotation") & 1) != 0) x = !x;
        return x ? 1 : 31;
    }
    private static int directionAction(EnumFacing direction) {
        return switch (direction) {
            case WEST -> 4; case EAST -> 5; case DOWN -> 6; case UP -> 7; case NORTH -> 8; case SOUTH -> 9;
        };
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.isCanceled() || mc.currentScreen != null || !isActive(mc)) return;
        ItemStack stack = mc.player.getHeldItemMainhand();
        if (!deployed(stack)) selected = DEPLOY;
        if (selected == PRINT && !mc.player.capabilities.isCreativeMode) selected = MOVE;
        int wheel = event.getDwheel();
        if (wheel != 0 && menuHeld()) {
            if (deployed(stack)) {
                int count = mc.player.capabilities.isCreativeMode ? 6 : 5;
                selected = Math.floorMod(selected + (wheel < 0 ? 1 : -1), count);
            }
            event.setCanceled(true); return;
        }
        if (wheel != 0 && GuiScreen.isCtrlKeyDown() && deployed(stack)) {
            int sign = wheel > 0 ? 1 : -1;
            EnumFacing face = selectedFace(mc, stack);
            if (selected == MOVE_Y) send(mc, sign > 0 ? 7 : 6);
            else if (selected == ROTATE) send(mc, sign > 0 ? 0 : 30);
            else if (selected == MOVE && face != null && face.getAxis().isHorizontal())
                send(mc, directionAction(sign > 0 ? face.getOpposite() : face));
            else if (selected == FLIP && face != null && face.getAxis().isHorizontal()) send(mc, mirrorAction(stack, face));
            else if (selected != MOVE && selected != FLIP) return;
            event.setCanceled(true); return;
        }
        if (event.getButton() != 1 || !event.isButtonstate() || mc.player.isSneaking() || selected == DEPLOY) return;
        RayTraceResult hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            net.minecraft.block.Block block = mc.world.getBlockState(hit.getBlockPos()).getBlock();
            if (block == AllBlocks.SCHEMATICANNON || block == AllBlocks.DEPLOYER) return;
        }
        if (selected == PRINT && mc.player.capabilities.isCreativeMode) send(mc, 15);
        if (selected == FLIP) {
            EnumFacing face = selectedFace(mc, stack);
            if (face != null && face.getAxis().isHorizontal()) send(mc, mirrorAction(stack, face));
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        event.setCanceled(true);
    }
    @SubscribeEvent public static void overlay(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || mc.currentScreen != null
                || mc.gameSettings.hideGUI || !isActive(mc)) return;
        ItemStack stack = mc.player.getHeldItemMainhand();
        if (!deployed(stack)) selected = DEPLOY;
        boolean focused = menuHeld();
        int count = !deployed(stack) ? 1 : mc.player.capabilities.isCreativeMode ? 6 : 5;
        if (selected >= count && count > 1) selected = MOVE;
        int width = Math.max(count * 50 + 30, 220), x = (event.getResolution().getScaledWidth() - width) / 2;
        int y = event.getResolution().getScaledHeight() - 105 - (focused ? 10 : 0);
        Gui.drawRect(x, y, x + width, y + 30, focused ? 0xDF202027 : 0x80202027);
        String hint = focused ? I18n.format("cogworkbridge.schematic.menu_cycle")
                : I18n.format("cogworkbridge.schematic.menu_focus", net.minecraft.client.settings.GameSettings.getKeyDisplayString(TOOL_MENU.getKeyCode()));
        mc.fontRenderer.drawStringWithShadow(hint, (event.getResolution().getScaledWidth() - mc.fontRenderer.getStringWidth(hint)) / 2F, y - 10, 0xCCDDFF);
        for (int i = 0; i < count; i++) {
            int tool = count == 1 ? DEPLOY : i;
            int px = x + 15 + (count == 1 ? 65 : i * 50), py = y + (tool == selected ? 1 : 11);
            GlStateManager.enableBlend(); GlStateManager.color(1, 1, 1, tool == selected || focused ? 1 : .2F);
            mc.getTextureManager().bindTexture(ICONS);
            Gui.drawModalRectWithCustomSizedTexture(px + 16, py, ICON_X[tool] * 16, ICON_Y[tool] * 16, 16, 16, 256, 256);
            if (tool == selected) {
                String name = I18n.format("cogworkbridge.schematic.tool." + NAMES[tool]);
                mc.fontRenderer.drawStringWithShadow(name, px + 24 - mc.fontRenderer.getStringWidth(name) / 2F, y + 18, 0xCCDDFF);
            }
        }
        if (focused) {
            String description = I18n.format("cogworkbridge.schematic.tool." + NAMES[selected] + ".description");
            Gui.drawRect(x, y + 33, x + width, y + 55, 0xDF202027);
            mc.fontRenderer.drawStringWithShadow(description, x + 5, y + 40, 0xCCDDFF);
        }
        GlStateManager.color(1, 1, 1, 1); GlStateManager.disableBlend();
    }
    @SubscribeEvent public static void renderBounds(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!isActive(mc) || !deployed(mc.player.getHeldItemMainhand())) return;
        AxisAlignedBB box = bounds(mc.player.getHeldItemMainhand()).offset(-mc.getRenderManager().viewerPosX,
                -mc.getRenderManager().viewerPosY, -mc.getRenderManager().viewerPosZ);
        GlStateManager.pushMatrix(); GlStateManager.disableTexture2D(); GlStateManager.enableBlend();
        GL11.glLineWidth(2F); RenderGlobal.drawSelectionBoundingBox(box, .40F, .53F, .77F, .85F);
        GL11.glLineWidth(1F); GlStateManager.disableBlend(); GlStateManager.enableTexture2D(); GlStateManager.popMatrix();
    }
}
