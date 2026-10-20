package com.simibubi.create.content.kinetics.saw;

import com.simibubi.create.*;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.content.kinetics.base.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import java.util.*;

public final class SawBlockEntity extends BlockBreakingKineticBlockEntity {
    public final SawInventory inventory = new SawInventory(this);
    private ItemStack playEvent=ItemStack.EMPTY;
    private final com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour filtering =
        new com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour(this).forRecipes().withCallback(stack -> contentsChanged());
    private static final TagKey<RecipeSerializer<?>> AUTOMATION_IGNORE = TagKey.create(Registries.RECIPE_SERIALIZER, Create.asResource("automation_ignore"));
    public SawBlockEntity(BlockPos pos, BlockState state) { super(AllBlockEntityTypes.SAW, pos, state); }
    public void contentsChanged() { setChanged(); if (level instanceof ServerLevel) sync(); }
    public ItemStack getFilter() { return filtering.getFilter().copy(); }
    public com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour getFilteringBehaviour() { return filtering; }
    public void setFilter(ItemStack stack) { filtering.setFilter(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1)); }
    public boolean canProcess() { return getBlockState().getValue(SawBlock.FACING) == Direction.UP; }
    public Storage<ItemVariant> storage(Direction side) { return canProcess() && side != Direction.DOWN ? inventory : null; }
    public Direction getItemMovementFacing() { return getBlockState().getValue(SawBlock.AXIS_ALONG_FIRST_COORDINATE) ? getSpeed() < 0 ? Direction.SOUTH : Direction.NORTH : getSpeed() < 0 ? Direction.WEST : Direction.EAST; }
    public Vec3 getItemMovementVec() { var side = getItemMovementFacing(); return new Vec3(side.getStepX(), 0, side.getStepZ()); }
    @Override protected boolean shouldRun() { return getBlockState().getValue(SawBlock.FACING).getAxis().isHorizontal(); }
    @Override protected BlockPos getBreakingPos() { return worldPosition.relative(getBlockState().getValue(SawBlock.FACING)); }
    @Override public boolean canBreak(BlockState state, float hardness) { return super.canBreak(state, hardness) && isSawable(state); }
    public static boolean isSawable(BlockState state) {
        if (state.is(BlockTags.SAPLINGS)) return false;
        return TreeCutter.isLog(state) || TreeCutter.isRoot(state) || state.is(BlockTags.LEAVES) || TreeCutter.isVerticalPlant(state)
                || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON) || TreeCutter.isChorus(state);
    }
    @Override public void onBlockBroken(BlockState state) {
        super.onBlockBroken(state);
        if (level instanceof ServerLevel server) TreeCutter.findTree(level, breakingPos, state).destroyBlocks(server, this::dropItemFromCutTree);
    }
    public void dropItemFromCutTree(BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return;
        var center = Vec3.atCenterOf(pos); var direction = breakingPos.subtract(worldPosition); double distance = Math.sqrt(pos.distSqr(breakingPos)) / 20;
        var entity = new ItemEntity(level, center.x, center.y, center.z, stack, direction.getX() * distance, direction.getY() * distance, direction.getZ() * distance);
        level.addFreshEntity(entity);
    }
    private List<RecipeHolder<?>> recipes(ItemStack input) {
        if (!(level instanceof ServerLevel server) || input.isEmpty()) return List.of();
        var candidate = new SingleRecipeInput(input); var recipes = new ArrayList<RecipeHolder<?>>();
        var assembly=com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.getRecipes(level,input,AllRecipeTypes.CUTTING,r->true);
        if(!assembly.isEmpty())return assembly.stream().<RecipeHolder<?>>map(match->new RecipeHolder<>(ResourceKey.create(Registries.RECIPE,match.id()),match.recipe())).toList();
        for (var holder : server.getServer().getRecipeManager().getRecipes()) {
            var recipe = holder.value(); ItemStack result;
            if (recipe instanceof CuttingRecipe cutting && cutting.matches(candidate, level)) result = cutting.assemble(candidate);
            else if (recipe instanceof StonecutterRecipe stone && com.simibubi.create.infrastructure.config.AllConfigs.server().recipes.allowStonecuttingOnSaw.get() && stone.matches(candidate, level)) result = stone.assemble(candidate);
            else continue;
            if (holder.id().identifier().getPath().endsWith("_manual_only") || BuiltInRegistries.RECIPE_SERIALIZER.wrapAsHolder(recipe.getSerializer()).is(AUTOMATION_IGNORE)) continue;
            if (!filtering.test(result)) continue;
            recipes.add(holder);
        }
        recipes.sort(Comparator.comparing(r -> r.id().identifier().toString())); return List.copyOf(recipes);
    }
    public record Selection(Identifier id, int index, int duration) {}
    Selection selectRecipe(ItemStack input, int index) {
        var matches = recipes(input); if (matches.isEmpty()) return new Selection(null, Math.max(0, index - 1), 10);
        index = index >= matches.size() ? 0 : index; var holder = matches.get(index);
        return new Selection(holder.id().identifier(), index, holder.value() instanceof CuttingRecipe cutting ? cutting.processingTime() : 50);
    }
    private void completionEvent(ItemStack input){playEvent=input.copy();sync();}
    private void consumeCompletionEvent(){
        if(level==null||!level.isClientSide()||getSpeed()==0||playEvent.isEmpty())return;
        var exemplar=playEvent;playEvent=ItemStack.EMPTY;
        net.minecraft.core.particles.ParticleOptions particle=exemplar.getItem() instanceof BlockItem block
            ?new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,block.getBlock().defaultBlockState())
            :new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM,ItemStackTemplate.fromNonEmptyStack(exemplar));
        var at=Vec3.atCenterOf(worldPosition).add(0,5/16f,0);
        for(int i=0;i<10;i++){
            double x=(level.getRandom().nextDouble()-.5)*.25,y=.25+(level.getRandom().nextDouble()-.5)*.25;
            level.addParticle(particle,at.x,at.y,at.z,x,y,y);
        }
        boolean wood=exemplar.getItem() instanceof BlockItem block&&block.getBlock().defaultBlockState().getSoundType()==SoundType.WOOD;
        level.playLocalSound(worldPosition,wood?AllSoundEvents.SAW_ACTIVATE_WOOD:AllSoundEvents.SAW_ACTIVATE_STONE,net.minecraft.sounds.SoundSource.BLOCKS,3,1,true);
    }
    private void applyRecipe() {
        var input = inventory.input(); var results = new ArrayList<ItemStack>();
        var assembly=com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.getRecipes(level,input,AllRecipeTypes.CUTTING,r->true).stream().filter(match->match.id().equals(inventory.selectedRecipe())).findFirst();
        if(assembly.isPresent()){for(int roll=0;roll<input.getCount();roll++)results.addAll(assembly.get().roll(level.getRandom()));inventory.apply(results);completionEvent(input);award(AllAdvancements.SAW_PROCESSING);return;}
        Recipe<?> recipe = null;
        if (inventory.selectedRecipe() != null && level instanceof ServerLevel server) recipe = server.getServer().getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, inventory.selectedRecipe())).map(RecipeHolder::value).orElse(null);
        var candidate = new SingleRecipeInput(input);
        boolean processed=recipe instanceof CuttingRecipe cutting && cutting.matches(candidate,level)
            || recipe instanceof StonecutterRecipe stone
                && com.simibubi.create.infrastructure.config.AllConfigs.server().recipes.allowStonecuttingOnSaw.get()
                && stone.matches(candidate,level);
        for(int roll=0;roll<input.getCount();roll++){
        if (recipe instanceof CuttingRecipe cutting && cutting.matches(candidate, level)) {
            for (var output : cutting.results()) { var rolled = output.rollOutput(level.getRandom()); if (!rolled.isEmpty()) results.add(rolled); }
        } else if (recipe instanceof StonecutterRecipe stone && com.simibubi.create.infrastructure.config.AllConfigs.server().recipes.allowStonecuttingOnSaw.get() && stone.matches(candidate, level)) results.add(stone.assemble(candidate));
        else {results.add(input);break;}
        if (recipe != null && input.getItem().getCraftingRemainder() != null) results.add(input.getItem().getCraftingRemainder().create());
        }
        inventory.apply(results);
        completionEvent(input);
        if(processed)award(AllAdvancements.SAW_PROCESSING);
    }
    private void export() {
        var side = getItemMovementFacing();
        var direct=com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(this,com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.TYPE);
        var funnel=direct==null?null:direct.exportingFunnel(side.getOpposite());
        if(funnel!=null){inventory.exportTo(funnel,true);if(inventory.isEmpty())inventory.clear();return;}
        var target = worldPosition.relative(side); if (!level.isLoaded(target)) return;
        var storage = com.simibubi.create.foundation.item.LoadedItemStorage.find(level,target,side.getOpposite());
        if (storage != null) { inventory.exportTo(storage); if (inventory.isEmpty()) inventory.clear(); return; }
        var out = Vec3.atCenterOf(worldPosition).add(side.getStepX() * 0.5, 0.5, side.getStepZ() * 0.5);
        for (var stack : inventory.outputs()) level.addFreshEntity(new ItemEntity(level, out.x, out.y, out.z, stack, side.getStepX() / 16.0, 0.125, side.getStepZ() / 16.0));
        inventory.clear();
    }
    public static void tick(Level level, BlockPos pos, BlockState state, SawBlockEntity saw) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, saw)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level, pos, state, saw);
        saw.consumeCompletionEvent();
        if (saw.shouldRun() && saw.ticksUntilNextProgress < 0) saw.destroyNextTick();
        saw.tickBreaking();
        if (!saw.canProcess() || saw.getSpeed() == 0) return;
        if (!level.isClientSide()) saw.inventory.resumeSourceRecipeSelection();
        if (!level.isClientSide() && saw.inventory.isEmpty()) {
            for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).expandTowards(0, 0.25, 0))) {
                if (!item.isAlive() || item.getY() < pos.getY() + 0.7 || item.getDeltaMovement().y > 0.01) continue;
                try (Transaction tx = Transaction.openOuter()) {
                    long inserted = saw.inventory.insert(ItemVariant.of(item.getItem()), item.getItem().getCount(), tx); if (inserted == 0) continue; tx.commit();
                    var rest = item.getItem().copy(); rest.shrink((int) inserted); if (rest.isEmpty()) item.discard(); else item.setItem(rest); break;
                }
            }
        }
        if (saw.inventory.isEmpty()) return;
        float speed = Math.clamp(Math.abs(saw.getSpeed()) / 24, 1, 128);
        saw.inventory.remainingTime = Math.max(0, saw.inventory.remainingTime - speed); saw.setChanged();
        if (level.isClientSide()) {
            var input = saw.inventory.input();
            if (!input.isEmpty()) level.addParticle(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(input)), pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, 0, 0.1, 0);
            if (!saw.isVirtual()) return;
        }
        if (saw.inventory.remainingTime < 5 && !saw.inventory.appliedRecipe) { saw.applyRecipe(); return; }
        if (saw.inventory.remainingTime <= 0) saw.export();
    
        }
    }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) { if (level instanceof ServerLevel) inventory.collect().forEach(stack -> Block.popResource(level, pos, stack)); super.preRemoveSideEffects(pos, state); }
    @Override protected void write(ValueOutput out, boolean clientPacket) { super.write(out, clientPacket); inventory.save(out.child("Inventory"));if(clientPacket&&!playEvent.isEmpty()){out.store("PlayEvent",ItemStack.OPTIONAL_CODEC,playEvent);playEvent=ItemStack.EMPTY;} }
    @Override protected void read(ValueInput in, boolean clientPacket) { super.read(in, clientPacket); inventory.load(in.childOrEmpty("Inventory"));playEvent=clientPacket?in.read("PlayEvent",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY):ItemStack.EMPTY; }
    private final com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour directBeltInput = com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.createNative(this);
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (filtering != null) behaviours.add(filtering);
        if (directBeltInput != null) behaviours.add(directBeltInput);
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("saw_processing");
    }
}
