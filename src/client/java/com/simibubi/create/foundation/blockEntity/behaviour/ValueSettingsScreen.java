package com.simibubi.create.foundation.blockEntity.behaviour;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.OptionalInt;
import java.util.function.Consumer;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.gui.widget.ScheduleEditBox;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;

/** Real source board data, including option icons, rows, formatter and full-range exact values. */
public class ValueSettingsScreen extends Screen {
    protected final BlockPos pos;
    protected final ValueSettingsBoard board;
    private final Consumer<ValueSettings> hovered;
    private final int behaviourIndex;
    private final Direction side;
    private final BlockEntityBehaviour sourceBehaviour;
    private int row,column,left,top,panelWidth,barLeft,barWidth,panelHeight;
    private boolean manual,syncing;
    private ScheduleEditBox exact;
    private Button confirm;
    public ValueSettingsScreen(BlockPos pos,ValueSettingsBoard board,ValueSettings initial,Consumer<ValueSettings> hovered,int behaviourIndex){
        this(pos,board,initial,hovered,behaviourIndex,findSource(pos,behaviourIndex));
    }
    public ValueSettingsScreen(BlockPos pos,ValueSettingsBoard board,ValueSettings initial,Consumer<ValueSettings> hovered,int behaviourIndex,BlockEntityBehaviour sourceBehaviour){
        super(board.title());this.pos=pos.immutable();this.board=board;this.hovered=hovered;this.behaviourIndex=behaviourIndex;this.sourceBehaviour=sourceBehaviour;
        if(board.rows().isEmpty()||board.maxValue()<0||board.milestoneInterval()<1)throw new IllegalArgumentException("Invalid source value board");
        row=Math.clamp(initial.row(),0,board.rows().size()-1);column=Math.clamp(initial.value(),0,board.maxValue());
        var client=net.minecraft.client.Minecraft.getInstance();
        side=client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos)?hit.getDirection():ValueSettingsClient.INSTANCE.interactHeldFace==null?Direction.UP:ValueSettingsClient.INSTANCE.interactHeldFace;
    }
    private static BlockEntityBehaviour findSource(BlockPos pos,int id){
        var level=net.minecraft.client.Minecraft.getInstance().level;
        if(level!=null&&level.getBlockEntity(pos) instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity smart)
            for(var behaviour:smart.getAllBehaviours())if(behaviour instanceof ValueSettingsBehaviour settings&&settings.netId()==id)return behaviour;
        return null;
    }
    private boolean current(){
        return minecraft.player!=null&&minecraft.level!=null&&sourceBehaviour!=null&&!sourceBehaviour.blockEntity.isRemoved()
            &&minecraft.level.getBlockEntity(pos)==sourceBehaviour.blockEntity
            &&sourceBehaviour.blockEntity.getBehaviour(sourceBehaviour.getType())==sourceBehaviour;
    }
    public BlockPos position(){return pos;}
    @Override protected void init(){
        panelWidth=Math.max(120,Math.min(320,width-24));panelHeight=108+board.rows().size()*16;
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;barLeft=left+Math.max(28,board.rows().stream().mapToInt(font::width).max().orElse(0)+16);barWidth=Math.max(16,left+panelWidth-12-barLeft);
        int y=top+50+board.rows().size()*16;
        exact=new ScheduleEditBox(font,left+10,y,panelWidth-70,20,board.title());exact.setMaxLength(10);
        exact.setFilter(value->value.length()<=10&&value.chars().allMatch(c->c>='0'&&c<='9'));
        exact.setValue(Integer.toString(column));exact.setResponder(value->{if(!syncing){manual=true;var parsed=parse(value);if(parsed.isPresent()){column=parsed.getAsInt();hover();}}validity();});
        addRenderableWidget(exact);
        confirm=addRenderableWidget(Button.builder(Component.translatable("gui.done"),button->confirm()).bounds(left+panelWidth-54,y,44,20).build());validity();
    }
    @Override protected void setInitialFocus(){}
    private OptionalInt parse(String text){if(text.isEmpty()||text.length()>10)return OptionalInt.empty();for(int i=0;i<text.length();i++)if(text.charAt(i)<'0'||text.charAt(i)>'9')return OptionalInt.empty();long value=Long.parseLong(text);return value<=board.maxValue()?OptionalInt.of((int)value):OptionalInt.empty();}
    private void validity(){if(exact==null||confirm==null)return;confirm.active=parse(exact.getValue()).isPresent();exact.setTextColor(confirm.active?0xFFE0E0E0:0xFFFF5555);}
    private void sync(){syncing=true;try{exact.setValue(Integer.toString(column));}finally{syncing=false;}validity();hover();}
    private void hover(){if(current())hovered.accept(new ValueSettings(row,column));}
    public Vec2 getCoordinateOfValue(int row,int column){double fraction=board.maxValue()==0?0:Math.clamp((long)column,0L,(long)board.maxValue())/(double)board.maxValue();return new Vec2((float)(barLeft+fraction*barWidth),top+29+row*16);}
    private ValueSettings sample(double x,double y,boolean milestones){
        int sampledRow=Math.clamp((int)Math.round((y-top-29)/16),0,board.rows().size()-1);
        double fraction=Math.clamp((x-barLeft)/barWidth,0,1);long value=Math.round(fraction*board.maxValue());
        if(milestones&&value!=board.maxValue())value=Math.round(value/(double)board.milestoneInterval())*(long)board.milestoneInterval();
        return new ValueSettings(sampledRow,(int)Math.clamp(value,0L,(long)board.maxValue()));
    }
    public ValueSettings getClosestCoordinate(int mouseX,int mouseY){return sample(mouseX,mouseY,minecraft.hasShiftDown());}
    public ValueSettings closestValue(double x,double y,boolean milestones){
        var sampled=sample(x,y,milestones);row=sampled.row();column=sampled.value();sync();return sampled;
    }
    private boolean boardHit(double x,double y){return x>=left+8&&x<=left+panelWidth-8&&y>=top+23&&y<top+23+board.rows().size()*16;}
    @Override public void tick(){if(!current()){onClose();return;}validity();}
    @Override public void mouseMoved(double x,double y){if(!manual&&boardHit(x,y))closestValue(x,y,minecraft.hasShiftDown());super.mouseMoved(x,y);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){if(event.button()==InputConstants.MOUSE_BUTTON_LEFT&&boardHit(event.x(),event.y())){manual=true;setFocused(null);closestValue(event.x(),event.y(),minecraft.hasShiftDown());return true;}return super.mouseClicked(event,twice);}
    @Override public boolean mouseReleased(MouseButtonEvent event){if(!manual&&minecraft.options.keyUse.matchesMouse(event)){confirm();return true;}return super.mouseReleased(event);}
    @Override public boolean keyReleased(KeyEvent event){if(!manual&&minecraft.options.keyUse.matches(event)){confirm();return true;}return super.keyReleased(event);}
    @Override public boolean keyPressed(KeyEvent event){
        if(event.isEscape())return super.keyPressed(event);if(event.isConfirmation()){confirm();return true;}
        if(event.isUp()||event.isDown()){row=Math.clamp(row+(event.isUp()?-1:1),0,board.rows().size()-1);hover();return true;}
        if(!exact.isFocused()&&(event.isLeft()||event.isRight())){step(event.isRight()?1:-1);return true;}return super.keyPressed(event);
    }
    private void step(int direction){column=(int)Math.clamp((long)column+(long)direction*(minecraft.hasShiftDown()?board.milestoneInterval():1),0L,(long)board.maxValue());sync();}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(vertical==0)return super.mouseScrolled(x,y,horizontal,vertical);step(vertical>0?1:-1);return true;}
    public void saveAndClose(double x,double y){confirm();}
    private void confirm(){if(!current()){onClose();return;}var value=parse(exact.getValue());if(value.isEmpty()){manual=true;setFocused(exact);return;}ClientPlayNetworking.send(new ValueSettingsPacket(pos,row,value.getAsInt(),null,null,side,com.simibubi.create.AllKeys.ctrlDown(),behaviourIndex));onClose();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){
        gui.fillGradient(0,0,width,height,0x50101010,0x50101010);gui.fill(left-2,top-2,left+panelWidth+2,top+panelHeight+2,0xFF442000);gui.fill(left,top,left+panelWidth,top+panelHeight,0xFFE0C39B);
        gui.centeredText(font,title,left+panelWidth/2,top+7,0xFF442000);
        for(int r=0;r<board.rows().size();r++){int y=top+24+r*16;gui.fill(left+8,y,left+panelWidth-8,y+12,r==row?0xFFFBDC7D:0xFFBA986D);gui.text(font,board.rows().get(r),left+12,y+2,0xFF442000,false);for(int tick=0;tick<=8;tick++){int x=barLeft+(int)((long)barWidth*tick/8);gui.fill(x,y+3,x+1,y+9,0xFF795330);}}
        int cursor=(int)getCoordinateOfValue(row,column).x;gui.fill(cursor-1,top+23+row*16,cursor+2,top+37+row*16,0xFF442000);
        var setting=new ValueSettings(row,column);var formatted=board.formatter().format(setting);
        int y=top+27+board.rows().size()*16;
        if(board.formatter() instanceof ValueSettingsFormatter.ScrollOptionSettingsFormatter options){com.simibubi.create.foundation.gui.IconRenderer.render(options.getIcon(setting),gui,left+8,y);gui.text(font,formatted,left+28,y+4,0xFF442000,false);}
        else gui.centeredText(font,formatted,left+panelWidth/2,y,0xFF442000);
        super.extractRenderState(gui,mouseX,mouseY,partial);
    }
}
