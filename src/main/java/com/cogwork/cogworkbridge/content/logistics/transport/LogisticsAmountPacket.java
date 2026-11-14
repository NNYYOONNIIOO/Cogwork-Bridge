package com.cogwork.cogworkbridge.content.logistics.transport;

import com.cogwork.cogworkbridge.content.logistics.chute.SmartChuteTileEntity;
import com.cogwork.cogworkbridge.content.logistics.ejector.EjectorTileEntity;
import com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.chassis.ChassisTileEntity;
import com.cogwork.cogworkbridge.registry.AllItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class LogisticsAmountPacket implements IMessage {
    private BlockPos pos; private int delta; private boolean bulk;
    public LogisticsAmountPacket() { }
    public LogisticsAmountPacket(BlockPos pos, int delta) { this.pos = pos; this.delta = delta; }
    public LogisticsAmountPacket(BlockPos pos, int delta, boolean bulk) { this.pos = pos; this.delta = delta; this.bulk = bulk; }
    @Override public void fromBytes(ByteBuf buffer) { pos = BlockPos.fromLong(buffer.readLong()); delta = buffer.readByte(); bulk = buffer.readBoolean(); }
    @Override public void toBytes(ByteBuf buffer) { buffer.writeLong(pos.toLong()); buffer.writeByte(delta); buffer.writeBoolean(bulk); }
    public static final class Handler implements IMessageHandler<LogisticsAmountPacket, IMessage> {
        @Override public IMessage onMessage(LogisticsAmountPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.pos == null || Math.abs(message.delta) != 1
                        || player.getDistanceSq(message.pos) > 64 || !player.world.isBlockLoaded(message.pos)) return;
                TileEntity tile = player.world.getTileEntity(message.pos); int value;
                if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerTileEntity assembler) {
                    if (!player.world.isBlockModifiable(player, message.pos) || player.isSneaking()) return;
                    net.minecraft.util.math.Vec3d eyes = player.getPositionEyes(1F);
                    net.minecraft.util.math.RayTraceResult hit = player.world.rayTraceBlocks(eyes,
                            eyes.add(player.getLook(1F).scale(player.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue())),
                            false, true, false);
                    net.minecraft.block.state.IBlockState state = player.world.getBlockState(message.pos);
                    if (hit == null || !message.pos.equals(hit.getBlockPos())
                            || !(state.getBlock() instanceof com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock)) return;
                    net.minecraft.util.EnumFacing.Axis side = hit.sideHit.getAxis();
                    boolean eastWest = state.getValue(com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock.SHAPE)
                            == net.minecraft.block.BlockRailBase.EnumRailDirection.EAST_WEST;
                    if (!side.isHorizontal() || (side == net.minecraft.util.EnumFacing.Axis.X) == eastWest) return;
                    assembler.setMovementMode(assembler.getMovementMode() + message.delta);
                    player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.cart.mode." + assembler.getMovementMode()), true);
                    return;
                }
                if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.bearing.ClockworkBearingTileEntity
                        || tile instanceof com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonTileEntity) {
                    if (!player.world.isBlockModifiable(player, message.pos) || player.isSneaking()) return;
                    net.minecraft.util.math.Vec3d eyes = player.getPositionEyes(1F);
                    net.minecraft.util.math.RayTraceResult hit = player.world.rayTraceBlocks(eyes,
                            eyes.add(player.getLook(1F).scale(player.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue())),
                            false, true, false);
                    net.minecraft.block.state.IBlockState state = player.world.getBlockState(message.pos);
                    if (hit == null || !message.pos.equals(hit.getBlockPos())
                            || !(state.getBlock() instanceof com.cogwork.cogworkbridge.kinetic.KineticBlock kinetic)
                            || hit.sideHit.getAxis() == kinetic.getAxis(state)) return;
                    if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonTileEntity piston) {
                        if (hit.sideHit.getAxis() == state.getValue(com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock.FACING).getAxis()) return;
                        piston.setMovementMode(piston.getMovementMode() + message.delta);
                        player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.pulley.mode." + piston.getMovementMode()), true);
                    } else if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.bearing.ClockworkBearingTileEntity clock) {
                        clock.setMode(clock.getMode() + message.delta);
                        player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.clockwork.mode." + clock.getMode()), true);
                    }
                    return;
                }
                if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.pulley.RopePulleyTileEntity pulley) {
                    if (!player.world.isBlockModifiable(player, message.pos) || player.isSneaking()) return;
                    net.minecraft.util.math.Vec3d eyes = player.getPositionEyes(1F);
                    net.minecraft.util.math.RayTraceResult hit = player.world.rayTraceBlocks(eyes,
                            eyes.add(player.getLook(1F).scale(player.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue())),
                            false, true, false);
                    if (hit == null || !message.pos.equals(hit.getBlockPos()) || hit.sideHit != net.minecraft.util.EnumFacing.UP) return;
                    pulley.setMovementMode(pulley.getMovementMode() + message.delta);
                    player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.pulley.mode." + pulley.getMovementMode()), true);
                    return;
                }
                if (tile instanceof ChassisTileEntity chassis) {
                    boolean wrench = player.getHeldItemMainhand().getItem() == AllItems.WRENCH
                            || player.getHeldItemOffhand().getItem() == AllItems.WRENCH;
                    if (!wrench || !player.world.isBlockModifiable(player, message.pos)) return;
                    int range = Math.max(1, Math.min(ChassisTileEntity.MAX_RANGE, chassis.getRange() + message.delta));
                    if (message.bulk) {
                        for (ChassisTileEntity member : chassis.collectChassisGroup())
                            if (player.world.isBlockModifiable(player, member.getPos())) member.setRange(range);
                    } else {
                        chassis.setRange(range);
                    }
                    player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.chassis.range", chassis.getRange()), true);
                    return;
                }
                if (tile instanceof EjectorTileEntity ejector) {
                    boolean wrench = player.getHeldItemMainhand().getItem() == AllItems.WRENCH
                            || player.getHeldItemOffhand().getItem() == AllItems.WRENCH;
                    if (!wrench) return;
                    value = Math.max(1, Math.min(64, ejector.getMaxStack() + message.delta));
                    ejector.setMaxStack(value);
                    player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.logistics.amount", Integer.toString(value)), true);
                    return;
                }
                if (!player.isSneaking() || !player.getHeldItemMainhand().isEmpty()
                        || !player.getHeldItemOffhand().isEmpty()) return;
                if (tile instanceof SmartChuteTileEntity chute) {
                    value = Math.max(0, Math.min(64, chute.getAmount() + message.delta)); chute.setAmount(value);
                } else if (tile instanceof MechanicalArmTileEntity arm) {
                    if (message.delta == 0) return;
                    arm.changeMode(player, message.delta);
                    return;
                } else return;
                player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.logistics.amount", value == 0 ? "*" : Integer.toString(value)), true);
            });
            return null;
        }
    }
}
