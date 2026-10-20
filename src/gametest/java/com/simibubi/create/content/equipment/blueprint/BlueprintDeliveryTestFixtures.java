package com.simibubi.create.content.equipment.blueprint;

import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.foundation.utility.NativeEntityAcceptance;
import com.simibubi.create.test.BlueprintTestFixtures;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

/** Test-only callbacks inside actual native add/onCreated, with original physical endpoints. */
public final class BlueprintDeliveryTestFixtures {
 public enum Mode{REFUSE,MUTATE_REMOVE_REFUSE,EARLY_SAVE,EARLY_THROW}
 public static final class NativeRefusal extends Error{public NativeRefusal(){super("Actual native blueprint entity admission callback");}}
 public static final class Observation{public Entity endpoint;public CompoundTag saved;public boolean exactInsertion,notYetVisible;public int attempts;public final NativeRefusal error=new NativeRefusal();}
 private record Armed(ServerLevel level,Mode mode,Observation observation){}
 private static final ThreadLocal<Armed> ACTIVE=new ThreadLocal<>();
 public static void with(ServerLevel level,Mode mode,Observation observation,Runnable action){if(ACTIVE.get()!=null)throw new IllegalStateException("Nested actual blueprint admission fixture");ACTIVE.set(new Armed(level,mode,observation));try{action.run();}finally{ACTIVE.remove();}}
 private static boolean target(Armed scope,Entity entity){var stack=entity instanceof ItemEntity item?item.getItem():entity instanceof PackageEntity parcel?parcel.getBox():net.minecraft.world.item.ItemStack.EMPTY;return scope!=null&&scope.level==entity.level()&&stack.is(BlueprintTestFixtures.OUTPUT)&&NativeEntityAcceptance.isPreparedEndpointInCurrentAdd(entity);}
 public static boolean before(ServerLevel level,Entity entity){var scope=ACTIVE.get();if(!target(scope,entity))return false;var observation=scope.observation;observation.endpoint=entity;observation.attempts++;if(scope.mode==Mode.REFUSE)return true;if(scope.mode==Mode.MUTATE_REMOVE_REFUSE){var stack=((ItemEntity)entity).getItem();stack.shrink(1);entity.discard();return true;}return false;}
 public static void early(Entity entity){var scope=ACTIVE.get();if(!target(scope,entity)||scope.mode!=Mode.EARLY_SAVE&&scope.mode!=Mode.EARLY_THROW)return;var observation=scope.observation;observation.endpoint=entity;observation.exactInsertion=NativeEntityAcceptance.isAcceptedSourceInCurrentAdd(entity);observation.notYetVisible=scope.level.getEntity(entity.getUUID())==null;observation.saved=capture(scope.level);if(scope.mode==Mode.EARLY_THROW)throw observation.error;}
 public static CompoundTag capture(net.minecraft.world.level.Level level){var server=(ServerLevel)level;return (CompoundTag)BlueprintDelivery.CODEC.encodeStart(server.registryAccess().createSerializationContext(NbtOps.INSTANCE),BlueprintDelivery.get(server)).getOrThrow();}
 public static void installDecoded(ServerLevel level,CompoundTag data){var decoded=BlueprintDelivery.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE),data).getOrThrow();level.getDataStorage().set(BlueprintDelivery.TYPE,decoded);BlueprintDelivery.get(level);}
}
