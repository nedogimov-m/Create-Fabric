package com.simibubi.create.content.processing.basin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.advancement.CreateAdvancement;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.simple.DeferralBehaviour;
import com.simibubi.create.foundation.recipe.RecipeFinder;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Genuine basin operating lifecycle over the single existing transaction-backed basin owner. */
public abstract class BasinOperatingBlockEntity extends KineticBlockEntity {
	public DeferralBehaviour basinChecker;
	public boolean basinRemoved;
	protected Recipe<?> currentRecipe;

	public BasinOperatingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		basinChecker = new DeferralBehaviour(this, this::updateBasin);
		behaviours.add(basinChecker);
	}

	@Override
	public void onSpeedChanged(float previousSpeed) {
		super.onSpeedChanged(previousSpeed);
		basinChecker.scheduleUpdate();
	}

	@Override
	public void tick() {
		if (!handleBasinRemoval())
			super.tick();
	}

	/** The real native static owner tick also consumes this source lifecycle state. */
	protected final boolean handleBasinRemoval() {
		if (!basinRemoved)
			return false;
		basinRemoved = false;
		currentRecipe = null;
		onBasinRemoved();
		basinChecker.scheduleUpdate();
		sendData();
		return true;
	}

	protected boolean updateBasin() {
		if (basinRemoved || !isSpeedRequirementFulfilled() || getSpeed() == 0 || isRunning()
				|| !(level instanceof ServerLevel) || getBasin().isEmpty())
			return true;
		var recipes = getMatchingRecipes();
		if (recipes.isEmpty())
			return true;
		currentRecipe = recipes.getFirst();
		startProcessingBasin();
		sendData();
		return true;
	}

	protected abstract boolean isRunning();

	public void startProcessingBasin() {
	}

	public boolean continueWithPreviousRecipe() {
		return true;
	}

	protected <I extends RecipeInput> boolean matchBasinRecipe(Recipe<I> recipe) {
		if (!(recipe instanceof BasinRecipe processing))
			return false;
		return getBasin().filter(basin -> basin.process(processing, true)).isPresent();
	}

	protected void applyBasinRecipe() {
		applyBasinRecipeTransaction();
	}

	/** A boolean result for the native press; all consumption/RNG/remainders remain in Basin.process. */
	protected final boolean applyBasinRecipeTransaction() {
		if (!(currentRecipe instanceof BasinRecipe processing))
			return false;
		var optional = getBasin();
		if (optional.isEmpty())
			return false;
		var basin = optional.get();
		if (!basin.process(processing, false))
			return false;
		getProcessedRecipeTrigger().ifPresent(this::award);
		if (matchBasinRecipe(currentRecipe)) {
			continueWithPreviousRecipe();
			sendData();
		}
		basin.notifyChangeOfContents();
		return true;
	}

	protected List<Recipe<?>> getMatchingRecipes() {
		var optional = getBasin();
		if (optional.isEmpty() || optional.get().isEmpty())
			return new ArrayList<>();
		var matches = new ArrayList<Recipe<?>>();
		for (var holder : RecipeFinder.get(getRecipeCacheKey(), level, this::matchStaticFilters))
			if (matchBasinRecipe(holder.value()))
				matches.add(holder.value());
		sortMatchingRecipes(matches);
		return matches;
	}

	protected final void sortMatchingRecipes(List<Recipe<?>> recipes) {
		recipes.sort(Comparator.comparingInt(BasinOperatingBlockEntity::ingredientCount).reversed());
	}

	private static int ingredientCount(Recipe<?> recipe) {
		return recipe instanceof BasinRecipe basin
			? (int) basin.ingredients().stream().filter(ingredient -> ingredient.left().isPresent()).count()
			: recipe.placementInfo().ingredients().size();
	}

	/** Preserve real recipe keys for the existing mixer save/reload/reload-generation checks. */
	protected final RecipeHolder<BasinRecipe> findBasinRecipe() {
		if (!(level instanceof ServerLevel server))
			return null;
		for (var recipe : getMatchingRecipes()) {
			if (!(recipe instanceof BasinRecipe processing))
				continue;
			for (var holder : server.getServer().getRecipeManager().getRecipes())
				if (holder.value() == processing)
					return new RecipeHolder<>(holder.id(), processing);
			// Existing native brewing/vanilla-crafting conversions retain their real source key.
			var converted = getBasin().map(basin -> basin.findRecipe(processing.getType())).orElse(null);
			if (converted != null && converted.value().equals(processing))
				return converted;
		}
		return null;
	}

	protected abstract void onBasinRemoved();

	protected Optional<BasinBlockEntity> getBasin() {
		if (level == null)
			return Optional.empty();
		var owner = LoadedLevelAccess.blockEntity(level, worldPosition.below(2));
		return owner instanceof BasinBlockEntity basin ? Optional.of(basin) : Optional.empty();
	}

	protected Optional<CreateAdvancement> getProcessedRecipeTrigger() {
		return Optional.empty();
	}

	protected abstract boolean matchStaticFilters(RecipeHolder<? extends Recipe<?>> recipe);

	protected abstract Object getRecipeCacheKey();
}
