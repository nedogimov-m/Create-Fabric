package com.simibubi.create.content.equipment.bell;

import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class PeculiarBellBlock extends AbstractBellBlock {
    public PeculiarBellBlock(Properties properties) { super(properties); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new PeculiarBellBlockEntity(pos,state); }
    @Override public void playSound(Level level,BlockPos pos) { if(!level.isClientSide())level.playSound(null,pos,AllSoundEvents.PECULIAR_BELL_USE,SoundSource.BLOCKS,2,.94f); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { var state=super.getStateForPlacement(context);return state==null?null:convert(context.getLevel(),context.getClickedPos(),state,LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos().below())); }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos otherPos,BlockState other,RandomSource random) { var next=super.updateShape(state,level,ticks,pos,side,otherPos,other,random);return side==Direction.DOWN?convert(level,pos,next,other):next; }
    private BlockState convert(LevelReader reader,BlockPos pos,BlockState state,BlockState below) {
        if(!state.is(this)||!below.is(Blocks.SOUL_FIRE)&&!below.is(Blocks.SOUL_CAMPFIRE))return state;
        if(reader instanceof Level level){if(level.isClientSide()){var random=level.getRandom();for(int i=random.nextInt(10)+15;i>0;i--){var velocity=Vec3.directionFromRotation(random.nextFloat()*120-90,random.nextFloat()*360).scale(random.nextDouble()*.1+.1);var center=Vec3.atCenterOf(pos);level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,center.x,center.y,center.z,velocity.x,velocity.y,velocity.z);}}else level.playSound(null,pos,AllSoundEvents.HAUNTED_BELL_CONVERT,SoundSource.BLOCKS,1,1);}
        return AllBlocks.HAUNTED_BELL.defaultBlockState().setValue(FACING,state.getValue(FACING)).setValue(ATTACHMENT,state.getValue(ATTACHMENT)).setValue(POWERED,state.getValue(POWERED));
    }
}
