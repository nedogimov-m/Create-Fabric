package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllFluids;
import com.simibubi.create.content.fluids.FluidPhysics;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;

public class FluidPhysicsGameTests {
    private static final BlockPos P=new BlockPos(3,3,3);
    private static net.minecraft.world.entity.animal.cow.Cow immersedCow(GameTestHelper h,Block liquid,int x){
        for(var pos:BlockPos.betweenClosed(new BlockPos(x-1,2,2),new BlockPos(x+1,5,4)))h.setBlock(pos,pos.getY()==2?Blocks.STONE:liquid);
        var cow=h.spawnWithNoFreeWill(EntityTypes.COW,x+0.5f,3.05f,3.5f);cow.setNoGravity(true);cow.baseTick();return cow;
    }
    @GameTest public void customFluidsTrackImmersionWithoutWaterSemantics(GameTestHelper h){
        for(var entry:java.util.List.of(new Object[]{AllBlocks.HONEY,2},new Object[]{AllBlocks.CHOCOLATE,6})){
            var cow=immersedCow(h,(Block)entry[0],(int)entry[1]);
            h.assertTrue(FluidPhysics.immersed(cow)&&FluidPhysics.eyesImmersed(cow),"Body and eyes tracked by native fluid interaction");
            h.assertTrue(cow.isInLiquid()&&cow.isInFloatableFluid(),"Fluid movement and mob float AI supported");
            h.assertValueEqual(net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic(cow,cow.blockPosition()),net.minecraft.world.level.pathfinder.PathType.WATER,"Native mob pathfinding recognizes liquid");
            h.assertFalse(cow.isInWater()||cow.isInLava(),"Water and lava identity retained");
            cow.igniteForSeconds(10);cow.fallDistance=10;cow.baseTick();
            h.assertTrue(cow.isOnFire(),"Fluid does not extinguish");h.assertValueEqual(cow.fallDistance,5.0,"Original fall distance multiplier");
        }
        h.assertFalse(AllFluids.HONEY.defaultFluidState().is(FluidTags.WATER)||AllFluids.CHOCOLATE.defaultFluidState().is(FluidTags.WATER),"No water tagging");h.succeed();
    }
    @GameTest public void honeyAndChocolateUseNativeWaterDragAndGravity(GameTestHelper h){
        var reference=immersedCow(h,Blocks.WATER,2);
        var honey=immersedCow(h,AllBlocks.HONEY,6);
        var chocolate=immersedCow(h,AllBlocks.CHOCOLATE,10);
        for(var cow:java.util.List.of(reference,honey,chocolate)){
            cow.setNoGravity(false);cow.setDeltaMovement(new Vec3(0.2,-0.02,0));cow.travel(Vec3.ZERO);
        }
        for(var cow:java.util.List.of(honey,chocolate)){
            h.assertTrue(cow.getDeltaMovement().distanceTo(reference.getDeltaMovement())<0.000001,"Same swimming drag and gravity as native water");
            cow.setSprinting(true);cow.updateSwimming();h.assertTrue(cow.isSwimming(),"Immersed sprint starts swimming");
            cow.setSprinting(false);cow.updateSwimming();h.assertFalse(cow.isSwimming(),"Stopping sprint ends swimming");
        }h.succeed();
    }
    @GameTest public void immersionConsumesAirAndDrowningRespectsWaterBreathing(GameTestHelper h){
        var honey=immersedCow(h,AllBlocks.HONEY,2);var chocolate=immersedCow(h,AllBlocks.CHOCOLATE,6);
        for(var cow:java.util.List.of(honey,chocolate)){
            cow.setAirSupply(-19);float health=cow.getHealth();cow.baseTick();
            h.assertValueEqual(cow.getHealth(),health-2,"Drowning damage at native cadence");h.assertValueEqual(cow.getAirSupply(),0,"Air reset after drowning damage");
            cow.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,200));cow.setAirSupply(100);cow.baseTick();
            h.assertTrue(cow.getAirSupply()>=100,"Breathing effect protects air supply");
        }h.succeed();
    }
    @GameTest public void nativeCurrentPushesItemsAndItemsFloat(GameTestHelper h){
        for(var pos:BlockPos.betweenClosed(new BlockPos(1,2,2),new BlockPos(5,2,4)))h.setBlock(pos,Blocks.STONE);
        h.setBlock(P.west(),AllBlocks.HONEY);h.setBlock(P,AllBlocks.HONEY.defaultBlockState().setValue(LiquidBlock.LEVEL,2));
        h.setBlock(P.east(),AllBlocks.HONEY.defaultBlockState().setValue(LiquidBlock.LEVEL,4));
        var item=h.spawnItem(Items.DIAMOND,3.5f,3.1f,3.5f);item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);item.baseTick();
        h.assertTrue(item.getDeltaMovement().x>0,"Custom fluid current applied with native current accumulator");
        item.setDeltaMovement(Vec3.ZERO);item.tick();h.assertTrue(item.getDeltaMovement().y>0,"Items float using native fluid buoyancy");h.succeed();
    }
}
