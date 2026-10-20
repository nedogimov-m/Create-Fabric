package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.foundation.mixin.accessor.ServerLevelEntityManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Actual restored seats retain original passenger identities across late native refusal. */
public class PassengerHandoffGameTests {
    private static final BlockPos P=new BlockPos(3,3,3),S=P.east();
    private record Fixture(MechanicalBearingBlockEntity bearing,ControlledContraptionEntity owner,Entity passenger){}
    private record Readiness(Long2ObjectMap<Object> values,long key,Object previous){
        void remove(){values.remove(key);}
        void restore(){values.put(key,previous);}
    }

    private static Fixture moving(GameTestHelper h){
        h.setBlock(P.below(),AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));
        h.setBlock(P,Blocks.BARREL);h.setBlock(S,AllBlocks.SEATS.get(DyeColor.RED));
        var cargo=new ItemStack(Items.DIAMOND,7);cargo.set(DataComponents.CUSTOM_NAME,Component.literal("Passenger handoff cargo"));
        h.getBlockEntity(P,BarrelBlockEntity.class).setItem(0,cargo);
        h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(P),h.absolutePos(S)))),"Actual narrow glue joins only the barrel and seat");
        var bearing=h.getBlockEntity(P.below(),MechanicalBearingBlockEntity.class);bearing.assemble();var owner=bearing.getMovedContraption();
        h.assertTrue(owner!=null&&owner.getContraption().getBlocks().size()==2,"Actual controller accepts one native barrel-and-seat owner");
        var passenger=h.spawnWithNoFreeWill(EntityTypes.PIG,S.above());passenger.setNoGravity(true);
        h.assertTrue(owner.addSittingPassenger(passenger,owner.getContraption().getSeats().indexOf(S.subtract(P))),"Original native pig boards the actual moving seat");
        h.assertTrue(h.getLevel().getEntity(passenger.getUUID())==passenger&&passenger.getVehicle()==owner,"Level and owner share the original passenger object/UUID");
        h.assertBlockPresent(Blocks.AIR,P);h.assertBlockPresent(Blocks.AIR,S);
        return new Fixture(bearing,owner,passenger);
    }

    private static void exactCargo(GameTestHelper h){
        var cargo=h.getBlockEntity(P,BarrelBlockEntity.class).getItem(0);
        h.assertTrue(cargo.is(Items.DIAMOND)&&cargo.getCount()==7&&cargo.has(DataComponents.CUSTOM_NAME),"World barrel retains exactly seven named diamonds");
    }
    private static void originalAlive(GameTestHelper h,Fixture f){
        h.assertTrue(f.passenger().isAlive()&&h.getLevel().getEntity(f.passenger().getUUID())==f.passenger(),"Original native passenger identity survives; no replacement entity is loaded");
    }
    private static SeatEntity seated(GameTestHelper h,Fixture f,BlockPos target){
        originalAlive(h,f);h.assertTrue(f.passenger().getVehicle() instanceof SeatEntity,"Original passenger rides an actual stationary native seat");
        var vehicle=(SeatEntity)f.passenger().getVehicle();
        h.assertTrue(!vehicle.isRemoved()&&h.getLevel().getEntity(vehicle.getUUID())==vehicle&&vehicle.blockPosition().equals(h.absolutePos(target)),"Accepted stationary vehicle exists at the actual restored seat");
        h.assertValueEqual(h.getLevel().getEntitiesOfClass(SeatEntity.class,new AABB(h.absolutePos(target))).size(),1,"One native seat vehicle owns this target");
        return vehicle;
    }
    private static void withBarrelStop(Consumer<MovementContext> hook,Runnable body){
        var active=new AtomicBoolean(true);var actor=new MovementBehaviour(){@Override public void stopMoving(MovementContext context){if(active.get())hook.accept(context);}};
        MovementBehaviour.REGISTRY.registerProvider(block->active.get()&&block==Blocks.BARREL?actor:null);
        try{body.run();}finally{active.set(false);MovementBehaviour.REGISTRY.invalidate();}
    }
    /** Discard inside the real native load callback, after addFreshEntity has begun accepting it. */
    private static void withSeatRefusal(GameTestHelper h,BlockPos target,Runnable body){
        var active=new AtomicBoolean(true);var refused=new AtomicInteger();var absolute=h.absolutePos(target);
        ServerEntityEvents.ENTITY_LOAD.register((entity,level)->{
            if(active.get()&&level==h.getLevel()&&entity instanceof SeatEntity&&entity.blockPosition().equals(absolute)){refused.incrementAndGet();entity.discard();}
        });
        try{body.run();h.assertTrue(refused.get()>0,"Actual native ENTITY_LOAD callback refused the requested stationary seat");}
        finally{active.set(false);}
    }
    private static void queued(GameTestHelper h,Fixture f,BlockPos target,int before){
        originalAlive(h,f);h.assertTrue(f.owner().isRemoved()&&f.owner().getContraption().phase()==Contraption.Phase.DISASSEMBLED&&!f.owner().getContraption().getStorage().ownsContents(),"World transfer completes and retires only the former cargo owner");
        h.assertFalse(f.passenger().isPassenger(),"Refused target leaves the original passenger safely dismounted");
        h.assertTrue(ContraptionPassengerHandoff.isPending(h.getLevel(),h.absolutePos(target))&&ContraptionPassengerHandoff.pendingCount(h.getLevel())==before+1,"Actual restored seat retains one pending passenger identity");
        var dismount=ContraptionPassengerHandoff.pendingDismount(h.getLevel(),f.passenger().getUUID());
        h.assertTrue(dismount!=null&&dismount.distanceTo(Vec3.atBottomCenterOf(h.absolutePos(target)).add(0,.5,0))<.00001,"Durable dismount uses the restored world seat");exactCargo(h);
    }
    @SuppressWarnings("unchecked") private static Readiness readiness(GameTestHelper h,BlockPos target)throws Exception{
        var manager=((ServerLevelEntityManagerAccessor)h.getLevel()).create$getEntityManager();var field=PersistentEntitySectionManager.class.getDeclaredField("chunkLoadStatuses");field.setAccessible(true);
        var values=(Long2ObjectMap<Object>)field.get(manager);long key=ChunkPos.pack(h.absolutePos(target));
        h.assertTrue(h.getLevel().areEntitiesLoaded(key),"Actual restoration entity data starts ready");return new Readiness(values,key,values.get(key));
    }

    @GameTest public void lateEntityReadinessLossAfterRestorePreflightKeepsTheSamePassengerForRetry(GameTestHelper h)throws Exception{
        var target=P.north();var readiness=readiness(h,target);var changed=new AtomicBoolean();var fixture=new AtomicReference<Fixture>();int before=ContraptionPassengerHandoff.pendingCount(h.getLevel());
        try{
            withBarrelStop(context->{
                h.assertTrue(context.contraption.phase()==Contraption.Phase.REMOVED,"Real barrel stop runs after the actual restoration preflight");
                if(changed.compareAndSet(false,true))readiness.remove();
            },()->{
                var f=moving(h);fixture.set(f);f.bearing().setAngle(90);
                h.assertTrue(f.bearing().disassemble(),"Late entity refusal does not reverse completed ordinary world placement");
                h.assertTrue(changed.get(),"Real actor callback changed native readiness during restoration");queued(h,f,target,before);
                h.assertTrue(f.passenger().position().distanceTo(Vec3.atBottomCenterOf(h.absolutePos(target)).add(0,.5,0))<.001,"Actual native eject lands by the transformed seat rather than the old rider pose");
            });
        }finally{readiness.restore();}
        var f=fixture.get();
        ContraptionPassengerHandoff.tick(h.getLevel());seated(h,f,target);exactCargo(h);h.assertValueEqual(ContraptionPassengerHandoff.pendingCount(h.getLevel()),before,"Successful native retry acknowledges the pending UUID");h.succeed();
    }

    @GameTest public void nativeSeatSpawnRefusalSurvivesSavedDataCodecAndActualDataStorageReload(GameTestHelper h){
        var f=moving(h);int before=ContraptionPassengerHandoff.pendingCount(h.getLevel());
        withSeatRefusal(h,S,()->{h.assertTrue(f.bearing().disassemble(),"Ordinary native restoration completes despite stationary vehicle refusal");queued(h,f,S,before);});
        h.assertTrue(h.getLevel().getEntitiesOfClass(SeatEntity.class,new AABB(h.absolutePos(S))).isEmpty(),"Discarded native reservations cannot become passenger owners");
        var storage=h.getLevel().getDataStorage();var data=storage.computeIfAbsent(ContraptionPassengerHandoff.TYPE);var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var encoded=ContraptionPassengerHandoff.CODEC.encodeStart(ops,data).getOrThrow();var entries=((CompoundTag)encoded).getListOrEmpty("Pending");CompoundTag entry=null;
        for(var value:entries)if(value instanceof CompoundTag candidate&&candidate.read("Passenger",UUIDUtil.CODEC).orElseThrow().equals(f.passenger().getUUID()))entry=candidate;
        h.assertTrue(entry!=null&&entry.keySet().equals(Set.of("Passenger","Seat"))&&entry.read("Seat",BlockPos.CODEC).orElseThrow().equals(h.absolutePos(S)),"SavedData persists only the actual original UUID and target block position");
        var restored=ContraptionPassengerHandoff.CODEC.parse(ops,encoded).getOrThrow();storage.set(ContraptionPassengerHandoff.TYPE,restored);
        h.assertTrue(storage.computeIfAbsent(ContraptionPassengerHandoff.TYPE)==restored&&ContraptionPassengerHandoff.isPending(h.getLevel(),h.absolutePos(S)),"Native level DataStorage uses the decoded pending handoff instance");
        ContraptionPassengerHandoff.tick(h.getLevel());seated(h,f,S);exactCargo(h);h.assertValueEqual(ContraptionPassengerHandoff.pendingCount(h.getLevel()),before,"Reload retry acknowledges only the original pending identity");h.succeed();
    }

    @GameTest public void destroyedRestoredSeatEndsPendingIntentWithoutInventingAVehicleOrPassenger(GameTestHelper h){
        var f=moving(h);int before=ContraptionPassengerHandoff.pendingCount(h.getLevel());
        withSeatRefusal(h,S,()->{h.assertTrue(f.bearing().disassemble(),"Actual restore places the original seat");queued(h,f,S,before);});
        h.setBlock(S,Blocks.AIR);ContraptionPassengerHandoff.tick(h.getLevel());originalAlive(h,f);
        h.assertFalse(f.passenger().isPassenger()||ContraptionPassengerHandoff.isPending(h.getLevel(),h.absolutePos(S)),"Destroyed target cancels its old seating intent without remounting");
        h.assertTrue(h.getLevel().getEntitiesOfClass(SeatEntity.class,new AABB(h.absolutePos(S))).isEmpty(),"Retry never fabricates a stationary vehicle over air");
        h.assertValueEqual(ContraptionPassengerHandoff.pendingCount(h.getLevel()),before,"Destroyed target releases only its pending entry");exactCargo(h);h.succeed();
    }

    @GameTest public void foreignOccupantOfRestoredTargetWinsOverAnOldPendingPassenger(GameTestHelper h){
        var f=moving(h);int before=ContraptionPassengerHandoff.pendingCount(h.getLevel());
        withSeatRefusal(h,S,()->{h.assertTrue(f.bearing().disassemble(),"Actual restore places seat before refusal");queued(h,f,S,before);});
        var foreign=h.spawnWithNoFreeWill(EntityTypes.PIG,S.above());foreign.setNoGravity(true);
        h.assertTrue(SeatBlock.trySitDown(h.getLevel(),h.absolutePos(S),foreign),"A later real native passenger occupies the restored seat");var vehicle=foreign.getVehicle();
        ContraptionPassengerHandoff.tick(h.getLevel());originalAlive(h,f);
        h.assertTrue(vehicle instanceof SeatEntity&&!vehicle.isRemoved()&&foreign.getVehicle()==vehicle&&vehicle.hasPassenger(foreign)&&!f.passenger().isPassenger(),"Retry cannot eject the foreign rider or steal its actual native vehicle");
        h.assertValueEqual(h.getLevel().getEntitiesOfClass(SeatEntity.class,new AABB(h.absolutePos(S))).size(),1,"Foreign target retains exactly its original stationary vehicle");
        h.assertValueEqual(ContraptionPassengerHandoff.pendingCount(h.getLevel()),before,"Superseded seating intent no longer blocks source recapture");exactCargo(h);h.succeed();
    }

    @GameTest public void ordinaryOwnerRestoreHandsBackTheOriginalPassengerAndExactCargoOnce(GameTestHelper h){
        var f=moving(h);int before=ContraptionPassengerHandoff.pendingCount(h.getLevel());
        h.assertTrue(f.bearing().disassemble(),"Ordinary native restore succeeds without a readiness or spawn refusal");var seat=seated(h,f,S);exactCargo(h);
        h.assertTrue(f.owner().isRemoved()&&!f.bearing().isRunning()&&!f.owner().getContraption().getStorage().ownsContents(),"Completed native owner releases cargo once and retires");
        ContraptionPassengerHandoff.tick(h.getLevel());h.assertTrue(f.passenger().getVehicle()==seat,"Later handoff ticks retain the same accepted stationary vehicle");
        h.assertValueEqual(ContraptionPassengerHandoff.pendingCount(h.getLevel()),before,"Successful ordinary handoff leaves no extra pending work");exactCargo(h);h.succeed();
    }

    @GameTest public void sourceRecaptureWaitsForTheOriginalPendingPassengerHandoff(GameTestHelper h)throws Exception{
        var f=moving(h);int before=ContraptionPassengerHandoff.pendingCount(h.getLevel());
        withSeatRefusal(h,S,()->{h.assertTrue(f.bearing().disassemble(),"Actual restored source retains a pending passenger");queued(h,f,S,before);});
        var recapture=new BearingContraption(false,Direction.UP);boolean refused=false;
        h.assertTrue(recapture.assemble(h.getLevel(),h.absolutePos(P.below()))&&recapture.getBlocks().size()==2,"Pure source search sees both actual restored blocks without transferring ownership");
        try{recapture.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO);}catch(AssemblyException pending){refused=pending.canRetry();}
        h.assertTrue(refused&&recapture.phase()==Contraption.Phase.CAPTURED&&!recapture.getStorage().ownsContents(),"Actual common ownership transfer refuses a restored seat still owned by the pending UUID handoff");
        h.assertBlockPresent(AllBlocks.SEATS.get(DyeColor.RED),S);exactCargo(h);originalAlive(h,f);
        ContraptionPassengerHandoff.tick(h.getLevel());seated(h,f,S);var fresh=new BearingContraption(false,Direction.UP);
        h.assertTrue(fresh.assemble(h.getLevel(),h.absolutePos(P.below()))&&fresh.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"Fresh ordinary ownership transfer succeeds after the actual native passenger is seated");
        h.assertTrue(fresh.getInitialPassengers().values().stream().anyMatch(passenger->passenger==f.passenger()),"Fresh source transfer captures the same actual seated passenger identity");
        h.assertBlockPresent(Blocks.AIR,P);h.assertBlockPresent(Blocks.AIR,S);
        h.assertTrue(fresh.tryAddBlocksToWorld(h.getLevel(),new StructureTransform(h.absolutePos(P),0,0,0)),"Cleanup restores both original source blocks and exact cargo");originalAlive(h,f);
        h.assertValueEqual(ContraptionPassengerHandoff.pendingCount(h.getLevel()),before,"Successful retry clears the source barrier");exactCargo(h);h.succeed();
    }
}
