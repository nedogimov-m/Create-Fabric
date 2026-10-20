package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.decoration.steamWhistle.*;
import com.simibubi.create.content.fluids.*;
import com.simibubi.create.content.fluids.hosePulley.*;
import com.simibubi.create.content.fluids.pipes.*;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.fluids.transfer.FluidManipulationBehaviour.BottomlessFluidMode;
import com.simibubi.create.foundation.fluid.FluidStack;
import java.util.*;
import net.createmod.catnip.math.BlockFace;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.*;

/** Actual source fluid custody, abort/commit, device identity and full native pitch collection. UNRUN. */
public final class GameplayFluidAdvancementGameTests {
    private static final BlockPos P=new BlockPos(4,5,4);
    private static OpenEndedPipe pipe(GameTestHelper h,ServerPlayer player){h.setBlock(P,AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));GameplayAdvancementGameTests.placed(h,P,player);var pipe=new OpenEndedPipe(new BlockFace(h.absolutePos(P),Direction.EAST));pipe.manageSource(h.getLevel(),h.getBlockEntity(P,FluidPipeBlockEntity.class));return pipe;}
    @GameTest public void actualWaterSourceAbortRetainsWorldAndCommitAwardsExactPipeOwner(GameTestHelper h){
        try(var owner=GameplayAdvancementGameTests.join(h);var observer=GameplayAdvancementGameTests.join(h)){
            var pipe=pipe(h,owner.player());h.setBlock(P.east(),Blocks.WATER);var water=FluidVariant.of(Fluids.WATER);
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pipe.provideHandler().extract(water,FluidConstants.BUCKET,tx),FluidConstants.BUCKET,"Real open end tentatively reserves one actual source bucket");}
            h.assertTrue(h.getBlockState(P.east()).getFluidState().isSource()&&!GameplayAdvancementGameTests.done(owner.player(),"water_supply"),"Outer abort preserves actual source water and original owner progress");
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pipe.provideHandler().extract(water,FluidConstants.BUCKET,tx),FluidConstants.BUCKET,"Original retained world bucket can actually commit once");tx.commit();}
            h.assertTrue(!h.getBlockState(P.east()).getFluidState().isSource()&&GameplayAdvancementGameTests.done(owner.player(),"water_supply")&&!GameplayAdvancementGameTests.done(observer.player(),"water_supply"),"Committed native source removal awards exact actual pipe owner without nearby adoption");
        }h.succeed();
    }
    @GameTest public void genuineHoneyFiveHiveDrainsQuarterBucketOnlyOnCommittedNativeSourceEdit(GameTestHelper h){
        try(var owner=GameplayAdvancementGameTests.join(h)){
            var pipe=pipe(h,owner.player());h.setBlock(P.east(),Blocks.BEEHIVE.defaultBlockState().setValue(BlockStateProperties.LEVEL_HONEY,5));var honey=FluidVariant.of(AllFluids.HONEY);long amount=FluidConstants.BUCKET/4;
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pipe.provideHandler().extract(honey,amount,tx),amount,"Actual full hive offers original250mB in Fabric units");}
            h.assertTrue(h.getBlockState(P.east()).getValue(BlockStateProperties.LEVEL_HONEY)==5&&!GameplayAdvancementGameTests.done(owner.player(),"honey_drain"),"Aborted native hive extraction cannot emit source criterion");
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pipe.provideHandler().extract(honey,amount,tx),amount,"Same actual quarter bucket commits");tx.commit();}
            h.assertTrue(h.getBlockState(P.east()).getValue(BlockStateProperties.LEVEL_HONEY)==0&&GameplayAdvancementGameTests.done(owner.player(),"honey_drain"),"Native honey target really empties before exact pipe owner earns original event");
        }h.succeed();
    }
    @GameTest public void nativePipeCollisionAwardsSavedOwnerBeforeDestroyingItsRealDevice(GameTestHelper h){
        try(var owner=GameplayAdvancementGameTests.join(h);var observer=GameplayAdvancementGameTests.join(h)){
            pipe(h,owner.player());var actual=h.getBlockEntity(P,FluidPipeBlockEntity.class);FluidReactions.handlePipeFlowCollision(h.getLevel(),h.absolutePos(P),new FluidStack(FluidVariant.of(Fluids.WATER),81),new FluidStack(FluidVariant.of(Fluids.LAVA),81));h.assertTrue(actual.isRemoved()&&h.getBlockState(P).is(Blocks.COBBLESTONE)&&GameplayAdvancementGameTests.done(owner.player(),"cross_streams")&&!GameplayAdvancementGameTests.done(observer.player(),"cross_streams"),"Actual collision has consumed its old native pipe, preserving the pinned before-destruction owner event");
        }h.succeed();
    }
    private static void wrench(GameTestHelper h,ServerPlayer player){player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH));h.assertTrue(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)),Direction.UP,h.absolutePos(P),false))).consumesAction(),"Real native wrench route handles actual pipe conversion");}
    @GameTest public void sourceGlassPipeRequiresActualStartedWaterFlowBeforeNativeWrenchConversion(GameTestHelper h){
        try(var emptyUser=GameplayAdvancementGameTests.join(h);var wetUser=GameplayAdvancementGameTests.join(h)){
            h.setBlock(P,AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));wrench(h,emptyUser.player());h.assertTrue(h.getBlockState(P).is(AllBlocks.GLASS_FLUID_PIPE)&&!GameplayAdvancementGameTests.done(emptyUser.player(),"glass_pipe"),"Actual dry native pipe converts without pretending fluid was present");wrench(h,emptyUser.player());h.setBlock(P.east(),Blocks.WATER);
            var be=h.getBlockEntity(P,FluidPipeBlockEntity.class);var transport=be.transport();transport.initialize();var connection=transport.interfaces.get(Direction.EAST);connection.manageSource(h.getLevel(),be.getBlockPos(),be);connection.getPressure().set(true,32f);connection.manageFlows(h.getLevel(),be.getBlockPos(),FluidStack.EMPTY,fluid->true);h.assertTrue(connection.hasFlow(),"Native source detection starts a real water flow from the physical neighboring source");wrench(h,wetUser.player());h.assertTrue(h.getBlockState(P).is(AllBlocks.GLASS_FLUID_PIPE)&&GameplayAdvancementGameTests.done(wetUser.player(),"glass_pipe")&&!GameplayAdvancementGameTests.done(emptyUser.player(),"glass_pipe"),"Genuine flowing conversion awards actual interacting player only");
        }h.succeed();
    }
    private static HosePulleyBlockEntity hose(GameTestHelper h,ServerPlayer owner){
        h.setBlock(P,AllBlocks.HOSE_PULLEY);GameplayAdvancementGameTests.placed(h,P,owner);var device=h.getBlockEntity(P,HosePulleyBlockEntity.class);var saved=device.saveWithFullMetadata(h.getLevel().registryAccess());var offset=new net.minecraft.nbt.CompoundTag();offset.putFloat("Value",2);saved.put("Offset",offset);device.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),saved));return device;
    }
    @GameTest public void finiteActualHoseDrainAbortThenCommitReservesOneWorldBucketAndSourceOwner(GameTestHelper h){
        try(var owner=GameplayAdvancementGameTests.join(h)){
            var device=hose(h,owner.player());h.setBlock(P.below(2),Blocks.WATER);for(var side:Direction.values())if(side!=Direction.UP)h.setBlock(P.below(2).relative(side),Blocks.STONE);for(int i=0;i<8;i++)HosePulleyBlockEntity.tick(h.getLevel(),device.getBlockPos(),device.getBlockState(),device);
            try(var tx=Transaction.openOuter()){h.assertValueEqual(device.storage.extract(FluidVariant.of(Fluids.WATER),FluidConstants.BUCKET,tx),FluidConstants.BUCKET,"Actual native finite search found one source bucket");}
            h.assertTrue(h.getBlockState(P.below(2)).is(Blocks.WATER)&&!GameplayAdvancementGameTests.done(owner.player(),"hose_pulley"),"Aborted source finite drain retains world and original progress");
            try(var tx=Transaction.openOuter()){h.assertValueEqual(device.storage.extract(FluidVariant.of(Fluids.WATER),FluidConstants.BUCKET,tx),FluidConstants.BUCKET,"Original native finite source can really drain once");tx.commit();}
            h.assertTrue(h.getBlockState(P.below(2)).isAir()&&GameplayAdvancementGameTests.done(owner.player(),"hose_pulley"),"Real finite native source removal earns the ordinary source hose event");
        }h.succeed();
    }
    @GameTest public void genuineFiniteHoseFillAbortCannotAwardAndCommittedSourcePlacementDoes(GameTestHelper h){
        try(var owner=GameplayAdvancementGameTests.join(h)){
            var device=hose(h,owner.player());for(var side:Direction.values())if(side!=Direction.UP)h.setBlock(P.below(2).relative(side),Blocks.STONE);HosePulleyBlockEntity.tick(h.getLevel(),device.getBlockPos(),device.getBlockState(),device);
            try(var tx=Transaction.openOuter()){h.assertValueEqual(device.storage.insert(FluidVariant.of(Fluids.WATER),FluidConstants.BUCKET,tx),FluidConstants.BUCKET,"Native reservoir accepts one genuine finite incoming bucket tentatively");}
            h.assertTrue(h.getBlockState(P.below(2)).isAir()&&!GameplayAdvancementGameTests.done(owner.player(),"hose_pulley"),"Outer abort cannot publish source water or award actual Owner");
            try(var tx=Transaction.openOuter()){h.assertValueEqual(device.storage.insert(FluidVariant.of(Fluids.WATER),FluidConstants.BUCKET,tx),FluidConstants.BUCKET,"Same actual incoming bucket commits");tx.commit();}
            h.assertTrue(h.getBlockState(P.below(2)).getFluidState().isSource()&&GameplayAdvancementGameTests.done(owner.player(),"hose_pulley"),"Real native full source placement earns original hose criterion");
        }h.succeed();
    }
    @GameTest public void actualNoSourcePlacementHoseSettingStillEarnsPinnedCommittedOrdinaryEvent(GameTestHelper h){
        try(var owner=GameplayAdvancementGameTests.join(h)){var device=hose(h,owner.player());device.configure(new HosePulleySettings(128,10000,false,BottomlessFluidMode.ALLOW_BY_TAG,false));HosePulleyBlockEntity.tick(h.getLevel(),device.getBlockPos(),device.getBlockState(),device);try(var tx=Transaction.openOuter()){h.assertValueEqual(device.storage.insert(FluidVariant.of(Fluids.WATER),FluidConstants.BUCKET,tx),FluidConstants.BUCKET,"Native configured no-source path consumes genuine incoming bucket");tx.commit();}h.assertTrue(h.getBlockState(P.below(2)).isAir()&&GameplayAdvancementGameTests.done(owner.player(),"hose_pulley"),"Pinned no-placeSources success awards genuine source owner without inventing a world source");}h.succeed();
    }
    @GameTest(structure="create_test:reservoir") public void twelveActualDistinctAttachedWhistlePitchesAcrossFullTankEarnOnlyControllerOwner(GameTestHelper h){
        var base=new BlockPos(10,3,10);for(int x=0;x<3;x++)for(int z=0;z<3;z++)h.setBlock(base.offset(x,0,z),AllBlocks.FLUID_TANK);ConnectivityHandler.formMulti(h.getBlockEntity(base,FluidTankBlockEntity.class));var controller=h.getBlockEntity(base,FluidTankBlockEntity.class).getControllerBE();h.assertTrue(controller!=null&&controller.getWidth()==3&&controller.getHeight()==1,"Actual nine-tank native multiblock formed");
        try(var owner=GameplayAdvancementGameTests.join(h);var observer=GameplayAdvancementGameTests.join(h)){
            var controllerState=controller.getBlockState();controllerState.getBlock().setPlacedBy(h.getLevel(),controller.getBlockPos(),controllerState,owner.player(),new net.minecraft.world.item.ItemStack(controllerState.getBlock()));h.assertTrue(owner.player().getUUID().equals(controller.sourceAdvancements.uuid()),"Actual controller placement retains source owner before any whistle: owner="+controller.sourceAdvancements.uuid()+", current="+(h.getLevel().getBlockEntity(controller.getBlockPos())==controller)+", removed="+controller.isRemoved());var sides=List.of(Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST);int count=0;var pitches=new HashSet<Integer>();
            for(var side:sides)for(int offset=0;offset<3;offset++){
                var tank=side==Direction.NORTH?base.offset(offset,0,0):side==Direction.SOUTH?base.offset(offset,0,2):side==Direction.WEST?base.offset(0,0,offset):base.offset(2,0,offset);var pos=tank.relative(side);int pitch=count%4;var size=WhistleBlock.WhistleSize.values()[count/4];
                h.setBlock(pos,AllBlocks.STEAM_WHISTLE.defaultBlockState().setValue(WhistleBlock.WALL,true).setValue(WhistleBlock.FACING,side.getOpposite()).setValue(WhistleBlock.SIZE,size));if(pitch>0)h.setBlock(pos.above(),AllBlocks.STEAM_WHISTLE_EXTENSION.defaultBlockState().setValue(WhistleExtenderBlock.SIZE,size).setValue(WhistleExtenderBlock.SHAPE,pitch==1?WhistleExtenderBlock.WhistleExtenderShape.SINGLE:WhistleExtenderBlock.WhistleExtenderShape.DOUBLE));if(pitch==3)h.setBlock(pos.above(2),AllBlocks.STEAM_WHISTLE_EXTENSION.defaultBlockState().setValue(WhistleExtenderBlock.SIZE,size));var whistle=h.getBlockEntity(pos,WhistleBlockEntity.class);whistle.updatePitch();pitches.add(whistle.getPitchId());count++;if(count<12)h.assertFalse(GameplayAdvancementGameTests.done(owner.player(),"pipe_organ"),"Fewer than twelve real unique native pitches cannot earn organ");
            }
            h.assertValueEqual(pitches.size(),12,"Twelve actual distinct source pitch IDs: "+pitches);
            h.assertTrue(GameplayAdvancementGameTests.done(owner.player(),"pipe_organ"),"All twelve real pitches award actual controller owner: storedOwner="+controller.sourceAdvancements.uuid()+", current="+(h.getLevel().getBlockEntity(controller.getBlockPos())==controller)+", loaded="+controller.structureLoaded()+", pitches="+pitches);
            h.assertFalse(GameplayAdvancementGameTests.done(observer.player(),"pipe_organ"),"Actual nearby observer never inherits source organ award");
            h.assertTrue(owner.player().getUUID().equals(controller.sourceAdvancements.uuid()),"Original controller retains unmet steam criterion owner: owner="+controller.sourceAdvancements.uuid()+", steamDone="+GameplayAdvancementGameTests.done(owner.player(),"steam_engine_maxed"));
        }h.succeed();
    }
}
