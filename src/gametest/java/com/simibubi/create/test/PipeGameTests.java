package com.simibubi.create.test;
import com.simibubi.create.*;
import com.simibubi.create.content.fluids.*;
import com.simibubi.create.content.fluids.pipes.*;
import com.simibubi.create.content.fluids.pump.*;
import com.simibubi.create.content.fluids.tank.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

public class PipeGameTests {
    private static final BlockPos P=new BlockPos(4,3,4);
    private static final long B=FluidConstants.BUCKET;
    private static FluidTankBlockEntity tank(GameTestHelper h,BlockPos pos){h.setBlock(pos,AllBlocks.FLUID_TANK);return h.getBlockEntity(pos,FluidTankBlockEntity.class);}
    private static void fill(FluidTankBlockEntity tank,int buckets){try(var tx=Transaction.openOuter()){tank.storage().insert(FluidVariant.of(Fluids.WATER),buckets*B,tx);tx.commit();}}
    private static KineticBlockEntity drive(GameTestHelper h,int rpm){
        h.setBlock(P.above(),AllBlocks.COGWHEEL.defaultBlockState().setValue(CogWheelBlock.AXIS,Direction.Axis.X));
        h.setBlock(P.above().west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));
        var motor=h.getBlockEntity(P.above().west(),KineticBlockEntity.class);for(int speed=16;speed<Math.abs(rpm);speed*=2)motor.adjustGeneratedSpeed(false);if(rpm<0)motor.adjustGeneratedSpeed(true);return motor;
    }
    private static PumpBlockEntity pump(GameTestHelper h,Direction facing,int rpm){h.setBlock(P,AllBlocks.MECHANICAL_PUMP.defaultBlockState().setValue(PumpBlock.FACING,facing));if(rpm!=0)drive(h,rpm);return h.getBlockEntity(P,PumpBlockEntity.class);}
    @GameTest(maxTicks=100)
    public void directlyAdjacentTanksTransferAtHalfRpmMillibuckets(GameTestHelper h){
        var source=tank(h,P.west());var target=tank(h,P.east());fill(source,4);var pump=pump(h,Direction.EAST,256);
        h.runAfterDelay(20,()->{h.assertTrue(pump.getSpeed()!=0,"Cogwheel drives pump");h.assertValueEqual(pump.getStressImpact(),1024.0,"Pump stress four per RPM");h.assertTrue(target.getTankInventory().getFluidAmount()>0,"Water transferred");});
        h.succeedWhen(()->{h.assertValueEqual(target.getTankInventory().getFluidAmount(),4*B,"All four buckets delivered");h.assertValueEqual(source.getTankInventory().getFluidAmount(),0L,"Source empty");});
    }
    @GameTest(maxTicks=180)
    public void fluidTraversesPipesAndGlassBeforeReachingTank(GameTestHelper h){
        var source=tank(h,P.west(3));var target=tank(h,P.east(3));fill(source,3);
        for(int offset:new int[]{-2,-1,1,2})h.setBlock(P.offset(offset,0,0),offset==1?AllBlocks.GLASS_FLUID_PIPE.defaultBlockState().setValue(AxisPipeBlock.AXIS,Direction.Axis.X):AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));
        pump(h,Direction.EAST,256);
        h.succeedWhen(()->{h.assertValueEqual(target.getTankInventory().getFluidAmount(),3*B,"Three buckets through pressure and flow propagation");h.assertValueEqual(source.getTankInventory().getFluidAmount(),0L,"No retained volume in visual flows");});
    }
    @GameTest(maxTicks=65)
    public void stalledPumpDoesNotTransfer(GameTestHelper h){var source=tank(h,P.west());var target=tank(h,P.east());fill(source,2);pump(h,Direction.EAST,0);h.runAfterDelay(30,()->{h.assertValueEqual(target.getTankInventory().getFluidAmount(),0L,"No drive no pumping");h.assertValueEqual(source.getTankInventory().getFluidAmount(),2*B,"Input retained");h.succeed();});}
    @GameTest(maxTicks=90)
    public void negativeRotationDoesNotReverseArrowDirection(GameTestHelper h){var source=tank(h,P.west());var target=tank(h,P.east());fill(source,2);pump(h,Direction.EAST,-256);h.succeedWhen(()->h.assertValueEqual(target.getTankInventory().getFluidAmount(),2*B,"Arrow direction independent of RPM sign"));}
    @GameTest(maxTicks=90)
    public void oppositeArrowReversesSourceAndDestination(GameTestHelper h){var source=tank(h,P.east());var target=tank(h,P.west());fill(source,2);pump(h,Direction.WEST,256);h.succeedWhen(()->h.assertValueEqual(target.getTankInventory().getFluidAmount(),2*B,"Arrow faces output"));}
    @GameTest(maxTicks=80)
    public void fullDestinationDoesNotConsumeInput(GameTestHelper h){var source=tank(h,P.west());var target=tank(h,P.east());fill(source,3);fill(target,8);pump(h,Direction.EAST,256);h.runAfterDelay(40,()->{h.assertValueEqual(source.getTankInventory().getFluidAmount(),3*B,"Full output retains source");h.assertValueEqual(target.getTankInventory().getFluidAmount(),8*B,"Output unchanged");h.succeed();});}
    @GameTest(maxTicks=150)
    public void branchSplitsFlowBetweenTwoOutputsWithoutLoss(GameTestHelper h){
        var source=tank(h,P.west());fill(source,4);var first=tank(h,P.east(2).north(2));var second=tank(h,P.east(2).south(2));
        h.setBlock(P.east(),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));
        var junction=AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.Z).setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST),true);h.setBlock(P.east(2),junction);
        h.setBlock(P.east(2).north(),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.Z));h.setBlock(P.east(2).south(),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.Z));pump(h,Direction.EAST,256);
        h.succeedWhen(()->{h.assertValueEqual(first.getTankInventory().getFluidAmount()+second.getTankInventory().getFluidAmount(),4*B,"Both outputs receive entire source");h.assertTrue(Math.abs(first.getTankInventory().getFluidAmount()-second.getTankInventory().getFluidAmount())<=81,"Balanced branch split within one millibucket");});
    }
    @GameTest(maxTicks=120)
    public void partiallyFullOutputRedistributesRemainder(GameTestHelper h){
        var source=tank(h,P.west());fill(source,3);var first=tank(h,P.east(2).north());fill(first,8);var second=tank(h,P.east(2).south());
        h.setBlock(P.east(),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));h.setBlock(P.east(2),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.Z).setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST),true));pump(h,Direction.EAST,256);
        h.succeedWhen(()->{h.assertValueEqual(second.getTankInventory().getFluidAmount(),3*B,"Open branch receives all unused share");h.assertValueEqual(first.getTankInventory().getFluidAmount(),8*B,"Full branch unchanged");});
    }
    @GameTest(maxTicks=120)
    public void replacingRemovedPipeRestoresTransfer(GameTestHelper h){
        var source=tank(h,P.west());fill(source,5);var target=tank(h,P.east(3));h.setBlock(P.east(),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));h.setBlock(P.east(2),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));pump(h,Direction.EAST,128);
        final long[] before={0};h.runAfterDelay(30,()->h.setBlock(P.east(2),Blocks.STONE));
        h.runAfterDelay(40,()->before[0]=target.getTankInventory().getFluidAmount());
        h.runAfterDelay(55,()->{h.assertValueEqual(target.getTankInventory().getFluidAmount(),before[0],"Blocked pipe stops transfer");h.setBlock(P.east(2),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));});
        h.succeedWhen(()->{h.assertTrue(h.getTick()>55,"Wait for reconnect");h.assertValueEqual(target.getTankInventory().getFluidAmount(),5*B,"Reconnected network finishes");});
    }
    @GameTest(maxTicks=140)
    public void pumpAndPipeReloadRebuildNetworkWithoutDuplicatingFluid(GameTestHelper h){
        var source=tank(h,P.west(2));fill(source,4);var target=tank(h,P.east(2));h.setBlock(P.west(),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));h.setBlock(P.east(),AllBlocks.GLASS_FLUID_PIPE.defaultBlockState().setValue(AxisPipeBlock.AXIS,Direction.Axis.X));pump(h,Direction.EAST,128);
        h.runAfterDelay(30,()->{for(var pos:java.util.List.of(P.west(),P,P.east())){var be=h.getLevel().getBlockEntity(h.absolutePos(pos));var loaded=net.minecraft.world.level.block.entity.BlockEntity.loadStatic(be.getBlockPos(),be.getBlockState(),be.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.getLevel().removeBlockEntity(be.getBlockPos());h.getLevel().setBlockEntity(loaded);}});
        h.succeedWhen(()->{h.assertTrue(h.getTick()>30,"Wait for reload");h.assertValueEqual(target.getTankInventory().getFluidAmount(),4*B,"Exactly four buckets after reload");h.assertValueEqual(source.getTankInventory().getFluidAmount(),0L,"Source empty once");});
    }
    @GameTest
    public void openEndSimulationAndAbortLeaveWorldUntouched(GameTestHelper h){
        var output=P.east();h.setBlock(output,Blocks.WATER);var end=new OpenEndedPipe(new net.createmod.catnip.math.BlockFace(h.absolutePos(P),Direction.EAST));end.manageSource(h.getLevel(),null);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(end.provideHandler().extract(FluidVariant.of(Fluids.WATER),B/4,tx),B/4,"Drain quarter bucket simulated");h.assertBlockPresent(Blocks.WATER,output);}
        h.assertBlockPresent(Blocks.WATER,output);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(end.provideHandler().extract(FluidVariant.of(Fluids.WATER),B/4,tx),B/4,"Drain quarter bucket committed");tx.commit();}
        h.assertFalse(h.getBlockState(output).getFluidState().isSource(),"World source removed after commit");
        try(var tx=Transaction.openOuter()){h.assertValueEqual(end.provideHandler().extract(FluidVariant.of(Fluids.WATER),B,tx),B*3/4,"Remaining three quarters buffered");tx.commit();}h.succeed();
    }
    @GameTest
    public void openEndPlacementIsAtomicAndNeedsOneBucket(GameTestHelper h){
        var end=new OpenEndedPipe(new net.createmod.catnip.math.BlockFace(h.absolutePos(P),Direction.EAST));end.manageSource(h.getLevel(),null);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(end.provideHandler().insert(FluidVariant.of(Fluids.WATER),B,tx),B,"Simulated full bucket");h.assertBlockPresent(Blocks.AIR,P.east());}
        h.assertBlockPresent(Blocks.AIR,P.east());
        try(var tx=Transaction.openOuter()){end.provideHandler().insert(FluidVariant.of(Fluids.WATER),B/2,tx);tx.commit();}h.assertBlockPresent(Blocks.AIR,P.east());
        try(var tx=Transaction.openOuter()){end.provideHandler().insert(FluidVariant.of(Fluids.WATER),B/2,tx);tx.commit();}h.assertTrue(h.getBlockState(P.east()).getFluidState().isSource(),"Two halves place one source");h.succeed();
    }
    @GameTest
    public void twoOpenEndsCannotConsumeSameWorldSourceInOneTransaction(GameTestHelper h){
        var worldPos=h.absolutePos(P);h.setBlock(P,Blocks.WATER);var first=new OpenEndedPipe(new net.createmod.catnip.math.BlockFace(worldPos.west(),Direction.EAST));var second=new OpenEndedPipe(new net.createmod.catnip.math.BlockFace(worldPos.east(),Direction.WEST));first.manageSource(h.getLevel(),null);second.manageSource(h.getLevel(),null);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(first.provideHandler().extract(FluidVariant.of(Fluids.WATER),B,tx),B,"First pipe reserves source");h.assertValueEqual(second.provideHandler().extract(FluidVariant.of(Fluids.WATER),B,tx),0L,"Second pipe cannot reserve it again");}
        h.assertTrue(h.getBlockState(P).getFluidState().isSource(),"Outer abort restores source reservation");h.succeed();
    }
    @GameTest(maxTicks=130)
    public void pumpDrainsWorldSourceIntoTank(GameTestHelper h){
        h.setBlock(P.west().below(),Blocks.STONE);h.setBlock(P.west(),Blocks.WATER);var target=tank(h,P.east());pump(h,Direction.EAST,256);
        h.succeedWhen(()->{h.assertValueEqual(target.getTankInventory().getFluidAmount(),B,"World source pumped into tank");h.assertFalse(h.getBlockState(P.west()).getFluidState().isSource(),"Original source consumed");});
    }
    @GameTest(maxTicks=130)
    public void pumpPlacesSourceBlockAtOpenOutput(GameTestHelper h){
        var source=tank(h,P.west());fill(source,1);h.setBlock(P.east().below(),Blocks.STONE);pump(h,Direction.EAST,256);
        h.succeedWhen(()->{h.assertTrue(h.getBlockState(P.east()).getFluidState().isSource(),"Source placed from open pipe");h.assertValueEqual(source.getTankInventory().getFluidAmount(),0L,"Bucket consumed once");});
    }
    @GameTest
    public void cauldronDrainAndWaterloggingUseWorldTransactions(GameTestHelper h){
        h.setBlock(P.east(),Blocks.LAVA_CAULDRON);var end=new OpenEndedPipe(new net.createmod.catnip.math.BlockFace(h.absolutePos(P),Direction.EAST));end.manageSource(h.getLevel(),null);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(end.provideHandler().extract(FluidVariant.of(Fluids.LAVA),B,tx),B,"Cauldron full bucket");tx.commit();}h.assertBlockPresent(Blocks.CAULDRON,P.east());
        h.setBlock(P.east(),Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true));
        try(var tx=Transaction.openOuter()){h.assertValueEqual(end.provideHandler().extract(FluidVariant.of(Fluids.WATER),B,tx),B,"Waterlogged block source");tx.commit();}h.assertFalse(h.getBlockState(P.east()).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED),"Slab retained and drained");h.succeed();
    }
    @GameTest(maxTicks=80)
    public void pumpRateMatchesOriginalMillibucketRounding(GameTestHelper h){
        var source=tank(h,P.west());var target=tank(h,P.east());fill(source,8);pump(h,Direction.EAST,64);final long[] at={0};
        h.runAfterDelay(30,()->at[0]=target.getTankInventory().getFluidAmount());h.runAfterDelay(40,()->{h.assertValueEqual(target.getTankInventory().getFluidAmount()-at[0],10*32*81L,"64 RPM moves 32 mB per tick after propagation");h.succeed();});
    }
    @GameTest(maxTicks=80,structure="create_test:long_pipe")
    public void pressureStopsAtRangeAndDoesNotReachSeventeenthPipe(GameTestHelper h){
        var source=tank(h,P.west());fill(source,8);var target=tank(h,P.east(18));for(int x=1;x<=17;x++)h.setBlock(P.east(x),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));pump(h,Direction.EAST,256);
        h.runAfterDelay(40,()->{h.assertValueEqual(target.getTankInventory().getFluidAmount(),0L,"Seventeen pipes exceed range");h.assertValueEqual(h.getBlockEntity(P.east(17),FluidPipeBlockEntity.class).transport().getConnection(Direction.EAST).getOutwardPressure(),0f,"No pressure beyond configured range");h.succeed();});
    }
    @GameTest(maxTicks=150,structure="create_test:long_pipe")
    public void alignedPumpsExtendRangeAndOpposedPumpBlocksIt(GameTestHelper h){
        var source=tank(h,P.west());fill(source,5);var second=P.east(12);var target=tank(h,P.east(15));for(int x=1;x<15;x++)if(x!=12)h.setBlock(P.east(x),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));pump(h,Direction.EAST,256);
        h.setBlock(second,AllBlocks.MECHANICAL_PUMP.defaultBlockState().setValue(PumpBlock.FACING,Direction.WEST));h.setBlock(second.above(),AllBlocks.COGWHEEL.defaultBlockState().setValue(CogWheelBlock.AXIS,Direction.Axis.X));h.setBlock(second.above().west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));var motor=h.getBlockEntity(second.above().west(),KineticBlockEntity.class);for(int i=0;i<4;i++)motor.adjustGeneratedSpeed(false);
        h.runAfterDelay(45,()->{h.assertValueEqual(target.getTankInventory().getFluidAmount(),0L,"Facing pumps do not push through one another");h.setBlock(second,AllBlocks.MECHANICAL_PUMP.defaultBlockState().setValue(PumpBlock.FACING,Direction.EAST));});
        h.succeedWhen(()->{h.assertTrue(h.getTick()>45,"Wait for alignment");h.assertValueEqual(target.getTankInventory().getFluidAmount(),5*B,"Aligned pumps extend working network");});
    }
    @GameTest(maxTicks=90)
    public void incompatibleInboundFluidsDestroyJunctionAndFormCobblestone(GameTestHelper h){
        var junction=P.east(2);h.setBlock(junction.west(),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));h.setBlock(junction,AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.Z).setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST),true));
        for(var side:new Direction[]{Direction.NORTH,Direction.SOUTH}){
            var pos=junction.relative(side,2);h.setBlock(pos,AllBlocks.MECHANICAL_PUMP.defaultBlockState().setValue(PumpBlock.FACING,side.getOpposite()));h.setBlock(junction.relative(side),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.Z));var tank=tank(h,pos.relative(side));try(var tx=Transaction.openOuter()){tank.storage().insert(FluidVariant.of(side==Direction.NORTH?Fluids.WATER:Fluids.LAVA),B,tx);tx.commit();}
            h.setBlock(pos.above(),AllBlocks.COGWHEEL.defaultBlockState().setValue(CogWheelBlock.AXIS,Direction.Axis.Z));h.setBlock(pos.above().north(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.SOUTH));var motor=h.getBlockEntity(pos.above().north(),KineticBlockEntity.class);for(int i=0;i<4;i++)motor.adjustGeneratedSpeed(false);
        }

        h.succeedWhen(()->h.assertBlockPresent(Blocks.COBBLESTONE,junction));
    }
    @GameTest(maxTicks=100)
    public void fluidVariantComponentsArePreservedThroughPipes(GameTestHelper h){
        var source=tank(h,P.west());var target=tank(h,P.east(2));var named=FluidVariant.of(Fluids.WATER,net.minecraft.core.component.DataComponentPatch.builder().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Process water")).build());try(var tx=Transaction.openOuter()){source.storage().insert(named,2*B,tx);tx.commit();}
        h.setBlock(P.east(),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));pump(h,Direction.EAST,256);
        h.succeedWhen(()->{h.assertValueEqual(target.getTankInventory().getFluidAmount(),2*B,"Named fluid amount");h.assertValueEqual(target.getTankInventory().getResource(),named,"Fluid components retained");});
    }
    @GameTest
    public void nestedOpenEndAbortRestoresParentReservation(GameTestHelper h){
        h.setBlock(P.east(),Blocks.WATER);var end=new OpenEndedPipe(new net.createmod.catnip.math.BlockFace(h.absolutePos(P),Direction.EAST));end.manageSource(h.getLevel(),null);
        try(var outer=Transaction.openOuter()){
            h.assertValueEqual(end.provideHandler().extract(FluidVariant.of(Fluids.WATER),B/4,outer),B/4,"Parent quarter");
            try(var nested=outer.openNested()){h.assertValueEqual(end.provideHandler().extract(FluidVariant.of(Fluids.WATER),B/4,nested),B/4,"Nested quarter");}
            h.assertValueEqual(end.provideHandler().extract(FluidVariant.of(Fluids.WATER),B,outer),3*B/4,"Nested rollback restores remaining three quarters");outer.commit();
        }
        h.assertFalse(h.getBlockState(P.east()).getFluidState().isSource(),"Exactly one source consumed");h.succeed();
    }
    @GameTest(maxTicks=100)
    public void wrenchFlipsPumpAndPipeConversionsPreserveFlow(GameTestHelper h){
        var source=tank(h,P.west(2));var target=tank(h,P.east(2));fill(source,4);h.setBlock(P.west(),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));h.setBlock(P.east(),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));pump(h,Direction.EAST,128);
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(AllItems.WRENCH));
        h.runAfterDelay(25,()->{var pos=h.absolutePos(P.east());AllItems.WRENCH.useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false)));h.assertBlockPresent(AllBlocks.GLASS_FLUID_PIPE,P.east());});
        h.runAfterDelay(40,()->{var pos=h.absolutePos(P);AllItems.WRENCH.useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false)));h.assertValueEqual(h.getBlockState(P).getValue(PumpBlock.FACING),Direction.WEST,"Wrench reverses arrow");});
        h.runAfterDelay(75,()->{h.assertTrue(source.getTankInventory().getFluidAmount()>target.getTankInventory().getFluidAmount(),"Reversed pump returns water");h.assertValueEqual(source.getTankInventory().getFluidAmount()+target.getTankInventory().getFluidAmount(),4*B,"No loss during conversion and reversal");h.succeed();});
    }
    @GameTest
    public void openEndBufferPersistsWithoutRecreatingWorldSource(GameTestHelper h){
        h.setBlock(P.east(),Blocks.WATER);var end=new OpenEndedPipe(new net.createmod.catnip.math.BlockFace(h.absolutePos(P),Direction.EAST));end.manageSource(h.getLevel(),null);
        try(var tx=Transaction.openOuter()){end.provideHandler().extract(FluidVariant.of(Fluids.WATER),B/4,tx);tx.commit();}
        var out=net.minecraft.world.level.storage.TagValueOutput.createWithoutContext(net.minecraft.util.ProblemReporter.DISCARDING);end.write(out);
        var input=net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,h.getLevel().registryAccess(),out.buildResult());var loaded=new OpenEndedPipe(new net.createmod.catnip.math.BlockFace(h.absolutePos(P),Direction.EAST));loaded.read(input);loaded.manageSource(h.getLevel(),null);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(loaded.provideHandler().extract(FluidVariant.of(Fluids.WATER),B,tx),3*B/4,"Remaining buffer survived reload");tx.commit();}h.succeed();
    }
    @GameTest(maxTicks=75)
    public void encasingAndRemovingCasingKeepConnectedFacesAndVolume(GameTestHelper h){
        var source=tank(h,P.west());fill(source,4);var target=tank(h,P.east(2));h.setBlock(P.east(),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));pump(h,Direction.EAST,128);var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        h.runAfterDelay(25,()->{var pos=h.absolutePos(P.east());var casing=new net.minecraft.world.item.ItemStack(AllBlocks.COPPER_CASING);h.getBlockState(P.east()).useItemOn(casing,h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false));h.assertBlockPresent(AllBlocks.ENCASED_FLUID_PIPE,P.east());h.assertValueEqual(h.getBlockEntity(P.east(),FluidPipeBlockEntity.class).getType(),AllBlockEntityTypes.ENCASED_FLUID_PIPE,"Original encased pipe BE id");});
        h.runAfterDelay(40,()->{var pos=h.absolutePos(P.east());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(AllItems.WRENCH));AllItems.WRENCH.useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false)));h.assertBlockPresent(AllBlocks.FLUID_PIPE,P.east());});
        h.runAfterDelay(65,()->{h.assertValueEqual(source.getTankInventory().getFluidAmount()+target.getTankInventory().getFluidAmount(),4*B,"Volume retained while casing changed");h.assertTrue(target.getTankInventory().getFluidAmount()>0,"Network continues working");h.succeed();});
    }
    @GameTest(dimension="minecraft:the_nether")
    public void waterEvaporatesAtOpenEndInNether(GameTestHelper h){
        var end=new OpenEndedPipe(new net.createmod.catnip.math.BlockFace(h.absolutePos(P),Direction.EAST));end.manageSource(h.getLevel(),null);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(end.provideHandler().insert(FluidVariant.of(Fluids.WATER),B,tx),B,"Bucket consumed in warm environment");tx.commit();}
        h.assertBlockPresent(Blocks.AIR,P.east());h.succeed();
    }
}
