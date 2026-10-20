package com.simibubi.create.content.decoration;

import com.simibubi.create.content.decoration.copycat.CopycatConfig;
import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Source hanging metal ladder and bounded native BlockItem placement assist. */
public final class MetalLadderBlock extends LadderBlock {
    public MetalLadderBlock(Properties properties) { super(properties); }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
        var above=LoadedLevelAccess.blockState(level,pos.above());
        return super.canSurvive(state,level,pos)||above.is(this)&&above.getValue(FACING)==state.getValue(FACING);
    }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,
            Direction face,BlockPos neighborPos,BlockState neighbor,RandomSource random) {
        return !state.canSurvive(level,pos)?Blocks.AIR.defaultBlockState():super.updateShape(state,level,ticks,pos,face,neighborPos,neighbor,random);
    }
    @Override protected VoxelShape getOcclusionShape(BlockState state) { return box(5,0,5,11,16,11); }
    @Override protected boolean skipRendering(BlockState state,BlockState adjacent,Direction face) {
        return face.getAxis().isHorizontal()?adjacent.isAir()||!adjacent.isSolid():face==Direction.UP&&adjacent.getBlock() instanceof LadderBlock;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(player.isShiftKeyDown()||!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,pos)
                ||!(stack.getItem() instanceof BlockItem item)||!(item.getBlock() instanceof MetalLadderBlock))return InteractionResult.TRY_WITH_EMPTY_HAND;
        Direction step=player.getXRot()<0?Direction.UP:Direction.DOWN;
        int range=CopycatConfig.placementAssistRange();var reach=player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if(reach!=null&&reach.hasModifier(ExtendoGripItem.singleRangeAttributeModifier.id()))range+=4;
        int attached=0;BlockPos target=pos.relative(step);
        while(attached<range) {
            if(!LoadedLevelAccess.isLoaded(level,target))return InteractionResult.PASS;
            if(!(LoadedLevelAccess.blockState(level,target).getBlock() instanceof LadderBlock))break;
            attached++;target=target.relative(step);
        }
        if(attached>=range||!level.isInValidBounds(target)||!level.mayInteract(player,target)
                ||!LoadedLevelAccess.blockState(level,target).canBeReplaced()||!state.canSurvive(level,target))return InteractionResult.PASS;
        final BlockPos at=target;Direction facing=state.getValue(FACING);
        var placement=new BlockPlaceContext(new UseOnContext(player,hand,hit)) {
            @Override public BlockPos getClickedPos(){return at;}
            @Override public Direction getClickedFace(){return facing;}
            @Override public Direction[] getNearestLookingDirections(){return new Direction[]{facing.getOpposite()};}
            @Override public boolean replacingClickedOnBlock(){return true;}
        };
        return item.place(placement);
    }
}
