package com.simibubi.create.content.kinetics.deployer;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

public final class DeployerHandler {
    private static final TagKey<Item> DEPLOYABLE_DRINK=TagKey.create(net.minecraft.core.registries.Registries.ITEM,com.simibubi.create.Create.asResource("deployable_drink"));
    public static boolean shouldActivate(DeployerBlockEntity deployer){return shouldActivate(deployer.getHeldItem(),deployer.getLevel(),deployer.getBlockPos().relative(deployer.getBlockState().getValue(DeployerBlock.FACING),2),null);}
    public static boolean shouldActivate(ItemStack held,Level level,BlockPos target,Direction facing){
        if(!LoadedLevelAccess.isLoaded(level,target))return false;
        if(held.getItem() instanceof BlockItem block&&LoadedLevelAccess.blockState(level,target).is(block.getBlock()))return false;
        if(held.getItem() instanceof BucketItem bucket&&bucket.getContent()!=net.minecraft.world.level.material.Fluids.EMPTY&&LoadedLevelAccess.blockState(level,target).getFluidState().getType()==bucket.getContent())return false;
        if(!held.isEmpty()&&facing==Direction.DOWN){var surface=LoadedLevelAccess.blockEntity(level,target);if(surface instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity||surface instanceof com.simibubi.create.content.logistics.depot.DepotBlockEntity)return false;}
        return true;
    }
    public static void activate(DeployerBlockEntity deployer,DeployerFakePlayer player){
        var facing=deployer.getBlockState().getValue(DeployerBlock.FACING);player.setXRot(facing==Direction.UP?-90:facing==Direction.DOWN?90:0);player.setYRot(facing.toYRot());
        player.blockBreakingProgress=deployer.breakingPos()==null?null:org.apache.commons.lang3.tuple.Pair.of(deployer.breakingPos(),deployer.breakingProgress());
        try(var source=DeployerCartItemSource.stationary(deployer,player)){
            activate(player,Vec3.atCenterOf(deployer.getBlockPos()),deployer.getBlockPos().relative(facing,2),facing.getUnitVec3(),deployer.getMode());
        }
        if(player.blockBreakingProgress==null)deployer.clearBreaking();else deployer.breaking(player.blockBreakingProgress.getKey(),player.blockBreakingProgress.getValue());
    }
    public static void activate(DeployerFakePlayer player,Vec3 center,BlockPos target,Vec3 direction,DeployerBlockEntity.Mode mode){
        if(!LoadedLevelAccess.isLoaded(player.level(),target))return;
        var origin=center.add(direction.scale(1.5+1d/64));var end=center.add(direction.scale(2.5-1d/64));
        // Native ray/item placement paths may inspect adjacent cells; all must already exist.
        var scan=new AABB(origin,end).inflate(1);for(var pos:BlockPos.betweenClosed(BlockPos.containing(scan.minX,scan.minY,scan.minZ),BlockPos.containing(scan.maxX,scan.maxY,scan.maxZ)))if(!LoadedLevelAccess.isLoaded(player.level(),pos))return;
        var modifiers=com.google.common.collect.HashMultimap.<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>,net.minecraft.world.entity.ai.attributes.AttributeModifier>create();
        player.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND,modifiers::put);net.minecraft.world.item.enchantment.EnchantmentHelper.forEachModifier(player.getMainHandItem(),EquipmentSlot.MAINHAND,modifiers::put);
        player.getAttributes().addTransientAttributeModifiers(modifiers);player.workTarget=target;player.acting=true;
        try{activateInner(player,center,target,direction,mode);}finally{player.acting=false;player.workTarget=null;player.getAttributes().removeAttributeModifiers(modifiers);}
    }
    private static void activateInner(DeployerFakePlayer player,Vec3 center,BlockPos target,Vec3 direction,DeployerBlockEntity.Mode mode){
        var level=player.level();player.setPos(center.add(direction.scale(1.5+1d/64)));
        var entities=level.getEntitiesOfClass(Entity.class,new AABB(target),entity->entity!=player&&!entity.isRemoved()&&!(entity instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity));
        if(!entities.isEmpty()){
            var entity=entities.get(level.getRandom().nextInt(entities.size()));
            if(mode==DeployerBlockEntity.Mode.PUNCH){player.resetAttackStrengthTicker();player.attack(entity);return;}
            var result=UseEntityCallback.EVENT.invoker().interact(player,level,InteractionHand.MAIN_HAND,entity,null);
            if(result==InteractionResult.FAIL)return;if(result.consumesAction())return;
            if(player.interactOn(entity,InteractionHand.MAIN_HAND,entity.getBoundingBox().getCenter().subtract(entity.position())).consumesAction()){
                if(entity instanceof net.minecraft.world.entity.npc.villager.AbstractVillager villager&&villager.getTradingPlayer()==player)villager.setTradingPlayer(null);return;
            }
            if(entity instanceof Player targetPlayer){
                var food=player.getMainHandItem().get(net.minecraft.core.component.DataComponents.FOOD);
                if(food!=null&&targetPlayer.canEat(food.canAlwaysEat())||player.getMainHandItem().is(DEPLOYABLE_DRINK)){player.setItemInHand(InteractionHand.MAIN_HAND,player.getMainHandItem().finishUsingItem(level,targetPlayer));return;}
            }
        }
        var origin=center.add(direction.scale(1.5+1d/64));var end=center.add(direction.scale(2.5-1d/64));var hit=level.clip(new ClipContext(origin,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        if(!hit.getBlockPos().equals(target))hit=new BlockHitResult(hit.getLocation(),hit.getDirection(),target,hit.isInside());
        var state=LoadedLevelAccess.blockState(level,target);if(!level.mayInteract(player,target))return;
        if(mode==DeployerBlockEntity.Mode.PUNCH){
            if(state.getShape(level,target).isEmpty()){clearBreaking(player);return;}
            if(AttackBlockCallback.EVENT.invoker().interact(player,level,InteractionHand.MAIN_HAND,target,hit.getDirection())!=InteractionResult.PASS)return;
            if(state.is(BlockTags.FIRE)){level.removeBlock(target,false);return;}
            state.attack(level,target,player);if(player.getMainHandItem().isEmpty())return;
            if(player.blockBreakingProgress!=null&&!target.equals(player.blockBreakingProgress.getKey()))clearBreaking(player);float previous=player.blockBreakingProgress==null?0:player.blockBreakingProgress.getValue();float progress=previous+state.getDestroyProgress(player,level,target)*16;
            level.playSound(null,target,state.getSoundType().getHitSound(),SoundSource.NEUTRAL,0.25f,1);
            if(progress>=1){player.gameMode.destroyBlock(target);clearBreaking(player);return;}
            if(progress<=0){clearBreaking(player);return;}if((int)(previous*10)!=(int)(progress*10))level.destroyBlockProgress(player.getId(),target,(int)(progress*10));player.blockBreakingProgress=org.apache.commons.lang3.tuple.Pair.of(target,progress);return;
        }
        if(player.getMainHandItem().is(Items.FLINT_AND_STEEL)&&state.isAir()){
            // Source convenience placement: click the adjacent support so native use creates fire
            // in the work cell. Protection is checked against that real cell before adjusting the hit.
            var permission=UseBlockCallback.EVENT.invoker().interact(player,level,InteractionHand.MAIN_HAND,hit);if(permission==InteractionResult.FAIL||permission.consumesAction())return;
            var face=hit.getDirection();if(!BaseFireBlock.canBePlacedAt(level,target,face))face=Direction.UP;
            var support=target.relative(face.getOpposite());if(!LoadedLevelAccess.isLoaded(level,support))return;
            player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(hit.getLocation(),face,support,hit.isInside())));return;
        }
        var usedItem=player.getMainHandItem().getItem();
        var result=player.gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
        if(result.consumesAction()){
            if(usedItem instanceof BlockItem block&&(block.getBlock() instanceof BaseRailBlock||block.getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock))player.placedTracks=true;
            return;
        }
        if(result==InteractionResult.FAIL)return;
        if(player.getMainHandItem().is(Items.ENDER_PEARL)||player.getMainHandItem().is(DEPLOYABLE_DRINK))return;
        player.gameMode.useItem(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND);
        if(!player.getUseItem().isEmpty())player.setItemInHand(InteractionHand.MAIN_HAND,player.getMainHandItem().finishUsingItem(level,player));player.stopUsingItem();
    }
    public static void clearBreaking(DeployerFakePlayer player){if(player.blockBreakingProgress!=null){player.level().destroyBlockProgress(player.getId(),player.blockBreakingProgress.getKey(),-1);player.blockBreakingProgress=null;}}
    private DeployerHandler(){}
}
