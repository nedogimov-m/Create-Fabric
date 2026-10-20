package com.simibubi.create.content.fluids.pipes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
public final class GlassFluidPipeBlock extends AxisPipeBlock {
    public static final BooleanProperty ALT=BooleanProperty.create("alt");
    public GlassFluidPipeBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(ALT,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(ALT);}
}
