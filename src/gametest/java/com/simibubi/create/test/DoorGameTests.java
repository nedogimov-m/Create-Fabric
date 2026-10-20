package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.decoration.slidingDoor.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Real world door halves/redstone/ownership tests; rendering is validated separately by a native client. */
public class DoorGameTests {
    private static final BlockPos P=new BlockPos(4,5,4);
    private static BlockState door(SlidingDoorBlock block,DoorHingeSide hinge){return block.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HINGE,hinge);}
    private static void place(GameTestHelper h,BlockPos pos,BlockState state){h.setBlock(pos,state);h.setBlock(pos.above(),state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));}
    private static BlockState state(GameTestHelper h,BlockPos pos){return h.getLevel().getBlockState(h.absolutePos(pos));}
    private static void assertDoor(GameTestHelper h,BlockPos pos,boolean open){for(var half:new BlockPos[]{pos,pos.above()})h.assertTrue(state(h,half).getValue(DoorBlock.OPEN)==open,"Both native halves share open="+open+" at "+half);}
    private static void pair(GameTestHelper h){place(h,P,door(AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT));place(h,P.east(),door(AllBlocks.TRAIN_DOOR,DoorHingeSide.RIGHT));}
    private static ControlledContraptionEntity moving(GameTestHelper h,boolean paired)throws Exception{
        if(paired)pair(h);else place(h,P,door(AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT));
        // A real glued pair is captured through the pinned source traversal rules.
        h.setBlock(P.below(),Blocks.SLIME_BLOCK);if(paired)h.setBlock(P.east().below(),Blocks.SLIME_BLOCK);
        var structure=new BearingContraption(false,Direction.UP);var root=P.below();
        h.assertTrue(structure.assemble(h.getLevel(),h.absolutePos(root.below())),"Capture door structure");
        h.assertTrue(structure.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"Atomic transfer");
        var owner=new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION,h.getLevel());owner.setContraption(structure);owner.setPos(Vec3.atLowerCornerOf(h.absolutePos(root)));h.getLevel().addFreshEntity(owner);owner.tickActors();return owner;
    }
    private static BlockPos localDoor(ControlledContraptionEntity owner){return owner.getContraption().getBlocks().entrySet().stream().filter(entry->entry.getValue().state().getBlock() instanceof SlidingDoorBlock&&entry.getValue().state().getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER).map(java.util.Map.Entry::getKey).min(java.util.Comparator.comparingInt(BlockPos::getX)).orElseThrow();}
    private static MovementContext context(ControlledContraptionEntity owner){return owner.getContraption().getActorAt(localDoor(owner)).right;}
    @GameTest public void everySourceDoorHasItsCorrectFoldingAndMaterialMode(GameTestHelper h){
        h.assertTrue(AllBlocks.ANDESITE_DOOR.isFoldingDoor()&&AllBlocks.COPPER_DOOR.isFoldingDoor()&&!AllBlocks.BRASS_DOOR.isFoldingDoor()&&!AllBlocks.TRAIN_DOOR.isFoldingDoor()&&!AllBlocks.FRAMED_GLASS_DOOR.isFoldingDoor(),"All five upstream variants");
        for(var block:new SlidingDoorBlock[]{AllBlocks.ANDESITE_DOOR,AllBlocks.BRASS_DOOR,AllBlocks.COPPER_DOOR,AllBlocks.TRAIN_DOOR,AllBlocks.FRAMED_GLASS_DOOR})h.assertTrue(block.type().canOpenByHand()&&block.defaultBlockState().getValue(SlidingDoorBlock.VISIBLE),"Native hand interaction and visible closed default");h.succeed();
    }
    @GameTest public void realItemPlacementCreatesTwoHalvesAndOneBlockEntity(GameTestHelper h){
        h.setBlock(P.below(),Blocks.STONE);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(h.absolutePos(P)).add(0,0,3));player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.TRAIN_DOOR));
        var support=h.absolutePos(P.below());var hit=new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false);var result=player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
        h.assertTrue(result.consumesAction()&&state(h,P).is(AllBlocks.TRAIN_DOOR)&&state(h,P.above()).is(AllBlocks.TRAIN_DOOR),"Native BlockItem placement");
        h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(P)) instanceof SlidingDoorBlockEntity&&h.getLevel().getBlockEntity(h.absolutePos(P.above()))==null,"Only lower half owns native BE");h.succeed();
    }
    @GameTest public void floatingDoorSurvivesRemovingItsFloor(GameTestHelper h){h.setBlock(P.below(),Blocks.STONE);place(h,P,door(AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT));h.setBlock(P.below(),Blocks.AIR);h.runAfterDelay(2,()->{h.assertBlockPresent(AllBlocks.TRAIN_DOOR,P);h.assertBlockPresent(AllBlocks.TRAIN_DOOR,P.above());h.succeed();});}
    @GameTest public void breakingUpperRemovesLowerWithoutLeavingBlockEntity(GameTestHelper h){place(h,P,door(AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT));h.getLevel().destroyBlock(h.absolutePos(P.above()),false);h.assertTrue(state(h,P).isAir()&&h.getLevel().getBlockEntity(h.absolutePos(P))==null,"Paired vertical cleanup");h.succeed();}
    @GameTest public void lowerClickOpensAllFourPairedHalves(GameTestHelper h){pair(h);h.useBlock(P);assertDoor(h,P,true);assertDoor(h,P.east(),true);h.assertFalse(state(h,P).getValue(SlidingDoorBlock.VISIBLE),"Static models hidden during opening");h.succeed();}
    @GameTest public void upperClickClosesPairAndTickerRestoresAllModels(GameTestHelper h){pair(h);h.useBlock(P.above());h.runAfterDelay(9,()->{h.useBlock(P.east().above());assertDoor(h,P,false);assertDoor(h,P.east(),false);h.runAfterDelay(9,()->{for(var pos:new BlockPos[]{P,P.above(),P.east(),P.east().above()})h.assertTrue(state(h,pos).getValue(SlidingDoorBlock.VISIBLE),"Server closing animation restores all four models");h.succeed();});});}
    @GameTest public void differentMaterialOrSameHingeDoesNotPair(GameTestHelper h){place(h,P,door(AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT));place(h,P.east(),door(AllBlocks.BRASS_DOOR,DoorHingeSide.RIGHT));h.useBlock(P);assertDoor(h,P.east(),false);h.succeed();}
    @GameTest public void sameHingeDoorsStayIndependent(GameTestHelper h){place(h,P,door(AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT));place(h,P.east(),door(AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT));h.useBlock(P);assertDoor(h,P.east(),false);h.succeed();}
    @GameTest public void actualUpperRedstonePowersAndReleasesWholePair(GameTestHelper h){pair(h);h.runAfterDelay(2,()->{h.setBlock(P.above().north(),Blocks.REDSTONE_BLOCK);h.runAfterDelay(2,()->{assertDoor(h,P,true);assertDoor(h,P.east(),true);for(var pos:new BlockPos[]{P,P.above(),P.east(),P.east().above()})h.assertTrue(state(h,pos).getValue(DoorBlock.POWERED),"Shared native redstone power");h.setBlock(P.above().north(),Blocks.AIR);h.runAfterDelay(9,()->{assertDoor(h,P,false);assertDoor(h,P.east(),false);h.succeed();});});});}
    @GameTest public void deferredPlacementDiscoversAlreadyPoweredNeighbor(GameTestHelper h){h.setBlock(P.north(),Blocks.REDSTONE_BLOCK);place(h,P,door(AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT));h.assertFalse(state(h,P).getValue(DoorBlock.OPEN),"Placement starts closed");h.runAfterDelay(2,()->{assertDoor(h,P,true);h.succeed();});}
    @GameTest public void hiddenClosedDoorRecoversAfterNativeReload(GameTestHelper h){
        place(h,P,door(AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT).setValue(SlidingDoorBlock.VISIBLE,false));var old=(SlidingDoorBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(P));var tag=old.saveWithFullMetadata(h.getLevel().registryAccess());h.getLevel().removeBlockEntity(h.absolutePos(P));var replacement=new SlidingDoorBlockEntity(h.absolutePos(P),state(h,P));replacement.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),tag));h.getLevel().setBlockEntity(replacement);
        h.runAfterDelay(9,()->{h.assertTrue(state(h,P).getValue(SlidingDoorBlock.VISIBLE)&&state(h,P.above()).getValue(SlidingDoorBlock.VISIBLE),"Saved hidden closing states cannot remain invisible");h.succeed();});
    }
    @GameTest public void openShapeSlidesOutsideItsCellAndFoldingShapeIsShorter(GameTestHelper h){
        var regular=door(AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT).setValue(DoorBlock.OPEN,true);var folded=door(AllBlocks.ANDESITE_DOOR,DoorHingeSide.LEFT).setValue(DoorBlock.OPEN,true);
        var a=regular.getShape(h.getLevel(),h.absolutePos(P)).bounds();var b=folded.getShape(h.getLevel(),h.absolutePos(P)).bounds();h.assertTrue(a.minX<0&&b.minX>a.minX&&a.getXsize()==1&&b.getXsize()==.375,"Source sliding and folding outline extents");h.succeed();
    }
    @GameTest public void captureIncludesUpperHalfAndNativeDoorBlockEntity(GameTestHelper h)throws Exception{var owner=moving(h,false);var local=localDoor(owner);h.assertTrue(owner.getContraption().getBlocks().size()==3&&owner.getContraption().getActors().size()==2&&owner.getContraption().getBlocks().get(local).nbt()!=null,"Supporting root, both halves and private lower BE captured");var captured=owner.getContraption().getBlocks().get(local).state();var bounds=captured.getCollisionShape(owner.getContraption().getCollisionLevel(),local).bounds();h.assertTrue(bounds.minX==0&&bounds.maxX==1,"Closed invisible mounted door collision must stay in its original cell, rather than using the placed-door animation outline cache");owner.discard();h.succeed();}
    @GameTest public void movingClickUpdatesBothStatesActorsAndCollisionShapes(GameTestHelper h)throws Exception{
        var owner=moving(h,false);var local=localDoor(owner);var behaviour=MovingInteractionBehaviour.REGISTRY.get(AllBlocks.TRAIN_DOOR);h.assertTrue(behaviour.handlePlayerInteraction(h.makeMockPlayer(GameType.SURVIVAL),InteractionHand.MAIN_HAND,local.above(),owner),"Registered moving interaction");
        for(var pos:new BlockPos[]{local,local.above()}){var info=owner.getContraption().getBlocks().get(pos);h.assertTrue(info.state().getValue(DoorBlock.OPEN)&&owner.getContraption().getActorAt(pos).right.state.getValue(DoorBlock.OPEN),"Captured and actor states update atomically");}
        var shape=new ContraptionWorld(owner.getContraption()).getBlockState(local).getCollisionShape(new ContraptionWorld(owner.getContraption()),local).bounds();h.assertTrue(shape.minX<0,"Actual contraption collision follows open sliding leaf");owner.discard();h.succeed();
    }
    @GameTest public void movingPairedClickAndDisabledTickCloseEveryLeaf(GameTestHelper h)throws Exception{
        var owner=moving(h,true);var local=localDoor(owner);MovingInteractionBehaviour.REGISTRY.get(AllBlocks.TRAIN_DOOR).handlePlayerInteraction(h.makeMockPlayer(GameType.SURVIVAL),InteractionHand.MAIN_HAND,local,owner);
        h.assertTrue(owner.getContraption().getBlocks().values().stream().filter(info->info.state().getBlock() instanceof SlidingDoorBlock).allMatch(info->info.state().getValue(DoorBlock.OPEN)),"Both two-part moving leaves open");
        // The source only changes doors when the desired state changes; explicit disable is such a change.
        for(var pair:owner.getContraption().getActors())pair.right.data.putBoolean("Open",true);owner.getContraption().setActorsActive(new ItemStack(AllBlocks.TRAIN_DOOR),false);owner.tickActors();
        h.assertTrue(owner.getContraption().getBlocks().values().stream().filter(info->info.state().getBlock() instanceof SlidingDoorBlock).noneMatch(info->info.state().getValue(DoorBlock.OPEN)),"Disabled actor still ticks to close");owner.discard();h.succeed();
    }
    @GameTest public void manualMovingDoorRemainsOpenUntilAutomaticPolicyChanges(GameTestHelper h)throws Exception{
        var owner=moving(h,false);var local=localDoor(owner);MovingInteractionBehaviour.REGISTRY.get(AllBlocks.TRAIN_DOOR).handlePlayerInteraction(h.makeMockPlayer(GameType.SURVIVAL),InteractionHand.MAIN_HAND,local,owner);owner.tickActors();h.assertTrue(owner.getContraption().getBlocks().get(local).state().getValue(DoorBlock.OPEN),"Unchanged automatic false policy preserves manual override");owner.discard();h.succeed();
    }
    @GameTest public void openMovingDoorStateSurvivesNativeContraptionSave(GameTestHelper h)throws Exception{
        var owner=moving(h,false);var local=localDoor(owner);SlidingDoorMovementBehaviour.setDoorOpen(owner.getContraption(),local,true,false);var saved=owner.getContraption().writeNBT(h.getLevel().registryAccess(),false);var restored=Contraption.fromNBT(h.getLevel(),saved,false);h.assertTrue(restored.getBlocks().get(local).state().getValue(DoorBlock.OPEN)&&restored.getBlocks().get(local.above()).state().getValue(DoorBlock.OPEN),"Both authoritative block states survive native NBT");owner.discard();h.succeed();
    }
    @GameTest public void actualMovingDisassemblyRestoresTwoOperableHalves(GameTestHelper h)throws Exception{
        var owner=moving(h,false);SlidingDoorMovementBehaviour.setDoorOpen(owner.getContraption(),localDoor(owner),true,false);h.assertTrue(owner.disassemble(),"Real owner disassembles");assertDoor(h,P,true);h.useBlock(P.above());assertDoor(h,P,false);h.runAfterDelay(9,()->{h.assertTrue(state(h,P).getValue(SlidingDoorBlock.VISIBLE),"Restored lower ticker closes native model");h.succeed();});
    }
    @GameTest public void nativeDoorControlBehaviourPersistsModeAndMatchesDirections(GameTestHelper h){
        place(h,P,door(AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT));var be=h.getLevel().getBlockEntity(h.absolutePos(P));var control=new DoorControlBehaviour(be);control.set(DoorControl.WEST);var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,h.getLevel().registryAccess());control.write(output);var restored=new DoorControlBehaviour(be);restored.read(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),output.buildResult()));h.assertTrue(restored.mode==DoorControl.WEST&&restored.mode.matches(Direction.WEST)&&!restored.mode.matches(Direction.EAST)&&DoorControl.ALL.matches(Direction.NORTH)&&!DoorControl.NONE.matches(Direction.NORTH),"Source enum persistence and directional settings");h.succeed();
    }
    @GameTest public void firstMovementAndActualMotionPreventAutomaticOpening(GameTestHelper h)throws Exception{
        var owner=moving(h,false);var ctx=context(owner);var probe=new SlidingDoorMovementBehaviour(){boolean update(boolean open){return shouldUpdate(ctx,open);}boolean can(){return canOpen(ctx);}};
        ctx.firstMovement=true;h.assertFalse(probe.update(true),"First movement never opens");ctx.firstMovement=false;h.assertTrue(probe.update(true),"Settled next policy transition");ctx.motion=new Vec3(.1,0,0);h.assertFalse(probe.can(),"Moving owner cannot open");ctx.motion=Vec3.ZERO;owner.setStalled(true);h.assertFalse(probe.can(),"Stalled owner cannot open");owner.discard();h.succeed();
    }
    @GameTest public void missingWorldDoorQueryNeverLoadsChunks(GameTestHelper h)throws Exception{
        var owner=moving(h,false);var ctx=context(owner);SlidingDoorMovementBehaviour.setDoorOpen(owner.getContraption(),localDoor(owner),true,false);ctx.position=new Vec3(2000000,60,2000000);ctx.firstMovement=false;ctx.data.putBoolean("Open",true);MovementBehaviour.REGISTRY.get(AllBlocks.TRAIN_DOOR).tick(ctx);h.assertTrue(h.getLevel().getChunkSource().getChunkNow(2000000>>4,2000000>>4)==null&&!owner.getContraption().getBlocks().get(localDoor(owner)).state().getValue(DoorBlock.OPEN),"Moving-door world lookup closes actor and uses no tickets");owner.discard();h.succeed();
    }
    @GameTest public void actualWrenchOnUpperRotatesBothHalves(GameTestHelper h){
        place(h,P,door(AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT));var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH));var clicked=h.absolutePos(P.above());
        var result=AllItems.WRENCH.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(clicked),Direction.UP,clicked,false)));h.assertTrue(result.consumesAction()&&state(h,P).getValue(DoorBlock.FACING)==Direction.EAST&&state(h,P.above()).getValue(DoorBlock.FACING)==Direction.EAST,"Native wrench route preserves paired vertical facing");h.succeed();
    }
    @GameTest public void shiftWrenchOnUpperRefundsExactlyOneDoor(GameTestHelper h){
        place(h,P,door(AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT));var player=h.makeMockPlayer(GameType.SURVIVAL);player.setShiftKeyDown(true);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH));var clicked=h.absolutePos(P.above());
        AllItems.WRENCH.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(clicked),Direction.UP,clicked,false)));int count=0;for(int slot=0;slot<player.getInventory().getContainerSize();slot++)if(player.getInventory().getItem(slot).is(AllBlocks.TRAIN_DOOR.asItem()))count+=player.getInventory().getItem(slot).getCount();
        h.assertTrue(count==1&&state(h,P).isAir()&&state(h,P.above()).isAir()&&h.getLevel().getBlockEntity(h.absolutePos(P))==null,"One native door loot refund with both halves removed");h.succeed();
    }
    @GameTest public void captureHidesBothMountedModelsWithoutChangingSourceBlocks(GameTestHelper h)throws Exception{
        h.setBlock(P.below(),Blocks.SLIME_BLOCK);place(h,P,door(AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT));var structure=new BearingContraption(false,Direction.UP);h.assertTrue(structure.assemble(h.getLevel(),h.absolutePos(P.below(2))),"Supporting slime captures door");
        for(var pos:new BlockPos[]{new BlockPos(0,1,0),new BlockPos(0,2,0)})h.assertFalse(structure.getBlocks().get(pos).state().getValue(SlidingDoorBlock.VISIBLE),"All mounted halves use animated geometry");h.assertTrue(state(h,P).getValue(SlidingDoorBlock.VISIBLE)&&state(h,P.above()).getValue(SlidingDoorBlock.VISIBLE),"Capture still leaves actual source ownership untouched");h.succeed();
    }
    private static void restorePowered(GameTestHelper h,boolean open)throws Exception{
        h.setBlock(P.below(),Blocks.SLIME_BLOCK);place(h,P,door(AllBlocks.TRAIN_DOOR,DoorHingeSide.LEFT).setValue(DoorBlock.POWERED,true).setValue(DoorBlock.OPEN,open).setValue(SlidingDoorBlock.VISIBLE,!open));
        var structure=new BearingContraption(false,Direction.UP);h.assertTrue(structure.assemble(h.getLevel(),h.absolutePos(P.below(2)))&&structure.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"Native ownership transfer from powered saved state");
        var owner=new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION,h.getLevel());owner.setContraption(structure);owner.setPos(Vec3.atLowerCornerOf(h.absolutePos(P.below())));h.getLevel().addFreshEntity(owner);h.assertTrue(owner.disassemble(),"Restore captured door without actor policy change");
        for(var pos:new BlockPos[]{P,P.above()})h.assertTrue(state(h,pos).getValue(DoorBlock.OPEN)==open&&!state(h,pos).getValue(DoorBlock.POWERED)&&state(h,pos).getValue(SlidingDoorBlock.VISIBLE)==!open,"Source restore derives visibility and resets stale power");h.succeed();
    }
    @GameTest public void closedRestorationShowsModelsAndResetsCapturedPower(GameTestHelper h)throws Exception{restorePowered(h,false);}
    @GameTest public void openRestorationKeepsAnimatedModelsAndResetsCapturedPower(GameTestHelper h)throws Exception{restorePowered(h,true);}
    @GameTest public void openMovingLeafCanBeRaySelectedOutsideItsOriginalCell(GameTestHelper h)throws Exception{
        var owner=moving(h,false);var local=localDoor(owner);SlidingDoorMovementBehaviour.setDoorOpen(owner.getContraption(),local,true,false);var start=owner.toGlobalVector(Vec3.atLowerCornerOf(local).add(-.4,.5,3),1);var end=owner.toGlobalVector(Vec3.atLowerCornerOf(local).add(-.4,.5,-1),1);
        var view=new ContraptionWorld(owner.getContraption());var captured=owner.getContraption().getBlocks().get(local).state();var shape=captured.getShape(view,local);var from=owner.toLocalVector(start,1);var to=owner.toLocalVector(end,1);var direct=shape.clip(from,to,local);var hit=owner.rayTrace(start,end,1);
        h.assertTrue(hit!=null&&hit.getBlockPos().equals(local),"Source neighbor-aware moving ray selects slid leaf outside original block cell: state="+captured+", local="+local+", shape="+shape.bounds()+", from="+from+", to="+to+", direct="+(direct==null?null:direct.getBlockPos()+"/"+direct.getLocation())+", actual="+(hit==null?null:hit.getBlockPos()+"/"+hit.getLocation()));MovingInteractionBehaviour.REGISTRY.get(AllBlocks.TRAIN_DOOR).handlePlayerInteraction(h.makeMockPlayer(GameType.SURVIVAL),InteractionHand.MAIN_HAND,hit.getBlockPos(),owner);h.assertFalse(owner.getContraption().getBlocks().get(local).state().getValue(DoorBlock.OPEN),"Selected slid leaf closes normally");owner.discard();h.succeed();
    }
    private static void verticalDrop(GameTestHelper h,boolean allowDrops)throws Exception{
        var owner=moving(h,false);var structure=owner.getContraption();var transform=new StructureTransform(h.absolutePos(P.east(4)),90,0,0);boolean previous=ContraptionSettings.noDropWhenContraptionReplaceBlocks;ContraptionSettings.noDropWhenContraptionReplaceBlocks=!allowDrops;
        try{h.assertTrue(structure.tryAddBlocksToWorld(h.getLevel(),transform),"Real ownership restoration under horizontal-axis rotation");}finally{ContraptionSettings.noDropWhenContraptionReplaceBlocks=previous;}
        for(var pos:new BlockPos[]{localDoor(owner),localDoor(owner).above()}){var target=transform.apply(pos);h.assertTrue(!h.getLevel().getBlockState(target).is(AllBlocks.TRAIN_DOOR)&&h.getLevel().getBlockEntity(target)==null,"Unrepresentable lying halves and native BE are removed");}
        int count=0;for(var item:h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(h.absolutePos(P.east(4))).inflate(5)))if(item.getItem().is(AllBlocks.TRAIN_DOOR.asItem()))count+=item.getItem().getCount();h.assertValueEqual(count,allowDrops?1:0,"Vertical door restoration drops exactly one source item, honoring the real no-drop setting");h.assertTrue(structure.phase()==Contraption.Phase.DISASSEMBLED&&!structure.tryAddBlocksToWorld(h.getLevel(),transform),"Ownership ends once after vertical drops");owner.discard();h.succeed();
    }
    @GameTest public void verticalRestorationDropsExactlyOneDoorAndEndsOwnership(GameTestHelper h)throws Exception{verticalDrop(h,true);}
    @GameTest public void verticalRestorationHonorsNoDropSetting(GameTestHelper h)throws Exception{verticalDrop(h,false);}
}
