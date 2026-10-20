package com.simibubi.create.content.redstone.displayLink;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.source.RedstonePowerDisplaySource;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.function.*;
import net.minecraft.core.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Actual directional reader/link control, source-only redstone exclusion and native server menu interaction. */
public final class DisplayLinkBlock extends DirectionalBlock implements EntityBlock {
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED;
    public DisplayLinkBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(FACING,Direction.UP).setValue(POWERED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,POWERED);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(placer instanceof net.minecraft.server.level.ServerPlayer player&&LoadedLevelAccess.blockEntity(level,pos) instanceof DisplayLinkBlockEntity link)link.placedBy(player);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new DisplayLinkBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntityTypes.DISPLAY_LINK?(world,pos,block,entity)->DisplayLinkBlockEntity.tick(world,pos,block,(DisplayLinkBlockEntity)entity):null;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var state=defaultBlockState().setValue(FACING,context.getClickedFace());return state.setValue(POWERED,shouldBePowered(state,context.getLevel(),context.getClickedPos()));}
    public static void notifyGatherers(LevelAccessor level,BlockPos pos){forEachAttachedGatherer(level,pos,DisplayLinkBlockEntity::tickSource);}
    public static <T extends DisplaySource>void sendToGatherers(LevelAccessor level,BlockPos pos,BiConsumer<DisplayLinkBlockEntity,T> callback,Class<T> type){forEachAttachedGatherer(level,pos,link->{if(type.isInstance(link.activeSource))callback.accept(link,type.cast(link.activeSource));});}
    private static void forEachAttachedGatherer(LevelAccessor level,BlockPos pos,Consumer<DisplayLinkBlockEntity> callback){for(var side:Direction.values()){var next=pos.relative(side);if(!(LoadedLevelAccess.blockState(level,next).getBlock()instanceof DisplayLinkBlock)||!(LoadedLevelAccess.blockEntity(level,next)instanceof DisplayLinkBlockEntity link)||link.activeSource==null||link.getDirection()!=side.getOpposite())continue;callback.accept(link);}}
    public static boolean shouldBePowered(BlockState state,Level level,BlockPos pos){var view=new SignalView(level);for(var side:Direction.values())if(side.getOpposite()!=state.getValue(FACING)&&view.getSignal(pos.relative(side),side)>0)return true;return false;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){
        if(level.isClientSide())return;boolean next=shouldBePowered(state,level,pos),before=state.getValue(POWERED);if(next!=before){level.setBlock(pos,state.setValue(POWERED,next),Block.UPDATE_CLIENTS);if(!next&&LoadedLevelAccess.blockEntity(level,pos)instanceof DisplayLinkBlockEntity link)link.onNoLongerPowered();}else if(LoadedLevelAccess.blockEntity(level,pos)instanceof DisplayLinkBlockEntity link&&link.activeSource instanceof RedstonePowerDisplaySource)link.tickSource();
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(player.isShiftKeyDown()||player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,pos))return InteractionResult.PASS;if(LoadedLevelAccess.blockEntity(level,pos)instanceof DisplayLinkBlockEntity link){if(link.getTargetOffset().equals(BlockPos.ZERO)){if(!level.isClientSide())player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("create.display_link.invalid"));return InteractionResult.SUCCESS;}if(!level.isClientSide())DisplayLinkMenu.open(player,link);return InteractionResult.SUCCESS;}return InteractionResult.PASS;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){var shape=Shapes.or(box(1,0,1,15,6,15),box(3,5,3,13,9,13));var side=state.getValue(FACING);var result=new VoxelShape[]{Shapes.empty()};shape.forAllBoxes((x,y,z,a,b,c)->{double[][] corners={{x,y,z},{x,y,c},{x,b,z},{x,b,c},{a,y,z},{a,y,c},{a,b,z},{a,b,c}};double minX=1,minY=1,minZ=1,maxX=0,maxY=0,maxZ=0;for(var point:corners){var p=orient(point[0],point[1],point[2],side);minX=Math.min(minX,p[0]);minY=Math.min(minY,p[1]);minZ=Math.min(minZ,p[2]);maxX=Math.max(maxX,p[0]);maxY=Math.max(maxY,p[1]);maxZ=Math.max(maxZ,p[2]);}result[0]=Shapes.or(result[0],Shapes.box(minX,minY,minZ,maxX,maxY,maxZ));});return result[0];}
    private static double[] orient(double x,double y,double z,Direction side){return switch(side){case UP->new double[]{x,y,z};case DOWN->new double[]{x,1-y,1-z};case NORTH->new double[]{x,z,1-y};case SOUTH->new double[]{x,1-z,y};case EAST->new double[]{y,1-x,z};case WEST->new double[]{1-y,x,z};};}
    public record SignalView(Level level)implements SignalGetter {
        @Override public BlockState getBlockState(BlockPos pos){return LoadedLevelAccess.blockState(level,pos);}
        @Override public BlockEntity getBlockEntity(BlockPos pos){return LoadedLevelAccess.blockEntity(level,pos);}
        @Override public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
        @Override public int getHeight(){return level.getHeight();}
        @Override public int getMinY(){return level.getMinY();}
    }
}
