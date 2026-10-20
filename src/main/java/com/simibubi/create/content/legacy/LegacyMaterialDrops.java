package com.simibubi.create.content.legacy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** Finite durable ownership of paid charged parts and source-created plain Radiance. */
public final class LegacyMaterialDrops extends SavedData {
 private record Encoded(Vec3 pos,Vec3 motion,int pickup,boolean justCreated,ItemStack loose,Optional<CompoundTag> entity,Optional<ResourceKey<Level>> dimension){
  static final Codec<Encoded> CODEC=RecordCodecBuilder.create(i->i.group(Vec3.CODEC.fieldOf("Pos").forGetter(Encoded::pos),Vec3.CODEC.fieldOf("Motion").forGetter(Encoded::motion),Codec.INT.fieldOf("Pickup").forGetter(Encoded::pickup),Codec.BOOL.optionalFieldOf("JustCreated",false).forGetter(Encoded::justCreated),ItemStack.OPTIONAL_CODEC.fieldOf("Loose").forGetter(Encoded::loose),CompoundTag.CODEC.optionalFieldOf("Entity").forGetter(Encoded::entity),Level.RESOURCE_KEY_CODEC.optionalFieldOf("Dimension").forGetter(Encoded::dimension)).apply(i,Encoded::new));
 }
 public static final Codec<LegacyMaterialDrops> CODEC=Encoded.CODEC.listOf().fieldOf("Pending").codec().xmap(LegacyMaterialDrops::new,data->data.entries.stream().map(Receipt::encode).toList());
 public static final SavedDataType<LegacyMaterialDrops> TYPE=new SavedDataType<>(Create.asResource("legacy_material_drops"),LegacyMaterialDrops::new,CODEC,null);
 private final List<Receipt> entries=new ArrayList<>();private ServerLevel ownerLevel;private boolean emitting;
 private final SnapshotParticipant<List<Receipt>> publication=new SnapshotParticipant<>(){
  @Override protected List<Receipt> createSnapshot(){return List.copyOf(entries);}
  @Override protected void readSnapshot(List<Receipt> before){entries.clear();entries.addAll(before);setDirty();}
  @Override protected void onFinalCommit(){setDirty();}
 };
 public LegacyMaterialDrops(){}
 private LegacyMaterialDrops(List<Encoded> saved){for(var entry:saved)entries.add(new Receipt(this,entry));}
 static LegacyMaterialDrops get(ServerLevel level){var data=level.getDataStorage().computeIfAbsent(TYPE);if(data.ownerLevel!=null&&data.ownerLevel!=level)throw new IllegalStateException("Legacy material data belongs to one native level");data.ownerLevel=level;return data;}
 private boolean current(ServerLevel level){return ownerLevel==level&&NativeSavedDataIdentity.isCurrent(level,TYPE,this);}
 Receipt own(ItemStack actualPart,Vec3 pos,Vec3 motion,int pickup,TransactionContext transaction){return own(actualPart,pos,motion,pickup,false,transaction);}
 Receipt own(ItemStack actualPart,Vec3 pos,Vec3 motion,int pickup,boolean justCreated,TransactionContext transaction){if(!current(ownerLevel))throw new IllegalStateException("Retired legacy material journal cannot take actual paid output");publication.updateSnapshots(transaction);var receipt=new Receipt(this,actualPart,pos,motion,pickup,justCreated);entries.add(receipt);setDirty();return receipt;}
 public static void tick(ServerLevel level){var data=get(level);if(data.emitting||level.getGameTime()%10!=0)return;for(var receipt:List.copyOf(data.entries))data.emit(level,receipt);}
 void emit(ServerLevel level,Receipt receipt){
  if(emitting||!current(level)||receipt.owner!=this||!entries.contains(receipt)||com.simibubi.create.content.equipment.TreeFertilizerSimulation.active(level)!=null)return;
  emitting=true;
  try{
   receipt.retainCurrentRejected();
   if(receipt.serialized!=null){var uuid=receipt.serialized.read("UUID",UUIDUtil.CODEC).orElse(null);if(uuid==null)throw new IllegalStateException("Native charged item save has no UUID");for(var world:level.getServer().getAllLevels())if(world.getEntity(uuid)!=null)return;var target=receipt.dimension==null?level:level.getServer().getLevel(receipt.dimension);if(target==null||!LoadedEntityAccess.isReady(target,net.minecraft.core.BlockPos.containing(receipt.pos)))return;var problems=new ProblemReporter.Collector();var loaded=EntityType.loadEntityRecursive(TagValueInput.create(problems,target.registryAccess(),receipt.serialized),target,EntitySpawnReason.LOAD,actual->actual);if(!current(level))return;if(!problems.isEmpty()||!(loaded instanceof ItemEntity item)||!item.getUUID().equals(uuid))throw new IllegalStateException("Actual saved charged item failed native decoding: "+problems.getReport());receipt.pending=item;receipt.serialized=null;}
   if(receipt.pending==null){if(receipt.stack.isEmpty()){entries.remove(receipt);setDirty();return;}if(!LoadedEntityAccess.isReady(level,net.minecraft.core.BlockPos.containing(receipt.pos)))return;var item=new ItemEntity(level,receipt.pos.x,receipt.pos.y,receipt.pos.z,receipt.stack);item.setDeltaMovement(receipt.motion);item.setPickUpDelay(receipt.pickup);if(receipt.justCreated)LegacyItemData.of(item).putBoolean("JustCreated",true);if(!current(level))return;receipt.pending=item;receipt.stack=ItemStack.EMPTY;setDirty();}
   var endpoint=receipt.pending;if(endpoint.isRemoved()){receipt.retainCurrentRejected();return;}if(!(endpoint.level() instanceof ServerLevel actual)||actual.getServer()!=level.getServer()||actual.getEntity(endpoint.getUUID())!=null||!LoadedEntityAccess.isReady(actual,endpoint.blockPosition()))return;
   try(var acceptance=NativeEntityAcceptance.prepare(endpoint,()->current(level)&&entries.contains(receipt)&&receipt.pending==endpoint&&!endpoint.isRemoved()&&!endpoint.getItem().isEmpty(),insertion->{insertion.requireCommitting(endpoint);entries.remove(receipt);setDirty();})){acceptance.add();}
   finally{if(current(level)&&entries.contains(receipt))receipt.retainCurrentRejected();}
  }finally{setDirty();emitting=false;}
 }
 static final class Receipt {
  final LegacyMaterialDrops owner;ItemStack stack;ItemEntity pending;CompoundTag serialized;ResourceKey<Level> dimension;final Vec3 pos,motion;final int pickup;boolean justCreated;
  Receipt(LegacyMaterialDrops owner,ItemStack stack,Vec3 pos,Vec3 motion,int pickup,boolean justCreated){this.owner=owner;this.stack=stack;this.pos=pos;this.motion=motion;this.pickup=pickup;this.justCreated=justCreated;}
  Receipt(LegacyMaterialDrops owner,Encoded saved){this(owner,saved.loose,saved.pos,saved.motion,saved.pickup,saved.justCreated);serialized=saved.entity.orElse(null);dimension=saved.dimension.orElse(null);if(serialized!=null&&!stack.isEmpty())throw new IllegalArgumentException("Two material owners in a native charged-item receipt");}
  void retainCurrentRejected(){if(pending!=null&&(pending.isRemoved()||pending.getItem().isEmpty())){stack=pending.getItem();justCreated=LegacyItemData.of(pending).contains("JustCreated");pending=null;serialized=null;dimension=null;owner.setDirty();}}
  Encoded encode(){retainCurrentRejected();if(pending==null)return new Encoded(pos,motion,pickup,justCreated,stack,Optional.ofNullable(serialized),Optional.ofNullable(dimension));var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,pending.registryAccess());if(!pending.save(output))throw new IllegalStateException("Current real charged item could not be saved natively");return new Encoded(pos,motion,pickup,justCreated,ItemStack.EMPTY,Optional.of(output.buildResult()),Optional.of(pending.level().dimension()));}
 }
}
