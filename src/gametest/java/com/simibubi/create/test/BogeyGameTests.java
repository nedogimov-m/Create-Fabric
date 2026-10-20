package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.trains.bogey.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;

/** Actual registered bogeys and source station-operation kernels. No fake station/train assembly is claimed. STAGED, UNRUN. */
public class BogeyGameTests {
    private static final BlockPos P=new BlockPos(4,4,4);
    private static StandardBogeyBlockEntity bogey(GameTestHelper h){return h.getBlockEntity(P,StandardBogeyBlockEntity.class);}
    private static BlockState state(GameTestHelper h){return h.getBlockState(P);}
    private static Player player(GameTestHelper h){var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(h.absolutePos(P)).add(0,0,1));return player;}
    private static void place(GameTestHelper h,StandardBogeyBlock block){h.setBlock(P,block.defaultBlockState().setValue(AbstractBogeyBlock.AXIS,Direction.Axis.Z));}
    private static void track(GameTestHelper h){h.setBlock(P.below(),AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.ZO));}
    private static boolean create(GameTestHelper h,Player player){var position=h.absolutePos(P.below());var state=h.getBlockState(P.below());return BogeyPlacement.placeAtAuthorizedTrack(player,InteractionHand.MAIN_HAND,(ITrackBlock)state.getBlock(),state,position);}
    private static void wrench(GameTestHelper h,Player player){player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH));var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)).add(0,.5,0),Direction.UP,h.absolutePos(P),false);h.assertTrue(AllItems.WRENCH.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Real native wrench action");}

    @GameTest public void registeredBogeysHaveActualSharedTypeAndNoInventedItem(GameTestHelper h){
        for(var block:new StandardBogeyBlock[]{AllBlocks.SMALL_BOGEY,AllBlocks.LARGE_BOGEY}){place(h,block);h.assertTrue(bogey(h).getType()==AllBlockEntityTypes.BOGEY&&block.asItem()==Items.AIR,"Actual source blocks have BE type but no standalone item");}h.succeed();
    }
    @GameTest public void sourceWheelAndConnectorDimensionsRemainExact(GameTestHelper h){
        h.assertTrue(AllBlocks.SMALL_BOGEY.getWheelRadius()==6.5/16d&&AllBlocks.LARGE_BOGEY.getWheelRadius()==12.5/16d,"Pinned source small/large physical wheel radii");
        for(var block:new StandardBogeyBlock[]{AllBlocks.SMALL_BOGEY,AllBlocks.LARGE_BOGEY})h.assertTrue(block.getWheelPointSpacing()==2&&block.getConnectorAnchorOffset(false).equals(new Vec3(0,7/32f,1))&&block.allowsSingleBogeyCarriage()&&!block.captureBlockEntityForTrain(),"Actual point spacing, connector and train-capture defaults");h.succeed();
    }
    @GameTest public void stickySurfacesFollowOnlyTheActualHorizontalTrackAxis(GameTestHelper h){
        var block=AllBlocks.SMALL_BOGEY;var x=block.defaultBlockState().setValue(AbstractBogeyBlock.AXIS,Direction.Axis.X);var z=x.setValue(AbstractBogeyBlock.AXIS,Direction.Axis.Z);
        h.assertTrue(block.getStickySurfaces(h.getLevel(),h.absolutePos(P),x).equals(java.util.EnumSet.of(Direction.EAST,Direction.WEST))&&block.getStickySurfaces(h.getLevel(),h.absolutePos(P),z).equals(java.util.EnumSet.of(Direction.NORTH,Direction.SOUTH)),"Only pinned sticky sides participate in capture");h.succeed();
    }
    @GameTest public void matchingBogeyRejectsUnsupportedUpDirectionsAndKeepsAxis(GameTestHelper h){
        for(var direction:Direction.values())h.assertTrue((AllBlocks.SMALL_BOGEY.getMatchingBogey(direction,true)!=null)==(direction==Direction.UP),"Standard bogey has no inverted or side-facing version");
        h.assertTrue(AllBlocks.LARGE_BOGEY.getMatchingBogey(Direction.UP,false).getValue(AbstractBogeyBlock.AXIS)==Direction.Axis.Z&&!AllBlocks.LARGE_BOGEY.canBeUpsideDown(),"Matching source axis without claiming inverted support");h.succeed();
    }
    @GameTest public void actualStandardStyleCyclesBothRealSizesAndRealTrackType(GameTestHelper h){
        var style=AllBogeyStyles.STANDARD;h.assertTrue(style.validSizes().size()==2&&style.getNextBlock(BogeySizes.SMALL)==AllBlocks.LARGE_BOGEY&&style.getNextBlock(BogeySizes.LARGE)==AllBlocks.SMALL_BOGEY,"Source style cycles actual registered blocks");
        h.assertTrue(!AllBlocks.SMALL_BOGEY.isOnIncompatibleTrack(TrackMaterial.ANDESITE,style)&&AllBlocks.SMALL_BOGEY.getValidPathfindingTypes(style).equals(java.util.Set.of(TrackMaterial.TrackType.STANDARD)),"Common material compatibility consumes actual track foundation type");h.succeed();
    }
    @GameTest public void sourceSizeRegistrySortsAndRejectsDuplicateWithoutCorruptingCycle(GameTestHelper h){
        int count=BogeySizes.all().size();boolean rejected=false;try{BogeySizes.register(BogeySizes.SMALL);}catch(IllegalArgumentException expected){rejected=true;}
        h.assertTrue(rejected&&BogeySizes.all().size()==count&&BogeySizes.SMALL.nextBySize()==BogeySizes.LARGE&&BogeySizes.LARGE.nextBySize()==BogeySizes.SMALL,"Duplicate size cannot alter sorted cyclic identity");h.succeed();
    }
    @GameTest public void nativeDataRoundtripBeforeSetLevelKeepsCustomCompoundAndStyle(GameTestHelper h){
        place(h,AllBlocks.SMALL_BOGEY);var data=bogey(h).getBogeyData().copy();var custom=new CompoundTag();custom.putString("Name","Persistent undercarriage");data.put("Custom",custom);bogey(h).setBogeyData(data);
        var restored=(StandardBogeyBlockEntity)BlockEntity.loadStatic(h.absolutePos(P),state(h),bogey(h).saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(restored!=null&&restored.getLevel()==null&&restored.getStyle()==AllBogeyStyles.STANDARD&&restored.getBogeyData().equals(data),"Native BogeyData decoding keeps exact custom source payload before world attachment");h.succeed();
    }
    @GameTest public void missingLegacyBogeyDataUsesActualDefaultStyleWithoutAWorld(GameTestHelper h){
        place(h,AllBlocks.LARGE_BOGEY);var tag=bogey(h).saveWithFullMetadata(h.getLevel().registryAccess());tag.remove(AbstractBogeyBlockEntity.BOGEY_DATA_KEY);var restored=(StandardBogeyBlockEntity)BlockEntity.loadStatic(h.absolutePos(P),state(h),tag,h.getLevel().registryAccess());
        h.assertTrue(restored!=null&&restored.getStyle()==AllBogeyStyles.STANDARD&&!restored.getBogeyData().getBooleanOr(AbstractBogeyBlockEntity.UPSIDE_DOWN_KEY,true),"Legacy missing data reconstructs upright source standard");h.succeed();
    }
    @GameTest public void unknownStyleFallsBackWithoutDiscardingOtherSourceData(GameTestHelper h){
        place(h,AllBlocks.SMALL_BOGEY);var data=bogey(h).getBogeyData().copy();data.putString(AbstractBogeyBlockEntity.BOGEY_STYLE_KEY,"missing:style");data.putInt("CustomValue",39);bogey(h).setBogeyData(data);
        h.assertTrue(bogey(h).getStyle()==AllBogeyStyles.STANDARD&&bogey(h).getBogeyData().getIntOr("CustomValue",0)==39,"Unknown style fallback repairs style identity and retains unrelated bogey payload");h.succeed();
    }
    @GameTest public void nativePickAndLootReturnOnlyOneRailwayCasing(GameTestHelper h){
        for(var block:new StandardBogeyBlock[]{AllBlocks.SMALL_BOGEY,AllBlocks.LARGE_BOGEY}){place(h,block);var drops=Block.getDrops(state(h),h.getLevel(),h.absolutePos(P),bogey(h));h.assertTrue(drops.size()==1&&drops.getFirst().is(AllBlocks.RAILWAY_CASING.asItem())&&drops.getFirst().getCount()==1&&state(h).getCloneItemStack(h.getLevel(),h.absolutePos(P),false).is(AllBlocks.RAILWAY_CASING.asItem()),"Actual native loot and pick use source casing cost");}h.succeed();
    }
    @GameTest public void nativeBogeyStreamCodecRejectsAForeignRegisteredBlock(GameTestHelper h){
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());try{AbstractBogeyBlock.STREAM_CODEC.encode(buffer,AllBlocks.LARGE_BOGEY);h.assertTrue(AbstractBogeyBlock.STREAM_CODEC.decode(buffer)==AllBlocks.LARGE_BOGEY,"Actual native bogey registry stream identity");buffer.clear();ByteBufCodecs.registry(Registries.BLOCK).encode(buffer,Blocks.STONE);boolean rejected=false;try{AbstractBogeyBlock.STREAM_CODEC.decode(buffer);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,"Foreign registry member cannot decode as a bogey");}finally{buffer.release();}h.succeed();
    }
    @GameTest public void realWrenchSizeCyclePreservesWaterAxisAndCustomData(GameTestHelper h){
        place(h,AllBlocks.SMALL_BOGEY);h.setBlock(P,state(h).setValue(AbstractBogeyBlock.WATERLOGGED,true));var data=bogey(h).getBogeyData().copy();data.putString("Marker","Wrench preserves source data");bogey(h).setBogeyData(data);var user=player(h);
        wrench(h,user);h.assertTrue(state(h).is(AllBlocks.LARGE_BOGEY)&&state(h).getValue(AbstractBogeyBlock.AXIS)==Direction.Axis.Z&&state(h).getFluidState().getType()==Fluids.WATER&&bogey(h).getBogeyData().equals(data),"Actual registered size replacement retains native fluid, axis and data");wrench(h,user);h.assertTrue(state(h).is(AllBlocks.SMALL_BOGEY),"Real second wrench returns small size");h.succeed();
    }
    @GameTest public void realSneakWrenchReturnsExactlyOneCasing(GameTestHelper h){
        place(h,AllBlocks.LARGE_BOGEY);var user=player(h);user.setShiftKeyDown(true);wrench(h,user);int casing=0;for(int i=0;i<user.getInventory().getContainerSize();i++){var stack=user.getInventory().getItem(i);if(stack.is(AllBlocks.RAILWAY_CASING.asItem()))casing+=stack.getCount();}
        h.assertTrue(state(h).isAir()&&casing==1,"Actual native retrieval refunds one original casing, without an invented bogey item");h.succeed();
    }
    @GameTest public void virtualWheelDistanceUsesEachPhysicalRadiusWithoutCreatingMotion(GameTestHelper h){
        for(var block:new StandardBogeyBlock[]{AllBlocks.SMALL_BOGEY,AllBlocks.LARGE_BOGEY}){place(h,block);bogey(h).animate((float)(Math.PI*block.getWheelRadius()/2));h.assertTrue(Math.abs(bogey(h).getVirtualAngle(1)+90)<.001&&state(h).is(block),"Source quarter circumference rotates virtual wheel -90deg without moving the world block");}h.succeed();
    }
    @GameTest public void admittedStationKernelPlacesARealBogeyAndConsumesOneSurvivalCasing(GameTestHelper h){
        track(h);var user=player(h);var stack=new ItemStack(AllBlocks.RAILWAY_CASING,2);user.setItemInHand(InteractionHand.MAIN_HAND,stack);h.assertTrue(create(h,user),"Source operation kernel accepts a pre-admitted real straight track");
        h.assertTrue(state(h).is(AllBlocks.SMALL_BOGEY)&&state(h).getValue(AbstractBogeyBlock.AXIS)==Direction.Axis.Z&&bogey(h).getType()==AllBlockEntityTypes.BOGEY&&stack.getCount()==1,"Actual state/BE was placed and survival cost consumed once");h.succeed();
    }
    @GameTest public void rejectedWrongItemLeavesBothWorldAndInventoryUntouched(GameTestHelper h){
        track(h);var user=player(h);var stack=new ItemStack(Items.DIAMOND,2);user.setItemInHand(InteractionHand.MAIN_HAND,stack);h.assertFalse(create(h,user),"Non-casing cannot pay native survival cost");h.assertTrue(state(h).isAir()&&stack.getCount()==2,"Rejected source operation leaves original world and stack");h.succeed();
    }
    @GameTest public void bedrockTargetIsRejectedBeforeConsumingTheCasing(GameTestHelper h){
        track(h);h.setBlock(P,Blocks.BEDROCK);var user=player(h);var stack=new ItemStack(AllBlocks.RAILWAY_CASING,2);user.setItemInHand(InteractionHand.MAIN_HAND,stack);h.assertFalse(create(h,user),"Source indestructible-target guard");h.assertTrue(state(h).is(Blocks.BEDROCK)&&stack.getCount()==2,"No destruction or payment on preflight failure");h.succeed();
    }
    @GameTest public void unsupportedSlopeCannotBecomeAPretendFlatBogey(GameTestHelper h){
        track(h);h.setBlock(P.below(),h.getBlockState(P.below()).setValue(TrackBlock.SHAPE,TrackShape.AE));var user=player(h);var stack=new ItemStack(AllBlocks.RAILWAY_CASING,2);user.setItemInHand(InteractionHand.MAIN_HAND,stack);h.assertFalse(create(h,user),"Initial source station slice requires one actual flat cardinal rail axis");h.assertTrue(state(h).isAir()&&stack.getCount()==2,"Unsupported geometry remains untouched");h.succeed();
    }
    @GameTest public void existingBogeyCannotBeOverwrittenOrConsumeAnotherCasing(GameTestHelper h){
        track(h);place(h,AllBlocks.LARGE_BOGEY);var original=bogey(h);var user=player(h);var stack=new ItemStack(AllBlocks.RAILWAY_CASING,2);user.setItemInHand(InteractionHand.MAIN_HAND,stack);h.assertFalse(create(h,user),"Existing bogey is a size action, not a second destructive placement");h.assertTrue(bogey(h)==original&&stack.getCount()==2,"Original bogey identity and casing retained");h.succeed();
    }
    @GameTest public void stationSizeKernelRetainsSourceDataWithoutAnExtraCasing(GameTestHelper h){
        place(h,AllBlocks.SMALL_BOGEY);var data=bogey(h).getBogeyData().copy();data.putInt("Marker",71);bogey(h).setBogeyData(data);var user=player(h);h.assertTrue(user.getMainHandItem().isEmpty()&&BogeyPlacement.cycleSize(user,h.absolutePos(P)),"Source station existing-bogey action needs no extra casing");h.assertTrue(state(h).is(AllBlocks.LARGE_BOGEY)&&bogey(h).getBogeyData().equals(data),"Real replacement preserves source payload");h.succeed();
    }
    @GameTest public void unloadedForeignTrackRequestCreatesNoLoadingTicket(GameTestHelper h){
        var far=new BlockPos(2600000,80,2600000);var user=player(h);user.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.RAILWAY_CASING,2));var track=AllBlocks.TRACK;
        h.assertFalse(BogeyPlacement.placeAtAuthorizedTrack(user,InteractionHand.MAIN_HAND,track,track.defaultBlockState(),far),"Unloaded/out-of-reach request refused");h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null&&user.getMainHandItem().getCount()==2,"No implicit ticket or casing loss");h.succeed();
    }
}
