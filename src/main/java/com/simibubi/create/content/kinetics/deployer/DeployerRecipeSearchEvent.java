package com.simibubi.create.content.kinetics.deployer;

import java.util.Optional;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Fabric counterpart of Create's prioritized, cancellable deployer recipe search. */
public final class DeployerRecipeSearchEvent {
    @FunctionalInterface public interface Callback {void onSearch(DeployerRecipeSearchEvent search);}
    public static final Event<Callback> EVENT=EventFactory.createArrayBacked(Callback.class,listeners->search->{for(var listener:listeners)listener.onSearch(search);});
    private final DeployerBlockEntity blockEntity;
    private final ItemApplicationRecipe.Input inventory;
    private DeployerRecipes.Match recipe;
    private int maxPriority;
    private boolean canceled;
    public DeployerRecipeSearchEvent(DeployerBlockEntity blockEntity,ItemApplicationRecipe.Input inventory){this.blockEntity=blockEntity;this.inventory=inventory;}
    public DeployerBlockEntity getBlockEntity(){return blockEntity;}
    public ItemApplicationRecipe.Input getInventory(){return inventory;}
    public boolean isCanceled(){return canceled;}
    public void setCanceled(boolean canceled){this.canceled=canceled;}
    public boolean shouldAddRecipeWithPriority(int priority){return !canceled&&priority>maxPriority;}
    public DeployerRecipes.Match getRecipe(){return canceled?null:recipe;}
    public void addRecipe(Supplier<Optional<DeployerRecipes.Match>> supplier,int priority){if(shouldAddRecipeWithPriority(priority))supplier.get().ifPresent(match->{recipe=match;maxPriority=priority;});}
}
