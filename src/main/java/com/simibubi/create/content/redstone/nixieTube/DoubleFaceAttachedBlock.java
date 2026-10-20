package com.simibubi.create.content.redstone.nixieTube;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;

public class DoubleFaceAttachedBlock extends HorizontalDirectionalBlock {
    public enum DoubleAttachFace implements StringRepresentable {
        FLOOR("floor"),WALL("wall"),WALL_REVERSED("wall_reversed"),CEILING("ceiling");
        private final String name;DoubleAttachFace(String name){this.name=name;}
        @Override public String getSerializedName(){return name;}
        public int xRot(){return this==FLOOR?0:this==CEILING?180:90;}
    }
    public static final EnumProperty<DoubleAttachFace> FACE=EnumProperty.create("double_face",DoubleAttachFace.class);
    protected DoubleFaceAttachedBlock(Properties properties){super(properties);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        for(var direction:context.getNearestLookingDirections()){
            BlockState state;if(direction.getAxis()==Direction.Axis.Y)state=defaultBlockState().setValue(FACE,direction==Direction.UP?DoubleAttachFace.CEILING:DoubleAttachFace.FLOOR).setValue(FACING,context.getHorizontalDirection());
            else{var normal=Vec3.ZERO.relative(direction.getClockWise(),1);var face=context.getPlayer()!=null&&context.getPlayer().getLookAngle().dot(normal)<0?DoubleAttachFace.WALL_REVERSED:DoubleAttachFace.WALL;state=defaultBlockState().setValue(FACE,face).setValue(FACING,direction.getOpposite());}
            if(state.canSurvive(context.getLevel(),context.getClickedPos()))return state;
        }return null;
    }
    protected static Direction getConnectedDirection(BlockState state){return switch(state.getValue(FACE)){case FLOOR->Direction.UP;case CEILING->Direction.DOWN;default->state.getValue(FACING);};}
}
