package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.KineticGraph;
import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.deployer.*;
import com.simibubi.create.content.kinetics.waterwheel.*;
import com.simibubi.create.content.logistics.box.*;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.foundation.advancement.PrerequisiteAdvancements;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Actual native interactions and literal survival owners; authored, runtime UNRUN. */
public final class GameplayAdvancementGameTests {
    private static final BlockPos P=new BlockPos(4,3,4);
    static boolean done(ServerPlayer player,String id){var entry=player.level().getServer().getAdvancements().get(Create.asResource(id));if(entry==null)throw new AssertionError("Missing source native advancement "+id);return player.getAdvancements().getOrStartProgress(entry).isDone();}
    static NativeSurvivalTestPlayer join(GameTestHelper h){return NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P.south(3))));}
    static void placed(GameTestHelper h,BlockPos pos,ServerPlayer owner){var state=h.getBlockState(pos);state.getBlock().setPlacedBy(h.getLevel(),h.absolutePos(pos),state,owner,new ItemStack(state.getBlock()));}
    static void drive(com.simibubi.create.content.kinetics.base.KineticBlockEntity be,float speed){be.setNetworkState(speed,speed,false,new KineticGraph.Stress(1024,16,false),Map.of(Direction.WEST,speed));}
    private static float exhaustion(ServerPlayer player){var out=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,player.registryAccess());player.getFoodData().addAdditionalSaveData(out);return out.buildResult().getFloatOr("foodExhaustionLevel",0);}
    private static PrerequisiteAdvancements.Owner owner(BlockEntity be){
        if(be instanceof MechanicalCrafterBlockEntity device)return device.sourceAdvancements;
        if(be instanceof WaterWheelBlockEntity device)return device.sourceAdvancements;
        if(be instanceof PumpBlockEntity device)return device.sourceAdvancements;
        if(be instanceof FluidPipeBlockEntity device)return device.sourceAdvancements;
        if(be instanceof com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity device)return device.sourceAdvancements;
        if(be instanceof ItemDrainBlockEntity device)return device.sourceAdvancements;
        if(be instanceof com.simibubi.create.content.fluids.pipes.valve.FluidValveBlockEntity device)return device.sourceAdvancements;
        throw new AssertionError("Actual source owner device required");
    }
    @GameTest public void nineteenOriginalCriteriaLoadAndChocolateRequiresActualNativeInventoryChange(GameTestHelper h){
        var ids=List.of("anvil_plough","belt_funnel_kiss","chained_drain","chocolate_bucket","crafter_lazy_000","cross_streams","fist_bump","glass_pipe","hand_crank_000","honey_drain","hose_pulley","lava_wheel_00000","long_travel","mechanical_pump_0","package_chute_throw","pipe_organ","self_deploying","train_roadkill","water_supply");
        var manager=h.getLevel().getServer().getAdvancements();for(var first:ids){String id=first;var seen=new HashSet<String>();while(id!=null&&seen.add(id)){var entry=manager.get(Create.asResource(id));h.assertTrue(entry!=null,"Actual source ancestor loaded: "+id);id=entry.value().parent().filter(key->key.getNamespace().equals("create")).map(key->key.getPath()).orElse(null);}}
        try(var session=join(h)){var player=session.player();h.assertFalse(done(player,"chocolate_bucket"),"Literal fresh survival inventory has no chocolate bucket");player.getInventory().setItem(0,new ItemStack(AllFluids.CHOCOLATE_FAMILY.bucket));player.inventoryMenu.broadcastChanges();h.assertTrue(done(player,"chocolate_bucket")&&player.getInventory().getItem(0).is(AllFluids.CHOCOLATE_FAMILY.bucket),"Original minecraft inventory_changed collects one actual bucket without spending it");}h.succeed();
    }
    @GameTest public void actualDeviceOwnersSurvivePreLevelDecodeAndOwnerlessSavesNeverAdoptObserver(GameTestHelper h){
        try(var session=join(h)){
            for(var block:List.of(AllBlocks.MECHANICAL_CRAFTER,AllBlocks.WATER_WHEEL,AllBlocks.LARGE_WATER_WHEEL,AllBlocks.MECHANICAL_PUMP,AllBlocks.FLUID_PIPE,AllBlocks.GLASS_FLUID_PIPE,AllBlocks.SMART_FLUID_PIPE,AllBlocks.FLUID_VALVE,AllBlocks.ITEM_DRAIN)){
                h.setBlock(P,block);placed(h,P,session.player());var device=h.getLevel().getBlockEntity(h.absolutePos(P));h.assertValueEqual(owner(device).uuid(),session.player().getUUID(),"Native source placed owner "+block);
                var saved=device.saveWithFullMetadata(h.getLevel().registryAccess());var decoded=BlockEntity.loadStatic(device.getBlockPos(),device.getBlockState(),saved,h.getLevel().registryAccess());h.assertTrue(decoded!=null&&decoded.getLevel()==null&&session.player().getUUID().equals(owner(decoded).uuid()),"Native pre-level Owner decode preserves the exact actual UUID");saved.remove("Owner");decoded=BlockEntity.loadStatic(device.getBlockPos(),device.getBlockState(),saved,h.getLevel().registryAccess());h.assertTrue(owner(decoded).uuid()==null,"An ownerless original device cannot acquire the actual nearby observer");
            }
        }h.succeed();
    }
    @GameTest public void actualHungryCrankInteractionAwardsInteractorAndExtendoExcludesOnlyExhaustion(GameTestHelper h){
        h.setBlock(P.below(),Blocks.STONE);h.setBlock(P,AllBlocks.HAND_CRANK.defaultBlockState().setValue(HandCrankBlock.FACING,Direction.UP));
        try(var first=join(h);var second=join(h)){
            var player=first.player();player.getFoodData().setFoodLevel(0);var stack=new ItemStack(Items.APPLE);player.setItemInHand(InteractionHand.MAIN_HAND,stack);var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)),Direction.UP,h.absolutePos(P),false);
            h.assertTrue(h.getBlockState(P).useItemOn(stack,h.getLevel(),player,InteractionHand.MAIN_HAND,hit).consumesAction()&&done(player,"hand_crank_000")&&!done(second.player(),"hand_crank_000"),"Real source item interaction turns the hungry crank and awards only that real player");
            var grip=new ItemStack(AllItems.EXTENDO_GRIP);second.player().getFoodData().setFoodLevel(0);second.player().setItemInHand(InteractionHand.MAIN_HAND,grip);float exhaustion=exhaustion(second.player());h.getBlockState(P).useItemOn(grip,h.getLevel(),second.player(),InteractionHand.MAIN_HAND,hit);h.assertTrue(done(second.player(),"hand_crank_000")&&exhaustion(second.player())==exhaustion&&second.player().getMainHandItem()==grip,"Source Extendo use skips native exhaustion while retaining the same actual zero-food award predicate");
        }h.succeed();
    }
    @GameTest public void actualCrafterSpeedChangesRespectZeroAndStrictFiveThresholdForSavedOwner(GameTestHelper h){
        h.setBlock(P,AllBlocks.MECHANICAL_CRAFTER);
        try(var session=join(h);var observer=join(h)){
            placed(h,P,session.player());var crafter=h.getBlockEntity(P,MechanicalCrafterBlockEntity.class);drive(crafter,0);h.assertFalse(done(session.player(),"crafter_lazy_000"),"Actual zero rotation cannot issue the source lazy criterion");drive(crafter,5);h.assertTrue(done(session.player(),"mechanical_crafter")&&!done(session.player(),"crafter_lazy_000")&&session.player().getUUID().equals(crafter.sourceAdvancements.uuid()),"Exact5 earns ordinary source rotation and retains Owner for strict lower threshold");drive(crafter,-4);h.assertTrue(done(session.player(),"crafter_lazy_000")&&!done(observer.player(),"crafter_lazy_000")&&crafter.sourceAdvancements.uuid()==null,"Actual negative4 rotation awards saved owner once and clears completed Owner");
        }h.succeed();
    }
    @GameTest public void actualPumpRotationAwardsOnlyItsGenuinePlacedOwner(GameTestHelper h){
        h.setBlock(P,AllBlocks.MECHANICAL_PUMP);
        try(var session=join(h);var observer=join(h)){placed(h,P,session.player());var pump=h.getBlockEntity(P,PumpBlockEntity.class);drive(pump,0);h.assertFalse(done(session.player(),"mechanical_pump_0"),"Actual stopped source pump cannot award");drive(pump,32);h.assertTrue(done(session.player(),"mechanical_pump_0")&&!done(observer.player(),"mechanical_pump_0")&&session.player().getUUID().equals(pump.sourceAdvancements.uuid()),"Actual source speed change earns pump while retaining owner for unmet shared pipe criteria");}h.succeed();
    }
    @GameTest public void lavaAtOneActualWheelOffsetQualifiesOnlyAlongsideRealNonzeroTotalFlow(GameTestHelper h){
        h.setBlock(P,AllBlocks.WATER_WHEEL.defaultBlockState().setValue(WaterWheelBlock.FACING,Direction.SOUTH));for(var side:List.of(Direction.EAST,Direction.WEST,Direction.UP,Direction.DOWN)){var cell=P.relative(side);for(var surround:Direction.values())if(!cell.relative(surround).equals(P))h.setBlock(cell.relative(surround),Blocks.STONE);h.setBlock(cell,Blocks.AIR);}h.setBlock(P.west(),Blocks.LAVA);
        try(var session=join(h)){placed(h,P,session.player());var wheel=h.getBlockEntity(P,WaterWheelBlockEntity.class);WaterWheelBlockEntity.tick(h.getLevel(),h.absolutePos(P),h.getBlockState(P),wheel);h.assertTrue(wheel.getDirectionalGeneratedSpeed()==0&&!done(session.player(),"lava_wheel_00000"),"Actual still lava with zero wheel-plane torque is insufficient");h.setBlock(P.east(),Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,8));WaterWheelBlockEntity.tick(h.getLevel(),h.absolutePos(P),h.getBlockState(P),wheel);h.assertTrue(wheel.getDirectionalGeneratedSpeed()!=0&&done(session.player(),"lava_wheel_00000"),"Real falling water supplies torque while a different original sampled offset supplies lava");}h.succeed();
    }
    @GameTest public void nativeWrenchExtendsActualOpposedFunnelsAndAwardsOnlyInteractor(GameTestHelper h){
        var belt=AllBlocks.BELT.defaultBlockState().setValue(com.simibubi.create.content.kinetics.belt.BeltBlock.HORIZONTAL_FACING,Direction.EAST).setValue(com.simibubi.create.content.kinetics.belt.BeltBlock.SLOPE,com.simibubi.create.content.kinetics.belt.BeltSlope.HORIZONTAL);h.setBlock(P.below(),belt.setValue(com.simibubi.create.content.kinetics.belt.BeltBlock.PART,com.simibubi.create.content.kinetics.belt.BeltPart.START));h.setBlock(P.east().below(),belt.setValue(com.simibubi.create.content.kinetics.belt.BeltBlock.PART,com.simibubi.create.content.kinetics.belt.BeltPart.END));
        var first=AllBlocks.ANDESITE_BELT_FUNNEL.defaultBlockState().setValue(BeltFunnelBlock.HORIZONTAL_FACING,Direction.EAST).setValue(BeltFunnelBlock.SHAPE,BeltFunnelBlock.Shape.RETRACTED);var second=first.setValue(BeltFunnelBlock.HORIZONTAL_FACING,Direction.WEST).setValue(BeltFunnelBlock.SHAPE,BeltFunnelBlock.Shape.EXTENDED);h.setBlock(P,first);h.setBlock(P.east(),second);
        try(var session=join(h);var observer=join(h)){session.player().setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH));var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)),Direction.UP,h.absolutePos(P),false);var result=session.player().getMainHandItem().useOn(new UseOnContext(session.player(),InteractionHand.MAIN_HAND,hit));h.assertTrue(result.consumesAction()&&h.getBlockState(P).getValue(BeltFunnelBlock.SHAPE)==BeltFunnelBlock.Shape.EXTENDED&&done(session.player(),"belt_funnel_kiss")&&!done(observer.player(),"belt_funnel_kiss"),"Genuine wrench event extends two physically opposed native funnels and awards only actual user");}h.succeed();
    }
    @GameTest public void realPairedPunchCycleAwardsOneSourceOwnerWithoutClearingDeployerAuthority(GameTestHelper h){
        h.setBlock(P,AllBlocks.DEPLOYER.defaultBlockState().setValue(DeployerBlock.FACING,Direction.EAST));h.setBlock(P.east(3),AllBlocks.DEPLOYER.defaultBlockState().setValue(DeployerBlock.FACING,Direction.WEST));
        try(var first=join(h);var second=join(h)){placed(h,P,first.player());placed(h,P.east(3),second.player());var left=h.getBlockEntity(P,DeployerBlockEntity.class);var right=h.getBlockEntity(P.east(3),DeployerBlockEntity.class);left.changeMode();right.changeMode();drive(left,128);drive(right,128);for(int i=0;i<80&&!done(first.player(),"fist_bump")&&!done(second.player(),"fist_bump");i++){DeployerBlockEntity.tick(h.getLevel(),right.getBlockPos(),right.getBlockState(),right);DeployerBlockEntity.tick(h.getLevel(),left.getBlockPos(),left.getBlockState(),left);}h.assertTrue(done(first.player(),"fist_bump")^done(second.player(),"fist_bump"),"Only actual finishing source hand earns the pinned owner event");h.assertTrue(first.player().getUUID().equals(left.owner)&&second.player().getUUID().equals(right.owner),"Fist achievement never clears actual durable actor permission identities");}h.succeed();
    }
    @GameTest public void completeNativeDrainHandoffMovesSevenOriginalApplesAndAwardsSourceOwner(GameTestHelper h){
        h.setBlock(P,AllBlocks.ITEM_DRAIN);h.setBlock(P.east(),AllBlocks.ITEM_DRAIN);
        try(var sourceOwner=join(h);var destinationOwner=join(h)){placed(h,P,sourceOwner.player());placed(h,P.east(),destinationOwner.player());var source=h.getBlockEntity(P,ItemDrainBlockEntity.class);var target=h.getBlockEntity(P.east(),ItemDrainBlockEntity.class);try(var tx=Transaction.openOuter()){h.assertValueEqual(source.storage(Direction.WEST).insert(ItemVariant.of(Items.APPLE),7,tx),7L,"Seven finite actual originals enter source direct input");tx.commit();}for(int i=0;i<12&&!source.getHeldItemStack().isEmpty();i++)ItemDrainBlockEntity.tick(h.getLevel(),source.getBlockPos(),source.getBlockState(),source);h.assertTrue(source.getHeldItemStack().isEmpty()&&target.getHeldItemStack().is(Items.APPLE)&&target.getHeldItemStack().getCount()==7&&done(sourceOwner.player(),"chained_drain")&&!done(destinationOwner.player(),"chained_drain"),"Full real native handoff pays source inventory and awards the exact source Owner");}h.succeed();
    }
    @GameTest public void actualSurvivalPackageThrowAwardsThrowerOnlyAfterWholeNativeChuteCapture(GameTestHelper h){
        h.setBlock(P,AllBlocks.CHUTE);var chute=h.getBlockEntity(P,ChuteBlockEntity.class);ChuteBlockEntity.tick(h.getLevel(),chute.getBlockPos(),chute.getBlockState(),chute);
        try(var session=join(h);var observer=join(h)){var player=session.player();var stack=PackageItem.containing(List.of(new ItemStack(Items.DIAMOND,7)));player.setItemInHand(InteractionHand.MAIN_HAND,stack);var item=(PackageItem)stack.getItem();h.assertTrue(item.releaseUsing(stack,h.getLevel(),player,item.getUseDuration(stack,player)-20)&&stack.isEmpty(),"Actual survival throw pays exactly one complete seven-diamond parcel");var boxes=h.getLevel().getEntities(AllEntityTypes.PACKAGE,new AABB(player.blockPosition()).inflate(6),box->box.tossedBy.get()==player);h.assertValueEqual(boxes.size(),1,"One actually admitted native throw remembers its genuine source player");var box=boxes.getFirst();box.setPos(Vec3.atBottomCenterOf(h.absolutePos(P.above())));for(int i=0;i<20&&box.isAlive();i++)chute.captureItem(box);h.assertTrue(box.isRemoved()&&done(player,"package_chute_throw")&&!done(observer.player(),"package_chute_throw"),"Whole actual centered package capture retires entity and awards actual thrower");h.assertValueEqual(PackageItem.getContents(chute.getItem()).stream().filter(s->s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum(),7,"One real chute owns exactly seven original packaged diamonds");}h.succeed();
    }
}
