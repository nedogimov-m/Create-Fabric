package com.simibubi.create.content.trains.entity;

import com.google.common.collect.MapMaker;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.world.entity.Entity;

/** Weak witnesses to actual native NPCs, not additional entities or serialized material owners. */
public final class CarriageNpcMaterialOwners {
    private record Witness(WeakReference<CarriagePassengerLifecycle> lifecycle,WeakReference<CarriageContraptionEntity> front,int seat,int entityId,UUID id){}
    // Weak identity keys never call Entity.hashCode(), which requires an assigned native ID.
    private static final Map<Entity,Witness> ACTUAL=new MapMaker().weakKeys().makeMap();
    static void remember(CarriagePassengerLifecycle lifecycle,CarriageContraptionEntity front,int seat,Entity actual){ACTUAL.put(actual,new Witness(new WeakReference<>(lifecycle),new WeakReference<>(front),seat,actual.getId(),actual.getUUID()));}
    static boolean permitsMove(Entity actual,Entity parent,CarriageContraptionEntity target){var witness=ACTUAL.get(actual);if(witness==null||actual.getId()!=witness.entityId()||!actual.getUUID().equals(witness.id()))return true;var lifecycle=witness.lifecycle().get();return lifecycle==null||lifecycle.permitsMove(witness.seat(),actual,parent,target);}
    /** True means vanilla may continue; false means the exact source was handled or safely withheld. */
    public static boolean beforeNonDestroyUnload(Entity actual,Entity.RemovalReason reason){
        if(reason!=Entity.RemovalReason.UNLOADED_TO_CHUNK||CarriageNpcRetirement.active(actual)||actual.isRemoved())return true;
        var witness=ACTUAL.get(actual);if(witness==null||actual.getId()!=witness.entityId()||!actual.getUUID().equals(witness.id()))return true;
        var lifecycle=witness.lifecycle().get();var front=witness.front().get();return lifecycle==null||front==null||lifecycle.beforeExternalUnload(front,witness.seat(),actual);
    }
    public static void afterActualUnlink(Entity actual){
        if(actual instanceof net.minecraft.world.entity.player.Player||actual.isRemoved()||CarriagePlayerReferenceTransitions.protectsNativeTransferRoot(actual))return;var witness=ACTUAL.get(actual);if(witness==null||actual.getId()!=witness.entityId()||!actual.getUUID().equals(witness.id()))return;var lifecycle=witness.lifecycle().get();var front=witness.front().get();if(lifecycle!=null&&front!=null)lifecycle.afterVoluntaryNativeUnlink(front,witness.seat(),actual);
    }
    public static void afterActualReason(Entity actual,Entity.RemovalReason reason){
        if(!reason.shouldDestroy()&&reason!=Entity.RemovalReason.CHANGED_DIMENSION&&reason!=Entity.RemovalReason.UNLOADED_TO_CHUNK)return;var witness=ACTUAL.get(actual);if(witness==null||actual.getId()!=witness.entityId()||!actual.getUUID().equals(witness.id())||actual.getRemovalReason()!=reason)return;
        var lifecycle=witness.lifecycle().get();var front=witness.front().get();if(lifecycle!=null&&front!=null)lifecycle.nativeSourceConsumed(front,witness.seat(),actual,reason.shouldDestroy());ACTUAL.remove(actual);
    }
    private CarriageNpcMaterialOwners(){}
}
