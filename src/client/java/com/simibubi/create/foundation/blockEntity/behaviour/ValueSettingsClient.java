package com.simibubi.create.foundation.blockEntity.behaviour;

import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

/** Source warmup, short-interact and board dispatch using the actual installed behaviour. */
public class ValueSettingsClient {
    public static final ValueSettingsClient INSTANCE=new ValueSettingsClient();
    private final Minecraft mc;
    private net.minecraft.client.multiplayer.ClientLevel interactionLevel;
    private BlockEntityBehaviour interactionOwner;
    private com.simibubi.create.foundation.blockEntity.SmartBlockEntity interactionBlockEntity;
    public int interactHeldTicks=-1;
    public BlockPos interactHeldPos;
    public BehaviourType<?> interactHeldBehaviour;
    public InteractionHand interactHeldHand;
    public Direction interactHeldFace;
    public List<MutableComponent> lastHoverTip;
    public int hoverTicks,hoverWarmup;
    public ValueSettingsClient(){mc=Minecraft.getInstance();}
    public boolean cancelIfWarmupAlreadyStarted(BlockPos pos){return interactHeldTicks!=-1&&pos.equals(interactHeldPos);}
    public void startInteractionWith(BlockPos pos,BehaviourType<?> type,InteractionHand hand,Direction side){
        var actual=mc.level==null?null:BlockEntityBehaviour.get(mc.level,pos,type);
        if(!(actual instanceof ValueSettingsBehaviour)){cancelInteraction();return;}
        interactionLevel=mc.level;interactionOwner=actual;interactionBlockEntity=actual.blockEntity;
        interactHeldTicks=0;interactHeldPos=pos.immutable();interactHeldBehaviour=type;interactHeldHand=hand;interactHeldFace=side;
    }
    public void cancelInteraction(){interactHeldTicks=-1;interactionLevel=null;interactionOwner=null;interactionBlockEntity=null;}
    private boolean currentInteraction(BlockEntityBehaviour actual){
        return actual!=null&&actual==interactionOwner&&actual.blockEntity==interactionBlockEntity
            &&mc.level==interactionLevel&&!actual.blockEntity.isRemoved()&&actual.getWorld()==mc.level
            &&com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(mc.level,interactHeldPos)==interactionBlockEntity
            &&BlockEntityBehaviour.get(mc.level,interactHeldPos,interactHeldBehaviour)==actual;
    }
    public void tick(){
        if(hoverWarmup>0)hoverWarmup--;if(hoverTicks>0)hoverTicks--;
        if(interactHeldTicks==-1)return;
        var player=mc.player;
        if(mc.level==null||mc.level!=interactionLevel||mc.gui.screen()!=null||!ValueSettingsInputHandler.canInteract(player)
                ||player.getMainHandItem().is(com.simibubi.create.AllBlocks.CLIPBOARD.asItem())
                ||!player.isWithinBlockInteractionRange(interactHeldPos,1)||!mc.level.mayInteract(player,interactHeldPos)
                ||!(mc.hitResult instanceof BlockHitResult hit)||!hit.getBlockPos().equals(interactHeldPos)||hit.getDirection()!=interactHeldFace){cancelInteraction();return;}
        var actual=BlockEntityBehaviour.get(mc.level,interactHeldPos,interactHeldBehaviour);
        if(!currentInteraction(actual)||!(actual instanceof ValueSettingsBehaviour settings)
                ||!settings.isActive()||!settings.mayInteract(player)||settings.bypassesInput(player.getMainHandItem())
                ||settings.onlyVisibleWithWrench()&&!player.getItemInHand(interactHeldHand).is(com.simibubi.create.AllItems.WRENCH)){cancelInteraction();return;}
        var slot=settings.getSlotPositioning();if(slot instanceof ValueBoxTransform.Sided sided)sided.fromSide(interactHeldFace);
        if(!slot.shouldRender(mc.level,interactHeldPos,actual.blockEntity.getBlockState())||!settings.testHit(hit.getLocation())
                ||!currentInteraction(actual)){cancelInteraction();return;}
        if(!mc.options.keyUse.isDown()){
            ClientPlayNetworking.send(new ValueSettingsPacket(interactHeldPos,0,0,interactHeldHand,hit,interactHeldFace,false,settings.netId()));
            settings.onShortInteract(player,interactHeldHand,interactHeldFace,hit);cancelInteraction();return;
        }
        if(interactHeldTicks++<5)return;
        if(actual.blockEntity instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor)
            mc.gui.setScreen(new com.simibubi.create.content.kinetics.motor.KineticSpeedScreen(interactHeldPos,motor.generatedSpeed.getValue()));
        else {
            var board=settings.createBoard(player,hit);
            if(!currentInteraction(actual)){cancelInteraction();return;}
            var initial=settings.getValueSettings();
            if(!currentInteraction(actual)){cancelInteraction();return;}
            mc.gui.setScreen(new ValueSettingsScreen(interactHeldPos,board,initial,settings::newSettingHovered,settings.netId(),actual));
        }
        cancelInteraction();
    }
    public void showHoverTip(List<MutableComponent> tip){
        if(mc.gui.screen()!=null)return;
        if(hoverWarmup<6){hoverWarmup+=2;return;}hoverWarmup++;
        hoverTicks=hoverTicks==0?11:Math.max(hoverTicks,6);lastHoverTip=tip;
    }
    public void render(GuiGraphicsExtractor gui,DeltaTracker delta){
        if(mc.level==null||mc.gui.screen()!=null||mc.gui.hud.isHidden()||!ValueSettingsInputHandler.canInteract(mc.player)||hoverTicks==0||lastHoverTip==null)return;
        int x=gui.guiWidth()/2,y=gui.guiHeight()-75-lastHoverTip.size()*12;
        float alpha=hoverTicks>5?(11-hoverTicks)/5f:Math.min(1,hoverTicks/5f);int a=(int)(alpha*255)<<24;
        for(int i=0;i<lastHoverTip.size();i++){var text=lastHoverTip.get(i);gui.text(mc.font,text,x-mc.font.width(text)/2,y,a|(i==0?0xFBDC7D:0xFFFFFF),false);y+=12;}
    }
    public static void register(){
        ValueBoxRenderHandler.register();
        ValueSettingsInputHandler.CLIENT_START=target->{
            if(INSTANCE.cancelIfWarmupAlreadyStarted(target.pos()))return;
            var mc=Minecraft.getInstance();if(mc.level==null)return;
            var owner=mc.level.getBlockEntity(target.pos());
            if(!(owner instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity smart))return;
            for(var behaviour:smart.getAllBehaviours())if(behaviour instanceof ValueSettingsBehaviour settings&&settings.netId()==target.behaviourIndex()){
                INSTANCE.startInteractionWith(target.pos(),behaviour.getType(),target.hand(),target.side());return;
            }
        };
        ClientTickEvents.END_CLIENT_TICK.register(client->INSTANCE.tick());
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,com.simibubi.create.Create.asResource("value_settings"),(gui,delta)->INSTANCE.render(gui,delta));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{INSTANCE.cancelInteraction();INSTANCE.lastHoverTip=null;INSTANCE.hoverTicks=INSTANCE.hoverWarmup=0;});
    }
}
