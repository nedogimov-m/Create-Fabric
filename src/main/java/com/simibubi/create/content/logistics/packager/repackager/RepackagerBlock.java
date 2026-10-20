package com.simibubi.create.content.logistics.packager.repackager;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.logistics.packager.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;

public final class RepackagerBlock extends PackagerBlock {
    public RepackagerBlock(Properties properties){super(properties);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,POWERED);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new RepackagerBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.REPACKAGER,PackagerBlockEntity::tick);}
}
