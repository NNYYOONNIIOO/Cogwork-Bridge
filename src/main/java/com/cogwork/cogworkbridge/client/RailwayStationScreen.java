package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.trains.track.RailwayStationPacket;
import com.cogwork.cogworkbridge.content.trains.track.RailwayTargetTile;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import java.io.IOException;

/** Station and assembly panels using Create's station atlas and button layout. */
public final class RailwayStationScreen extends GuiScreen {
    private static final ResourceLocation STATION=new ResourceLocation("create","textures/gui/schedule_2.png");
    private static final ResourceLocation ASSEMBLY=new ResourceLocation("create","textures/gui/assemble.png");
    private static final ResourceLocation WIDGETS=new ResourceLocation("create","textures/gui/widgets.png");
    private static final String DEFAULT_STATION="Track Station";
    private final BlockPos pos;
    private final Boolean initialMode;
    private String draftName;
    private GuiTextField name;
    private int left,top;
    private boolean assembling,suppressClose;

    public RailwayStationScreen(BlockPos pos){this(pos,null,null);}
    private RailwayStationScreen(BlockPos pos,Boolean initialMode,String draftName) {
        this.pos=pos.toImmutable();this.initialMode=initialMode;this.draftName=draftName;
    }
    private RailwayTargetTile.Station station() {
        return mc.world!=null&&mc.world.getTileEntity(pos) instanceof RailwayTargetTile.Station s?s:null;
    }
    @Override public void initGui() {
        RailwayTargetTile.Station tile=station();
        if(tile==null){mc.displayGuiScreen(null);return;}
        assembling=initialMode!=null?initialMode:tile.assembling();
        if(draftName==null)draftName=tile.name();
        int h=assembling?178:127;
        left=(width-200)/2;top=(height-h)/2;
        buttonList.clear();name=null;
        if(!assembling) {
            name=new GuiTextField(0,fontRenderer,left+23,top+4,154,10);
            name.setEnableBackgroundDrawing(false);name.setTextColor(0x592424);name.setMaxStringLength(25);
            name.setText(DEFAULT_STATION.equals(draftName)?I18n.format("gui.cogworkbridge.station.default_name"):draftName);
            centerName();
            buttonList.add(new StationButton(1,left+(tile.trainPresent()?94:84),top+65,tile.trainPresent()?39:14));
        } else {
            buttonList.add(new CreateIconButton(1,left+73,top+154,1,0));
            buttonList.add(new StationButton(2,left+94,top+154,64));
        }
        buttonList.add(new CreateIconButton(0,left+167,top+h-24,0,1));
        Keyboard.enableRepeatEvents(true);
    }
    private void centerName() {
        if(name!=null)name.x=left+Math.max(23,(200-fontRenderer.getStringWidth(name.getText()))/2);
    }
    private String currentName() {
        if(name==null)return draftName;
        String value=name.getText();
        if(DEFAULT_STATION.equals(draftName)&&I18n.format("gui.cogworkbridge.station.default_name").equals(value))
            return DEFAULT_STATION;
        return value;
    }
    private void send(String value,boolean mode,boolean assemble) {
        if(mc.player!=null&&value!=null)ModNetwork.CHANNEL.sendToServer(new RailwayStationPacket(pos,value,mode,assemble));
    }
    private void switchMode(boolean next) {
        String value=currentName();send(value,next,false);suppressClose=true;
        mc.displayGuiScreen(new RailwayStationScreen(pos,next,value));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if(button.id==0){mc.displayGuiScreen(null);return;}
        if(button.id==1){switchMode(!assembling);return;}
        if(button.id==2){send(currentName(),true,true);suppressClose=true;mc.displayGuiScreen(null);}
    }
    @Override protected void keyTyped(char c,int key)throws IOException {
        if(name!=null&&(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER)&&name.isFocused()) {
            name.setFocused(false);return;
        }
        if(name!=null&&name.textboxKeyTyped(c,key)){centerName();return;}
        super.keyTyped(c,key);
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException {
        if(name!=null) {
            if(button==0&&y>=top&&y<top+14&&x>=left&&x<left+190) {
                name.setFocused(true);name.setCursorPositionEnd();return;
            }
            name.mouseClicked(x,y,button);
        }
        super.mouseClicked(x,y,button);
    }
    @Override public void updateScreen(){if(name!=null)name.updateCursorCounter();}
    @Override public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if(!suppressClose)send(currentName(),assembling,false);
        for(GuiButton button:buttonList)if(button instanceof CreateIconButton icon)icon.release();
    }
    @Override public void drawScreen(int x,int y,float partial) {
        drawDefaultBackground();GlStateManager.color(1F,1F,1F,1F);
        mc.getTextureManager().bindTexture(assembling?ASSEMBLY:STATION);
        if(assembling) {
            Gui.drawModalRectWithCustomSizedTexture(left,top,0,0,200,178,256,256);
            String title=I18n.format("gui.cogworkbridge.station.assembly");
            fontRenderer.drawString(title,left+100-fontRenderer.getStringWidth(title)/2,top+4,0x0e2233);
            fontRenderer.drawSplitString(I18n.format("gui.cogworkbridge.station.assembly_hint"),left+28,top+62,138,0x7a7a7a);
        } else {
            Gui.drawModalRectWithCustomSizedTexture(left,top,0,111,200,127,256,256);
            if(name!=null) {
                name.drawTextBox();
                if(!name.isFocused()) {
                    int editX=Math.min(left+170,name.x+fontRenderer.getStringWidth(name.getText())+5);
                    GlStateManager.color(1,1,1,1);mc.getTextureManager().bindTexture(STATION);
                    Gui.drawModalRectWithCustomSizedTexture(editX,top+1,0,239,13,13,256,256);
                }
            }
        }
        super.drawScreen(x,y,partial);
        for(GuiButton button:buttonList)if(button.isMouseOver()) {
            RailwayTargetTile.Station tile=station();
            String key=button.id==1?(assembling?"gui.cogworkbridge.station.cancel"
                    :tile!=null&&tile.trainPresent()?"gui.cogworkbridge.station.disassemble":"gui.cogworkbridge.station.new_train")
                    :button.id==2?"gui.cogworkbridge.station.assemble":null;
            if(key!=null)drawHoveringText(I18n.format(key),x,y);
        }
    }
    @Override public boolean doesGuiPauseGame(){return false;}

    /** Upstream's 26-pixel wide station buttons use icons from the station atlas. */
    private static final class StationButton extends GuiButton {
        private final int iconU;
        StationButton(int id,int x,int y,int iconU){super(id,x,y,26,18,"");this.iconU=iconU;}
        @Override public void drawButton(Minecraft mc,int mouseX,int mouseY,float partial) {
            if(!visible)return;
            hovered=mouseX>=x&&mouseY>=y&&mouseX<x+width&&mouseY<y+height;
            GlStateManager.color(1,1,1,1);GlStateManager.enableBlend();
            mc.getTextureManager().bindTexture(WIDGETS);
            int u=!enabled?90:hovered?18:0;
            Gui.drawModalRectWithCustomSizedTexture(x,y,u,0,18,18,256,256);
            Gui.drawModalRectWithCustomSizedTexture(x+9,y,u+1,0,17,18,256,256);
            mc.getTextureManager().bindTexture(STATION);
            Gui.drawModalRectWithCustomSizedTexture(x+1,y+1,iconU,239,24,16,256,256);
        }
    }
}
