package com.simibubi.create.content.kinetics.mechanicalArm;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class ArmBlock extends KineticBlock implements ICogWheel {
    public static final BooleanProperty CEILING=BooleanProperty.create("ceiling");
    private static final VoxelShape FLOOR=Shapes.or(box(2,0,2,14,10,14),box(3,0,3,13,14,13),box(0,0,0,16,6,16));
    private static final VoxelShape TOP=Shapes.or(box(2,6,2,14,16,14),box(3,2,3,13,16,13),box(0,10,0,16,16,16));
    public ArmBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(CEILING,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(CEILING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(CEILING,context.getClickedFace()==Direction.DOWN);}
    @Override public boolean hasShaftTowards(LevelReader level,BlockPos pos,BlockState state,Direction face){return false;}
    @Override public Direction.Axis getRotationAxis(BlockState state){return Direction.Axis.Y;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ArmBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.MECHANICAL_ARM,ArmBlockEntity::tick);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(CEILING)?TOP:FLOOR;}
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){super.onPlace(state,level,pos,old,moved);if(LoadedLevelAccess.blockEntity(level,pos) instanceof ArmBlockEntity arm)arm.redstoneUpdate();}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){super.neighborChanged(state,level,pos,neighbor,orientation,moved);if(LoadedLevelAccess.blockEntity(level,pos) instanceof ArmBlockEntity arm)arm.redstoneUpdate();}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(placer instanceof ServerPlayer player&&LoadedLevelAccess.blockEntity(level,pos) instanceof ArmBlockEntity arm)arm.beginPlacement(player);}
    public static boolean settingsHit(BlockState state,BlockPos pos,BlockHitResult hit){
        if(hit.getDirection().getAxis().isVertical())return false;
        var relative=hit.getLocation().subtract(pos.getX(),pos.getY(),pos.getZ());
        double y=state.getValue(CEILING)?13/16d:3/16d;
        double horizontal=hit.getDirection().getAxis()==Direction.Axis.X?relative.z:relative.x;
        return Math.abs(relative.y-y)<3/16d&&Math.abs(horizontal-.5)<3/16d;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,pos))return InteractionResult.PASS;
        if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof ArmBlockEntity arm))return InteractionResult.PASS;
        if(stack.is(AllItems.GOGGLES)&&!arm.hasGoggles()){if(!level.isClientSide())arm.setGoggles();return InteractionResult.SUCCESS;}
        if(!player.isShiftKeyDown()&&settingsHit(state,pos,hit)){
            if(player instanceof ServerPlayer server)ArmNetworking.openSettings(server,arm);return InteractionResult.SUCCESS;
        }
        if(arm.getHeldItem().isEmpty())return InteractionResult.PASS;
        if(!level.isClientSide())player.getInventory().placeItemBackInInventory(arm.takeHeldItem(),Prediction.SERVER_ONLY);
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return useItemOn(ItemStack.EMPTY,state,level,pos,player,InteractionHand.MAIN_HAND,hit);}
}
