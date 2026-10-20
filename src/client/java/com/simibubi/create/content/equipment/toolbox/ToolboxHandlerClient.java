package com.simibubi.create.content.equipment.toolbox;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.item.ItemStack;

public final class ToolboxHandlerClient {
    public static final KeyMapping TOOLBELT=KeyMappingHelper.registerKeyMapping(new KeyMapping("create.keyinfo.toolbelt",InputConstants.KEY_LALT,KeyMapping.Category.INVENTORY));
    static int cooldown;
    private static net.minecraft.world.level.Level level;
    private static void open(Minecraft client){if(client.player==null||client.level==null||client.player.isSpectator()||client.gui.screen()!=null||cooldown>0)return;var boxes=new ArrayList<>(ToolboxHandler.getNearest(client.level,client.player,8));boxes.sort(Comparator.comparing(ToolboxBlockEntity::getUniqueId));var binding=ToolboxBindings.get(client.player,client.player.getInventory().getSelectedSlot());if(binding!=null){var actual=ToolboxHandler.resolve(client.player,binding);if(actual!=null&&ToolboxHandler.withinRange(client.player,actual)){var screen=new RadialToolboxMenu(boxes,RadialToolboxMenu.State.SELECT_ITEM_UNEQUIP,actual);screen.prevSlot(binding.compartment());client.gui.setScreen(screen);}else client.gui.setScreen(new RadialToolboxMenu(List.of(),RadialToolboxMenu.State.DETACH,null));return;}if(boxes.size()==1)client.gui.setScreen(new RadialToolboxMenu(boxes,RadialToolboxMenu.State.SELECT_ITEM,boxes.getFirst()));else if(!boxes.isEmpty())client.gui.setScreen(new RadialToolboxMenu(boxes,RadialToolboxMenu.State.SELECT_BOX,null));}
    public static boolean onPickItem(ItemStack sample){var client=Minecraft.getInstance();if(client.player==null||client.level==null||client.player.isCreative()||client.player.isSpectator()||sample==null||sample.isEmpty())return false;for(var box:ToolboxHandler.getNearest(client.level,client.player,8))for(int compartment=0;compartment<8;compartment++){ItemStack present=box.inventory.takeFromCompartment(1,compartment,true);if(present.isEmpty()||!ItemStack.isSameItemSameComponents(present,sample))continue;ClientPlayNetworking.send(new ToolboxEquipPacket(box.getBlockPos(),box.getUniqueId(),compartment,client.player.getInventory().getSelectedSlot()));return true;}return false;}
    private static void overlay(GuiGraphicsExtractor gui,DeltaTracker delta){var client=Minecraft.getInstance();if(client.player==null||client.gameMode==null||client.gui.hud.isHidden()||client.player.isSpectator())return;int x=gui.guiWidth()/2-90,y=gui.guiHeight()-23;for(int slot=0;slot<9;slot++){var binding=ToolboxBindings.get(client.player,slot);if(binding==null)continue;boolean selected=client.player.getInventory().getSelectedSlot()==slot;double range=ToolboxHandler.getMaxRange(client.player);boolean reachable=(binding.dimension().isEmpty()||binding.dimension().equals(client.level.dimension().identifier().toString()))&&ToolboxHandler.distance(client.player.position(),binding.pos())<range*range;var box=ToolboxHandler.resolve(client.player,binding);if(client.level.isLoaded(binding.pos())&&box==null)reachable=false;var texture=reachable?selected?ToolboxTextures.SELECTED_ON:ToolboxTextures.HOTBAR_ON:selected?ToolboxTextures.SELECTED_OFF:ToolboxTextures.HOTBAR_OFF;texture.render(gui,x+20*slot-(selected?1:0),y+(selected?1:0));}}
    public static void register(){ToolboxPortrait.register();MenuScreens.register(ToolboxMenu.TYPE,ToolboxScreen::new);BlockEntityRenderers.register(AllBlockEntityTypes.TOOLBOX,ToolboxRenderer::new);ClientPlayNetworking.registerGlobalReceiver(ToolboxNetworking.Range.TYPE,(packet,context)->ToolboxConfig.installClient(packet.value()));ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{ToolboxConfig.clearClient();cooldown=0;ToolboxHandler.onWorldUnload(level);level=null;});ClientTickEvents.END_CLIENT_TICK.register(client->{if(level!=client.level){ToolboxHandler.onWorldUnload(level);level=client.level;}if(cooldown>0&&!TOOLBELT.isDown())cooldown--;while(TOOLBELT.consumeClick())open(client);});HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR,Create.asResource("toolbox_links"),ToolboxHandlerClient::overlay);}
    private ToolboxHandlerClient(){}
}
