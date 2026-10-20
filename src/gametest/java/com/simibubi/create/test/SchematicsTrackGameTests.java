package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.schematics.*;
import com.simibubi.create.content.schematics.cannon.*;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.content.trains.track.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.*;

/** Genuine physical item-built source curves, native snapshots, reserves and actual reciprocal world owners. UNRUN. */
public final class SchematicsTrackGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private static final BlockPos ORIGIN=new BlockPos(4,6,4),COPY=new BlockPos(4,6,20),
        A=ORIGIN.east(8),B=ORIGIN.offset(16,0,8),C=ORIGIN.south(8),
        CA=COPY.east(8),CB=COPY.offset(16,0,8),CC=COPY.south(8);
    private record Fixture(SchematicStructure snapshot,TrackBlockEntity source,Player player){}
    private static BlockState rail(TrackShape shape){return AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,shape);}
    private static TrackBlockEntity endpoint(GameTestHelper h,BlockPos pos,TrackShape shape){h.setBlock(pos.below(),Blocks.STONE);h.setBlock(pos,rail(shape).setValue(TrackBlock.HAS_BE,true));return h.getBlockEntity(pos,TrackBlockEntity.class);}
    private static void connect(GameTestHelper h,Player player,BlockPos first,BlockPos second,float yaw,boolean girders){
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.TRACK,64));player.setItemInHand(InteractionHand.OFF_HAND,girders?new ItemStack(AllBlocks.METAL_GIRDER,64):ItemStack.EMPTY);player.setYRot(yaw);player.setXRot(0);
        var start=h.absolutePos(first);player.setPos(Vec3.atCenterOf(start).add(0,1,2));h.assertTrue(player.getMainHandItem().getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atBottomCenterOf(start).add(0,.125,0),Direction.UP,start,false))).consumesAction(),"Actual source Track item selects its physical first endpoint");
        var end=h.absolutePos(second);player.setPos(Vec3.atCenterOf(end).add(0,1,2));player.setYRot(180);h.assertTrue(player.getMainHandItem().getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atBottomCenterOf(end).add(0,.125,0),Direction.UP,end,false))).consumesAction(),"Actual source Track item publishes the real curve and pays its native stock");h.assertTrue(h.getBlockEntity(first,TrackBlockEntity.class).getConnections().containsKey(end),"Genuine native source endpoint owns the actual item-built curve");
    }
    @FunctionalInterface private interface ReadyScenario { void run() throws Exception; }
    /** Waits only for actual source/visible query metadata; no tickets or readiness state are changed. */
    private static void whenExportReady(GameTestHelper h,ReadyScenario scenario){
        h.startSequence().thenWaitUntil(()->{
            var first=h.absolutePos(ORIGIN);var last=h.absolutePos(ORIGIN.offset(16,0,8));var checked=new HashSet<Long>();
            for(int x=first.getX()>>4;x<=last.getX()>>4;x++)for(int z=first.getZ()>>4;z<=last.getZ()>>4;z++){
                h.assertTrue(com.simibubi.create.foundation.utility.LoadedEntityAccess.isReady(h.getLevel(),x,z),"Actual native source entity storage is naturally available before source export");
                try{com.simibubi.create.foundation.utility.LoadedEntityAccess.requireGlueQueriesReady(h.getLevel(),new BlockPos(x<<4,first.getY(),z<<4),checked);}
                catch(com.simibubi.create.content.contraptions.AssemblyException pending){h.fail("Actual native schematic glue query preparation is pending: "+pending.component.getString());}
            }
        }).thenExecute(()->{
            try{scenario.run();}
            catch(RuntimeException|Error failure){throw failure;}
            catch(Exception failure){throw new RuntimeException("Actual native prepared schematic scenario failed",failure);}
        });
    }
    private static Fixture fixture(GameTestHelper h)throws java.io.IOException{
        endpoint(h,A,TrackShape.XO);endpoint(h,B,TrackShape.ZO);endpoint(h,C,TrackShape.ZO);var player=h.makeMockPlayer(GameType.SURVIVAL);connect(h,player,A,B,-90,false);connect(h,player,A,C,90,false);
        var level=h.getLevel();var actual=SchematicExport.capture(level,h.absolutePos(ORIGIN),h.absolutePos(ORIGIN.offset(16,0,8)));var template=SchematicFiles.decode(level,actual);var snapshot=SchematicStructure.load(level,template,h.absolutePos(COPY),new StructurePlaceSettings(),true);var source=(TrackBlockEntity)snapshot.getBlockEntity(h.absolutePos(CA));h.assertTrue(source!=null&&source.getConnections().size()==2&&source.getConnections().containsKey(h.absolutePos(CB))&&source.getConnections().containsKey(h.absolutePos(CC)),"Native exported source snapshot retains both genuinely built curves at actual transformed endpoints");return new Fixture(snapshot,source,player);
    }
    private static LaunchedItem paid(GameTestHelper h,Fixture fixture){
        var level=h.getLevel();var target=h.absolutePos(CA);var block=fixture.snapshot.getBlockState(target);var safe=fixture.snapshot.safeBlockEntity(target);var requirement=ItemRequirement.of(block,safe);var stock=new SchematicInventory(2,(slot,item)->true,slot->64,()->{},player->false);var tracks=new ItemStack(AllBlocks.TRACK,64);tracks.set(DataComponents.CUSTOM_NAME,Component.literal("Original reserved schematic tracks"));var girders=new ItemStack(AllBlocks.METAL_GIRDER,64);girders.set(DataComponents.CUSTOM_NAME,Component.literal("Original reserved schematic girders"));stock.setItem(0,tracks);stock.setItem(1,girders);
        var launch=new LaunchedItem.ForBlockState(target.west(3),target,new ItemStack(AllBlocks.TRACK),block,SchematicNbt.prepare(level,block,fixture.source));try(var transaction=Transaction.openOuter()){var result=SchematicPayment.collect(requirement,List.of(stock.storage),transaction);h.assertTrue(result.complete(),"Every source curve and base requirement is reserved through actual Fabric storage");launch.paid(result.receipt());transaction.commit();}launch.totalTicks=launch.ticksRemaining=0;return launch;
    }
    private static List<ItemStack> originals(LaunchedItem launch){return launch.paidOriginals();}
    private static void sameOriginals(GameTestHelper h,List<ItemStack> expected,LaunchedItem launch){var actual=launch.paidOriginals();h.assertTrue(actual.size()==expected.size(),"Actual reserve retains the exact original stack partition");for(int i=0;i<actual.size();i++)h.assertTrue(ItemStack.matches(actual.get(i),expected.get(i)),"All original counts/components remain in canonical private custody");}
    @GameTest(structure=STRUCTURE) public void missingActualReciprocalRefusesWithoutChangingExistingEndpointOrSpendingReserve(GameTestHelper h)throws Exception{
        whenExportReady(h,()->{
        var fixture=fixture(h);var first=endpoint(h,CA,TrackShape.XO);endpoint(h,CB,TrackShape.ZO);h.setBlock(CC,Blocks.AIR);var before=first.saveWithFullMetadata(h.getLevel().registryAccess());var launch=paid(h,fixture);var originals=originals(launch);h.assertFalse(launch.update(h.getLevel()),"Missing actual reciprocal endpoint refuses native geometry admission");h.assertTrue(first.getConnections().isEmpty()&&first.saveWithFullMetadata(h.getLevel().registryAccess()).equals(before),"Original actual first endpoint state/save/curves remain intact");sameOriginals(h,originals,launch);var restored=LaunchedItem.fromNBT(launch.serializeNBT(h.getLevel().registryAccess()),h.getLevel().registryAccess(),h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK));sameOriginals(h,originals,restored);endpoint(h,CC,TrackShape.ZO);h.assertTrue(restored.update(h.getLevel())&&restored.paidOriginals().isEmpty(),"The same original reserved material crosses custody only after actual reciprocal endpoints exist");h.succeed();
    
        });
    }
    @GameTest(structure=STRUCTURE) public void actualIdenticalAndSecondaryCurvesMergeWithoutReplacingUnrelatedOriginalOwner(GameTestHelper h)throws Exception{
        whenExportReady(h,()->{
        var fixture=fixture(h);endpoint(h,CA,TrackShape.XO);endpoint(h,CB,TrackShape.ZO);endpoint(h,CC,TrackShape.ZO);connect(h,fixture.player,CA,CB,-90,false);var first=h.getBlockEntity(CA,TrackBlockEntity.class);var second=h.getBlockEntity(CB,TrackBlockEntity.class);var original=first.getConnections().get(h.absolutePos(CB));var reverse=second.getConnections().get(h.absolutePos(CA));var launch=paid(h,fixture);h.assertTrue(launch.update(h.getLevel()),"Real duplicate curve plus new second curve are accepted by one native reciprocal merge");h.assertTrue(h.getBlockEntity(CA,TrackBlockEntity.class)==first&&h.getBlockEntity(CB,TrackBlockEntity.class)==second&&first.getConnections().get(h.absolutePos(CB))==original&&second.getConnections().get(h.absolutePos(CA))==reverse&&first.getConnections().size()==2,"Literal existing native owners and identical original curve objects remain intact");var source=fixture.snapshot.getBlockEntity(h.absolutePos(CC));h.assertTrue(SchematicPlacement.placeBlock(h.getLevel(),fixture.snapshot.getBlockState(h.absolutePos(CC)),h.absolutePos(CC),ItemStack.EMPTY,SchematicNbt.prepare(h.getLevel(),fixture.snapshot.getBlockState(h.absolutePos(CC)),source)),"Secondary reversed source curve is genuinely recognized as the same native ownership");h.assertTrue(first.getConnections().size()==2&&h.getBlockEntity(CC,TrackBlockEntity.class).getConnections().size()==1&&launch.paidOriginals().isEmpty(),"No extra actual curve or refundable material appears after secondary replay");h.succeed();
    
        });
    }
    @GameTest(structure=STRUCTURE) public void actualGeometryConflictPreservesBothOriginalConnectionsAndCompleteReservedStacks(GameTestHelper h)throws Exception{
        whenExportReady(h,()->{
        var fixture=fixture(h);endpoint(h,CA,TrackShape.XO);endpoint(h,CB,TrackShape.ZO);endpoint(h,CC,TrackShape.ZO);connect(h,fixture.player,CA,CB,-90,true);var first=h.getBlockEntity(CA,TrackBlockEntity.class);var second=h.getBlockEntity(CB,TrackBlockEntity.class);var before=first.saveWithFullMetadata(h.getLevel().registryAccess());var reverse=second.saveWithFullMetadata(h.getLevel().registryAccess());var launch=paid(h,fixture);var original=originals(launch);h.assertFalse(launch.update(h.getLevel()),"Actual girder-geometry ownership conflicts with ungirded original schematic geometry");h.assertTrue(first.saveWithFullMetadata(h.getLevel().registryAccess()).equals(before)&&second.saveWithFullMetadata(h.getLevel().registryAccess()).equals(reverse)&&h.getBlockEntity(CC,TrackBlockEntity.class).getConnections().isEmpty(),"Both original real owners and the unmodified third endpoint survive conflict preflight");sameOriginals(h,original,launch);h.succeed();
    
        });
    }
    @GameTest(structure=STRUCTURE) public void genuinelyUnavailableReciprocalChunkCannotBeForcedOrSpendOriginalReserve(GameTestHelper h)throws Exception{
        whenExportReady(h,()->{
        var fixture=fixture(h);var first=endpoint(h,CA,TrackShape.XO);endpoint(h,CB,TrackShape.ZO);endpoint(h,CC,TrackShape.ZO);
        var level=h.getLevel();var from=h.absolutePos(CA);var absent=net.minecraft.world.level.ChunkPos.containing(from).equals(net.minecraft.world.level.ChunkPos.containing(h.absolutePos(CB)))?h.absolutePos(CC):h.absolutePos(CB);
        var before=first.saveWithFullMetadata(level.registryAccess());var launch=paid(h,fixture);var originals=originals(launch);var tickets=SchematicBoundaryFixtures.tickets(level);
        TrackPavingGameTests.unavailable(h,absent,from,()->{h.assertFalse(launch.update(level),"Real unavailable native reciprocal terrain defers the paid source curve");h.assertTrue(first.saveWithFullMetadata(level.registryAccess()).equals(before),"Original actual endpoint/curve save is unchanged during native unavailability");sameOriginals(h,originals,launch);h.assertValueEqual(SchematicBoundaryFixtures.tickets(level),tickets,"Schematic curve retry adds no real native chunk ticket");});
        var restored=LaunchedItem.fromNBT(launch.serializeNBT(level.registryAccess()),level.registryAccess(),level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK));h.assertTrue(restored.update(level)&&restored.paidOriginals().isEmpty(),"The saved original receipt succeeds only after the exact genuine reciprocal chunk becomes available");h.succeed();
    
        });
    }
    @GameTest(structure=STRUCTURE) public void actualNativeMissingReciprocalOwnerStaysUnknownWithoutEmptyFactoryCreation(GameTestHelper h)throws Exception{
        whenExportReady(h,()->{
        var fixture=fixture(h);var first=endpoint(h,CA,TrackShape.XO);endpoint(h,CB,TrackShape.ZO);var reciprocal=endpoint(h,CC,TrackShape.ZO);var level=h.getLevel();var missing=h.absolutePos(CC);var saved=first.saveWithFullMetadata(level.registryAccess());var launch=paid(h,fixture);var originals=originals(launch);level.removeBlockEntity(missing);
        try{h.assertTrue(level.getBlockState(missing).getValue(TrackBlock.HAS_BE)&&TrackBlockEntityAccess.existing(level,missing)==null,"Genuine native turn=true state has no available saved/live owner");h.assertFalse(launch.update(level),"Actual missing reciprocal BE defers paid geometry");h.assertFalse(SchematicPlacement.placeBlock(level,Blocks.STONE.defaultBlockState(),missing,new ItemStack(Items.STONE),null),"Unknown native owner also refuses a generic schematic replacement");h.assertTrue(TrackBlockEntityAccess.existing(level,missing)==null&&first.saveWithFullMetadata(level.registryAccess()).equals(saved),"CHECK-only source reads never manufacture an empty endpoint or mutate the original first owner");sameOriginals(h,originals,launch);}finally{reciprocal.clearRemoved();level.setBlockEntity(reciprocal);}
        var restored=LaunchedItem.fromNBT(launch.serializeNBT(level.registryAccess()),level.registryAccess(),level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK));h.assertTrue(restored.update(level)&&restored.paidOriginals().isEmpty()&&TrackBlockEntityAccess.existing(level,missing)==reciprocal,"Only the exact original native reciprocal owner permits saved-job retry");h.succeed();
    
        });
    }
    private static final class RefusingUpdate extends TrackBlockEntity {
        boolean armed;RefusingUpdate(BlockPos pos,BlockState state){super(pos,state);}
        @Override public void notifyUpdate(){if(armed){armed=false;throw new IllegalStateException("Actual second endpoint notification callback refused");}super.notifyUpdate();}
    }
    @GameTest(structure=STRUCTURE) public void actualSecondEndpointCallbackRollsBackMapsAndRealCannonDestructionReturnsOnlyOriginalReserve(GameTestHelper h)throws Exception{
        whenExportReady(h,()->{
        var fixture=fixture(h);endpoint(h,CA,TrackShape.XO);endpoint(h,CB,TrackShape.ZO);endpoint(h,CC,TrackShape.ZO);connect(h,fixture.player,CA,CB,-90,false);var level=h.getLevel();var at=h.absolutePos(CC);var failed=new RefusingUpdate(at,level.getBlockState(at));level.setBlockEntity(failed);var first=h.getBlockEntity(CA,TrackBlockEntity.class);var second=h.getBlockEntity(CB,TrackBlockEntity.class);CompoundTag a=first.saveWithFullMetadata(level.registryAccess()),b=second.saveWithFullMetadata(level.registryAccess()),c=failed.saveWithFullMetadata(level.registryAccess());var launch=paid(h,fixture);var originals=originals(launch);var cannonPos=h.absolutePos(COPY.above(2));level.setBlockAndUpdate(cannonPos,AllBlocks.SCHEMATICANNON.defaultBlockState());var cannon=(SchematicannonBlockEntity)level.getBlockEntity(cannonPos);cannon.flyingBlocks.add(launch);failed.armed=true;h.assertFalse(launch.update(level),"Actual notification callback refuses the second newly connected native endpoint");h.assertTrue(first.saveWithFullMetadata(level.registryAccess()).equals(a)&&second.saveWithFullMetadata(level.registryAccess()).equals(b)&&failed.saveWithFullMetadata(level.registryAccess()).equals(c),"All original native connection maps/save metadata are restored after the actual callback failure");sameOriginals(h,originals,launch);h.assertTrue(level.destroyBlock(cannonPos,false),"Actual cannon owner is really destroyed through native world lifecycle");var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(cannonPos).inflate(5));for(var original:originals){int count=drops.stream().map(net.minecraft.world.entity.item.ItemEntity::getItem).filter(stack->ItemStack.isSameItemSameComponents(stack,original)).mapToInt(ItemStack::getCount).sum();h.assertValueEqual(count,originals.stream().filter(stack->ItemStack.isSameItemSameComponents(stack,original)).mapToInt(ItemStack::getCount).sum(),"Literal admitted refund entities retain the full original reserved components/count exactly once");}h.assertTrue(launch.paidOriginals().isEmpty()&&!launch.update(level),"Retired canonical launch cannot spend or refund a second time");h.succeed();
    
        });
    }
}
