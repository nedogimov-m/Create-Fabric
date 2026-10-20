package com.simibubi.create.content.redstone.link.controller;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class LinkedControllerClientHandler{
    public enum Mode{IDLE,ACTIVE,BIND}
    public static Mode MODE=Mode.IDLE;
    private static final Set<Integer> PRESSED=new LinkedHashSet<>();
    private static BlockPos lectern,selected;
    private static Object level;
    private static int cooldown;
    private static float equip,previousEquip;
    private static final float[] BUTTONS=new float[6],PREVIOUS_BUTTONS=new float[6];
    public static List<KeyMapping> controls(Minecraft client){return List.of(client.options.keyUp,client.options.keyDown,client.options.keyLeft,client.options.keyRight,client.options.keyJump,client.options.keyShift);}
    public static Set<Integer> currentlyPressed(){return Set.copyOf(PRESSED);}
    public static float button(int index,float partial){return PREVIOUS_BUTTONS[index]+(BUTTONS[index]-PREVIOUS_BUTTONS[index])*partial;}
    public static float equip(float partial){return previousEquip+(equip-previousEquip)*partial;}
    private static void send(Set<Integer> buttons,boolean pressed,BlockPos source){if(!buttons.isEmpty())ClientPlayNetworking.send(new LinkedControllerPackets.Input(List.copyOf(buttons),pressed,source));}
    public static void reset(boolean send){
        var client=Minecraft.getInstance();var oldLectern=lectern;
        if(send&&client.getConnection()!=null&&client.player!=null){send(PRESSED,false,oldLectern);if(oldLectern!=null)ClientPlayNetworking.send(new LinkedControllerPackets.Stop(oldLectern));}
        MODE=Mode.IDLE;PRESSED.clear();lectern=selected=null;cooldown=0;
        if(client.options!=null)for(var key:controls(client))key.setDown(client.isWindowActive()&&ControllerPhysicalInput.down(key));
        Arrays.fill(BUTTONS,0);Arrays.fill(PREVIOUS_BUTTONS,0);
    }
    private static void action(LinkedControllerItem.ClientAction action,BlockPos pos){
        if(MODE!=Mode.IDLE){reset(true);return;}
        MODE=action==LinkedControllerItem.ClientAction.BIND?Mode.BIND:Mode.ACTIVE;selected=pos;lectern=null;
        controls(Minecraft.getInstance()).forEach(key->key.setDown(false));
    }
    private static void lecternState(LecternControllerBlockEntity entity){
        var client=Minecraft.getInstance();if(client.player==null||entity.getLevel()!=client.level||!entity.isPublished())return;
        var pos=entity.getBlockPos();var user=entity.getUser();
        if(client.player.getUUID().equals(user)&&MODE==Mode.IDLE){MODE=Mode.ACTIVE;lectern=pos.immutable();controls(client).forEach(key->key.setDown(false));}
        else if(pos.equals(lectern)&&!client.player.getUUID().equals(user))reset(false);
    }
    private static void tick(Minecraft client){
        if(level!=client.level){reset(false);ControllerPhysicalInput.clear();level=client.level;equip=previousEquip=0;}
        if(client.player==null||client.level==null)return;
        if(!client.isPaused()){
            previousEquip=equip;equip+=((MODE!=Mode.IDLE?1:0)-equip)*.2f;
            for(int i=0;i<6;i++){PREVIOUS_BUTTONS[i]=BUTTONS[i];BUTTONS[i]+=((PRESSED.contains(i)?1:0)-BUTTONS[i])*.4f;}
        }
        if(MODE==Mode.IDLE)return;
        if(!client.isWindowActive()||client.player.isSpectator()||client.gui.screen()!=null||InputConstants.isKeyDown(InputConstants.KEY_ESCAPE)){
            reset(true);if(!client.isWindowActive())ControllerPhysicalInput.clear();return;
        }
        if(lectern==null&&LinkedControllerItem.heldController(client.player).isEmpty()){reset(true);return;}
        if(lectern!=null&&(!(LoadedLevelAccess.blockEntity(client.level,lectern)instanceof LecternControllerBlockEntity entity)||!entity.isUsedBy(client.player))){reset(true);return;}
        var down=new LinkedHashSet<Integer>();var keys=controls(client);for(int i=0;i<6;i++)if(ControllerPhysicalInput.down(keys.get(i)))down.add(i);
        var released=new LinkedHashSet<>(PRESSED);released.removeAll(down);var fresh=new LinkedHashSet<>(down);fresh.removeAll(PRESSED);
        if(MODE==Mode.BIND){
            if(selected==null||!LoadedLevelAccess.isLoaded(client.level,selected)){reset(true);return;}
            if(!fresh.isEmpty()){
                int button=fresh.iterator().next();ClientPlayNetworking.send(new LinkedControllerPackets.Bind(button,selected));
                client.player.sendOverlayMessage(Component.translatable("create.linked_controller.key_bound",keys.get(button).getTranslatedKeyMessage().getString()));
                reset(false);keys.forEach(key->key.setDown(false));return;
            }
        }else{
            send(released,false,lectern);if(!released.isEmpty())client.player.playSound(com.simibubi.create.AllSoundEvents.CONTROLLER_CLICK,1,.5f);
            if(!fresh.isEmpty()){send(fresh,true,lectern);client.player.playSound(com.simibubi.create.AllSoundEvents.CONTROLLER_CLICK,1,.75f);cooldown=5;}
            else if(cooldown>0)cooldown--;
            if(cooldown==0&&!down.isEmpty()){send(down,true,lectern);cooldown=5;}
        }
        PRESSED.clear();PRESSED.addAll(down);keys.forEach(key->key.setDown(false));
    }
    public static void register(){
        LinkedControllerItem.CLIENT_ACTION=LinkedControllerClientHandler::action;
        LecternControllerBlockEntity.CLIENT_SESSION=LinkedControllerClientHandler::lecternState;
        ClientTickEvents.END_CLIENT_TICK.register(LinkedControllerClientHandler::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("controller_bind"),(gui,delta)->{
            var client=Minecraft.getInstance();if(MODE!=Mode.BIND||client.gui.hud.isHidden())return;
            var title=Component.translatable("create.linked_controller.bind_mode");int y=gui.guiHeight()-64;gui.text(client.font,title,(gui.guiWidth()-client.font.width(title))/2,y,0xFFFFB040,false);
            var labels=controls(client).stream().map(key->key.getTranslatedKeyMessage().getString()).toArray();
            var hint=Component.translatable("create.linked_controller.press_keybind",labels);gui.text(client.font,hint,(gui.guiWidth()-client.font.width(hint))/2,y+12,0xFFE0E0E0,false);
        });
        LinkedControllerItemRenderer.register();
    }
    private LinkedControllerClientHandler(){}
}
