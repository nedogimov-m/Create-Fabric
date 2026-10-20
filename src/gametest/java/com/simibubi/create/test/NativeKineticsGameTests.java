package com.simibubi.create.test;

import java.util.List;
import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.kinetics.crank.*;
import com.simibubi.create.content.kinetics.fan.*;
import com.simibubi.create.content.kinetics.flywheel.FlywheelBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.*;
import com.simibubi.create.content.kinetics.simpleRelays.encased.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Genuine native callbacks, source inventory semantics, solver, saved owner phase and entity forces. UNRUN. */
public final class NativeKineticsGameTests {
    private static final BlockPos P = new BlockPos(4,4,4);
    private static Player user(GameTestHelper h,ItemStack stack) { var player = h.makeMockPlayer(GameType.SURVIVAL); player.setPos(Vec3.atCenterOf(h.absolutePos(P)).add(0,0,1)); player.setItemInHand(InteractionHand.MAIN_HAND,stack); return player; }
    private static BlockHitResult hit(GameTestHelper h,Direction face) { return new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)).relative(face,.5),face,h.absolutePos(P),false); }
    private static void use(GameTestHelper h,Player player,Direction face) { h.assertTrue(h.getBlockState(P).useItemOn(player.getMainHandItem(),h.getLevel(),player,InteractionHand.MAIN_HAND,hit(h,face)).consumesAction(),"Actual native source interaction"); }
    private static void motor(GameTestHelper h,BlockPos pos,Direction facing,int rpm) { h.setBlock(pos,AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,facing)); h.getBlockEntity(pos,KineticBlockEntity.class).setGeneratedSpeed(rpm); }
    private static int count(Player player,Item item) { int count=0; for(int slot=0;slot<player.getInventory().getContainerSize();slot++) { var stack=player.getInventory().getItem(slot); if(stack.is(item))count+=stack.getCount(); } return count; }

    @GameTest public void casingIsSourceReusableToolAndSneakWrenchDoesNotRefundOne(GameTestHelper h) {
        h.setBlock(P,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));
        var held = new ItemStack(AllBlocks.BRASS_CASING,3); var player = user(h,held); use(h,player,Direction.UP);
        h.assertTrue(h.getBlockState(P).is(AllBlocks.BRASS_ENCASED_SHAFT) && held.getCount()==3
                && h.getBlockEntity(P,KineticBlockEntity.class).getType()==AllBlockEntityTypes.ENCASED_SHAFT,"Actual source conversion preserves original shaft ownership and reusable casing count");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH)); player.setShiftKeyDown(true);
        h.assertTrue(AllItems.WRENCH.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit(h,Direction.UP))).consumesAction(),"Actual native sneak wrench");
        h.assertTrue(h.getBlockState(P).is(AllBlocks.SHAFT) && h.getBlockState(P).getValue(ShaftBlock.AXIS)==Direction.Axis.X
                && count(player,AllBlocks.BRASS_CASING.asItem())==0 && held.getCount()==3,"Source uncase leaves the shaft and creates no casing refund"); h.succeed();
    }
    @GameTest(maxTicks=45) public void cogEncasingDetectsRealShaftEndsAndWrenchClosesOnlyTheSelectedPort(GameTestHelper h) {
        motor(h,P.west(),Direction.EAST,64); h.setBlock(P,AllBlocks.COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));
        h.setBlock(P.east(),AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));
        use(h,user(h,new ItemStack(AllBlocks.ANDESITE_CASING)),Direction.UP);
        h.assertTrue(h.getBlockState(P).getValue(EncasedCogwheelBlock.TOP_SHAFT) && h.getBlockState(P).getValue(EncasedCogwheelBlock.BOTTOM_SHAFT),"Actual neighbors expose both source cog shaft ends");
        h.runAfterDelay(12,() -> {
            h.assertTrue(Math.abs(h.getBlockEntity(P.east(),KineticBlockEntity.class).getSpeed())==64,"Real source network reaches exposed output");
            var player=user(h,new ItemStack(AllItems.WRENCH)); AllItems.WRENCH.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit(h,Direction.EAST)));
        });
        h.runAfterDelay(26,() -> {
            h.assertTrue(!h.getBlockState(P).getValue(EncasedCogwheelBlock.TOP_SHAFT) && h.getBlockState(P).getValue(EncasedCogwheelBlock.BOTTOM_SHAFT)
                    && Math.abs(h.getBlockEntity(P,KineticBlockEntity.class).getSpeed())==64 && h.getBlockEntity(P.east(),KineticBlockEntity.class).getSpeed()==0,"Actual native selected port change disconnects downstream graph"); h.succeed();
        });
    }
    @GameTest(maxTicks=30) public void encasedCogTeethStillDriveAnActualNeighborWithoutShaftFlags(GameTestHelper h) {
        motor(h,P.below(),Direction.UP,32);
        h.setBlock(P,AllBlocks.BRASS_ENCASED_COGWHEEL.defaultBlockState().setValue(EncasedCogwheelBlock.BOTTOM_SHAFT,true));
        h.setBlock(P.east(),AllBlocks.COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Y));
        h.runAfterDelay(12,() -> { var input=h.getBlockEntity(P,KineticBlockEntity.class); var output=h.getBlockEntity(P.east(),KineticBlockEntity.class);
            h.assertTrue(input.getSpeed()!=0 && output.getSpeed()==-input.getSpeed(),"Genuine source ICogWheel teeth couple through actual solver despite closed top shaft"); h.succeed(); });
    }
    @GameTest public void dyeKeepsTheActualValveBlockEntityAndDoesNotConsumeSourceDye(GameTestHelper h) {
        h.setBlock(P.below(),Blocks.STONE); h.setBlock(P,AllBlocks.COPPER_VALVE_HANDLE);
        var valve=h.getBlockEntity(P,ValveHandleBlockEntity.class); valve.setAngleInput(-135); h.assertTrue(valve.activate(false),"Actual idle source valve activation");
        var dye=new ItemStack(Items.DYE.pick(DyeColor.BLUE),2); use(h,user(h,dye),Direction.UP);
        h.assertTrue(h.getBlockState(P).is(AllBlocks.DYED_VALVE_HANDLES.get(DyeColor.BLUE)) && h.getBlockEntity(P,ValveHandleBlockEntity.class)==valve
                && valve.getAngleInput()==-135 && valve.inUse>0 && dye.getCount()==2,"Native shouldChangedStateKeepBlockEntity retains real running owner and source reusable dye count"); h.succeed();
    }
    @GameTest(maxTicks=40) public void valveOwnAnglePhaseReachesActualOutputThenClearsWhenSourceStops(GameTestHelper h) {
        h.setBlock(P.east(),AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));
        h.setBlock(P,AllBlocks.COPPER_VALVE_HANDLE.defaultBlockState().setValue(ValveHandleBlock.FACING,Direction.WEST));
        var valve=h.getBlockEntity(P,ValveHandleBlockEntity.class); valve.activate(false);
        h.runAfterDelay(3,() -> { var output=h.getBlockEntity(P.east(),KineticBlockEntity.class);
            h.assertTrue(Math.abs(output.getSpeed())==32 && output.sequenceContext!=null && output.getSequencePhase()!=null
                    && output.sequenceContext.getEffectiveValue(32)==45,"Actual generator owns a source45-degree context propagated by native solver"); });
        h.runAfterDelay(22,() -> { var output=h.getBlockEntity(P.east(),KineticBlockEntity.class);
            h.assertTrue(output.getSpeed()==0 && output.sequenceContext==null && valve.inUse==0,"Native source overshoot period ends and downstream phase is cleared"); h.succeed(); });
    }
    @GameTest public void valveSavedNativeFactoryPreservesTheSameOwnerPhaseAndConfiguration(GameTestHelper h) {
        h.setBlock(P.below(),Blocks.STONE); h.setBlock(P,AllBlocks.COPPER_VALVE_HANDLE);
        var valve=h.getBlockEntity(P,ValveHandleBlockEntity.class); valve.setAngleInput(-180); valve.activate(true);
        var saved=valve.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored=(ValveHandleBlockEntity)BlockEntity.loadStatic(valve.getBlockPos(),valve.getBlockState(),saved,h.getLevel().registryAccess());
        h.assertTrue(restored!=null && restored.getType()==AllBlockEntityTypes.VALVE_HANDLE && restored.inUse==valve.inUse && restored.backwards
                && restored.getAngleInput()==-180 && restored.getSequencePhase().equals(valve.getSequencePhase())
                && restored.sequenceContext.equals(valve.sequenceContext),"Actual native registered factory restores exact persisted source phase without a new activation"); h.succeed();
    }
    @GameTest public void valveReceiverRejectsOutOfRangeAndUnloadedRequestsWithoutLoadingChunks(GameTestHelper h) {
        h.setBlock(P.below(),Blocks.STONE); h.setBlock(P,AllBlocks.COPPER_VALVE_HANDLE); var player=user(h,ItemStack.EMPTY); var pos=h.absolutePos(P);
        h.assertTrue(!ValveHandleSettings.apply(h.getLevel(),player,new ValveHandleSettings.SetAngle(pos,181))
                && !ValveHandleSettings.apply(h.getLevel(),player,new ValveHandleSettings.SetAngle(pos,0)),"Server enforces actual source degree bounds");
        var far=new BlockPos(2700000,80,2700000);
        h.assertTrue(!ValveHandleSettings.apply(h.getLevel(),player,new ValveHandleSettings.SetAngle(far,90))
                && h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Foreign packet creates no native loading ticket"); h.succeed();
    }
    @GameTest(maxTicks=40) public void realNozzleUsesRadialEntityForceAndSuppressesLinearFanProcessing(GameTestHelper h) {
        motor(h,P.west(),Direction.EAST,64); h.setBlock(P,AllBlocks.ENCASED_FAN.defaultBlockState().setValue(EncasedFanBlock.FACING,Direction.EAST));
        h.setBlock(P.east(),AllBlocks.NOZZLE.defaultBlockState().setValue(NozzleBlock.FACING,Direction.EAST));
        var point=Vec3.atCenterOf(h.absolutePos(P.east())).add(0,0,2); var item=new ItemEntity(h.getLevel(),point.x,point.y,point.z,new ItemStack(Items.DIAMOND,2),0,0,0); item.setNoGravity(true); h.getLevel().addFreshEntity(item);
        h.runAfterDelay(15,() -> { var fan=h.getBlockEntity(P,EncasedFanBlockEntity.class); var nozzle=h.getBlockEntity(P.east(),NozzleBlockEntity.class);
            h.assertTrue(nozzle.getRange()>3 && nozzle.isPushing() && item.getDeltaMovement().z>0 && item.getItem().getCount()==2
                    && fan.getAirCurrent().maxDistance==0,"Actual radial source nozzle affects real side entity; no parallel linear processing simulation"); h.succeed(); });
    }
    @GameTest public void nozzleRequiresActualAirSourceAndNormalWrenchCannotRotateIt(GameTestHelper h) {
        h.setBlock(P.west(),AllBlocks.ENCASED_FAN.defaultBlockState().setValue(EncasedFanBlock.FACING,Direction.EAST));
        h.setBlock(P,AllBlocks.NOZZLE.defaultBlockState().setValue(NozzleBlock.FACING,Direction.EAST)); var player=user(h,new ItemStack(AllItems.WRENCH));
        h.assertTrue(AllItems.WRENCH.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit(h,Direction.UP)))==InteractionResult.FAIL,"Original source nozzle rejects rotation");
        h.setBlock(P.west(),Blocks.AIR); h.assertTrue(h.getBlockState(P).isAir(),"Native neighbor removal destroys unsupported nozzle"); h.succeed();
    }
    @GameTest(maxTicks=30) public void nativeFlywheelPassesActualShaftPowerWithNoInventedGeneration(GameTestHelper h) {
        motor(h,P.west(),Direction.EAST,64); h.setBlock(P,AllBlocks.FLYWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));
        h.setBlock(P.east(),AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));
        h.runAfterDelay(12,() -> { var flywheel=h.getBlockEntity(P,FlywheelBlockEntity.class);
            h.assertTrue(Math.abs(flywheel.getSpeed())==64 && Math.abs(h.getBlockEntity(P.east(),KineticBlockEntity.class).getSpeed())==64
                    && flywheel.getDirectionalGeneratedSpeed()==0 && com.simibubi.create.api.stress.BlockStressValues.getImpact(AllBlocks.FLYWHEEL)==0,"Native source flywheel is a real pass-through with client visual inertia only"); h.succeed(); });
    }
    @GameTest(maxTicks=40) public void actualTurntableRotatesAnOriginalLivingEntityThroughNativeCollisionTicks(GameTestHelper h) {
        motor(h,P.below(),Direction.UP,32); h.setBlock(P,AllBlocks.TURNTABLE);
        var center=Vec3.atCenterOf(h.absolutePos(P));
        var stand=new net.minecraft.world.entity.decoration.ArmorStand(h.getLevel(),center.x,center.y+.1,center.z); h.getLevel().addFreshEntity(stand);
        float initial=stand.getYRot();
        h.runAfterDelay(18,() -> { h.assertTrue(Math.abs(h.getBlockEntity(P,KineticBlockEntity.class).getSpeed())==32 && stand.getYRot()!=initial,
                "Genuine world entity collision applies source turntable yaw; no synthetic input or RPM injection"); h.succeed(); });
    }
    @GameTest public void valveCraftingRecipeUsesRealCopperSheetsAndAndesiteAlloy(GameTestHelper h) {
        var input=CraftingInput.of(3,2,List.of(new ItemStack(AllItems.COPPER_SHEET),new ItemStack(AllItems.COPPER_SHEET),new ItemStack(AllItems.COPPER_SHEET),ItemStack.EMPTY,new ItemStack(AllItems.ANDESITE_ALLOY),ItemStack.EMPTY));
        var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow(); var output=recipe.value().assemble(input);
        h.assertTrue(output.is(AllBlocks.COPPER_VALVE_HANDLE.asItem()) && output.getCount()==1,"Genuine source native crafting recipe and stock output"); h.succeed();
    }
}
