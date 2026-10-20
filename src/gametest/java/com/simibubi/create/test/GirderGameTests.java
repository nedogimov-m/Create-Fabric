package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.decoration.girder.*;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.trains.track.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;

/** Actual source connections, native item operations, registered loot/recipe and real kinetic solver. STAGED, UNRUN. */
public class GirderGameTests {
    private static final BlockPos P=new BlockPos(4,4,4);
    private static BlockState state(GameTestHelper h){return h.getBlockState(P);}
    private static BlockState beam(Direction.Axis axis){return AllBlocks.METAL_GIRDER.defaultBlockState().setValue(GirderBlock.AXIS,axis).setValue(GirderBlock.X,axis==Direction.Axis.X).setValue(GirderBlock.Z,axis==Direction.Axis.Z);}
    private static Player user(GameTestHelper h,ItemStack stack){var user=h.makeMockPlayer(GameType.SURVIVAL);user.setPos(Vec3.atCenterOf(h.absolutePos(P)).add(0,0,1));user.setItemInHand(InteractionHand.MAIN_HAND,stack);return user;}
    private static BlockHitResult hit(GameTestHelper h,Direction face){return new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)).relative(face,.5),face,h.absolutePos(P),false);}
    private static void use(GameTestHelper h,Player user,Direction face){h.assertTrue(state(h).useItemOn(user.getMainHandItem(),h.getLevel(),user,InteractionHand.MAIN_HAND,hit(h,face)).consumesAction(),"Actual native girder item interaction");}
    private static int inventory(Player user,Item item){int count=0;for(int i=0;i<user.getInventory().getContainerSize();i++){var stack=user.getInventory().getItem(i);if(stack.is(item))count+=stack.getCount();}return count;}

    @GameTest public void nativeSurvivalBlockItemPlacesTheSourcePoleAndConsumesOne(GameTestHelper h){
        h.setBlock(P.below(),Blocks.STONE);var stack=new ItemStack(AllBlocks.METAL_GIRDER,2);var user=user(h,stack);var target=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P.below())).add(0,.5,0),Direction.UP,h.absolutePos(P.below()),false);
        h.assertTrue(((BlockItem)stack.getItem()).place(new BlockPlaceContext(new UseOnContext(user,InteractionHand.MAIN_HAND,target))).consumesAction(),"Actual native survival placement");h.assertTrue(state(h).is(AllBlocks.METAL_GIRDER)&&!state(h).getValue(GirderBlock.X)&&!state(h).getValue(GirderBlock.Z)&&state(h).getValue(GirderBlock.AXIS)==Direction.Axis.Y&&stack.getCount()==1,"Source vertical pole and one consumed real item");h.succeed();
    }
    @GameTest public void nativeBeamHasSourceTwelveHighEightWideCollisionAndCenterSupport(GameTestHelper h){
        h.setBlock(P,beam(Direction.Axis.X));var box=state(h).getShape(h.getLevel(),h.absolutePos(P)).bounds();h.assertTrue(box.minX==0&&box.maxX==1&&box.minY==2/16d&&box.maxY==14/16d&&box.minZ==4/16d&&box.maxZ==12/16d,"Exact source beam bounds");var support=state(h).getBlockSupportShape(h.getLevel(),h.absolutePos(P)).bounds();h.assertTrue(support.minY==0&&support.maxY==1,"Actual support shape includes the full center pole for lantern/chain attachments");h.succeed();
    }
    @GameTest public void perpendicularNeighborBeamsBuildARealCross(GameTestHelper h){
        h.setBlock(P,AllBlocks.METAL_GIRDER);h.setBlock(P.east(),beam(Direction.Axis.X));h.setBlock(P.north(),beam(Direction.Axis.Z));h.assertTrue(state(h).getValue(GirderBlock.X)&&state(h).getValue(GirderBlock.Z),"Actual neighbor shape propagation joins both source beam axes");h.succeed();
    }
    @GameTest public void removingTheLastUnanchoredBeamClearsItsConnection(GameTestHelper h){
        h.setBlock(P,AllBlocks.METAL_GIRDER);h.setBlock(P.east(),beam(Direction.Axis.X));h.assertTrue(state(h).getValue(GirderBlock.X),"Fixture received actual adjacent beam");h.setBlock(P.east(),Blocks.AIR);h.assertFalse(state(h).getValue(GirderBlock.X),"Removed last unanchored neighbor clears source horizontal extension");h.succeed();
    }
    @GameTest public void actualSolidNeighborProducesOnlyTheCorrectHorizontalBracketPort(GameTestHelper h){
        h.setBlock(P,beam(Direction.Axis.X));h.setBlock(P.east(),Blocks.STONE);h.assertTrue(GirderBlock.isConnected(h.getLevel(),h.absolutePos(P),state(h),Direction.EAST)&&!GirderBlock.isConnected(h.getLevel(),h.absolutePos(P),state(h),Direction.NORTH),"Source bracket geometry is permitted only on the active beam axis toward actual full support");h.succeed();
    }
    @GameTest public void nativeStandingLanternAddsAndRemovalClearsTopCap(GameTestHelper h){
        h.setBlock(P,beam(Direction.Axis.X));h.setBlock(P.above(),Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,false));h.assertTrue(state(h).getValue(GirderBlock.TOP),"Real standing lantern uses center support and native top cap");h.setBlock(P.above(),Blocks.AIR);h.assertFalse(state(h).getValue(GirderBlock.TOP),"Actual empty support clears beam cap");h.succeed();
    }
    @GameTest public void nativeWrenchTogglesSingleVerticalConnection(GameTestHelper h){
        h.setBlock(P,beam(Direction.Axis.X));var user=user(h,new ItemStack(AllItems.WRENCH));boolean before=state(h).getValue(GirderBlock.TOP);use(h,user,Direction.UP);h.assertTrue(state(h).getValue(GirderBlock.TOP)!=before,"Actual wrench click toggles selected source top cap");h.succeed();
    }
    @GameTest public void nativeWrenchPairUpdatesBothOriginalBeamFaces(GameTestHelper h){
        h.setBlock(P,beam(Direction.Axis.X));h.setBlock(P.above(),beam(Direction.Axis.Z));var user=user(h,new ItemStack(AllItems.WRENCH));boolean first=state(h).getValue(GirderBlock.TOP),second=h.getBlockState(P.above()).getValue(GirderBlock.BOTTOM);use(h,user,Direction.UP);
        h.assertTrue(state(h).getValue(GirderBlock.TOP)!=first&&h.getBlockState(P.above()).getValue(GirderBlock.BOTTOM)!=second,"Source pair click changes both native blocks through quiet state writes");h.succeed();
    }
    @GameTest public void poleAndCrossExposeNoInventedWrenchDirections(GameTestHelper h){
        h.setBlock(P,AllBlocks.METAL_GIRDER);h.assertTrue(GirderWrenchBehavior.getValidDirections(h.getLevel(),h.absolutePos(P)).isEmpty(),"Source pole has no horizontal/vertical toggle UI");h.setBlock(P,beam(Direction.Axis.X).setValue(GirderBlock.Z,true));h.assertTrue(GirderWrenchBehavior.getValidDirections(h.getLevel(),h.absolutePos(P)).isEmpty(),"Source cross has no pair cap toggle");h.succeed();
    }
    @GameTest public void realSurvivalShaftInsertionKeepsWaterAndCreatesActualKineticType(GameTestHelper h){
        h.setBlock(P,beam(Direction.Axis.Z).setValue(GirderBlock.WATERLOGGED,true).setValue(GirderBlock.TOP,true));var stack=new ItemStack(AllBlocks.SHAFT,2);var user=user(h,stack);use(h,user,Direction.EAST);
        h.assertTrue(state(h).is(AllBlocks.METAL_GIRDER_ENCASED_SHAFT)&&state(h).getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS)==Direction.Axis.X&&state(h).getFluidState().getType()==Fluids.WATER&&stack.getCount()==1&&h.getBlockEntity(P,KineticBlockEntity.class).getType()==AllBlockEntityTypes.ENCASED_SHAFT,"Actual inserted shaft is perpendicular to source beam with native water/data and one survival cost");h.succeed();
    }
    @GameTest public void actualWrenchExtractsExactlyOneShaftAndLeavesTheBeam(GameTestHelper h){
        h.setBlock(P,AllBlocks.METAL_GIRDER_ENCASED_SHAFT.defaultBlockState().setValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS,Direction.Axis.X));var user=user(h,new ItemStack(AllItems.WRENCH));var context=new UseOnContext(user,InteractionHand.MAIN_HAND,hit(h,Direction.UP));h.assertTrue(AllItems.WRENCH.useOn(context).consumesAction(),"Actual native wrench extraction");
        h.assertTrue(state(h).is(AllBlocks.METAL_GIRDER)&&state(h).getValue(GirderBlock.Z)&&inventory(user,AllBlocks.SHAFT.asItem())==1,"Original perpendicular beam retained, one real shaft refunded");h.succeed();
    }
    @GameTest public void actualSneakWrenchRetrievesBothOriginalIngredients(GameTestHelper h){
        h.setBlock(P,AllBlocks.METAL_GIRDER_ENCASED_SHAFT);var user=user(h,new ItemStack(AllItems.WRENCH));user.setShiftKeyDown(true);h.assertTrue(AllItems.WRENCH.useOn(new UseOnContext(user,InteractionHand.MAIN_HAND,hit(h,Direction.UP))).consumesAction(),"Actual native retrieval");
        h.assertTrue(state(h).isAir()&&inventory(user,AllBlocks.METAL_GIRDER.asItem())==1&&inventory(user,AllBlocks.SHAFT.asItem())==1,"Native source loot refunds one beam and one shaft");h.succeed();
    }
    @GameTest public void sourcePlacementHelperUsesRealBlockItemAtTheEndOfExistingBeams(GameTestHelper h){
        for(int i=0;i<3;i++)h.setBlock(P.east(i),beam(Direction.Axis.X));var stack=new ItemStack(AllBlocks.METAL_GIRDER,2);var user=user(h,stack);use(h,user,Direction.EAST);
        h.assertTrue(h.getBlockState(P.east(3)).is(AllBlocks.METAL_GIRDER)&&h.getBlockState(P.east(3)).getValue(GirderBlock.X)&&stack.getCount()==1,"Native assisted BlockItem placement reaches actual end and consumes exactly one");h.succeed();
    }
    @GameTest public void sourceGirderRecipeConsumesIronSheetsAndAlloyForEight(GameTestHelper h){
        var input=CraftingInput.of(3,2,List.of(new ItemStack(AllItems.IRON_SHEET),new ItemStack(AllItems.IRON_SHEET),new ItemStack(AllItems.IRON_SHEET),new ItemStack(AllItems.ANDESITE_ALLOY),new ItemStack(AllItems.ANDESITE_ALLOY),new ItemStack(AllItems.ANDESITE_ALLOY)));
        var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();var output=recipe.value().assemble(input);h.assertTrue(output.is(AllBlocks.METAL_GIRDER.asItem())&&output.getCount()==8,"Actual native source plates/alloy recipe and output count");h.succeed();
    }
    @GameTest(maxTicks=20)public void actualGirdableTrackAddsTheSourcePerpendicularSupportBeam(GameTestHelper h){
        h.setBlock(P,AllBlocks.METAL_GIRDER);h.setBlock(P.above().north(),AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO));h.getLevel().scheduleTick(h.absolutePos(P),AllBlocks.METAL_GIRDER,1);
        h.runAfterDelay(4,()->{h.assertTrue(state(h).getValue(GirderBlock.X),"Actual source TrackBlock.updateGirders scheduling recomputes the supporting axis");h.succeed();});
    }
    @GameTest public void foreignUnloadedPlacementFrontierDoesNotCreateTickets(GameTestHelper h){
        var far=new BlockPos(2600000,80,2600000);var hit=new BlockHitResult(Vec3.atCenterOf(far),Direction.UP,far,false);h.assertTrue(GirderPlacementHelper.target(h.getLevel(),AllBlocks.METAL_GIRDER.defaultBlockState(),far,hit)==null&&h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Bounded native placement assist does not turn unloaded frontier into loading work");h.succeed();
    }
    @GameTest(maxTicks=45)public void realMotorPropagatesThroughEncasedShaftAndRemovalDisconnectsActualOutput(GameTestHelper h){
        h.setBlock(P.west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));h.getBlockEntity(P.west(),KineticBlockEntity.class).setGeneratedSpeed(64);h.setBlock(P,AllBlocks.METAL_GIRDER_ENCASED_SHAFT.defaultBlockState().setValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS,Direction.Axis.X));h.setBlock(P.east(),AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));
        h.runAfterDelay(12,()->{h.assertTrue(Math.abs(h.getBlockEntity(P,KineticBlockEntity.class).getSpeed())==64&&Math.abs(h.getBlockEntity(P.east(),KineticBlockEntity.class).getSpeed())==64,"Real native kinetic solver carries generated source RPM through actual shaft");var user=user(h,new ItemStack(AllItems.WRENCH));AllItems.WRENCH.useOn(new UseOnContext(user,InteractionHand.MAIN_HAND,hit(h,Direction.UP)));});
        h.runAfterDelay(26,()->{h.assertTrue(state(h).is(AllBlocks.METAL_GIRDER)&&h.getBlockEntity(P.east(),KineticBlockEntity.class).getSpeed()==0,"Real shaft removal invalidates the source graph; beam cannot conduct synthetic RPM");h.succeed();});
    }
    @GameTest(maxTicks=30)public void perpendicularShaftCannotRelayMotorPowerAcrossTheBeam(GameTestHelper h){
        h.setBlock(P.west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));h.getBlockEntity(P.west(),KineticBlockEntity.class).setGeneratedSpeed(64);h.setBlock(P,AllBlocks.METAL_GIRDER_ENCASED_SHAFT.defaultBlockState().setValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS,Direction.Axis.Z));h.setBlock(P.east(),AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));h.runAfterDelay(12,()->{h.assertTrue(h.getBlockEntity(P,KineticBlockEntity.class).getSpeed()==0&&h.getBlockEntity(P.east(),KineticBlockEntity.class).getSpeed()==0,"Only actual shaft faces transmit through native girder");h.succeed();});
    }
}
