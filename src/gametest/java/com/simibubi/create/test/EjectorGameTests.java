package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.depot.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Genuine source charge, stock, flight, native processor/owner and player API tests. Runtime is UNRUN. */
public final class EjectorGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private static final BlockPos P=new BlockPos(5,5,5),TARGET=P.east(6),MOTOR=P.north(),ORIGIN=P.west(3),POWER=P.south();
    private static ItemStack stock(int amount){var stack=new ItemStack(Items.DIAMOND,amount);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Original weighted ejector diamonds"));return stack;}
    private static EjectorBlockEntity fixture(GameTestHelper h,boolean drive){
        h.setBlock(P,AllBlocks.WEIGHTED_EJECTOR.defaultBlockState().setValue(EjectorBlock.HORIZONTAL_FACING,Direction.EAST));
        h.setBlock(TARGET,AllBlocks.DEPOT);h.setBlock(ORIGIN,Blocks.CHEST);h.setBlock(POWER,Blocks.REDSTONE_BLOCK);
        var ejector=h.getBlockEntity(P,EjectorBlockEntity.class);ejector.setTarget(6,0);
        if(drive){h.setBlock(MOTOR,AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.SOUTH));h.getBlockEntity(MOTOR,KineticBlockEntity.class).setGeneratedSpeed(256);}
        return ejector;
    }
    private static Storage<ItemVariant> storage(GameTestHelper h,BlockPos pos){var storage=ItemStorage.SIDED.find(h.getLevel(),h.absolutePos(pos),Direction.UP);h.assertTrue(storage!=null,"Actual registered world storage exists at "+pos);return storage;}
    private static long count(Storage<ItemVariant> storage,ItemStack stack){long total=0;for(var view:storage)if(view.getResource().equals(ItemVariant.of(stack)))total+=view.getAmount();return total;}
    private static void seed(GameTestHelper h,ItemStack stack){var chest=h.getBlockEntity(ORIGIN,ChestBlockEntity.class);h.assertTrue(chest.isEmpty(),"Finite initial stock is issued once");int remaining=stack.getCount();for(int slot=0;remaining>0;slot++){int amount=Math.min(stack.getMaxStackSize(),remaining);chest.setItem(slot,stack.copyWithCount(amount));remaining-=amount;}}
    private static void move(GameTestHelper h,BlockPos from,BlockPos to,ItemStack stack,int amount){
        var source=storage(h,from);var target=storage(h,to);var variant=ItemVariant.of(stack);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(target.insert(variant,amount,tx),(long)amount,"Real destination accepts actual original stock");h.assertValueEqual(source.extract(variant,amount,tx),(long)amount,"The same original source is atomically debited");tx.commit();}
    }
    private static int flying(EjectorBlockEntity ejector,ItemStack stack){return ejector.getLaunchedItems().stream().filter(flight->ItemStack.isSameItemSameComponents(flight.getValue(),stack)).mapToInt(flight->flight.getValue().getCount()).sum();}
    private static int incoming(EjectorBlockEntity ejector,ItemStack stack){return ejector.depotBehaviour.getIncomingItems().stream().filter(item->ItemStack.isSameItemSameComponents(item.stack,stack)).mapToInt(item->item.stack.getCount()).sum();}
    private static int world(GameTestHelper h,ItemStack stack){return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(P)).inflate(20),entity->ItemStack.isSameItemSameComponents(entity.getItem(),stack)).stream().mapToInt(entity->entity.getItem().getCount()).sum();}
    private static void charged(GameTestHelper h,EjectorBlockEntity ejector){h.assertValueEqual(ejector.getState(),EjectorBlockEntity.State.CHARGED,"Real kinetic source has naturally charged the lid");}

    @GameTest(structure=STRUCTURE,maxTicks=60) public void sourceCannotChargeOrAcceptAutomationWithoutActualRotation(GameTestHelper h){
        var ejector=fixture(h,false);seed(h,stock(7));
        h.runAfterDelay(20,()->{h.assertValueEqual(ejector.getState(),EjectorBlockEntity.State.RETRACTING,"Zero real RPM retains retracting source state");h.assertValueEqual(ejector.getLidProgress(1),1f,"Unpowered spring remains unwound");try(var tx=Transaction.openOuter()){h.assertValueEqual(storage(h,P).insert(ItemVariant.of(stock(1)),7,tx),0L,"Actual item capability rejects stock before CHARGED");}h.assertValueEqual(count(storage(h,ORIGIN),stock(1)),7L,"Original finite chest stock remains untouched");h.succeed();});
    }
    @GameTest(structure=STRUCTURE,maxTicks=80) public void actualKineticNetworkChargesAtSourceDistanceScaledSpeed(GameTestHelper h){
        var ejector=fixture(h,true);h.startSequence().thenWaitUntil(()->charged(h,ejector)).thenExecute(()->{h.assertValueEqual(ejector.getSpeed(),256f,"Actual connected motor supplies source RPM");h.assertTrue(Math.abs(ejector.getWindUpSpeed()-1/6f)<.000001,"Original distance-six winding formula is retained");h.assertValueEqual(ejector.getLidProgress(1),0f,"Natural charged state reaches genuine zero lid");}).thenSucceed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=160) public void originalEightStockThresholdAndRedstoneLockGateGenuineAutomaticLaunch(GameTestHelper h){
        var ejector=fixture(h,true);ejector.setMinimumStackSize(8);seed(h,stock(8));
        h.startSequence().thenWaitUntil(()->charged(h,ejector)).thenExecute(()->move(h,ORIGIN,P,stock(1),7)).thenExecuteAfter(24,()->{
            h.assertValueEqual(count(storage(h,P),stock(1)),7L,"Seven original diamonds remain below the source threshold");h.assertValueEqual(count(storage(h,ORIGIN),stock(1)),1L,"Last original diamond still has its real chest owner");h.assertTrue(ejector.getLaunchedItems().isEmpty(),"No launch below original threshold");move(h,ORIGIN,P,stock(1),1);
        }).thenExecuteAfter(15,()->{h.assertTrue(ejector.isPowered()&&ejector.getLaunchedItems().isEmpty(),"Real powered signal inhibits launch of the full eight");h.setBlock(POWER,Blocks.AIR);})
                .thenWaitUntil(()->{h.assertValueEqual(count(storage(h,TARGET),stock(1)),8L,"Actual source trajectory inserts all original eight into true target");h.assertValueEqual(count(storage(h,ORIGIN),stock(1))+count(storage(h,P),stock(1))+flying(ejector,stock(1))+world(h,stock(1)),0L,"No original source, flight or world duplicate remains");}).thenSucceed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=90) public void wildcardZeroAllows64ActualItemsButNeverA65thFromTheOriginalChest(GameTestHelper h){
        var ejector=fixture(h,true);seed(h,stock(65));h.startSequence().thenWaitUntil(()->charged(h,ejector)).thenExecute(()->{
            h.assertValueEqual(ejector.getMinimumStackSize(),0,"Source default setting is wildcard0");move(h,ORIGIN,P,stock(1),64);
            try(var tx=Transaction.openOuter()){h.assertValueEqual(storage(h,P).insert(ItemVariant.of(stock(1)),1,tx),0L,"Original wildcard is a64 capacity, never unlimited");}
            h.assertValueEqual(count(storage(h,P),stock(1)),64L,"Actual held owner has64");h.assertValueEqual(count(storage(h,ORIGIN),stock(1)),1L,"Original65th remains in actual chest");
        }).thenSucceed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=90) public void stillIncomingSourceStockParticipatesInCapacityAndNestedRollback(GameTestHelper h){
        var ejector=fixture(h,true);ejector.setMinimumStackSize(8);seed(h,stock(8));
        h.startSequence().thenWaitUntil(()->charged(h,ejector)).thenExecute(()->{
            move(h,ORIGIN,P,stock(1),5);var target=storage(h,P);var source=storage(h,ORIGIN);
            try(var outer=Transaction.openOuter()){try(var inner=outer.openNested()){h.assertValueEqual(target.insert(ItemVariant.of(stock(1)),3,inner),3L,"Source merging creates real tentative incoming ownership");h.assertValueEqual(source.extract(ItemVariant.of(stock(1)),3,inner),3L,"Actual chest is tentatively debited");inner.commit();}h.assertValueEqual(incoming(ejector,stock(1)),3,"Three actual incoming items exist inside the outer transaction");h.assertValueEqual(ejector.depotBehaviour.getRemainingSpace(),0,"Incoming consumes actual source capacity");}
            h.assertTrue(ItemStack.matches(ejector.depotBehaviour.getHeldItemStack(),stock(5)),"Outer abort restores actual held five");h.assertValueEqual(incoming(ejector,stock(1)),0,"Outer abort removes tentative incoming owner");h.assertValueEqual(count(source,stock(1)),3L,"Original last three remain in actual chest");
        }).thenSucceed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=130) public void realSourceActivationTransfersBothHeldAndStillIncomingOwnersIntoFlight(GameTestHelper h){
        var ejector=fixture(h,true);ejector.setMinimumStackSize(64);seed(h,stock(7));
        var receipt=TARGET.below();h.setBlock(receipt,Blocks.CHEST);h.setBlock(TARGET,AllBlocks.BRASS_FUNNEL.defaultBlockState().setValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.FACING,Direction.UP).setValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.EXTRACTING,false));
        h.assertTrue(h.getBlockEntity(receipt,ChestBlockEntity.class).isEmpty(),"Actual upward collecting funnel targets one empty native receipt chest for both original flight batches");
        h.startSequence().thenWaitUntil(()->charged(h,ejector)).thenExecute(()->{move(h,ORIGIN,P,stock(1),4);move(h,ORIGIN,P,stock(1),3);h.assertValueEqual(incoming(ejector,stock(1)),3,"Original second batch is still in its distinct incoming owner");ejector.activate();})
                .thenWaitUntil(()->{h.assertValueEqual(flying(ejector,stock(1)),7,"All original held and incoming stock genuinely moved into flight");h.assertTrue(ejector.depotBehaviour.getHeldItemStack().isEmpty()&&ejector.depotBehaviour.getIncomingItems().isEmpty(),"Both original depot owners were debited");})
                .thenWaitUntil(()->{h.assertValueEqual(count(storage(h,receipt),stock(1)),7L,"All original stock arrives exactly once despite threshold64 source activation");h.assertValueEqual(ejector.getMinimumStackSize(),64,"Original source activation threshold remains64");h.assertValueEqual(count(storage(h,ORIGIN),stock(1))+count(storage(h,P),stock(1))+incoming(ejector,stock(1))+flying(ejector,stock(1))+world(h,stock(1)),0L,"Every original source, incoming, flight and world owner is debited without a duplicate");}).thenSucceed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=160) public void occupiedRealTargetBlocksAutomaticLaunchUntilItsStockActuallyMovesToAChest(GameTestHelper h){
        var ejector=fixture(h,true);seed(h,stock(7));var target=h.getBlockEntity(TARGET,DepotBlockEntity.class);
        try(var tx=Transaction.openOuter()){target.inventory.automation.insert(ItemVariant.of(Items.STONE),64,tx);tx.commit();}
        h.startSequence().thenWaitUntil(()->charged(h,ejector)).thenExecute(()->{move(h,ORIGIN,P,stock(1),7);h.setBlock(POWER,Blocks.AIR);})
                .thenExecuteAfter(30,()->{h.assertTrue(ejector.getLaunchedItems().isEmpty()&&ejector.getState()==EjectorBlockEntity.State.CHARGED,"Actual occupied source target inhibits automatic firing");h.assertValueEqual(count(storage(h,P),stock(1)),7L,"Original seven remain held");move(h,TARGET,ORIGIN,new ItemStack(Items.STONE),64);})
                .thenWaitUntil(()->{h.assertValueEqual(count(storage(h,TARGET),stock(1)),7L,"Naturally resumed trajectory inserts original seven");h.assertValueEqual(count(storage(h,ORIGIN),new ItemStack(Items.STONE)),64L,"Original blocking stock remains in an actual receipt chest");}).thenSucceed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=130) public void realSolidObstacleRecordsEarlyCollisionAndRetainsEveryOriginalItem(GameTestHelper h){
        var ejector=fixture(h,true);seed(h,stock(7));
        h.startSequence().thenWaitUntil(()->charged(h,ejector)).thenExecute(()->{
            var obstacle=BlockPos.containing(ejector.getLaunchedItemLocation(5));h.getLevel().setBlockAndUpdate(obstacle,Blocks.STONE.defaultBlockState());
            move(h,ORIGIN,P,stock(1),7);h.setBlock(POWER,Blocks.AIR);
        }).thenWaitUntil(()->{h.assertTrue(ejector.getEarlyTarget()!=null&&ejector.getEarlyTargetTime()>2,"Real clip after source tick2 records the physical early target");h.assertValueEqual(world(h,stock(1))+count(storage(h,TARGET),stock(1)),7L,"All original material exists in real world/target owners after the early impact");h.assertValueEqual(flying(ejector,stock(1))+count(storage(h,P),stock(1)),0L,"Old flight/main owners cannot duplicate an early hit");}).thenSucceed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=140) public void actualSourceParcelRetainsItsSealedContentsAndAddressThroughBallisticInsertion(GameTestHelper h){
        var ejector=fixture(h,true);var parcel=PackageItem.containing(List.of(stock(7)));PackageItem.addAddress(parcel,"Actual ejector destination");seed(h,parcel);
        h.startSequence().thenWaitUntil(()->charged(h,ejector)).thenExecute(()->{move(h,ORIGIN,P,parcel,1);h.setBlock(POWER,Blocks.AIR);})
                .thenWaitUntil(()->{var received=h.getBlockEntity(TARGET,DepotBlockEntity.class).getHeldItem();h.assertTrue(ItemStack.matches(received,parcel),"The original actual parcel and every component reach the real target");h.assertValueEqual(count(storage(h,ORIGIN),parcel)+count(storage(h,P),parcel)+flying(ejector,parcel)+world(h,parcel),0L,"No duplicate parcel remains");}).thenSucceed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=130) public void genuineFlightSaveLoadRestoresTimeComponentsAndOneExclusiveNewOwner(GameTestHelper h){
        var original=fixture(h,true);seed(h,stock(7));var savedOwner=new AtomicReference<EjectorBlockEntity>();
        h.startSequence().thenWaitUntil(()->charged(h,original)).thenExecute(()->{move(h,ORIGIN,P,stock(1),7);h.setBlock(POWER,Blocks.AIR);})
                .thenWaitUntil(()->h.assertValueEqual(flying(original,stock(1)),7,"Actual source is in flight before saving"))
                .thenExecute(()->{
                    var tag=original.saveWithFullMetadata(h.getLevel().registryAccess());var before=original.getLaunchedItems();
                    var restored=(EjectorBlockEntity)BlockEntity.loadStatic(original.getBlockPos(),original.getBlockState(),tag,h.getLevel().registryAccess());
                    h.assertTrue(restored!=null&&restored!=original,"Native save creates a genuine fresh block entity");
                    h.assertValueEqual(restored.getLaunchedItems().getFirst().getFirst(),before.getFirst().getFirst(),"Actual elapsed flight time is persisted");h.assertValueEqual(flying(restored,stock(1)),7,"Exact original component-bearing flight stock loads once");
                    h.getLevel().removeBlockEntity(original.getBlockPos());h.getLevel().setBlockEntity(restored);savedOwner.set(restored);
                    h.assertTrue(original.isRemoved(),"Prior block entity actually left the native level");original.dropFlyingItems();h.assertValueEqual(world(h,stock(1)),0,"Retired original block entity cannot export a duplicate of its saved flight snapshot");
                }).thenWaitUntil(()->{h.assertValueEqual(count(storage(h,TARGET),stock(1)),7L,"Fresh real owner completes its original saved trajectory once");h.assertTrue(savedOwner.get().getLaunchedItems().isEmpty(),"Actual completed flight is debited from new owner");}).thenSucceed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=120) public void realBlockRemovalDropsOnlyTheCurrentlyOwnedFlightAndCannotRepeatIt(GameTestHelper h){
        var ejector=fixture(h,true);seed(h,stock(7));
        h.startSequence().thenWaitUntil(()->charged(h,ejector)).thenExecute(()->{move(h,ORIGIN,P,stock(1),7);h.setBlock(POWER,Blocks.AIR);})
                .thenWaitUntil(()->h.assertValueEqual(flying(ejector,stock(1)),7,"Actual native source entered flight"))
                .thenExecute(()->{h.setBlock(P,Blocks.AIR);h.assertValueEqual(world(h,stock(1)),7,"True destruction exports exactly original flying components");h.assertTrue(ejector.getLaunchedItems().isEmpty(),"Destroyed source owns no second copy");ejector.dropFlyingItems();h.assertValueEqual(world(h,stock(1)),7,"Repeated public drop cannot export another original batch");}).thenSucceed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=110) public void nativeDynamicFrictionIsExactlyOneOnlyDuringActualLaunching(GameTestHelper h){
        var ejector=fixture(h,true);seed(h,stock(1));
        h.startSequence().thenWaitUntil(()->charged(h,ejector)).thenExecute(()->{h.assertValueEqual(EjectorFriction.value(h.getLevel(),h.absolutePos(P),.6f),.6f,"Charged state retains normal native friction");move(h,ORIGIN,P,stock(1),1);h.setBlock(POWER,Blocks.AIR);})
                .thenWaitUntil(()->h.assertValueEqual(ejector.getState(),EjectorBlockEntity.State.LAUNCHING,"Actual native lid is launching"))
                .thenExecute(()->h.assertValueEqual(EjectorFriction.value(h.getLevel(),h.absolutePos(P),.6f),1f,"Source launching state uses exact friction1")).thenSucceed();
    }
    @GameTest(structure=STRUCTURE) public void exactSourceDistanceSetterDiffersFromItsConfiguredLoadClamp(GameTestHelper h){
        var ejector=fixture(h,false);int maximum=EjectorConfig.server().maxDistance();ejector.setTarget(0,maximum+11);
        h.assertValueEqual(ejector.getLauncher().getHorizontalDistance(),1,"Runtime source setter enforces horizontal minimum1");h.assertValueEqual(ejector.getLauncher().getVerticalDistance(),maximum+11,"Runtime source setter does not silently clamp vertical distance");
        var restored=(EjectorBlockEntity)BlockEntity.loadStatic(ejector.getBlockPos(),ejector.getBlockState(),ejector.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(restored!=null&&restored.getLauncher().getVerticalDistance()==maximum,"Source NBT read applies actual configured clamp");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void sourceAxisShapeAndStressUseTheActualFourHorizontalBlockStates(GameTestHelper h){
        for(var direction:Direction.Plane.HORIZONTAL){var state=AllBlocks.WEIGHTED_EJECTOR.defaultBlockState().setValue(EjectorBlock.HORIZONTAL_FACING,direction);var expected=direction.getClockWise().getAxis();h.assertValueEqual(AllBlocks.WEIGHTED_EJECTOR.getRotationAxis(state),expected,"Actual facing defines the source transverse shaft axis");for(var face:Direction.values())h.assertValueEqual(AllBlocks.WEIGHTED_EJECTOR.hasShaftTowards(h.getLevel(),h.absolutePos(P),state,face),face.getAxis()==expected,"Only physical source axle faces connect");h.assertValueEqual(state.getShape(h.getLevel(),h.absolutePos(P)).max(Direction.Axis.Y),13/16d,"Source13px activation surface is retained");}
        h.assertValueEqual(BlockStressValues.getImpact(AllBlocks.WEIGHTED_EJECTOR),2d,"Actual registered kinetic stress impact is source2");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void forgedTriggerAwardAndGliderPacketsCannotInventAnActualLaunch(GameTestHelper h){
        var ejector=fixture(h,false);try(var session=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(0,0,2))){var player=session.player();
            h.assertFalse(new EjectorTriggerPacket(h.absolutePos(P)).apply(player),"Uncharged non-contact source refuses forged trigger");h.assertFalse(new EjectorAwardPacket(h.absolutePos(P),17).apply(player),"Absent recent launch cannot grant advancement");player.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.ELYTRA));h.assertFalse(new EjectorElytraPacket(h.absolutePos(P),17).apply(player),"Actually equipped glider still needs a genuine recent launch receipt");h.assertFalse(player.isFallFlying(),"Rejected packet never fabricates a gliding flag");}
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void realEquippedGliderUsesNativeEligibilityAndNeverFabricatesFlightWithoutEquipment(GameTestHelper h){
        var ejector=fixture(h,false);try(var session=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(0,3,2))){var player=session.player();player.setOnGround(false);
            h.assertFalse(ejector.deployElytra(player),"No actual equipped glider means no native flight");player.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.ELYTRA));h.assertTrue(LivingEntity.canGlideUsing(player.getItemBySlot(EquipmentSlot.CHEST),EquipmentSlot.CHEST),"Native GLIDER/equippable components recognize actual original Elytra");h.assertTrue(ejector.deployElytra(player)&&player.isFallFlying(),"Actual source deployment starts native fall flying");player.stopFallFlying();player.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);h.assertFalse(ejector.deployElytra(player)||player.isFallFlying(),"Removing real equipment removes actual flight eligibility");}
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void realPaidSurvivalPlacementDebitsOneEjectorAndOnlyItsCapturedNonceConfiguresOnce(GameTestHelper h){
        h.setBlock(P.below(),Blocks.STONE);
        try(var session=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(0,0,2))){var player=session.player();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.WEIGHTED_EJECTOR,2));
            var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P.below())).add(0,.499,0),Direction.UP,h.absolutePos(P.below()),false);
            h.assertTrue(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Actual native block item places in literal survival");h.assertValueEqual(player.getMainHandItem().getCount(),1,"One original physical ejector item is paid");
            EjectorPlacementPacket.ClientBoundRequest request=null;Object message;
            while((message=session.channel().readOutbound())!=null)if(message instanceof ClientboundCustomPayloadPacket packet&&packet.payload() instanceof EjectorPlacementPacket.ClientBoundRequest actual)request=actual;
            h.assertTrue(request!=null&&request.nonce()!=0&&request.pos().equals(h.absolutePos(P)),"Actual paid placement emitted its real server request");
            var packet=new EjectorPlacementPacket(6,2,request.pos(),Direction.EAST,request.nonce());h.assertTrue(packet.apply(player),"Actual captured source placement token applies target");h.assertFalse(packet.apply(player),"Original token cannot be replayed");var owner=h.getBlockEntity(P,EjectorBlockEntity.class);h.assertValueEqual(owner.getTargetPosition(),h.absolutePos(P).east(6).above(2),"Real paid owner stores actual original h/v/facing");
        }h.succeed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=120) public void actualOppositeOverheadFunnelReceivesHeldAndIncomingInsteadOfBallisticCopies(GameTestHelper h){
        var ejector=fixture(h,true);seed(h,stock(7));var funnel=P.above();var receipt=funnel.east();h.setBlock(receipt,Blocks.CHEST);
        h.setBlock(funnel,AllBlocks.BRASS_BELT_FUNNEL.defaultBlockState().setValue(com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.HORIZONTAL_FACING,Direction.WEST).setValue(com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.SHAPE,com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.Shape.PULLING));
        h.startSequence().thenWaitUntil(()->charged(h,ejector)).thenExecute(()->{move(h,ORIGIN,P,stock(1),4);move(h,ORIGIN,P,stock(1),3);h.assertValueEqual(incoming(ejector,stock(1)),3,"Original second batch is genuinely incoming before source activation");ejector.activate();})
                .thenWaitUntil(()->{h.assertValueEqual(count(storage(h,receipt),stock(1)),7L,"Actual opposite funnel exports all original held and incoming stock into its real attached chest");h.assertValueEqual(flying(ejector,stock(1))+incoming(ejector,stock(1))+count(storage(h,P),stock(1))+count(storage(h,TARGET),stock(1))+world(h,stock(1)),0L,"Alternate source route creates no ballistic, depot or world duplicate");}).thenSucceed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=180) public void partialRealFunnelTransferRetainsOnlyItsActualUnpaidRemainderAndConservesAfterFreeingOutput(GameTestHelper h){
        var ejector=fixture(h,true);seed(h,stock(7));var funnel=P.above();var receipt=funnel.east();h.setBlock(receipt,Blocks.CHEST);var chest=h.getBlockEntity(receipt,ChestBlockEntity.class);
        chest.setItem(0,stock(62));for(int index=1;index<chest.getContainerSize();index++)chest.setItem(index,new ItemStack(Items.STONE,64));
        h.setBlock(funnel,AllBlocks.BRASS_BELT_FUNNEL.defaultBlockState().setValue(com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.HORIZONTAL_FACING,Direction.WEST).setValue(com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.SHAPE,com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.Shape.PULLING));
        h.startSequence().thenWaitUntil(()->charged(h,ejector)).thenExecute(()->{move(h,ORIGIN,P,stock(1),4);move(h,ORIGIN,P,stock(1),3);ejector.activate();})
                .thenWaitUntil(()->{h.assertValueEqual(count(storage(h,receipt),stock(1)),64L,"Real attached chest accepts only its genuine two free spaces");h.assertValueEqual(count(storage(h,P),stock(1))+incoming(ejector,stock(1)),5L,"Only the five actually unpaid items remain owned at source");h.assertValueEqual(count(storage(h,receipt),new ItemStack(Items.STONE)),26L*64,"Every original full stone slot remains in the actual chest");})
                .thenExecute(()->{move(h,receipt,ORIGIN,stock(1),64);h.setBlock(POWER,Blocks.AIR);})
                .thenWaitUntil(()->{h.assertValueEqual(count(storage(h,receipt),stock(1)),5L,"Naturally recharged real source sends original unpaid five after output is actually freed");h.assertValueEqual(count(storage(h,ORIGIN),stock(1)),64L,"All original blocking stock stays in a true receipt owner");h.assertValueEqual(count(storage(h,P),stock(1))+incoming(ejector,stock(1))+flying(ejector,stock(1)),0L,"No second source owner remains");h.assertValueEqual(count(storage(h,receipt),stock(1))+count(storage(h,ORIGIN),stock(1)),69L,"All original62+7 exact component-bearing diamonds are conserved");}).thenSucceed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=150) public void actualMechanicalPressUsesTheGenuineEjectorProcessingBehaviourAndSourceHeldOutputRouting(GameTestHelper h){
        var ejector=fixture(h,true);ejector.setMinimumStackSize(64);seed(h,new ItemStack(Items.IRON_INGOT,2));var press=P.above(2);
        h.setBlock(press,AllBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(com.simibubi.create.content.kinetics.press.MechanicalPressBlock.FACING,Direction.EAST));h.setBlock(press.west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));h.getBlockEntity(press.west(),KineticBlockEntity.class).setGeneratedSpeed(256);
        h.startSequence().thenWaitUntil(()->charged(h,ejector)).thenExecute(()->move(h,ORIGIN,P,new ItemStack(Items.IRON_INGOT),2))
                .thenWaitUntil(()->{h.assertValueEqual(count(storage(h,P),new ItemStack(com.simibubi.create.AllItems.IRON_SHEET)),2L,"Actual press cycles process both original ingots on real Ejector");h.assertTrue(ejector.depotBehaviour.getHeldItemStack().is(com.simibubi.create.AllItems.IRON_SHEET),"Last actual converted result becomes source held stack");h.assertValueEqual(count(storage(h,P),new ItemStack(Items.IRON_INGOT)),0L,"Both original inputs are actually consumed");}).thenSucceed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=100) public void actualSpoutPaysOriginalFluidForRealEjectorHeldStock(GameTestHelper h){
        var ejector=fixture(h,true);seed(h,new ItemStack(Items.BUCKET));var spout=P.above(2);h.setBlock(spout,AllBlocks.SPOUT);var source=h.getBlockEntity(spout,com.simibubi.create.content.fluids.spout.SpoutBlockEntity.class);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(source.tank.insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.world.level.material.Fluids.WATER),net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET,tx),net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET,"Actual finite source water enters native spout tank");tx.commit();}
        h.startSequence().thenWaitUntil(()->charged(h,ejector)).thenExecute(()->move(h,ORIGIN,P,new ItemStack(Items.BUCKET),1))
                .thenWaitUntil(()->{h.assertTrue(ejector.depotBehaviour.getHeldItemStack().is(Items.WATER_BUCKET),"Actual spout recognizes real Ejector processing surface");h.assertValueEqual(ejector.depotBehaviour.getHeldItemStack().getCount(),1,"Exactly original one bucket is filled");h.assertValueEqual(source.tank.getAmount(),0L,"Original actual bucket of water is paid once");h.assertValueEqual(count(storage(h,P),new ItemStack(Items.BUCKET)),0L,"No original empty bucket duplicate remains");}).thenSucceed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=120) public void actualDeployerPolishesTheOriginalEjectorStackAndPaysItsRealTool(GameTestHelper h){
        var ejector=fixture(h,true);seed(h,new ItemStack(com.simibubi.create.AllItems.ROSE_QUARTZ));var hand=P.above(2);
        h.setBlock(hand,AllBlocks.DEPLOYER.defaultBlockState().setValue(com.simibubi.create.content.kinetics.deployer.DeployerBlock.FACING,Direction.DOWN).setValue(com.simibubi.create.content.kinetics.deployer.DeployerBlock.AXIS_ALONG_FIRST_COORDINATE,true));h.setBlock(hand.west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));h.getBlockEntity(hand.west(),KineticBlockEntity.class).setGeneratedSpeed(256);
        var deployer=h.getBlockEntity(hand,com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity.class);deployer.setHeldItem(new ItemStack(com.simibubi.create.AllItems.SAND_PAPER));
        h.startSequence().thenWaitUntil(()->charged(h,ejector)).thenExecute(()->move(h,ORIGIN,P,new ItemStack(com.simibubi.create.AllItems.ROSE_QUARTZ),1))
                .thenWaitUntil(()->{h.assertTrue(ejector.depotBehaviour.getHeldItemStack().is(com.simibubi.create.AllItems.POLISHED_ROSE_QUARTZ),"Actual deployer recognizes genuine source Ejector callbacks and inventory");h.assertValueEqual(deployer.getHeldItem().getDamageValue(),1,"Original actual sandpaper pays one native polishing action");h.assertValueEqual(count(storage(h,P),new ItemStack(com.simibubi.create.AllItems.ROSE_QUARTZ)),0L,"Original rose quartz is actually debited");}).thenSucceed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=280) public void actualFanProcessesGenuineEjectorTransportMetadataWithoutADepotTypeShortcut(GameTestHelper h){
        var ejector=fixture(h,true);seed(h,new ItemStack(com.simibubi.create.AllItems.WHEAT_FLOUR));var fan=P.west(3).above();
        h.setBlock(fan,AllBlocks.ENCASED_FAN.defaultBlockState().setValue(com.simibubi.create.content.kinetics.fan.EncasedFanBlock.FACING,Direction.EAST));h.setBlock(fan.west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));h.getBlockEntity(fan.west(),KineticBlockEntity.class).setGeneratedSpeed(128);h.setBlock(fan.east(),Blocks.WATER);
        h.startSequence().thenWaitUntil(()->charged(h,ejector)).thenExecute(()->move(h,ORIGIN,P,new ItemStack(com.simibubi.create.AllItems.WHEAT_FLOUR),1))
                .thenWaitUntil(()->{h.assertTrue(ejector.depotBehaviour.getHeldItemStack().is(com.simibubi.create.AllItems.DOUGH),"Actual fan flow uses genuine Ejector transported-item processing");h.assertValueEqual(ejector.depotBehaviour.getHeldItemStack().getCount(),1,"Exactly original one flour becomes source held dough");h.assertValueEqual(count(storage(h,P),new ItemStack(com.simibubi.create.AllItems.WHEAT_FLOUR)),0L,"Original flour is actually consumed");}).thenSucceed();
    }

    @GameTest(structure=STRUCTURE,maxTicks=100) public void originalSourceIncomingNbtShapePreservesRealStockAndAdvertisedNativeCapacity(GameTestHelper h){
        var original=fixture(h,true);original.setMinimumStackSize(8);seed(h,stock(7));
        h.startSequence().thenWaitUntil(()->charged(h,original)).thenExecute(()->{
            move(h,ORIGIN,P,stock(1),4);move(h,ORIGIN,P,stock(1),3);
            var saved=original.saveWithFullMetadata(h.getLevel().registryAccess());saved.remove("Inventory");saved.remove("MinimumStackSize");saved.putInt("ScrollValue",8);
            var held=saved.getCompoundOrEmpty("HeldItem");held.remove("Id");held.putInt("InDirection",1);
            for(var value:saved.getListOrEmpty("Incoming")){var transported=(net.minecraft.nbt.CompoundTag)value;transported.remove("Id");transported.putInt("InDirection",1);}
            var restored=(EjectorBlockEntity)BlockEntity.loadStatic(original.getBlockPos(),original.getBlockState(),saved,h.getLevel().registryAccess());
            h.assertTrue(restored!=null&&restored.getMinimumStackSize()==8,"Original flat ScrollValue setting migrates through genuine native input");h.assertValueEqual(restored.depotBehaviour.getHeldItemStack().getCount()+incoming(restored,stock(1)),7,"All original actual held4/incoming3 stock loads from source shape without Id/native Inventory");
            h.getLevel().removeBlockEntity(original.getBlockPos());h.getLevel().setBlockEntity(restored);h.assertValueEqual(storage(h,P).iterator().next().getCapacity(),8L,"Actual source setting governs advertised native held-slot capacity");
            try(var tx=Transaction.openOuter()){h.assertValueEqual(original.depotBehaviour.itemHandler.extract(ItemVariant.of(stock(1)),4,tx),0L,"Retired raw handler also refuses to duplicate saved stock");}
        }).thenSucceed();
    }

}
