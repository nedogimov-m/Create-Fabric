package com.simibubi.create.test;

import com.google.gson.Gson;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.*;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.*;

/** Test-only observations of the real submitted frame. Never calls extraction or a renderer itself. */
public final class EquipmentNativeFrameProbe {
    private static final Gson JSON=new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create();
    private static boolean active,inFrame,requireHands;
    private static String label;
    private static int observedFrames;
    private static final List<Frame> frames=new ArrayList<>();
    private static Frame current;
    private static Matrix4f projection;
    private record Bounds(Vector3f min,Vector3f max){}
    private static final java.util.Deque<Bounds> ITEM_BOUNDS=new java.util.ArrayDeque<>();
    public static final class Frame {
        public int number,nativeHands,customHandCalls,customAccepted,armSubmissions,itemStates,draws,vertices,finiteClip,forwardVertices,inScreenVertices,boundsViolations,drawsWithoutNativeBounds;
        public boolean panoramic,hasPlayer,firstPerson,sleeping,hudHidden,spectator,avatar,invisible,scoping,mainCustom,offCustom;
        public String cameraType,selection,mainItem,offItem;
        public float oldMainHeight,mainHeight,oldOffHeight,offHeight,mainSwapScale,offSwapScale;
        public final List<Map<String,Object>> calls=new ArrayList<>();
        public final List<Map<String,Object>> geometry=new ArrayList<>();
    }
    public static void begin(String name){
        var client=Minecraft.getInstance();label=name;frames.clear();observedFrames=0;current=null;projection=null;inFrame=false;active=true;ITEM_BOUNDS.clear();
        var player=client.player;
        requireHands=player!=null&&!player.isSpectator()&&client.options.getCameraType().isFirstPerson()&&(source(player.getMainHandItem())||source(player.getOffhandItem()));
    }
    private static boolean source(net.minecraft.world.item.ItemStack stack){return stack.is(AllItems.POTATO_CANNON)||stack.is(AllItems.EXTENDO_GRIP);}
    public static void frame(CameraRenderState camera,PlayerRenderState player,GameRenderState game,Projection hud){
        if(!active)return;inFrame=true;var f=new Frame();f.number=++observedFrames;f.panoramic=camera.isPanoramicMode;f.hasPlayer=player.hasPlayer;f.cameraType=String.valueOf(game.optionsRenderState.cameraType);f.firstPerson=game.optionsRenderState.cameraType.isFirstPerson();f.sleeping=camera.entityRenderState!=null&&camera.entityRenderState.isSleeping;f.hudHidden=game.guiRenderState.isHudHidden;f.spectator=Minecraft.getInstance().gameMode.getPlayerMode()==net.minecraft.world.level.GameType.SPECTATOR;f.avatar=player.avatarRenderState!=null;f.invisible=f.avatar&&player.avatarRenderState.isInvisible;
        var hands=player.firstPersonHandsAndItems;if(hands!=null){f.oldMainHeight=hands.oldMainHandHeight;f.mainHeight=hands.mainHandHeight;f.oldOffHeight=hands.oldOffHandHeight;f.offHeight=hands.offHandHeight;f.mainSwapScale=hands.mainHandSwapScale;f.offSwapScale=hands.offHandSwapScale;f.scoping=hands.isScoping;f.selection=String.valueOf(hands.handRenderSelection);f.mainItem=hands.mainHandItem.toString();f.offItem=hands.offHandItem.toString();var equipment=((EquipmentHandsAccess)hands).create$equipmentHands();f.mainCustom=equipment.main.custom;f.offCustom=equipment.off.custom;}
        projection=hud.getMatrix(new Matrix4f());if(frames.size()==4)frames.removeFirst();frames.add(f);current=f;
    }
    public static void finishFrame(){inFrame=false;}
    public static void nativeHands(){if(observing())current.nativeHands++;}
    private static boolean observing(){return active&&inFrame&&current!=null;}
    public static void custom(PlayerRenderState player,FirstPersonHandsAndItemsRenderState state,InteractionHand which,PoseStack pose,Boolean accepted){
        if(!observing())return;var hand=which==InteractionHand.MAIN_HAND?((EquipmentHandsAccess)state).create$equipmentHands().main:((EquipmentHandsAccess)state).create$equipmentHands().off;
        if(accepted==null){current.customHandCalls++;var call=new LinkedHashMap<String,Object>();call.put("hand",which.toString());call.put("custom",hand.custom);call.put("suppressed",hand.suppressed);call.put("itemEmpty",hand.item==null||hand.item.isEmpty());call.put("pose",matrix(pose.last().pose()));call.put("arm",matrix(hand.arm));call.put("tool",matrix(hand.tool));call.put("avatar",player.avatarRenderState!=null);current.calls.add(call);}else if(accepted)current.customAccepted++;
    }
    public static void arm(){if(observing())current.armSubmissions++;}
    public static void itemState(net.minecraft.client.renderer.item.ItemStackRenderState state,PoseStack pose){
        if(!observing())return;current.itemStates++;var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);var itemPose=new Matrix4f(pose.last().pose());
        state.visitExtents(point->{var actual=itemPose.transformPosition(point,new Vector3f());min.min(actual);max.max(actual);});ITEM_BOUNDS.push(new Bounds(min,max));
        var receipt=new LinkedHashMap<String,Object>();receipt.put("nativeVisitExtentsMin",List.of(min.x,min.y,min.z));receipt.put("nativeVisitExtentsMax",List.of(max.x,max.y,max.z));if(current.geometry.size()<32)current.geometry.add(receipt);
    }
    public static void finishItemState(){if(observing()&&!ITEM_BOUNDS.isEmpty())ITEM_BOUNDS.pop();}
    public static void draw(PoseStack pose,ItemDisplayContext display,ItemQuads quads){
        if(!observing())return;var f=current;f.draws++;var clip=new Matrix4f(projection).mul(RenderSystem.getModelViewStack()).mul(pose.last().pose());var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);int vertices=0,finite=0,forward=0,inside=0;
        if(ITEM_BOUNDS.isEmpty())f.drawsWithoutNativeBounds++;var nativeBounds=ITEM_BOUNDS.peek();
        for(var quad:quads.all())for(int i=0;i<4;i++){vertices++;if(nativeBounds!=null){var actual=pose.last().pose().transformPosition(quad.position(i),new Vector3f());float epsilon=.0001f;if(actual.x<nativeBounds.min.x-epsilon||actual.y<nativeBounds.min.y-epsilon||actual.z<nativeBounds.min.z-epsilon||actual.x>nativeBounds.max.x+epsilon||actual.y>nativeBounds.max.y+epsilon||actual.z>nativeBounds.max.z+epsilon)f.boundsViolations++;}var point=new Vector4f(quad.position(i),1);clip.transform(point);if(Float.isFinite(point.x)&&Float.isFinite(point.y)&&Float.isFinite(point.z)&&Float.isFinite(point.w)&&point.w!=0){finite++;if(point.w>0){forward++;if(java.lang.Math.abs(point.x)<=point.w&&java.lang.Math.abs(point.y)<=point.w)inside++;}var projected=new Vector3f(point.x/point.w,point.y/point.w,point.z/point.w);min.min(projected);max.max(projected);}}
        f.vertices+=vertices;f.finiteClip+=finite;f.forwardVertices+=forward;f.inScreenVertices+=inside;
        if(f.geometry.size()<32){var entry=new LinkedHashMap<String,Object>();entry.put("display",display.toString());entry.put("vertices",vertices);entry.put("finiteClip",finite);entry.put("forward",forward);entry.put("insideXY",inside);entry.put("pose",matrix(pose.last().pose()));entry.put("projectionViewPose",matrix(clip));if(finite>0){entry.put("ndcMin",List.of(min.x,min.y,min.z));entry.put("ndcMax",List.of(max.x,max.y,max.z));}f.geometry.add(entry);}
    }
    private static List<Float> matrix(Matrix4fc matrix){float[] values=new float[16];matrix.get(values);var result=new ArrayList<Float>(16);for(float value:values)result.add(value);return result;}
    public static void end(){
        active=false;inFrame=false;var receipt=new LinkedHashMap<String,Object>();receipt.put("scene",label);receipt.put("requiredSourceHands",requireHands);receipt.put("observedFrameCount",observedFrames);receipt.put("lastActualNativeFrames",frames);System.out.println("CREATE_EQUIPMENT_NATIVE_FRAME "+JSON.toJson(receipt));
        if(!requireHands)return;if(frames.isEmpty())throw new AssertionError("Real GameRenderer never submitted a hand/screen frame; see native frame receipt");var f=frames.getLast();
        if(f.nativeHands==0||f.customHandCalls==0||f.customAccepted==0||f.itemStates==0||f.draws==0||f.vertices==0||f.finiteClip!=f.vertices||f.forwardVertices==0||f.inScreenVertices==0||f.boundsViolations>0||f.drawsWithoutNativeBounds>0||!f.invisible&&f.armSubmissions==0)throw new AssertionError("Real source equipment frame did not submit finite, visible hand/item geometry: "+JSON.toJson(f));
    }
    private EquipmentNativeFrameProbe(){}
}
