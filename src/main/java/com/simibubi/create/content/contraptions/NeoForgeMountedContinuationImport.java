package com.simibubi.create.content.contraptions;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.simibubi.create.content.contraptions.minecart.capability.MinecartController;
import com.simibubi.create.foundation.utility.NeoForgeOrientedSource;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;

/** A genuine complete saved cart/direct mounted child jointly supply the new native continuation. */
final class NeoForgeMountedContinuationImport {
    static void convert(CompoundTag actualOriginalCart,CompoundTag actualOriginalChild,CompoundTag convertedCart,
            CompoundTag convertedChild,int version,HolderLookup.Provider registries){
        if(version!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION)throw invalid("Unknown original mounted continuation version");
        var cartId=uuid(actualOriginalCart,"UUID");var childId=uuid(actualOriginalChild,"UUID");
        if(!cartId.equals(uuid(convertedCart,"UUID"))||!childId.equals(uuid(convertedChild,"UUID")))throw invalid("Mounted continuation lost an actual original owner identity");
        int matches=0;for(var value:list(actualOriginalCart,"Passengers")){
            if(!(value instanceof CompoundTag child))throw invalid("Malformed actual source cart passenger");
            if(uuid(child,"UUID").equals(childId)){if(!child.equals(actualOriginalChild))throw invalid("Mounted continuation does not use its exact actual direct source child");matches++;}
            else if(string(child,"id").equals("create:contraption")&&string(compound(child,"Contraption"),"Type").equals("create:mounted"))throw invalid("One native source cart cannot acquire two canonical mounted body owners");
        }
        if(matches!=1)throw invalid("Mounted continuation requires its one actual direct source child");
        String sourceKind="ABSENT";
        if(actualOriginalCart.contains("neoforge:attachments")){
            var original=compound(actualOriginalCart,"neoforge:attachments");
            if(original.contains("create:minecart_controller"))sourceKind=string(compound(original,"create:minecart_controller"),"Type");
        }
        var attachments=convertedCart.contains("fabric:attachments")?compound(convertedCart,"fabric:attachments").copy():new CompoundTag();
        var controller=attachments.contains("create:minecart_controller")?compound(attachments,"create:minecart_controller").copy():new CompoundTag();
        if(sourceKind.equals("NORMAL")&&!attachments.contains("create:minecart_controller"))throw invalid("A genuine source normal controller lost its native converted value");
        if(!Set.of("ABSENT","EMPTY","NORMAL").contains(sourceKind))throw invalid("Unknown original mounted source controller kind");
        if(!sourceKind.equals("NORMAL")){
            if(controller.contains("LegacyEmpty")){if(!bool(controller,"LegacyEmpty")||controller.size()!=1)throw invalid("Source empty controller acquired unexplained native owned fields");controller.remove("LegacyEmpty");}
            else if(!controller.isEmpty())throw invalid("An absent source controller cannot replace actual native controller data");
        }
        if(controller.contains("MountedRoot")&&!childId.equals(uuid(controller,"MountedRoot")))throw invalid("Actual native cart already names a different mounted owner");
        controller.store("MountedRoot",UUIDUtil.CODEC,childId);
        Optional<Vec3> cached=Optional.empty();
        if(actualOriginalChild.contains("CachedMotion")){
            var vector=vector(list(actualOriginalChild,"CachedMotion"));cached=Optional.of(new Vec3(((DoubleTag)vector.get(0)).doubleValue(),((DoubleTag)vector.get(1)).doubleValue(),((DoubleTag)vector.get(2)).doubleValue()));
            var zero=new ListTag();for(int axis=0;axis<3;axis++)zero.add(DoubleTag.valueOf(0));convertedChild.put("Motion",zero);
        }
        float originalYaw=floating(actualOriginalChild,"Yaw");Optional<Float> forced=actualOriginalChild.contains("ForceYaw")?Optional.of(floating(actualOriginalChild,"ForceYaw")):Optional.empty();float currentYaw=forced.orElse(originalYaw);
        if(cached.isPresent()&&!cached.get().equals(Vec3.ZERO)){
            var value=cached.get();currentYaw+=(float)((3*Math.PI/2+Math.atan2(value.z,value.x))/Math.PI*180);
        }
        if(!Float.isFinite(currentYaw))throw invalid("Original mounted load pose overflows its actual finite float representation");
        convertedChild.putFloat("Yaw",currentYaw);boolean stalled=bool(actualOriginalChild,"Stalled");
        if(!sourceKind.equals("NORMAL")&&stalled){
            // NEW native external continuation. Its position/angles come from the
            // actual complete source cart; its motion is the actual old fallback
            // cache. This is not a claim that the old EMPTY controller saved it.
            var motion=cached.orElseThrow(()->invalid("Original fallback freeze lost its genuine saved motion cache"));
            var rotation=list(actualOriginalCart,"Rotation");if(rotation.size()!=2||!(rotation.get(0) instanceof FloatTag yaw)||!(rotation.get(1) instanceof FloatTag pitch))throw invalid("Original mounted cart lost its actual saved pose");
            var stall=new CompoundTag();stall.put("Pos",vector(list(actualOriginalCart,"Pos")).copy());stall.put("Motion",Vec3.CODEC.encodeStart(NbtOps.INSTANCE,motion).getOrThrow());stall.putFloat("Yaw",yaw.floatValue());stall.putFloat("Pitch",pitch.floatValue());stall.putBoolean("MotionOnly",true);controller.put("StallData",stall);
        }
        var ops=registries.createSerializationContext(NbtOps.INSTANCE);var actualController=MinecartController.CODEC.parse(ops,controller).getOrThrow();attachments.put("create:minecart_controller",MinecartController.CODEC.encodeStart(ops,actualController).getOrThrow());convertedCart.put("fabric:attachments",attachments);
        convertedChild.put(NeoForgeOrientedSource.KEY,new NeoForgeOrientedSource(version,sourceKind,stalled,originalYaw,forced,cached).write());
    }
    private NeoForgeMountedContinuationImport(){}
}
