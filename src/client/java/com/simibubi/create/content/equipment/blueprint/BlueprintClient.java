package com.simibubi.create.content.equipment.blueprint;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.filter.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.EntityHitResult;

/** Source blueprint HUD with authoritative native server recipes and stale-identity rejection. */
public final class BlueprintClient {
 private static BlueprintMenu.Opening target;private static BlueprintPackets.Preview preview;private static boolean batch;private static int age,revision;private static final Map<ItemStack,List<ItemStack>> FILTERS=new IdentityHashMap<>();
 public static void register(){
  MenuScreens.register(BlueprintMenu.TYPE,BlueprintScreen::new);
  ClientPlayNetworking.registerGlobalReceiver(BlueprintPackets.Preview.TYPE,(packet,context)->{var client=context.client();if(target==null||!target.equals(packet.target())||packet.revision()!=revision||packet.batch()!=batch||client.level==null)return;var entity=client.level.getEntity(target.entity());if(!(entity instanceof BlueprintEntity blueprint)||!blueprint.getUUID().equals(target.uuid())||!blueprint.isAlive())return;preview=packet;FILTERS.clear();});
  ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());ClientTickEvents.END_CLIENT_TICK.register(BlueprintClient::tick);
  HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR,Create.asResource("crafting_blueprint"),BlueprintClient::extract);
 }
 private static void clear(){target=null;preview=null;FILTERS.clear();age=0;}
 private static void tick(Minecraft client){
  if(client.player==null||client.level==null||client.player.isSpectator()||client.gui.screen()!=null||!(client.hitResult instanceof EntityHitResult hit)||!(hit.getEntity() instanceof BlueprintEntity blueprint)||!blueprint.canPlayerUse(client.player)){clear();return;}
  var section=blueprint.getSectionAt(hit.getLocation().subtract(blueprint.position()));var next=new BlueprintMenu.Opening(blueprint.getId(),blueprint.getUUID(),section.index);boolean shift=client.player.isShiftKeyDown();
  if(!next.equals(target)||shift!=batch){target=next;batch=shift;preview=null;age=10;FILTERS.clear();}
  if(++age>=10){age=0;if(revision==Integer.MAX_VALUE)revision=0;ClientPlayNetworking.send(new BlueprintPackets.Query(target,++revision,batch));}
 }
 private static void extract(GuiGraphicsExtractor gui,DeltaTracker delta){
  var client=Minecraft.getInstance();var frame=preview;if(target==null||frame==null||frame.empty()||client.player==null||client.level==null||client.gui.hud.isHidden()||client.gui.screen()!=null)return;
  int count=frame.available().size()+frame.missing().size(),width=21*count+51,x=(gui.guiWidth()-width)/2,y=gui.guiHeight()-100;
  for(var stack:frame.available()){slot(gui,x,y,true,false);item(gui,client,stack,x,y,null);x+=21;}
  for(var stack:frame.missing()){slot(gui,x,y,false,false);item(gui,client,stack,x,y,"§6"+stack.getCount());x+=21;}
  x+=5;gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/widgets.png"),x,y+4,24,51,20,12,256,256);x+=25;
  slot(gui,x,y,frame.craftable(),frame.craftable());item(gui,client,frame.result().isEmpty()?new ItemStack(Items.BARRIER):frame.result(),x,y,null);
 }
 private static void slot(GuiGraphicsExtractor gui,int x,int y,boolean active,boolean output){gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/widgets.png"),output?x-1:x,output?y-1:y,output?27:0,output?67:active?46:68,output?24:22,output?24:22,256,256);}
 private static void item(GuiGraphicsExtractor gui,Minecraft client,ItemStack sample,int x,int y,String count){
  var display=sample;if(sample.getItem() instanceof ListFilterItem||sample.getItem() instanceof AttributeFilterItem){var choices=FILTERS.computeIfAbsent(sample,stack->{if(stack.getItem() instanceof ListFilterItem)return ListFilterItem.getFilterItems(stack).stream().filter(item->!item.isEmpty()).toList();var filter=FilterItemStack.of(stack);return BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(item->!item.isEmpty()&&filter.test(client.level,item)).toList();});if(!choices.isEmpty())display=choices.get((client.gui.hud.getGuiTicks()/10)%choices.size());}
  gui.item(display,x+3,y+3);gui.itemDecorations(client.font,display,x+3,y+3,count);
 }
 public static void assignCompleteRecipe(BlueprintMenu menu,net.minecraft.resources.Identifier recipe){ClientPlayNetworking.send(new BlueprintPackets.Assign(menu.containerId,menu.opening(),recipe));}
 private BlueprintClient(){}
}
