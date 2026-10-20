package com.simibubi.create.content.schematics.client;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.content.schematics.client.tools.ToolType;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Original horizontal passive tool overlay, source focus animation and four-line descriptions. */
public class ToolSelectionScreen extends Screen {
    public final String scrollToCycle=Component.translatable("create.gui.toolmenu.cycle").getString();
    public final String holdToFocus="gui.toolmenu.focusKey";
    protected List<ToolType> tools;
    protected Consumer<ToolType> callback;
    public boolean focused;
    private float offset,previous;
    protected int selection;
    protected int w,h;
    public ToolSelectionScreen(List<ToolType> tools,Consumer<ToolType> callback){super(Component.translatable("create.keyinfo.toolmenu"));this.tools=List.copyOf(tools);this.callback=callback;if(tools.isEmpty())throw new IllegalArgumentException("Actual tool overlay requires a source tool");w=Math.max(tools.size()*50+30,220);h=30;callback.accept(tools.get(selection));}
    public void setSelectedElement(ToolType tool){int index=tools.indexOf(tool);if(index>=0)selection=index;}
    public ToolType selected(){return tools.get(selection);}
    public void cycle(int direction){selection=Math.floorMod(selection+(direction<0?1:-1),tools.size());}
    public void update(){previous=offset;offset=focused?offset+(10-offset)*.1f:offset*.9f;}
    @Override public void onClose(){callback.accept(selected());}
    public void renderPassive(GuiGraphicsExtractor gui,float partial){
        int width=w,x=(gui.guiWidth()-width)/2+15,y=gui.guiHeight()-h-75-(int)(previous+(offset-previous)*partial);
        AllGuiTextures.HUD_BACKGROUND.stretched(gui,x-15,y,width,30,focused?0xDFFFFFFF:0x80FFFFFF);
        float tooltip=(previous+(offset-previous)*partial)/10;
        if(tooltip>.25f){int alpha=(int)(tooltip*255)<<24;AllGuiTextures.HUD_BACKGROUND.stretched(gui,x-15,y+33,width,52,alpha|0xB3B3CC);var lines=selected().getDescription();for(int i=0;i<lines.size();i++)gui.text(font,lines.get(i),x-10,y+38+(i==0?0:12+(i-1)*10),alpha|(i==0?0xEEEEEE:i==3?0xCCCCDD:0xCCDDFF),false);}
        if(tools.size()>1)gui.centeredText(font,Component.translatable(focused?"create.gui.toolmenu.cycle":"create.gui.toolmenu.focusKey",SchematicsClient.TOOL_MENU.getTranslatedKeyMessage()),gui.guiWidth()/2,y-10,0xFFCCDDFF);else x+=65;
        for(int i=0;i<tools.size();i++){var tool=tools.get(i);boolean chosen=i==selection;int at=y+(chosen?-10:0);int alpha=chosen||focused?0xFF000000:0x33000000;com.simibubi.create.foundation.gui.IconRenderer.render(tool.getIcon(),gui,x+i*50+16,at+12,alpha);com.simibubi.create.foundation.gui.IconRenderer.render(tool.getIcon(),gui,x+i*50+16,at+11,alpha|0xFFFFFF);if(chosen)gui.centeredText(font,tool.getDisplayName(),x+i*50+24,at+28,0xFFCCDDFF);}
    }
    @Override public boolean isPauseScreen(){return false;}
}
