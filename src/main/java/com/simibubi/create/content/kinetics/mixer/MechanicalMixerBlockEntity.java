package com.simibubi.create.content.kinetics.mixer;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class MechanicalMixerBlockEntity extends BasinOperatingBlockEntity {
    private static final Object shapelessOrMixingRecipesKey = new Object();
    public static final int MIN_SPEED = 30;
    @Override public float getMinimumRequiredSpeed(){return com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.mediumSpeed.getF();}
    private boolean running;
    private boolean originalProcessing;
    private int runningTicks;
    private int processingTicks = -1;
    private ResourceKey<Recipe<?>> activeRecipe;

    public MechanicalMixerBlockEntity(BlockPos pos, BlockState state) { this(AllBlockEntityTypes.MECHANICAL_MIXER, pos, state); }
    public MechanicalMixerBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public boolean isRunning() { return running; }
    public int getRunningTicks() { return runningTicks; }
    public float getHeadOffset(float partialTick) {
        if (!running) return 7f / 16f;
        float progress = runningTicks < 20 ? runningTicks + partialTick : runningTicks == 20 ? 20 : 40 - runningTicks - partialTick;
        return (1 - (float) Math.cos(Math.clamp(progress, 0, 20) / 20f * Math.PI)) / 2 + 7f / 16f;
    }
    public float getHeadAngle(float partialTick) {
        float multiplier = !running ? 0.5f : runningTicks >= 15 && runningTicks <= 20 ? 2 : 1;
        return getAngle(partialTick) * multiplier * 2;
    }
    private BasinBlockEntity basin() {
        return getBasin().orElse(null);
    }
    private void stop() { running = false; originalProcessing = false; runningTicks = 0; processingTicks = -1; activeRecipe = null; currentRecipe = null; basinChecker.scheduleUpdate(); setChanged(); sync(); }

    @Override public void startProcessingBasin() {
        if (running && runningTicks <= 20) return;
        if (isVirtual()) {
            running = true; runningTicks = 0; processingTicks = -1;
            return;
        }
        var recipe = findBasinRecipe();
        if (recipe == null) return;
        currentRecipe = recipe.value(); activeRecipe = recipe.id();
        running = true; runningTicks = 0; processingTicks = -1;
    }
    @Override public boolean continueWithPreviousRecipe() { runningTicks = 20; return true; }
    @Override protected void onBasinRemoved() { stop(); }
    @Override protected Object getRecipeCacheKey() { return shapelessOrMixingRecipesKey; }
    @Override protected boolean matchStaticFilters(net.minecraft.world.item.crafting.RecipeHolder<? extends Recipe<?>> recipe) {
        return recipe.value().getType() == AllRecipeTypes.MIXING;
    }
    @Override protected java.util.List<Recipe<?>> getMatchingRecipes() {
        var recipes = super.getMatchingRecipes();
        var converted = getBasin().map(basin -> basin.findRecipe(AllRecipeTypes.MIXING)).orElse(null);
        if (converted != null && !recipes.contains(converted.value()) && matchBasinRecipe(converted.value())) recipes.add(converted.value());
        sortMatchingRecipes(recipes);
        return recipes;
    }
    @Override protected java.util.Optional<com.simibubi.create.foundation.advancement.CreateAdvancement> getProcessedRecipeTrigger() {
        return java.util.Optional.of(com.simibubi.create.foundation.advancement.AllAdvancements.MIXER);
    }

    @Override public void tick() {
        if (level == null) super.tick();
        else tick(level, worldPosition, getBlockState(), this);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MechanicalMixerBlockEntity mixer) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, mixer)) {
            if (!createTick.shouldTick()) return;
        if (mixer.handleBasinRemoval()) return;

        KineticBlockEntity.tick(level, pos, state, mixer);
        if (mixer.isVirtual()) { mixer.tickVirtualMixing(); return; }
        if (level.isClientSide()) {
            if (mixer.running && mixer.runningTicks == 20) mixer.renderParticles();
            if (mixer.running && mixer.runningTicks != 20) mixer.runningTicks = Math.min(39, mixer.runningTicks + 1);
            if (mixer.running && mixer.runningTicks == 20 && level.getGameTime() % 4 == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, AllSoundEvents.MIXING, SoundSource.BLOCKS, 0.75f, 1, false);
            }
            return;
        }
        var basin = mixer.basin();
        if (!mixer.running) {
            if (basin == null || Math.abs(mixer.getSpeed()) < mixer.getMinimumRequiredSpeed() || level.getGameTime() % 5 != 0) return;
            mixer.updateBasin();
            if (!mixer.running) return;
        }
        if (basin == null) { mixer.stop(); return; }
        if (Math.abs(mixer.getSpeed()) < mixer.getMinimumRequiredSpeed()) {
            if (mixer.runningTicks < 20) mixer.runningTicks = 40 - mixer.runningTicks;
            else if (mixer.runningTicks == 20) mixer.runningTicks++;
        }
        if (mixer.runningTicks == 20) {
            var recipe = mixer.findBasinRecipe();
            if(mixer.originalProcessing&&mixer.activeRecipe==null&&recipe!=null&&basin.process(recipe.value(),true)){
                // Original currentRecipe/processingTicks were transient. Adopt only an actual
                // currently payable basin recipe; this creates no historical output/payment.
                mixer.activeRecipe=recipe.id();mixer.currentRecipe=recipe.value();mixer.processingTicks=-1;mixer.originalProcessing=false;mixer.setChanged();
            }
            if (recipe == null || !recipe.id().equals(mixer.activeRecipe) || !basin.process(recipe.value(), true)) {
                mixer.runningTicks++; mixer.sync();
            } else if (mixer.processingTicks < 0) {
                int log = 31 - Integer.numberOfLeadingZeros(Math.max(1, (int) (512 / Math.abs(mixer.getSpeed()))));
                mixer.processingTicks = Math.max(1, log * (int) Math.ceil(recipe.value().processingTime() / 100f * 15) + 1);
            } else if (--mixer.processingTicks <= 0) {
                mixer.currentRecipe = recipe.value();
                if (!mixer.applyBasinRecipeTransaction()) { mixer.runningTicks++; mixer.sync(); return; }
                mixer.processingTicks = -1;
                var next = mixer.findBasinRecipe();
                if (next == null || !next.id().equals(mixer.activeRecipe) || !basin.process(next.value(), true)) mixer.runningTicks++;
                mixer.sync();
            }
        } else mixer.runningTicks++;
        if (mixer.runningTicks >= 40) mixer.stop();
        mixer.setChanged();
    
        }
    }
    /** The genuine source null-recipe virtual animation uses the canonical renderer fields. */
    private void tickVirtualMixing() {
        if (runningTicks >= 40) { stop(); return; }
        if (!running) return;
        float speed = Math.abs(getSpeed());
        if (runningTicks == 20) renderParticles();
        if (speed == 0 || !isSpeedRequirementFulfilled()) {
            if (runningTicks < 20) runningTicks = 40 - runningTicks;
            else if (runningTicks == 20) runningTicks++;
        }
        if (runningTicks == 20) {
            if (processingTicks < 0) {
                int logarithm = 31 - Integer.numberOfLeadingZeros(Math.max(1, (int) (512 / speed)));
                processingTicks = Math.max(logarithm * 15 + 1, 1);
                var basin = basin();
                if (basin != null && basin.fluids.allTanks().stream().anyMatch(tank -> !tank.isResourceBlank()))
                    level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,
                        SoundSource.BLOCKS, .75f, speed < 65 ? .75f : 1.5f);
            } else if (--processingTicks == 0) {
                runningTicks++; processingTicks = -1;
            }
        }
        if (runningTicks != 20) runningTicks++;
    }

    /** Complete original particle emission, reading the actual native basin slots/tanks. */
    public void renderParticles() {
        var basin = basin();
        if (basin == null || level == null || !level.isClientSide()) return;
        for (var slot : basin.allItems()) if (!slot.isResourceBlank())
            spillParticle(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM,
                net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(slot.stack())));
        for (var tank : basin.fluids.allTanks()) if (!tank.isResourceBlank())
            spillParticle(new com.simibubi.create.content.fluids.particle.FluidParticleData(
                com.simibubi.create.AllParticleTypes.FLUID_PARTICLE,
                new com.simibubi.create.foundation.fluid.FluidStack(tank.getResource(), tank.getAmount())));
    }

    protected void spillParticle(net.minecraft.core.particles.ParticleOptions data) {
        float angle = level.getRandom().nextFloat() * 360;
        var offset = net.createmod.catnip.math.VecHelper.rotate(new net.minecraft.world.phys.Vec3(0, 0, .25f), angle, net.minecraft.core.Direction.Axis.Y);
        var target = net.createmod.catnip.math.VecHelper.rotate(offset, getSpeed() > 0 ? 25 : -25, net.minecraft.core.Direction.Axis.Y).add(0, .25f, 0);
        var center = offset.add(net.createmod.catnip.math.VecHelper.getCenterOf(worldPosition));
        target = net.createmod.catnip.math.VecHelper.offsetRandomly(target.subtract(offset), level.getRandom(), 1 / 128f);
        level.addParticle(data, center.x, center.y - 1.75f, center.z, target.x, target.y, target.z);
    }

    @Override protected void write(ValueOutput output, boolean clientPacket) {
        super.write(output, clientPacket); output.putBoolean("Running", running); output.putInt("Ticks", runningTicks);
        output.putInt("ProcessingTicks", processingTicks); output.putBoolean("OriginalProcessing",originalProcessing);
        if (activeRecipe != null) output.store("ActiveRecipe", ResourceKey.codec(net.minecraft.core.registries.Registries.RECIPE), activeRecipe);
    }
    @Override protected void read(ValueInput input, boolean clientPacket) {
         running = input.getBooleanOr("Running", false); runningTicks = input.getIntOr("Ticks", 0); originalProcessing=input.getBooleanOr("OriginalProcessing",false);
        processingTicks = Math.clamp(input.getIntOr("ProcessingTicks", -1), -1, 1000000);
        activeRecipe = input.read("ActiveRecipe", ResourceKey.codec(net.minecraft.core.registries.Registries.RECIPE)).orElse(null);
    
        super.read(input, clientPacket);
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("mechanical_mixer");
    }
}
