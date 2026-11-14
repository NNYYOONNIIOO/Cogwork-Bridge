package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.funnel.BrassFunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.funnel.FunnelBlock;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BrassTunnelTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class FunnelConfigClientEvents {
    private static BlockPos heldPos;
    private static EnumFacing heldSide;
    private static int heldTicks;

    private FunnelConfigClientEvents() { }

    private static boolean matches(Minecraft mc, BlockPos pos, EnumFacing side) {
        RayTraceResult hit = mc.objectMouseOver;
        if (mc.player == null || mc.world == null || hit == null || hit.hitVec == null
                || hit.typeOfHit != RayTraceResult.Type.BLOCK || !pos.equals(hit.getBlockPos())
                || side != hit.sideHit || mc.player.isSneaking()
                || !mc.player.getHeldItemMainhand().isEmpty() || !mc.player.getHeldItemOffhand().isEmpty()) return false;
        Vec3d local = hit.hitVec.subtract(pos.getX(), pos.getY(), pos.getZ());
        if (mc.world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.logistics.table.TableClothTileEntity cloth)
            return cloth.maySetPrice(mc.player) && com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock.targetsPrice(mc.world,pos,cloth,local);
        if (mc.world.getTileEntity(pos) instanceof BrassTunnelTileEntity) {
            if (side == EnumFacing.UP)
                return Math.abs(local.x - 0.5D) <= 0.2D && Math.abs(local.z - 0.5D) <= 0.2D;
            if (!side.getAxis().isHorizontal()) return false;
            double across = side.getAxis() == EnumFacing.Axis.X ? local.z : local.x;
            return Math.abs(across - 0.5D) <= 0.15D && Math.abs(local.y - 13D / 16D) <= 0.15D;
        }
        if (!(mc.world.getTileEntity(pos) instanceof BrassFunnelTileEntity)) return false;
        IBlockState state = mc.world.getBlockState(pos);
        if (!(state.getBlock() instanceof FunnelBlock)) return false;
        return FunnelBlock.isFilterSlot(mc.world, pos, state, side,
                (float) local.x, (float) local.y, (float) local.z);
    }

    private static boolean begin(Minecraft mc) {
        if (mc.currentScreen != null || mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK
                || mc.objectMouseOver.sideHit == null) return false;
        BlockPos pos = mc.objectMouseOver.getBlockPos();
        EnumFacing side = mc.objectMouseOver.sideHit;
        if (!matches(mc, pos, side)) return false;
        heldPos = pos.toImmutable();
        heldSide = side;
        heldTicks = 0;
        return true;
    }

    private static void clear() { heldPos = null; heldSide = null; heldTicks = 0; }

    @SubscribeEvent public static void mouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getButton() < 0 || event.isCanceled()
                || event.getButton() - 100 != mc.gameSettings.keyBindUseItem.getKeyCode()) return;
        if (!event.isButtonstate()) {
            if (heldPos != null) { clear(); event.setCanceled(true); }
        } else if (begin(mc)) event.setCanceled(true);
    }

    @SubscribeEvent public static void keyboard(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        if (key <= 0 || !Keyboard.getEventKeyState() || Keyboard.getEventKey() != key
                || heldPos != null || !begin(mc)) return;
        KeyBinding.setKeyBindState(key, false);
        while (mc.gameSettings.keyBindUseItem.isPressed()) { }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || heldPos == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || !KineticMachineConfigClientEvents.useKeyDown(mc)
                || !matches(mc, heldPos, heldSide)) { clear(); return; }
        if (++heldTicks >= 6) {
            BlockPos pos = heldPos;
            clear();
            if (mc.world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.logistics.table.TableClothTileEntity)
                mc.displayGuiScreen(new TableClothPriceScreen(pos));
            else if (mc.world.getTileEntity(pos) instanceof BrassTunnelTileEntity)
                mc.displayGuiScreen(new BrassTunnelConfigScreen(pos));
            else mc.displayGuiScreen(new BrassFunnelConfigScreen(pos));
        }
    }
}
