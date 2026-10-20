package com.simibubi.create.content.decoration.bracket;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

/** Source bracket state is carried by its actual host, never a separate world block. */
public class BracketBlock extends WrenchableDirectionalBlock {
    public static final BooleanProperty AXIS_ALONG_FIRST_COORDINATE=BooleanProperty.create("axis_along_first");
    public static final EnumProperty<BracketType> TYPE=EnumProperty.create("type",BracketType.class);
    public enum BracketType implements StringRepresentable {PIPE,COG,SHAFT;
        @Override public String getSerializedName(){return name().toLowerCase(java.util.Locale.ROOT);}}
    public BracketBlock(Properties properties){super(properties);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder.add(AXIS_ALONG_FIRST_COORDINATE,TYPE));}
    public static boolean isSimpleShaft(BlockState state){return state.is(AllBlocks.SHAFT)||state.is(AllBlocks.COGWHEEL)||state.is(AllBlocks.LARGE_COGWHEEL);}
    public Optional<BlockState> getSuitableBracket(BlockState target,Direction face){
        boolean shaft=isSimpleShaft(target);
        Direction.Axis targetAxis=shaft?target.getValue(ShaftBlock.AXIS):FluidPropagator.getStraightPipeAxis(target);
        var faceAxis=face.getAxis();if(targetAxis==null||targetAxis==faceAxis)return Optional.empty();
        boolean alongFirst=faceAxis!=Direction.Axis.Z?targetAxis==Direction.Axis.Z:targetAxis==Direction.Axis.Y;
        return Optional.of(defaultBlockState().setValue(FACING,face).setValue(AXIS_ALONG_FIRST_COORDINATE,!alongFirst)
            .setValue(TYPE,shaft?(target.is(AllBlocks.SHAFT)?BracketType.SHAFT:BracketType.COG):BracketType.PIPE));
    }
    @Override public BlockState rotate(BlockState state,Rotation rotation){if(rotation.ordinal()%2==1)state=state.cycle(AXIS_ALONG_FIRST_COORDINATE);return super.rotate(state,rotation);}
}
