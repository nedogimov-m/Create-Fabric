package com.simibubi.create.content.processing.basin;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.foundation.fluid.ProcessingFluidStorage;
import com.simibubi.create.foundation.item.ProcessingInventory;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.EnumSet;
import net.minecraft.core.Direction;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import com.mojang.datafixers.util.Either;
import com.simibubi.create.content.processing.recipe.FluidIngredient;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;

public final class BasinBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    @Override public boolean addToGoggleTooltip(List<net.minecraft.network.chat.Component> tooltip,boolean sneaking){
        int start=tooltip.size();tooltip.add(com.simibubi.create.content.equipment.goggles.GoggleTooltip.translate("gui.goggles.basin_contents"));boolean empty=true;
        for(var slot:allItems()){if(slot.isResourceBlank())continue;empty=false;com.simibubi.create.content.equipment.goggles.GoggleTooltip.line(tooltip,slot.stack().getHoverName().copy().withStyle(net.minecraft.ChatFormatting.GRAY).append(net.minecraft.network.chat.Component.literal(" x"+slot.getAmount()).withStyle(net.minecraft.ChatFormatting.GREEN)),1);}
        for(var tank:fluids.allTanks()){if(tank.isResourceBlank())continue;empty=false;com.simibubi.create.content.equipment.goggles.GoggleTooltip.line(tooltip,net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.getName(tank.getResource()).copy().withStyle(net.minecraft.ChatFormatting.GRAY).append(" ").append(com.simibubi.create.content.equipment.goggles.GoggleTooltip.units(tank.getAmount()/81d,"millibuckets").withStyle(net.minecraft.ChatFormatting.BLUE)),1);}
        if(empty)tooltip.remove(start);return true;
    }
    public final ProcessingInventory inventory = new ProcessingInventory(9, 9, variant -> true, this::changed, true, true);
    public final ProcessingFluidStorage fluids = new ProcessingFluidStorage(2, 2, FluidConstants.BUCKET, this::changed);
    public final BasinSpoutBuffer spoutBuffer = new BasinSpoutBuffer(this);
    private final com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour filtering =
        new com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour(this).forRecipes().withCallback(stack -> changed());
    public com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour getFilter() { return filtering; }
    public boolean canContinueProcessing() { return spoutBuffer.isEmpty(); }
    public static final int OUTPUT_ANIMATION_TIME = 10;
    final List<net.createmod.catnip.data.IntAttached<ItemStack>> visualizedOutputItems =
        java.util.Collections.synchronizedList(new ArrayList<>());

    final List<net.createmod.catnip.data.IntAttached<com.simibubi.create.foundation.fluid.FluidStack>> visualizedOutputFluids =
        java.util.Collections.synchronizedList(new ArrayList<>());

    private void tickVisualizedOutputs() {
        visualizedOutputItems.forEach(net.createmod.catnip.data.IntAttached::decrement);
        visualizedOutputItems.removeIf(net.createmod.catnip.data.IntAttached::isOrBelowZero);
        visualizedOutputFluids.forEach(net.createmod.catnip.data.IntAttached::decrement);
        visualizedOutputFluids.removeIf(net.createmod.catnip.data.IntAttached::isOrBelowZero);
        var facing=getBlockState().getValue(BasinBlock.FACING);
        if(level==null||!level.isClientSide()||facing==Direction.DOWN||visualizedOutputFluids.isEmpty())return;
        var direction=net.minecraft.world.phys.Vec3.atLowerCornerOf(facing.getUnitVec3i());
        var origin=net.minecraft.world.phys.Vec3.atCenterOf(worldPosition).add(direction.scale(.65).subtract(0,.25,0));
        var motion=direction.scale(1/16f).add(0,-1/16f,0);
        for(int pass=0;pass<2;pass++)for(var event:List.copyOf(visualizedOutputFluids)){
            var particle=new com.simibubi.create.content.fluids.particle.FluidParticleData(com.simibubi.create.AllParticleTypes.FLUID_PARTICLE,event.getValue());
            var velocity=net.createmod.catnip.math.VecHelper.offsetRandomly(motion,level.getRandom(),1/16f);
            level.addAlwaysVisibleParticle(particle,origin.x,origin.y,origin.z,velocity.x,velocity.y,velocity.z);
        }
    }

    private long revision;
    private record CachedRecipe(RecipeManager manager, Object recipes, long revision, HeatCondition heat, int configFlags, RecipeHolder<BasinRecipe> recipe) {}
    private final Map<RecipeType<BasinRecipe>, CachedRecipe> cachedRecipes = new IdentityHashMap<>();
    private final EnumSet<Direction> disabledOutputs = EnumSet.noneOf(Direction.class);
    private Direction preferredOutput;

    public BasinBlockEntity(BlockPos pos, BlockState state) { super(AllBlockEntityTypes.BASIN, pos, state); }
    private void changed() {
        revision++;
        setChanged();
        getOperator().ifPresent(operator -> operator.basinChecker.scheduleUpdate());
        if (level instanceof ServerLevel) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }
    public List<ProcessingInventory.Slot> allItems() {
        var slots = new ArrayList<>(inventory.inputs); slots.addAll(inventory.outputs); return slots;
    }

    public boolean isEmpty() {
        return spoutBuffer.isEmpty() && allItems().stream().allMatch(slot -> slot.isResourceBlank())
                && fluids.allTanks().stream().allMatch(tank -> tank.isResourceBlank());
    }

    private java.util.Optional<BasinOperatingBlockEntity> getOperator() {
        if (level == null) return java.util.Optional.empty();
        var owner = com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level, worldPosition.above(2));
        return owner instanceof BasinOperatingBlockEntity operator ? java.util.Optional.of(operator) : java.util.Optional.empty();
    }

    public void notifyChangeOfContents() {
        changed();
    }

    public void onEmptied() {
        getOperator().ifPresent(operator -> operator.basinRemoved = true);
    }

    @Override public void remove() {
        super.remove();
        onEmptied();
    }

    public void onWrenched(Direction side) {
        if (side.getAxis().isVertical()) return;
        disabledOutputs.remove(side);
        if (getBlockState().getValue(BasinBlock.FACING) == side) {
            disabledOutputs.add(side);
            if (preferredOutput == side) preferredOutput = null;
        } else preferredOutput = side;
        updateOutput(); changed();
    }

    private boolean canOutput(Direction side) {
        if (level == null || side.getAxis().isVertical()) return false;
        var beside = worldPosition.relative(side);
        var target = beside.below();
        if (!level.isLoaded(beside) || !level.isLoaded(target) || !level.getBlockState(beside).getCollisionShape(level, beside).isEmpty()) return false;
        var block = level.getBlockState(target).getBlock();
        return block instanceof BasinBlock || block instanceof com.simibubi.create.content.logistics.depot.DepotBlock
                || block instanceof com.simibubi.create.content.kinetics.belt.BeltBlock
                && com.simibubi.create.content.kinetics.belt.BeltBlock.canTransportObjects(level.getBlockState(target));
    }

    private void updateOutput() {
        if (level == null || level.isClientSide()) return;
        Direction selected = Direction.DOWN;
        for (Direction side : Direction.Plane.HORIZONTAL) if (!disabledOutputs.contains(side) && canOutput(side)) selected = side;
        if (preferredOutput != null && !disabledOutputs.contains(preferredOutput) && canOutput(preferredOutput)) selected = preferredOutput;
        if (selected != getBlockState().getValue(BasinBlock.FACING)) {
            level.setBlockAndUpdate(worldPosition, getBlockState().setValue(BasinBlock.FACING, selected));
        }
    }

    private void exportOutputs() {
        var side = getBlockState().getValue(BasinBlock.FACING);
        if (!canOutput(side)) return;
        var target = worldPosition.relative(side).below();
        var items = com.simibubi.create.foundation.item.LoadedItemStorage.find(level, target, side.getOpposite());
        var fluidStorage = FluidStorage.SIDED.find(level, target, com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,target), com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,target), side.getOpposite());
        if (!canContinueProcessing()) return;
        try (Transaction transaction = Transaction.openOuter()) {
            var paidItems = new ArrayList<ItemStack>(); var paidFluids = new ArrayList<BasinSpoutBuffer.Fluid>();
            if (items != null) for (var slot : inventory.outputs) {
                if (slot.isResourceBlank()) continue;
                var stack = slot.stack();
                if (slot.extract(slot.getResource(), slot.getAmount(), transaction) != stack.getCount()) return;
                paidItems.add(stack);
            }
            if (fluidStorage != null) for (var tank : fluids.outputs) {
                if (tank.isResourceBlank()) continue;
                var fluid = new BasinSpoutBuffer.Fluid(tank.getResource(), tank.getAmount());
                if (tank.extract(fluid.variant(), fluid.amount(), transaction) != fluid.amount()) return;
                paidFluids.add(fluid);
            }
            spoutBuffer.append(paidItems, paidFluids, transaction);
            transaction.commit();
        }
    }

    public HeatCondition heat() {
        if (level == null) return HeatCondition.NONE;
        var below = level.getBlockState(worldPosition.below());
        if (!below.hasProperty(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL)) return HeatCondition.NONE;
        return switch (below.getValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL)) {
            case SEETHING -> HeatCondition.SUPERHEATED;
            case KINDLED, FADING -> HeatCondition.HEATED;
            default -> HeatCondition.NONE;
        };
    }
    public BasinRecipeInput recipeInput() {
        return new BasinRecipeInput(allItems().stream().map(ProcessingInventory.Slot::stack).toList(),
                fluids.allTanks().stream().map(t -> new BasinRecipeInput.FluidAmount(t.getResource(), t.getAmount())).toList(), heat());
    }
    public RecipeHolder<BasinRecipe> findRecipe(RecipeType<BasinRecipe> type) {
        if (!(level instanceof ServerLevel server) || !canContinueProcessing()) return null;
        var manager = server.getServer().getRecipeManager();
        var cached = cachedRecipes.get(type);
        var heat = heat();
        var config=com.simibubi.create.infrastructure.config.AllConfigs.server().recipes;int configFlags=(config.allowBrewingInMixer.get()?1:0)|(config.allowShapelessInMixer.get()?2:0)|(config.allowShapedSquareInPress.get()?4:0);
        var recipes = manager.getRecipes();
        if (cached != null && cached.manager() == manager && cached.recipes() == recipes && cached.revision() == revision && cached.heat() == heat && cached.configFlags()==configFlags) return cached.recipe();
        var input = recipeInput();
        RecipeHolder<BasinRecipe> recipe = null;
        for (var holder : manager.getRecipes()) if (holder.value() instanceof BasinRecipe candidate
                && candidate.getType() == type && candidate.matches(input, level) && matchesFilter(candidate)) {
            recipe = new RecipeHolder<>(holder.id(), candidate); break;
        }
        if(recipe==null&&config.allowBrewingInMixer.get()&&type==com.simibubi.create.AllRecipeTypes.MIXING)recipe=com.simibubi.create.content.fluids.potion.PotionMixingRecipes.findRecipe(server,input);
        if (recipe != null && !matchesFilter(recipe.value())) recipe = null;
        if (recipe == null) recipe = findCraftingRecipe(manager, type, input);
        cachedRecipes.put(type, new CachedRecipe(manager, recipes, revision, heat, configFlags, recipe));
        return recipe;
    }

    private RecipeHolder<BasinRecipe> findCraftingRecipe(RecipeManager manager, RecipeType<BasinRecipe> type, BasinRecipeInput input) {
        boolean mixing = type == com.simibubi.create.AllRecipeTypes.MIXING;
        var config=com.simibubi.create.infrastructure.config.AllConfigs.server().recipes;
        if(mixing?!config.allowShapelessInMixer.get():!config.allowShapedSquareInPress.get())return null;
        for (var holder : manager.getRecipes()) {
            if (!(holder.value() instanceof CraftingRecipe crafting)) continue;
            // Vanilla shaped/shapeless recipes have a static result. Special crafting needs its own adapter.
            if (crafting.getClass() != ShapedRecipe.class && crafting.getClass() != ShapelessRecipe.class) continue;
            var ingredients = crafting.placementInfo().ingredients();
            if (ingredients.size() < 2 || ingredients.size() > 9) continue;
            boolean compressed = (ingredients.size() == 4 || ingredients.size() == 9)
                    && ingredients.stream().allMatch(ingredients.getFirst()::equals);
            if (mixing ? !(crafting instanceof ShapelessRecipe) || compressed&&config.allowShapedSquareInPress.get() : !compressed) continue;
            var result = crafting.assemble(CraftingInput.EMPTY);
            if (result.isEmpty()) continue;
            var converted = new BasinRecipe(mixing,
                    ingredients.stream().map(i -> Either.<Ingredient, FluidIngredient>left(i)).toList(),
                    List.of(Either.left(new ProcessingOutput(result.getItem(), result.getCount(), result.getComponentsPatch(), 1))),
                    HeatCondition.NONE, 100);
            if (converted.matches(input, level) && matchesFilter(converted)) return new RecipeHolder<>(holder.id(), converted);
        }
        return null;
    }

    /** Consumption, remainders and both output kinds commit together or roll back together. */
    public boolean process(BasinRecipe recipe, boolean simulate) {
        if (level == null || level.isClientSide() || !canContinueProcessing() || !matchesFilter(recipe)) return false;
        var input = recipeInput();
        var plan = recipe.consumption(input);
        if (plan == null) return false;
        var slots = allItems();
        var tanks = fluids.allTanks();
        var remainders = new ArrayList<ItemStack>();
        try (Transaction transaction = Transaction.openOuter()) {
            for (int slot = 0; slot < slots.size(); slot++) {
                long amount = plan.items()[slot];
                if (amount == 0) continue;
                var stored = slots.get(slot);
                var remainder = stored.getResource().getItem().getCraftingRemainder();
                if (remainder != null) for (int i = 0; i < amount; i++) remainders.add(remainder.create());
                if (stored.extract(stored.getResource(), amount, transaction) != amount) return false;
            }
            for (int tank = 0; tank < tanks.size(); tank++) {
                long amount = plan.fluids()[tank];
                if (amount != 0 && tanks.get(tank).extract(tanks.get(tank).getResource(), amount, transaction) != amount) return false;
            }
            // Check maximum possible outputs without using RNG. Full outputs must not reroll recipes.
            try (Transaction capacityCheck = transaction.openNested()) {
                var maximumItems = new ArrayList<ItemStack>(remainders);
                var maximumFluids = new ArrayList<BasinSpoutBuffer.Fluid>();
                for (var result : recipe.results()) result.ifLeft(item -> maximumItems.add(item.stack()))
                    .ifRight(fluid -> maximumFluids.add(new BasinSpoutBuffer.Fluid(fluid.variant(), fluid.amount())));
                if (!routeOutputs(maximumItems, maximumFluids, capacityCheck)) return false;
            }
            if (simulate) return true;
            var paidItems = new ArrayList<ItemStack>(remainders); var paidFluids = new ArrayList<BasinSpoutBuffer.Fluid>();
            for (var result : recipe.results()) result.ifLeft(item -> paidItems.add(item.rollOutput(level.getRandom())))
                .ifRight(fluid -> paidFluids.add(new BasinSpoutBuffer.Fluid(fluid.variant(), fluid.amount())));
            if (!routeOutputs(paidItems, paidFluids, transaction)) return false;
            transaction.commit();
            return true;
        }
    }

    private boolean matchesFilter(BasinRecipe recipe) {
        var firstItem = recipe.results().stream().flatMap(result -> result.left().stream()).findFirst();
        if (firstItem.isPresent()) return filtering.test(firstItem.get().stack());
        var firstFluid = recipe.results().stream().flatMap(result -> result.right().stream()).findFirst();
        return firstFluid.isEmpty() || filtering.test(new com.simibubi.create.foundation.fluid.FluidStack(firstFluid.get().variant(), firstFluid.get().amount()));
    }
    private boolean routeOutputs(List<ItemStack> outputItems, List<BasinSpoutBuffer.Fluid> outputFluids,
                                 net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
        var side = getBlockState().getValue(BasinBlock.FACING);
        if (side == Direction.DOWN) {
            for (var stack : outputItems) if (!inventory.insertOutput(stack, transaction)) return false;
            for (var fluid : outputFluids) if (!fluids.insertOutput(fluid.variant(), fluid.amount(), transaction)) return false;
            return true;
        }
        var target = worldPosition.relative(side).below();
        if (!com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(level, target)) return false;
        var be = com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level, target);
        var targetItems = com.simibubi.create.foundation.item.LoadedItemStorage.find(level, target, side.getOpposite());
        if (outputItems.stream().anyMatch(stack -> !stack.isEmpty()) && targetItems == null) return false;
        var targetFluids = FluidStorage.SIDED.find(level, target, com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level, target), be, side.getOpposite());
        if (targetFluids == null) for (var fluid : outputFluids)
            if (!fluids.insertOutput(fluid.variant(), fluid.amount(), transaction)) return false;
        spoutBuffer.append(outputItems, targetFluids == null ? List.of() : outputFluids, transaction);
        return true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BasinBlockEntity basin) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, basin)) {
            if (!createTick.shouldTick()) return;

        if (level.isClientSide()) { basin.tickVisualizedOutputs(); return; }
        basin.spoutBuffer.flush();
        if (level.getGameTime() % 5 == 0) { basin.updateOutput(); basin.exportOutputs(); }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(0, 0.25, 0))) {
            if (item.isRemoved() || item.getY() < pos.getY() + 0.1) continue;
            var stack = item.getItem();
            try (Transaction transaction = Transaction.openOuter()) {
                long inserted = basin.inventory.automation.insert(ItemVariant.of(stack), stack.getCount(), transaction);
                if (inserted == 0) continue;
                transaction.commit();
                stack.shrink((int) inserted);
                if (stack.isEmpty()) item.discard(); else item.setItem(stack);
            }
        }
    
        }
    }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        spoutBuffer.captureRemoved();
        if (level != null && !level.isClientSide()) allItems().forEach(slot -> Block.popResource(level, worldPosition, slot.stack()));
        super.preRemoveSideEffects(pos, state);
    }
    @Override protected void write(ValueOutput output, boolean clientPacket) {
        super.write(output, clientPacket); inventory.save(output.child("Inventory")); fluids.save(output.child("Fluids")); spoutBuffer.save(output);
        if (preferredOutput != null) output.store("PreferredSpoutput", Direction.CODEC, preferredOutput);
        output.store("DisabledSpoutput", Direction.CODEC.listOf(), List.copyOf(disabledOutputs));
        if(clientPacket){
            output.store("VisualizedItems",ItemStack.OPTIONAL_CODEC.listOf(),visualizedOutputItems.stream().map(net.createmod.catnip.data.IntAttached::getValue).toList());
            var events=output.childrenList("VisualizedFluids");for(var event:List.copyOf(visualizedOutputFluids)){
                var row=events.addChild();row.store("variant",net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.CODEC,event.getValue().variant());row.putLong("amount",event.getValue().getAmount());
            }
            visualizedOutputItems.clear();visualizedOutputFluids.clear();
        }
    }
    @Override protected void read(ValueInput input, boolean clientPacket) {
        super.read(input, clientPacket); inventory.load(input.childOrEmpty("Inventory")); fluids.load(input.childOrEmpty("Fluids")); spoutBuffer.load(input);
        // Original source client event snapshots; virtual Ponder loads use ordinary native ValueInput.
        if (clientPacket || isVirtual()) input.read("VisualizedItems", ItemStack.OPTIONAL_CODEC.listOf())
            .ifPresent(stacks -> stacks.forEach(stack -> visualizedOutputItems.add(
                net.createmod.catnip.data.IntAttached.with(OUTPUT_ANIMATION_TIME, stack.copy()))));
        if(clientPacket||isVirtual())for(var event:input.childrenListOrEmpty("VisualizedFluids")){
            var variant=event.read("variant",net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.CODEC).orElse(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.blank());long amount=event.getLongOr("amount",0);
            if(!variant.isBlank()&&amount>0)visualizedOutputFluids.add(net.createmod.catnip.data.IntAttached.with(OUTPUT_ANIMATION_TIME,new com.simibubi.create.foundation.fluid.FluidStack(variant,amount)));
        }
        revision++; cachedRecipes.clear();
        preferredOutput = input.read("PreferredSpoutput", Direction.CODEC).filter(side -> side.getAxis().isHorizontal()).orElse(null);
        disabledOutputs.clear();
        input.read("DisabledSpoutput", Direction.CODEC.listOf()).orElse(List.of()).stream().filter(side -> side.getAxis().isHorizontal()).forEach(disabledOutputs::add);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return super.getUpdateTag(registries); }
    private final com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour directBeltInput = com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.createNative(this);
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (filtering != null) behaviours.add(filtering);
        if (directBeltInput != null) behaviours.add(directBeltInput);
    }
}
