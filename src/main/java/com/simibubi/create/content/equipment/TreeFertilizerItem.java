package com.simibubi.create.content.equipment;

import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Source detached native bonemeal simulation/replay, not repeated ordinary bonemeal. */
public final class TreeFertilizerItem extends Item {
 public TreeFertilizerItem(Properties properties){super(properties);}
 private static BlockState staged(BlockState state){return state.hasProperty(BlockStateProperties.STAGE)?state.setValue(BlockStateProperties.STAGE,1):state;}
 @Override public InteractionResult useOn(UseOnContext context){
  var level=context.getLevel();var sapling=context.getClickedPos();var state=LoadedLevelAccess.blockState(level,sapling);var block=state.getBlock();if(!(block instanceof BonemealableBlock bonemealable)||!state.is(BlockTags.SAPLINGS)||state.getOptionalValue(MangrovePropaguleBlock.HANGING).orElse(false)||TreeFertilizerSimulation.active(level)!=null)return InteractionResult.PASS;
  var player=context.getPlayer();if(player!=null&&(player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,sapling)))return InteractionResult.PASS;
  if(level.isClientSide()){BoneMealItem.addGrowthParticles(level,sapling,100);return InteractionResult.SUCCESS;}if(!(level instanceof ServerLevel server)||!LoadedLevelAccess.isLoaded(level,sapling.below()))return InteractionResult.PASS;
  var soil=LoadedLevelAccess.blockState(level,sapling.below());if(soil.is(BlockTags.SUBSTRATE_OVERWORLD))soil=Blocks.DIRT.defaultBlockState();var starts=new java.util.LinkedHashMap<BlockPos,BlockState>();for(var offset:BlockPos.betweenClosed(-1,0,-1,1,0,1))if(LoadedLevelAccess.blockState(level,sapling.offset(offset)).getBlock()==block)starts.put(offset.above(10).immutable(),staged(state));
  List<TreeFertilizerSimulation.Placement> placements;TreeFertilizerSimulation simulation;
  try(var dream=TreeFertilizerSimulation.open(server,soil)){simulation=dream;starts.forEach(dream::setBlock);bonemealable.performBonemeal(server,dream.random,TreeFertilizerSimulation.ORIGIN,staged(state),BonemealSource.INTERACTION);placements=dream.placements();}
  simulation.replay(placements,sapling,player);if(player!=null&&!player.isCreative())context.getItemInHand().shrink(1);return InteractionResult.SUCCESS;
 }
 public static void register(){net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,Create.asResource("base"))).register(output->output.accept(AllItems.TREE_FERTILIZER));}
}
