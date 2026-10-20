package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.KineticGraph;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.gauge.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public class GaugeGameTests {
    private static final BlockPos P = new BlockPos(3, 3, 3);
    private static BlockState xGauge(GaugeBlock block) { return block.defaultBlockState().setValue(GaugeBlock.FACING, Direction.UP).setValue(GaugeBlock.AXIS_ALONG_FIRST_COORDINATE, true); }
    private static int signal(GameTestHelper h, BlockPos pos) { return h.getBlockState(pos).getAnalogOutputSignal(h.getLevel(), h.absolutePos(pos), Direction.NORTH); }
    private static int comparator(GameTestHelper h) { return h.getBlockEntity(P.south(),net.minecraft.world.level.block.entity.ComparatorBlockEntity.class).getOutputSignal(); }
    @GameTest(maxTicks=50) public void speedometerTracksSpeedDirectionAndDisconnectWithComparator(GameTestHelper h) {
        h.setBlock(P, xGauge(AllBlocks.SPEEDOMETER)); h.setBlock(P.west(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.EAST)); h.setBlock(P.east(), AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
        h.setBlock(P.south().below(),Blocks.STONE); h.setBlock(P.south(),Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING,Direction.NORTH));
        h.runAfterDelay(4, () -> { h.assertValueEqual(signal(h,P),4,"16 RPM signal"); h.assertValueEqual(comparator(h),4,"Native comparator reads running gauge"); h.assertValueEqual(h.getBlockEntity(P.east(),KineticBlockEntity.class).getSpeed(),16f,"Gauge transmits shaft rotation"); h.getBlockEntity(P.west(),KineticBlockEntity.class).adjustGeneratedSpeed(false); });
        h.runAfterDelay(8, () -> { h.assertValueEqual(signal(h,P),7,"32 RPM crosses medium threshold"); h.assertValueEqual(comparator(h),7,"Reading change updates native comparator"); var motor=h.getBlockEntity(P.west(),KineticBlockEntity.class); motor.adjustGeneratedSpeed(false); motor.adjustGeneratedSpeed(false); });
        h.runAfterDelay(12, () -> { h.assertValueEqual(signal(h,P),12,"128 RPM fast signal"); h.getBlockEntity(P.west(),KineticBlockEntity.class).adjustGeneratedSpeed(false); });
        h.runAfterDelay(16, () -> { h.assertValueEqual(signal(h,P),15,"256 RPM saturates comparator"); h.getBlockEntity(P.west(),KineticBlockEntity.class).adjustGeneratedSpeed(true); });
        h.runAfterDelay(20, () -> { h.assertValueEqual(h.getBlockEntity(P,SpeedGaugeBlockEntity.class).getSpeed(),-256f,"Reverse speed delivered"); h.assertValueEqual(signal(h,P),15,"Direction does not affect reading"); h.setBlock(P.west(),Blocks.AIR); });
        h.runAfterDelay(24, () -> { h.assertValueEqual(signal(h,P),0,"Disconnected gauge zeroes comparator"); h.assertValueEqual(comparator(h),0,"Native comparator updated after source disconnect"); h.assertValueEqual(h.getBlockEntity(P,SpeedGaugeBlockEntity.class).dialTarget,0f,"Stopped dial"); h.succeed(); });
    }
    @GameTest(maxTicks=35) public void stressometerDistinguishesFullLoadOverstressAndStoppedNetwork(GameTestHelper h) {
        h.setBlock(P,xGauge(AllBlocks.STRESSOMETER)); h.setBlock(P.east(),AllBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.FACING,Direction.EAST)); h.setBlock(P.east(2),AllBlocks.HAND_CRANK.defaultBlockState().setValue(HandCrankBlock.FACING,Direction.EAST));
        h.runAfterDelay(2, () -> h.getBlockEntity(P.east(2),KineticBlockEntity.class).turnCrank(false));
        h.runAfterDelay(4, () -> { var gauge=h.getBlockEntity(P,StressGaugeBlockEntity.class); h.assertValueEqual(gauge.dialTarget,1f,"One press uses entire crank capacity"); h.assertValueEqual(signal(h,P),14,"Full load is not overload"); h.assertValueEqual(gauge.getNetworkCapacity(),256d,"256 SU capacity"); h.setBlock(P.west(),AllBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.FACING,Direction.EAST)); });
        h.runAfterDelay(6, () -> { var gauge=h.getBlockEntity(P,StressGaugeBlockEntity.class); h.assertTrue(gauge.isOverstressed()&&gauge.getSpeed()==0&&gauge.getTheoreticalSpeed()!=0,"Overload stops actual rotation"); h.assertValueEqual(signal(h,P),15,"Overload still emits 15"); h.assertValueEqual(gauge.color,0xFF0000,"Overload warning red"); var lines=new ArrayList<Component>(); gauge.addToGoggleTooltip(lines,false); h.assertTrue(lines.toString().contains("200%")&&lines.toString().contains("-256"),"HUD retains overload and negative remaining capacity"); h.setBlock(P.west(),Blocks.AIR); });
        h.runAfterDelay(8, () -> { h.assertValueEqual(signal(h,P),14,"Removing excess load restores reading"); });
        h.runAfterDelay(18, () -> { h.assertValueEqual(signal(h,P),0,"Expired crank stops gauge"); var lines=new ArrayList<Component>(); h.getBlockEntity(P,StressGaugeBlockEntity.class).addToGoggleTooltip(lines,false); h.assertTrue(lines.toString().contains("no_rotation"),"Stopped network tooltip"); h.succeed(); });
    }
    @GameTest public void gaugeOrientationHasExactlyTwoShaftPortsAndWrenchRotatesThemTogether(GameTestHelper h) {
        for(var facing:Direction.values()) for(boolean first:new boolean[]{false,true}) {
            var state=AllBlocks.SPEEDOMETER.defaultBlockState().setValue(GaugeBlock.FACING,facing).setValue(GaugeBlock.AXIS_ALONG_FIRST_COORDINATE,first); var axis=AllBlocks.SPEEDOMETER.getRotationAxis(state);
            h.assertTrue(axis!=facing.getAxis(),"Shaft perpendicular to mount"); int ports=0; for(var face:Direction.values()) if(AllBlocks.SPEEDOMETER.hasShaftTowards(h.getLevel(),h.absolutePos(P),state,face)) ports++; h.assertValueEqual(ports,2,"Through-shaft ports");
            for(var face:Direction.values()) { var rotated=WrenchItem.rotate(state,face); var oldDirection=Direction.get(Direction.AxisDirection.POSITIVE,axis); var expected=axis==face.getAxis()?axis:oldDirection.getClockWise(face.getAxis()).getAxis(); h.assertValueEqual(AllBlocks.SPEEDOMETER.getRotationAxis(rotated),expected,"Wrench preserves orientation frame"); h.assertFalse(rotated.getShape(h.getLevel(),h.absolutePos(P)).isEmpty(),"All rotated collision shapes valid"); }
        } h.succeed();
    }
    @GameTest public void placementOnEveryExposedShaftConnectsAndPreservesInputItem(GameTestHelper h) {
        var player=h.makeMockPlayer(GameType.SURVIVAL); var absolute=h.absolutePos(P);
        for(var face:Direction.values()) {
            h.setBlock(P,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,face.getAxis())); var stack=new ItemStack(AllBlocks.SPEEDOMETER); player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(absolute).add(face.getStepX()*0.5,face.getStepY()*0.5,face.getStepZ()*0.5),face,absolute,false));
            var state=AllBlocks.SPEEDOMETER.getStateForPlacement(context); h.assertTrue(state!=null&&AllBlocks.SPEEDOMETER.getRotationAxis(state)==face.getAxis(),"Placement connects to "+face+" shaft"); h.assertValueEqual(stack.getCount(),1,"Placement query read-only");
        } h.succeed();
    }
    @GameTest public void gaugeHeadsHideMountShaftVerticalAndOccludedFaces(GameTestHelper h) {
        var state=xGauge(AllBlocks.SPEEDOMETER); h.setBlock(P,state); int count=0;
        for(var face:Direction.values()) if(AllBlocks.SPEEDOMETER.shouldRenderHeadOnFace(h.getLevel(),h.absolutePos(P),state,face)) count++; h.assertValueEqual(count,2,"Two horizontal heads on horizontal shaft");
        h.setBlock(P.north(),Blocks.STONE); h.assertFalse(AllBlocks.SPEEDOMETER.shouldRenderHeadOnFace(h.getLevel(),h.absolutePos(P),state,Direction.NORTH),"Solid neighbor culls head"); h.assertTrue(AllBlocks.SPEEDOMETER.shouldRenderHeadOnFace(h.getLevel(),h.absolutePos(P),state,Direction.SOUTH),"Opposite head remains");
        state=AllBlocks.STRESSOMETER.defaultBlockState().setValue(GaugeBlock.FACING,Direction.SOUTH).setValue(GaugeBlock.AXIS_ALONG_FIRST_COORDINATE,false); h.setBlock(P,state); count=0; for(var face:Direction.values()) if(AllBlocks.STRESSOMETER.shouldRenderHeadOnFace(h.getLevel(),h.absolutePos(P),state,face)) count++; h.assertValueEqual(count,1,"Vertical shaft has one head facing mount normal"); h.succeed();
    }
    @GameTest public void gaugeSaveRestoresOriginalValueAndColorWithoutInventingRotation(GameTestHelper h) {
        h.setBlock(P,xGauge(AllBlocks.STRESSOMETER)); var gauge=h.getBlockEntity(P,StressGaugeBlockEntity.class); gauge.setNetworkState(32,32,false,new KineticGraph.Stress(256,192,false),Map.of());
        var saved=gauge.saveWithFullMetadata(h.getLevel().registryAccess()); var restored=(StressGaugeBlockEntity)BlockEntity.loadStatic(h.absolutePos(P),gauge.getBlockState(),saved,h.getLevel().registryAccess());
        h.assertValueEqual(restored.dialTarget,0.75f,"Original Value key retained"); h.assertValueEqual(restored.color,gauge.color,"Color persisted"); h.assertValueEqual(restored.getNetworkStress(),192d,"Network load persisted");
        saved.putFloat("Value",Float.NaN); restored=(StressGaugeBlockEntity)BlockEntity.loadStatic(h.absolutePos(P),gauge.getBlockState(),saved,h.getLevel().registryAccess()); h.assertValueEqual(restored.dialTarget,0f,"Malformed dial data rejected"); h.succeed();
    }
    @GameTest public void originalGaugeCraftAndConversionRecipesAreUsable(GameTestHelper h) {
        var recipes=h.getLevel().getServer().getRecipeManager(); var input=net.minecraft.world.item.crafting.CraftingInput.of(1,2,List.of(new ItemStack(Items.COMPASS),new ItemStack(AllBlocks.ANDESITE_CASING)));
        var crafted=recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,h.getLevel()); h.assertTrue(crafted.isPresent()&&crafted.get().value().assemble(input).is(AllBlocks.SPEEDOMETER.asItem()),"Compass plus casing creates speedometer");
        for(var source:List.of(AllBlocks.SPEEDOMETER,AllBlocks.STRESSOMETER)) { var other=source==AllBlocks.SPEEDOMETER?AllBlocks.STRESSOMETER:AllBlocks.SPEEDOMETER; input=net.minecraft.world.item.crafting.CraftingInput.of(1,1,List.of(new ItemStack(source))); var recipe=recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,h.getLevel()); h.assertTrue(recipe.isPresent()&&recipe.get().value().assemble(input).is(other.asItem()),"Reversible gauge conversion"); } h.succeed();
    }
}
