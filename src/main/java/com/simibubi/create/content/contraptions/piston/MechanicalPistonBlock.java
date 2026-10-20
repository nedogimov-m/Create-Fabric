package com.simibubi.create.content.contraptions.piston;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.chassis.AbstractChassisBlock;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MechanicalPistonBlock extends DirectionalAxisKineticBlock {
    public enum PistonState implements StringRepresentable{RETRACTED,MOVING,EXTENDED;@Override public String getSerializedName(){return name().toLowerCase(java.util.Locale.ROOT);}}
    public static final EnumProperty<PistonState> STATE=EnumProperty.create("state",PistonState.class);
    public final boolean isSticky;
    public static MechanicalPistonBlock normal(Properties p){return new MechanicalPistonBlock(p,false);}
    public static MechanicalPistonBlock sticky(Properties p){return new MechanicalPistonBlock(p,true);}
    public MechanicalPistonBlock(Properties p,boolean sticky){super(p.strength(1.5f,6).sound(SoundType.STONE).noOcclusion());isSticky=sticky;registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(AXIS_ALONG_FIRST_COORDINATE,false).setValue(STATE,PistonState.RETRACTED));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder.add(STATE));}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!player.mayBuild()||player.isSpectator()||player.isShiftKeyDown()||!level.mayInteract(player,pos))return InteractionResult.TRY_WITH_EMPTY_HAND;
        if(!stack.is(AbstractChassisBlock.SLIMEBALLS)&&!stack.is(net.minecraft.world.item.Items.SLIME_BALL))return InteractionResult.TRY_WITH_EMPTY_HAND;
        if(isSticky||state.getValue(STATE)!=PistonState.RETRACTED||hit.getDirection()!=state.getValue(FACING))return InteractionResult.TRY_WITH_EMPTY_HAND;
        if(level.isClientSide()){var point=hit.getLocation();level.addParticle(ParticleTypes.ITEM_SLIME,point.x,point.y,point.z,0,0,0);}
        else{if(!player.getAbilities().instabuild)stack.shrink(1);level.playSound(null,pos,AllSoundEvents.SLIME_ADDED,SoundSource.BLOCKS,.5f,1);level.setBlockAndUpdate(pos,AllBlocks.STICKY_MECHANICAL_PISTON.defaultBlockState().setValue(FACING,state.getValue(FACING)).setValue(AXIS_ALONG_FIRST_COORDINATE,state.getValue(AXIS_ALONG_FIRST_COORDINATE)));}
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!player.mayBuild()||player.isSpectator()||player.isShiftKeyDown()||!player.getMainHandItem().isEmpty()||!level.mayInteract(player,pos))return InteractionResult.PASS;
        if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof MechanicalPistonBlockEntity piston)piston.requestAssembly();return InteractionResult.SUCCESS;
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(STATE)==PistonState.RETRACTED?Shapes.block():PistonShapes.casing(state.getValue(FACING),state.getValue(STATE)==PistonState.EXTENDED);}
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){PistonStructure.destroyConnectedParts(level,pos,state,player);return super.playerWillDestroy(level,pos,state,player);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new MechanicalPistonBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntityTypes.MECHANICAL_PISTON?(world,pos,block,be)->MechanicalPistonBlockEntity.tick(world,pos,block,(MechanicalPistonBlockEntity)be):null;}
    public static boolean isPiston(BlockState state){return state.getBlock() instanceof MechanicalPistonBlock;}
    public static boolean isStickyPiston(BlockState state){return state.getBlock() instanceof MechanicalPistonBlock piston&&piston.isSticky;}
    public static boolean isExtensionPole(BlockState state){return state.getBlock() instanceof PistonExtensionPoleBlock&&!(state.getBlock() instanceof MechanicalPistonHeadBlock);}
    public static boolean isPistonHead(BlockState state){return state.getBlock() instanceof MechanicalPistonHeadBlock;}
    public static int maxAllowedPistonPoles(){return com.simibubi.create.content.contraptions.ContraptionSettings.maxPistonPoles;}
}
