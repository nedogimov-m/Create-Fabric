package com.simibubi.create;

import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.*;

public final class AllOpenPipeEffectHandlers {
    private AllOpenPipeEffectHandlers(){}
    public static void registerDefaults(){
        OpenPipeEffectHandler.REGISTRY.registerProvider(SimpleRegistry.Provider.forFluidTag(FluidTags.WATER,(level,area,fluid)->{
            if(level.getGameTime()%5!=0)return;
            for(var entity:level.getEntities((Entity)null,area,Entity::isOnFire))entity.clearFire();
            for(var pos:BlockPos.betweenClosed(area)){
                if(!level.isLoaded(pos))continue;var state=LoadedLevelAccess.blockState(level,pos);
                if(state.is(BlockTags.FIRE))level.removeBlock(pos,false);
                else if(AbstractCandleBlock.isLit(state))AbstractCandleBlock.extinguish(null,state,level,pos);
                else if(CampfireBlock.isLitCampfire(state)){level.levelEvent(LevelEvent.SOUND_EXTINGUISH_FIRE,pos,0);CampfireBlock.douse(null,level,pos,state);level.setBlockAndUpdate(pos,state.setValue(CampfireBlock.LIT,false));}
            }
        }));
        OpenPipeEffectHandler.REGISTRY.registerProvider(SimpleRegistry.Provider.forFluidTag(FluidTags.LAVA,(level,area,fluid)->{
            if(level.getGameTime()%5!=0)return;for(var entity:level.getEntities((Entity)null,area,entity->!entity.fireImmune()))entity.igniteForSeconds(3);
        }));
        OpenPipeEffectHandler.REGISTRY.registerProvider(SimpleRegistry.Provider.forFluidTag(TagKey.create(Registries.FLUID,net.minecraft.resources.Identifier.fromNamespaceAndPath("c","milk")),(level,area,fluid)->{
            if(level.getGameTime()%5!=0)return;
            for(var entity:level.getEntitiesOfClass(LivingEntity.class,area,LivingEntity::isAffectedByPotions))
                for(var effect:net.minecraft.world.item.component.Consumables.MILK_BUCKET.onConsumeEffects())effect.apply(level,new ItemStack(Items.MILK_BUCKET),entity);
        }));
        OpenPipeEffectHandler potion=(level,area,fluid)->{
            var contents=fluid.variant().getComponents().getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY);
            for(var entity:level.getEntitiesOfClass(LivingEntity.class,area,LivingEntity::isAffectedByPotions))for(var effect:contents.getAllEffects()){
                var type=effect.getEffect().value();
                if(type.isInstantaneous()&&level instanceof net.minecraft.server.level.ServerLevel server)type.applyInstantaneousEffect(server,null,null,entity,effect.getAmplifier(),0.5);
                else entity.addEffect(new MobEffectInstance(effect));
            }
        };
        OpenPipeEffectHandler tea=(level,area,fluid)->{
            if(level.getGameTime()%5!=0)return;for(var entity:level.getEntitiesOfClass(LivingEntity.class,area,LivingEntity::isAffectedByPotions))entity.addEffect(new MobEffectInstance(MobEffects.HASTE,21,0,false,false,false));
        };
        OpenPipeEffectHandler.REGISTRY.register(AllFluids.POTION,potion);OpenPipeEffectHandler.REGISTRY.register(AllFluids.FLOWING_POTION,potion);
        OpenPipeEffectHandler.REGISTRY.register(AllFluids.TEA,tea);OpenPipeEffectHandler.REGISTRY.register(AllFluids.FLOWING_TEA,tea);
    }
}
