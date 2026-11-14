package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.logistics.table.TableClothPricePacket;
import com.cogwork.cogworkbridge.content.logistics.table.TableClothTileEntity;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Mouse;
import java.io.IOException;

/** The upstream table-cloth price board: one amount row, 1 to 100, release to confirm. */
public final class TableClothPriceScreen extends GuiScreen {
    private static final ResourceLocation TEXTURE=new ResourceLocation("create","textures/gui/value_settings.png");
    private final BlockPos pos;
    private int count=1,left,top,initialX,initialY;
    private boolean moved;
    public TableClothPriceScreen(BlockPos pos) {
        this.pos=pos.toImmutable();
        if (Minecraft.getMinecraft().world.getTileEntity(pos) instanceof TableClothTileEntity cloth) count=cloth.priceCount();
    }
    @Override public void initGui() {
        left=(width-238)/2; top=(height-63)/2;
        Mouse.setCursorPosition((left+74+Math.round((count-1)*150/99F))*mc.displayWidth/Math.max(1,width),
                (height-top-29)*mc.displayHeight/Math.max(1,height));
        Mouse.poll(); initialX=Mouse.getX()*width/Math.max(1,mc.displayWidth);
        initialY=height-1-Mouse.getY()*height/Math.max(1,mc.displayHeight);
    }
    private void send(int value) {
        int next=Math.max(1,Math.min(100,value));
        if (count==next) return;
        count=next; ModNetwork.CHANNEL.sendToServer(new TableClothPricePacket(pos,count));
    }
    private void select(int x,int y) {
        if (!moved && x==initialX && y==initialY) return;
        moved=true; send(1+Math.round(Math.max(0,Math.min(150,x-left-74))*99F/150));
    }
    @Override public void updateScreen() {
        select(Mouse.getX()*width/Math.max(1,mc.displayWidth),height-1-Mouse.getY()*height/Math.max(1,mc.displayHeight));
        if (!KineticMachineConfigClientEvents.useKeyDown(mc)) mc.displayGuiScreen(null);
    }
    @Override public void handleMouseInput() throws IOException {
        int wheel=Mouse.getEventDWheel();
        if (wheel!=0) send(count+(wheel>0?1:-1)*(isShiftKeyDown()?10:1));
        super.handleMouseInput();
    }
    @Override protected void mouseReleased(int x,int y,int button) {
        if (button-100==mc.gameSettings.keyBindUseItem.getKeyCode()) { select(x,y); mc.displayGuiScreen(null); }
    }
    private void stretch(int x,int y,int w,int h,int u,int v,int sw,int sh) {
        drawScaledCustomSizeModalRect(x,y,u,v,sw,sh,w,h,256,256);
    }
    @Override public void drawScreen(int x,int y,float partial) {
        drawDefaultBackground(); GlStateManager.color(1,1,1,1); GlStateManager.enableBlend();
        mc.getTextureManager().bindTexture(TEXTURE);
        stretch(left,top,238,63,80,9,1,1);
        stretch(left+70,top+20,158,3,0,24,256,3);
        stretch(left+70,top+36,158,3,0,27,256,3);
        stretch(left+70,top+23,3,13,65,14,3,4);
        stretch(left+225,top+23,3,13,71,14,3,4);
        stretch(left+74,top+23,150,11,7,0,249,8);
        for (int value:new int[]{1,10,20,30,40,50,60,70,80,90,100})
            drawTexturedModalRect(left+74+Math.round((value-1)*150/99F)-3,top+24,0,0,7,8);
        String number=Integer.toString(count); int at=left+74+Math.round((count-1)*150/99F),w=fontRenderer.getStringWidth(number)+4;
        stretch(at-w/2,top+22,w,14,4,9,56,14);
        fontRenderer.drawString(number,at-fontRenderer.getStringWidth(number)/2,top+25,0x442000);
        fontRenderer.drawString(I18n.format("gui.cogworkbridge.table_cloth.amount"),left+7,top+25,0xDDDDDD);
        String title=I18n.format("gui.cogworkbridge.table_cloth.price");
        fontRenderer.drawString(title,left+(238-fontRenderer.getStringWidth(title))/2,top+7,0xDDDDDD);
        String hint=I18n.format("gui.cogworkbridge.kinetic.release_to_confirm",mc.gameSettings.keyBindUseItem.getDisplayName());
        fontRenderer.drawString(hint,left+(238-fontRenderer.getStringWidth(hint))/2,top+48,0xDDDDDD);
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
