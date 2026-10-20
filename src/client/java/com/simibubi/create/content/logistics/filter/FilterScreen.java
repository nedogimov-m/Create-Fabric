package com.simibubi.create.content.logistics.filter;

import com.simibubi.create.Create;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Source textures and native menu/input/GUI extraction; samples remain ghosts on the server. */
public final class FilterScreen extends AbstractContainerScreen<FilterMenu> {
    private final Button[] choices=new Button[4];
    public FilterScreen(FilterMenu menu,Inventory inventory,Component title){super(menu,inventory,title,230,211);}
    @Override protected void init(){
        super.init();String[] labels={"+","−","*","="},tips={"allow_list","deny_list","ignore_data","respect_data"};int[] positions={36,18,86,66};
        for(int i=0;i<4;i++){int option=i;choices[i]=addRenderableWidget(Button.builder(Component.literal(labels[i]),button->choose(option)).bounds(leftPos+positions[i],topPos+75,18,18).tooltip(Tooltip.create(Component.translatable("create.gui.filter."+tips[i]))).build());}
        addRenderableWidget(Button.builder(Component.literal("×"),button->choose(4)).bounds(leftPos+152,topPos+75,18,18).tooltip(Tooltip.create(Component.translatable("controls.reset"))).build());
        addRenderableWidget(Button.builder(Component.literal("✓"),button->onClose()).bounds(leftPos+181,topPos+75,18,18).tooltip(Tooltip.create(Component.translatable("gui.done"))).build());
    }
    private void choose(int option){minecraft.gameMode.handleInventoryButtonClick(menu.containerId,option);}
    public net.minecraft.world.phys.Vec2 slotPosition(int index){var slot=menu.getSlot(index);return new net.minecraft.world.phys.Vec2(leftPos+slot.x+8,topPos+slot.y+8);}
    @Override protected void containerTick(){super.containerTick();for(int i=0;i<4;i++)choices[i].active=(i/2==0?menu.blacklist():menu.respectData())!=(i%2==1);}
    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){
        super.extractBackground(gui,mouseX,mouseY,partial);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/filters.png"),leftPos,topPos,0,0,214,99,256,256);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/player_inventory.png"),leftPos+30,topPos+103,0,0,176,108,256,256);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor gui,int mouseX,int mouseY){gui.text(font,title,(214-font.width(title))/2,5,0xFF303030,false);gui.text(font,playerInventoryTitle,38,109,0xFF303030,false);}
}
