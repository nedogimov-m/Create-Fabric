package com.simibubi.create.content.kinetics.deployer;

import com.simibubi.create.*;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.advancement.CreateAdvancement;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.Optional;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;

/** Data-driven in-world item application, after normal Fabric interaction guards. */
public final class ManualApplicationRecipe {
    public static void register(){
        var phase=Create.asResource("manual_application");UseBlockCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE,phase);
        com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register(phase,(player,level,hand,hit)->apply(player,level,hand,hit.getBlockPos()));
    }
    private static Optional<ItemApplicationRecipe> find(Level level,ItemApplicationRecipe.Input input){
        if(level instanceof ServerLevel server)return server.getServer().getRecipeManager().getRecipeFor(AllRecipeTypes.ITEM_APPLICATION,input,level).map(holder->holder.value());
        return level.recipeAccess().getSynchronizedRecipes().getFirstMatch(AllRecipeTypes.ITEM_APPLICATION,input,level).map(holder->holder.value());
    }
    private static InteractionResult apply(Player player,Level level,InteractionHand hand,BlockPos pos){
        var held=player.getItemInHand(hand);if(held.isEmpty()||player.isSpectator()||!player.mayBuild()||!level.isLoaded(pos)||!level.mayInteract(player,pos))return InteractionResult.PASS;
        var state=LoadedLevelAccess.blockState(level,pos);if(state.isAir())return InteractionResult.PASS;
        var found=find(level,new ItemApplicationRecipe.Input(new ItemStack(state.getBlock()),held));if(found.isEmpty())return InteractionResult.PASS;if(level.isClientSide())return InteractionResult.SUCCESS;
        var recipe=found.get();var main=recipe.results().getFirst();var output=main.rollOutput(level.getRandom());var replacement=output.getItem() instanceof BlockItem block?block.getBlock().withPropertiesOf(state):Blocks.AIR.defaultBlockState();
        level.destroyBlock(pos,false);boolean placed=level.setBlock(pos,replacement,Block.UPDATE_ALL);
        level.playSound(null,pos,SoundEvents.COPPER_BREAK,SoundSource.PLAYERS,1,1.45f);
        int start=main.item() instanceof BlockItem?1:0;for(int i=start;i<recipe.results().size();i++)Block.popResource(level,pos,recipe.results().get(i).rollOutput(level.getRandom()));
        if(!recipe.keepHeldItem()&&!player.isCreative()&&!held.has(net.minecraft.core.component.DataComponents.UNBREAKABLE)){
            if(held.isDamageableItem())held.hurtAndBreak(1,player,hand);
            else {var remainder=held.getItem().getCraftingRemainder();held.shrink(1);if(remainder!=null){if(held.isEmpty())player.setItemInHand(hand,remainder.create());else player.getInventory().placeItemBackInInventory(remainder.create(),net.minecraft.util.Prediction.SERVER_ONLY);}}
        }
        if(placed&&LoadedLevelAccess.blockState(level,pos).is(replacement.getBlock())&&!(player instanceof FakePlayer)){
            var advancement=casingAdvancement(output);if(advancement!=null)advancement.awardTo(player);
        }
        return InteractionResult.SUCCESS;
    }
    /** Original manual/deployer output event; owning the item alone is not this criterion. */
    static CreateAdvancement casingAdvancement(ItemStack created){
        if(created.is(AllBlocks.ANDESITE_CASING.asItem()))return AllAdvancements.ANDESITE_CASING;
        if(created.is(AllBlocks.BRASS_CASING.asItem()))return AllAdvancements.BRASS_CASING;
        if(created.is(AllBlocks.COPPER_CASING.asItem()))return AllAdvancements.COPPER_CASING;
        if(created.is(AllBlocks.RAILWAY_CASING.asItem()))return AllAdvancements.TRAIN_CASING;
        return null;
    }
    private ManualApplicationRecipe(){}
}
