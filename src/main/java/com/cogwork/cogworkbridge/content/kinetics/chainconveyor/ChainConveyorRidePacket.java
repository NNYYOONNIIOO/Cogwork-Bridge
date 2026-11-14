package com.cogwork.cogworkbridge.content.kinetics.chainconveyor;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.registry.AllItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ChainConveyorRidePacket implements IMessage {
    private long wheel, end;
    private float position;
    private boolean hasEnd, stop;
    public ChainConveyorRidePacket() { }
    public ChainConveyorRidePacket(BlockPos wheel, BlockPos end, float position, boolean stop) {
        this.wheel = wheel.toLong(); this.hasEnd = end != null; this.end = end == null ? 0 : end.toLong(); this.position = position; this.stop = stop;
    }
    @Override public void fromBytes(ByteBuf b) { wheel=b.readLong(); hasEnd=b.readBoolean(); if(hasEnd)end=b.readLong(); position=b.readFloat(); stop=b.readBoolean(); }
    @Override public void toBytes(ByteBuf b) { b.writeLong(wheel); b.writeBoolean(hasEnd); if(hasEnd)b.writeLong(end); b.writeFloat(position); b.writeBoolean(stop); }
    public static final class Handler implements IMessageHandler<ChainConveyorRidePacket,IMessage> {
        @Override public IMessage onMessage(ChainConveyorRidePacket p, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (p.stop) { Tracker.ACTIVE.remove(player.getUniqueID()); return; }
                BlockPos pos = BlockPos.fromLong(p.wheel), target = p.hasEnd ? BlockPos.fromLong(p.end) : null;
                if (!Float.isFinite(p.position) || player.isSneaking() || player.isSpectator()
                        || player.getHeldItemMainhand().getItem() != AllItems.WRENCH && player.getHeldItemOffhand().getItem() != AllItems.WRENCH
                        || !player.world.isBlockLoaded(pos) || !(player.world.getTileEntity(pos) instanceof ChainConveyorTileEntity wheel)) return;
                Vec3d point;
                if (target != null) {
                    if (!wheel.connections().contains(target)) return;
                    var geometry = wheel.geometry(target);
                    if (p.position < 0 || p.position > geometry.length() + .1F) return;
                    point = geometry.start().add(geometry.end().subtract(geometry.start()).scale(p.position / Math.max(.001F, geometry.length())));
                } else {
                    double angle = Math.toRadians(p.position);
                    point = new Vec3d(pos.getX() + .5 + Math.sin(angle), pos.getY() + .25, pos.getZ() + .5 + Math.cos(angle));
                }
                if (player.getDistanceSq(point.x, point.y, point.z) > 64D) return;
                Tracker.ACTIVE.put(player.getUniqueID(), player.world.getTotalWorldTime() + 25);
            });
            return null;
        }
    }
    @Mod.EventBusSubscriber(modid=Reference.MOD_ID)
    public static final class Tracker {
        private static final Map<UUID,Long> ACTIVE = new HashMap<>();
        private static final Field FLOATING_TICKS = floatingTicks();
        private static Field floatingTicks() {
            try { return ReflectionHelper.findField(NetHandlerPlayServer.class, "floatingTickCount", "field_147365_f"); }
            catch (RuntimeException ignored) { return null; }
        }
        @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof EntityPlayerMP player)) return;
            Long until = ACTIVE.get(player.getUniqueID()); if (until == null) return;
            if (player.world.getTotalWorldTime() > until || player.isSneaking()
                    || player.getHeldItemMainhand().getItem() != AllItems.WRENCH && player.getHeldItemOffhand().getItem() != AllItems.WRENCH) {
                ACTIVE.remove(player.getUniqueID()); return;
            }
            player.fallDistance = 0;
            if (FLOATING_TICKS != null) try { FLOATING_TICKS.setInt(player.connection, 0); } catch (IllegalAccessException ignored) { }
        }
        @SubscribeEvent public static void logout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent e) { ACTIVE.remove(e.player.getUniqueID()); }
    }
}
