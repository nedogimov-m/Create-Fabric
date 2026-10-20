package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.fluids.hosePulley.*;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.fluids.transfer.FluidManipulationBehaviour.BottomlessFluidMode;
import com.simibubi.create.content.kinetics.chainConveyor.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.*;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.simibubi.create.content.logistics.redstoneRequester.AutoRequestData;
import com.simibubi.create.content.logistics.stockTicker.*;
import com.simibubi.create.content.logistics.tableCloth.*;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.advancement.PrerequisiteAdvancements;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;

/** Real source prerequisites and finite world/stock witnesses. Proposed cases; runtime is UNRUN. */
public class PrerequisiteAdvancementGameTests {
    private static final BlockPos P=new BlockPos(3,3,3);
    private static ServerPlayer player(GameTestHelper h){return sourcePlayer(h,P.south(3));}
    private static ServerPlayer sourcePlayer(GameTestHelper h,BlockPos position){
        h.setBlock(position.below(),Blocks.STONE);
        var joined=NativeSurvivalTestPlayer.join(h,Vec3.atBottomCenterOf(h.absolutePos(position)));
        var closed=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup=()->{if(closed.compareAndSet(false,true))joined.close();};
        ((com.simibubi.create.test.mixin.GameTestHelperNativeInfoAccessor)(Object)h).create$getActualNativeTestInfo().addListener(new net.minecraft.gametest.framework.GameTestListener(){
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info){}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo info,net.minecraft.gametest.framework.GameTestRunner runner){cleanup.run();}
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo info,net.minecraft.gametest.framework.GameTestRunner runner){cleanup.run();}
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo old,net.minecraft.gametest.framework.GameTestInfo next,net.minecraft.gametest.framework.GameTestRunner runner){cleanup.run();}
        });
        return joined.player();
    }

    private static boolean done(ServerPlayer player,String id){var entry=player.level().getServer().getAdvancements().get(Create.asResource(id));return entry!=null&&player.getAdvancements().getOrStartProgress(entry).isDone();}
    private static void placed(GameTestHelper h,BlockPos pos,ServerPlayer owner){var state=h.getBlockState(pos);state.getBlock().setPlacedBy(h.getLevel(),h.absolutePos(pos),state,owner,new ItemStack(state.getBlock()));}
    private static PrerequisiteAdvancements.Owner owner(BlockEntity device){
        if(device instanceof WindmillBearingBlockEntity be)return be.sourceAdvancements;
        if(device instanceof FluidTankBlockEntity be)return be.sourceAdvancements;
        if(device instanceof HosePulleyBlockEntity be)return be.sourceAdvancements;
        if(device instanceof SpoutBlockEntity be)return be.sourceAdvancements;
        if(device instanceof PackagerBlockEntity be)return be.sourceAdvancements;
        if(device instanceof FrogportBlockEntity be)return be.sourceAdvancements;
        if(device instanceof FactoryPanelBlockEntity be)return be.sourceAdvancements;
        throw new AssertionError("Actual source owner device required");
    }
    private record Station(PackagerBlockEntity packager,BarrelBlockEntity barrel,PackagerLinkBlockEntity link){}
    private static Station station(GameTestHelper h,BlockPos pos,UUID network,int apples,ServerPlayer owner){
        h.setBlock(pos.north(),Blocks.BARREL);var barrel=h.getBlockEntity(pos.north(),BarrelBlockEntity.class);if(apples>0)barrel.setItem(0,new ItemStack(Items.APPLE,apples));
        h.setBlock(pos,AllBlocks.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING,Direction.SOUTH));placed(h,pos,owner);
        h.setBlock(pos.above(),AllBlocks.STOCK_LINK);var link=h.getBlockEntity(pos.above(),PackagerLinkBlockEntity.class);link.placedBy=owner.getUUID();link.behaviour.setFrequency(network);link.initialize();
        return new Station(h.getBlockEntity(pos,PackagerBlockEntity.class),barrel,link);
    }
    private static PackageOrderWithCrafts apples(int count){return PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.APPLE),count)));}

    @GameTest public void allOriginalMissingParentsResolveAndCardboardUsesActualVanillaCollection(GameTestHelper h){
        var manager=h.getLevel().getServer().getAdvancements();var visited=new HashSet<String>();
        for(var root:List.of("ejector_maxed","cardboard_armor_trim","diving_suit_lava")){
            String id=root;while(id!=null&&visited.add(id)){var entry=manager.get(Create.asResource(id));h.assertTrue(entry!=null,"Actual original advancement ancestor loaded: "+id);id=entry.value().parent().filter(parent->parent.getNamespace().equals("create")).map(parent->parent.getPath()).orElse(null);}
        }
        var player=player(h);h.assertFalse(done(player,"cardboard"),"Fresh native player has no original cardboard collection");player.getInventory().setItem(0,new ItemStack(AllItems.CARDBOARD));player.inventoryMenu.broadcastChanges();
        h.assertTrue(done(player,"cardboard")&&player.getInventory().getItem(0).is(AllItems.CARDBOARD),"Real native inventory criterion collects the original cardboard without consuming it");h.succeed();
    }
    @GameTest public void sevenActualPlacementOwnersPersistBeforeNativeLevelAssignmentWithoutNearestFallback(GameTestHelper h){
        var player=player(h);h.setBlock(P.below(),Blocks.STONE);
        for(var block:List.of(AllBlocks.WINDMILL_BEARING,AllBlocks.FLUID_TANK,AllBlocks.HOSE_PULLEY,AllBlocks.SPOUT,AllBlocks.PACKAGER,AllBlocks.PACKAGE_FROGPORT,AllBlocks.FACTORY_GAUGE)){
            h.setBlock(P,block);placed(h,P,player);var device=h.getLevel().getBlockEntity(h.absolutePos(P));h.assertValueEqual(owner(device).uuid(),player.getUUID(),"Original native placement owner: "+block);
            var saved=device.saveWithFullMetadata(h.getLevel().registryAccess());var decoded=BlockEntity.loadStatic(device.getBlockPos(),device.getBlockState(),saved,h.getLevel().registryAccess());h.assertTrue(decoded!=null&&decoded.getLevel()==null&&player.getUUID().equals(owner(decoded).uuid()),"Native pre-level Owner decode: "+block);
            saved.remove("Owner");decoded=BlockEntity.loadStatic(device.getBlockPos(),device.getBlockState(),saved,h.getLevel().registryAccess());h.assertTrue(owner(decoded).uuid()==null,"Ownerless original saves cannot adopt the nearby test player");
        }h.succeed();
    }
    @GameTest public void replacedOriginalPackagerCannotAwardItsPreviousOwner(GameTestHelper h){
        var previous=player(h);var replacement=player(h);var source=station(h,P,UUID.randomUUID(),7,previous);var old=source.packager;
        h.setBlock(P,Blocks.AIR);h.setBlock(P,AllBlocks.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING,Direction.SOUTH));placed(h,P,replacement);
        old.sourceAdvancements.award("packager");h.assertFalse(done(previous,"packager"),"Retired exact world BE cannot issue a previous-owner criterion");
        var fresh=h.getBlockEntity(P,PackagerBlockEntity.class);fresh.attemptToSend(null);h.assertTrue(done(replacement,"packager")&&!done(previous,"packager")&&source.barrel.isEmpty(),"Actual fresh packager consumes the seven original apples and awards only its genuine new owner");h.succeed();
    }
    private static String nativeWindmillReceipt(GameTestHelper h,WindmillBearingBlockEntity bearing,ServerPlayer player){
        var rotor=bearing.getMovedContraption();var forest=rotor==null?null:rotor.getContraption();
        return " [nativeWindmill tick="+h.getTick()+" controllerCurrent="+(h.getLevel().getBlockEntity(bearing.getBlockPos())==bearing)+" running="+bearing.isRunning()+" lastError="+bearing.getLastAssemblyException()+" owner="+bearing.sourceAdvancements.uuid()+" player="+player.getUUID()+" alive="+player.isAlive()+" mode="+player.gameMode()
            +" awards="+done(player,"windmill")+"/"+done(player,"windmill_maxed")+"/"+done(player,"contraption_actors")+" rotor="+(rotor==null?"null":rotor.getUUID()+" current="+(h.getLevel().getEntity(rotor.getUUID())==rotor)+" removed="+rotor.isRemoved()+" owned="+rotor.hasOwnedStructure())
            +" forest="+(forest==null?"null":"blocks="+forest.getBlocks().size()+" actors="+forest.getActors().size()+" sails="+((BearingContraption)forest).getSailBlocks()+" actualSailRows="+forest.getBlocks().values().stream().filter(row->row.state().is(BearingContraption.WINDMILL_SAILS)).count()+" actualDrillRows="+forest.getBlocks().values().stream().filter(row->row.state().is(AllBlocks.MECHANICAL_DRILL)).count()+" breakers="+forest.containsBlockBreakers()+" recovering="+forest.getStabilizedSubContraptions().isRecovering())+"]";
    }
    private static String nativeBoilerReceipt(GameTestHelper h,BlockPos base,BlockPos reservePos,ServerPlayer player,java.util.concurrent.atomic.AtomicLong spent){
        var tank=h.getBlockEntity(base,FluidTankBlockEntity.class).getControllerBE();var reserve=h.getBlockEntity(reservePos,FluidTankBlockEntity.class).getControllerBE();
        return " [nativeBoiler tick="+h.getTick()+" player="+player.getUUID()+" alive="+player.isAlive()+" registered="+(h.getLevel().getPlayerByUUID(player.getUUID())==player)+" maxAward="+done(player,"steam_engine_maxed")+" pipeOrganAward="+done(player,"pipe_organ")+" owner="+(tank==null?null:tank.sourceAdvancements.uuid())+" pending="+(tank!=null&&com.simibubi.create.foundation.advancement.AdvancementBehaviour.hasPendingFor(tank,tank.sourceAdvancements.uuid()))
            +" tank="+(tank==null?"null":tank.getBlockPos()+" current="+(h.getLevel().getBlockEntity(tank.getBlockPos())==tank)+" structureLoaded="+tank.structureLoaded()+" size="+tank.getTotalTankSize()+" active="+tank.boiler.isActive()+" heat="+tank.boiler.activeHeat+" supply="+tank.boiler.waterSupply+" actualHeat="+tank.boiler.getActualHeat(tank.getTotalTankSize())+" engines="+tank.boiler.attachedEngines+" whistles="+tank.boiler.attachedWhistles)
            +" reserveAmount="+(reserve==null?-1:reserve.getTankInventory().getAmount())+" committedSpent="+spent.get()+" finiteLimit="+(12*FluidConstants.BUCKET)+"]";
    }
    private static WindmillBearingBlockEntity windmill(GameTestHelper h,int count,ServerPlayer player){
        var bearing=new BlockPos(14,3,14);var anchor=bearing.above();h.setBlock(bearing,AllBlocks.WINDMILL_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));placed(h,bearing,player);
        int made=0;h.setBlock(anchor,Blocks.WOOL.pick(DyeColor.WHITE));made++;
        for(int x=-7;x<=8&&made<count;x++)for(int z=-3;z<=4&&made<count;z++)if(x!=0||z!=0){h.setBlock(anchor.offset(x,0,z),Blocks.WOOL.pick(DyeColor.WHITE));made++;}
        h.assertValueEqual(made,count,"Finite actual original sail stock placed once");h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(anchor.offset(-7,0,-3)),h.absolutePos(anchor.offset(8,0,4)))));
        // The source bearing also retains CONTRAPTION_ACTORS; admit one real drill so
        // the128-sail case genuinely completes every registered owner criterion.
        h.setBlock(anchor.above(),AllBlocks.MECHANICAL_DRILL.defaultBlockState().setValue(com.simibubi.create.content.kinetics.drill.DrillBlock.FACING,Direction.UP));
        h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(anchor),h.absolutePos(anchor.above())))),"Actual native glue admits the original drill with the paid sail forest");
        var result=h.getBlockEntity(bearing,WindmillBearingBlockEntity.class);result.requestAssembly();return result;
    }
    @GameTest(structure="create_test:reservoir",maxTicks=70) public void actualOneHundredTwentySevenSailRotorCannotEarnOriginalMaxedThreshold(GameTestHelper h){
        var player=player(h);var bearing=windmill(h,127,player);h.succeedWhen(()->{var rotor=bearing.getMovedContraption();h.assertTrue(rotor!=null&&rotor.hasOwnedStructure()&&((BearingContraption)rotor.getContraption()).getSailBlocks()==127&&rotor.getContraption().containsBlockBreakers(),"Actual admitted native rotor owns exactly127 original sails"+nativeWindmillReceipt(h,bearing,player));h.assertTrue(done(player,"windmill")&&done(player,"contraption_actors")&&!done(player,"windmill_maxed")&&player.getUUID().equals(bearing.sourceAdvancements.uuid()),"Real ordinary windmill event retains owner for unmet original128 threshold"+nativeWindmillReceipt(h,bearing,player));});
    }
    @GameTest(structure="create_test:reservoir",maxTicks=70) public void actualOneHundredTwentyEightSailForestAdmissionAwardsSavedOwnerAndRetiresCompletedOwner(GameTestHelper h){
        var player=player(h);var bearing=windmill(h,128,player);h.succeedWhen(()->{var rotor=bearing.getMovedContraption();h.assertTrue(rotor!=null&&rotor.hasOwnedStructure()&&((BearingContraption)rotor.getContraption()).getSailBlocks()==128&&rotor.getContraption().containsBlockBreakers(),"Actual native forest has the128 original paid world sails"+nativeWindmillReceipt(h,bearing,player));h.assertTrue(done(player,"windmill")&&done(player,"contraption_actors")&&done(player,"windmill_maxed")&&bearing.sourceAdvancements.uuid()==null,"Actual source threshold awards original placement owner then clears completed Owner"+nativeWindmillReceipt(h,bearing,player));});
    }
    @GameTest(structure="create_test:reservoir",maxTicks=120) public void realSeventyTwoTankNinePaidBurnerBoilerRequiresCommittedWaterForSourceLevelEighteen(GameTestHelper h){
        var player=player(h);var base=new BlockPos(12,3,12);int fuel=0;
        for(int x=0;x<3;x++)for(int z=0;z<3;z++){
            for(int y=0;y<8;y++)h.setBlock(base.offset(x,y,z),AllBlocks.FLUID_TANK);
            var pos=base.offset(x,-1,z);h.setBlock(pos,AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.SMOULDERING));var cake=new ItemStack(AllItems.BLAZE_CAKE);player.setItemInHand(InteractionHand.MAIN_HAND,cake);var absolute=h.absolutePos(pos);
            h.assertTrue(h.getBlockState(pos).useItemOn(cake,h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(absolute),Direction.UP,absolute,false)).consumesAction()&&cake.isEmpty(),"Native burner interaction consumes one original finite blaze cake");fuel++;
        }
        h.assertValueEqual(fuel,9,"Nine actual paid native heaters");placed(h,base,player);
        h.setBlock(base.west(3),AllBlocks.SHAFT.defaultBlockState().setValue(com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlock.AXIS,Direction.Axis.Z));
        h.setBlock(base.west(),AllBlocks.STEAM_ENGINE.defaultBlockState().setValue(SteamEngineBlock.FACE,AttachFace.WALL).setValue(SteamEngineBlock.FACING,Direction.WEST));
        var reservePos=base.east(7);h.setBlock(reservePos,AllBlocks.FLUID_TANK);h.setBlock(reservePos.above(),AllBlocks.FLUID_TANK);
        var spent=new java.util.concurrent.atomic.AtomicLong();
        h.runAfterDelay(5,()->{var reserve=h.getBlockEntity(reservePos,FluidTankBlockEntity.class).getControllerBE();try(var tx=Transaction.openOuter()){h.assertValueEqual(reserve.storage().insert(FluidVariant.of(Fluids.WATER),12*FluidConstants.BUCKET,tx),12*FluidConstants.BUCKET,"Original finite twelve-bucket water reserve issued only once");tx.commit();}});
        h.onEachTick(()->{var controller=h.getBlockEntity(base,FluidTankBlockEntity.class).getControllerBE();var reserve=h.getBlockEntity(reservePos,FluidTankBlockEntity.class).getControllerBE();if(controller!=null&&reserve!=null&&controller.boiler.isActive())try(var tx=Transaction.openOuter()){long amount=reserve.storage().extract(FluidVariant.of(Fluids.WATER),180*81L,tx);h.assertValueEqual(controller.storage().insert(FluidVariant.of(Fluids.WATER),amount,tx),amount,"Same finite original water moves atomically into genuine boiler supply");if(h.getTick()>25){tx.addOuterCloseCallback(result->{if(result.wasCommitted())spent.addAndGet(amount);});tx.commit();}}});
        h.runAfterDelay(23,()->h.assertFalse(done(player,"steam_engine_maxed"),"Aborted native water supply cannot create the original maxed boiler award"));
        h.succeedWhen(()->{var tank=h.getBlockEntity(base,FluidTankBlockEntity.class).getControllerBE();h.assertTrue(h.getTick()>25&&tank.getTotalTankSize()==72&&tank.boiler.getActualHeat(72)==18,"Actual native connectivity, nine finite heated cages and real committed water samples produce level18"+nativeBoilerReceipt(h,base,reservePos,player,spent));h.assertTrue(done(player,"steam_engine_maxed")&&!done(player,"pipe_organ")&&player.getUUID().equals(tank.sourceAdvancements.uuid())&&com.simibubi.create.foundation.advancement.AdvancementBehaviour.hasPendingFor(tank,player.getUUID())&&player.getUUID().equals(tank.saveWithFullMetadata(h.getLevel().registryAccess()).read("Owner",UUIDUtil.CODEC).orElse(null))&&spent.get()>0&&spent.get()<=12*FluidConstants.BUCKET,"Real source sample consumes original finite water, awards actual tank placement owner and persists Owner for genuine unearned pipe-organ criterion"+nativeBoilerReceipt(h,base,reservePos,player,spent));});
    }
    private static HosePulleyBlockEntity hose(GameTestHelper h,ServerPlayer player,boolean infinite){
        var pos=new BlockPos(3,5,3);var root=pos.below(3);for(var p:BlockPos.betweenClosed(root.offset(-1,-1,-1),root.offset(3,0,1)))h.setBlock(p,Blocks.STONE);for(int x=0;x<3;x++)h.setBlock(root.east(x),Blocks.LAVA);
        h.setBlock(pos,AllBlocks.HOSE_PULLEY);placed(h,pos,player);var original=h.getBlockEntity(pos,HosePulleyBlockEntity.class);var tag=original.saveWithFullMetadata(h.getLevel().registryAccess());var offset=new net.minecraft.nbt.CompoundTag();offset.putFloat("Value",3);tag.put("Offset",offset);
        var result=(HosePulleyBlockEntity)BlockEntity.loadStatic(original.getBlockPos(),original.getBlockState(),tag,h.getLevel().registryAccess());h.getLevel().removeBlockEntity(original.getBlockPos());h.getLevel().setBlockEntity(result);result.configure(new HosePulleySettings(128,2,false,infinite?BottomlessFluidMode.ALLOW_ALL:BottomlessFluidMode.DENY_ALL,true));return result;
    }
    @GameTest(maxTicks=60) public void actualInfiniteLavaDrainAwardsOnlyAfterTheOuterTransferCommits(GameTestHelper h){
        var player=player(h);var pulley=hose(h,player,true);h.runAfterDelay(12,()->{
            h.assertTrue(pulley.isInfinite(),"Three real connected source cells meet the actual configured bottomless threshold");
            try(var outer=Transaction.openOuter()){try(var inner=outer.openNested()){h.assertValueEqual(pulley.storage.extract(FluidVariant.of(Fluids.LAVA),FluidConstants.BUCKET,inner),FluidConstants.BUCKET,"Real tentative nested lava transfer");inner.commit();}}
            h.assertFalse(done(player,"hose_pulley_lava"),"Nested success followed by outer abort grants no source criterion");
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(FluidVariant.of(Fluids.LAVA),FluidConstants.BUCKET,tx),FluidConstants.BUCKET,"Same real infinite lava transfer remains available");tx.commit();}
            h.assertTrue(done(player,"hose_pulley_lava")&&pulley.sourceAdvancements.uuid()==null&&pulley.tank.isEmpty(),"One actual committed source extraction awards saved owner without extra buffer stock");for(int x=0;x<3;x++)h.assertBlockPresent(Blocks.LAVA,new BlockPos(3,2,3).east(x));h.succeed();
        });
    }
    @GameTest(maxTicks=60) public void actualFiniteLavaCannotUseTheOriginalInfiniteLavaCriterion(GameTestHelper h){
        var player=player(h);var pulley=hose(h,player,false);h.runAfterDelay(12,()->{h.assertFalse(pulley.isInfinite(),"Real source policy denies bottomless lava");try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(FluidVariant.of(Fluids.LAVA),FluidConstants.BUCKET,tx),FluidConstants.BUCKET,"Genuine finite lava bucket supplied");tx.commit();}h.assertFalse(done(player,"hose_pulley_lava"),"Actual finite source consumption cannot earn original infinite-body event");h.succeed();});
    }
    private static void food(GameTestHelper h,SpoutBlockEntity spout,Item input,net.minecraft.world.level.material.Fluid fluid){
        var depot=h.getBlockEntity(P,DepotBlockEntity.class);try(var tx=Transaction.openOuter()){h.assertValueEqual(depot.inventory.automation.insert(ItemVariant.of(input),1,tx),1L,"One original finite food input admitted");h.assertValueEqual(spout.tank.insert(FluidVariant.of(fluid),250*81L,tx),250*81L,"Exact original filling fluid paid once");tx.commit();}
    }
    private static void receiveFood(GameTestHelper h,SpoutBlockEntity spout,Item output){
        var source=h.getBlockEntity(P,DepotBlockEntity.class).inventory.automation;var barrel=h.getBlockEntity(P.east(2),BarrelBlockEntity.class);var receipt=ContainerStorage.of(barrel,null);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(source.extract(ItemVariant.of(output),1,tx),1L,"Actual original filling result exists once");h.assertValueEqual(receipt.insert(ItemVariant.of(output),1,tx),1L,"Same real result moves to original receipt barrel");tx.commit();}h.assertTrue(spout.tank.isEmpty(),"Exact250 mB was actually consumed");
    }
    @GameTest(maxTicks=160) public void sameRealSpoutProducesAllThreeFoodsPaysFluidsAndPreservesOriginalFoodFlagsAcrossNativeReload(GameTestHelper h){
        var player=player(h);h.setBlock(P,AllBlocks.DEPOT);h.setBlock(P.east(2),Blocks.BARREL);h.setBlock(P.above(2),AllBlocks.SPOUT);placed(h,P.above(2),player);var first=h.getBlockEntity(P.above(2),SpoutBlockEntity.class);food(h,first,Items.SWEET_BERRIES,AllFluids.CHOCOLATE);
        h.runAfterDelay(35,()->{receiveFood(h,first,AllItems.CHOCOLATE_BERRIES);h.assertFalse(done(player,"foods"),"One actual food is insufficient");food(h,first,Items.APPLE,AllFluids.HONEY);});
        h.runAfterDelay(75,()->{receiveFood(h,first,AllItems.HONEYED_APPLE);h.assertFalse(done(player,"foods"),"Two actual foods remain below original three-food requirement");var saved=first.saveWithFullMetadata(h.getLevel().registryAccess());h.assertTrue(saved.getBooleanOr("ChocolateBerries",false)&&saved.getBooleanOr("HoneyApple",false),"Original actual producer markers survive native disk save");var restored=(SpoutBlockEntity)BlockEntity.loadStatic(first.getBlockPos(),first.getBlockState(),saved,h.getLevel().registryAccess());h.getLevel().removeBlockEntity(first.getBlockPos());h.getLevel().setBlockEntity(restored);food(h,restored,Items.BREAD,AllFluids.MILK);});
        h.runAfterDelay(120,()->{var restored=h.getBlockEntity(P.above(2),SpoutBlockEntity.class);receiveFood(h,restored,AllItems.SWEET_ROLL);h.assertTrue(done(player,"foods")&&restored.sourceAdvancements.uuid()==null,"Fresh real spout owner receives original three-food event");var receipt=h.getBlockEntity(P.east(2),BarrelBlockEntity.class);int total=0;for(var stack:receipt)total+=stack.getCount();h.assertValueEqual(total,3,"Only the three original paid recipe results remain owned in the real barrel");h.succeed();});
    }
    @GameTest public void actualPackagerRequestAbortRetainsStockAndOwnerUntilRealPackagingCommits(GameTestHelper h){
        var player=player(h);var id=UUID.randomUUID();var station=station(h,P,id,7,player);
        var requests=LogisticsManager.findPackagersForRequest(h.getLevel(),id,apples(7),null,"Original");var request=new ArrayList<>(requests.get(station.packager));h.assertFalse(request.isEmpty(),"Actual network discovers original warehouse stock");
        try(var tx=Transaction.openOuter()){h.assertTrue(station.packager.fulfillRequests(request,tx),"Real seven-apple packaging tentatively succeeds");}
        h.assertFalse(done(player,"packager"),"Aborted original packaging cannot award");h.assertTrue(station.packager.heldBox.isEmpty()&&station.barrel.getItem(0).getCount()==7,"Outer abort restores actual source apples and output owner");
        station.packager.attemptToSend(null);h.assertTrue(done(player,"packager")&&station.packager.sourceAdvancements.uuid()==null&&station.barrel.isEmpty(),"Genuine native packaging commits original stock and placement owner event");h.assertValueEqual(PackageItem.getContents(station.packager.heldBox).stream().mapToInt(ItemStack::getCount).sum(),7,"Exactly seven original apples have one actual parcel owner");h.succeed();
    }
    @GameTest(maxTicks=140) public void onlyActualCatchingFrogOwnerEarnsCriterionDuringRealBarrelChainBarrelDelivery(GameTestHelper h){
        var senderOwner=player(h);var receiverOwner=player(h);var a=new BlockPos(1,5,2);var b=a.east(5);var send=a.south(2).below(2);var receive=b.south(2).below(2);
        h.setBlock(a,AllBlocks.CHAIN_CONVEYOR);h.setBlock(b,AllBlocks.CHAIN_CONVEYOR);var first=h.getBlockEntity(a,ChainConveyorBlockEntity.class);var second=h.getBlockEntity(b,ChainConveyorBlockEntity.class);first.connectTo(second,null);
        h.setBlock(a.below(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.UP));var motor=h.getBlockEntity(a.below(),KineticBlockEntity.class);for(int i=0;i<3;i++)motor.adjustGeneratedSpeed(false);
        h.setBlock(send,AllBlocks.PACKAGE_FROGPORT);h.setBlock(receive,AllBlocks.PACKAGE_FROGPORT);placed(h,send,senderOwner);placed(h,receive,receiverOwner);var sender=h.getBlockEntity(send,FrogportBlockEntity.class);var receiver=h.getBlockEntity(receive,FrogportBlockEntity.class);sender.addressFilter="Sender";sender.acceptsPackages=false;receiver.addressFilter="Market";
        sender.setTarget(new ChainConveyorFrogportTarget(h.absolutePos(a).subtract(sender.getBlockPos()),0,null,false));receiver.setTarget(new ChainConveyorFrogportTarget(h.absolutePos(b).subtract(receiver.getBlockPos()),90,null,false));
        h.setBlock(send.below(),Blocks.BARREL);h.setBlock(receive.below(),Blocks.BARREL);var source=h.getBlockEntity(send.below(),BarrelBlockEntity.class);var receipt=h.getBlockEntity(receive.below(),BarrelBlockEntity.class);var original=PackageItem.containing(List.of(new ItemStack(Items.DIAMOND,7)));PackageItem.addAddress(original,"Market");source.setItem(0,original.copy());
        h.runAfterDelay(110,()->{h.assertTrue(done(receiverOwner,"frogport")&&!done(senderOwner,"frogport"),"Source criterion belongs to genuine catch owner, not depositing sender");h.assertTrue(source.isEmpty()&&ItemStack.isSameItemSameComponents(receipt.getItem(0),original)&&sender.animatedPackage.isEmpty()&&receiver.animatedPackage.isEmpty()&&first.packageCount()+second.packageCount()==0,"Exactly one original sealed seven-diamond parcel traveled through actual powered chain into native receipt");h.succeed();});
    }
    @GameTest(maxTicks=120) public void factoryRecipeAwardsOnlyAfterRealIngredientNetworkRequestAndKeepsOriginalOwners(GameTestHelper h){
        var warehouseOwner=player(h);var gaugeOwner=player(h);var id=UUID.randomUUID();var station=station(h,new BlockPos(2,2,2),id,20,warehouseOwner);var pos=new BlockPos(2,2,5);h.setBlock(pos.below(),Blocks.STONE);h.setBlock(pos,AllBlocks.FACTORY_GAUGE);var gauge=h.getBlockEntity(pos,FactoryPanelBlockEntity.class);gauge.addPanel(FactoryPanelBlock.PanelSlot.TOP_LEFT,id);gauge.addPanel(FactoryPanelBlock.PanelSlot.TOP_RIGHT,id);placed(h,pos,gaugeOwner);
        var input=gauge.panels.get(FactoryPanelBlock.PanelSlot.TOP_LEFT);input.setFilter(new ItemStack(Items.APPLE));input.setAmount(0,true);var output=gauge.panels.get(FactoryPanelBlock.PanelSlot.TOP_RIGHT);output.setFilter(new ItemStack(Items.DIAMOND));output.setAmount(2,true);output.recipeOutput=2;h.assertTrue(output.addConnection(input.getPanelPosition()),"Actual source recipe input edge accepted");output.targetedBy.get(input.getPanelPosition()).amount=3;
        h.runAfterDelay(20,()->{h.assertFalse(done(gaugeOwner,"factory_gauge"),"Missing genuine delivery address cannot create an original factory criterion");h.assertValueEqual(station.barrel.getItem(0).getCount(),20,"Original stock retained before real recipe request");output.recipeAddress="Assembly";output.resetTimerSlightly();});
        h.succeedWhen(()->{h.assertTrue(h.getTick()>20&&done(gaugeOwner,"factory_gauge")&&!done(warehouseOwner,"factory_gauge")&&done(warehouseOwner,"packager"),"Actual factory recipe and native packaging award their separate saved device owners");h.assertValueEqual(station.barrel.getItem(0).getCount(),17,"Exactly three original real ingredients requested");h.assertValueEqual(output.getPromised(),2,"Only actual expected source output is promised");h.assertValueEqual(PackageItem.getContents(station.packager.heldBox).stream().mapToInt(ItemStack::getCount).sum(),3,"Three paid ingredients have one native parcel owner");});
    }
    @GameTest public void realTickerMenuRejectsForgedAndEmptyOrdersBeforeActualNonemptySourceAward(GameTestHelper h){
        var player=player(h);var id=UUID.randomUUID();var source=station(h,new BlockPos(2,2,2),id,20,player);var pos=new BlockPos(5,2,5);h.setBlock(pos,AllBlocks.STOCK_TICKER);var ticker=h.getBlockEntity(pos,StockTickerBlockEntity.class);ticker.behaviour.setFrequency(id);ticker.tick();player.setPos(Vec3.atCenterOf(ticker.getBlockPos()).add(0,0,1));var menu=new StockTickerMenu(14,player.getInventory(),new StockTickerMenu.Opening(ticker.getBlockPos(),ticker.getUpdateTag(h.getLevel().registryAccess()),false,true,false));
        menu.request(player,new StockTickerMenu.Request(14,PackageOrderWithCrafts.empty(),"Remember",false));h.assertFalse(done(player,"stock_ticker"),"Empty native menu close cannot earn original request criterion");h.assertFalse(menu.request(player,new StockTickerMenu.Request(15,apples(7),"Forged",false)),"Wrong actual native menu id rejects source action");h.assertFalse(done(player,"stock_ticker"),"Rejected native menu action cannot award");
        h.assertTrue(menu.request(player,new StockTickerMenu.Request(14,apples(7),"Original",false)),"Real nearby nonempty native menu request succeeds");h.assertTrue(done(player,"stock_ticker")&&source.barrel.getItem(0).getCount()==13&&PackageItem.getAddress(source.packager.heldBox).equals("Original"),"Actual player request awards source criterion and moves exactly seven original apples");h.succeed();
    }
    @GameTest public void genuineEncodedShopPlacementPaysOneClothAndAwardsOnlyActualPlacer(GameTestHelper h){
        var player=player(h);var other=player(h);var tickerPos=new BlockPos(2,2,2);h.setBlock(tickerPos,AllBlocks.STOCK_TICKER);var ticker=h.getBlockEntity(tickerPos,StockTickerBlockEntity.class);var network=UUID.randomUUID();ticker.behaviour.setFrequency(network);h.setBlock(tickerPos.west(),AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.SMOULDERING));
        var pos=new BlockPos(5,3,5);h.setBlock(pos.below(),Blocks.STONE);var cloth=AllBlocks.TABLE_CLOTHS.get(DyeColor.RED);var stack=new ItemStack(cloth,2);stack.set(AllDataComponents.AUTO_REQUEST_DATA,new AutoRequestData(apples(4),"Original shop",ticker.getBlockPos(),h.getLevel().dimension().identifier().toString(),false));player.setItemInHand(InteractionHand.MAIN_HAND,stack);player.setPos(Vec3.atCenterOf(h.absolutePos(pos)).add(0,1,2));var floor=h.absolutePos(pos.below());
        h.assertTrue(stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(floor).add(0,.499,0),Direction.UP,floor,false))).consumesAction(),"Actual programmed native BlockItem placement succeeds");var placed=h.getBlockEntity(pos,TableClothBlockEntity.class);h.assertTrue(done(player,"table_cloth_shop")&&!done(other,"table_cloth_shop")&&placed.owner.equals(player.getUUID())&&placed.getTicker()==ticker&&stack.getCount()==1,"One actual encoded cloth is paid, real source shop restored and only its genuine placer receives criterion");h.succeed();
    }
}
