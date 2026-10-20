package com.simibubi.create.content.fluids.spout;

import com.simibubi.create.AllBlockEntityTypes;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;

public final class SpoutBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE=Shapes.or(box(1,2,1,15,14,15),box(2,0,2,14,16,14));
    public SpoutBlock(Properties properties){super(properties);}
    @Override public void setPlacedBy(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof SpoutBlockEntity device)device.sourceAdvancements.place(placer);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new SpoutBlockEntity(pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.SPOUT,SpoutBlockEntity::tick);}
    @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos,Direction side){
        if(!(level.getBlockEntity(pos) instanceof SpoutBlockEntity spout)||spout.tank.isEmpty())return 0;
        return 1+(int)(14*spout.tank.getAmount()/spout.tank.getCapacity());
    }
}
