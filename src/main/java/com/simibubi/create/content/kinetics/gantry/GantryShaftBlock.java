package com.simibubi.create.content.kinetics.gantry;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public class GantryShaftBlock extends DirectionalKineticBlock {
    public enum Part implements StringRepresentable{START,MIDDLE,END,SINGLE;@Override public String getSerializedName(){return name().toLowerCase(Locale.ROOT);}}
    public static final EnumProperty<Part> PART=EnumProperty.create("part",Part.class);
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED;
    public GantryShaftBlock(Properties p){super(p.strength(1.5f,6).sound(SoundType.STONE).forceSolidOn());registerDefaultState(defaultBlockState().setValue(FACING,Direction.UP).setValue(PART,Part.SINGLE).setValue(POWERED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder.add(PART,POWERED));}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return switch(state.getValue(FACING).getAxis()){case X->box(0,4,4,16,12,12);case Y->box(4,0,4,12,16,12);case Z->box(4,4,0,12,12,16);};}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
    @Override public Direction.Axis getRotationAxis(BlockState state){return state.getValue(FACING).getAxis();}
    @Override public boolean hasShaftTowards(LevelReader level,BlockPos pos,BlockState state,Direction side){return side.getAxis()==getRotationAxis(state);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new GantryShaftBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.GANTRY_SHAFT,com.simibubi.create.content.kinetics.base.KineticBlockEntity::tick);}
    private static boolean matches(BlockState state,Direction facing){return state.getBlock() instanceof GantryShaftBlock&&state.getValue(FACING)==facing;}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos otherPos,BlockState other,RandomSource random){
        var facing=state.getValue(FACING);if(side.getAxis()!=facing.getAxis())return state;boolean connect=matches(other,facing);var part=state.getValue(PART);
        if(side.getAxisDirection()==facing.getAxisDirection())part=switch(part){case END->connect?Part.MIDDLE:part;case SINGLE->connect?Part.START:part;case MIDDLE->connect?part:Part.END;case START->connect?part:Part.SINGLE;};
        else part=switch(part){case START->connect?Part.MIDDLE:part;case SINGLE->connect?Part.END:part;case MIDDLE->connect?part:Part.START;case END->connect?part:Part.SINGLE;};
        return state.setValue(PART,part);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var state=super.getStateForPlacement(context);var level=context.getLevel();var pos=context.getClickedPos();var back=LoadedLevelAccess.blockState(level,pos.relative(state.getValue(FACING).getOpposite()));var clicked=back.getBlock() instanceof GantryShaftBlock?back:LoadedLevelAccess.blockState(level,pos.relative(context.getClickedFace().getOpposite()));
        if(clicked.getBlock() instanceof GantryShaftBlock&&clicked.getValue(FACING).getAxis()==state.getValue(FACING).getAxis())state=state.setValue(FACING,context.getPlayer()!=null&&context.getPlayer().isShiftKeyDown()?clicked.getValue(FACING).getOpposite():clicked.getValue(FACING));
        boolean powered=LoadedLevelAccess.getBestNeighborSignal(level,pos)>0;for(var side:new Direction[]{state.getValue(FACING),state.getValue(FACING).getOpposite()}){var adjacent=LoadedLevelAccess.blockState(level,pos.relative(side));powered|=matches(adjacent,state.getValue(FACING))&&adjacent.getValue(POWERED);}
        return state.setValue(POWERED,powered);
    }
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){
        super.onPlace(state,level,pos,old,moved);if(level.isClientSide())return;
        if(old.getBlock()==this&&((old.getValue(PART)!=Part.MIDDLE&&state.getValue(PART)==Part.MIDDLE)||(old.getValue(PART)==Part.SINGLE&&state.getValue(PART)!=Part.SINGLE))&&LoadedLevelAccess.blockEntity(level,pos) instanceof GantryShaftBlockEntity shaft)shaft.checkAttachedCarriageBlocks();
        if(old.getBlock()!=this&&level instanceof ServerLevel server)server.scheduleTick(pos,this,1);
    }
    /** Collect the visible, identically oriented chain before its first native state mutation. */
    public void refreshPower(ServerLevel level,BlockPos pos){
        if(!LoadedLevelAccess.isLoaded(level,pos)||!(LoadedLevelAccess.blockState(level,pos).getBlock() instanceof GantryShaftBlock))return;
        var facing=LoadedLevelAccess.blockState(level,pos).getValue(FACING);var positions=new ArrayList<BlockPos>();positions.add(pos.immutable());
        for(var direction:new Direction[]{facing,facing.getOpposite()}){
            var previous=LoadedLevelAccess.blockState(level,pos);
            for(var next=pos.relative(direction);;next=next.relative(direction)){
                if(!LoadedLevelAccess.isLoaded(level,next)){
                    var part=previous.getValue(PART);boolean continues=direction==facing?part==Part.START||part==Part.MIDDLE:part==Part.END||part==Part.MIDDLE;
                    if(continues){level.scheduleTick(pos,this,3);return;}
                    break;
                }
                var adjacent=LoadedLevelAccess.blockState(level,next);if(!matches(adjacent,facing))break;positions.add(next.immutable());previous=adjacent;
            }
        }
        boolean powered=false;for(var next:positions){if(!LoadedEntityAccess.isReady(level,next)){level.scheduleTick(pos,this,3);return;}powered|=LoadedLevelAccess.getBestNeighborSignal(level,next)>0;}
        boolean changed=false;for(var next:positions){var state=LoadedLevelAccess.blockState(level,next);if(state.getValue(POWERED)!=powered){changed=true;level.setBlock(next,state.setValue(POWERED,powered),Block.UPDATE_CLIENTS);}}
        KineticNetworks.markDirty(level);
        // The native solver preserves equal RPM across a mode-only change; source detach/reattach
        // used to trigger this check. Explicitly queue after the whole rack is unpowered.
        if(changed&&!powered)for(var next:positions)if(LoadedLevelAccess.blockEntity(level,next) instanceof GantryShaftBlockEntity shaft)shaft.checkAttachedCarriageBlocks();
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){super.neighborChanged(state,level,pos,neighbor,orientation,moved);if(level instanceof ServerLevel server)refreshPower(server,pos);}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){refreshPower(level,pos);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player.isShiftKeyDown()||player.isSpectator()||!player.mayBuild()||!(stack.getItem() instanceof BlockItem item)||item.getBlock()!=this)return InteractionResult.TRY_WITH_EMPTY_HAND;
        var axis=state.getValue(FACING).getAxis();var preferred=hit.getDirection();if(preferred.getAxis()!=axis){var offset=hit.getLocation().subtract(Vec3.atCenterOf(pos));preferred=Direction.get(axis.choose(offset.x,offset.y,offset.z)>=0?Direction.AxisDirection.POSITIVE:Direction.AxisDirection.NEGATIVE,axis);}
        for(var direction:new Direction[]{preferred,preferred.getOpposite()})for(int distance=1;distance<=16;distance++){
            var target=pos.relative(direction,distance);if(!LoadedLevelAccess.isLoaded(level,target))break;var other=LoadedLevelAccess.blockState(level,target);if(other.getBlock()==this&&other.getValue(FACING).getAxis()==axis)continue;if(!other.canBeReplaced()||!level.mayInteract(player,target)||!player.mayUseItemAt(target,direction,stack))break;
            var support=target.relative(direction.getOpposite());var result=item.place(new BlockPlaceContext(new UseOnContext(player,hand,new BlockHitResult(Vec3.atCenterOf(support).add(direction.getUnitVec3().scale(.5)),direction,support,false))));
            if(result.consumesAction()&&!level.isClientSide()){var placed=LoadedLevelAccess.blockState(level,target);if(placed.getBlock()==this)level.setBlockAndUpdate(target,placed.setValue(FACING,state.getValue(FACING)).setValue(POWERED,state.getValue(POWERED)));}return result;
        }return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
}
