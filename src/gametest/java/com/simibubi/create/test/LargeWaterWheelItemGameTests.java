package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.waterwheel.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;

/** Actual registered survival player/native BlockItem contexts and exact finite item/structure ownership. Authored5 UNRUN. */
public final class LargeWaterWheelItemGameTests {
    private static final BlockPos P=new BlockPos(6,8,6);
    private static final class Case implements AutoCloseable {
        final GameTestHelper h;final NativeSurvivalTestPlayer session;final ItemStack stack;
        Case(GameTestHelper h){this.h=h;session=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(3,0,0));stack=new ItemStack(AllBlocks.LARGE_WATER_WHEEL,4);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Four original survival large wheels"));session.player().setItemInHand(InteractionHand.MAIN_HAND,stack);session.player().setYRot(90);session.player().setYHeadRot(90);session.player().setXRot(0);h.assertTrue(Direction.orderedByNearest(session.player())[0]==Direction.WEST,"Actual native LivingEntity head view is WEST before the source placement context reads its camera direction");h.assertTrue(stack.getItem() instanceof LargeWaterWheelBlockItem,"Actual native registered large-wheel item has the source placement policy");}
        UseOnContext use(BlockPos support,Direction face){return new UseOnContext(session.player(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(h.absolutePos(support)).add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5),face,h.absolutePos(support),false));}
        @Override public void close(){session.close();}
    }
    private static void structure(GameTestHelper h,BlockPos centre,Direction.Axis axis){
        var absolute=h.absolutePos(centre);var cells=LargeWaterWheelBlock.structure(absolute,axis);h.assertTrue(cells.size()==8,"Actual source structure has exactly eight unique cells");
        for(var entry:cells.entrySet()){var state=h.getLevel().getBlockState(entry.getKey());h.assertTrue(state.is(AllBlocks.WATER_WHEEL_STRUCTURAL)&&state.getValue(WaterWheelStructuralBlock.FACING)==entry.getValue()&&absolute.equals(WaterWheelStructuralBlock.master(h.getLevel(),entry.getKey(),state)),"Every native structural cell resolves exactly its real paid centre");}
    }
    @GameTest(structure="create_test:reservoir",maxTicks=40)public void firstNativeSuccessDebitsExactlyOneWithoutAnotherPlacement(GameTestHelper h){
        h.setBlock(P.below(),Blocks.STONE);ItemStack stock;
        try(var test=new Case(h)){stock=test.stack;test.session.player().setXRot(80);var use=test.use(P.below(),Direction.UP);var context=new BlockPlaceContext(use);h.assertTrue(context.getClickedPos().equals(h.absolutePos(P))&&AllBlocks.LARGE_WATER_WHEEL.getAxisForPlacement(context)==Direction.Axis.Y,"Actual native context resolves the first source centre and camera-axis policy");h.assertTrue(stock.getItem().useOn(use).consumesAction(),"First actual native BlockItem placement succeeds");h.assertTrue(stock.getCount()==3&&test.session.player().getMainHandItem()==stock&&h.getBlockState(P).is(AllBlocks.LARGE_WATER_WHEEL)&&h.getBlockState(P).getValue(LargeWaterWheelBlock.AXIS)==Direction.Axis.Y&&h.getBlockState(P.above()).isAir(),"First source success consumes one original item and never attempts another centre");}
        h.succeedWhen(()->{structure(h,P,Direction.Axis.Y);h.assertTrue(stock.getCount()==3&&h.getBlockState(P.above()).isAir(),"Exactly one native paid wheel remains after real structural ticks");});
    }
    @GameTest(structure="create_test:reservoir",maxTicks=40)public void failedFirstPlaneRetriesOneCellOnlyAndPaysExactlyOne(GameTestHelper h){
        h.setBlock(P.below(),Blocks.STONE);ItemStack stock;
        try(var test=new Case(h)){stock=test.stack;var use=test.use(P.below(),Direction.UP);var context=new BlockPlaceContext(use);h.assertTrue(AllBlocks.LARGE_WATER_WHEEL.getAxisForPlacement(context)==Direction.Axis.X&&AllBlocks.LARGE_WATER_WHEEL.getStateForPlacement(context)==null,"Actual original vertical footprint is blocked by the clicked source support");h.assertTrue(stock.getItem().useOn(use).consumesAction(),"Source unequal-axis failure reaches the real native adjusted-context retry");h.assertTrue(stock.getCount()==3&&test.session.player().getMainHandItem()==stock&&h.getBlockState(P.above()).is(AllBlocks.LARGE_WATER_WHEEL)&&h.getBlockState(P.above()).getValue(LargeWaterWheelBlock.AXIS)==Direction.Axis.X&&h.getBlockState(P.below()).is(Blocks.STONE),"Only the real shifted centre owns one original paid item; source support survives");}
        h.succeedWhen(()->{structure(h,P.above(),Direction.Axis.X);h.assertTrue(stock.getCount()==3&&!h.getBlockState(P).is(AllBlocks.LARGE_WATER_WHEEL),"Initial target becomes only the actual lower structural cell of the single paid retry centre");});
    }
    @GameTest(structure="create_test:reservoir",maxTicks=40)public void bothRealFootprintsBlockedRetainExactBarrelCargoAndAllSourceWheels(GameTestHelper h){
        h.setBlock(P.below(),Blocks.STONE);var obstacle=P.above().south();h.setBlock(obstacle,Blocks.BARREL);var barrel=h.getBlockEntity(obstacle,BarrelBlockEntity.class);var cargo=new ItemStack(Items.DIAMOND,11);cargo.set(DataComponents.CUSTOM_NAME,Component.literal("Eleven original blocked-wheel diamonds"));barrel.setItem(0,cargo);
        try(var test=new Case(h)){h.assertTrue(test.stack.getItem().useOn(test.use(P.below(),Direction.UP))==InteractionResult.FAIL,"Both actual native first and retry footprints refuse the physical source obstruction");h.assertTrue(test.stack.getCount()==4&&test.session.player().getMainHandItem()==test.stack&&h.getBlockState(P).isAir()&&h.getBlockState(P.above()).isAir()&&h.getBlockEntity(obstacle,BarrelBlockEntity.class)==barrel&&barrel.getItem(0)==cargo&&cargo.getCount()==11,"Rejected native placement preserves exact original item/cargo identities and both empty centres");}
        h.runAfterDelay(3,()->{h.assertTrue(h.getBlockEntity(obstacle,BarrelBlockEntity.class)==barrel&&barrel.getItem(0)==cargo&&h.getBlockState(P).isAir()&&h.getBlockState(P.above()).isAir(),"No delayed native structural callback writes after failure");h.succeed();});
    }
    @GameTest(structure="create_test:reservoir")public void equalClickedAndPlacementAxesNeverRetryEvenIfNextPlaneIsFree(GameTestHelper h){
        h.setBlock(P.west(),Blocks.STONE);h.setBlock(P.below(),Blocks.STONE);
        try(var test=new Case(h)){var use=test.use(P.west(),Direction.EAST);var context=new BlockPlaceContext(use);h.assertTrue(context.getClickedPos().equals(h.absolutePos(P))&&AllBlocks.LARGE_WATER_WHEEL.getAxisForPlacement(context)==Direction.Axis.X&&AllBlocks.LARGE_WATER_WHEEL.getStateForPlacement(context)==null&&AllBlocks.LARGE_WATER_WHEEL.getStateForPlacement(BlockPlaceContext.at(context,h.absolutePos(P.east()),Direction.EAST))!=null,"Actual first plane is blocked while a real next plane exists along the same source axis");h.assertTrue(test.stack.getItem().useOn(use)==InteractionResult.FAIL&&test.stack.getCount()==4&&h.getBlockState(P).isAir()&&h.getBlockState(P.east()).isAir(),"Equal-axis source failure cannot manufacture an unauthorized retry or consume an item");}h.succeed();
    }
    @GameTest(structure="create_test:reservoir")public void sharedNativePreferredShaftCameraSneakAndConflictAxisPolicy(GameTestHelper h){
        h.setBlock(P.below(),Blocks.STONE);
        try(var test=new Case(h)){var context=new BlockPlaceContext(test.use(P.below(),Direction.UP));h.assertTrue(AllBlocks.LARGE_WATER_WHEEL.getAxisForPlacement(context)==Direction.Axis.X,"No shaft uses actual source camera axis");h.setBlock(P.north(),AllBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS,Direction.Axis.Z));h.assertTrue(AllBlocks.LARGE_WATER_WHEEL.getAxisForPlacement(context)==Direction.Axis.Z,"Actual neighboring native shaft overrides camera axis");test.session.player().setShiftKeyDown(true);h.assertTrue(AllBlocks.LARGE_WATER_WHEEL.getAxisForPlacement(context)==Direction.Axis.Y,"Actual source sneak with a preferred shaft chooses clicked-face axis");h.setBlock(P.east(),AllBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS,Direction.Axis.X));h.assertTrue(AllBlocks.LARGE_WATER_WHEEL.getAxisForPlacement(context)==Direction.Axis.X&&test.stack.getCount()==4,"Real conflicting shaft axes fall back to actual camera without inventing stock or requiring an accepted footprint");}h.succeed();
    }
}
