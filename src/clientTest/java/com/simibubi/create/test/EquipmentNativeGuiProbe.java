package com.simibubi.create.test;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllItems;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.*;

/** Observes native GUI atlas rendering of the actual paid InventoryScreen items. No renderer/extraction calls. */
public final class EquipmentNativeGuiProbe {
    private static boolean active;
    private static String label;
    private static Object latestActualCannonBakeIdentity;
    public static Object actualCannonBakedIdentity(){return latestActualCannonBakeIdentity;}
    private static Slot slot;
    private static final List<Map<String,Object>> RECEIPTS=new ArrayList<>();
    private static final Map<String,Object> IDENTITIES=new LinkedHashMap<>();
    private static int cannonSlots,gripSlots,vertices,finite,inside,boundsViolations;
    private static final class Slot {
        final Vector3f min=new Vector3f(Float.POSITIVE_INFINITY),max=new Vector3f(Float.NEGATIVE_INFINITY);
        Matrix4f projection;int x,y,size;boolean cannon,grip;
    }
    public static void begin(String name){var client=Minecraft.getInstance();if(!(client.gui.screen() instanceof InventoryScreen)||client.player.getInventory().countItem(AllItems.POTATO_CANNON)!=1||client.player.getInventory().countItem(AllItems.EXTENDO_GRIP)!=2||!client.player.containerMenu.getCarried().isEmpty())throw new AssertionError("Real native inventory must retain the original one cannon/two grips and empty carried slot before GUI observation");label=name;slot=null;RECEIPTS.clear();IDENTITIES.clear();cannonSlots=gripSlots=vertices=finite=inside=boundsViolations=0;active=true;}
    public static void identity(net.minecraft.client.renderer.item.ItemStackRenderState state,net.minecraft.world.item.ItemStack stack,ItemDisplayContext display){
        if(display!=ItemDisplayContext.GUI||!(state instanceof net.minecraft.client.renderer.item.TrackingItemStackRenderState tracking)||!stack.is(AllItems.POTATO_CANNON)&&!stack.is(AllItems.EXTENDO_GRIP))return;
        Object key=tracking.getModelIdentity();if(!(key instanceof java.util.List<?> list)||list.isEmpty()){if(active)throw new AssertionError("Actual source GUI model has empty native atlas identity: "+stack);return;}
        if(stack.is(AllItems.POTATO_CANNON))latestActualCannonBakeIdentity=list.getFirst();if(!active)return;
        var immutable=java.util.List.copyOf(list);String item=stack.is(AllItems.POTATO_CANNON)?"cannon":"grip";String other=item.equals("cannon")?"grip":"cannon";if(immutable.equals(IDENTITIES.get(other)))throw new AssertionError("Actual native GUI cannon/grip model identities collide");IDENTITIES.put(item,immutable);
    }
    public static void nativeSlot(int x,int y,int size,Projection projection,ItemStackRenderState state,PoseStack pose){
        if(!active)return;var actual=new Slot();actual.x=x*size;actual.y=y*size;actual.size=size;actual.projection=projection.getMatrix(new Matrix4f());var base=new Matrix4f(pose.last().pose());state.visitExtents(point->{var transformed=base.transformPosition(point,new Vector3f());actual.min.min(transformed);actual.max.max(transformed);});slot=actual;
    }
    public static void finishSlot(){if(!active||slot==null)return;if(slot.cannon)cannonSlots++;if(slot.grip)gripSlots++;slot=null;}
    public static void draw(Object renderer,PoseStack pose,ItemDisplayContext display,ItemQuads quads){
        if(!active||slot==null||display!=ItemDisplayContext.GUI)return;String type=renderer.getClass().getName();boolean cannon=type.endsWith("PotatoCannonItemRenderer"),grip=type.endsWith("ExtendoGripItemRenderer");if(!cannon&&!grip)return;slot.cannon|=cannon;slot.grip|=grip;var clip=new Matrix4f(slot.projection).mul(RenderSystem.getModelViewStack()).mul(pose.last().pose());int count=0,finiteDraw=0,insideDraw=0,violations=0;
        for(var quad:quads.all())for(int i=0;i<4;i++){
            count++;var actual=pose.last().pose().transformPosition(quad.position(i),new Vector3f());float epsilon=.01f;
            if(actual.x<slot.min.x-epsilon||actual.y<slot.min.y-epsilon||actual.z<slot.min.z-epsilon||actual.x>slot.max.x+epsilon||actual.y>slot.max.y+epsilon||actual.z>slot.max.z+epsilon)violations++;
            var projected=clip.transform(new Vector4f(quad.position(i),1));if(Float.isFinite(projected.x)&&Float.isFinite(projected.y)&&Float.isFinite(projected.z)&&Float.isFinite(projected.w)&&projected.w!=0)finiteDraw++;
            if(actual.x>=slot.x&&actual.y>=slot.y&&actual.x<=slot.x+slot.size&&actual.y<=slot.y+slot.size)insideDraw++;
        }
        vertices+=count;finite+=finiteDraw;inside+=insideDraw;boundsViolations+=violations;
        if(RECEIPTS.size()<48){var record=new LinkedHashMap<String,Object>();record.put("sourceRenderer",type);record.put("actualAtlasSlot",List.of(slot.x,slot.y,slot.size));record.put("vertices",count);record.put("finiteClip",finiteDraw);record.put("insideAtlasSlot",insideDraw);record.put("nativeBoundsViolations",violations);record.put("nativeVisitExtentsMin",List.of(slot.min.x,slot.min.y,slot.min.z));record.put("nativeVisitExtentsMax",List.of(slot.max.x,slot.max.y,slot.max.z));record.put("submittedPose",matrix(pose.last().pose()));record.put("projectionViewPose",matrix(clip));RECEIPTS.add(record);}
    }
    private static List<Float> matrix(Matrix4fc value){var array=new float[16];value.get(array);var result=new ArrayList<Float>();for(float number:array)result.add(number);return result;}
    public static void end(){active=false;slot=null;var receipt=new LinkedHashMap<String,Object>();receipt.put("scene",label);receipt.put("actualSourceIdentityKinds",new java.util.ArrayList<>(IDENTITIES.keySet()));receipt.put("realCannonAtlasSlots",cannonSlots);receipt.put("realGripAtlasSlots",gripSlots);receipt.put("vertices",vertices);receipt.put("finiteClip",finite);receipt.put("insideActualAtlasSlots",inside);receipt.put("nativeBoundsViolations",boundsViolations);receipt.put("draws",RECEIPTS);String json=new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(receipt);System.out.println("CREATE_EQUIPMENT_NATIVE_GUI "+json);if(!IDENTITIES.containsKey("cannon")||!IDENTITIES.containsKey("grip")||cannonSlots==0||gripSlots==0||vertices==0||finite!=vertices||inside==0||boundsViolations!=0)throw new AssertionError("Actual source InventoryScreen atlas geometry/bounds failed: "+json);}
    private EquipmentNativeGuiProbe(){}
}
