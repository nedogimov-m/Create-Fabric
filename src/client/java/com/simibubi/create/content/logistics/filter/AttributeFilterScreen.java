package com.simibubi.create.content.logistics.filter;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Native editor backed by the server's attribute discovery and recipe state. */
public final class AttributeFilterScreen extends AbstractContainerScreen<AttributeFilterMenu> {
    private int index;
    private int referenceRevision=-1;
    private Button selector,add,invert;
    private final Button[] modes=new Button[3];
    public AttributeFilterScreen(AttributeFilterMenu menu,Inventory inventory,Component title){super(menu,inventory,title,256,197);}
    public static void register(){
        net.minecraft.client.gui.screens.MenuScreens.register(AttributeFilterMenu.TYPE,AttributeFilterScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(AttributeFilterNetworking.Sync.TYPE,(packet,context)->{var player=context.client().player;if(player!=null&&player.containerMenu instanceof AttributeFilterMenu menu&&menu.containerId==packet.menu())menu.receive(packet);});
    }
    @Override protected void init(){
        super.init();selector=addRenderableWidget(Button.builder(Component.empty(),button->cycle(1)).bounds(leftPos+39,topPos+26,137,18).build());
        add=button("+",182,26,()->append(false),"add_attribute");invert=button("−",200,26,()->append(true),"add_inverted_attribute");
        String[] names={"OR","AND","NOT"},tips={"allow_list_disjunctive","allow_list_conjunctive","deny_list"};for(int i=0;i<3;i++){int choice=i;modes[i]=button(names[i],38+i*27,61,()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,choice),tips[i]);modes[i].setWidth(26);}
        button("×",179,61,()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,3),null);button("✓",208,61,this::onClose,null);
        update();
    }
    private Button button(String text,int x,int y,Runnable action,String tooltip){var builder=Button.builder(Component.literal(text),button->action.run()).bounds(leftPos+x,topPos+y,18,18);if(tooltip!=null)builder.tooltip(Tooltip.create(Component.translatable("create.gui.attribute_filter."+tooltip)));return addRenderableWidget(builder.build());}
    private void cycle(int direction){if(!menu.available().isEmpty())index=Math.floorMod(index+direction,menu.available().size());update();}
    private void append(boolean inverted){if(index>=0&&index<menu.available().size())ClientPlayNetworking.send(new AttributeFilterNetworking.Add(menu.containerId,index,inverted,menu.revision()));}
    private void update(){
        if(referenceRevision!=menu.revision()){referenceRevision=menu.revision();index=0;}
        var options=menu.available();index=options.isEmpty()?0:Math.clamp(index,0,options.size()-1);selector.active=!options.isEmpty();var text=options.isEmpty()?Component.translatable("create.gui.attribute_filter.add_reference_item"):options.get(index).format(false);selector.setMessage(Component.literal(font.plainSubstrByWidth(text.getString(),127)));selector.setTooltip(Tooltip.create(text));
        boolean duplicate=!options.isEmpty()&&menu.selected().stream().anyMatch(entry->ItemAttribute.saveStatic(entry.attribute(),minecraft.player.registryAccess()).equals(ItemAttribute.saveStatic(options.get(index),minecraft.player.registryAccess())));add.active=invert.active=!options.isEmpty()&&!duplicate;for(int i=0;i<3;i++)modes[i].active=menu.mode().ordinal()!=i;
    }
    @Override protected void containerTick(){super.containerTick();update();}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(isHovering(39,26,137,18,x,y)&&vertical!=0){cycle(vertical>0?-1:1);return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){super.extractBackground(gui,mouseX,mouseY,partial);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/filters.png"),leftPos,topPos,0,99,241,85,256,256);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/player_inventory.png"),leftPos+43,topPos+89,0,0,176,108,256,256);}
    @Override protected void extractLabels(GuiGraphicsExtractor gui,int mouseX,int mouseY){gui.text(font,title,(241-font.width(title))/2,5,0xFF303030,false);gui.text(font,playerInventoryTitle,51,95,0xFF303030,false);}
    @Override protected void extractSlot(GuiGraphicsExtractor gui,net.minecraft.world.inventory.Slot slot,int mouseX,int mouseY){super.extractSlot(gui,slot,mouseX,mouseY);if(slot.index==37)gui.itemDecorations(font,slot.getItem(),slot.x,slot.y,Integer.toString(menu.selected().size()));}
    @Override protected void extractTooltip(GuiGraphicsExtractor gui,int mouseX,int mouseY){
        if(hoveredSlot!=null&&hoveredSlot.index==37){var lines=new ArrayList<Component>();lines.add(Component.translatable("create.gui.attribute_filter."+(menu.selected().isEmpty()?"no_selected_attributes":"selected_attributes")));menu.selected().forEach(entry->lines.add(Component.literal("- ").append(entry.attribute().format(entry.inverted()))));gui.setComponentTooltipForNextFrame(font,lines,mouseX,mouseY);return;}super.extractTooltip(gui,mouseX,mouseY);
    }
    public net.minecraft.world.phys.Vec2 slotPosition(int slot){var entry=menu.getSlot(slot);return new net.minecraft.world.phys.Vec2(leftPos+entry.x+8,topPos+entry.y+8);}
    public void selectAttribute(int selected){referenceRevision=menu.revision();index=selected;update();}
    public int selectedIndex(){return index;}
}
