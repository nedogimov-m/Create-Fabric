package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.decoration.*;
import com.simibubi.create.content.decoration.bracket.*;
import com.simibubi.create.content.fluids.pipes.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;

/** Actual finite-item/native factory/custody/neighbor callbacks. All cases UNRUN. */
public final class DecorationGameTests {
    private static final BlockPos P=new BlockPos(4,4,4);
    private static Player player(GameTestHelper h,ItemStack stack){var p=h.makeMockPlayer(GameType.SURVIVAL);p.setPos(Vec3.atCenterOf(h.absolutePos(P)).add(0,0,1));p.setItemInHand(InteractionHand.MAIN_HAND,stack);return p;}
    private static BlockHitResult hit(GameTestHelper h,Direction face){return new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)).relative(face,.5),face,h.absolutePos(P),false);}
    private static UseOnContext context(GameTestHelper h,Player player,Direction face){return new UseOnContext(player,InteractionHand.MAIN_HAND,hit(h,face));}
    private static BracketedBlockEntityBehaviour bracket(GameTestHelper h){return BlockEntityBehaviour.get(h.getLevel(),h.absolutePos(P),BracketedBlockEntityBehaviour.TYPE);}
    private static void attach(GameTestHelper h,Player p){h.assertTrue(p.getMainHandItem().getItem().useOn(context(h,p,Direction.UP)).consumesAction(),"Real bracket BlockItem interaction");}
    private static int count(Player player,Item item){int total=0;for(int slot=0;slot<player.getInventory().getContainerSize();slot++){var s=player.getInventory().getItem(slot);if(s.is(item))total+=s.getCount();}return total;}
    private static int drops(GameTestHelper h,Item item){return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(P)).inflate(3)).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum();}
    private static void shaft(GameTestHelper h){h.setBlock(P,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));}

    @GameTest public void genuineBracketSwapAndWrenchConserveOriginalFiniteItems(GameTestHelper h){
        shaft(h);var wood=new ItemStack(AllBlocks.WOODEN_BRACKET,2);var p=player(h,wood);attach(h,p);
        h.assertTrue(wood.getCount()==1&&bracket(h).getBracket().is(AllBlocks.WOODEN_BRACKET),"One original bracket moves into host");
        attach(h,p);h.assertTrue(wood.getCount()==1,"Identical bracket is not paid twice");
        p.getInventory().setItem(8,wood);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.METAL_BRACKET,2));attach(h,p);
        h.assertTrue(count(p,AllBlocks.WOODEN_BRACKET.asItem())==2&&count(p,AllBlocks.METAL_BRACKET.asItem())==1,"Replacing returns exactly the old paid bracket and pays one new bracket");
        p.getInventory().setItem(7,p.getMainHandItem());p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH));
        var before=h.getBlockState(P);AllItems.WRENCH.useOn(context(h,p,Direction.UP));
        h.assertTrue(h.getBlockState(P)==before&&!bracket(h).isBracketPresent()&&count(p,AllBlocks.METAL_BRACKET.asItem())==2,"First real wrench action removes one bracket before rotating host");h.succeed();
    }
    @GameTest public void unsupportedNativeHostsAndJunctionPipesCannotAcceptABracket(GameTestHelper h){
        h.setBlock(P,AllBlocks.CREATIVE_MOTOR);var held=new ItemStack(AllBlocks.METAL_BRACKET,3);var p=player(h,held);
        h.assertTrue(!held.getItem().useOn(context(h,p,Direction.UP)).consumesAction()&&held.getCount()==3,"Actual powered shaft is not a source simple-shaft owner");
        h.setBlock(P,AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X).setValue(BlockStateProperties.NORTH,true));
        h.assertTrue(!held.getItem().useOn(context(h,p,Direction.UP)).consumesAction()&&held.getCount()==3&&!bracket(h).isBracketPresent(),"Actual pipe junction cannot accept straight-pipe bracket");h.succeed();
    }
    @GameTest public void nativeFactoryAndSchematicRequirementRetainOneActualBracket(GameTestHelper h){
        shaft(h);attach(h,player(h,new ItemStack(AllBlocks.WOODEN_BRACKET)));
        var owner=h.getBlockEntity(P,KineticBlockEntity.class);var saved=owner.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored=BlockEntity.loadStatic(owner.getBlockPos(),owner.getBlockState(),saved,h.getLevel().registryAccess());
        var actual=BlockEntityBehaviour.get(restored,BracketedBlockEntityBehaviour.TYPE);
        h.assertTrue(actual==BlockEntityBehaviour.get(restored,BracketedBlockEntityBehaviour.TYPE)&&actual.getBracket()==bracket(h).getBracket(),"Native registered factory restores exact state into one cached owner component");
        var requirement=ItemRequirement.of(restored.getBlockState(),restored).getRequiredItems();
        h.assertTrue(requirement.stream().filter(r->r.stack.is(AllBlocks.WOODEN_BRACKET.asItem())).mapToInt(r->r.stack.getCount()).sum()==1,"Actual schematic consumption includes exactly one owned bracket");h.succeed();
    }
    @GameTest public void ordinaryWorldBreakDropsPaidBracketOnlyOnce(GameTestHelper h){
        shaft(h);attach(h,player(h,new ItemStack(AllBlocks.METAL_BRACKET)));var owner=h.getBlockEntity(P,KineticBlockEntity.class);
        h.getLevel().destroyBlock(h.absolutePos(P),false);owner.preRemoveSideEffects(h.absolutePos(P),owner.getBlockState());
        h.assertTrue(drops(h,AllBlocks.METAL_BRACKET.asItem())==1&&!owner.bracketBehaviour().isBracketPresent(),"Native removal clears custody before its single real item drop");h.succeed();
    }
    @GameTest public void sourceShaftWaterloggingPreservesBracketButAxisChangeReturnsIt(GameTestHelper h){
        shaft(h);attach(h,player(h,new ItemStack(AllBlocks.METAL_BRACKET)));
        h.setBlock(P,h.getBlockState(P).setValue(BlockStateProperties.WATERLOGGED,true));
        h.assertTrue(bracket(h).isBracketPresent()&&drops(h,AllBlocks.METAL_BRACKET.asItem())==0,"Pure native waterlogging keeps source bracket ownership");
        h.setBlock(P,h.getBlockState(P).setValue(ShaftBlock.AXIS,Direction.Axis.Z));
        h.assertTrue(!bracket(h).isBracketPresent()&&drops(h,AllBlocks.METAL_BRACKET.asItem())==1,"Actual source shaft geometry change refunds its bracket exactly once");h.succeed();
    }
    @GameTest public void actualPipeBracketFreezesConnectionsAndWrenchReopensNativeNeighborDiscovery(GameTestHelper h){
        h.setBlock(P,AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));attach(h,player(h,new ItemStack(AllBlocks.METAL_BRACKET)));
        var before=h.getBlockState(P);h.setBlock(P.north(),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.Z));
        h.assertTrue(h.getBlockState(P)==before&&!FluidPipeBlock.canConnectTo(h.getLevel(),h.absolutePos(P),before,Direction.NORTH),"Actual neighboring pipe cannot create a bracket-blocked side connection");
        var p=player(h,new ItemStack(AllItems.WRENCH));AllItems.WRENCH.useOn(context(h,p,Direction.UP));
        h.assertTrue(!bracket(h).isBracketPresent()&&h.getBlockState(P).getValue(BlockStateProperties.NORTH)&&count(p,AllBlocks.METAL_BRACKET.asItem())==1,"Source wrench removal returns actual bracket and recalculates real fluid-pipe branch");h.succeed();
    }
    @GameTest public void actualMovingCaptureOwnsBracketWithoutAnExtraWorldDrop(GameTestHelper h)throws Exception{
        shaft(h);attach(h,player(h,new ItemStack(AllBlocks.WOODEN_BRACKET)));
        var structure=new BearingContraption(false,Direction.UP);
        h.assertTrue(structure.assemble(h.getLevel(),h.absolutePos(P.below()))&&structure.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"Actual native capture transfers the bracketed source shaft");
        h.assertTrue(h.getBlockState(P).isAir()&&drops(h,AllBlocks.WOODEN_BRACKET.asItem())==0,"Native skip-side-effects capture leaves no second paid bracket in world");
        var restored=Contraption.fromNBT(h.getLevel(),structure.writeNBT(h.getLevel().registryAccess(),false),false);
        var transform=new StructureTransform(h.absolutePos(P),0,90,0);
        h.assertTrue(restored.tryAddBlocksToWorld(h.getLevel(),transform),"Native persisted contraption restores through actual registered factory");
        var current=BlockEntityBehaviour.get(h.getLevel(),h.absolutePos(P),BracketedBlockEntityBehaviour.TYPE);
        h.assertTrue(current!=null&&current.getBracket()!=null&&current.getBracket().is(AllBlocks.WOODEN_BRACKET)&&drops(h,AllBlocks.WOODEN_BRACKET.asItem())==0,"Restored moving owner retains the single original bracket");h.succeed();
    }
    @GameTest(maxTicks=15) public void nativeMetalScaffoldDoesNotFallWithoutASupportBlock(GameTestHelper h){
        h.setBlock(P,AllBlocks.ANDESITE_SCAFFOLD.defaultBlockState().setValue(MetalScaffoldingBlock.BOTTOM,true));
        h.getLevel().scheduleTick(h.absolutePos(P),AllBlocks.ANDESITE_SCAFFOLD,1);
        h.runAfterDelay(5,()->{h.assertTrue(h.getBlockState(P).is(AllBlocks.ANDESITE_SCAFFOLD)&&h.getBlockState(P).canSurvive(h.getLevel(),h.absolutePos(P)),"Actual inherited scaffold callback preserves source non-falling metal state");h.succeed();});
    }
    @GameTest public void nativeMetalScaffoldingKeepsSourceSneakDescentInsteadOfLadderClamping(GameTestHelper h){
        h.setBlock(P,AllBlocks.ANDESITE_SCAFFOLD.defaultBlockState().setValue(MetalScaffoldingBlock.BOTTOM,false));
        var p=player(h,ItemStack.EMPTY);p.setPos(Vec3.atLowerCornerOf(h.absolutePos(P)).add(.5,.2,.5));p.setShiftKeyDown(true);p.setOnGround(false);p.setDeltaMovement(0,-.1,0);
        h.assertTrue(p.onClimbable(),"Actual source metal scaffold belongs to native climbable tag");p.travel(Vec3.ZERO);
        h.assertTrue(p.getDeltaMovement().y<0,"Actual native26.3 movement recognizes metal scaffolding and retains source sneak descent");h.succeed();
    }
    @GameTest public void hangingSourceLadderKeepsFacingAndAcceptsOnlyTheSameMetalAbove(GameTestHelper h){
        h.setBlock(P.above().south(),Blocks.STONE);var lower=AllBlocks.BRASS_LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH);h.setBlock(P.above(),lower);h.setBlock(P,lower);
        h.assertTrue(lower.canSurvive(h.getLevel(),h.absolutePos(P)),"Actual hanging ladder is supported by identical facing/metal above");
        h.setBlock(P.above(),AllBlocks.COPPER_LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH));
        h.assertTrue(!lower.canSurvive(h.getLevel(),h.absolutePos(P)),"Different real metal ladder above is not a valid source support");h.succeed();
    }
    @GameTest public void poweredSourceTrapdoorStillRespondsToHandAndWrench(GameTestHelper h){
        h.setBlock(P,AllBlocks.TRAIN_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.POWERED,true));var p=player(h,ItemStack.EMPTY);
        h.getBlockState(P).useWithoutItem(h.getLevel(),p,hit(h,Direction.UP));
        h.assertTrue(h.getBlockState(P).getValue(TrapDoorBlock.OPEN),"Actual hand action can open source trapdoor while powered");
        var facing=h.getBlockState(P).getValue(TrapDoorBlock.FACING);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH));AllItems.WRENCH.useOn(context(h,p,Direction.UP));
        h.assertTrue(h.getBlockState(P).getValue(TrapDoorBlock.FACING)==facing.getClockWise(),"Actual source wrench rotates native horizontal trapdoor state");h.succeed();
    }
}
