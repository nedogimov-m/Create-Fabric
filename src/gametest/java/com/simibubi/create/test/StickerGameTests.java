package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.chassis.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Real pulse edges, both structural attachment directions, native restitution and saved cargo ownership. */
public class StickerGameTests {
    private static final BlockPos P=new BlockPos(4,5,4);
    private static BlockState sticker(Direction facing,boolean extended){return AllBlocks.STICKER.defaultBlockState().setValue(StickerBlock.FACING,facing).setValue(StickerBlock.EXTENDED,extended);}
    private static BlockState state(GameTestHelper h){return h.getLevel().getBlockState(h.absolutePos(P));}
    private static BearingContraption assemble(GameTestHelper h,BlockPos root)throws Exception{var structure=new BearingContraption(false,Direction.UP);h.assertTrue(structure.assemble(h.getLevel(),h.absolutePos(root.below())),"Capture actual supporting structure");return structure;}
    private static void pulse(GameTestHelper h){h.setBlock(P.north(),Blocks.REDSTONE_BLOCK);h.setBlock(P.north(),Blocks.AIR);}
    @GameTest public void realRedstoneRisingEdgesToggleWhileFallingEdgesKeepLatch(GameTestHelper h){
        h.setBlock(P,sticker(Direction.UP,false));h.setBlock(P.north(),Blocks.REDSTONE_BLOCK);h.assertTrue(state(h).getValue(StickerBlock.POWERED)&&state(h).getValue(StickerBlock.EXTENDED),"First rising edge extends");
        h.getLevel().neighborChanged(state(h),h.absolutePos(P),Blocks.REDSTONE_BLOCK,null,false);h.assertTrue(state(h).getValue(StickerBlock.EXTENDED),"Held high does not keep toggling");h.setBlock(P.north(),Blocks.AIR);h.assertTrue(!state(h).getValue(StickerBlock.POWERED)&&state(h).getValue(StickerBlock.EXTENDED),"Falling edge retains extension");pulse(h);h.assertFalse(state(h).getValue(StickerBlock.EXTENDED),"Second rising edge retracts");pulse(h);h.assertTrue(state(h).getValue(StickerBlock.EXTENDED),"Third rising edge extends again");h.succeed();
    }
    @GameTest public void poweredPlacementSamplesInputWithoutInventingFirstToggle(GameTestHelper h){
        h.setBlock(P.north(),Blocks.REDSTONE_BLOCK);h.setBlock(P.below(),Blocks.STONE);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(h.absolutePos(P)).add(0,0,3));player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.STICKER));var below=h.absolutePos(P.below());var input=new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(below).add(0,.5,0),Direction.UP,below,false)));var placed=AllBlocks.STICKER.getStateForPlacement(input);h.assertTrue(placed.getValue(StickerBlock.POWERED)&&!placed.getValue(StickerBlock.EXTENDED),"Native placement preserves initial power without a fictional rising edge");h.assertValueEqual(placed.getValue(StickerBlock.FACING),input.getNearestLookingDirection().getOpposite(),"Normal placement opposes look");player.setShiftKeyDown(true);h.assertValueEqual(AllBlocks.STICKER.getStateForPlacement(input).getValue(StickerBlock.FACING),input.getNearestLookingDirection(),"Sneak uses look direction");h.succeed();
    }
    @GameTest public void stickerDoesNotRelayNeighborStrongPower(GameTestHelper h){h.setBlock(P,sticker(Direction.UP,false));h.setBlock(P.north(),Blocks.REDSTONE_BLOCK);for(var side:Direction.values())h.assertValueEqual(h.getLevel().getSignal(h.absolutePos(P),side),0,"Native nonconduction replaces source weak-power opt-out");h.succeed();}
    @GameTest public void extendedStickerRootCapturesItsFrontCargo(GameTestHelper h)throws Exception{h.setBlock(P,sticker(Direction.EAST,true));h.setBlock(P.east(),Blocks.BARREL);h.assertValueEqual(assemble(h,P).getBlocks().size(),2,"Sticker-to-target frontier attachment");h.succeed();}
    @GameTest public void movingTargetAlsoCapturesItsAttachedExtendedSticker(GameTestHelper h)throws Exception{h.setBlock(P,sticker(Direction.EAST,true));h.setBlock(P.east(),Blocks.BARREL);h.assertValueEqual(assemble(h,P.east()).getBlocks().size(),2,"Target-to-sticker BlockMovementChecks attachment");h.succeed();}
    @GameTest public void retractedStickerHasNoImplicitFrontAttachment(GameTestHelper h)throws Exception{h.setBlock(P,sticker(Direction.EAST,false));h.setBlock(P.east(),Blocks.BARREL);h.assertValueEqual(assemble(h,P).getBlocks().size(),1,"Retracted sticker does not take front target");h.assertValueEqual(assemble(h,P.east()).getBlocks().size(),1,"Target does not take retracted sticker");h.succeed();}
    @GameTest public void stickerRequiresSupportiveTargetFace(GameTestHelper h)throws Exception{h.setBlock(P,sticker(Direction.EAST,true));h.setBlock(P.east(),AllBlocks.MECHANICAL_DRILL.defaultBlockState().setValue(BlockStateProperties.FACING,Direction.WEST));h.assertValueEqual(assemble(h,P).getBlocks().size(),1,"An opposing drill front is not an implicit attachment surface");h.succeed();}
    @GameTest public void explicitGlueRemainsIndependentOfRetractedStickerFront(GameTestHelper h)throws Exception{h.setBlock(P,sticker(Direction.EAST,false));h.setBlock(P.east(),Blocks.BARREL);h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(P),h.absolutePos(P.east()))));h.assertValueEqual(assemble(h,P).getBlocks().size(),2,"Existing explicit glue is not erased by the retracted support predicate");h.succeed();}
    @GameTest public void retractedFrontIsNonsupportiveButSidesAreOrdinary(GameTestHelper h){for(var facing:Direction.values())for(var side:Direction.values()){h.assertTrue(BlockMovementChecks.isNotSupportive(sticker(facing,false),side)==(facing==side),"Only retracted facing is nonsupportive");h.assertFalse(BlockMovementChecks.isNotSupportive(sticker(facing,true),side),"Extended sticker supports its ordinary faces");}h.succeed();}
    @GameTest public void stickyFacePreviewClassificationRetainsSourceLatchDistinction(GameTestHelper h){h.setBlock(P,sticker(Direction.EAST,false));h.assertTrue(SuperGlueEntity.isSideSticky(h.getLevel(),h.absolutePos(P),Direction.EAST)&&!SuperGlueEntity.isSideSticky(h.getLevel(),h.absolutePos(P),Direction.WEST),"Source visual sticky face is facing even when latch is retracted");h.succeed();}
    @GameTest public void registeredAttachedCheckOverridesExtendedStickerFallback(GameTestHelper h){
        h.setBlock(P,sticker(Direction.EAST,true));h.setBlock(P.east(),Blocks.STONE);var active=new AtomicBoolean(true);var actual=h.absolutePos(P);
        BlockMovementChecks.registerAttachedCheck((s,l,p,d)->active.get()&&l==h.getLevel()&&p.equals(actual)&&s.getBlock() instanceof StickerBlock?BlockMovementChecks.CheckResult.SUCCESS:BlockMovementChecks.CheckResult.PASS);
        BlockMovementChecks.registerAttachedCheck((s,l,p,d)->active.get()&&l==h.getLevel()&&p.equals(actual)&&s.getBlock() instanceof StickerBlock?BlockMovementChecks.CheckResult.FAIL:BlockMovementChecks.CheckResult.PASS);
        try{h.assertFalse(BlockMovementChecks.isBlockAttachedTowards(state(h),h.getLevel(),actual,Direction.EAST),"Last registered attachment check wins before extended front rule");}finally{active.set(false);}
        h.assertTrue(BlockMovementChecks.isBlockAttachedTowards(state(h),h.getLevel(),actual,Direction.EAST),"PASS callbacks restore real extended attachment");h.succeed();
    }
    @GameTest public void registeredSupportCheckOverridesRetractedStickerFront(GameTestHelper h){
        var block=sticker(Direction.EAST,false);var active=new AtomicBoolean(true);
        BlockMovementChecks.registerNotSupportiveCheck((s,d)->active.get()&&s==block?BlockMovementChecks.CheckResult.SUCCESS:BlockMovementChecks.CheckResult.PASS);
        BlockMovementChecks.registerNotSupportiveCheck((s,d)->active.get()&&s==block?BlockMovementChecks.CheckResult.FAIL:BlockMovementChecks.CheckResult.PASS);
        try{h.assertFalse(BlockMovementChecks.isNotSupportive(block,Direction.EAST),"Last registered support override precedes the retracted front fallback");}finally{active.set(false);}
        h.assertTrue(BlockMovementChecks.isNotSupportive(block,Direction.EAST),"PASS callbacks restore nonsupportive retracted front");h.succeed();
    }
    @GameTest public void extendedStickerStillUsesRegisteredBrittleFallback(GameTestHelper h){
        var block=sticker(Direction.EAST,true);var active=new AtomicBoolean(true);
        BlockMovementChecks.registerBrittleCheck(s->active.get()&&s==block?BlockMovementChecks.CheckResult.SUCCESS:BlockMovementChecks.CheckResult.PASS);
        try{h.assertTrue(BlockMovementChecks.isNotSupportive(block,Direction.WEST),"Extended sticker still reaches the public brittle fallback");}finally{active.set(false);}
        h.assertFalse(BlockMovementChecks.isNotSupportive(block,Direction.WEST),"Inert test hook leaves ordinary extended support unchanged");h.succeed();
    }
    @GameTest public void actualAttachedFaceIgnoresUnknownChunks(GameTestHelper h){var far=new BlockPos(2000000,60,2000000);var sticker=new StickerBlockEntity(far,sticker(Direction.EAST,true));sticker.setLevel(h.getLevel());h.assertFalse(sticker.isAttachedToBlock(),"Missing neighbor cannot be declared an attachment");h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Attachment check does not load terrain");h.succeed();}
    @GameTest public void nativeLoadBeforeSetLevelStartsAtSavedExtension(GameTestHelper h){var block=sticker(Direction.WEST,true);var original=new StickerBlockEntity(h.absolutePos(P),block);var tag=original.saveWithFullMetadata(h.getLevel().registryAccess());var copy=new StickerBlockEntity(h.absolutePos(P),block);copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),tag));copy.setLevel(h.getLevel());h.assertTrue(copy.isBlockStateExtended()&&copy.getPiston(0)==1&&copy.getPiston(1)==1,"Saved block latch initializes settled interpolation before native level assignment");h.succeed();}
    private static void collision(GameTestHelper h,Direction facing,boolean living,boolean suppress){
        h.setBlock(P,sticker(facing,false));Entity entity;var pos=Vec3.atLowerCornerOf(h.absolutePos(P)).add(.5,1.1,.5);
        if(living){var cow=h.spawn(EntityTypes.COW,P.above(2));cow.setNoAi(true);entity=cow;}else{var item=new ItemEntity(h.getLevel(),pos.x,pos.y,pos.z,new ItemStack(Items.STONE));h.getLevel().addFreshEntity(item);entity=item;}
        entity.setPos(pos);entity.setShiftKeyDown(suppress);entity.setDeltaMovement(0,-.4,0);entity.move(MoverType.SELF,new Vec3(0,-.4,0));
        h.assertTrue(entity.verticalCollisionBelow,"Real native entity movement reached sticker surface");if(facing==Direction.UP&&!suppress)h.assertTrue(entity.getDeltaMovement().y>0,"Native restitution solver bounces upward for actual upright sticker");else h.assertTrue(entity.getDeltaMovement().y<=0,"Side/down/sneak preserve normal native landing");entity.discard();h.succeed();
    }
    @GameTest public void realLivingLandingBouncesOnlyOnUprightSticker(GameTestHelper h){collision(h,Direction.UP,true,false);}
    @GameTest public void realItemLandingBouncesOnUprightSticker(GameTestHelper h){collision(h,Direction.UP,false,false);}
    @GameTest public void sidewaysStickerDoesNotGiveSlimeBounce(GameTestHelper h){collision(h,Direction.EAST,false,false);}
    @GameTest public void downwardStickerDoesNotGiveSlimeBounce(GameTestHelper h){collision(h,Direction.DOWN,true,false);}
    @GameTest public void suppressingBounceWinsOverUprightSticker(GameTestHelper h){collision(h,Direction.UP,true,true);}
    @GameTest public void uprightSteppingSlowsOnlyNonCarefulHorizontalMotion(GameTestHelper h){
        h.setBlock(P,sticker(Direction.UP,false));var player=h.makeMockPlayer(GameType.SURVIVAL);player.setDeltaMovement(.5,.05,.5);AllBlocks.STICKER.stepOn(h.getLevel(),h.absolutePos(P),state(h),player);h.assertTrue(Math.abs(player.getDeltaMovement().x-.205)<.0001&&player.getDeltaMovement().y==.05,"Source step multiplier .4+abs(vY)*.2");player.setShiftKeyDown(true);player.setDeltaMovement(.5,.05,.5);AllBlocks.STICKER.stepOn(h.getLevel(),h.absolutePos(P),state(h),player);h.assertTrue(player.getDeltaMovement().x==.5,"Careful stepping retains velocity");h.succeed();
    }
    @GameTest(maxTicks=80,skyAccess=true) public void actualFallKeepsSourceDamageWhileBouncing(GameTestHelper h){
        h.setBlock(P,sticker(Direction.UP,false));
        // The native default structure is eight blocks tall; its barrier roof otherwise
        // catches this seven-block fall above the real sticker before it can land.
        for(int height=1;height<=9;height++)h.assertBlockPresent(Blocks.AIR,P.above(height));
        var cow=h.spawn(EntityTypes.COW,P.above(7));cow.setNoAi(false);cow.removeFreeWill();float initial=cow.getHealth();
        h.runAfterDelay(28,()->{h.assertTrue(cow.isAlive()&&cow.getHealth()<initial,"Sticker retains source ordinary fall damage, unlike vanilla SlimeBlock; health="+cow.getHealth()+", y="+cow.getY()+", velocity="+cow.getDeltaMovement());h.assertTrue(cow.getY()>h.absolutePos(P).getY()+1.1,"Actual landing produced an upward bounce");cow.discard();h.succeed();});
    }
    @GameTest public void savedMovingStickerAndNamedCargoRestoreWithoutInventedActor(GameTestHelper h)throws Exception{
        h.setBlock(P,sticker(Direction.EAST,true));h.setBlock(P.east(),Blocks.BARREL);var cargo=new ItemStack(Items.DIAMOND,7);cargo.set(DataComponents.CUSTOM_NAME,Component.literal("Sticker cargo"));((BarrelBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(P.east()))).setItem(0,cargo);var structure=assemble(h,P);h.assertTrue(structure.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"Take real ownership");h.assertTrue(MovementBehaviour.REGISTRY.get(AllBlocks.STICKER)==null&&structure.getActors().isEmpty(),"Sticker is a captured latch/BE, not an invented world-redstone actor");
        var restored=Contraption.fromNBT(h.getLevel(),structure.writeNBT(h.getLevel().registryAccess(),false),false);var transform=new StructureTransform(h.absolutePos(P),0,90,0);h.assertTrue(restored.tryAddBlocksToWorld(h.getLevel(),transform),"Restore saved native moving sticker");var block=h.getLevel().getBlockState(transform.apply(BlockPos.ZERO));h.assertTrue(block.getValue(StickerBlock.EXTENDED)&&block.getValue(StickerBlock.FACING)==transform.rotateFacing(Direction.EAST),"Captured latch and real orientation survive rotation");var result=((BarrelBlockEntity)h.getLevel().getBlockEntity(transform.apply(new BlockPos(1,0,0)))).getItem(0);h.assertTrue(result.getCount()==7&&result.has(DataComponents.CUSTOM_NAME),"Exactly seven named diamonds restored");h.succeed();
    }
}
