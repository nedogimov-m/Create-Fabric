package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.decoration.girder.GirderEncasedShaftBlock;
import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.*;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlock;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Actual registered items, joined survival game-mode interaction, native placement and physical stock. */
public final class CogwheelPlacementGameTests {
    private static final BlockPos SOURCE=new BlockPos(2,2,2);
    private static final String LONG="create_test:long_pipe";
    private static void clear(GameTestHelper h){
        for(int x=0;x<=4;x++)for(int y=0;y<=4;y++)for(int z=0;z<=4;z++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
    }
    private static Direction positive(Direction.Axis axis){return Direction.get(Direction.AxisDirection.POSITIVE,axis);}
    private static List<Direction> planar(Direction.Axis axis){return switch(axis){case X->List.of(Direction.UP,Direction.SOUTH);case Y->List.of(Direction.EAST,Direction.SOUTH);case Z->List.of(Direction.EAST,Direction.UP);};}
    private static BlockHitResult rim(GameTestHelper h,Direction.Axis axis){
        var p=planar(axis);var source=h.absolutePos(SOURCE);
        return new BlockHitResult(Vec3.atCenterOf(source).add(p.getFirst().getUnitVec3().scale(.5)).add(p.getLast().getUnitVec3().scale(.3)).add(positive(axis).getUnitVec3().scale(.15)),p.getFirst(),source,false);
    }
    private static net.minecraft.world.InteractionResult use(GameTestHelper h,ServerPlayer player,ItemStack stack,BlockHitResult hit){
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        return player.gameMode.useItemOn(player,h.getLevel(),stack,InteractionHand.MAIN_HAND,hit);
    }
    private static void placed(GameTestHelper h,ServerPlayer player,ItemStack stack,Block block,BlockPos target,Direction.Axis axis){
        var state=h.getLevel().getBlockState(h.absolutePos(target));
        h.assertTrue(state.is(block)&&state.getValue(ShaftBlock.AXIS)==axis,"Actual helper places the real registered block at source geometry and rotation axis: "+target+" / "+axis);
        h.assertTrue(stack.getCount()==1&&player.getMainHandItem()==stack,"Native survival placement debits one from the same physical two-item stack");
        h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(target)) instanceof KineticBlockEntity,"Actual placed cog has its native kinetic block entity owner");
    }

    @GameTest public void registeredCogItemsPlaceAllFourSizePairsAcrossThreeAxesAndAwardRealMixedGearAdvancement(GameTestHelper h){
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(SOURCE)).add(0,0,2))){
            var player=joined.player();
            for(var axis:Direction.Axis.values())for(boolean sourceLarge:new boolean[]{false,true})for(boolean heldLarge:new boolean[]{false,true}){
                clear(h);var source=(sourceLarge?AllBlocks.LARGE_COGWHEEL:AllBlocks.COGWHEEL).defaultBlockState().setValue(ShaftBlock.AXIS,axis);h.setBlock(SOURCE,source);
                var held=heldLarge?AllBlocks.LARGE_COGWHEEL:AllBlocks.COGWHEEL;var stack=new ItemStack(held,2);var usedBefore=player.getStats().getValue(net.minecraft.stats.Stats.ITEM_USED.get(held.asItem()));
                h.assertTrue(stack.getItem() instanceof CogwheelBlockItem,"Actual small/large registry item must own the restored source helper");
                var p=planar(axis);var target=!sourceLarge&&!heldLarge?SOURCE.relative(p.getFirst()):sourceLarge&&heldLarge?SOURCE.relative(p.getFirst()).relative(positive(axis)):SOURCE.relative(p.getFirst()).relative(p.getLast());
                var placedAxis=sourceLarge&&heldLarge?p.getFirst().getAxis():axis;
                h.assertTrue(use(h,player,stack,rim(h,axis)).consumesAction(),"Actual survival game-mode must perform every size/axis helper interaction");
                placed(h,player,stack,held,target,placedAxis);h.assertTrue(h.getLevel().getBlockState(h.absolutePos(SOURCE))==source,"Original clicked cog is preserved");
                h.assertTrue(player.getStats().getValue(net.minecraft.stats.Stats.ITEM_USED.get(held.asItem()))==usedBefore+1,"Every actual successful source pre-block helper awards exactly one ordinary native item-used stat");
            }
            var advancement=h.getLevel().getServer().getAdvancements().get(Create.asResource("shifting_gears"));
            h.assertTrue(advancement!=null&&player.getAdvancements().getOrStartProgress(advancement).isDone(),"Actual native setPlacedBy awards the real source mixed-size cog advancement");
        }h.succeed();
    }

    @GameTest public void realObstructionSelectsNextSourceDistanceCandidateAndNativePlacementRetainsWater(GameTestHelper h){
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(SOURCE)).add(0,0,2))){
            var player=joined.player();clear(h);h.setBlock(SOURCE,AllBlocks.COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Y));h.setBlock(SOURCE.east(),Blocks.OBSIDIAN);
            var next=new ItemStack(AllBlocks.COGWHEEL,2);h.assertTrue(use(h,player,next,rim(h,Direction.Axis.Y)).consumesAction(),"Blocked nearest candidate must use the next source-ranked candidate");
            placed(h,player,next,AllBlocks.COGWHEEL,SOURCE.south(),Direction.Axis.Y);h.assertTrue(h.getLevel().getBlockState(h.absolutePos(SOURCE.east())).is(Blocks.OBSIDIAN),"Actual obstruction remains intact");
            clear(h);h.setBlock(SOURCE,AllBlocks.COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Y));h.setBlock(SOURCE.east(),Blocks.WATER);
            var wet=new ItemStack(AllBlocks.COGWHEEL,2);h.assertTrue(use(h,player,wet,rim(h,Direction.Axis.Y)).consumesAction(),"Actual native helper may replace source water");
            placed(h,player,wet,AllBlocks.COGWHEEL,SOURCE.east(),Direction.Axis.Y);h.assertTrue(h.getLevel().getBlockState(h.absolutePos(SOURCE.east())).getValue(ShaftBlock.WATERLOGGED),"Actual native placement preserves waterlogging at the target");
            clear(h);h.setBlock(SOURCE,AllBlocks.COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Y));for(var side:Direction.Plane.HORIZONTAL)h.setBlock(SOURCE.relative(side),Blocks.OBSIDIAN);
            var blocked=new ItemStack(AllBlocks.COGWHEEL,2);h.assertTrue(!use(h,player,blocked,rim(h,Direction.Axis.Y)).consumesAction()&&blocked.getCount()==2,"All actual blocked source candidates refuse without finite item loss");
        }h.succeed();
    }

    @GameTest public void sourceShaftHitAndSneakBypassCogAssistanceAndNativeSurvivalRejectsInvalidOrdinaryPlacement(GameTestHelper h){
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(SOURCE)).add(0,0,2))){
            var player=joined.player();clear(h);h.setBlock(SOURCE,AllBlocks.COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Y));var pos=h.absolutePos(SOURCE);
            var axial=new ItemStack(AllBlocks.COGWHEEL,2);h.assertTrue(use(h,player,axial,new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false)).consumesAction(),"Actual six-voxel shaft hit takes ordinary axial placement");placed(h,player,axial,AllBlocks.COGWHEEL,SOURCE.above(),Direction.Axis.Y);
            clear(h);h.setBlock(SOURCE,AllBlocks.COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Y));player.setShiftKeyDown(true);var sneaked=new ItemStack(AllBlocks.COGWHEEL,2);
            h.assertTrue(use(h,player,sneaked,rim(h,Direction.Axis.Y)).consumesAction(),"Actual sneak uses ordinary block placement");placed(h,player,sneaked,AllBlocks.COGWHEEL,SOURCE.east(),Direction.Axis.X);player.setShiftKeyDown(false);
            clear(h);h.setBlock(SOURCE,Blocks.STONE);h.setBlock(SOURCE.east().south(),AllBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Y));
            var invalid=new ItemStack(AllBlocks.COGWHEEL,2);h.assertTrue(!use(h,player,invalid,rim(h,Direction.Axis.Y)).consumesAction()&&invalid.getCount()==2&&h.getLevel().getBlockState(h.absolutePos(SOURCE.east())).isAir(),"Real native BlockItem survival rejects source-invalid adjacent cog interference without stock debit");
        }h.succeed();
    }

    @GameTest public void integratedAndEncasedCogsRetainOriginalFirstHelperActionBeforeTheirBlockInteraction(GameTestHelper h){
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(SOURCE)).add(0,0,2))){
            var player=joined.player();
            for(var block:List.<Block>of(AllBlocks.MILLSTONE,AllBlocks.MECHANICAL_MIXER,AllBlocks.MECHANICAL_CRAFTER,AllBlocks.MECHANICAL_ARM,AllBlocks.ANDESITE_ENCASED_COGWHEEL))for(boolean large:new boolean[]{false,true}){
                clear(h);var state=block.defaultBlockState();var axis=((ICogWheel)block).getRotationAxis(state);h.setBlock(SOURCE,state);var held=large?AllBlocks.LARGE_COGWHEEL:AllBlocks.COGWHEEL;var stack=new ItemStack(held,2);var p=planar(axis);var target=large?SOURCE.relative(p.getFirst()).relative(p.getLast()):SOURCE.relative(p.getFirst());
                h.assertTrue(use(h,player,stack,rim(h,axis)).consumesAction(),"Actual integrated/encased owner accepts original first cog helper action");placed(h,player,stack,held,target,axis);
                h.assertTrue(h.getLevel().getBlockState(h.absolutePos(SOURCE))==state&&player.containerMenu==player.inventoryMenu,"Source integrated/encased machine remains intact and never opens its ordinary UI");
            }
        }h.succeed();
    }

    @GameTest(structure=LONG) public void actualShaftChainUsesConfiguredBoundAndRealHeldExtendoAddsFourThenDebitsNativeAction(GameTestHelper h){
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(SOURCE)).add(0,0,2))){
            var player=joined.player();int range=AllConfigs.server().equipment.placementAssistRange.get();h.assertTrue(range>=3&&range<=24,"Physical32-cell fixture contains actual configured assistance range");
            for(int i=-1;i<=range+1;i++)h.setBlock(SOURCE.east(i),i<0?Blocks.OBSIDIAN.defaultBlockState():i<=range?AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X):Blocks.AIR.defaultBlockState());
            var pos=h.absolutePos(SOURCE);var hit=new BlockHitResult(Vec3.atCenterOf(pos).add(.5,0,0),Direction.EAST,pos,false);var stack=new ItemStack(AllBlocks.COGWHEEL,2);var usedBefore=player.getStats().getValue(net.minecraft.stats.Stats.ITEM_USED.get(AllBlocks.COGWHEEL.asItem()));
            h.assertTrue(!use(h,player,stack,hit).consumesAction()&&stack.getCount()==2&&h.getLevel().getBlockState(h.absolutePos(SOURCE.east(range+1))).isAir(),"Actual source range exhaustion with blocked reverse end cannot extend or debit");
            h.assertTrue(player.getStats().getValue(net.minecraft.stats.Stats.ITEM_USED.get(AllBlocks.COGWHEEL.asItem()))==usedBefore,"Failed actual shaft extension awards no item-use statistic");
            var grip=new ItemStack(AllItems.EXTENDO_GRIP);player.setItemInHand(InteractionHand.OFF_HAND,grip);ExtendoGripItem.holdingExtendoGripIncreasesRange(player);
            h.assertTrue(use(h,player,stack,hit).consumesAction(),"Real equipped grip's native modifier extends the actual source shaft chain");
            placed(h,player,stack,AllBlocks.COGWHEEL,SOURCE.east(range+1),Direction.Axis.X);h.assertTrue(grip.getDamageValue()==1,"Actual native BlockItem placement applies one ordinary extendo action cost");
            h.assertTrue(player.getStats().getValue(net.minecraft.stats.Stats.ITEM_USED.get(AllBlocks.COGWHEEL.asItem()))==usedBefore+1,"Actual shaft-block helper awards exactly one native item-used statistic");
            for(int i=0;i<=range;i++)h.assertTrue(h.getLevel().getBlockState(h.absolutePos(SOURCE.east(i))).is(AllBlocks.SHAFT),"Every original chain shaft remains its actual owner");
        }h.succeed();
    }

    @GameTest public void ordinaryShaftAndCogUseSourcePreferredAxisCameraSneakAndControllerRules(GameTestHelper h){
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(SOURCE)).add(0,0,2))){
            var player=joined.player();var pos=h.absolutePos(SOURCE);var top=new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false);
            clear(h);h.setBlock(SOURCE,Blocks.STONE);player.setYRot(0);player.setXRot(0);var camera=new ItemStack(AllBlocks.SHAFT,2);
            h.assertTrue(use(h,player,camera,top).consumesAction(),"Actual ordinary shaft placement uses source camera axis without a preferred shaft");placed(h,player,camera,AllBlocks.SHAFT,SOURCE.above(),Direction.Axis.Z);
            clear(h);h.setBlock(SOURCE,Blocks.STONE);h.setBlock(SOURCE.above().east(),AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));var preferred=new ItemStack(AllBlocks.SHAFT,2);
            h.assertTrue(use(h,player,preferred,top).consumesAction(),"Actual ordinary shaft follows the unique existing shaft connection");placed(h,player,preferred,AllBlocks.SHAFT,SOURCE.above(),Direction.Axis.X);
            clear(h);h.setBlock(SOURCE,Blocks.STONE);h.setBlock(SOURCE.above().east(),AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));player.setShiftKeyDown(true);var sneak=new ItemStack(AllBlocks.SHAFT,2);
            h.assertTrue(use(h,player,sneak,top).consumesAction(),"Actual sneak overrides a real preferred shaft axis with clicked face");placed(h,player,sneak,AllBlocks.SHAFT,SOURCE.above(),Direction.Axis.Y);player.setShiftKeyDown(false);
            clear(h);h.setBlock(SOURCE,AllBlocks.ROTATION_SPEED_CONTROLLER.defaultBlockState().setValue(SpeedControllerBlock.HORIZONTAL_AXIS,Direction.Axis.X));var controller=new ItemStack(AllBlocks.LARGE_COGWHEEL,2);
            h.assertTrue(use(h,player,controller,top).consumesAction(),"Actual native large cog and speed controller placement remain compatible");placed(h,player,controller,AllBlocks.LARGE_COGWHEEL,SOURCE.above(),Direction.Axis.Z);
        }h.succeed();
    }

    @GameTest public void girderOnActualHorizontalShaftKeepsAxisWaterAndOneNativeReplacementOwnerWithOneDebit(GameTestHelper h){
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(SOURCE)).add(0,0,2))){
            var player=joined.player();
            for(var axis:List.of(Direction.Axis.X,Direction.Axis.Z))for(boolean water:new boolean[]{false,true}){
                clear(h);h.setBlock(SOURCE,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,axis).setValue(ShaftBlock.WATERLOGGED,water));var old=h.getLevel().getBlockEntity(h.absolutePos(SOURCE));var stock=new ItemStack(AllBlocks.METAL_GIRDER,2);
                h.assertTrue(use(h,player,stock,rim(h,Direction.Axis.Y)).consumesAction(),"Actual source horizontal shaft accepts real metal girder item");var state=h.getLevel().getBlockState(h.absolutePos(SOURCE));var owner=h.getLevel().getBlockEntity(h.absolutePos(SOURCE));
                h.assertTrue(state.is(AllBlocks.METAL_GIRDER_ENCASED_SHAFT)&&state.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS)==axis&&state.getValue(GirderEncasedShaftBlock.WATERLOGGED)==water,"Actual source girder conversion retains horizontal shaft axis and waterlogging");
                h.assertTrue(owner instanceof KineticBlockEntity&&owner!=old&&old.isRemoved()&&owner.getType()==AllBlockEntityTypes.ENCASED_SHAFT&&owner.getBlockState()==state,"Only the actual replacement native shaft BE owns the converted cell; old owner is removed");
                h.assertTrue(stock.getCount()==1&&player.getMainHandItem()==stock&&h.getLevel().getBlockState(h.absolutePos(SOURCE.east())).isAir(),"Exactly one finite girder is consumed by in-place conversion without adjacent girder placement");
            }
        }h.succeed();
    }

    @GameTest public void assistedCogWithActualOffhandGlueKeepsSourceHelperExemptionWithoutAutoGlueOrGlueDebit(GameTestHelper h){
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(SOURCE)).add(0,0,2))){
            var player=joined.player();clear(h);h.setBlock(SOURCE,AllBlocks.COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Y));var glue=new ItemStack(AllItems.SUPER_GLUE);player.setItemInHand(InteractionHand.OFF_HAND,glue);player.setYRot(180);player.setXRot(38);
            var stock=new ItemStack(AllBlocks.COGWHEEL,2);h.assertTrue(use(h,player,stock,rim(h,Direction.Axis.Y)).consumesAction(),"Actual offhand glue never cancels a source-assisted placement away from the gaze-adjacent cell");
            placed(h,player,stock,AllBlocks.COGWHEEL,SOURCE.east(),Direction.Axis.Y);
            h.assertTrue(glue.getDamageValue()==0&&player.getOffhandItem()==glue&&h.getLevel().getEntitiesOfClass(SuperGlueEntity.class,new AABB(h.absolutePos(SOURCE)).inflate(3)).isEmpty(),"Source helper exemption never applies or consumes actual offhand glue");
        }h.succeed();
    }
}
