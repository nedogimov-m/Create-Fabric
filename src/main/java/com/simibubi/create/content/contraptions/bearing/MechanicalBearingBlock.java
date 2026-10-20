package com.simibubi.create.content.contraptions.bearing;

import com.simibubi.create.AllBlockEntityTypes;
import net.minecraft.core.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class MechanicalBearingBlock extends BearingBlock {
    public MechanicalBearingBlock(Properties properties){super(properties);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new MechanicalBearingBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntityTypes.MECHANICAL_BEARING?(world,pos,block,entity)->MechanicalBearingBlockEntity.tick(world,pos,block,(MechanicalBearingBlockEntity)entity):null;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!player.mayBuild()||player.isShiftKeyDown())return InteractionResult.PASS;
        if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof MechanicalBearingBlockEntity bearing){if(bearing.isRunning())bearing.disassemble();else bearing.requestAssembly();}return InteractionResult.SUCCESS;
    }
}
