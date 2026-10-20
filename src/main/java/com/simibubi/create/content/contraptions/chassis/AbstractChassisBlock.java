package com.simibubi.create.content.contraptions.chassis;

import com.simibubi.create.*;
import com.simibubi.create.api.contraption.transformable.TransformableBlock;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Chassis glue is a face setting, separate from an explicit Super Glue entity. */
public abstract class AbstractChassisBlock extends RotatedPillarBlock implements EntityBlock,TransformableBlock {
    public static final TagKey<Item> SLIMEBALLS=TagKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("c","slimeballs"));
    protected AbstractChassisBlock(Properties properties){super(properties.strength(2).sound(SoundType.WOOD));}
    public abstract BooleanProperty getGlueableSide(BlockState state,Direction face);
    public boolean glueAllowedOnSide(BlockGetter level,BlockPos pos,BlockState state,Direction side){return true;}
    /** Used by both native glue input paths to defer exactly the same permitted face interaction. */
    public boolean handlesGlueUse(BlockState state,Direction side){return getGlueableSide(state,side)!=null;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,pos))return InteractionResult.TRY_WITH_EMPTY_HAND;
        var property=getGlueableSide(state,hit.getDirection());if(property==null)return InteractionResult.TRY_WITH_EMPTY_HAND;
        boolean glue=stack.is(SLIMEBALLS)||stack.is(Items.SLIME_BALL)||stack.is(AllItems.SUPER_GLUE);
        if(glue&&state.getValue(property)){
            var next=state;for(var side:Direction.values()){var flag=getGlueableSide(state,side);if(flag!=null&&!next.getValue(flag)&&glueAllowedOnSide(level,pos,state,side)){next=next.setValue(flag,true);if(!level.isClientSide())level.playSound(null,pos,AllSoundEvents.SLIME_ADDED,SoundSource.BLOCKS,.5f,1);}}
            if(level.isClientSide()){if(next!=state){var point=hit.getLocation();level.addParticle(ParticleTypes.ITEM_SLIME,point.x,point.y,point.z,0,0,0);}}else if(next!=state)level.setBlock(pos,next,Block.UPDATE_ALL);
            return InteractionResult.SUCCESS;
        }
        if((!stack.isEmpty()||!player.isShiftKeyDown())&&!glue||state.getValue(property)==glue||!glueAllowedOnSide(level,pos,state,hit.getDirection()))return InteractionResult.TRY_WITH_EMPTY_HAND;
        if(level.isClientSide()){var point=hit.getLocation();level.addParticle(ParticleTypes.ITEM_SLIME,point.x,point.y,point.z,0,0,0);}else{level.playSound(null,pos,AllSoundEvents.SLIME_ADDED,SoundSource.BLOCKS,.5f,1);level.setBlock(pos,state.setValue(property,glue),Block.UPDATE_ALL);}
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return useItemOn(ItemStack.EMPTY,state,level,pos,player,InteractionHand.MAIN_HAND,hit)==InteractionResult.TRY_WITH_EMPTY_HAND?InteractionResult.PASS:InteractionResult.SUCCESS;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return transformFaces(state,super.rotate(state,rotation),rotation::rotate);}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return transformFaces(state,state,mirror::mirror);}
    private BlockState transformFaces(BlockState original,BlockState transformed,java.util.function.UnaryOperator<Direction> transform){
        var next=transformed;for(var face:Direction.values()){var flag=getGlueableSide(next,face);if(flag!=null)next=next.setValue(flag,false);}
        for(var face:Direction.values()){var flag=getGlueableSide(original,face);if(flag==null||!original.getValue(flag))continue;var rotated=getGlueableSide(next,transform.apply(face));if(rotated!=null)next=next.setValue(rotated,true);}
        return next;
    }
    @Override public BlockState transform(BlockState state,StructureTransform transform){
        if(transform.mirror!=null)state=mirror(state,transform.mirror);
        if(transform.rotationAxis==Direction.Axis.Y)return rotate(state,transform.rotation);
        return transform.rotation==Rotation.NONE?state:transformFaces(state,state.setValue(AXIS,transform.rotateAxis(state.getValue(AXIS))),transform::rotateFacing);
    }
    public InteractionResult onWrenched(BlockState state,UseOnContext context){
        if(context.getPlayer()==null)return InteractionResult.PASS;
        if(context.getPlayer().isShiftKeyDown())return InteractionResult.PASS; // Root's standard removal route handles loot and break hooks.
        var level=context.getLevel();if(!level.isClientSide()){
            var next=transform(state,new StructureTransform(BlockPos.ZERO,context.getClickedFace().getAxis()==Direction.Axis.X?90:0,context.getClickedFace().getAxis()==Direction.Axis.Y?90:0,context.getClickedFace().getAxis()==Direction.Axis.Z?90:0));
            level.setBlock(context.getClickedPos(),next,Block.UPDATE_ALL);level.playSound(null,context.getClickedPos(),AllSoundEvents.WRENCH_ROTATE,SoundSource.BLOCKS,.25f,1.25f);
        }return InteractionResult.SUCCESS;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ChassisBlockEntity(pos,state);}
}
