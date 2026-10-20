package com.simibubi.create.test;

import com.simibubi.create.api.contraption.*;
import com.simibubi.create.api.contraption.transformable.*;
import com.simibubi.create.content.contraptions.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;

public class StructureTransformGameTests {
    private static final BlockPos P=new BlockPos(2,3,2);
    private static StructureTransform rotation(Direction.Axis axis,int degrees){return new StructureTransform(BlockPos.ZERO,axis==Direction.Axis.X?degrees:0,axis==Direction.Axis.Y?degrees:0,axis==Direction.Axis.Z?degrees:0);}
    @GameTest public void sourceRotationSignsAgreeForPositionsAndFacingOnAllAxes(GameTestHelper h){
        var x=rotation(Direction.Axis.X,90);var y=rotation(Direction.Axis.Y,90);var z=rotation(Direction.Axis.Z,90);
        h.assertValueEqual(x.rotateFacing(Direction.UP),Direction.SOUTH,"Source positive X turns up to south");h.assertValueEqual(y.rotateFacing(Direction.EAST),Direction.NORTH,"Source positive Y turns east to north");h.assertValueEqual(z.rotateFacing(Direction.EAST),Direction.UP,"Source positive Z turns east to up");
        h.assertTrue(x.applyWithoutOffsetUncentered(new Vec3(0,1,0)).distanceTo(new Vec3(0,0,1))<1e-6,"Vector X sign matches facing");h.assertTrue(y.applyWithoutOffsetUncentered(new Vec3(1,0,0)).distanceTo(new Vec3(0,0,-1))<1e-6,"Vector Y sign matches facing");h.assertTrue(z.applyWithoutOffsetUncentered(new Vec3(1,0,0)).distanceTo(new Vec3(0,1,0))<1e-6,"Vector Z sign matches facing");h.succeed();
    }
    @GameTest public void transformedBlockCoordinatesRoundTripWithAllMirrorsAndQuarterTurns(GameTestHelper h){
        for(var axis:Direction.Axis.values())for(var mirror:Mirror.values())for(var rot:Rotation.values()){
            var transform=new StructureTransform(new BlockPos(10,-7,23),axis,rot,mirror);
            for(var pos:List.of(BlockPos.ZERO,new BlockPos(2,3,4),new BlockPos(-5,1,-9))){h.assertValueEqual(transform.unapply(transform.apply(pos)),pos,"Centered block coordinate inverse");var point=Vec3.atCenterOf(pos).add(.1,-.2,.3);h.assertTrue(transform.unapplyWithoutOffset(transform.applyWithoutOffset(point)).distanceTo(point)<1e-5,"Continuous centered inverse");}
        }
        h.assertValueEqual(new StructureTransform(new BlockPos(10,20,30),90,0,0).apply(new BlockPos(2,3,4)),new BlockPos(12,16,33),"Known source anchored quarter turn");h.succeed();
    }
    @GameTest public void nativeBlockStateFacingAxesLeversStairsAndSlabsUseSourceTransforms(GameTestHelper h){
        h.assertValueEqual(rotation(Direction.Axis.X,90).apply(Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING,Direction.UP)).getValue(DispenserBlock.FACING),Direction.SOUTH,"Six-way facing");
        h.assertValueEqual(rotation(Direction.Axis.X,90).apply(Blocks.OAK_LOG.defaultBlockState()).getValue(BlockStateProperties.AXIS),Direction.Axis.Z,"Log axis");
        h.assertValueEqual(rotation(Direction.Axis.X,180).apply(Blocks.STONE_SLAB.defaultBlockState()).getValue(SlabBlock.TYPE),SlabType.TOP,"Horizontal half turn flips a slab");
        for(var block:List.of(Blocks.OAK_STAIRS,Blocks.OAK_LOG,Blocks.DISPENSER,Blocks.STONE_SLAB))for(var state:block.getStateDefinition().getPossibleStates())for(var axis:Direction.Axis.values()){
            var next=state;for(int i=0;i<4;i++)next=rotation(axis,90).apply(next);
            h.assertValueEqual(next,state,"Four source quarter turns restore native state: "+state+" axis "+axis);
        }
        for(var block:List.of(Blocks.LEVER,Blocks.STONE_BUTTON))for(var state:block.getStateDefinition().getPossibleStates())for(var axis:Direction.Axis.values()){
            var transform=rotation(axis,90);var changed=transform.apply(state);
            for(var direction:Direction.values())if(BlockMovementChecks.isBlockAttachedTowards(state,h.getLevel(),h.absolutePos(P),direction))h.assertTrue(BlockMovementChecks.isBlockAttachedTowards(changed,h.getLevel(),h.absolutePos(P),transform.rotateFacing(direction)),"Face-attached support rotates correctly, including source facing normalization");
        }
        h.succeed();
    }
    @GameTest public void wallBellBecomesSingleAttachmentOnlyWhenRotatedAroundY(GameTestHelper h){var state=Blocks.BELL.defaultBlockState().setValue(BlockStateProperties.BELL_ATTACHMENT,BellAttachType.DOUBLE_WALL).setValue(BellBlock.FACING,Direction.NORTH);var changed=rotation(Direction.Axis.Y,90).apply(state);h.assertValueEqual(changed.getValue(BlockStateProperties.BELL_ATTACHMENT),BellAttachType.SINGLE_WALL,"Source prevents invalid double-wall support after disassembly");h.assertValueEqual(changed.getValue(BellBlock.FACING),Direction.WEST,"Bell rotates around Y");h.succeed();}
    @GameTest public void transformStreamCodecPreservesNullableAxesOffsetsMirrorsAndAngles(GameTestHelper h){
        for(var transform:List.of(new StructureTransform(BlockPos.ZERO,0,0,0),new StructureTransform(new BlockPos(-13,29,7),Direction.Axis.Z,Rotation.CLOCKWISE_90,Mirror.FRONT_BACK),new StructureTransform(P,-450,0,0))){var buffer=Unpooled.buffer();try{StructureTransform.STREAM_CODEC.encode(buffer,transform);var restored=StructureTransform.STREAM_CODEC.decode(buffer);h.assertValueEqual(restored.offset,transform.offset,"Offset");h.assertValueEqual(restored.angle,transform.angle,"Snapped angle");h.assertTrue(Objects.equals(restored.rotationAxis,transform.rotationAxis),"Nullable axis");h.assertValueEqual(restored.mirror,transform.mirror,"Mirror");h.assertValueEqual(restored.rotation,transform.rotation,"Rotation");h.assertValueEqual(buffer.readableBytes(),0,"Entire native payload consumed");}finally{buffer.release();}}h.succeed();
    }
    @GameTest public void addonTransformerRegistryTakesPriorityOverBlockInterface(GameTestHelper h){var block=ContraptionTestFixtures.BLOCK;int before=block.calls;var transform=rotation(Direction.Axis.Y,90);h.assertValueEqual(transform.apply(block.defaultBlockState()).getValue(BlockStateProperties.FACING),Direction.EAST,"Interface extension used");MovedBlockTransformerRegistries.BLOCK_TRANSFORMERS.register(block,(state,t)->state.setValue(BlockStateProperties.FACING,Direction.WEST));h.assertValueEqual(transform.apply(block.defaultBlockState()).getValue(BlockStateProperties.FACING),Direction.WEST,"Explicit addon transformer wins");h.assertValueEqual(block.calls,before+1,"Interface not called after registry override");h.succeed();}
    @GameTest public void addonBlockEntityTransformerTakesPriorityOverEntityInterface(GameTestHelper h){var be=new ContraptionTestFixtures.TransformBE(BlockPos.ZERO,ContraptionTestFixtures.ENTITY_BLOCK.defaultBlockState());var transform=rotation(Direction.Axis.Z,90);transform.apply(be);h.assertValueEqual(be.calls,1,"BE interface called");MovedBlockTransformerRegistries.BLOCK_ENTITY_TRANSFORMERS.register(ContraptionTestFixtures.TYPE,(target,t)->((ContraptionTestFixtures.TransformBE)target).calls+=10);transform.apply(be);h.assertValueEqual(be.calls,11,"BE registry wins over interface");h.succeed();}
    @GameTest public void emptyCollisionTagsAndBrittleSupportRulesMatchSource(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(P);
        h.assertFalse(BlockMovementChecks.isMovementNecessary(Blocks.AIR.defaultBlockState(),level,pos),"Air ignored");h.assertFalse(BlockMovementChecks.isMovementNecessary(Blocks.WATER.defaultBlockState(),level,pos),"World water ignored");
        h.assertTrue(BlockMovementChecks.isMovementNecessary(Blocks.COBWEB.defaultBlockState(),level,pos),"Source movable_empty_collider tag");h.assertTrue(BlockMovementChecks.isMovementNecessary(Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN,true),level,pos),"Open fence gate kept despite empty collider");
        h.assertTrue(BlockMovementChecks.isBrittle(Blocks.REDSTONE_WIRE.defaultBlockState()),"Native renamed redstone wire");h.assertTrue(BlockMovementChecks.isBrittle(Blocks.BELL.defaultBlockState()),"Source brittle tag");h.assertFalse(BlockMovementChecks.isBrittle(Blocks.GRINDSTONE.defaultBlockState()),"Grindstone deliberately excluded from attached face class");h.succeed();
    }
    @GameTest public void nativeDoorsBedsLaddersAndCeilingAttachmentsStayConnected(GameTestHelper h){var level=h.getLevel();var pos=h.absolutePos(P);var ladder=Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH);h.assertTrue(BlockMovementChecks.isBlockAttachedTowards(ladder,level,pos,Direction.SOUTH),"Ladder support");h.assertFalse(BlockMovementChecks.isBlockAttachedTowards(ladder,level,pos,Direction.NORTH),"Ladder front not attached");var door=Blocks.OAK_DOOR.defaultBlockState();h.assertTrue(BlockMovementChecks.isBlockAttachedTowards(door,level,pos,Direction.UP)&&BlockMovementChecks.isBlockAttachedTowards(door,level,pos,Direction.DOWN),"Lower door connects to upper half and floor");var bed=Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.FOOT);h.assertTrue(BlockMovementChecks.isBlockAttachedTowards(bed,level,pos,Direction.EAST),"Bed foot connects to head");var lever=Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE,AttachFace.CEILING);h.assertTrue(BlockMovementChecks.isBlockAttachedTowards(lever,level,pos,Direction.UP),"Ceiling lever support");h.succeed();}
    @GameTest public void movementSettingsKeepSpawnerPickupRestrictionAndHonorLiveSuppliers(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(P);h.assertFalse(BlockMovementChecks.isMovementAllowed(Blocks.BEDROCK.defaultBlockState(),level,pos),"Unbreakable immovable");h.assertValueEqual(ContraptionMovementSetting.get(Blocks.SPAWNER),ContraptionMovementSetting.NO_PICKUP,"Source spawner setting");h.assertTrue(BlockMovementChecks.isMovementAllowed(Blocks.SPAWNER.defaultBlockState(),level,pos),"Spawner can mount while pickup stays restricted");h.assertTrue(ContraptionMovementSetting.isNoPickup(List.of(new StructureBlockInfo(BlockPos.ZERO,Blocks.SPAWNER.defaultBlockState(),null))),"Collection pickup restriction");
        var previous=ContraptionSettings.obsidianMovement;try{h.assertFalse(BlockMovementChecks.isMovementAllowed(Blocks.OBSIDIAN.defaultBlockState(),level,pos),"Default obsidian blocked");ContraptionSettings.obsidianMovement=ContraptionMovementSetting.MOVABLE;h.assertValueEqual(ContraptionMovementSetting.get(Blocks.OBSIDIAN),ContraptionMovementSetting.MOVABLE,"Supplier not cached to a fixed enum");h.assertTrue(BlockMovementChecks.isMovementAllowed(Blocks.OBSIDIAN.defaultBlockState(),level,pos),"Source config can enable obsidian movement");}finally{ContraptionSettings.obsidianMovement=previous;}h.succeed();
    }
    @GameTest public void reverseRegistrationOrderAndPassFallbackApplyToMovementChecks(GameTestHelper h){var active=new AtomicBoolean(true);var state=Blocks.DIAMOND_BLOCK.defaultBlockState();var level=h.getLevel();var pos=h.absolutePos(P);BlockMovementChecks.registerMovementAllowedCheck((s,l,p)->active.get()&&s.is(Blocks.DIAMOND_BLOCK)?BlockMovementChecks.CheckResult.SUCCESS:BlockMovementChecks.CheckResult.PASS);BlockMovementChecks.registerMovementAllowedCheck((s,l,p)->active.get()&&s.is(Blocks.DIAMOND_BLOCK)?BlockMovementChecks.CheckResult.FAIL:BlockMovementChecks.CheckResult.PASS);try{h.assertFalse(BlockMovementChecks.isMovementAllowed(state,level,pos),"Last registration wins");}finally{active.set(false);}h.assertTrue(BlockMovementChecks.isMovementAllowed(state,level,pos),"All PASS checks fall back to source rule");h.succeed();}
    @GameTest public void unsupportedMachineFrontsStopGluePropagation(GameTestHelper h){var drill=com.simibubi.create.AllBlocks.MECHANICAL_DRILL.defaultBlockState().setValue(BlockStateProperties.FACING,Direction.EAST);h.assertTrue(BlockMovementChecks.isNotSupportive(drill,Direction.EAST),"Drill work face not supportive");h.assertFalse(BlockMovementChecks.isNotSupportive(drill,Direction.WEST),"Drill rear can be glued");h.assertTrue(BlockMovementChecks.isNotSupportive(Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState(),Direction.UP),"Carpet cannot support blocks above");h.succeed();}
}
