package com.simibubi.create.content.kinetics.simpleRelays;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Source pre-block cog assistance; native BlockItem owns placement, components and finite payment. */
public final class CogwheelBlockItem extends BlockItem {
    public record Placement(BlockPos pos,Axis axis) {
        public Placement { pos=pos.immutable(); }
    }
    private static boolean registered;
    private final boolean large;

    public CogwheelBlockItem(CogWheelBlock block,Properties properties){super(block,properties);large=block.isLargeCog();}

    public static void register(){
        if(registered)return;registered=true;
        com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->player.getItemInHand(hand).getItem() instanceof CogwheelBlockItem item
            ?item.useOnFirst(new UseOnContext(player,hand,hit)):InteractionResult.PASS);
    }

    public InteractionResult useOnFirst(UseOnContext context){
        var player=context.getPlayer();var level=context.getLevel();var source=context.getClickedPos();
        if(player==null||player.isSpectator()||!player.mayBuild()||player.isShiftKeyDown()
            ||context.getItemInHand().isEmpty()||!LoadedLevelAccess.isLoaded(level,source)||!level.mayInteract(player,source))return InteractionResult.PASS;
        var state=LoadedLevelAccess.blockState(level,source);
        if(!(state.getBlock() instanceof ICogWheel))return InteractionResult.PASS;
        var hit=new BlockHitResult(context.getClickLocation(),context.getClickedFace(),source,true);
        var placement=primaryOffset(large,level,state,source,hit);
        if(placement==null)return InteractionResult.PASS;
        return placeAssisted(this,context,placement);
    }

    private static InteractionResult placeAssisted(BlockItem item,UseOnContext context,Placement placement){
        var player=context.getPlayer();if(player==null)return InteractionResult.PASS;
        var level=context.getLevel();var target=placement.pos();
        if(!available(level,target)||!level.mayInteract(player,target)||!player.mayUseItemAt(target,context.getClickedFace(),context.getItemInHand()))return InteractionResult.FAIL;
        var targetHit=new BlockHitResult(Vec3.atCenterOf(target),context.getClickedFace(),target,false);
        // Fabric protection hooks see the actual target as well as the original source click.
        if(UseBlockCallback.EVENT.invoker().interact(player,level,context.getHand(),targetHit)!=InteractionResult.PASS)return InteractionResult.FAIL;
        if(!available(level,target))return InteractionResult.FAIL;
        var nativeContext=assistedContext(context,placement);
        if(!nativeContext.getClickedPos().equals(target))return InteractionResult.FAIL;
        var result=item.place(nativeContext);
        // Pre-block/shaft-block helpers bypass ItemStack.useOn's ordinary stat wrapper, as in source Catnip.
        if(!level.isClientSide()&&result.consumesAction())player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(item));
        return result;
    }

    /** Read-only preview and actual action share the same native target/axis context. */
    public static BlockPlaceContext assistedContext(UseOnContext source,Placement placement){
        return new AssistedPlacementContext(source,new BlockHitResult(Vec3.atCenterOf(placement.pos()),source.getClickedFace(),placement.pos(),false),placement.axis());
    }

    /** Equivalent to the original IPlacementHelper.ID exemption for off-hand auto-glue. */
    public static boolean isAssistedPlacement(BlockPlaceContext context){return context instanceof AssistedPlacementContext;}
    public static @Nullable Axis assistedAxis(BlockPlaceContext context){return context instanceof AssistedPlacementContext assisted?assisted.axis:null;}
    private static final class AssistedPlacementContext extends BlockPlaceContext {
        final Axis axis;
        AssistedPlacementContext(UseOnContext source,BlockHitResult target,Axis axis){
            super(source.getLevel(),source.getPlayer(),source.getHand(),source.getItemInHand(),target);this.axis=axis;
        }
    }

    /** Source ShaftBlock's pole helper accepts shafts and cogs and only extends actual shaft owners. */
    public static InteractionResult extendShaft(net.minecraft.world.item.ItemStack stack,BlockState state,Level level,BlockPos source,
            net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,BlockHitResult hit){
        if(!(stack.getItem() instanceof BlockItem item)||!(item.getBlock() instanceof ShaftBlock)||player.isShiftKeyDown()
            ||player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,source))return InteractionResult.TRY_WITH_EMPTY_HAND;
        var placement=shaftOffset(player,level,state,source,hit);
        return placement==null?InteractionResult.TRY_WITH_EMPTY_HAND:placeAssisted(item,new UseOnContext(player,hand,hit),placement);
    }

    /** Original bounded pole prediction; reads actual loaded state and actual player modifier only. */
    public static @Nullable Placement shaftOffset(net.minecraft.world.entity.player.@Nullable Player player,Level level,BlockState state,BlockPos source,BlockHitResult hit){
        if(!(state.is(com.simibubi.create.AllBlocks.SHAFT)||state.is(com.simibubi.create.AllBlocks.POWERED_SHAFT))||!LoadedLevelAccess.isLoaded(level,source))return null;
        int range=com.simibubi.create.infrastructure.config.AllConfigs.server().equipment.placementAssistRange.get();
        var reach=player==null?null:player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);
        if(reach!=null&&reach.hasModifier(com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem.singleRangeAttributeModifier.id()))range+=4;
        var axis=state.getValue(ShaftBlock.AXIS);
        for(var side:ordered(source,hit.getLocation(),d->d.getAxis()==axis)){
            int poles=0;var target=source.relative(side);
            while(poles<range&&LoadedLevelAccess.isLoaded(level,target)){
                var next=LoadedLevelAccess.blockState(level,target);
                if(!(next.getBlock() instanceof ShaftBlock)||next.getValue(ShaftBlock.AXIS)!=axis)break;
                poles++;target=target.relative(side);
            }
            if(poles>=range||!available(level,target))continue;
            return new Placement(target,axis);
        }
        return null;
    }

    /** Original first helper matches all ICogWheel states, including integrated/encased cogs. */
    public static @Nullable Placement primaryOffset(boolean large,Level level,BlockState state,BlockPos source,BlockHitResult hit){
        if(!(state.getBlock() instanceof ICogWheel cog)||hitOnShaft(cog.getRotationAxis(state),hit.getLocation()))return null;
        var axis=cog.getRotationAxis(state);
        if(!large&&!cog.isLargeCog()){
            for(var side:ordered(source,hit.getLocation(),d->d.getAxis()!=axis)){
                var target=source.relative(side);
                if(available(level,target)&&CogWheelBlock.isValidCogwheelPosition(false,level,target,axis))return new Placement(target,axis);
            }return null;
        }
        if(large&&cog.isLargeCog()){
            var axial=ordered(source,hit.getLocation(),d->d.getAxis()==axis).getFirst();
            for(var side:ordered(source,hit.getLocation(),d->d.getAxis()!=axis)){
                var target=source.relative(side).relative(axial);
                if(available(level,target)&&CogWheelBlock.isValidCogwheelPosition(true,level,target,side.getAxis()))return new Placement(target,side.getAxis());
            }return null;
        }
        var closest=ordered(source,hit.getLocation(),d->d.getAxis()!=axis).getFirst();
        for(var side:ordered(source,hit.getLocation(),d->d.getAxis()!=axis&&d.getAxis()!=closest.getAxis())){
            var target=source.relative(side).relative(closest);
            // Source DiagonalCogHelper deliberately checks the clicked cog's size here.
            if(available(level,target)&&CogWheelBlock.isValidCogwheelPosition(cog.isLargeCog(),level,target,axis))return new Placement(target,axis);
        }return null;
    }

    /** Original registered integrated helper geometry, retained separately from the primary action order. */
    public static @Nullable Placement integratedOffset(boolean large,Level level,BlockState state,BlockPos source,BlockHitResult hit){
        if(ICogWheel.isDedicatedCogWheel(state.getBlock())||!ICogWheel.isSmallCog(state))return null;
        var axis=integratedAxis(state);var face=hit.getDirection();
        if(face.getAxis()==axis)return null;
        for(var side:ordered(source,hit.getLocation(),d->d.getAxis()!=axis&&(!large||d.getAxis()!=face.getAxis()))){
            var target=large?source.relative(face).relative(side):source.relative(side);
            if(!available(level,target))continue;
            if(!CogWheelBlock.isValidCogwheelPosition(false,level,target,axis))return null;
            return new Placement(target,axis);
        }return null;
    }

    private static Axis integratedAxis(BlockState state){
        if(state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING))return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).getAxis();
        if(state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING))return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING).getAxis();
        return state.hasProperty(CogWheelBlock.AXIS)?state.getValue(CogWheelBlock.AXIS):Axis.Y;
    }
    private static boolean hitOnShaft(Axis axis,Vec3 hit){
        double x=hit.x-Math.floor(hit.x),y=hit.y-Math.floor(hit.y),z=hit.z-Math.floor(hit.z);
        double min=5/16d-.001,max=11/16d+.001;
        return switch(axis){case X->y>=min&&y<max&&z>=min&&z<max;case Y->x>=min&&x<max&&z>=min&&z<max;case Z->x>=min&&x<max&&y>=min&&y<max;};
    }
    private static List<Direction> ordered(BlockPos source,Vec3 hit,Predicate<Direction> include){
        var directions=new ArrayList<Direction>();for(var side:Direction.values())if(include.test(side))directions.add(side);
        var centerToHit=hit.subtract(Vec3.atCenterOf(source));
        directions.sort(Comparator.comparingDouble(side->side.getUnitVec3().distanceTo(centerToHit)));
        return directions;
    }
    private static boolean available(Level level,BlockPos pos){
        return level.isInValidBounds(pos)&&level.getWorldBorder().isWithinBounds(pos)&&LoadedLevelAccess.isLoaded(level,pos)&&LoadedLevelAccess.blockState(level,pos).canBeReplaced();
    }
}
