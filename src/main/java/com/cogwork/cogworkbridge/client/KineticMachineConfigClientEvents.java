package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.kinetics.config.KineticMachineConfig;
import com.cogwork.cogworkbridge.content.contraptions.bearing.MechanicalBearingBlock;
import com.cogwork.cogworkbridge.content.contraptions.bearing.MechanicalBearingTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerBlock;
import com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.motor.CreativeMotorBlock;
import com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmBlock;
import com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.speedController.SpeedControllerBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class KineticMachineConfigClientEvents {
    private static BlockPos heldPos;
    private static int heldTicks;
    private static KineticMachineConfig heldConfig;
    private static EnumFacing heldSide;

    private KineticMachineConfigClientEvents() {
    }

    public static boolean hasTargetHint(IBlockState state, TileEntity tile, BlockPos target, RayTraceResult hit) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || hit == null) return false;
        KineticMachineConfig config = KineticMachineConfig.forTile(tile);
        boolean waiting = tile instanceof MechanicalBearingTileEntity bearing && bearing.isAwaitingUpdate();
        boolean sideControl = config != null && hit.sideHit != null && !mc.player.isSneaking()
                && (config == KineticMachineConfig.CART_ASSEMBLER
                    || mc.player.getHeldItemMainhand().isEmpty() && mc.player.getHeldItemOffhand().isEmpty())
                && hitsControl(state, target, config, hit.sideHit, hit.hitVec);
        boolean materialControl = tile instanceof MechanicalRollerTileEntity && state.getBlock() instanceof MechanicalRollerBlock
                && !mc.player.isSneaking() && MechanicalRollerBlock.hitsSetting(state, target, hit.sideHit, hit.hitVec, true);
        return waiting || sideControl || materialControl;
    }

    @SubscribeEvent
    public static void renderHint(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL
                || mc.player == null || mc.world == null || mc.currentScreen != null
                || mc.gameSettings.hideGUI) {
            return;
        }
        CartControl cart = findCartControl(mc);
        if (cart == null && (mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK)) return;
        BlockPos target = cart != null ? cart.pos : mc.objectMouseOver.getBlockPos();
        TileEntity tile = mc.world.getTileEntity(target);
        KineticMachineConfig config = KineticMachineConfig.forTile(tile);
        boolean waiting = tile instanceof MechanicalBearingTileEntity bearing
                && bearing.isAwaitingUpdate();
        EnumFacing side = cart != null ? cart.side : mc.objectMouseOver.sideHit;
        Vec3d hit = cart != null ? cart.hit : mc.objectMouseOver.hitVec;
        IBlockState state = mc.world.getBlockState(target);
        boolean sideControl = config != null && side != null && !mc.player.isSneaking()
                && (config == KineticMachineConfig.CART_ASSEMBLER
                    || mc.player.getHeldItemMainhand().isEmpty() && mc.player.getHeldItemOffhand().isEmpty())
                && hitsControl(state, target, config, side, hit);
        boolean materialControl = tile instanceof MechanicalRollerTileEntity
                && state.getBlock() instanceof MechanicalRollerBlock && !mc.player.isSneaking()
                && MechanicalRollerBlock.hitsSetting(state, target, side, hit, true);
        if (!waiting && !sideControl && !materialControl) {
            return;
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (waiting) {
            lines.add(I18n.format("gui.cogworkbridge.kinetic.bearing.awaiting"));
            lines.add(I18n.format("gui.cogworkbridge.kinetic.bearing.awaiting_hint"));
        }
        if (sideControl) {
            if (config == KineticMachineConfig.MECHANICAL_BEARING) {
                String modeKey = switch (((MechanicalBearingTileEntity) tile).getRotationMode()) {
                    case ROTATE_PLACE -> "gui.cogworkbridge.kinetic.bearing.rotate_place";
                    case ROTATE_PLACE_RETURNED -> "gui.cogworkbridge.kinetic.bearing.rotate_return";
                    case ROTATE_NEVER_PLACE -> "gui.cogworkbridge.kinetic.bearing.never_place";
                };
                lines.add(I18n.format("gui.cogworkbridge.kinetic.bearing.current",
                        I18n.format(modeKey)));
            } else if (config == KineticMachineConfig.MECHANICAL_ROLLER) {
                String modeKey = switch (((MechanicalRollerTileEntity) tile).getMode()) {
                    case TUNNEL_PAVE -> "gui.cogworkbridge.kinetic.roller.tunnel_pave";
                    case STRAIGHT_FILL -> "gui.cogworkbridge.kinetic.roller.straight_fill";
                    case WIDE_FILL -> "gui.cogworkbridge.kinetic.roller.wide_fill";
                };
                lines.add(I18n.format("gui.cogworkbridge.kinetic.roller.current", I18n.format(modeKey)));
            } else if (config == KineticMachineConfig.MECHANICAL_ARM) {
                lines.add(I18n.format("gui.cogworkbridge.arm.current",
                        I18n.format("cogworkbridge.arm.mode." + ((MechanicalArmTileEntity) tile).getSelectionMode())));
            } else if (config == KineticMachineConfig.MECHANICAL_PISTON) {
                lines.add(I18n.format("cogworkbridge.pulley.mode." + config.read(tile)));
            } else if (config == KineticMachineConfig.CART_ASSEMBLER) {
                lines.add(I18n.format("cogworkbridge.cart.mode." + config.read(tile)));
            } else if (config == KineticMachineConfig.WINDMILL_BEARING || config == KineticMachineConfig.STEAM_ENGINE) {
                lines.add(I18n.format(config.read(tile) == 0 ? "gui.cogworkbridge.kinetic.clockwise" : "gui.cogworkbridge.kinetic.counter_clockwise"));
            } else if (config == KineticMachineConfig.VALVE_HANDLE) {
                lines.add(I18n.format("gui.cogworkbridge.kinetic.valve_handle") + ": " + config.read(tile) + "\u00b0");
            } else {
                lines.add(I18n.format("gui.cogworkbridge.kinetic.current_speed", config.read(tile)));
            }
            lines.add(I18n.format("gui.cogworkbridge.kinetic.hold_to_configure",
                    mc.gameSettings.keyBindUseItem.getDisplayName()));
        }
        if (materialControl) {
            ItemStack material = ((MechanicalRollerTileEntity) tile).getMaterial();
            lines.add(I18n.format("gui.cogworkbridge.kinetic.roller.material",
                    material.isEmpty() ? I18n.format("gui.cogworkbridge.kinetic.roller.no_material")
                            : material.getDisplayName()));
            lines.add(I18n.format("gui.cogworkbridge.kinetic.roller.material_hint"));
        }
        CreateOverlayTooltip.draw(lines, event.getResolution(), event.getResolution().getScaledWidth() / 2 + 16,
                event.getResolution().getScaledHeight() / 2 + 12, ItemStack.EMPTY);
    }

    @SubscribeEvent
    public static void renderControlFrame(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.currentScreen != null
                || mc.gameSettings.hideGUI || mc.player.isSneaking()) {
            return;
        }
        CartControl cart = findCartControl(mc);
        if (cart == null && (mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK)) return;
        BlockPos pos = cart != null ? cart.pos : mc.objectMouseOver.getBlockPos();
        EnumFacing side = cart != null ? cart.side : mc.objectMouseOver.sideHit;
        Vec3d hit = cart != null ? cart.hit : mc.objectMouseOver.hitVec;
        IBlockState state = mc.world.getBlockState(pos);
        KineticMachineConfig config = KineticMachineConfig.forTile(mc.world.getTileEntity(pos));
        boolean emptyHands = mc.player.getHeldItemMainhand().isEmpty()
                && mc.player.getHeldItemOffhand().isEmpty();
        if (!emptyHands && config != KineticMachineConfig.MECHANICAL_ROLLER
                && config != KineticMachineConfig.CART_ASSEMBLER) {
            return;
        }
        Vec3d center = controlCenter(state, pos, config, side);
        Entity view = mc.getRenderViewEntity();
        if (center == null || view == null) {
            return;
        }
        double x = view.lastTickPosX + (view.posX - view.lastTickPosX) * event.getPartialTicks();
        double y = view.lastTickPosY + (view.posY - view.lastTickPosY) * event.getPartialTicks();
        double z = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * event.getPartialTicks();
        if (config == KineticMachineConfig.MECHANICAL_ROLLER) {
            if (emptyHands) {
                drawControlFrame(center, side, 2.25D / 16.0D,
                        hitsControl(state, pos, config, side, hit), x, y, z);
            }
            Vec3d filter = MechanicalRollerBlock.settingCenter(state, pos, true);
            drawControlFrame(filter, side, 2.25D / 16.0D,
                    MechanicalRollerBlock.hitsSetting(state, pos, side, hit, true),
                    x, y, z);
            return;
        }
        drawControlFrame(center, side, 0.25D,
                hitsControl(state, pos, config, side, hit), x, y, z);
    }

    private static final class CartControl {
        private final BlockPos pos;
        private final EnumFacing side;
        private final Vec3d hit;
        private CartControl(BlockPos pos, EnumFacing side, Vec3d hit) {
            this.pos = pos; this.side = side; this.hit = hit;
        }
    }

    /** The panel protrudes into the adjacent cell; vanilla's block traversal can miss it. */
    private static CartControl findCartControl(Minecraft mc) {
        Entity view = mc.getRenderViewEntity();
        if (view == null || mc.world == null || mc.playerController == null) return null;
        float partial = mc.getRenderPartialTicks();
        Vec3d eyes = view.getPositionEyes(partial);
        Vec3d direction = view.getLook(partial);
        double reach = mc.playerController.getBlockReachDistance();
        Vec3d end = eyes.add(direction.scale(reach));
        double limitSq = reach * reach;
        if (mc.objectMouseOver != null && mc.objectMouseOver.hitVec != null
                && mc.objectMouseOver.typeOfHit != RayTraceResult.Type.MISS)
            limitSq = Math.min(limitSq, eyes.squareDistanceTo(mc.objectMouseOver.hitVec) + .01D);
        Set<BlockPos> visited = new HashSet<>();
        CartControl nearest = null;
        int steps = (int) Math.ceil(reach * 4D);
        for (int i = 0; i <= steps; i++) {
            BlockPos cell = new BlockPos(eyes.add(direction.scale(Math.min(reach, i / 4D))));
            for (int candidate = -1; candidate < 4; candidate++) {
                BlockPos pos = candidate < 0 ? cell : cell.offset(EnumFacing.byHorizontalIndex(candidate));
                if (!visited.add(pos) || !mc.world.isBlockLoaded(pos)) continue;
                IBlockState state = mc.world.getBlockState(pos);
                if (!(state.getBlock() instanceof com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock)
                        || KineticMachineConfig.forTile(mc.world.getTileEntity(pos)) != KineticMachineConfig.CART_ASSEMBLER)
                    continue;
                for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
                    Vec3d center = controlCenter(state, pos, KineticMachineConfig.CART_ASSEMBLER, side);
                    if (center == null) continue;
                    double hx = side.getAxis() == EnumFacing.Axis.X ? .002D : .25D;
                    double hz = side.getAxis() == EnumFacing.Axis.Z ? .002D : .25D;
                    RayTraceResult hit = new AxisAlignedBB(center.x - hx, center.y - .25D, center.z - hz,
                            center.x + hx, center.y + .25D, center.z + hz).calculateIntercept(eyes, end);
                    if (hit == null) continue;
                    double distanceSq = eyes.squareDistanceTo(hit.hitVec);
                    if (distanceSq <= limitSq) {
                        limitSq = distanceSq;
                        nearest = new CartControl(pos, side, hit.hitVec);
                    }
                }
            }
        }
        return nearest;
    }

    private static void drawControlFrame(Vec3d center, EnumFacing side, double half,
                                         boolean highlighted, double x, double y, double z) {
        double halfX = side.getAxis() == EnumFacing.Axis.X ? 0.001D : half;
        double halfY = side.getAxis() == EnumFacing.Axis.Y ? 0.001D : half;
        double halfZ = side.getAxis() == EnumFacing.Axis.Z ? 0.001D : half;
        AxisAlignedBB frame = new AxisAlignedBB(center.x - halfX, center.y - halfY,
                center.z - halfZ, center.x + halfX, center.y + halfY, center.z + halfZ)
                .offset(-x, -y, -z);
        boolean textureEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean lightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
        float lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.glLineWidth(highlighted ? 2.0F : 1.0F);
            float shade = highlighted ? 1.0F : 0.65F;
            com.cogwork.cogworkbridge.content.contraptions.glue.client.SuperGlueOutlineRenderer.renderFrame(
                    frame, shade, shade, shade, 1.0F, highlighted ? 1F / 24F : 1F / 32F);
        } finally {
            GlStateManager.glLineWidth(lineWidth);
            if (lightingEnabled) {
                GlStateManager.enableLighting();
            }
            if (textureEnabled) {
                GlStateManager.enableTexture2D();
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static Vec3d controlCenter(IBlockState state, BlockPos pos,
                                       KineticMachineConfig config, EnumFacing side) {
        if (config == null || side == null) {
            return null;
        }
        Vec3d center = new Vec3d(pos).add(0.5D, 0.5D, 0.5D)
                .add(new Vec3d(side.getDirectionVec()).scale(0.502D));
        if (config == KineticMachineConfig.CART_ASSEMBLER
                && state.getBlock() instanceof com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock) {
            boolean eastWest = state.getValue(com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock.SHAPE)
                    == net.minecraft.block.BlockRailBase.EnumRailDirection.EAST_WEST;
            if (!side.getAxis().isHorizontal() || (side.getAxis() == EnumFacing.Axis.X) == eastWest) return null;
            // The outer face extends 2.05 pixels beyond the block. Keep the
            // control frame outside that face so it is not hidden by the model.
            return center.add(0, -1D / 16D, 0).add(new Vec3d(side.getDirectionVec()).scale(2.05D / 16D));
        }
        if (config == KineticMachineConfig.MECHANICAL_ARM
                && state.getBlock() instanceof MechanicalArmBlock && side.getAxis().isHorizontal()) {
            double y = state.getValue(MechanicalArmBlock.CEILING) ? 13D / 16D : 3D / 16D;
            return new Vec3d(pos).add(.5D, y, .5D).add(new Vec3d(side.getDirectionVec()).scale(.502D));
        }
        if (config == KineticMachineConfig.MECHANICAL_BEARING
                && state.getBlock() instanceof MechanicalBearingBlock) {
            EnumFacing facing = state.getValue(MechanicalBearingBlock.FACING);
            return side.getAxis() == facing.getAxis() ? null
                    : center.subtract(new Vec3d(facing.getDirectionVec()).scale(0.125D));
        }
        if (config == KineticMachineConfig.MECHANICAL_PISTON
                && state.getBlock() instanceof com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock piston) {
            EnumFacing facing = state.getValue(com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock.FACING);
            return side.getAxis() == facing.getAxis() || side.getAxis() == piston.getAxis(state)
                    ? null : center.subtract(new Vec3d(facing.getDirectionVec()).scale(2D / 16D));
        }
        if (config == KineticMachineConfig.VALVE_HANDLE
                && state.getBlock() instanceof com.cogwork.cogworkbridge.content.kinetics.crank.ValveHandleBlock) {
            return side == state.getValue(com.cogwork.cogworkbridge.content.kinetics.crank.HandCrankBlock.FACING) ? center : null;
        }
        if (config == KineticMachineConfig.WINDMILL_BEARING
                && state.getBlock() instanceof MechanicalBearingBlock) {
            EnumFacing facing = state.getValue(MechanicalBearingBlock.FACING);
            return side.getAxis() == facing.getAxis() ? null : center.subtract(new Vec3d(facing.getDirectionVec()).scale(0.125D));
        }
        if (config == KineticMachineConfig.STEAM_ENGINE
                && state.getBlock() instanceof com.cogwork.cogworkbridge.content.kinetics.steam.SteamEngineBlock) {
            return side.getAxis() == state.getValue(com.cogwork.cogworkbridge.content.kinetics.steam.SteamEngineBlock.FACING).getAxis() ? null : center;
        }
        if (config == KineticMachineConfig.CREATIVE_MOTOR
                && state.getBlock() instanceof CreativeMotorBlock) {
            EnumFacing facing = state.getValue(CreativeMotorBlock.FACING);
            if (side.getAxis() == facing.getAxis()
                    || facing.getAxis().isHorizontal() && side == EnumFacing.DOWN) {
                return null;
            }
            return center.subtract(new Vec3d(facing.getDirectionVec()).scale(0.0625D));
        }
        if (config == KineticMachineConfig.SPEED_CONTROLLER
                && state.getBlock() instanceof SpeedControllerBlock controller
                && side.getAxis().isHorizontal() && side.getAxis() != controller.getAxis(state)) {
            return center.add(0.0D, 3.0D / 16.0D, 0.0D);
        }
        if (config == KineticMachineConfig.MECHANICAL_ROLLER
                && state.getBlock() instanceof MechanicalRollerBlock && side == EnumFacing.UP) {
            return MechanicalRollerBlock.settingCenter(state, pos, false);
        }
        return null;
    }

    private static boolean hitsControl(IBlockState state, BlockPos pos,
                                       KineticMachineConfig config, EnumFacing side,
                                       Vec3d hit) {
        Vec3d center = controlCenter(state, pos, config, side);
        if (config == KineticMachineConfig.MECHANICAL_ROLLER) {
            return center != null && MechanicalRollerBlock.hitsSetting(state, pos, side, hit, false);
        }
        if (center == null || hit == null) {
            return false;
        }
        double x = Math.abs(center.x - hit.x);
        double y = Math.abs(center.y - hit.y);
        double z = Math.abs(center.z - hit.z);
        return switch (side.getAxis()) {
            case X -> y <= 0.25D && z <= 0.25D;
            case Y -> x <= 0.25D && z <= 0.25D;
            case Z -> x <= 0.25D && y <= 0.25D;
        };
    }

    @SubscribeEvent
    public static void mouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getButton() < 0 || event.isCanceled()
                || event.getButton() - 100 != mc.gameSettings.keyBindUseItem.getKeyCode()) {
            return;
        }
        if (mc.player == null || mc.world == null || mc.currentScreen != null) {
            return;
        }
        if (!event.isButtonstate()) {
            if (heldPos != null) {
                if (heldConfig == KineticMachineConfig.VALVE_HANDLE || heldConfig == KineticMachineConfig.WINDMILL_BEARING) {
                    com.cogwork.cogworkbridge.network.ModNetwork.CHANNEL.sendToServer(
                            new com.cogwork.cogworkbridge.content.kinetics.config.KineticConfigChangePacket(heldPos, heldConfig, Integer.MAX_VALUE));
                }
                heldPos = null;
                heldTicks = 0;
                heldConfig = null;
                heldSide = null;
                event.setCanceled(true);
            }
            return;
        }
        if (beginInteraction(mc)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void keyboard(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        int code = mc.gameSettings.keyBindUseItem.getKeyCode();
        if (code <= 0 || !Keyboard.getEventKeyState() || Keyboard.getEventKey() != code
                || mc.player == null || mc.world == null || mc.currentScreen != null
                || heldPos != null || !beginInteraction(mc)) {
            return;
        }
        KeyBinding.setKeyBindState(code, false);
        while (mc.gameSettings.keyBindUseItem.isPressed()) {
            // Consume the normal use action while the configuration control is held.
        }
    }

    private static boolean beginInteraction(Minecraft mc) {
        EntityPlayerSP player = mc.player;
        ItemStack main = player.getHeldItemMainhand();
        ItemStack off = player.getHeldItemOffhand();
        CartControl cart = player.isSneaking() ? null : findCartControl(mc);
        if (cart != null) {
            heldPos = cart.pos.toImmutable();
            heldTicks = 0;
            heldConfig = KineticMachineConfig.CART_ASSEMBLER;
            heldSide = cart.side;
            return true;
        }
        if (!main.isEmpty() || !off.isEmpty() || mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK) {
            return false;
        }
        BlockPos target = mc.objectMouseOver.getBlockPos();
        TileEntity tile = mc.world.getTileEntity(target);
        KineticMachineConfig config = KineticMachineConfig.forTile(tile);
        EnumFacing side = mc.objectMouseOver.sideHit;
        if (config == null || side == null || player.isSneaking()) {
            return false;
        }
        IBlockState state = mc.world.getBlockState(target);
        if (!hitsControl(state, target, config, side, mc.objectMouseOver.hitVec)) {
            return false;
        }
        heldPos = target.toImmutable();
        heldTicks = 0;
        heldConfig = config;
        heldSide = side;
        return true;
    }

    static boolean useKeyDown(Minecraft mc) {
        int code = mc.gameSettings.keyBindUseItem.getKeyCode();
        if (code < 0) {
            int button = code + 100;
            return Mouse.isCreated() && button >= 0 && button < Mouse.getButtonCount()
                    && Mouse.isButtonDown(button);
        }
        return code > 0 && Keyboard.isCreated() && Keyboard.isKeyDown(code);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || heldPos == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!useKeyDown(mc) && (heldConfig == KineticMachineConfig.VALVE_HANDLE || heldConfig == KineticMachineConfig.WINDMILL_BEARING)) {
            com.cogwork.cogworkbridge.network.ModNetwork.CHANNEL.sendToServer(
                    new com.cogwork.cogworkbridge.content.kinetics.config.KineticConfigChangePacket(heldPos, heldConfig, Integer.MAX_VALUE));
        }
        EntityPlayerSP player = mc.player;
        WorldClient world = mc.world;
        boolean validTarget = false;
        if (player != null && world != null && heldConfig == KineticMachineConfig.CART_ASSEMBLER) {
            CartControl cart = findCartControl(mc);
            validTarget = !player.isSneaking() && cart != null
                    && heldPos.equals(cart.pos) && heldSide == cart.side;
        } else if (player != null && world != null && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
            validTarget = heldPos.equals(mc.objectMouseOver.getBlockPos())
                    && heldSide == mc.objectMouseOver.sideHit
                    && hitsControl(world.getBlockState(heldPos), heldPos, heldConfig,
                            heldSide, mc.objectMouseOver.hitVec)
                    && player.getHeldItemMainhand().isEmpty() && player.getHeldItemOffhand().isEmpty();
        }
        if (player == null || world == null || mc.currentScreen != null
                || !useKeyDown(mc)
                || !validTarget
                || heldConfig != KineticMachineConfig.forTile(world.getTileEntity(heldPos))
                ) {
            heldPos = null;
            heldTicks = 0;
            heldConfig = null;
            heldSide = null;
            return;
        }
        if (++heldTicks >= 6) {
            BlockPos target = heldPos;
            KineticMachineConfig config = heldConfig;
            TileEntity tile = world.getTileEntity(target);
            heldPos = null;
            heldTicks = 0;
            heldConfig = null;
            heldSide = null;
            mc.displayGuiScreen(new KineticMachineConfigScreen(target, config, config.read(tile)));
        }
    }
}
