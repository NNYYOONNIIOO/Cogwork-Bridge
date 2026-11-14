package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.trains.track.TrainSchedule;
import com.cogwork.cogworkbridge.content.trains.track.TrainSchedulePacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Schedule cards and the upstream schedule editor, backed by the supported schedule fields. */
public final class TrainScheduleScreen extends GuiScreen {
    private static final ResourceLocation SCHEDULE=new ResourceLocation("create","textures/gui/schedule.png");
    private static final ResourceLocation EDITOR=new ResourceLocation("create","textures/gui/schedule_2.png");
    private static final int PAGE_SIZE=3, CARD_HEIGHT=52;
    private static final String DEFAULT_STATION="Track Station";
    private static final class Row {
        String name,seconds;
        TrainSchedule.Condition condition;
        TrainSchedule.Entry data;
        Row(String name,String seconds,TrainSchedule.Condition condition) {
            this.name=name;this.seconds=seconds;this.condition=condition;
            data=new TrainSchedule.Entry(name,0,condition);
        }
    }
    private final List<Row> rows=new ArrayList<>();
    private int page,left,top,slot,editing=-1;
    private boolean cyclic,initialized,editingCondition;
    private GuiTextField editorField;
    private int groupIndex,conditionIndex,selectingFilter=-1;
    private TrainSchedule.Wait selectedWait() {
        Row row=rows.get(editing);
        if(row.data.groups.isEmpty())row.data.groups.add(new ArrayList<>());
        groupIndex=Math.max(0,Math.min(row.data.groups.size()-1,groupIndex));
        List<TrainSchedule.Wait> group=row.data.groups.get(groupIndex);
        if(group.isEmpty())group.add(new TrainSchedule.Wait(TrainSchedule.Condition.DELAY,100));
        conditionIndex=Math.max(0,Math.min(group.size()-1,conditionIndex));return group.get(conditionIndex);
    }
    private String waitValue(TrainSchedule.Wait wait) {
        return wait.type==TrainSchedule.Condition.TIME_OF_DAY?String.format(Locale.ROOT,"%02d:%02d",wait.value/60%24,wait.value%60)
                : Integer.toString(wait.value/(wait.type==TrainSchedule.Condition.DELAY||wait.type==TrainSchedule.Condition.IDLE_CARGO?20:1));
    }
    private void storeField() {
        if(editing<0||editorField==null)return;
        Row row=rows.get(editing);String text=editorField.getText();
        if(!editingCondition) {
            if(row.data.instruction==TrainSchedule.Instruction.THROTTLE) {
                try{row.data.value=Math.max(0,Math.min(100,Integer.parseInt(text)));}catch(NumberFormatException ignored) { }
            } else {row.name=text;row.data.text=storedStation(text);}
            return;
        }
        var wait=selectedWait();
        try {
            int value;
            if(wait.type==TrainSchedule.Condition.TIME_OF_DAY) {String[] clock=text.split(":");value=Integer.parseInt(clock[0])*60+(clock.length>1?Integer.parseInt(clock[1]):0);value=Math.floorMod(value,1440);}
            else {value=Integer.parseInt(text);if(wait.type==TrainSchedule.Condition.DELAY||wait.type==TrainSchedule.Condition.IDLE_CARGO)value=Math.min(3600,Math.max(0,value))*20;}
            wait.value=Math.max(0,Math.min(1000000,value));
        }catch(NumberFormatException ignored) { }
        row.condition=row.data.first().type;row.seconds=Integer.toString(row.data.waitTicks()/20);
    }

    @Override public void initGui() {
        if(!initialized) {
            initialized=true;slot=mc.player.inventory.currentItem;
            TrainSchedule schedule=TrainSchedule.fromItem(mc.player.getHeldItemMainhand());cyclic=schedule.cyclic;
            for(TrainSchedule.Entry entry:schedule.entries) {
                Row row=new Row(displayStation(entry.destination()),Integer.toString(entry.waitTicks()/20),entry.condition());row.data=entry.copy();rows.add(row);
            }
            if(rows.isEmpty())rows.add(new Row("","0",TrainSchedule.Condition.DELAY));
        }
        left=(width-256)/2;top=(height-226)/2;
        buttonList.clear();editorField=null;
        if(editing>=0) {
            Row row=rows.get(editing);
            editorField=new GuiTextField(0,fontRenderer,left+77,top+91,120,14);
            editorField.setMaxStringLength(editingCondition?9:TrainSchedule.MAX_NAME);
            editorField.setEnableBackgroundDrawing(false);
            editorField.setTextColor(0xffffff);
            editorField.setText(editingCondition?waitValue(selectedWait()):row.data.instruction==TrainSchedule.Instruction.THROTTLE?Integer.toString(row.data.value):row.name);
            editorField.setFocused(true);
            buttonList.add(new CreateIconButton(5,left+222,top+127,0,1));
            buttonList.add(new CreateIconButton(6,left+9,top+127,1,0));
            if(editingCondition) {
                buttonList.add(new GuiButton(20,left+26,top+149,50,18,I18n.format("gui.cogworkbridge.schedule.and")));
                buttonList.add(new GuiButton(21,left+78,top+149,50,18,I18n.format("gui.cogworkbridge.schedule.or")));
                buttonList.add(new CreateIconButton(22,left+130,top+149,1,0));
                buttonList.add(new CreateIconButton(23,left+153,top+149,4,0));
                buttonList.add(new CreateIconButton(24,left+175,top+149,5,0));
                buttonList.add(new CreateIconButton(25,left+198,top+149,3,1));
                var w=selectedWait();
                if(w.type==TrainSchedule.Condition.ITEM_THRESHOLD||w.type==TrainSchedule.Condition.FLUID_THRESHOLD)
                    buttonList.add(new GuiButton(26,left+27,top+109,44,16,I18n.format("gui.cogworkbridge.schedule.operator."+w.operator.name().toLowerCase(Locale.ROOT))));
                if(w.type==TrainSchedule.Condition.TIME_OF_DAY||w.type==TrainSchedule.Condition.ITEM_THRESHOLD||w.type==TrainSchedule.Condition.PLAYER_COUNT||w.type==TrainSchedule.Condition.WIRELESS_REDSTONE) {
                    String unit=w.type==TrainSchedule.Condition.TIME_OF_DAY?I18n.format("gui.cogworkbridge.schedule.period."+w.rotation)
                            :w.type==TrainSchedule.Condition.PLAYER_COUNT?I18n.format("gui.cogworkbridge.schedule.players."+w.measure)
                            :w.type==TrainSchedule.Condition.WIRELESS_REDSTONE?I18n.format("gui.cogworkbridge.schedule.signal."+w.measure)
                            :I18n.format(w.measure==0?"gui.cogworkbridge.schedule.units":"gui.cogworkbridge.schedule.stacks");
                    buttonList.add(new GuiButton(27,left+142,top+109,82,16,unit));
                }
            }
        } else {
            CreateIconButton loop=new CreateIconButton(4,left+21,top+196,3,1);
            loop.setSelected(cyclic);buttonList.add(loop);
            CreateIconButton prev=new CreateIconButton(1,left+47,top+196,4,0);
            prev.enabled=page>0;buttonList.add(prev);
            CreateIconButton next=new CreateIconButton(2,left+67,top+196,5,0);
            next.enabled=(page+1)*PAGE_SIZE<rows.size();buttonList.add(next);
            CreateIconButton add=new CreateIconButton(3,left+90,top+196,0,0);
            add.enabled=rows.size()<TrainSchedule.MAX_ENTRIES;buttonList.add(add);
            buttonList.add(new CreateIconButton(0,left+214,top+196,0,1));
        }
        Keyboard.enableRepeatEvents(true);
    }

    private static String displayStation(String name) {
        return DEFAULT_STATION.equals(name)?I18n.format("gui.cogworkbridge.station.default_name"):name;
    }
    private static String storedStation(String name) {
        return I18n.format("gui.cogworkbridge.station.default_name").equals(name)?DEFAULT_STATION:name;
    }
    private void releaseButtons() {
        for(GuiButton button:buttonList)if(button instanceof CreateIconButton icon)icon.release();
    }
    private void refresh() { releaseButtons();initGui(); }
    private void finishEditing() {
        if(editing<0)return;
        storeField();
        editing=-1;refresh();
    }
    private void openEditor(int row,boolean condition) {
        editing=row;editingCondition=condition;groupIndex=conditionIndex=0;selectingFilter=-1;refresh();
    }
    @Override protected void actionPerformed(GuiButton button) {
        if(editing>=0&&editingCondition&&button.id>=20) {
            storeField();var row=rows.get(editing);var group=row.data.groups.get(groupIndex);var wait=selectedWait();
            if(button.id==20&&group.size()<TrainSchedule.MAX_CONDITIONS) {group.add(new TrainSchedule.Wait());conditionIndex=group.size()-1;}
            if(button.id==21&&row.data.groups.size()<TrainSchedule.MAX_GROUPS) {row.data.groups.add(new ArrayList<>(List.of(new TrainSchedule.Wait())));groupIndex=row.data.groups.size()-1;conditionIndex=0;}
            if(button.id==22) {
                group.remove(conditionIndex);if(group.isEmpty())row.data.groups.remove(groupIndex);
                if(row.data.groups.isEmpty()){editing=-1;refresh();return;}
            }
            if(button.id==23)conditionIndex=Math.floorMod(conditionIndex-1,Math.max(1,group.size()));
            if(button.id==24)conditionIndex=Math.floorMod(conditionIndex+1,Math.max(1,group.size()));
            if(button.id==25){groupIndex=(groupIndex+1)%row.data.groups.size();conditionIndex=0;}
            if(button.id==26)wait.operator=TrainSchedule.Operator.values()[(wait.operator.ordinal()+1)%3];
            if(button.id==27){if(wait.type==TrainSchedule.Condition.TIME_OF_DAY)wait.rotation=(wait.rotation+1)%10;else wait.measure=1-wait.measure;}
            refresh();return;
        }
        if(button.id==5){finishEditing();return;}
        if(button.id==6) {
            rows.remove(editing);
            if(rows.isEmpty())rows.add(new Row("","0",TrainSchedule.Condition.DELAY));
            editing=-1;page=Math.min(page,(rows.size()-1)/PAGE_SIZE);refresh();return;
        }
        if(button.id==0) {
            TrainSchedule result=new TrainSchedule();result.cyclic=cyclic;
            for(Row row:rows) {
                TrainSchedule.Entry entry=row.data.copy();entry.text=storedStation(row.name);
                if(entry.instruction!=TrainSchedule.Instruction.DESTINATION||!entry.text.isBlank())result.entries.add(entry);
            }
            ModNetwork.CHANNEL.sendToServer(new TrainSchedulePacket(slot,result));
            mc.displayGuiScreen(null);return;
        }
        if(button.id==1&&page>0)page--;
        else if(button.id==2&&(page+1)*PAGE_SIZE<rows.size())page++;
        else if(button.id==3&&rows.size()<TrainSchedule.MAX_ENTRIES) {
            rows.add(new Row("","0",TrainSchedule.Condition.DELAY));
            page=(rows.size()-1)/PAGE_SIZE;openEditor(rows.size()-1,false);return;
        } else if(button.id==4)cyclic=!cyclic;
        refresh();
    }
    private void cycleCondition(int delta) {
        TrainSchedule.Condition[] types=TrainSchedule.Condition.values();
        storeField();
        Row row=rows.get(editing);
        var wait=selectedWait();wait.type=types[Math.floorMod(wait.type.ordinal()+delta,types.length)];row.condition=row.data.first().type;refresh();
    }
    @Override protected void keyTyped(char c,int key)throws IOException {
        if(editing>=0) {
            if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER){finishEditing();return;}
            if(key==Keyboard.KEY_ESCAPE){finishEditing();return;}
            if(editorField!=null&&(!editingCondition||Character.isDigit(c)||c==':'||c==0
                    ||key==Keyboard.KEY_BACK||key==Keyboard.KEY_DELETE)&&editorField.textboxKeyTyped(c,key))return;
        }
        super.keyTyped(c,key);
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException {
        if(selectingFilter>=0) {
            if(x>=left+47&&x<left+209&&y>=top+56&&y<top+128) {
                int col=(x-left-47)/18,row=(y-top-56)/18,index=row==3?col:9+row*9+col;
                var sample=mc.player.inventory.getStackInSlot(index).copy();if(!sample.isEmpty())sample.setCount(1);
                if(selectingFilter==0)selectedWait().filter=sample;else selectedWait().second=sample;
            }
            selectingFilter=-1;return;
        }
        if(editing>=0) {
            if(editingCondition&&hasFilter(selectedWait())&&y>=top+128&&y<top+146&&x>=left+77&&x<left+(selectedWait().type==TrainSchedule.Condition.WIRELESS_REDSTONE?115:96)) {
                int filter=x<left+96?0:1;
                if(button==1){if(filter==0)selectedWait().filter=net.minecraft.item.ItemStack.EMPTY;else selectedWait().second=net.minecraft.item.ItemStack.EMPTY;}
                else selectingFilter=filter;return;
            }
            if(editorField!=null)editorField.mouseClicked(x,y,button);
            if(editingCondition&&x>=left+53&&x<left+198&&y>=top+64&&y<top+82) {
                cycleCondition(button==1?-1:1);return;
            }
            if(!editingCondition&&x>=left+53&&x<left+198&&y>=top+64&&y<top+82) {
                storeField();var data=rows.get(editing).data;
                data.instruction=TrainSchedule.Instruction.values()[Math.floorMod(data.instruction.ordinal()+(button==1?-1:1),TrainSchedule.Instruction.values().length)];refresh();return;
            }
        } else if(button==0) {
            for(int i=0;i<PAGE_SIZE&&page*PAGE_SIZE+i<rows.size();i++) {
                int cy=top+25+i*CARD_HEIGHT;
                if(x>=left+205&&x<left+220&&y>=cy+2&&y<cy+15) {
                    rows.remove(page*PAGE_SIZE+i);
                    if(rows.isEmpty())rows.add(new Row("","0",TrainSchedule.Condition.DELAY));
                    page=Math.min(page,(rows.size()-1)/PAGE_SIZE);refresh();return;
                }
                if(x>=left+50&&x<left+205&&y>=cy+2&&y<cy+21) {openEditor(page*PAGE_SIZE+i,false);return;}
                if(x>=left+50&&x<left+205&&y>=cy+23&&y<cy+45) {openEditor(page*PAGE_SIZE+i,true);return;}
            }
        }
        super.mouseClicked(x,y,button);
    }
    @Override public void handleMouseInput()throws IOException {
        super.handleMouseInput();
        if(selectingFilter>=0)return;
        int wheel=Mouse.getEventDWheel();
        if(editing>=0&&editingCondition&&wheel!=0)cycleCondition(wheel>0?-1:1);
        else if(editing<0&&wheel!=0) {
            int max=Math.max(0,(rows.size()-1)/PAGE_SIZE);
            int next=Math.max(0,Math.min(max,page+(wheel<0?1:-1)));
            if(next!=page){page=next;refresh();}
        }
    }
    @Override public void updateScreen(){if(editorField!=null)editorField.updateCursorCounter();}

    @Override public void drawScreen(int mouseX,int mouseY,float partial) {
        drawDefaultBackground();GlStateManager.color(1,1,1,1);
        mc.getTextureManager().bindTexture(SCHEDULE);
        Gui.drawModalRectWithCustomSizedTexture(left,top,0,0,256,226,256,256);
        String title=I18n.format("gui.cogworkbridge.schedule.title");
        fontRenderer.drawString(title,left+124-fontRenderer.getStringWidth(title)/2,top+4,0x505050);
        for(int i=0;i<PAGE_SIZE&&page*PAGE_SIZE+i<rows.size();i++)drawCard(rows.get(page*PAGE_SIZE+i),top+25+i*CARD_HEIGHT);
        fontRenderer.drawString((page+1)+"/"+Math.max(1,(rows.size()+PAGE_SIZE-1)/PAGE_SIZE),left+116,top+201,0x505050);
        if(editing>=0) {
            drawRect(0,0,width,height,0x99000000);
            GlStateManager.color(1,1,1,1);mc.getTextureManager().bindTexture(EDITOR);
            Gui.drawModalRectWithCustomSizedTexture(left-2,top+40,0,0,256,89,256,256);
            String label=I18n.format(editingCondition?"gui.cogworkbridge.schedule.condition_editor":"gui.cogworkbridge.schedule.destination");
            fontRenderer.drawString(label,left+124-fontRenderer.getStringWidth(label)/2,top+44,0x505050);
            String type=editingCondition?conditionName(selectedWait().type):I18n.format("gui.cogworkbridge.schedule.instruction."+rows.get(editing).data.instruction.name().toLowerCase(Locale.ROOT));
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(type,132),left+58,top+70,0xffffff);
            if(editingCondition) {
                fontRenderer.drawString("OR "+(groupIndex+1)+"/"+rows.get(editing).data.groups.size()+"  AND "+(conditionIndex+1)+"/"+rows.get(editing).data.groups.get(groupIndex).size(),left+38,top+177,0xffffff);
                for(int i=0;i<(hasFilter(selectedWait())?(selectedWait().type==TrainSchedule.Condition.WIRELESS_REDSTONE?2:1):0);i++) {drawRect(left+77+i*20,top+128,left+95+i*20,top+146,0xff777777);
                    var item=i==0?selectedWait().filter:selectedWait().second;net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();mc.getRenderItem().renderItemAndEffectIntoGUI(item,left+78+i*20,top+129);net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();}
            }
            if(editorField!=null)editorField.drawTextBox();
        }
        super.drawScreen(mouseX,mouseY,partial);
        if(selectingFilter>=0) {
            drawRect(0,0,width,height,0xcc000000);mc.getTextureManager().bindTexture(new ResourceLocation("create","textures/gui/player_inventory.png"));GlStateManager.color(1,1,1,1);
            drawModalRectWithCustomSizedTexture(left+39,top+38,0,0,176,100,256,256);
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            for(int row=0;row<4;row++)for(int col=0;col<9;col++)mc.getRenderItem().renderItemAndEffectIntoGUI(mc.player.inventory.getStackInSlot(row==3?col:9+row*9+col),left+48+col*18,top+57+row*18);
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        }
        if(editing<0) {
            for(GuiButton b:buttonList)if(b.isMouseOver()) {
                String key=b.id==4?(cyclic?"gui.cogworkbridge.schedule.cyclic":"gui.cogworkbridge.schedule.once")
                        :b.id==3?"gui.cogworkbridge.schedule.add":null;
                if(key!=null)drawHoveringText(I18n.format(key),mouseX,mouseY);
            }
        }
    }
    private void drawCard(Row row,int y) {
        int x=left+25;
        drawRect(x,y,x+195,y+47,0xffbababa);
        drawRect(x+1,y+1,x+194,y+46,0xff333333);
        drawRect(x+2,y+2,x+193,y+45,0xff777777);
        drawRect(x+2,y+2,x+193,y+22,0xffbababa);
        drawRect(x+8,y,x+11,y+47,0xffcfb978);
        mc.getTextureManager().bindTexture(SCHEDULE);GlStateManager.color(1,1,1,1);
        Gui.drawModalRectWithCustomSizedTexture(x+4,y+5,12,239,11,16,256,256);
        Gui.drawModalRectWithCustomSizedTexture(x+4,y+25,1,239,11,16,256,256);
        Gui.drawModalRectWithCustomSizedTexture(x+181,y+2,51,243,12,12,256,256);
        var instruction=row.data.instruction;
        String name=instruction==TrainSchedule.Instruction.DESTINATION?(row.name.isEmpty()?I18n.format("gui.cogworkbridge.schedule.destination"):row.name)
                :I18n.format("gui.cogworkbridge.schedule.instruction."+instruction.name().toLowerCase(Locale.ROOT))
                +(instruction==TrainSchedule.Instruction.THROTTLE?" "+row.data.value+"%":instruction==TrainSchedule.Instruction.RENAME?" "+row.name:"");
        fontRenderer.drawString(fontRenderer.trimStringToWidth(name,145),x+26,y+7,0x353535);
        String wait=instruction==TrainSchedule.Instruction.RENAME||instruction==TrainSchedule.Instruction.THROTTLE?""
                :row.data.groups.isEmpty()?I18n.format("gui.cogworkbridge.schedule.no_wait")
                :conditionName(row.condition)+(row.condition==TrainSchedule.Condition.DELAY?"  "+row.seconds+"s":"")
                +(row.data.groups.size()>1||row.data.groups.get(0).size()>1?"  +":"");
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(wait,145),x+26,y+29,0xffffff);
    }
    private static boolean hasFilter(TrainSchedule.Wait w) {return w.type==TrainSchedule.Condition.ITEM_THRESHOLD||w.type==TrainSchedule.Condition.FLUID_THRESHOLD||w.type==TrainSchedule.Condition.WIRELESS_REDSTONE;}
    private static String conditionName(TrainSchedule.Condition condition) {
        return I18n.format("gui.cogworkbridge.schedule.condition."+condition.name().toLowerCase(Locale.ROOT));
    }
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);releaseButtons();}
    @Override public boolean doesGuiPauseGame(){return false;}
}
