package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.trains.track.RailwayCarriageEntity;
import com.cogwork.cogworkbridge.content.trains.track.TrainControlInputPacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

@Mod.EventBusSubscriber(modid=Reference.MOD_ID,value=Side.CLIENT)
public final class TrainControlClient {
    private static int carriage=-1,lastMask=-1,refresh,spawnGrace;
    private static BlockPos controlsPos;
    private TrainControlClient() { }
    public static void start(int entityId,BlockPos pos){carriage=entityId;if(pos!=null)controlsPos=pos.toImmutable();lastMask=-1;refresh=0;spawnGrace=40;}
    public static boolean isControlling(int entityId,BlockPos pos){return carriage==entityId&&pos.equals(controlsPos);}
    public static int inputMask(){return Math.max(0,lastMask);}
    private static boolean pressed(KeyBinding key){
        int code=key.getKeyCode();return code<0?Mouse.isButtonDown(code+100):code!=0&&Keyboard.isKeyDown(code);
    }
    private static void stop(){
        if(carriage>=0)ModNetwork.CHANNEL.sendToServer(new TrainControlInputPacket(carriage,0,true));
        carriage=-1;lastMask=-1;controlsPos=null;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||carriage<0)return;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player==null||mc.world==null){carriage=-1;controlsPos=null;return;}
        if(mc.currentScreen!=null||pressed(mc.gameSettings.keyBindSneak)||mc.player.isSpectator()){stop();return;}
        if(!(mc.world.getEntityByID(carriage) instanceof RailwayCarriageEntity)){if(spawnGrace-->0)return;stop();return;}
        spawnGrace=0;
        int mask=(pressed(mc.gameSettings.keyBindForward)?1:0)|(pressed(mc.gameSettings.keyBindBack)?2:0)
                |(pressed(mc.gameSettings.keyBindLeft)?4:0)|(pressed(mc.gameSettings.keyBindRight)?8:0)|(pressed(mc.gameSettings.keyBindJump)?16:0)
                |(pressed(mc.gameSettings.keyBindSprint)?32:0);
        if(mask!=lastMask||++refresh>=5){ModNetwork.CHANNEL.sendToServer(new TrainControlInputPacket(carriage,mask,false));lastMask=mask;refresh=0;}
    }
    @SubscribeEvent public static void preventMovement(InputUpdateEvent event){
        if(carriage<0||event.getEntityPlayer()!=Minecraft.getMinecraft().player)return;
        event.getMovementInput().moveForward=0F;event.getMovementInput().moveStrafe=0F;event.getMovementInput().jump=false;
    }
    @SubscribeEvent public static void hint(RenderGameOverlayEvent.Post event){
        if(carriage<0||event.getType()!=RenderGameOverlayEvent.ElementType.ALL)return;
        Minecraft mc=Minecraft.getMinecraft();
        String hint=I18n.format("cogworkbridge.train.controls_hint",mc.gameSettings.keyBindJump.getDisplayName(),
                mc.gameSettings.keyBindSprint.getDisplayName(),mc.gameSettings.keyBindSneak.getDisplayName());
        var lines=mc.fontRenderer.listFormattedStringToWidth(hint,Math.max(1,event.getResolution().getScaledWidth()-20));
        int y=event.getResolution().getScaledHeight()-55-(lines.size()-1)*mc.fontRenderer.FONT_HEIGHT;
        for(String line:lines) {
            mc.fontRenderer.drawStringWithShadow(line,(event.getResolution().getScaledWidth()-mc.fontRenderer.getStringWidth(line))/2F,y,0xffffff);
            y+=mc.fontRenderer.FONT_HEIGHT;
        }
    }
}
