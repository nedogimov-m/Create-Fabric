package com.simibubi.create.content.logistics.chute;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

public final class SmartChuteBlock extends AbstractChuteBlock {
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED;
    public SmartChuteBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(POWERED,true));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(POWERED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(POWERED,context.getLevel().hasNeighborSignal(context.getClickedPos()));}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){super.tick(state,level,pos,random);boolean power=level.hasNeighborSignal(pos);if(power!=state.getValue(POWERED))level.setBlock(pos,state.setValue(POWERED,power),Block.UPDATE_CLIENTS);}
    @Override public BlockState updateChuteState(BlockState state,BlockState above,BlockGetter level,BlockPos pos){return state;}
    public static boolean activeSide(Direction side){return side.getAxis().isHorizontal();}
    public static net.minecraft.world.phys.Vec3 filterPosition(Direction side){return new net.minecraft.world.phys.Vec3(.5+side.getStepX()*7.5/16,11f/16,.5+side.getStepZ()*7.5/16);}
}
