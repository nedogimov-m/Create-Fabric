package com.simibubi.create.test;

import com.simibubi.create.content.equipment.TreeFertilizerSimulation;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.Fluids;

/** Real registered bonemealable sapling callback; bounded deterministic world/effect/error boundaries. */
public final class FertilizerTestFixtures implements ModInitializer {
 public static SaplingBlock SAPLING;
 public enum Mode{NORMAL,ERROR,UNKNOWN_BE,ENTITY}
 public static final class NativeRefusal extends Error{public NativeRefusal(){super("Genuine native fertilizer sapling callback");}}
 public static final class Observation {public int saplings;public boolean allStageOne=true,air,random,detachedHive;public net.minecraft.world.level.block.state.BlockState soil,soilAfter;public Entity entity;}
 public record Plan(ServerLevel level,Mode mode,Observation observation,NativeRefusal failure){}
 private static final ThreadLocal<Plan> ACTIVE=new ThreadLocal<>();
 public static void with(Plan plan,Runnable action){if(ACTIVE.get()!=null)throw new IllegalStateException("Nested fertilizer fixture");ACTIVE.set(plan);try{action.run();}finally{ACTIVE.remove();}}
 @Override public void onInitialize(){var key=ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath("create_test","native_fertilizer_sapling"));SAPLING=Registry.register(BuiltInRegistries.BLOCK,key,new SaplingBlock(TreeGrower.OAK,BlockBehaviour.Properties.of().setId(key).noCollision().instabreak()){
  @Override public void performBonemeal(ServerLevel level,RandomSource random,BlockPos pos,BlockState state,BonemealSource source){var plan=Objects.requireNonNull(ACTIVE.get(),"Real fertilizer fixture is armed only during actual native use");if(level!=plan.level||!pos.equals(TreeFertilizerSimulation.ORIGIN)||source!=BonemealSource.INTERACTION)throw new IllegalStateException("Wrong real native fertilizer context");var observation=plan.observation;for(var offset:BlockPos.betweenClosed(-1,0,-1,1,0,1)){var candidate=level.getBlockState(pos.offset(offset));if(candidate.is(this)){observation.saplings++;observation.allStageOne&=candidate.getValue(STAGE)==1;}}observation.soil=level.getBlockState(pos.below());observation.air=level.getBlockState(new BlockPos(2000,100,2000)).isAir();observation.random=random==level.getRandom();level.setBlockAndUpdate(pos,Blocks.OAK_LOG.defaultBlockState());if(plan.mode==Mode.ERROR)throw plan.failure;if(plan.mode==Mode.UNKNOWN_BE){level.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());level.getBlockEntity(pos);throw new AssertionError("Unsupported virtual chest must fail before original chunk lookup");}
   level.setBlockAndUpdate(pos.east(),Blocks.OAK_LOG.defaultBlockState());level.setBlockAndUpdate(pos.west(),Blocks.OAK_LEAVES.defaultBlockState());level.setBlockAndUpdate(pos.north(),Blocks.OAK_LOG.defaultBlockState());level.setBlockAndUpdate(pos.above(),Blocks.OAK_LEAVES.defaultBlockState());level.setBlockAndUpdate(pos.south(),Blocks.BEE_NEST.defaultBlockState());var hive=(BeehiveBlockEntity)level.getBlockEntity(pos.south());observation.detachedHive=hive!=null&&hive.getLevel()==null;hive.storeBee(BeehiveBlockEntity.Occupant.create(20));hive.storeBee(BeehiveBlockEntity.Occupant.create(40));level.setBlockAndUpdate(pos.below(),Blocks.ROOTED_DIRT.defaultBlockState());observation.soilAfter=level.getBlockState(pos.below());level.setBlockAndUpdate(pos.north().east(),Blocks.PODZOL.defaultBlockState());level.scheduleTick(pos.above(),Blocks.OAK_LEAVES,1);level.scheduleTick(pos.above(),Fluids.WATER,1);
   if(plan.mode==Mode.ENTITY){var entity=Objects.requireNonNull(EntityTypes.PIG.create(level,EntitySpawnReason.STRUCTURE));entity.snapTo(pos.north().above().getX()+.5,pos.getY()+1,pos.getZ()-.5);observation.entity=entity;if(!level.addFreshEntity(entity)||level.getEntity(entity.getUUID())!=null)throw new IllegalStateException("Native generated entity must stay deferred during the actual dream");}
  }
 });}
}
