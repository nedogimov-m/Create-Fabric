package com.simibubi.create.content.processing.burner;

import com.simibubi.create.*;
import java.util.Locale;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.*;
import net.minecraft.tags.*;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** The empty cage can carry an ordinary fire without capturing a blaze. */
public final class LitBlazeBurnerBlock extends Block {
    public enum FlameType implements StringRepresentable {
        REGULAR, SOUL;
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    public static final EnumProperty<FlameType> FLAME_TYPE=EnumProperty.create("flame_type",FlameType.class);
    /** Fabric data-pack extension point for tools that can extinguish the cage. */
    public static final TagKey<Item> EXTINGUISH_FLAME=TagKey.create(Registries.ITEM,Create.asResource("extinguish_flame"));
    public LitBlazeBurnerBlock(Properties properties) {
        super(properties.lightLevel(LitBlazeBurnerBlock::getLight)); registerDefaultState(defaultBlockState().setValue(FLAME_TYPE,FlameType.REGULAR));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FLAME_TYPE); }
    public static int getLight(BlockState state) { return state.getValue(FLAME_TYPE)==FlameType.SOUL?9:12; }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(!player.mayBuild()||!level.mayInteract(player,pos))return InteractionResult.PASS;
        if(stack.is(ItemTags.SHOVELS)||stack.is(EXTINGUISH_FLAME)) {
            level.playSound(player,pos,SoundEvents.GENERIC_EXTINGUISH_FIRE,SoundSource.BLOCKS,0.5f,2);
            if(!level.isClientSide()) { stack.hurtAndBreak(1,player,hand); level.setBlockAndUpdate(pos,AllBlocks.BLAZE_BURNER.defaultBlockState()); }
            return InteractionResult.SUCCESS;
        }
        if(state.getValue(FLAME_TYPE)==FlameType.REGULAR&&stack.is(ItemTags.SOUL_FIRE_BASE_BLOCKS)) {
            level.playSound(player,pos,SoundEvents.SOUL_SAND_PLACE,SoundSource.BLOCKS,1,level.getRandom().nextFloat()*0.4f+0.8f);
            if(!level.isClientSide())level.setBlockAndUpdate(pos,state.setValue(FLAME_TYPE,FlameType.SOUL));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return box(1,0,1,15,14,15); }
    @Override protected ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state,boolean includeData) { return new ItemStack(AllItems.EMPTY_BLAZE_BURNER); }
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type) { return false; }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos,Direction side) { return state.getValue(FLAME_TYPE)==FlameType.REGULAR?1:2; }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random) {
        level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE,true,pos.getX()+0.5+random.nextDouble()/3*(random.nextBoolean()?1:-1),pos.getY()+random.nextDouble()+random.nextDouble(),pos.getZ()+0.5+random.nextDouble()/3*(random.nextBoolean()?1:-1),0,0.07,0);
        if(random.nextInt(10)==0)level.playLocalSound(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,SoundEvents.CAMPFIRE_CRACKLE,SoundSource.BLOCKS,0.25f+random.nextFloat()*0.25f,random.nextFloat()*0.7f+0.6f,false);
        if(state.getValue(FLAME_TYPE)==FlameType.SOUL) {
            if(random.nextInt(8)==0)level.addParticle(ParticleTypes.SOUL,pos.getX()+0.5+random.nextDouble()/4*(random.nextBoolean()?1:-1),pos.getY()+0.3+random.nextDouble()/2,pos.getZ()+0.5+random.nextDouble()/4*(random.nextBoolean()?1:-1),0,random.nextDouble()*0.04+0.04,0);
        } else if(random.nextInt(5)==0)level.addParticle(ParticleTypes.LAVA,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,random.nextFloat()/2,0.00005,random.nextFloat()/2);
    }
}
