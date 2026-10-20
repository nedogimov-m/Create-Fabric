package com.simibubi.create.content.trains.bogey;

import com.simibubi.create.*;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;

/** Concrete source bogey geometry and styles. Carriage traversal is integrated when real Carriage exists. */
public abstract class AbstractBogeyBlock<T extends AbstractBogeyBlockEntity> extends Block implements EntityBlock,SimpleWaterloggedBlock {
    public static final EnumProperty<Direction.Axis> AXIS=BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    public static final StreamCodec<RegistryFriendlyByteBuf,AbstractBogeyBlock<?>> STREAM_CODEC=ByteBufCodecs.registry(Registries.BLOCK).map(block->{if(block instanceof AbstractBogeyBlock<?> bogey)return bogey;throw new IllegalArgumentException("Block is not a bogey");},block->block);
    private static final List<Identifier> BOGEYS=new ArrayList<>();
    public final BogeySizes.BogeySize size;
    protected AbstractBogeyBlock(Properties properties,BogeySizes.BogeySize size){super(properties);this.size=Objects.requireNonNull(size);registerDefaultState(defaultBlockState().setValue(AXIS,Direction.Axis.X).setValue(WATERLOGGED,false));}
    public static void registerStandardBogey(Identifier id){if(BOGEYS.contains(id))throw new IllegalArgumentException("Duplicate bogey block "+id);BOGEYS.add(id);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(AXIS,WATERLOGGED);}
    @Override protected BlockState updateShape(BlockState state,LevelReader reader,ScheduledTickAccess ticks,BlockPos pos,Direction direction,BlockPos next,BlockState neighbor,net.minecraft.util.RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(reader));return state;}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    public EnumSet<Direction> getStickySurfaces(BlockGetter world,BlockPos pos,BlockState state){return state.getValue(AXIS)==Direction.Axis.X?EnumSet.of(Direction.EAST,Direction.WEST):EnumSet.of(Direction.NORTH,Direction.SOUTH);}
    public abstract TrackMaterial.TrackType getTrackType(BogeyStyle style);
    public Set<TrackMaterial.TrackType> getValidPathfindingTypes(BogeyStyle style){return Set.of(getTrackType(style));}
    public boolean isOnIncompatibleTrack(TrackMaterial material,BogeyStyle style){return material.trackType!=getTrackType(style);}
    public abstract double getWheelPointSpacing();
    public abstract double getWheelRadius();
    protected abstract Vec3 getConnectorAnchorOffset();
    public Vec3 getConnectorAnchorOffset(boolean upsideDown){return getConnectorAnchorOffset();}
    public abstract BogeyStyle getDefaultStyle();
    public BogeySizes.BogeySize getSize(){return size;}
    public boolean allowsSingleBogeyCarriage(){return true;}
    public boolean captureBlockEntityForTrain(){return false;}
    public Direction getBogeyUpDirection(){return Direction.UP;}
    public boolean isTrackAxisAlongFirstCoordinate(BlockState state){return state.getValue(AXIS)==Direction.Axis.X;}
    public BlockState getMatchingBogey(Direction up,boolean alongFirst){return up==Direction.UP?defaultBlockState().setValue(AXIS,alongFirst?Direction.Axis.X:Direction.Axis.Z):null;}
    public boolean canBeUpsideDown(){return false;}
    public boolean isUpsideDown(BlockState state){return false;}
    public BlockState getVersion(BlockState state,boolean upsideDown){return state;}
    public List<Property<?>> propertiesToCopy(){return List.of(WATERLOGGED,AXIS);}
    private static <V extends Comparable<V>>BlockState copyProperty(BlockState from,BlockState to,Property<V> property){return from.hasProperty(property)&&to.hasProperty(property)?to.setValue(property,from.getValue(property)):to;}
    protected BlockState copyProperties(BlockState from,BlockState to){for(var property:propertiesToCopy())to=copyProperty(from,to,property);return to;}
    protected List<Identifier> getBogeyBlockCycle(){return Collections.unmodifiableList(BOGEYS);}
    public BlockState getRotatedBlockState(BlockState state,Direction face){
        var cycle=getBogeyBlockCycle();int index=cycle.indexOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));if(index<0)return state;
        for(int offset=1;offset<cycle.size();offset++){
            var candidate=BuiltInRegistries.BLOCK.getValue(cycle.get((index+offset)%cycle.size()));
            if(candidate instanceof AbstractBogeyBlock<?> block){var matching=block.getMatchingBogey(getBogeyUpDirection(),isTrackAxisAlongFirstCoordinate(state));if(matching!=null)return copyProperties(state,matching);}
        }
        return state;
    }
    public BlockState getNextSize(Level level,BlockPos pos){return LoadedLevelAccess.blockEntity(level,pos)instanceof AbstractBogeyBlockEntity entity?getNextSize(entity):LoadedLevelAccess.blockState(level,pos);}
    public BlockState getNextSize(AbstractBogeyBlockEntity entity){return copyProperties(entity.getBlockState(),entity.getStyle().getNextBlock(size).defaultBlockState());}
    public BlockState getStateOfSize(AbstractBogeyBlockEntity entity,BogeySizes.BogeySize size){return copyProperties(entity.getBlockState(),entity.getStyle().getBlockForSize(size).defaultBlockState());}
    public BogeyStyle getNextStyle(Level level,BlockPos pos){return LoadedLevelAccess.blockEntity(level,pos)instanceof AbstractBogeyBlockEntity entity?getNextStyle(entity.getStyle()):getDefaultStyle();}
    public BogeyStyle getNextStyle(BogeyStyle current){var group=new ArrayList<>(current.getCycleGroup().values());return group.size()<2?current:group.get((group.indexOf(current)+1)%group.size());}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!stack.is(AllItems.WRENCH)||player.isShiftKeyDown()||AllBogeyStyles.BOGEY_STYLES.size()<2)return InteractionResult.PASS;
        if(player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,pos))return InteractionResult.PASS;
        if(player.getCooldowns().isOnCooldown(stack))return InteractionResult.SUCCESS;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!(LoadedLevelAccess.blockEntity(level,pos)instanceof AbstractBogeyBlockEntity entity))return InteractionResult.FAIL;
        var current=entity.getStyle();var next=getNextStyle(current);if(next==current)return InteractionResult.PASS;
        var nextSize=size;for(int i=0;i<BogeySizes.all().size()&&!next.validSizes().contains(nextSize);i++)nextSize=nextSize.nextBySize();
        var replacement=copyProperties(state,next.getBlockForSize(nextSize).defaultBlockState());var data=entity.getBogeyData().copy().merge(next.defaultData);data.putString(AbstractBogeyBlockEntity.BOGEY_STYLE_KEY,next.id.toString());
        if(!BogeyPlacement.replace(level,pos,state,replacement,data))return InteractionResult.FAIL;
        player.getCooldowns().addCooldown(stack,20);player.sendOverlayMessage(ComponentHelper.updated(nextSize!=size,next));return InteractionResult.SUCCESS;
    }
    public InteractionResult onWrenched(BlockState state,UseOnContext context){
        var level=context.getLevel();var pos=context.getClickedPos();var next=getRotatedBlockState(state,context.getClickedFace());if(next==state)return InteractionResult.SUCCESS;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!(LoadedLevelAccess.blockEntity(level,pos)instanceof AbstractBogeyBlockEntity entity)||!BogeyPlacement.replace(level,pos,state,next,entity.getBogeyData()))return InteractionResult.FAIL;
        level.playSound(null,pos,AllSoundEvents.WRENCH_ROTATE,SoundSource.BLOCKS,1,1);return InteractionResult.SUCCESS;
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return rotation==Rotation.CLOCKWISE_90||rotation==Rotation.COUNTERCLOCKWISE_90?state.cycle(AXIS):state;}
    private static final class ComponentHelper {
        private static net.minecraft.network.chat.Component updated(boolean size,BogeyStyle style){return net.minecraft.network.chat.Component.translatable("create.bogey.style."+(size?"updated_style_and_size":"updated_style")).append(": ").append(style.displayName);}
    }
}
