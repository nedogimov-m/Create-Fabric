package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.bearing.ClockworkBearingBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.KineticGraph;
import com.simibubi.create.foundation.mixin.accessor.ServerLevelEntityManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

/** Native actor callbacks test the boundary between two real owned hand transfers. */
public class PairedRestorationGameTests {
    private static final BlockPos B=new BlockPos(4,3,4),H=B.above(),M=B.above(2),W=H.west(),T=M.west();

    private static ClockworkBearingBlockEntity setup(GameTestHelper h){
        h.setBlock(B,AllBlocks.CLOCKWORK_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));
        h.setBlock(H,Blocks.GOLD_BLOCK);h.setBlock(W,Blocks.STONE);h.setBlock(M,Blocks.BARREL);
        h.setBlock(T,Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING,Direction.WEST));
        var cargo=new ItemStack(Items.DIAMOND,7);cargo.set(DataComponents.CUSTOM_NAME,Component.literal("Paired restoration cargo"));
        h.getBlockEntity(M,BarrelBlockEntity.class).setItem(0,cargo);
        for(var end:new BlockPos[]{H,T}){
            var glue=new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(W),h.absolutePos(end)));
            h.assertTrue(h.getLevel().addFreshEntity(glue),"Native level accepts the narrow hour-only glue volume");
        }
        var bearing=h.getBlockEntity(B,ClockworkBearingBlockEntity.class);bearing.assemble();
        h.assertTrue(bearing.getHourHand()!=null&&bearing.getMinuteHand()!=null,"Both native hand owners exist");
        h.assertValueEqual(bearing.getHourHand().getContraption().getBlocks().size(),3,"Hour owns the base, bridge and wall torch");
        h.assertValueEqual(bearing.getMinuteHand().getContraption().getBlocks().size(),1,"Minute owns the torch support barrel independently");
        h.assertBlockPresent(Blocks.AIR,H);h.assertBlockPresent(Blocks.AIR,M);h.assertBlockPresent(Blocks.AIR,T);
        return bearing;
    }

    private static void exactWorld(GameTestHelper h){
        h.assertBlockPresent(Blocks.GOLD_BLOCK,H);h.assertBlockPresent(Blocks.STONE,W);h.assertBlockPresent(Blocks.BARREL,M);h.assertBlockPresent(Blocks.WALL_TORCH,T);
        var cargo=h.getBlockEntity(M,BarrelBlockEntity.class).getItem(0);
        h.assertTrue(cargo.is(Items.DIAMOND)&&cargo.getCount()==7&&cargo.has(DataComponents.CUSTOM_NAME),"Exactly seven named diamonds restore once");
    }

    /** The provider exists only while this synchronous real capture/transfer callback runs. */
    private static void withStopHook(Consumer<MovementContext> hook,Runnable body){
        var active=new AtomicBoolean(true);
        var actor=new MovementBehaviour(){@Override public void stopMoving(MovementContext context){if(active.get())hook.accept(context);}};
        MovementBehaviour.REGISTRY.registerProvider(block->active.get()&&block==Blocks.GOLD_BLOCK?actor:null);
        try{body.run();}finally{active.set(false);MovementBehaviour.REGISTRY.invalidate();}
    }

    @GameTest public void adjacentHandSupportRestoresBeforeHourAttachmentShapeUpdates(GameTestHelper h){
        var bearing=setup(h);h.assertTrue(bearing.disassemble(),"Both real hands restore as one deferred neighbor sequence");exactWorld(h);
        h.runAfterDelay(2,()->{exactWorld(h);h.assertFalse(bearing.isRunning(),"Both ownership identities completed");h.succeed();});
    }

    @SuppressWarnings("unchecked")
    @GameTest public void unavailableSecondHandAfterFirstTransferKeepsAttachmentAndExactCargoForRetry(GameTestHelper h)throws Exception{
        var manager=((ServerLevelEntityManagerAccessor)h.getLevel()).create$getEntityManager();
        var field=PersistentEntitySectionManager.class.getDeclaredField("chunkLoadStatuses");field.setAccessible(true);
        var statuses=(Long2ObjectMap<Object>)field.get(manager);long key=ChunkPos.pack(h.absolutePos(M));
        h.assertTrue(h.getLevel().areEntitiesLoaded(key),"Actual minute entity data starts ready");
        var saved=statuses.get(key);var changed=new AtomicBoolean();
        try{
            withStopHook(context->{
                if(changed.compareAndSet(false,true)){
                    h.assertTrue(context.contraption.phase()==Contraption.Phase.REMOVED,"Actual hour stop callback executes inside restoration");
                    statuses.remove(key);
                }
            },()->{
                var bearing=setup(h);var hour=bearing.getHourHand();var minute=bearing.getMinuteHand();
                h.assertFalse(bearing.disassemble(),"Second native restoration preflight refuses entity data removed by the first actor callback");
                h.assertTrue(changed.get()&&hour.getContraption().disassembled,"First transfer really completed before the minute refusal");
                h.assertBlockPresent(Blocks.GOLD_BLOCK,H);h.assertBlockPresent(Blocks.AIR,M);
                h.assertBlockPresent(Blocks.WALL_TORCH,T);
                h.assertTrue(minute.isAlive()&&bearing.getMinuteHand()==minute&&bearing.isRecovering(),"Exact remaining native owner enters recovery");
                var owned=minute.getContraption().getStorage().getAllItemStorages().get(BlockPos.ZERO).getStackInSlot(0);
                h.assertTrue(owned.is(Items.DIAMOND)&&owned.getCount()==7&&owned.has(DataComponents.CUSTOM_NAME),"Refused minute retains all named cargo");
                var savedBearing=bearing.saveWithFullMetadata(h.getLevel().registryAccess());
                var pending=savedBearing.read("PendingRestorationUpdates",BlockPos.CODEC.listOf()).orElseThrow();
                h.assertValueEqual(java.util.Set.copyOf(pending),java.util.Set.of(h.absolutePos(H),h.absolutePos(W),h.absolutePos(T)),"Only already restored absolute cells persist for later finalization");
                h.assertTrue(!savedBearing.contains("HourOwner")&&savedBearing.read("MinuteOwner",UUIDUtil.CODEC).orElseThrow().equals(minute.getUUID()),"Persist only the remaining live cargo owner UUID");
                var restored=(ClockworkBearingBlockEntity)BlockEntity.loadStatic(h.absolutePos(B),h.getBlockState(B),savedBearing,h.getLevel().registryAccess());
                h.assertTrue(restored!=null&&restored.getLevel()==null&&restored.isRecovering(),"Native load-before-level preserves pending recovery");
                restored.setLevel(h.getLevel());h.getLevel().setBlockEntity(restored);
                h.assertTrue(restored.getHourHand()==null&&restored.getMinuteHand()==minute,"Reload resolves the same original minute owner without recreating hour cargo");
                statuses.put(key,saved);
                restored.setNetworkState(16,16,false,new KineticGraph.Stress(512,64,false),java.util.Map.of(Direction.DOWN,16f));
                ClockworkBearingBlockEntity.tick(h.getLevel(),h.absolutePos(B),h.getBlockState(B),restored);
                h.assertTrue(restored.getSpeed()==16&&!restored.isRecovering()&&!restored.isRunning(),"Native recovery tick finishes manual disassembly even under nonzero drive");exactWorld(h);
                h.assertFalse(restored.saveWithFullMetadata(h.getLevel().registryAccess()).contains("PendingRestorationUpdates"),"Successful paired finalization clears the persisted pending cells");
                ClockworkBearingBlockEntity.tick(h.getLevel(),h.absolutePos(B),h.getBlockState(B),restored);
                h.assertTrue(restored.getSpeed()==16&&!restored.isRunning()&&restored.getHourHand()==null&&restored.getMinuteHand()==null,"Following native tick must not reassemble after completing a manual stop");exactWorld(h);
            });
        }finally{statuses.put(key,saved);}
        h.succeed();
    }

    @GameTest public void realStopCallbackCannotReenterEitherOwnedHandRestoration(GameTestHelper h){
        var callback=new AtomicBoolean();var bearingRef=new java.util.concurrent.atomic.AtomicReference<ClockworkBearingBlockEntity>();
        withStopHook(context->{
            h.assertTrue(callback.compareAndSet(false,true),"Hour stop executes exactly once");var bearing=bearingRef.get();var minute=bearing.getMinuteHand();
            h.assertFalse(bearing.disassemble(),"Reentrant controller request is refused during the active hour transfer");
            h.assertTrue(context.contraption.phase()==Contraption.Phase.REMOVED&&context.contraption.getStorage().ownsContents()&&minute.getContraption().getStorage().ownsContents(),"Nested request cannot release either cargo owner");
            h.assertBlockPresent(Blocks.AIR,H);h.assertBlockPresent(Blocks.AIR,M);
        },()->{
            var bearing=setup(h);bearingRef.set(bearing);h.assertTrue(bearing.disassemble(),"Outer native paired transfer completes");
            h.assertTrue(callback.get(),"Real registered actor stop callback ran");exactWorld(h);
            h.assertTrue(bearing.getHourHand()==null&&bearing.getMinuteHand()==null&&!bearing.isRecovering(),"Only outer completion clears both identities and recovery state");
        });h.succeed();
    }
}
