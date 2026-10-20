package com.simibubi.create.content.decoration.copycat;

import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Source panel-nearest-plane and bounded pole extension, using the real finite native BlockItem. */
public final class CopycatPlacement {
    static final class Context extends BlockPlaceContext {
        final BlockState template;
        Context(Player player,InteractionHand hand,ItemStack stack,BlockHitResult hit,BlockState template){super(player,hand,stack,hit);this.template=template;}
    }
    public static InteractionResult extend(CopycatBlock block,ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,pos))return InteractionResult.PASS;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        boolean panel=block instanceof CopycatPanelBlock;var axis=panel?state.getValue(CopycatPanelBlock.FACING).getAxis():state.getValue(CopycatStepBlock.FACING).getClockWise().getAxis();
        var directions=Arrays.stream(Direction.values()).filter(side->panel?side.getAxis()!=axis:side.getAxis()==axis).sorted(Comparator.comparingDouble(side->Vec3.atCenterOf(pos.relative(side)).distanceToSqr(hit.getLocation()))).toList();
        int range=panel?1:CopycatConfig.placementAssistRange();var reach=player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);if(!panel&&reach!=null&&reach.hasModifier(ExtendoGripItem.singleRangeAttributeModifier.id()))range+=4;
        for(var direction:directions){
            int poles=0;var target=pos.relative(direction);
            if(!panel)while(poles<range&&LoadedLevelAccess.isLoaded(level,target)){
                var next=LoadedLevelAccess.blockState(level,target);if(!(next.getBlock() instanceof CopycatStepBlock)||next.getValue(CopycatStepBlock.FACING).getClockWise().getAxis()!=axis)break;poles++;target=pos.relative(direction,poles+1);
            }
            if(poles>=range||!LoadedLevelAccess.isLoaded(level,target)||!level.isInValidBounds(target)||!level.getWorldBorder().isWithinBounds(target)||!level.mayInteract(player,target)||!LoadedLevelAccess.blockState(level,target).canBeReplaced())continue;
            var targetHit=new BlockHitResult(Vec3.atCenterOf(target),Direction.UP,target,false);
            // The source Panel/Step consumes this helper interaction even when the
            // target refuses placement. Otherwise native item use places elsewhere.
            if(UseBlockCallback.EVENT.invoker().interact(player,level,hand,targetHit)!=InteractionResult.PASS)return InteractionResult.SUCCESS;
            ((BlockItem)stack.getItem()).place(new Context(player,hand,stack,targetHit,state));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }
    private CopycatPlacement(){}
}
