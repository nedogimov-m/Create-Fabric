package com.simibubi.create.foundation.blockEntity.behaviour;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Actual interaction entry point for source settings owners; client warmup has a typed target. */
public class ValueSettingsInputHandler {
    public record InteractionTarget(BlockPos pos,Direction side,InteractionHand hand,int behaviourIndex,BlockHitResult hit){
        public InteractionTarget{pos=pos.immutable();}
    }
    public static java.util.function.Consumer<InteractionTarget> CLIENT_START=target->{};
    public static boolean canInteract(Player player){return player!=null&&!player.isSpectator()&&!player.isShiftKeyDown()&&player.mayBuild();}
    public static InteractionResult interact(Level level,Player player,InteractionHand hand,BlockHitResult hit){
        if(!canInteract(player)||player.getMainHandItem().is(com.simibubi.create.AllBlocks.CLIPBOARD.asItem())
                ||!player.isWithinBlockInteractionRange(hit.getBlockPos(),1)||!LoadedLevelAccess.isLoaded(level,hit.getBlockPos())||!level.mayInteract(player,hit.getBlockPos())
                ||!(LoadedLevelAccess.blockEntity(level,hit.getBlockPos()) instanceof SmartBlockEntity smart))return InteractionResult.PASS;
        if(smart.isRemoved())return InteractionResult.PASS;
        for(var behaviour:java.util.List.copyOf(smart.getAllBehaviours())){
            if(!(behaviour instanceof ValueSettingsBehaviour settings)||!settings.isActive()||!settings.mayInteract(player)
                    ||settings.bypassesInput(player.getMainHandItem())
                    ||settings.onlyVisibleWithWrench()&&!player.getItemInHand(hand).is(AllItems.WRENCH))continue;
            var slot=settings.getSlotPositioning();
            if(slot instanceof ValueBoxTransform.Sided sided)sided.fromSide(hit.getDirection());
            if(!slot.shouldRender(level,hit.getBlockPos(),smart.getBlockState()))continue;
            boolean fake=player instanceof net.fabricmc.fabric.api.entity.FakePlayer;
            if(!fake&&!settings.testHit(hit.getLocation()))continue;
            if(smart.isRemoved()||LoadedLevelAccess.blockEntity(level,hit.getBlockPos())!=smart
                    ||smart.getBehaviour(behaviour.getType())!=behaviour)return InteractionResult.PASS;
            if(!settings.acceptsValueSettings()||fake){if(!level.isClientSide())settings.onShortInteract(player,hand,hit.getDirection(),hit);}
            else if(level.isClientSide())CLIENT_START.accept(new InteractionTarget(hit.getBlockPos(),hit.getDirection(),hand,settings.netId(),hit));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    public static void register(){com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->interact(level,player,hand,hit));}
}
