package com.simibubi.create.content.equipment.toolbox;

import com.simibubi.create.Create;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/** Exact original toolbox/toolbelt atlas coordinates, through native GUI pipelines. */
public enum ToolboxTextures {
    BOX("toolbox",0,0,188,171),PLAYER("player_inventory",0,0,176,108),SLOT("widgets",0,68,22,22),HIGHLIGHT("widgets",27,67,24,24),MAIN("widgets",0,97,24,24),EMPTY("widgets",27,98,22,22),INACTIVE("widgets",52,98,22,22),HOTBAR_OFF("widgets",0,130,20,24),HOTBAR_ON("widgets",20,130,20,24),SELECTED_OFF("widgets",0,155,22,22),SELECTED_ON("widgets",22,155,22,22);
    private final String path;private final int u,v,width,height;
    ToolboxTextures(String path,int u,int v,int width,int height){this.path=path;this.u=u;this.v=v;this.width=width;this.height=height;}
    public void render(GuiGraphicsExtractor gui,int x,int y){gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/"+path+".png"),x,y,u,v,width,height,256,256);}
    public static void icon(GuiGraphicsExtractor gui,int x,int y,int u,int v){gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/icons.png"),x,y,u*16,v*16,16,16,256,256);}
}
