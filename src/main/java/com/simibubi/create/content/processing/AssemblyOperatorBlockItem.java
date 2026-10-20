package com.simibubi.create.content.processing;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Source press/mixer/spout/deployer auto-height; ordinary native placement validates and pays once. */
public class AssemblyOperatorBlockItem extends BlockItem {
    public AssemblyOperatorBlockItem(Block block,Properties properties){super(block,properties);}
    @Override public InteractionResult place(BlockPlaceContext context){
        var placedOn=context.getClickedPos().relative(context.getClickedFace().getOpposite());var level=context.getLevel();
        if(context.getClickedFace()==Direction.UP&&operatesOn(level,placedOn,LoadedLevelAccess.blockState(level,placedOn))){
            var target=placedOn.above(2);
            if(!LoadedLevelAccess.isLoaded(level,target)||!LoadedLevelAccess.blockState(level,target).canBeReplaced())return InteractionResult.FAIL;
            context=adjustContext(context,placedOn);
        }
        return super.place(context);
    }
    protected BlockPlaceContext adjustContext(BlockPlaceContext context,BlockPos placedOn){
        var target=placedOn.above(2);
        return new AssemblyOperatorUseContext(context.getLevel(),context.getPlayer(),context.getHand(),context.getItemInHand(),new BlockHitResult(Vec3.atCenterOf(target).add(0,.5,0),Direction.UP,target,false));
    }
    protected boolean operatesOn(LevelReader level,BlockPos pos,BlockState state){
        if(state.is(AllBlocks.BELT))return state.getValue(BeltBlock.SLOPE)==BeltSlope.HORIZONTAL;
        return LoadedLevelAccess.blockEntity(level,pos) instanceof BasinBlockEntity||state.is(AllBlocks.DEPOT)||state.is(AllBlocks.WEIGHTED_EJECTOR);
    }
}
