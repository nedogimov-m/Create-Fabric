package com.simibubi.create.content.trains.entity;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.Entity;

/** One exact non-destroy native removal. A copied live snapshot cannot independently pay admission. */
public final class CarriageNpcRetirement implements AutoCloseable {
    record Payment(BooleanSupplier current,Runnable commit){}
    private static final ThreadLocal<IdentityHashMap<Entity,CarriageNpcRetirement>> ACTIVE=ThreadLocal.withInitial(IdentityHashMap::new);
    private final CarriagePassengerLifecycle lifecycle;private final CarriageContraptionEntity front;private final int seat;private final Entity actual;private Payment payment;private boolean committed,closed;
    private CarriageNpcRetirement(CarriagePassengerLifecycle lifecycle,CarriageContraptionEntity front,int seat,Entity actual){this.lifecycle=lifecycle;this.front=front;this.seat=seat;this.actual=actual;if(ACTIVE.get().putIfAbsent(actual,this)!=null)throw new IllegalStateException("Native NPC already has a source retirement");}
    static CarriageNpcRetirement prepare(CarriagePassengerLifecycle lifecycle,CarriageContraptionEntity front,int seat,Entity actual){return new CarriageNpcRetirement(lifecycle,front,seat,actual);}
    public static boolean active(Entity actual){return ACTIVE.get().containsKey(actual);}
    /** Immediately before vanilla first writes removalReason, including real subclass pre-remove mutations. */
    public static boolean beforeNativeReason(Entity actual,Entity.RemovalReason reason){var scope=ACTIVE.get().get(actual);if(scope==null)return true;if(scope.closed||scope.committed||reason!=Entity.RemovalReason.UNLOADED_TO_CHUNK||actual.isRemoved())return false;scope.payment=scope.lifecycle.preparePayment(scope.front,scope.seat,actual);return scope.payment!=null&&scope.payment.current().getAsBoolean();}
    /** Actual non-destroy removal is now irreversible; commit before native rider/world callbacks. */
    public static void afterNativeReason(Entity actual,Entity.RemovalReason reason){var scope=ACTIVE.get().get(actual);if(scope==null)return;if(scope.closed||scope.committed||reason!=Entity.RemovalReason.UNLOADED_TO_CHUNK||actual.getRemovalReason()!=reason||scope.payment==null)throw new IllegalStateException("Exact actual NPC non-destroy removal proof required");scope.payment.commit().run();scope.committed=true;CarriageNativeSources.retired(actual);}
    public boolean committed(){return committed;}
    @Override public void close(){if(!closed){closed=true;ACTIVE.get().remove(actual,this);if(ACTIVE.get().isEmpty())ACTIVE.remove();}}
}
