package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorRidePacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid=Reference.MOD_ID, value=Side.CLIENT)
public final class ChainConveyorRideClient {
    private static BlockPos wheel, end;
    private static float position;
    private static boolean reversed;
    private static int catchup;
    private ChainConveyorRideClient() { }
    public static void start(ChainConveyorTileEntity source, BlockPos target, float progress) {
        wheel=source.getPos(); end=target; position=progress; reversed=source.getSpeed()<0; catchup=20;
        ModNetwork.CHANNEL.sendToServer(new ChainConveyorRidePacket(wheel,end,position,false));
    }
    private static void stop() {
        if (wheel != null) ModNetwork.CHANNEL.sendToServer(new ChainConveyorRidePacket(wheel,end,position,true));
        wheel=null; end=null;
    }
    private static float wrap(float value) { value %= 360F; return value < 0 ? value+360F : value; }
    private static boolean crossed(float from,float delta,float to) { return delta>=0 ? wrap(to-from)<=delta : wrap(from-to)<=-delta; }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || wheel==null) return;
        Minecraft mc=Minecraft.getMinecraft(); if(mc.player==null || mc.world==null) { wheel=null; end=null; return; }
        if(mc.isGamePaused()) return;
        if(mc.player.isSneaking() || mc.player.getHeldItemMainhand().getItem()!=AllItems.WRENCH && mc.player.getHeldItemOffhand().getItem()!=AllItems.WRENCH
                || !mc.world.isBlockLoaded(wheel) || !(mc.world.getTileEntity(wheel) instanceof ChainConveyorTileEntity source)) { stop(); return; }
        float speed=source.getSpeed();
        if(end!=null) {
            if(!source.connections().contains(end)) { stop(); return; }
            var geometry=source.geometry(end);
            if(speed!=0 && reversed!=(speed<0)) {
                BlockPos old=wheel; wheel=end; end=old; position=Math.max(0,geometry.length()-position); reversed=speed<0;
                if(!mc.world.isBlockLoaded(wheel) || !(mc.world.getTileEntity(wheel) instanceof ChainConveyorTileEntity)) { stop(); return; }
                source=(ChainConveyorTileEntity)mc.world.getTileEntity(wheel); geometry=source.geometry(end);
            }
            position=Math.min(geometry.length(),position+Math.abs(speed)/360F);
            if(position>=geometry.length() && mc.world.isBlockLoaded(end) && mc.world.getTileEntity(end) instanceof ChainConveyorTileEntity next) {
                wheel=end; end=null; position=geometry.endAngle(); source=next;
            }
        } else {
            float delta=speed/(float)(Math.PI*1.5), previous=position; position=wrap(position+delta);
            BlockPos chosen=null; double best=Double.MAX_VALUE; Vec3d look=mc.player.getLookVec().normalize();
            for(BlockPos candidate:source.connections()) {
                Vec3d d=new Vec3d(candidate.subtract(wheel)).normalize(); double score=d.squareDistanceTo(look);
                if(score<best) {best=score; chosen=candidate;}
            }
            if(chosen!=null && crossed(previous,delta,source.geometry(chosen).angle())) {end=chosen; position=0; reversed=speed<0;}
        }
        Vec3d target;
        if(end!=null) { var g=source.geometry(end); target=g.start().add(g.end().subtract(g.start()).scale(Math.min(1,position/Math.max(.001F,g.length())))); }
        else { double angle=Math.toRadians(position); target=new Vec3d(wheel.getX()+.5+Math.sin(angle),wheel.getY()+.25,wheel.getZ()+.5+Math.cos(angle)); }
        Vec3d playerTop=new Vec3d(mc.player.posX,mc.player.posY+mc.player.height+.5,mc.player.posZ), diff=target.subtract(playerTop);
        if(catchup>0) catchup--; else if(diff.length()>3 || diff.y < -1) { stop(); return; }
        mc.player.motionX=mc.player.motionX*.75+diff.x*.25; mc.player.motionY=mc.player.motionY*.75+diff.y*.25; mc.player.motionZ=mc.player.motionZ*.75+diff.z*.25;
        mc.player.fallDistance=0;
        if(mc.player.ticksExisted%10==0) ModNetwork.CHANNEL.sendToServer(new ChainConveyorRidePacket(wheel,end,position,false));
    }
}
