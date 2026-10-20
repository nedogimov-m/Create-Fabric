package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.kinetics.transmission.sequencer.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Real registered native blocks, solver provenance, source clocks and physical bearing limits. UNRUN. */
public class SequencedGearshiftGameTests {
    private static final BlockPos S=new BlockPos(4,4,4),B=S.east();
    private static SequencedGearshiftBlockEntity seq(GameTestHelper h){return h.getBlockEntity(S,SequencedGearshiftBlockEntity.class);}
    private static MechanicalBearingBlockEntity bearing(GameTestHelper h){return h.getBlockEntity(B,MechanicalBearingBlockEntity.class);}
    private static KineticBlockEntity kinetic(GameTestHelper h,BlockPos pos){return h.getBlockEntity(pos,KineticBlockEntity.class);}
    private static Instruction angle(int value,InstructionSpeedModifiers modifier){return new Instruction(SequencerInstructions.TURN_ANGLE,modifier,value);}
    private static Instruction await(){return new Instruction(SequencerInstructions.AWAIT);}
    private static Instruction end(){return new Instruction(SequencerInstructions.END);}
    private static void drive(GameTestHelper h,Direction output,int speed){
        var axis=output.getAxis();var state=AllBlocks.SEQUENCED_GEARSHIFT.defaultBlockState().setValue(SequencedGearshiftBlock.VERTICAL,axis==Direction.Axis.Y)
            .setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS,axis==Direction.Axis.Y?Direction.Axis.X:axis);
        h.setBlock(S,state);h.setBlock(S.relative(output.getOpposite()),AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,axis));
        h.setBlock(S.relative(output.getOpposite(),2),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,output));
        kinetic(h,S.relative(output.getOpposite(),2)).setGeneratedSpeed(speed);
        h.setBlock(S.relative(output),AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,axis));
    }
    private static void machine(GameTestHelper h,int speed){
        drive(h,Direction.EAST,speed);h.setBlock(B,AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.EAST));
        bearing(h).setMovementMode(IControlContraption.RotationMode.ROTATE_NEVER_PLACE);h.setBlock(B.east(),Blocks.BARREL);
    }
    private static void power(GameTestHelper h,boolean powered){var axis=((IRotate)h.getBlockState(S).getBlock()).getRotationAxis(h.getBlockState(S));h.setBlock(S.relative(axis==Direction.Axis.Z?Direction.EAST:Direction.SOUTH),powered?Blocks.REDSTONE_BLOCK:Blocks.AIR);}
    private static void configureAndPower(GameTestHelper h,List<Instruction> instructions){seq(h).configure(instructions);power(h,true);}
    private static void verifyDirection(GameTestHelper h,Direction output){
        drive(h,output,32);h.runAfterDelay(5,()->{
            h.assertTrue(seq(h).getSpeed()==32*output.getAxisDirection().getStep()&&seq(h).getPortSpeed(output)==0,"Idle input remains powered while output disconnected");
            configureAndPower(h,List.of(angle(360,InstructionSpeedModifiers.FORWARD_FAST),await()));
        });
        h.runAfterDelay(11,()->{
            int input=32*output.getAxisDirection().getStep();h.assertTrue(seq(h).getSourceFacing()==output.getOpposite()&&seq(h).getSpeed()==input&&seq(h).getTheoreticalSpeed()==input,"Actual source port, never faster output, is primary");
            h.assertTrue(seq(h).getPortSpeed(output)==2*input&&kinetic(h,S.relative(output)).getSpeed()==2*input,"Real doubled output in this axis");h.succeed();
        });
    }
    @GameTest(maxTicks=30)public void sourceFromWest(GameTestHelper h){verifyDirection(h,Direction.EAST);}
    @GameTest(maxTicks=30)public void sourceFromEast(GameTestHelper h){verifyDirection(h,Direction.WEST);}
    @GameTest(maxTicks=30)public void sourceFromBelow(GameTestHelper h){verifyDirection(h,Direction.UP);}
    @GameTest(maxTicks=30)public void sourceFromAbove(GameTestHelper h){verifyDirection(h,Direction.DOWN);}
    @GameTest(maxTicks=30)public void sourceFromNorth(GameTestHelper h){verifyDirection(h,Direction.SOUTH);}
    @GameTest(maxTicks=30)public void sourceFromSouth(GameTestHelper h){verifyDirection(h,Direction.NORTH);}

    @GameTest public void nativeDefaultStateAndSaveBeforeSetLevel(GameTestHelper h){
        h.setBlock(S,AllBlocks.SEQUENCED_GEARSHIFT);var saved=seq(h).saveWithFullMetadata(h.getLevel().registryAccess());
        var restored=(SequencedGearshiftBlockEntity)BlockEntity.loadStatic(h.absolutePos(S),h.getBlockState(S),saved,h.getLevel().registryAccess());
        h.assertTrue(restored!=null&&restored.isIdle()&&restored.getInstructions().equals(Instruction.createDefault())&&h.getBlockState(S).getValue(SequencedGearshiftBlock.STATE)==0,"Default90/END and idle decode without level assignment");h.succeed();
    }
    @GameTest public void instructionCodecBoundsListsAndEnumsBeforeWorldLookup(GameTestHelper h){
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());boolean rejected=false;
        try{buffer.writeVarInt(Integer.MAX_VALUE);try{Instruction.LIST_STREAM_CODEC.decode(buffer);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,"Count rejected before allocating list");buffer.clear();buffer.writeVarInt(999);buffer.writeVarInt(1);buffer.writeVarInt(90);rejected=false;try{Instruction.STREAM_CODEC.decode(buffer);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,"Invalid enum rejected at native decode");}finally{buffer.release();}h.succeed();
    }
    @GameTest public void allFiveStepsRoundTripThroughPersistentAndNativePacketCodecs(GameTestHelper h){
        var instructions=List.of(angle(123,InstructionSpeedModifiers.BACK_FAST),new Instruction(SequencerInstructions.TURN_DISTANCE,17),new Instruction(SequencerInstructions.DELAY,51),await(),end());
        var encoded=Instruction.LIST_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,instructions).getOrThrow();h.assertTrue(Instruction.LIST_CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,encoded).getOrThrow().equals(instructions),"Source Type/Modifier/Value keys preserve steps");
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());try{ConfigureSequencedGearshiftPacket.STREAM_CODEC.encode(buffer,new ConfigureSequencedGearshiftPacket(h.absolutePos(S),instructions));var decoded=ConfigureSequencedGearshiftPacket.STREAM_CODEC.decode(buffer);h.assertTrue(decoded.instructions().equals(instructions)&&decoded.pos().equals(h.absolutePos(S))&&buffer.readableBytes()==0,"Actual bounded native payload roundtrip");}finally{buffer.release();}h.succeed();
    }
    @GameTest public void sourceDurationAndSignedUnmodifiedProgressAreDistinct(GameTestHelper h){
        var fast=angle(90,InstructionSpeedModifiers.FORWARD_FAST);h.assertValueEqual(fast.getDuration(0,16),12,"ceil90/9.6 plus2");h.assertValueEqual(fast.getTickProgress(16),4.8f,"Raw input progress, without2x");h.assertValueEqual(fast.getTickProgress(-16),-4.8f,"Signed input progress preserved");
        h.assertValueEqual(angle(360,InstructionSpeedModifiers.FORWARD).getDuration(0,3),402,"Native float angular conversion preserves the exact400tick boundary before plus2");
        h.assertValueEqual(angle(90,InstructionSpeedModifiers.BACK_FAST).getDuration(24,32),6,"Source modifier absolute duration after partial progress");h.assertValueEqual(await().getDuration(0,16),-1,"AWAIT has no clock");h.succeed();
    }
    @GameTest(maxTicks=40)public void heldPowerKeepsEndStateLatchedAndRequiresLowHighForRestart(GameTestHelper h){
        drive(h,Direction.EAST,16);var epoch=new long[1];
        h.runAfterDelay(5,()->configureAndPower(h,List.of(new Instruction(SequencerInstructions.DELAY,2),end())));
        h.runAfterDelay(13,()->{h.assertTrue(seq(h).isIdle()&&h.getBlockState(S).getValue(SequencedGearshiftBlock.STATE)==1,"END under held power retains STATE1");epoch[0]=seq(h).saveWithoutMetadata(h.getLevel().registryAccess()).getLongOr("SequenceEpoch",0);});
        h.runAfterDelay(20,()->{h.assertTrue(seq(h).isIdle()&&seq(h).saveWithoutMetadata(h.getLevel().registryAccess()).getLongOr("SequenceEpoch",0)==epoch[0],"Held power never retriggers");power(h,false);});
        h.runAfterDelay(24,()->{h.assertValueEqual(h.getBlockState(S).getValue(SequencedGearshiftBlock.STATE),0,"Low clears source state latch");power(h,true);});
        h.runAfterDelay(32,()->{h.assertTrue(seq(h).saveWithoutMetadata(h.getLevel().registryAccess()).getLongOr("SequenceEpoch",0)>epoch[0],"New rising power really starts a new phase");h.succeed();});
    }
    @GameTest(maxTicks=50)public void awaitContinuesOnlyOnANewPhysicalRisingEdge(GameTestHelper h){
        drive(h,Direction.EAST,16);h.runAfterDelay(5,()->configureAndPower(h,List.of(await(),new Instruction(SequencerInstructions.DELAY,5),end())));
        h.runAfterDelay(20,()->{h.assertTrue(seq(h).getInstructionIndex()==0&&seq(h).getTimer()==0&&seq(h).getPortSpeed(Direction.EAST)==0,"Held input does not consume AWAIT");power(h,false);});
        h.runAfterDelay(24,()->{h.assertValueEqual(seq(h).getInstructionIndex(),0,"Falling edge cannot continue");power(h,true);});
        h.runAfterDelay(27,()->h.assertValueEqual(seq(h).getInstructionIndex(),1,"New rising edge enters DELAY"));
        h.runAfterDelay(38,()->{h.assertTrue(seq(h).isIdle()&&h.getBlockState(S).getValue(SequencedGearshiftBlock.STATE)==2,"END retains most recent nonzero source state");h.succeed();});
    }
    @GameTest(maxTicks=50)public void bearingClampsItsRealLastNonintegerStepExactlyAtNinety(GameTestHelper h){
        machine(h,16);h.runAfterDelay(5,()->configureAndPower(h,List.of(angle(90,InstructionSpeedModifiers.FORWARD),end())));
        h.runAfterDelay(36,()->{var owner=bearing(h).getMovedContraption();h.assertTrue(owner!=null&&bearing(h).isRunning()&&Math.abs(owner.getAngle(1)-90)<.001,"Real moving payload stops at90, not source timer overshoot");h.assertTrue(seq(h).isIdle()&&bearing(h).getSpeed()==0,"Sequence ended and output stopped");h.succeed();});
    }
    @GameTest(maxTicks=45)public void fastPhaseUsesInputSixteenAndClampsOutputThirtyTwo(GameTestHelper h){
        machine(h,16);h.runAfterDelay(5,()->configureAndPower(h,List.of(angle(90,InstructionSpeedModifiers.FORWARD_FAST),end())));
        h.runAfterDelay(10,()->{h.assertTrue(seq(h).getSpeed()==16&&bearing(h).getSpeed()==32&&seq(h).getInstructionDuration()==12,"2x duration uses actual input16");});
        h.runAfterDelay(28,()->{h.assertTrue(Math.abs(bearing(h).getMovedContraption().getAngle(1)-90)<.001,"2x does not overshoot target");h.succeed();});
    }
    @GameTest(maxTicks=80)public void twoIdenticalPhasesResetTheConsumerLimitWithoutAnRpmChange(GameTestHelper h){
        machine(h,16);var firstPhase=new SequencePhase[1];
        h.runAfterDelay(5,()->configureAndPower(h,List.of(angle(90,InstructionSpeedModifiers.FORWARD),angle(90,InstructionSpeedModifiers.FORWARD),end())));
        h.runAfterDelay(12,()->firstPhase[0]=bearing(h).getSequencePhase());
        h.runAfterDelay(34,()->h.assertTrue(bearing(h).getSequencePhase()!=null&&!bearing(h).getSequencePhase().equals(firstPhase[0]),"Equal RPM/context still carries a new phase identity"));
        h.runAfterDelay(62,()->{h.assertTrue(Math.abs(bearing(h).getMovedContraption().getAngle(1)-180)<.001,"Real payload consumes both ninety-degree phases");h.succeed();});
    }
    @GameTest(maxTicks=50)public void downstreamHalfSpeedBearingReceivesFortyFiveDegreeLimit(GameTestHelper h){
        drive(h,Direction.EAST,64);h.setBlock(B,AllBlocks.COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));var large=B.above().south();var bearingPos=large.east();
        h.setBlock(large,AllBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));h.setBlock(bearingPos,AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.EAST));
        var consumer=h.getBlockEntity(bearingPos,MechanicalBearingBlockEntity.class);consumer.setMovementMode(IControlContraption.RotationMode.ROTATE_NEVER_PLACE);h.setBlock(bearingPos.east(),Blocks.BARREL);
        h.runAfterDelay(5,()->configureAndPower(h,List.of(angle(90,InstructionSpeedModifiers.FORWARD),end())));
        h.runAfterDelay(8,()->h.assertTrue(consumer.getTheoreticalSpeed()==-32&&consumer.sequenceContext!=null&&consumer.sequenceContext.getEffectiveValue(-32)==45,"Context scales by actual downstream RPM"));
        h.runAfterDelay(30,()->{h.assertTrue(Math.abs(consumer.getMovedContraption().getAngle(1)+45)<.001,"Native geared payload rotates minus45");h.succeed();});
    }
    @GameTest(maxTicks=35)public void consumerStallConsumesNoAngleBudget(GameTestHelper h){
        machine(h,16);h.runAfterDelay(5,()->configureAndPower(h,List.of(angle(360,InstructionSpeedModifiers.FORWARD),await())));
        h.runAfterDelay(12,()->{var consumer=bearing(h);var owner=consumer.getMovedContraption();h.assertTrue(owner!=null&&consumer.getSequencedAngleLimit()>0,"Running real consumer has remaining budget");var angle=owner.getAngle(1);var remaining=consumer.getSequencedAngleLimit();owner.setStalled(true);
            MechanicalBearingBlockEntity.tick(h.getLevel(),h.absolutePos(B),h.getBlockState(B),consumer);h.assertTrue(owner.getAngle(1)==angle&&consumer.getSequencedAngleLimit()==remaining,"Real stalled native ticker consumes no angle");owner.setStalled(false);
            MechanicalBearingBlockEntity.tick(h.getLevel(),h.absolutePos(B),h.getBlockState(B),consumer);h.assertTrue(consumer.getSequencedAngleLimit()<remaining,"Actual resumed movement consumes budget");h.succeed();});
    }
    @GameTest(maxTicks=90)public void replacingSequencerAtSamePositionGrantsFreshNinetyToTheRetainedRealOwner(GameTestHelper h){
        machine(h,16);var original=seq(h);var firstPhase=new SequencePhase[1];var secondPhase=new SequencePhase[1];var ownerId=new UUID[1];var stoppedAngle=new float[1];var oldRemaining=new double[1];
        h.runAfterDelay(5,()->configureAndPower(h,List.of(angle(90,InstructionSpeedModifiers.FORWARD),end())));
        h.runAfterDelay(12,()->{var consumer=bearing(h);var owner=consumer.getMovedContraption();h.assertTrue(owner!=null&&consumer.getSequencedAngleLimit()>0&&consumer.getSequencedAngleLimit()<90,"Original real owner consumed only part of its first ninety-degree command");ownerId[0]=owner.getUUID();firstPhase[0]=seq(h).getSequencePhase();power(h,false);h.setBlock(S,Blocks.AIR);});
        h.runAfterDelay(18,()->{var consumer=bearing(h);var owner=consumer.getMovedContraption();h.assertTrue(owner!=null&&ownerId[0].equals(owner.getUUID())&&consumer.isRunning()&&consumer.getSpeed()==0&&consumer.getSequencePhase()==null,"Physical sequencer removal stops output while retaining the actual bearing owner");stoppedAngle[0]=owner.getAngle(1);oldRemaining[0]=consumer.saveWithoutMetadata(h.getLevel().registryAccess()).getDoubleOr("SequenceLimitRemaining",-1);h.assertTrue(stoppedAngle[0]>0&&stoppedAngle[0]<90&&oldRemaining[0]>0&&oldRemaining[0]<90,"Stopped real movement has an unfinished durable old receipt");h.setBlock(S,AllBlocks.SEQUENCED_GEARSHIFT.defaultBlockState().setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS,Direction.Axis.X));});
        h.runAfterDelay(24,()->{h.assertTrue(seq(h)!=original&&seq(h).isIdle()&&seq(h).getSpeed()==16,"Same native position now contains a genuinely new loaded controller, not the old BE");configureAndPower(h,List.of(angle(90,InstructionSpeedModifiers.FORWARD),await()));});
        h.runAfterDelay(30,()->{secondPhase[0]=seq(h).getSequencePhase();h.assertTrue(firstPhase[0]!=null&&secondPhase[0]!=null&&firstPhase[0].origin().equals(secondPhase[0].origin())&&firstPhase[0].epoch()==1&&secondPhase[0].epoch()==1,"Both real controller instances run their first command at the same position");});
        h.runAfterDelay(65,()->{var owner=bearing(h).getMovedContraption();h.assertTrue(owner!=null&&ownerId[0].equals(owner.getUUID())&&seq(h).getInstructionIndex()==1,"Replacement command completed against the retained physical owner and entered AWAIT");float actual=owner.getAngle(1),expected=stoppedAngle[0]+90;h.assertTrue(Math.abs(actual-expected)<.001,"Replacing the controller must grant a fresh ninety degrees: stopped="+stoppedAngle[0]+", oldRemaining="+oldRemaining[0]+", expected="+expected+", actual="+actual+", oldPhase="+firstPhase[0]+", newPhase="+secondPhase[0]);h.assertFalse(firstPhase[0].equals(secondPhase[0]),"Distinct controllers cannot reuse the old consumed phase identity");h.succeed();});
    }
    @GameTest(maxTicks=35)public void saveLoadKeepsTimerPhaseAndConsumerRemainingWithoutReplay(GameTestHelper h){
        machine(h,16);h.runAfterDelay(5,()->configureAndPower(h,List.of(angle(360,InstructionSpeedModifiers.FORWARD),await())));
        h.runAfterDelay(12,()->{var phase=seq(h).getSequencePhase();var savedSeq=seq(h).saveWithFullMetadata(h.getLevel().registryAccess());var savedBearing=bearing(h).saveWithFullMetadata(h.getLevel().registryAccess());
            var restoredSeq=(SequencedGearshiftBlockEntity)BlockEntity.loadStatic(h.absolutePos(S),h.getBlockState(S),savedSeq,h.getLevel().registryAccess());var restoredBearing=(MechanicalBearingBlockEntity)BlockEntity.loadStatic(h.absolutePos(B),h.getBlockState(B),savedBearing,h.getLevel().registryAccess());
            h.assertTrue(restoredSeq.getTimer()==seq(h).getTimer()&&restoredSeq.getInstructionProgress()==seq(h).getInstructionProgress()&&phase.equals(restoredSeq.getSequencePhase()),"Running sequence resumes same native timer/phase");
            h.assertTrue(restoredBearing.getSequencedAngleLimit()==bearing(h).getSequencedAngleLimit()&&restoredBearing.getSequencePhase().equals(bearing(h).getSequencePhase()),"Native load does not replay/reset consumer limit");h.succeed();});
    }
    @GameTest(maxTicks=35)public void legacyProducerAndConsumerPhasesMigrateStablyWithoutReplayingConsumedTravel(GameTestHelper h){
        machine(h,16);var ownerId=new UUID[1];var remaining=new double[1];var migratedPhase=new SequencePhase[1];
        h.runAfterDelay(5,()->configureAndPower(h,List.of(angle(360,InstructionSpeedModifiers.FORWARD),await())));
        h.runAfterDelay(12,()->{var original=seq(h);var consumer=bearing(h);ownerId[0]=consumer.getMovedContraption().getUUID();remaining[0]=consumer.getSequencedAngleLimit();var savedSeq=original.saveWithFullMetadata(h.getLevel().registryAccess());var savedBearing=consumer.saveWithFullMetadata(h.getLevel().registryAccess());
            savedSeq.remove("SequenceGeneration");savedSeq.getCompoundOrEmpty("SequencePhase").remove("Generation");savedBearing.getCompoundOrEmpty("SequencePhase").remove("Generation");savedBearing.getCompoundOrEmpty("SequenceLimitPhase").remove("Generation");
            var restoredSeq=(SequencedGearshiftBlockEntity)BlockEntity.loadStatic(h.absolutePos(S),h.getBlockState(S),savedSeq,h.getLevel().registryAccess());var restoredBearing=(MechanicalBearingBlockEntity)BlockEntity.loadStatic(h.absolutePos(B),h.getBlockState(B),savedBearing,h.getLevel().registryAccess());migratedPhase[0]=restoredSeq.getSequencePhase();
            h.assertTrue(restoredSeq.getSequenceGeneration().equals(SequencePhase.LEGACY_GENERATION)&&migratedPhase[0].equals(new SequencePhase(h.absolutePos(S),original.getSequencePhase().epoch()))&&migratedPhase[0].equals(restoredBearing.getSequencePhase()),"Old two-field phase and producer adopt the same stable legacy generation before level assignment");
            h.assertTrue(restoredSeq.getTimer()==original.getTimer()&&restoredSeq.getInstructionProgress()==original.getInstructionProgress()&&restoredBearing.getSequencedAngleLimit()==remaining[0],"Native migration preserves consumed timer/progress/travel");
            restoredBearing.applySequenceContext(null,null);restoredBearing.applySequenceContext(restoredSeq.sequenceContext,migratedPhase[0]);h.assertTrue(restoredBearing.getSequencedAngleLimit()==remaining[0],"Same migrated phase resumes its dormant receipt rather than replaying full360degrees");
            var sourceOnly=savedSeq.copy();sourceOnly.remove("SequencePhase");sourceOnly.remove("SequenceEpoch");var sourceA=(SequencedGearshiftBlockEntity)BlockEntity.loadStatic(h.absolutePos(S),h.getBlockState(S),sourceOnly,h.getLevel().registryAccess());var sourceB=(SequencedGearshiftBlockEntity)BlockEntity.loadStatic(h.absolutePos(S),h.getBlockState(S),sourceOnly,h.getLevel().registryAccess());h.assertTrue(sourceA.getSequencePhase().equals(new SequencePhase(h.absolutePos(S),1))&&sourceA.getSequencePhase().equals(sourceB.getSequencePhase()),"Original source NBT without phase or generation reconstructs a stable legacy controller identity");
            h.getLevel().removeBlockEntity(h.absolutePos(S));h.getLevel().setBlockEntity(restoredSeq);h.getLevel().removeBlockEntity(h.absolutePos(B));h.getLevel().setBlockEntity(restoredBearing);
        });
        h.runAfterDelay(14,()->h.assertTrue(migratedPhase[0].equals(seq(h).getSequencePhase())&&migratedPhase[0].equals(bearing(h).getSequencePhase())&&bearing(h).getSequencedAngleLimit()<=remaining[0]&&ownerId[0].equals(bearing(h).getMovedContraption().getUUID()),"Actual native world rejoin keeps migrated owner/phase and cannot reset the consumed budget"));
        h.runAfterDelay(20,()->{var data=seq(h).saveWithFullMetadata(h.getLevel().registryAccess());var copyA=(SequencedGearshiftBlockEntity)BlockEntity.loadStatic(h.absolutePos(S),h.getBlockState(S),data,h.getLevel().registryAccess());var copyB=(SequencedGearshiftBlockEntity)BlockEntity.loadStatic(h.absolutePos(S),h.getBlockState(S),data,h.getLevel().registryAccess());h.assertTrue(copyA.getSequencePhase().equals(migratedPhase[0])&&copyB.getSequencePhase().equals(migratedPhase[0])&&data.read("SequenceGeneration",UUIDUtil.CODEC).orElseThrow().equals(SequencePhase.LEGACY_GENERATION),"Saving migration makes generation durable and repeated native reads never randomize it");h.succeed();});
    }
    @GameTest(maxTicks=30)public void nativeEpochRolloverChangesDurableGenerationBeforeReusingPhaseOne(GameTestHelper h){
        machine(h,16);var oldGeneration=new UUID[1];var ownerId=new UUID[1];var remaining=new double[1];var newPhase=new SequencePhase[1];var trace=new StringBuilder();
        h.runAfterDelay(5,()->configureAndPower(h,List.of(angle(360,InstructionSpeedModifiers.FORWARD),await())));
        h.runAfterDelay(12,()->{var producer=seq(h);oldGeneration[0]=producer.getSequenceGeneration();ownerId[0]=bearing(h).getMovedContraption().getUUID();remaining[0]=bearing(h).getSequencedAngleLimit();var saved=producer.saveWithFullMetadata(h.getLevel().registryAccess());saved.putLong("SequenceEpoch",Long.MAX_VALUE);saved.getCompoundOrEmpty("SequencePhase").putLong("Epoch",Long.MAX_VALUE);trace.append(" beforeRead=").append(producer.saveWithoutMetadata(h.getLevel().registryAccess()));var original=producer;
            // Source keeps wasMoved on the placed owner; use a genuine persisted reload,
            // retaining its saved generation and physical input before issuing the next phase.
            producer=(SequencedGearshiftBlockEntity)BlockEntity.loadStatic(h.absolutePos(S),h.getBlockState(S),saved,h.getLevel().registryAccess());
            h.assertTrue(producer!=null&&producer!=original&&producer.getSequenceGeneration().equals(oldGeneration[0])&&producer.getSequencePhase().generation().equals(oldGeneration[0])&&producer.getSequencePhase().epoch()==Long.MAX_VALUE&&producer.getSpeed()==16,"Actual native MAX-epoch reload preserves the old durable generation and saved original input16");
            h.getLevel().removeBlockEntity(h.absolutePos(S));h.assertTrue(original.isRemoved(),"Native producer replacement retires the original block entity before publishing its persisted replacement");producer.setLevel(h.getLevel());h.getLevel().setBlockEntity(producer);
            h.assertTrue(seq(h)==producer&&kinetic(h,S.west(2)).getGeneratedSpeed()==16&&kinetic(h,S.west()).getSpeed()==16&&ownerId[0].equals(bearing(h).getMovedContraption().getUUID())&&bearing(h).saveWithoutMetadata(h.getLevel().registryAccess()).getDoubleOr("SequenceLimitRemaining",-1)==remaining[0],"Reloaded producer is the actual native world owner with original input16 and the same bearing's unchanged consumed receipt");trace.append(" afterRead=").append(producer.saveWithoutMetadata(h.getLevel().registryAccess()));producer.run(0);trace.append(" afterRun=").append(producer.saveWithoutMetadata(h.getLevel().registryAccess()));newPhase[0]=producer.getSequencePhase();h.assertTrue(newPhase[0].epoch()==1&&!newPhase[0].generation().equals(oldGeneration[0]),"Reusing epoch1 after MAX_VALUE must first create a distinct controller generation");var data=producer.saveWithFullMetadata(h.getLevel().registryAccess());var restored=(SequencedGearshiftBlockEntity)BlockEntity.loadStatic(h.absolutePos(S),h.getBlockState(S),data,h.getLevel().registryAccess());h.assertTrue(restored.getSequencePhase().equals(newPhase[0])&&restored.getSequenceGeneration().equals(newPhase[0].generation()),"Rollover generation and phase survive actual native save/load");trace.append(" afterDetachedLoad=").append(producer.saveWithoutMetadata(h.getLevel().registryAccess()));});
        h.runAfterDelay(13,()->trace.append(" tick13=").append(seq(h).saveWithoutMetadata(h.getLevel().registryAccess())));
        h.runAfterDelay(14,()->trace.append(" tick14=").append(seq(h).saveWithoutMetadata(h.getLevel().registryAccess())));
        h.runAfterDelay(15,()->{h.assertTrue(newPhase[0].equals(bearing(h).getSequencePhase())&&ownerId[0].equals(bearing(h).getMovedContraption().getUUID())&&bearing(h).getSequencedAngleLimit()>remaining[0],"Actual retained consumer receives a fresh command after generation rollover, not an old phase-one receipt: new="+newPhase[0]+", producer="+seq(h).getSequencePhase()+", consumer="+bearing(h).getSequencePhase()+", originalOwner="+ownerId[0]+", actualOwner="+(bearing(h).getMovedContraption()==null?null:bearing(h).getMovedContraption().getUUID())+", priorRemaining="+remaining[0]+", actualRemaining="+bearing(h).getSequencedAngleLimit()+", input="+seq(h).getSpeed()+", output="+bearing(h).getSpeed()+", angle="+(bearing(h).getMovedContraption()==null?Float.NaN:bearing(h).getMovedContraption().getAngle(1))+", receipt="+bearing(h).saveWithoutMetadata(h.getLevel().registryAccess()).get("SequenceLimitPhase")+", timeline="+trace);h.succeed();});
    }
    @GameTest(maxTicks=35)public void removingInputAbortsAndClearsDownstreamSequenceContext(GameTestHelper h){
        machine(h,16);h.runAfterDelay(5,()->configureAndPower(h,List.of(angle(360,InstructionSpeedModifiers.FORWARD),await())));
        h.runAfterDelay(12,()->h.setBlock(S.west(2),Blocks.AIR));h.runAfterDelay(19,()->{h.assertTrue(seq(h).isIdle()&&seq(h).sequenceContext==null&&bearing(h).sequenceContext==null&&bearing(h).getSequencePhase()==null&&bearing(h).getSpeed()==0,"No stale phase or artificial RPM source after upstream removal");h.succeed();});
    }
    @GameTest(maxTicks=40)public void changingRealMotorSpeedRetimesWithoutUsingDoubledOutputAsInput(GameTestHelper h){
        machine(h,16);var oldDuration=new int[1];h.runAfterDelay(5,()->configureAndPower(h,List.of(angle(360,InstructionSpeedModifiers.FORWARD_FAST),await())));
        h.runAfterDelay(11,()->{oldDuration[0]=seq(h).getInstructionDuration();kinetic(h,S.west(2)).setGeneratedSpeed(32);});
        h.runAfterDelay(16,()->{h.assertTrue(seq(h).getSpeed()==32&&bearing(h).getSpeed()==64&&seq(h).getInstructionDuration()<oldDuration[0]&&seq(h).getInstructionIndex()==0,"Real speed change recomputes remaining source duration from input32");h.succeed();});
    }
    @GameTest public void farConfigurationCannotLoadAChunkAndNearConfigurationIsAuthoritative(GameTestHelper h){
        h.setBlock(S,AllBlocks.SEQUENCED_GEARSHIFT);var player=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(S)).add(0,0,1));
        var steps=List.of(new Instruction(SequencerInstructions.DELAY,73),end());h.assertTrue(new ConfigureSequencedGearshiftPacket(h.absolutePos(S),steps).apply(player)&&seq(h).getInstructions().equals(steps),"Server owns bounded instructions on the actual loaded BE");
        var far=new BlockPos(2600000,70,2600000);h.assertFalse(new ConfigureSequencedGearshiftPacket(far,steps).apply(player),"Unloaded/far target refused");h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Configuration creates no loading ticket");h.succeed();
    }
    @GameTest public void sourceTransformKeepsHorizontalBackingAxisAndVerticalShaftValid(GameTestHelper h){
        var state=AllBlocks.SEQUENCED_GEARSHIFT.defaultBlockState().setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS,Direction.Axis.X);
        var rotated=new StructureTransform(BlockPos.ZERO,Direction.Axis.Z,Rotation.CLOCKWISE_90,Mirror.NONE).apply(state);
        h.assertTrue(((IRotate)rotated.getBlock()).getRotationAxis(rotated)==Direction.Axis.Y&&rotated.getValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS)!=Direction.Axis.Y,"Real transformed shaft uses vertical flag without invalid horizontal property");
        var twice=new StructureTransform(BlockPos.ZERO,Direction.Axis.Z,Rotation.CLOCKWISE_90,Mirror.NONE).apply(rotated);h.assertTrue(((IRotate)twice.getBlock()).getRotationAxis(twice)==Direction.Axis.X,"Second quarter turn restores horizontal axis");h.succeed();
    }
}
