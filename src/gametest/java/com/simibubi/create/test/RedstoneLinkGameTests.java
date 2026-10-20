package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.redstone.link.*;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Actual native blocks/ownership plus deterministic tests of the source wireless update contract. */
public class RedstoneLinkGameTests{
    private static final BlockPos P=new BlockPos(3,4,4),Q=new BlockPos(7,4,4);
    private static final class Actor implements IRedstoneLinkable{
        private BlockPos pos;private Couple<Frequency> key;private boolean alive=true,listening;private int power,received=-1;private Runnable callback=()->{};
        Actor(BlockPos pos,Couple<Frequency> key,boolean listening,int power){this.pos=pos;this.key=key;this.listening=listening;this.power=power;}
        @Override public int getTransmittedStrength(){return power;}@Override public void setReceivedStrength(int value){received=value;callback.run();}
        @Override public boolean isListening(){return listening;}@Override public boolean isAlive(){return alive;}
        @Override public Couple<Frequency>getNetworkKey(){return key;}@Override public BlockPos getLocation(){return pos;}
    }
    private static Couple<Frequency> key(){return Couple.create(Frequency.of(new ItemStack(Items.DIAMOND)),Frequency.of(new ItemStack(Items.EMERALD)));}
    public static ItemStack sample(GameTestHelper h,boolean first){var stack=new ItemStack(first?Items.DIAMOND:Items.EMERALD);var origin=h.absolutePos(P);stack.set(DataComponents.DYED_COLOR,new DyedItemColor((first?origin.getX():origin.getZ())&0xffffff));return stack;}
    private static RedstoneLinkBlockEntity place(GameTestHelper h,BlockPos pos,boolean receiver){
        h.setBlock(pos.below(),Blocks.STONE);h.setBlock(pos,AllBlocks.REDSTONE_LINK.defaultBlockState().setValue(RedstoneLinkBlock.FACING,Direction.UP).setValue(RedstoneLinkBlock.RECEIVER,receiver));
        var entity=h.getBlockEntity(pos,RedstoneLinkBlockEntity.class);entity.getLink().setFrequency(true,sample(h,true));entity.getLink().setFrequency(false,sample(h,false));entity.revalidateNetwork();return entity;
    }
    @GameTest public void frequenciesIgnoreCountAndNamesButKeepItemAndDyedColor(GameTestHelper h){
        var ordinary=new ItemStack(Items.LEATHER_HELMET);var named=ordinary.copyWithCount(9);named.set(DataComponents.CUSTOM_NAME,Component.literal("Display only"));
        h.assertTrue(Frequency.of(ordinary).equals(Frequency.of(named)),"Count/custom name do not split wireless keys");
        ordinary.set(DataComponents.DYED_COLOR,new DyedItemColor(0x123456));named.set(DataComponents.DYED_COLOR,new DyedItemColor(0x654321));
        h.assertFalse(Frequency.of(ordinary).equals(Frequency.of(named)),"RGB participates in frequency identity");
        h.assertFalse(Frequency.of(new ItemStack(Items.DIAMOND)).equals(Frequency.of(new ItemStack(Items.EMERALD))),"Item identity differs");h.succeed();
    }
    @GameTest public void mutableCallerAndReturnedStacksCannotCorruptTheNetworkKey(GameTestHelper h){
        var stack=new ItemStack(Items.LEATHER_HELMET);stack.set(DataComponents.DYED_COLOR,new DyedItemColor(123));var frequency=Frequency.of(stack);int hash=frequency.hashCode();
        stack.set(DataComponents.DYED_COLOR,new DyedItemColor(456));var display=frequency.getStack();display.set(DataComponents.DYED_COLOR,new DyedItemColor(789));
        h.assertTrue(frequency.hashCode()==hash&&frequency.getStack().get(DataComponents.DYED_COLOR).rgb()==123,"Hash and sample snapshot stay immutable");h.succeed();
    }
    @GameTest public void orderedAndEmptyFrequenciesDoNotCollide(GameTestHelper h){
        var network=new RedstoneLinkNetworkHandler();var key=key();var transmitter=new Actor(BlockPos.ZERO,key,false,15);
        var reversed=new Actor(BlockPos.ZERO,Couple.create(key.getSecond(),key.getFirst()),true,0);var empty=new Actor(BlockPos.ZERO,Couple.create(Frequency.EMPTY,Frequency.EMPTY),true,0);
        network.addToNetwork(h.getLevel(),transmitter);network.addToNetwork(h.getLevel(),reversed);network.addToNetwork(h.getLevel(),empty);
        h.assertTrue(reversed.received==0&&empty.received==0,"Ordered/empty networks remain independent");h.succeed();
    }
    @GameTest public void analogueMaximumUpdatesAndRemovalDropsToTheRemainingTransmitter(GameTestHelper h){
        var network=new RedstoneLinkNetworkHandler();var key=key();var low=new Actor(BlockPos.ZERO,key,false,4);var high=new Actor(BlockPos.ZERO.east(),key,false,11);var receiver=new Actor(BlockPos.ZERO.north(),key,true,0);
        network.addToNetwork(h.getLevel(),receiver);network.addToNetwork(h.getLevel(),low);network.addToNetwork(h.getLevel(),high);h.assertValueEqual(receiver.received,11,"Maximum analogue value");
        high.power=7;network.updateNetworkOf(h.getLevel(),high);h.assertValueEqual(receiver.received,7,"Analogue reduction, not binary15");network.removeFromNetwork(h.getLevel(),high);h.assertValueEqual(receiver.received,4,"Remaining transmitter wins");network.removeFromNetwork(h.getLevel(),low);h.assertValueEqual(receiver.received,0,"No stale power");h.succeed();
    }
    @GameTest public void strictRangeIncludesYAndExcludesTheExactBoundary(GameTestHelper h){
        var network=new RedstoneLinkNetworkHandler();network.setRange(8);var key=key();var transmitter=new Actor(BlockPos.ZERO,key,false,15);
        var inside=new Actor(new BlockPos(0,7,0),key,true,0);var boundary=new Actor(new BlockPos(8,0,0),key,true,0);
        network.addToNetwork(h.getLevel(),transmitter);network.addToNetwork(h.getLevel(),inside);network.addToNetwork(h.getLevel(),boundary);
        h.assertValueEqual(inside.received,15,"Vertical distance within range");h.assertValueEqual(boundary.received,0,"Exact boundary excluded");h.succeed();
    }
    @GameTest public void sourceUpdateUsesTheInitiatorsRadiusForItsSingleMaximum(GameTestHelper h){
        var network=new RedstoneLinkNetworkHandler();network.setRange(10);var key=key();var initiating=new Actor(BlockPos.ZERO,key,false,2);var other=new Actor(new BlockPos(-9,0,0),key,false,12);var receiver=new Actor(new BlockPos(9,0,0),key,true,0);
        network.addToNetwork(h.getLevel(),initiating);network.addToNetwork(h.getLevel(),other);network.addToNetwork(h.getLevel(),receiver);network.updateNetworkOf(h.getLevel(),initiating);
        h.assertValueEqual(receiver.received,12,"Source initiator-centered aggregate preserved despite receiver-transmitter distance18");h.succeed();
    }
    @GameTest public void staleDeadParticipantIsPrunedWithoutLosingLiveReceiver(GameTestHelper h){
        var network=new RedstoneLinkNetworkHandler();var key=key();var transmitter=new Actor(BlockPos.ZERO,key,false,15);var receiver=new Actor(BlockPos.ZERO,key,true,0);
        network.addToNetwork(h.getLevel(),transmitter);network.addToNetwork(h.getLevel(),receiver);transmitter.alive=false;network.tick(h.getLevel());
        h.assertTrue(receiver.received==0&&network.getNetworkOf(h.getLevel(),receiver).size()==1,"Native ownership death cannot strand power");h.succeed();
    }
    @GameTest public void reentrantCallbackRemovalDoesNotReinsertOrIterateAStaleHandle(GameTestHelper h){
        var network=new RedstoneLinkNetworkHandler();var key=key();var receiver=new Actor(BlockPos.ZERO,key,true,0);var transmitter=new Actor(BlockPos.ZERO,key,false,15);
        network.addToNetwork(h.getLevel(),receiver);receiver.callback=()->network.removeFromNetwork(h.getLevel(),receiver);network.addToNetwork(h.getLevel(),transmitter);
        h.assertFalse(network.isRegistered(h.getLevel(),receiver),"Callback removal owns membership");h.assertTrue(network.isRegistered(h.getLevel(),transmitter),"Live transmitter retained");h.succeed();
    }
    @GameTest public void unloadingNetworkSpaceDoesNotAllowLateRemovalToRecreateWorldReferences(GameTestHelper h){
        var network=new RedstoneLinkNetworkHandler();var actor=new Actor(BlockPos.ZERO,key(),false,15);network.addToNetwork(h.getLevel(),actor);int version=network.globalPowerVersion.get();
        network.onUnloadWorld(h.getLevel());network.removeFromNetwork(h.getLevel(),actor);network.updateNetworkOf(h.getLevel(),actor);network.addToNetwork(h.getLevel(),actor);
        h.assertTrue(network.networksIn(h.getLevel()).isEmpty()&&!network.hasAnyLoadedPower(actor.key)&&network.globalPowerVersion.get()!=version,"Closed world cannot be resurrected by late handles");h.succeed();
    }
    @GameTest public void removingLastTransmitterInvalidatesGlobalPowerVersion(GameTestHelper h){
        var network=new RedstoneLinkNetworkHandler();var actor=new Actor(BlockPos.ZERO,key(),false,15);network.addToNetwork(h.getLevel(),actor);int before=network.globalPowerVersion.get();network.removeFromNetwork(h.getLevel(),actor);
        h.assertTrue(network.globalPowerVersion.get()!=before&&!network.hasAnyLoadedPower(actor.key),"Future schedule cache observes last participant removal");h.succeed();
    }
    @GameTest public void powerDoesNotCrossDimensionsButLoadedPowerQueryCanSeeIt(GameTestHelper h){
        var other=h.getLevel().getServer().getLevel(Level.NETHER);h.assertTrue(other!=null,"Native test Nether exists");var network=new RedstoneLinkNetworkHandler();var transmitter=new Actor(BlockPos.ZERO,key(),false,15);var receiver=new Actor(BlockPos.ZERO,key(),true,0);
        network.addToNetwork(h.getLevel(),transmitter);network.addToNetwork(other,receiver);h.assertValueEqual(receiver.received,0,"No dimension crossing");h.assertTrue(network.hasAnyLoadedPower(h.getLevel().getServer(),key()),"Server-wide schedule query sees a loaded source");network.unload(h.getLevel().getServer());h.assertFalse(network.hasAnyLoadedPower(key()),"Server lifecycle clears every dimension");h.succeed();
    }
    @GameTest public void nativeReceiverFirstAndTransmitterFirstInitializeImmediately(GameTestHelper h){
        var receiver=place(h,Q,true);var transmitter=place(h,P,false);transmitter.transmit(7);h.assertValueEqual(receiver.getReceivedSignal(),7,"Receiver-first initialization");
        var later=place(h,Q.north(2),true);h.assertValueEqual(later.getReceivedSignal(),7,"Transmitter-first initialization");h.succeed();
    }
    @GameTest(maxTicks=30) public void realSignalDrivesNativeWireAndClearsAfterTransmitterRemoval(GameTestHelper h){
        place(h,P,false);var receiver=place(h,Q,true);h.setBlock(P.west(),Blocks.REDSTONE_BLOCK);h.setBlock(Q.east().below(),Blocks.STONE);h.setBlock(Q.east(),Blocks.REDSTONE_WIRE);
        h.runAfterDelay(6,()->{h.assertTrue(receiver.getReceivedSignal()==15&&h.getBlockState(Q).getValue(RedstoneLinkBlock.POWERED)&&h.getBlockState(Q.east()).getValue(RedstoneWireBlock.POWER)==15,"Native block/BE/wire update");h.setBlock(P,Blocks.AIR);h.runAfterDelay(5,()->{h.assertTrue(receiver.getReceivedSignal()==0&&h.getBlockState(Q.east()).getValue(RedstoneWireBlock.POWER)==0,"Removal really clears wire");h.succeed();});});
    }
    @GameTest public void changingFrequencyDisconnectsOldPairAndRetainsDisplayedComponents(GameTestHelper h){
        var transmitter=place(h,P,false);var receiver=place(h,Q,true);transmitter.transmit(13);var named=new ItemStack(Items.APPLE,17);named.set(DataComponents.CUSTOM_NAME,Component.literal("Frequency display"));
        transmitter.getLink().setFrequency(true,named);h.assertValueEqual(receiver.getReceivedSignal(),0,"Old pair disconnected");receiver.getLink().setFrequency(true,new ItemStack(Items.APPLE));h.assertValueEqual(receiver.getReceivedSignal(),13,"Names do not partition network");
        var sample=transmitter.getLink().getNetworkKey().getFirst().getStack();h.assertTrue(sample.getCount()==1&&sample.has(DataComponents.CUSTOM_NAME)&&named.getCount()==17,"Ghost copy preserves display components without consuming input");h.succeed();
    }
    @GameTest public void nativeBlockModeSwapDoesNotLeaveTwoWirelessMemberships(GameTestHelper h){
        var first=place(h,P,false);var second=place(h,Q,true);first.transmit(9);((RedstoneLinkBlock)AllBlocks.REDSTONE_LINK).toggleMode(h.getBlockState(P),h.getLevel(),h.absolutePos(P));
        h.assertTrue(first.getLink().isListening()&&second.getReceivedSignal()==0,"Old transmitter removed");h.assertValueEqual(RedstoneLinkNetworkHandler.INSTANCE.getNetworkOf(h.getLevel(),first.getLink()).size(),2,"Exactly two distinct BE handles");h.succeed();
    }
    @GameTest public void nativeSaveLoadBeforeSetLevelPreservesFrequenciesAndAnalogueState(GameTestHelper h){
        var entity=place(h,P,true);entity.setSignal(6);var saved=entity.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored=(RedstoneLinkBlockEntity)BlockEntity.loadStatic(h.absolutePos(P),h.getBlockState(P),saved,h.getLevel().registryAccess());
        h.assertTrue(restored.getLink().getNetworkKey().equals(entity.getLink().getNetworkKey())&&restored.getReceivedSignal()==6,"Native codec without level already assigned");h.succeed();
    }
    @GameTest public void loadedOnlyUnknownSignalReadsNeverGenerateFarChunks(GameTestHelper h){
        var far=new BlockPos(2300000,70,2300000);var state=AllBlocks.REDSTONE_LINK.defaultBlockState();((RedstoneLinkBlock)AllBlocks.REDSTONE_LINK).updateTransmittedSignal(state,h.getLevel(),far);
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"No loading lookup through public transmitter update");h.succeed();
    }
    @GameTest public void captureUnregistersLinkAndRestorationRecreatesExactFrequencySamples(GameTestHelper h)throws Exception{
        var entity=place(h,P,false);var receiver=place(h,Q,true);entity.transmit(8);var before=entity.getLink().getNetworkKey();
        var structure=new BearingContraption(false,Direction.UP);h.assertTrue(structure.assemble(h.getLevel(),h.absolutePos(P.below(2))),"Real support capture");
        h.assertTrue(structure.getBlocks().size()==2&&structure.getBlocks().get(BlockPos.ZERO).state().is(Blocks.STONE)
            &&structure.getBlocks().get(BlockPos.ZERO.above()).state().is(AllBlocks.REDSTONE_LINK),"Link is attached to the captured support block");
        h.assertTrue(structure.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO)&&h.getBlockState(P).isAir()&&h.getBlockState(P.below()).isAir(),"Real capture transfers both support and link");
        h.assertValueEqual(receiver.getReceivedSignal(),0,"Removed link cannot transmit in contraption payload");
        var owner=new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION,h.getLevel());owner.setContraption(structure);owner.setPos(Vec3.atLowerCornerOf(h.absolutePos(P.below())));h.assertTrue(owner.disassemble(),"Restore link and support");
        var restored=h.getBlockEntity(P,RedstoneLinkBlockEntity.class);h.assertTrue(restored.getLink().getNetworkKey().equals(before),"Frequency samples survive capture and restoration");restored.revalidateNetwork();restored.transmit(5);h.assertValueEqual(receiver.getReceivedSignal(),5,"Restored BE owns one working membership");h.succeed();
    }
}
