package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.wrench.*;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Literal PlayerList player, actual native ray/shape/cargo and unchanged normal wrench interaction. Authored4 UNRUN. */
public final class RadialWrenchGameTests {
    private static final BlockPos P=new BlockPos(5,6,5);
    private static void look(ServerPlayer player,Vec3 point){
        var delta=point.subtract(player.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        player.setYRot(yaw);player.setYHeadRot(yaw);
        player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        // Native Entity.pick/getViewVector uses LivingEntity head yaw, not body yaw.
        if(player.getViewVector(1).normalize().dot(delta.normalize())<.99999)
            throw new AssertionError("Actual native radial fixture head/view must face the physical source target");
    }
    private static NativeSurvivalTestPlayer player(GameTestHelper h){var pos=Vec3.atCenterOf(h.absolutePos(P)).add(0,0,3);var session=NativeSurvivalTestPlayer.join(h,pos);session.player().setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH));look(session.player(),Vec3.atCenterOf(h.absolutePos(P)));return session;}
    private static RadialWrenchMenuSubmitPacket packet(GameTestHelper h,BlockState state,String property,String value){return new RadialWrenchMenuSubmitPacket(h.absolutePos(P),state,property,value);}
    @GameTest(structure="create_test:reservoir")public void realNativeRotationPreservesSameBarrelAndExactNamedCargo(GameTestHelper h){
        h.setBlock(P,Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING,Direction.NORTH));var barrel=h.getBlockEntity(P,BarrelBlockEntity.class);var cargo=new ItemStack(Items.DIAMOND,11);cargo.set(DataComponents.CUSTOM_NAME,Component.literal("Eleven original radial barrel diamonds"));barrel.setItem(0,cargo);
        try(var session=player(h)){
            var original=h.getBlockState(P);h.assertTrue(packet(h,original,"facing","west").apply(session.player()),"Real source-permitted rotation passes literal native player/wrench/look, native shape and survival");
            h.assertTrue(h.getBlockState(P)==original.setValue(BarrelBlock.FACING,Direction.WEST)&&h.getBlockEntity(P,BarrelBlockEntity.class)==barrel&&barrel.getItem(0)==cargo&&cargo.getCount()==11&&session.player().getMainHandItem().is(AllItems.WRENCH),"Actual native block lifecycle retains exact original BE/cargo object and finite source stock");
            h.assertFalse(packet(h,original,"facing","east").apply(session.player()),"A stale screen cannot overwrite a real newer state");
            h.assertTrue(h.getBlockEntity(P,BarrelBlockEntity.class)==barrel&&barrel.getItem(0)==cargo&&cargo.getCount()==11,"Refused replay retains same physical source custody");
        }h.succeed();
    }
    @GameTest(structure="create_test:reservoir")public void realPlayerCannotForgeOtherPropertiesGearReachOrObstructedLook(GameTestHelper h){
        h.setBlock(P,Blocks.BARREL.defaultBlockState());var state=h.getBlockState(P);
        try(var session=player(h)){
            var player=session.player();
            h.assertFalse(packet(h,state,"open","true").apply(player),"Native barrel OPEN is outside pinned rotation properties");
            h.assertFalse(packet(h,state,"facing","fabricated").apply(player),"Unknown value cannot fabricate native state");
            h.assertFalse(packet(h,Blocks.STONE.defaultBlockState(),"axis","x").apply(player),"Different expected block cannot replace real source");
            player.setItemInHand(InteractionHand.OFF_HAND,player.getMainHandItem());player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);h.assertFalse(packet(h,state,"facing","west").apply(player),"Offhand-only wrench cannot authorize source main-hand menu");player.setItemInHand(InteractionHand.MAIN_HAND,player.getOffhandItem());player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            player.setGameMode(GameType.SPECTATOR);h.assertFalse(packet(h,state,"facing","west").apply(player),"Actual spectator cannot rotate");player.setGameMode(GameType.ADVENTURE);h.assertFalse(packet(h,state,"facing","west").apply(player),"Actual native mayBuild ability is required");player.setGameMode(GameType.SURVIVAL);
            var actual=player.position();player.setPos(actual.add(0,0,20));look(player,Vec3.atCenterOf(h.absolutePos(P)));h.assertFalse(packet(h,state,"facing","west").apply(player),"Native interaction reach cannot be fabricated by packet");player.setPos(actual);look(player,Vec3.atCenterOf(h.absolutePos(P)));
            h.setBlock(P.south(),Blocks.STONE);h.setBlock(P.south().above(),Blocks.STONE);h.assertFalse(packet(h,state,"facing","west").apply(player),"Actual native outline ray cannot pass physical obstruction");h.setBlock(P.south(),Blocks.AIR);h.setBlock(P.south().above(),Blocks.AIR);
            h.assertTrue(h.getBlockState(P)==state&&packet(h,state,"facing","west").apply(player),"All rejected requests preserve source, then genuine native look succeeds");
            var missing=new BlockPos(25600000,6,25600000);h.assertFalse(LoadedLevelAccess.isLoaded(h.getLevel(),missing),"Actual distant frontier is unavailable before forged request");h.assertFalse(new RadialWrenchMenuSubmitPacket(missing,state,"facing","east").apply(player),"Unknown out-of-range target refuses");h.assertFalse(LoadedLevelAccess.isLoaded(h.getLevel(),missing),"Refused packet creates no actual native chunk");
        }h.succeed();
    }
    @GameTest(structure="create_test:reservoir")public void originalWheelBlacklistPropertyDomainsAndOrdinaryWrenchRemainNative(GameTestHelper h){
        h.assertTrue(RadialWrenchRotation.propertiesFor(AllBlocks.LARGE_WATER_WHEEL.defaultBlockState()).isEmpty()&&RadialWrenchRotation.propertiesFor(AllBlocks.WATER_WHEEL_STRUCTURAL.defaultBlockState()).isEmpty()&&RadialWrenchRotation.propertiesFor(Blocks.STONE.defaultBlockState()).isEmpty(),"Both actual source wheel exclusions and propertyless native block remain unavailable");
        var hopper=Blocks.HOPPER.defaultBlockState();h.assertTrue(RadialWrenchRotation.states(hopper,HopperBlock.FACING).size()==5&&RadialWrenchRotation.select(hopper,"facing","up").isEmpty(),"Native hopper's exact source five-value property excludes UP");
        var sequencer=AllBlocks.SEQUENCED_GEARSHIFT.defaultBlockState();h.assertTrue(RadialWrenchRotation.propertiesFor(sequencer).size()==2&&RadialWrenchRotation.select(sequencer,"vertical","true").orElseThrow().getValue(SequencedGearshiftBlock.VERTICAL),"Original sequencer offers both axis and vertical choices without accepting runtime stage properties");
        h.setBlock(P,AllBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS,Direction.Axis.X));
        try(var session=player(h)){
            var owner=LoadedLevelAccess.blockEntity(h.getLevel(),h.absolutePos(P));var before=h.getBlockState(P);var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)),Direction.UP,h.absolutePos(P),false);
            h.assertTrue(session.player().getMainHandItem().getItem().useOn(new UseOnContext(session.player(),InteractionHand.MAIN_HAND,hit)).consumesAction(),"Ordinary actual WrenchItem.useOn remains available without radial binding");
            h.assertTrue(h.getBlockState(P)==WrenchItem.rotate(before,Direction.UP)&&LoadedLevelAccess.blockEntity(h.getLevel(),h.absolutePos(P))==owner,"Existing ordinary wrench changes its native axis and retains actual original kinetic owner");
        }h.succeed();
    }
    @GameTest(structure="create_test:reservoir")public void realLoadedChunkEdgeDefersUnknownNativeNeighborWithoutLoadingOrLosingCargo(GameTestHelper h){
        try(var session=player(h)){
        var level=h.getLevel();var origin=h.absolutePos(P);int directionX=origin.getX()<0?1:-1,directionZ=origin.getZ()<0?1:-1;int chunkX=(origin.getX()>>4)+1024*directionX,chunkZ=(origin.getZ()>>4)+1024*directionZ;
        // Prepare one real native FULL source column beyond the cohort; never request its eastern neighbor as FULL.
        int attempts=0;while(attempts<256&&(level.getChunkSource().getChunkNow(chunkX,chunkZ)!=null||level.getChunkSource().getChunkNow(chunkX+1,chunkZ)!=null)){chunkX+=32*directionX;attempts++;}
        var target=new BlockPos((chunkX<<4)+15,origin.getY()+16,(chunkZ<<4)+8);var absent=target.east();
        h.assertTrue(attempts<256&&level.isInValidBounds(target)&&level.isInValidBounds(absent)&&level.getWorldBorder().isWithinBounds(target)&&level.getWorldBorder().isWithinBounds(absent)&&!LoadedLevelAccess.isLoaded(level,target)&&!LoadedLevelAccess.isLoaded(level,absent),"Dedicated real native frontier begins with two genuinely unavailable columns safely inside the actual world border");
        level.getChunk(chunkX,chunkZ);
        h.assertTrue(attempts<256&&level.getChunkSource().getChunkNow(chunkX,chunkZ)!=null&&level.getChunkSource().getChunkNow(chunkX+1,chunkZ)==null,"Actual native level has a finite loaded east frontier");
        var old=LoadedLevelAccess.blockState(level,target);var oldOwner=LoadedLevelAccess.blockEntity(level,target);
        h.assertTrue(old.isAir()&&oldOwner==null,"Real frontier fixture uses an actually empty native cell without replacing material");
        var state=Blocks.BARREL.defaultBlockState();h.assertTrue(LoadedLevelAccess.setBlockQuiet(level,target,state),"Actual already loaded native chunk installs one real frontier barrel without shape reads");var barrel=(BarrelBlockEntity)LoadedLevelAccess.blockEntity(level,target);var cargo=new ItemStack(Items.DIAMOND,11);barrel.setItem(0,cargo);
        try{
            var player=session.player();player.setPos(Vec3.atCenterOf(target).add(-2,0,0));look(player,Vec3.atCenterOf(target));
            h.assertTrue(LoadedLevelAccess.isLoaded(level,target)&&!LoadedLevelAccess.isLoaded(level,absent)&&LoadedLevelAccess.blockState(level,target)==state&&LoadedLevelAccess.blockEntity(level,target)==barrel&&barrel.getItem(0)==cargo,"Actual barrel and literal player preparation leave the precise eastern shape-read cell genuinely unavailable immediately before production apply");
            h.assertFalse(new RadialWrenchMenuSubmitPacket(target,state,"facing","west").apply(player),"Exact native shape query refuses the genuinely unavailable neighboring cell");
            h.assertTrue(!LoadedLevelAccess.isLoaded(level,absent)&&LoadedLevelAccess.blockState(level,target)==state&&LoadedLevelAccess.blockEntity(level,target)==barrel&&barrel.getItem(0)==cargo&&cargo.getCount()==11,"Rejected native frontier query creates no chunk and preserves the same real barrel and original finite stock");
        }finally{barrel.clearContent();LoadedLevelAccess.setBlockQuiet(level,target,old);}
        }h.succeed();
    }
}
