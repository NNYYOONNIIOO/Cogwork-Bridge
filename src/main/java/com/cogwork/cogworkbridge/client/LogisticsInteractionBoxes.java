package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.funnel.FunnelBlock;
import com.cogwork.cogworkbridge.content.logistics.funnel.FunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.content.logistics.depot.DepotTileEntity;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BeltTunnelBlock;
import com.cogwork.cogworkbridge.content.logistics.chute.ChuteBlock;
import com.cogwork.cogworkbridge.content.logistics.crate.CreativeCrateBlock;
import com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeBlock;
import com.cogwork.cogworkbridge.content.redstone.RedstoneLinkBlock;
import com.cogwork.cogworkbridge.content.redstone.DetectorBlock;
import com.cogwork.cogworkbridge.content.redstone.PulseDiodeBlock;
import com.cogwork.cogworkbridge.content.kinetics.deployer.DeployerBlock;
import com.cogwork.cogworkbridge.content.contraptions.glue.client.SuperGlueOutlineRenderer;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class LogisticsInteractionBoxes {
    private LogisticsInteractionBoxes() { }

    @SubscribeEvent public static void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK) return;
        BlockPos pos = mc.objectMouseOver.getBlockPos();
        IBlockState state = mc.world.getBlockState(pos);
        boolean itemHeld = isUsableItem(mc.player.getHeldItemMainhand()) || isUsableItem(mc.player.getHeldItemOffhand());
        boolean emptyHands = mc.player.getHeldItemMainhand().isEmpty() && mc.player.getHeldItemOffhand().isEmpty();
        if (mc.world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.logistics.table.TableClothTileEntity cloth
                && cloth.isShop() && (itemHeld || emptyHands)) {
            Vec3d c=new Vec3d(pos).add(com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock.priceCenter(mc.world,pos,cloth));
            draw(c,com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock.priceOccluded(mc.world,pos,cloth)
                    ? EnumFacing.UP : cloth.facing(),.1875,.1875,event.getPartialTicks());
        } else if (state.getBlock() == com.cogwork.cogworkbridge.registry.AllBlocks.TRACK_OBSERVER && (itemHeld || emptyHands)) {
            draw(new Vec3d(pos).add(.5D,1.002D,.5D),EnumFacing.UP,.25D,.25D,event.getPartialTicks());
        } else if (state.getBlock() instanceof PulseDiodeBlock && emptyHands) {
            draw(new Vec3d(pos).add(.5D, 3D / 16D + .002D, .5D), EnumFacing.UP, .24D, .24D, event.getPartialTicks());
        } else if (state.getBlock() instanceof DetectorBlock && (itemHeld || emptyHands)) {
            for (EnumFacing side : EnumFacing.values()) {
                if (DetectorBlock.isFilterSide(state, side))
                    draw(new Vec3d(pos).add(DetectorBlock.filterCenter(side)), side, .15D, .15D, event.getPartialTicks());
            }
        } else if (state.getBlock() instanceof com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock
                && state.getValue(com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock.FACING) == EnumFacing.UP
                && mc.world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawTileEntity saw
                && (itemHeld || emptyHands)) {
            draw(new Vec3d(pos).add(com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock.filterCenter(state, saw.isFlipped())),
                    EnumFacing.UP, .15D, .15D, event.getPartialTicks());
        } else if (state.getBlock() instanceof DeployerBlock && (itemHeld || emptyHands)) {
            for (EnumFacing side : EnumFacing.values()) {
                if (!DeployerBlock.isFilterSide(state, side)) continue;
                Vec3d slot = new Vec3d(pos).add(DeployerBlock.filterCenter(state, side))
                        .add(side.getXOffset() * .002D, side.getYOffset() * .002D, side.getZOffset() * .002D);
                draw(slot, side, .20D, .20D, event.getPartialTicks());
            }
        } else if (state.getBlock() instanceof FunnelBlock funnel && funnel.isBrass()
                && (itemHeld || emptyHands)) {
            drawFunnel(pos, state.getValue(FunnelBlock.FACING), event.getPartialTicks());
        } else if (state.getBlock() instanceof BeltTunnelBlock tunnel && tunnel.isBrass()
                && (itemHeld || emptyHands)) {
            drawTunnel(pos, state.getValue(BeltTunnelBlock.AXIS), emptyHands, event.getPartialTicks());
        } else if (state.getBlock() instanceof ChuteBlock chute && chute.isSmart() && (itemHeld || emptyHands)) {
            for (EnumFacing side : EnumFacing.HORIZONTALS) {
                draw(center(pos).add(side.getXOffset() * 0.505D, 3D / 16D,
                        side.getZOffset() * 0.505D), side, 0.2D, 0.225D, event.getPartialTicks());
            }
        } else if (state.getBlock() instanceof SmartFluidPipeBlock && (itemHeld || emptyHands)) {
            Vec3d offset = SmartFluidPipeBlock.filterCenter(state);
            drawOriented(new Vec3d(pos).add(offset), SmartFluidPipeBlock.filterYaw(state),
                    SmartFluidPipeBlock.filterPitch(state), 0.15D, -1D / 32D, event.getPartialTicks());
        } else if (state.getBlock() instanceof RedstoneLinkBlock && (itemHeld || emptyHands)
                && mc.player.getHeldItemMainhand().getItem() != AllItems.LINKED_CONTROLLER
                && mc.player.getHeldItemOffhand().getItem() != AllItems.LINKED_CONTROLLER) {
            EnumFacing facing = state.getValue(RedstoneLinkBlock.FACING);
            for (int slot = 0; slot < 2; slot++) {
                Vec3d surface = new Vec3d(pos).add(RedstoneLinkBlock.frequencySlotCenter(facing, slot))
                        .add(facing.getXOffset() * .003D, facing.getYOffset() * .003D, facing.getZOffset() * .003D);
                draw(surface, facing, 2D / 16D, 2D / 16D, event.getPartialTicks());
            }
        } else if (state.getBlock() instanceof CreativeCrateBlock && (itemHeld || emptyHands)) {
            draw(new Vec3d(pos).add(0.5D, 14D / 16D + 0.002D, 0.5D),
                    EnumFacing.UP, 0.3D, 0.3D, event.getPartialTicks());
        }
    }

    private static boolean isUsableItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() != AllItems.WRENCH;
    }

    private static void drawFunnel(BlockPos pos, EnumFacing facing, float partial) {
        if (facing.getAxis().isHorizontal()) {
            Minecraft mc = Minecraft.getMinecraft();
            net.minecraft.tileentity.TileEntity surface = mc.world.getTileEntity(pos.down());
            if (!(surface instanceof BeltTileEntity) && !(surface instanceof DepotTileEntity)) {
                // The north face of the horizontal funnel model is rotated
                // -22.5 degrees around (y=0,z=12). Project the filter centre
                // onto that exact plane before rotating to the block's facing.
                double y = 12.2D / 16D;
                double faceZ = (12D - 12.2D * Math.tan(Math.toRadians(22.5D))) / 16D;
                double outward = 0.5D - faceZ;
                Vec3d slot = center(pos).add(facing.getXOffset() * outward, y - 0.5D,
                        facing.getZOffset() * outward);
                drawOriented(slot, -facing.getHorizontalAngle(), 22.5F,
                        0.125D, 1D / 128D, partial);
                return;
            }
            boolean aligned = surface instanceof BeltTileEntity belt
                    && (belt.step().getX() != 0 ? EnumFacing.Axis.X : EnumFacing.Axis.Z) == facing.getAxis();
            boolean extended = mc.world.getTileEntity(pos) instanceof FunnelTileEntity funnel && funnel.isExtended();
            double y = aligned ? (extended ? 15.5D : 13D) : 12.2D;
            double z = aligned ? (extended ? 13D : 7.5D) : 8.55D;
            Vec3d center = center(pos).add(facing.getXOffset() * (z / 16D - 0.5D),
                    y / 16D - 0.5D, facing.getZOffset() * (z / 16D - 0.5D));
            if (aligned && extended) {
                draw(center.add(0, 0.055D, 0), EnumFacing.UP, 0.12D, 0.12D, partial);
            } else {
                // The panel rises toward the exposed side.  In the local
                // frame +Z points outward, so its tilt has the opposite sign
                // to the model's rotation about its north-facing plane.
                drawTilted(center.add(facing.getXOffset() * 0.045D, 0.015D,
                        facing.getZOffset() * 0.045D), facing, aligned ? 0F : 22.5F, partial);
            }
            return;
        }
        double y = facing == EnumFacing.UP ? 2D / 16D : 14D / 16D;
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            Vec3d center = center(pos).add(side.getXOffset() * 0.515D, y - 0.5D,
                    side.getZOffset() * 0.515D);
            draw(center, side, 0.12D, 0.12D, partial);
        }
    }

    private static void drawTunnel(BlockPos pos, EnumFacing.Axis axis, boolean mode, float partial) {
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            Vec3d center = center(pos).add(side.getXOffset() * 0.515D, 0.3125D,
                    side.getZOffset() * 0.515D);
            draw(center, side, 0.12D, 0.12D, partial);
        }
        if (mode) {
            Vec3d center = center(pos).add(0, 0.502D, 0);
            draw(center, EnumFacing.UP, 0.2D, 0.2D, partial);
        }
    }

    private static Vec3d center(BlockPos pos) {
        return new Vec3d(pos).add(0.5D, 0.5D, 0.5D);
    }

    private static void drawTilted(Vec3d center, EnumFacing face, float tilt, float partial) {
        drawOriented(center, -face.getHorizontalAngle(), tilt, 0.12D, 0D, partial);
    }

    private static void drawOriented(Vec3d center, float yaw, float tilt, double size, double normalOffset, float partial) {
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        if (camera == null) return;
        double cx = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partial;
        double cy = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partial;
        double cz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partial;
        GlStateManager.pushMatrix();
        GlStateManager.translate(center.x - cx, center.y - cy, center.z - cz);
        GlStateManager.rotate(yaw, 0, 1, 0);
        GlStateManager.rotate(tilt, 1, 0, 0);
        GlStateManager.translate(0, 0, normalOffset);
        SuperGlueOutlineRenderer.renderFrame(new AxisAlignedBB(-size, -size, -0.0015D,
                size, size, 0.0015D), 0.95F, 0.95F, 0.75F, 0.85F, 1F / 32F);
        GlStateManager.popMatrix();
    }

    private static void draw(Vec3d center, EnumFacing face, double halfU, double halfV, float partial) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity camera = mc.getRenderViewEntity();
        if (camera == null) return;
        double cx = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partial;
        double cy = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partial;
        double cz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partial;
        double normal = 0.0015D;
        AxisAlignedBB box;
        if (face.getAxis() == EnumFacing.Axis.X) {
            box = new AxisAlignedBB(center.x - normal, center.y - halfV, center.z - halfU,
                    center.x + normal, center.y + halfV, center.z + halfU);
        } else if (face.getAxis() == EnumFacing.Axis.Y) {
            box = new AxisAlignedBB(center.x - halfU, center.y - normal, center.z - halfV,
                    center.x + halfU, center.y + normal, center.z + halfV);
        } else {
            box = new AxisAlignedBB(center.x - halfU, center.y - halfV, center.z - normal,
                    center.x + halfU, center.y + halfV, center.z + normal);
        }
        SuperGlueOutlineRenderer.renderFrame(box.offset(-cx, -cy, -cz), 0.95F, 0.95F, 0.75F, 0.85F, 1F / 32F);
    }
}
