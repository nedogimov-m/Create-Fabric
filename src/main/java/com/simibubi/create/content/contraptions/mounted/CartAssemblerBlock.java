package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.*;
import com.simibubi.create.content.redstone.rail.ControllerRailBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.util.Prediction;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Pinned five-rail action table, collision, replaceable underlying rail and real assembler block entity. */
public final class CartAssemblerBlock extends BaseRailBlock implements EntityBlock {
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED,BACKWARDS=BooleanProperty.create("backwards");
    public static final EnumProperty<RailShape> RAIL_SHAPE=EnumProperty.create("shape",RailShape.class,RailShape.EAST_WEST,RailShape.NORTH_SOUTH);
    public static final EnumProperty<CartAssembleRailType> RAIL_TYPE=EnumProperty.create("rail_type",CartAssembleRailType.class);
    private static final VoxelShape X_SHAPE=Shapes.or(Block.box(0,12,0,16,16,16),Block.box(-2,0,1,18,14,15));
    private static final VoxelShape Z_SHAPE=Shapes.or(Block.box(0,12,0,16,16,16),Block.box(1,0,-2,15,14,18));
    public CartAssemblerBlock(Properties properties){super(true,properties);registerDefaultState(stateDefinition.any().setValue(RAIL_SHAPE,RailShape.NORTH_SOUTH).setValue(POWERED,false).setValue(BACKWARDS,false).setValue(RAIL_TYPE,CartAssembleRailType.POWERED_RAIL).setValue(WATERLOGGED,false));}
    public static BlockState createAnchor(BlockState state){return AllBlocks.MINECART_ANCHOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS,state.getValue(RAIL_SHAPE)==RailShape.NORTH_SOUTH?Direction.Axis.Z:Direction.Axis.X);}
    public static BlockState getRailBlock(BlockState state){var rail=(BaseRailBlock)state.getValue(RAIL_TYPE).getBlock();var result=rail.defaultBlockState().setValue(rail.getShapeProperty(),state.getValue(RAIL_SHAPE));return result.hasProperty(ControllerRailBlock.BACKWARDS)?result.setValue(ControllerRailBlock.BACKWARDS,state.getValue(BACKWARDS)):result;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(RAIL_SHAPE,POWERED,RAIL_TYPE,BACKWARDS,WATERLOGGED);}
    @Override public Property<RailShape> getShapeProperty(){return RAIL_SHAPE;}
    /** Native26.3 has no Forge canMakeSlopes hook. This overlay retains its explicitly placed straight axis. */
    @Override protected BlockState updateDir(Level level,BlockPos pos,BlockState state,boolean placed){return state;}
    @Override protected void updateState(BlockState state,Level level,BlockPos pos,Block neighbor){if(!level.isClientSide()&&state.getValue(POWERED)!=level.hasNeighborSignal(pos))level.setBlock(pos,state.cycle(POWERED),Block.UPDATE_CLIENTS);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){if(!level.isClientSide()){updateState(state,level,pos,neighbor);super.neighborChanged(LoadedLevelAccess.blockState(level,pos),level,pos,neighbor,orientation,moved);}}
    public enum CartAssemblerAction {
        ASSEMBLE,DISASSEMBLE,ASSEMBLE_ACCELERATE,DISASSEMBLE_BRAKE,ASSEMBLE_ACCELERATE_DIRECTIONAL,PASS;
        public boolean shouldAssemble(){return this==ASSEMBLE||this==ASSEMBLE_ACCELERATE||this==ASSEMBLE_ACCELERATE_DIRECTIONAL;}
        public boolean shouldDisassemble(){return this==DISASSEMBLE||this==DISASSEMBLE_BRAKE;}
    }
    public static CartAssemblerAction getActionForCart(BlockState state,AbstractMinecart cart){
        boolean power=state.getValue(POWERED);return switch(state.getValue(RAIL_TYPE)){
            case ACTIVATOR_RAIL->power?CartAssemblerAction.DISASSEMBLE:CartAssemblerAction.PASS;
            case CONTROLLER_RAIL->power?CartAssemblerAction.ASSEMBLE_ACCELERATE_DIRECTIONAL:CartAssemblerAction.DISASSEMBLE_BRAKE;
            case DETECTOR_RAIL->cart.getPassengers().isEmpty()?CartAssemblerAction.ASSEMBLE_ACCELERATE:CartAssemblerAction.DISASSEMBLE;
            case POWERED_RAIL->power?CartAssemblerAction.ASSEMBLE_ACCELERATE:CartAssemblerAction.DISASSEMBLE_BRAKE;
            case REGULAR->power?CartAssemblerAction.ASSEMBLE:CartAssemblerAction.DISASSEMBLE;
        };
    }
    public static boolean canAssembleTo(AbstractMinecart cart){return cart.isRideable()||cart instanceof MinecartFurnace||cart instanceof MinecartChest;}
    /** Invoked by actual native Old/New movement call sites, once per native traversal position. */
    public void onMinecartPass(BlockState state,Level world,BlockPos pos,AbstractMinecart cart){if(!world.isClientSide()&&canAssembleTo(cart)&&LoadedLevelAccess.blockEntity(world,pos) instanceof CartAssemblerBlockEntity assembler)assembler.assembleNextTick(cart);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        var previous=state.getValue(RAIL_TYPE);CartAssembleRailType next=null;for(var type:CartAssembleRailType.values())if(type.getItem()==stack.getItem())next=type;
        if(next==null||next==previous)return InteractionResult.TRY_WITH_EMPTY_HAND;
        if(!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,pos))return InteractionResult.FAIL;
        if(!level.isClientSide()&&level.setBlockAndUpdate(pos,state.setValue(RAIL_TYPE,next))){
            if(!player.isCreative()){stack.shrink(1);player.getInventory().placeItemBackInInventory(new ItemStack(previous.getItem()),Prediction.SERVER_ONLY);}
            level.playSound(null,pos,SoundEvents.ITEM_PICKUP,SoundSource.PLAYERS,1,1);
        }return InteractionResult.SUCCESS;
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return state.getValue(RAIL_SHAPE)==RailShape.NORTH_SOUTH?Z_SHAPE:X_SHAPE;}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){
        if(context instanceof EntityCollisionContext entity){if(entity.getEntity() instanceof AbstractMinecart)return Shapes.empty();if(entity.getEntity() instanceof Player)return state.getValue(RAIL_SHAPE)==RailShape.NORTH_SOUTH?Block.box(1,0,0,15,16,16):Block.box(0,0,1,16,16,15);}
        return Shapes.block();
    }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return false;}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(LoadedLevelAccess.blockEntity(level,pos) instanceof CartAssemblerBlockEntity assembler)assembler.sourceAdvancements.place(placer);}
    @Override protected boolean isPathfindable(BlockState state,net.minecraft.world.level.pathfinder.PathComputationType path){return false;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new CartAssemblerBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntityTypes.CART_ASSEMBLER?(world,pos,current,entity)->CartAssemblerBlockEntity.tick(world,pos,current,(CartAssemblerBlockEntity)entity):null;}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder parameters){var drops=new ArrayList<>(super.getDrops(state,parameters));drops.addAll(getRailBlock(state).getDrops(parameters));return drops;}
    public InteractionResult onSneakWrenched(BlockState state,UseOnContext context){
        if(!(context.getLevel() instanceof ServerLevel server))return InteractionResult.SUCCESS;var player=context.getPlayer();if(player==null)return InteractionResult.PASS;
        var pos=context.getClickedPos();if(!net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(server,player,pos,state,LoadedLevelAccess.blockEntity(server,pos)))return InteractionResult.FAIL;var drops=player.isCreative()?List.<ItemStack>of():super.getDrops(state,new LootParams.Builder(server).withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,Vec3.atCenterOf(pos)).withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,context.getItemInHand()));
        if(server.setBlockAndUpdate(pos,getRailBlock(state)))drops.forEach(item->player.getInventory().placeItemBackInInventory(item,Prediction.SERVER_ONLY));return InteractionResult.SUCCESS;
    }
    public InteractionResult onWrenched(BlockState state,UseOnContext context){if(!context.getLevel().isClientSide()){context.getLevel().setBlock(context.getClickedPos(),rotate(state,Rotation.CLOCKWISE_90),Block.UPDATE_ALL);context.getLevel().updateNeighborsAt(context.getClickedPos().below(),this);}return InteractionResult.SUCCESS;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){if(rotation==Rotation.NONE)return state;var transformed=AllBlocks.CONTROLLER_RAIL.defaultBlockState().setValue(ControllerRailBlock.SHAPE,state.getValue(RAIL_SHAPE)).setValue(ControllerRailBlock.BACKWARDS,state.getValue(BACKWARDS)).rotate(rotation);return state.setValue(RAIL_SHAPE,transformed.getValue(ControllerRailBlock.SHAPE)).setValue(BACKWARDS,transformed.getValue(ControllerRailBlock.BACKWARDS));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){if(mirror==Mirror.NONE)return state;var transformed=AllBlocks.CONTROLLER_RAIL.defaultBlockState().setValue(ControllerRailBlock.SHAPE,state.getValue(RAIL_SHAPE)).setValue(ControllerRailBlock.BACKWARDS,state.getValue(BACKWARDS)).mirror(mirror);return state.setValue(BACKWARDS,transformed.getValue(ControllerRailBlock.BACKWARDS));}
    public static Direction getHorizontalDirection(BlockState state){if(!(state.getBlock() instanceof CartAssemblerBlock))return Direction.SOUTH;var front=state.getValue(RAIL_SHAPE)==RailShape.EAST_WEST?Direction.WEST:Direction.NORTH;return state.getValue(BACKWARDS)?front.getOpposite():front;}
}
