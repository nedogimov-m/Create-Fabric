package com.simibubi.create.content.legacy;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.utility.NativeEntityAcceptance;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;

/** Test-only faults at actual native admission, early onCreated and world destroy boundaries. */
public final class LegacyMaterialTestFixtures {
 public enum Mode{REFUSE,MUTATE_REMOVE_REFUSE,EARLY_SAVE,EARLY_THROW,EARLY_REENTER,DESTROY_FALSE,DESTROY_THROW}
 public static final class NativeFault extends Error{public NativeFault(){super("Actual native legacy material callback");}}
 public static final class Observation{public ItemEntity endpoint;public CompoundTag saved;public int attempts,destroys,sourceAtDestroy;public boolean exactInsertion,notYetVisible;public final NativeFault fault=new NativeFault();}
 private record Armed(ServerLevel level,ItemEntity source,BlockPos light,Mode mode,Observation observation){}
 private static final ThreadLocal<Armed> ACTIVE=new ThreadLocal<>();
 public static void with(ServerLevel level,ItemEntity source,BlockPos light,Mode mode,Observation observation,Runnable action){if(ACTIVE.get()!=null)throw new IllegalStateException("Nested native legacy fixture");ACTIVE.set(new Armed(level,source,light,mode,observation));try{action.run();}finally{ACTIVE.remove();}}
 private static boolean target(Armed scope,Entity entity){return scope!=null&&scope.level==entity.level()&&entity instanceof ItemEntity item&&(item.getItem().is(AllItems.CHROMATIC_COMPOUND)||item.getItem().is(AllItems.REFINED_RADIANCE))&&NativeEntityAcceptance.isPreparedEndpointInCurrentAdd(entity);}
 public static boolean before(ServerLevel level,Entity entity){var scope=ACTIVE.get();if(!target(scope,entity))return false;var observation=scope.observation;observation.endpoint=(ItemEntity)entity;observation.attempts++;if(scope.mode==Mode.REFUSE)return true;if(scope.mode==Mode.MUTATE_REMOVE_REFUSE){observation.endpoint.setItem(new ItemStack(Items.EMERALD,3));entity.discard();return true;}return false;}
 public static void early(Entity entity){var scope=ACTIVE.get();if(!target(scope,entity)||scope.mode!=Mode.EARLY_SAVE&&scope.mode!=Mode.EARLY_THROW&&scope.mode!=Mode.EARLY_REENTER)return;var observation=scope.observation;observation.endpoint=(ItemEntity)entity;observation.exactInsertion=NativeEntityAcceptance.isAcceptedSourceInCurrentAdd(entity);observation.notYetVisible=scope.level.getEntity(entity.getUUID())==null;observation.saved=capture(scope.level);if(scope.mode==Mode.EARLY_REENTER){var current=scope.source.getItem();if(current.getItem() instanceof ChromaticCompoundItem item)item.onEntityItemUpdate(current,scope.source);}if(scope.mode==Mode.EARLY_THROW)throw observation.fault;}
 public static boolean destroy(net.minecraft.world.level.Level level,BlockPos pos){var scope=ACTIVE.get();if(scope==null||scope.level!=level||!pos.equals(scope.light))return false;var observation=scope.observation;observation.destroys++;observation.sourceAtDestroy=scope.source.getItem().getCount();observation.saved=capture(scope.level);if(scope.mode==Mode.DESTROY_THROW)throw observation.fault;return scope.mode==Mode.DESTROY_FALSE;}
 public static CompoundTag capture(ServerLevel level){return (CompoundTag)LegacyMaterialDrops.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE),LegacyMaterialDrops.get(level)).getOrThrow();}
 public static void decode(ServerLevel level,CompoundTag saved){level.getDataStorage().set(LegacyMaterialDrops.TYPE,LegacyMaterialDrops.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE),saved).getOrThrow());}
 @SuppressWarnings("unchecked") static List<LegacyMaterialDrops.Receipt> pending(ServerLevel level){try{var field=LegacyMaterialDrops.class.getDeclaredField("entries");field.setAccessible(true);return (List<LegacyMaterialDrops.Receipt>)field.get(LegacyMaterialDrops.get(level));}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}}
 static void retry(ServerLevel level){for(var receipt:List.copyOf(pending(level)))LegacyMaterialDrops.get(level).emit(level,receipt);}
 static int owned(ServerLevel level,Item item){int count=0;var ops=level.registryAccess().createSerializationContext(NbtOps.INSTANCE);for(var tag:capture(level).getListOrEmpty("Pending")){var entry=(CompoundTag)tag;var loose=entry.read("Loose",ItemStack.OPTIONAL_CODEC,ops).orElse(ItemStack.EMPTY);if(loose.is(item))count+=loose.getCount();var stack=entry.getCompoundOrEmpty("Entity").read("Item",ItemStack.OPTIONAL_CODEC,ops).orElse(ItemStack.EMPTY);if(stack.is(item))count+=stack.getCount();}return count;}
}
