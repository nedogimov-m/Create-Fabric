package com.simibubi.create.content.kinetics.steamEngine;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

public final class PoweredShaftBlock extends ShaftBlock {
    public PoweredShaftBlock(Properties properties){super(properties);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PoweredShaftBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.POWERED_SHAFT,PoweredShaftBlockEntity::tick);}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(!stillValid(state,level,pos))level.setBlockAndUpdate(pos,AllBlocks.SHAFT.defaultBlockState().setValue(AXIS,state.getValue(AXIS)).setValue(WATERLOGGED,state.getValue(WATERLOGGED)));}
    @Override protected ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state,boolean data){return new ItemStack(AllBlocks.SHAFT);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return switch(state.getValue(AXIS)){case X->box(0,4,4,16,12,12);case Y->box(4,0,4,12,16,12);case Z->box(4,4,0,12,12,16);};}
    public static boolean stillValid(BlockState state,LevelReader level,BlockPos pos){
        for(var side:Direction.values()){
            if(side.getAxis()==state.getValue(AXIS))continue;var enginePos=pos.relative(side,2);
            if(level instanceof Level world&&!world.isLoaded(enginePos))continue;
            var engine=LoadedLevelAccess.blockState(level,enginePos);
            if(engine.getBlock() instanceof SteamEngineBlock&&SteamEngineBlock.getShaftPos(engine,enginePos).equals(pos)&&SteamEngineBlock.isShaftValid(engine,state))return true;
        }return false;
    }
    public static BlockState getEquivalent(BlockState state){return AllBlocks.POWERED_SHAFT.defaultBlockState().setValue(AXIS,state.getValue(AXIS)).setValue(WATERLOGGED,state.getValue(WATERLOGGED));}
}
