package com.simibubi.create.content.redstone.displayLink;

import com.simibubi.create.Create;
import com.simibubi.create.api.behaviour.display.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.Label;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Actual source/options/line editor using actual native widgets, authoritative snapshots and a real menu-bound packet. */
public final class DisplayLinkScreen extends AbstractContainerScreen<DisplayLinkMenu> {
    private final Map<Identifier,CompoundTag> drafts=new HashMap<>();
    private Identifier selected;
    private int line,seenRevision;
    private boolean sent;
    public DisplayLinkScreen(DisplayLinkMenu menu,Inventory inventory,Component title){super(menu,inventory,title,235,162);selected=Identifier.tryParse(menu.opening().config().getStringOr("Id",""));line=menu.opening().line();}
    public Identifier selectedSource(){return selected;}
    public CompoundTag draft(){return selected==null?new CompoundTag():drafts.computeIfAbsent(selected,id->id.toString().equals(menu.opening().config().getStringOr("Id",""))?menu.opening().config().copy():menu.opening().sources().stream().filter(entry->entry.id().equals(id)).findFirst().map(entry->entry.defaults().copy()).orElseGet(CompoundTag::new));}
    public int targetLine(){return line;}
    @Override protected void init(){
        super.init();var sources=menu.opening().sources();if(sources.stream().noneMatch(entry->entry.id().equals(selected)))selected=sources.isEmpty()?null:sources.getFirst().id();line=Math.clamp(line,0,Math.max(0,menu.opening().rows()-1));seenRevision=menu.revision();var entry=sources.stream().filter(source->source.id().equals(selected)).findFirst().orElse(null);
        if(entry!=null){var source=DisplaySource.get(entry.id());addRenderableWidget(Button.builder(source==null?Component.empty():source.getName(),button->cycleSource()).bounds(leftPos+61,topPos+26,135,18).build());
            var widgets=new DisplaySourceConfigurationWidgets.Context(menu.opening(),entry,draft(),font,leftPos+61,topPos+51,135,widget->addRenderableWidget(widget));
            if(!DisplaySourceConfigurationWidgets.create(source,widgets)){int row=0;for(var option:entry.options()){
            int selectedRow=option.widgetLine()<0?row:option.widgetLine();row=Math.max(row,selectedRow+1);
            int y=topPos+51+selectedRow*21;if(option.isNumber()){
                var label=new Label(leftPos+66+option.widgetOffset(),y+4,Component.empty()).withShadow().withSuffix(option.suffix());
                var input=new ScrollInput(leftPos+61+option.widgetOffset(),y,option.widgetWidth(),18);
                input.withRange(option.minimum(),option.maximum()).withShiftStep(option.shiftStep()).titled(Component.translatable(option.title()))
                        .calling(value->draft().putInt(option.key(),value)).writingTo(label).setState(option.number(draft()));
                addRenderableWidget(label);addRenderableWidget(input);
            }else if(option.isText()){var field=new EditBox(font,leftPos+61+option.widgetOffset(),y,option.widgetWidth(),18,Component.translatable(option.key().equals("Objective")?"create.display_source.scoreboard.objective":option.key().equals("Filter")?"create.schedule.instruction.filter_edit_box":"create.display_source.label"));field.setMaxLength(option.maxLength());field.setValue(draft().getStringOr(option.key(),""));field.setResponder(value->draft().putString(option.key(),value));addRenderableWidget(field);}
            else{int index=Math.clamp(draft().getIntOr(option.key(),0),0,option.choices().size()-1);addRenderableWidget(Button.builder(Component.translatable(option.choices().get(index)),button->{int next=(draft().getIntOr(option.key(),0)+1)%option.choices().size();draft().putInt(option.key(),next);button.setMessage(Component.translatable(option.choices().get(next)));}).bounds(leftPos+61+option.widgetOffset(),y,option.widgetWidth(),18).build());}
        }}}
        var target=DisplayTarget.get(menu.opening().target());if(target!=null&&menu.opening().rows()>0)addRenderableWidget(Button.builder(target.getLineOptionText(line),button->{line=(line+1)%menu.opening().rows();button.setMessage(target.getLineOptionText(line));}).bounds(leftPos+61,topPos+105,135,18).build());var confirm=addRenderableWidget(Button.builder(Component.literal("✓"),button->onClose()).bounds(leftPos+202,topPos+138,18,18).build());confirm.active=entry!=null&&target!=null&&menu.opening().rows()>0;
    }
    private void cycleSource(){var sources=menu.opening().sources();int index=0;for(int i=0;i<sources.size();i++)if(sources.get(i).id().equals(selected))index=i;selected=sources.get((index+1)%sources.size()).id();rebuildWidgets();}
    private void send(){if(sent||selected==null||minecraft.player==null||!menu.stillValid(minecraft.player)||menu.opening().rows()==0)return;sent=true;var data=draft().copy();data.putString("Id",selected.toString());ClientPlayNetworking.send(new DisplayLinkConfigurationPacket(menu.containerId,menu.opening().pos(),data,line));}
    @Override public void onClose(){send();super.onClose();}
    @Override public void removed(){send();super.removed();}
    @Override public boolean mouseScrolled(double x,double y,double scrollX,double scrollY){return getChildAt(x,y).filter(child->child.mouseScrolled(x,y,scrollX,scrollY)).isPresent()||super.mouseScrolled(x,y,scrollX,scrollY);}
    @Override protected void containerTick(){super.containerTick();for(var child:children())if(child instanceof ScrollInput input)input.tick();if(!menu.stillValid(minecraft.player)){onClose();return;}if(seenRevision!=menu.revision())rebuildWidgets();}
    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){super.extractBackground(gui,mouseX,mouseY,partial);gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/display_link.png"),leftPos,topPos,0,0,235,162,256,256);}
    @Override protected void extractLabels(GuiGraphicsExtractor gui,int mouseX,int mouseY){gui.text(font,title,(imageWidth-font.width(title))/2,4,0xFF592424,false);if(menu.opening().sources().isEmpty())gui.text(font,Component.translatable("create.display_link.no_source"),65,30,0xFFD3D3D3,false);if(menu.opening().target()==null||menu.opening().rows()==0)gui.text(font,Component.translatable("create.display_link.no_target"),65,109,0xFFD3D3D3,false);}
}
