package com.simibubi.create.content.contraptions.actors.harvester;

import com.simibubi.create.Create;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.content.contraptions.ContraptionSettings;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour;
import com.simibubi.create.foundation.mixin.accessor.CropBlockAccessor;
import com.simibubi.create.foundation.utility.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.Vec3;

public class HarvesterMovementBehaviour implements MovementBehaviour {
    public static final TagKey<Block> NON_HARVESTABLE=TagKey.create(Registries.BLOCK,Create.asResource("non_harvestable")),SUGAR_CANE_VARIANTS=TagKey.create(Registries.BLOCK,Create.asResource("sugar_cane_variants"));
    /** Fabric replacement for NeoForge SpecialPlantable/custom plant recognition. */
    public static final SimpleRegistry<Block,PlantCheck> SPECIAL_PLANTS=SimpleRegistry.create();
    @FunctionalInterface public interface PlantCheck{boolean canHarvest(Level level,BlockPos pos,BlockState state);}
    @Override public boolean isActive(MovementContext context){return MovementBehaviour.super.isActive(context)&&!DrillMovementBehaviour.backwards(context,context.state.getValue(HarvesterBlock.FACING));}
    @Override public Vec3 getActiveAreaOffset(MovementContext context){return context.state.getValue(HarvesterBlock.FACING).getUnitVec3().scale(.45);}
    @Override public void visitNewPosition(MovementContext context,BlockPos pos){
        if(!(context.world instanceof ServerLevel level)||!LoadedLevelAccess.isLoaded(level,pos))return;var state=LoadedLevelAccess.blockState(level,pos);if(state.isAir()||state.is(NON_HARVESTABLE))return;
        boolean cuttable=!isValidCrop(level,pos,state);if(cuttable&&!isValidOther(level,pos,state))return;
        var tool=state.is(BlockTags.LEAVES)?new ItemStack(Items.SHEARS):ItemStack.EMPTY;var seedSubtracted=new AtomicBoolean(cuttable);var seed=new ItemStack(state.getBlock());
        BlockHelper.destroyBlockAs(level,pos,null,tool,state.is(BlockTags.LEAVES)?.45f:1,stack->{if(ContraptionSettings.harvesterReplants&&!seedSubtracted.get()&&ItemStack.isSameItem(stack,seed)){stack.shrink(1);seedSubtracted.set(true);}collectOrDropItem(context,stack);});
        var cut=cutCrop(level,pos,state);level.setBlockAndUpdate(pos,cut.canSurvive(level,pos)?cut:Blocks.AIR.defaultBlockState());
    }
    public boolean isValidCrop(Level level,BlockPos pos,BlockState state){
        boolean partial=ContraptionSettings.harvestPartiallyGrown,replant=ContraptionSettings.harvesterReplants;
        if(state.getBlock() instanceof CropBlock crop)return partial?state!=crop.getStateForAge(0)||!replant:crop.isMaxAge(state);
        if(state.getCollisionShape(level,pos).isEmpty()||state.getBlock() instanceof CocoaBlock)for(var property:state.getProperties())if(property instanceof IntegerProperty age&&property.getName().equals("age")){
            int value=state.getValue(age);if(state.getBlock() instanceof SweetBerryBushBlock&&value<=1&&replant)continue;if(value==0&&replant||!partial&&age.getPossibleValues().size()-1!=value)continue;return true;
        }return false;
    }
    public boolean isValidOther(Level level,BlockPos pos,BlockState state){
        var block=state.getBlock();if(block instanceof CropBlock)return false;if(block instanceof SugarCaneBlock||state.is(BlockTags.LEAVES))return true;if(block instanceof CocoaBlock)return state.getValue(CocoaBlock.AGE)==CocoaBlock.MAX_AGE;
        if(!state.getCollisionShape(level,pos).isEmpty())return false;if(block instanceof GrowingPlantBlock)return true;
        for(var property:state.getProperties())if(property instanceof IntegerProperty&&property.getName().equals("age"))return false;
        if(block instanceof MushroomBlock&&net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("farmersdelight")){var soil=BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("farmersdelight","rich_soil"));if(soil!=null&&LoadedLevelAccess.blockState(level,pos.below()).is(soil))return false;}
        if(block instanceof VegetationBlock)return true;var extension=SPECIAL_PLANTS.get(block);return extension!=null&&extension.canHarvest(level,pos,state);
    }
    private BlockState cutCrop(Level level,BlockPos pos,BlockState state){
        if(!ContraptionSettings.harvesterReplants)return state.getFluidState().createLegacyBlock();var block=state.getBlock();
        if(block instanceof CropBlock crop){var reset=crop.getStateForAge(0);return reset.is(block)?state.setValue(((CropBlockAccessor)crop).create$callGetAgeProperty(),0):reset;}
        if(state.is(Blocks.SWEET_BERRY_BUSH))return state.setValue(BlockStateProperties.AGE_3,1);
        if(state.is(SUGAR_CANE_VARIANTS)||block instanceof GrowingPlantBlock)return state.getFluidState().createLegacyBlock();
        if(state.getCollisionShape(level,pos).isEmpty()||block instanceof CocoaBlock)for(var property:state.getProperties())if(property instanceof IntegerProperty age&&property.getName().equals("age"))return state.setValue(age,0);
        return state.getFluidState().createLegacyBlock();
    }
    @Override public boolean disableBlockEntityRendering(){return true;}
}
