package com.simibubi.create.content.equipment.zapper;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.ScheduleWidget;
import com.simibubi.create.content.trains.schedule.ScheduleTextures;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
public class ZapperIconButton extends ScheduleWidget {
    protected AllIcons icon;protected Runnable callback;public boolean green;
    public ZapperIconButton(int x,int y,AllIcons icon){super(x,y,18,18);this.icon=icon;}
    public ZapperIconButton withCallback(Runnable action){callback=action;return this;}
    public void setToolTip(Component text){toolTip.clear();toolTip.add(text);setMessage(text);}
    @Override public void onClick(MouseButtonEvent event,boolean twice){if(callback!=null)callback.run();}
    @Override public boolean keyPressed(KeyEvent event){if(active&&visible&&event.isConfirmation()){if(callback!=null)callback.run();return true;}return false;}
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){(!active?ScheduleTextures.BUTTON_DISABLED:isHoveredOrFocused()?ScheduleTextures.BUTTON_HOVER:green?ScheduleTextures.BUTTON_GREEN:ScheduleTextures.BUTTON).render(gui,getX(),getY());com.simibubi.create.foundation.gui.IconRenderer.render(icon,gui,getX()+1,getY()+1);super.extractWidgetRenderState(gui,mouseX,mouseY,partial);}
}
