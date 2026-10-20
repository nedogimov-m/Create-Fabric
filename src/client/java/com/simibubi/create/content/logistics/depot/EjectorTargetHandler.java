package com.simibubi.create.content.logistics.depot;

import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Source Shift targeting and ballistic preview using immutable native gizmo extraction/submission. */
public final class EjectorTargetHandler {
    private static BlockPos selection;
    private static ItemStack currentItem;
    private static Level selectedLevel;
    private static long lastHovered=-1;
    private static final Map<LevelRenderState,Frame> FRAMES=new WeakHashMap<>();
    private static Frame latest;
    public record Frame(DrawableGizmoPrimitives primitives,BlockPos selection,BlockPos candidate,int arcSegments,boolean valid){}
    public static BlockPos selection(){return selection;}
    public static Frame latest(){return latest;}
    private static boolean holding(Minecraft client){return client.player!=null&&client.player.getMainHandItem().getItem() instanceof EjectorItem;}
    private static void begin(Minecraft client){
        var stack=client.player.getMainHandItem();
        if(selectedLevel!=client.level||currentItem!=null&&!ItemStack.isSameItemSameComponents(currentItem,stack)){selection=null;lastHovered=-1;}
        selectedLevel=client.level;currentItem=stack.copy();
    }
    public static boolean select(BlockPos pos){
        var client=Minecraft.getInstance();if(!holding(client)||client.player.isSpectator()||!client.player.isShiftKeyDown())return false;
        begin(client);selection=pos.immutable();lastHovered=-1;
        client.player.sendOverlayMessage(Component.translatable("create.weighted_ejector.target_set").withColor(0xFFAA00));return true;
    }
    public static boolean deselect(BlockPos pos){
        var client=Minecraft.getInstance();if(!holding(client)||!client.player.isShiftKeyDown()||!pos.equals(selection))return false;
        selection=null;lastHovered=-1;return true;
    }
    public static Direction validDirection(BlockPos pos,BlockPos target,int max){
        if(target==null||target.getX()==pos.getX()&&target.getZ()==pos.getZ())return null;
        int x=target.getX()-pos.getX(),z=target.getZ()-pos.getZ();
        if(Math.abs((long)x)>max||Math.abs((long)z)>max)return null;
        if(x==0)return Direction.get(z<0?Direction.AxisDirection.NEGATIVE:Direction.AxisDirection.POSITIVE,Direction.Axis.Z);
        if(z==0)return Direction.get(x<0?Direction.AxisDirection.NEGATIVE:Direction.AxisDirection.POSITIVE,Direction.Axis.X);
        return null;
    }
    public static void flushSettings(BlockPos pos,long nonce){
        var client=Minecraft.getInstance();if(client.player==null||client.level==null)return;
        Direction facing=validDirection(pos,selection,EjectorConfig.get(client.level).maxDistance());
        if(facing==null){client.player.sendOverlayMessage(Component.translatable("create.weighted_ejector."+(selection==null?"no_target":"target_not_valid")));}
        else{
            int horizontal=Math.abs(pos.getX()-selection.getX()+pos.getZ()-selection.getZ()),vertical=selection.getY()-pos.getY();
            ClientPlayNetworking.send(new EjectorPlacementPacket(horizontal,vertical,pos,facing,nonce));
            client.player.sendOverlayMessage(Component.translatable("create.weighted_ejector.targeting",selection.getX(),selection.getY(),selection.getZ()).withColor(0x55FF55));
        }
        currentItem=null;selection=null;lastHovered=-1;
    }
    private static void tick(Minecraft client){
        if(client.level!=selectedLevel){selection=null;currentItem=null;selectedLevel=client.level;lastHovered=-1;}
        if(client.player==null||client.level==null)return;
        if(holding(client))begin(client);else currentItem=null;
        if(client.player.getMainHandItem().is(AllItems.WRENCH)&&client.hitResult instanceof BlockHitResult hit){
            if(LoadedLevelAccess.blockEntity(client.level,hit.getBlockPos()) instanceof EjectorBlockEntity ejector){
                if(lastHovered!=hit.getBlockPos().asLong()){selection=ejector.getTargetPosition();lastHovered=hit.getBlockPos().asLong();}
            }else{lastHovered=-1;selection=null;}
        }
    }
    private static void outline(DrawableGizmoPrimitives primitives,AABB box,int color){
        double[] low={box.minX,box.minY,box.minZ},high={box.maxX,box.maxY,box.maxZ};
        for(int axis=0;axis<3;axis++)for(int a=0;a<2;a++)for(int b=0;b<2;b++){
            var start=low.clone();var end=low.clone();int other=(axis+1)%3,last=(axis+2)%3;
            start[other]=end[other]=a==0?low[other]:high[other];start[last]=end[last]=b==0?low[last]:high[last];end[axis]=high[axis];
            primitives.addLine(new Vec3(start[0],start[1],start[2]),new Vec3(end[0],end[1],end[2]),color,2);
        }
    }
    public static Frame extract(Minecraft client){
        var primitives=new DrawableGizmoPrimitives();int segments=0;BlockPos candidate=null;boolean valid=false;
        if(client.level!=null&&client.player!=null&&client.gui.screen()==null&&selection!=null&&(holding(client)||client.player.getMainHandItem().is(AllItems.WRENCH))){
            var shape=LoadedLevelAccess.blockState(client.level,selection).getShape(client.level,selection);
            outline(primitives,(shape.isEmpty()?new AABB(BlockPos.ZERO):shape.bounds()).move(selection).inflate(.005),0xFFFFCB74);
            if(client.hitResult instanceof BlockHitResult hit){
                boolean wrench=client.player.getMainHandItem().is(AllItems.WRENCH);candidate=wrench?hit.getBlockPos():hit.getBlockPos().relative(hit.getDirection());
                int x=selection.getX()-candidate.getX(),y=selection.getY()-candidate.getY(),z=selection.getZ()-candidate.getZ();
                int validX=Math.abs(z)>Math.abs(x)?0:x,validZ=Math.abs(z)<Math.abs(x)?0:z;
                BlockPos projected=selection.offset(validX,y,validZ);Direction direction=validDirection(projected,selection,EjectorConfig.get(client.level).maxDistance());
                if(direction!=null){
                    valid=x==validX&&z==validZ;int color=valid?0xFF9EDE73:0xFFFF7171;
                    var launcher=new EntityLauncher(Math.abs(validX+validZ),y);double duration=launcher.getTotalFlyingTicks()+3;segments=(int)duration/3+1;
                    outline(primitives,new AABB(0,0,0,1,0,1).move(selection.offset(-validX,-y,-validZ)),color);
                    for(int index=0;index<segments;index++){
                        Vec3 start=launcher.getGlobalPos(duration*index/segments,direction,candidate).add(x-validX,0,z-validZ);
                        Vec3 end=launcher.getGlobalPos(duration*(index+1)/segments,direction,candidate).add(x-validX,0,z-validZ);
                        primitives.addLine(start,end,color,2);
                    }
                }
            }
        }
        return new Frame(primitives,selection,candidate,segments,valid);
    }
    public static void register(){
        EjectorNetworking.installClientTrigger(pos->ClientPlayNetworking.send(new EjectorTriggerPacket(pos)));
        ClientPlayNetworking.registerGlobalReceiver(EjectorPlacementPacket.ClientBoundRequest.TYPE,(packet,context)->flushSettings(packet.pos(),packet.nonce()));
        ClientPlayNetworking.registerGlobalReceiver(EjectorNetworking.Limits.TYPE,(packet,context)->EjectorConfig.installClient(packet.values()));
        ClientPlayNetworking.registerGlobalReceiver(EjectorNetworking.Settings.TYPE,(packet,context)->{if(context.client().level!=null&&LoadedLevelAccess.blockEntity(context.client().level,packet.pos()) instanceof EjectorBlockEntity)context.client().gui.setScreen(new EjectorSettingsScreen(packet.pos(),packet.value()));});
        ClientPlayNetworking.registerGlobalReceiver(EjectorLaunchPacket.TYPE,(packet,context)->{
            var player=context.player();var client=context.client();if(client.level==null||!(LoadedLevelAccess.blockEntity(client.level,packet.pos()) instanceof EjectorBlockEntity ejector))return;
            player.setPos(packet.pos().getX()+.5,packet.pos().getY()+1,packet.pos().getZ()+.5);player.setOnGround(false);
            new EntityLauncher(packet.h(),packet.v()).applyMotion(player,packet.facing());
            if((long)packet.h()*packet.h()+(long)packet.v()*packet.v()>=625)ClientPlayNetworking.send(new EjectorAwardPacket(packet.pos(),packet.nonce()));
            if(packet.glider()&&net.minecraft.world.entity.LivingEntity.canGlideUsing(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST),net.minecraft.world.entity.EquipmentSlot.CHEST)){
                player.setXRot(-35);player.setYRot(packet.facing().toYRot());player.setDeltaMovement(player.getDeltaMovement().scale(.75f));
                if(ejector.deployElytra(player))ClientPlayNetworking.send(new EjectorElytraPacket(packet.pos(),packet.nonce()));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{selection=null;currentItem=null;selectedLevel=null;lastHovered=-1;FRAMES.clear();latest=null;EjectorConfig.clearClient();});
        ClientTickEvents.END_CLIENT_TICK.register(EjectorTargetHandler::tick);
        UseBlockCallback.EVENT.register((player,level,hand,hit)->level.isClientSide()&&hand==InteractionHand.MAIN_HAND&&select(hit.getBlockPos())?InteractionResult.SUCCESS:InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player,level,hand,pos,face)->level.isClientSide()&&hand==InteractionHand.MAIN_HAND&&deselect(pos)?InteractionResult.FAIL:InteractionResult.PASS);
        LevelExtractionEvents.END_EXTRACTION.register(context->{var frame=extract(Minecraft.getInstance());FRAMES.put(context.levelState(),frame);latest=frame;});
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{var frame=FRAMES.get(context.levelState());if(frame!=null)frame.primitives.submit(context.submitNodeCollector(),context.levelState().cameraRenderState,false);});
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("ejector_stack_size"),(gui,delta)->{
            var client=Minecraft.getInstance();if(client.level==null||client.player==null||client.gui.screen()!=null||client.gui.hud.isHidden()||!(client.hitResult instanceof BlockHitResult hit)||!(LoadedLevelAccess.blockEntity(client.level,hit.getBlockPos()) instanceof EjectorBlockEntity ejector)||!EjectorBlock.settingsHit(ejector.getBlockState(),hit.getBlockPos(),hit,ejector))return;
            gui.centeredText(client.font,Component.translatable("create.weighted_ejector.stack_size").append(": ").append(ejector.getMinimumStackSize()==0?"*":Integer.toString(ejector.getMinimumStackSize())),gui.guiWidth()/2,gui.guiHeight()-88,0xFFFBDC7D);
        });
    }
    private EjectorTargetHandler(){}
}
