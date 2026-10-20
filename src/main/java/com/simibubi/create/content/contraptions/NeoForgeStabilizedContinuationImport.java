package com.simibubi.create.content.contraptions;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.simibubi.create.foundation.utility.*;
import java.util.Optional;
import net.minecraft.nbt.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.phys.Vec3;

/** Actual complete direct saved parent/child relationship, no substituted cart or controller. */
final class NeoForgeStabilizedContinuationImport {
    static void convert(CompoundTag actualOriginalParent,CompoundTag actualOriginalChild,CompoundTag convertedChild,int version,HolderLookup.Provider registries){
        if(version!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION)throw invalid("Unknown original stabilized continuation version");var parent=uuid(actualOriginalParent,"UUID");var child=uuid(actualOriginalChild,"UUID");
        if(!parent.equals(uuid(convertedChild,"ParentContraption"))||!child.equals(uuid(convertedChild,"UUID")))throw invalid("Actual original stabilized parent/child identity changed during conversion");
        int found=0;for(var value:list(actualOriginalParent,"Passengers")){if(!(value instanceof CompoundTag node))throw invalid("Malformed actual source stabilized parent passenger");if(uuid(node,"UUID").equals(child)){if(!node.equals(actualOriginalChild))throw invalid("Stabilized continuation does not use its exact actual original direct child");found++;}}
        if(found!=1)throw invalid("Actual stabilized continuation requires exactly one genuine direct source parent edge");
        Optional<Vec3> motion=Optional.empty();if(actualOriginalChild.contains("CachedMotion")){
            var values=vector(list(actualOriginalChild,"CachedMotion"));motion=Optional.of(new Vec3(((DoubleTag)values.get(0)).doubleValue(),((DoubleTag)values.get(1)).doubleValue(),((DoubleTag)values.get(2)).doubleValue()));var zero=new ListTag();for(int axis=0;axis<3;axis++)zero.add(DoubleTag.valueOf(0));convertedChild.put("Motion",zero);
        }
        boolean stalled=bool(actualOriginalChild,"Stalled");if(stalled){var actual=motion.orElseThrow(()->invalid("Frozen original stabilized child lost its genuine saved parent-motion cache"));var pending=new StabilizedParentMotion(parent,actual);convertedChild.put(StabilizedParentMotion.KEY,StabilizedParentMotion.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),pending).getOrThrow());}
        float yaw=floating(actualOriginalChild,"Yaw");var force=actualOriginalChild.contains("ForceYaw")?Optional.of(floating(actualOriginalChild,"ForceYaw")):Optional.<Float>empty();float current=force.orElse(yaw);
        if(motion.isPresent()&&!motion.get().equals(Vec3.ZERO))current+=(float)((3*Math.PI/2+Math.atan2(motion.get().z,motion.get().x))/Math.PI*180);
        if(!Float.isFinite(current))throw invalid("Original stabilized load yaw overflows its finite source float");convertedChild.putFloat("Yaw",current);
        convertedChild.put(NeoForgeStabilizedSource.KEY,new NeoForgeStabilizedSource(version,parent,stalled,yaw,force,motion).write());
    }
    private NeoForgeStabilizedContinuationImport(){}
}
