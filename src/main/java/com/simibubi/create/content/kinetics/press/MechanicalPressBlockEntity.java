package com.simibubi.create.content.kinetics.press;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

public class MechanicalPressBlockEntity extends BasinOperatingBlockEntity implements com.simibubi.create.content.kinetics.belt.behaviour.BeltItemProcessor, PressingBehaviour.NativePressingBehaviourSpecifics {
    private static final Object compressingRecipesKey = new Object();
    public static final int CYCLE=PressingBehaviour.CYCLE;
    public PressingBehaviour pressingBehaviour;
    private int tracksCreated;
    private net.fabricmc.fabric.api.transfer.v1.item.ItemVariant pendingInput=net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.blank();
    private net.minecraft.resources.Identifier pendingRecipe;
    private java.util.List<ItemStack> pendingOutputs;
    private int pendingRolls=1;
    private boolean processingWorldOutput;
    private int processingCount(ItemStack input){return com.simibubi.create.infrastructure.config.AllConfigs.server().recipes.bulkPressing.get()?input.getCount():1;}
    private record Plan(net.minecraft.resources.Identifier id,PressingRecipe recipe,com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.Match assembly){}

    public MechanicalPressBlockEntity(BlockPos pos, BlockState state) { this(AllBlockEntityTypes.MECHANICAL_PRESS, pos, state); }
    public MechanicalPressBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override protected AABB createRenderBoundingBox(){return new AABB(worldPosition).expandTowards(0,-1.5,0).expandTowards(0,1,0);}
    @Override public void addBehaviours(java.util.List<BlockEntityBehaviour> behaviours){
        super.addBehaviours(behaviours);pressingBehaviour=new PressingBehaviour(this);behaviours.add(pressingBehaviour);
    }
    public PressingBehaviour getPressingBehaviour(){return pressingBehaviour;}
    @Override public boolean isRunning(){return pressingBehaviour.running;}
    @Override public void startProcessingBasin(){
        if(pressingBehaviour.running&&pressingBehaviour.runningTicks<=CYCLE/2)return;
        super.startProcessingBasin();pressingBehaviour.start(PressingBehaviour.Mode.BASIN);
    }
    @Override protected void onBasinRemoved(){pressingBehaviour.cancel();currentRecipe=null;sendData();}
    @Override public boolean isNativeCycleBlocked(){return basinRemoved;}
    @Override public float getKineticSpeed(){return getSpeed();}
    @Override public boolean canProcessInBulk(){return com.simibubi.create.infrastructure.config.AllConfigs.server().recipes.bulkPressing.get();}
    @Override public int getParticleAmount(){return 15;}
    @Override public void onPressingCompleted(){basinChecker.scheduleUpdate();}
    private boolean actualProcessingOwner(){
        return level instanceof ServerLevel&&!isVirtual()&&!isRemoved()
            &&com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,worldPosition)==this
            &&getBehaviour(BeltProcessingBehaviour.TYPE)==pressingBehaviour;
    }
    @Override public boolean tryStartNativeCycle(){return actualProcessingOwner()&&getSpeed()!=0&&tryPress(true);}
    @Override public boolean tryApplyNativeImpact(){
        if(!actualProcessingOwner()||getSpeed()==0||!tryPress(false))return false;
        boolean transported=pressingBehaviour.mode!=PressingBehaviour.Mode.WORLD;
        level.playSound(null,worldPosition,transported?AllSoundEvents.PRESS_BELT:AllSoundEvents.PRESS,
            net.minecraft.sounds.SoundSource.BLOCKS,transported?.75f:.125f,.75f+Math.abs(getSpeed())/1024f);
        level.playSound(null,worldPosition,transported?AllSoundEvents.PRESS_BELT_BREAK:AllSoundEvents.PRESS_BREAK,
            net.minecraft.sounds.SoundSource.BLOCKS,transported?.15f:.5f,.75f);return true;
    }
    @Override protected Object getRecipeCacheKey() { return compressingRecipesKey; }
    @Override protected boolean matchStaticFilters(net.minecraft.world.item.crafting.RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>> recipe) {
        return recipe.value().getType() == AllRecipeTypes.COMPACTING;
    }
    @Override protected java.util.List<net.minecraft.world.item.crafting.Recipe<?>> getMatchingRecipes() {
        var recipes = super.getMatchingRecipes();
        var converted = getBasin().map(basin -> basin.findRecipe(AllRecipeTypes.COMPACTING)).orElse(null);
        if (converted != null && !recipes.contains(converted.value()) && matchBasinRecipe(converted.value())) recipes.add(converted.value());
        sortMatchingRecipes(recipes);
        return recipes;
    }
    @Override protected java.util.Optional<com.simibubi.create.foundation.advancement.CreateAdvancement> getProcessedRecipeTrigger() {
        return java.util.Optional.of(com.simibubi.create.foundation.advancement.AllAdvancements.COMPACTING);
    }
    public float getHeadOffset(float partialTick){return pressingBehaviour.getRenderedHeadOffset(partialTick)*pressingBehaviour.mode.headOffset;}

    private Plan recipe(ItemStack stack) {
        if (!(level instanceof ServerLevel server) || stack.isEmpty()) return null;
        var assembly=com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.getRecipe(level,stack,AllRecipeTypes.PRESSING,r->true);if(assembly.isPresent()){var match=assembly.get();return new Plan(match.id(),(PressingRecipe)match.recipe(),match);}
        return server.getServer().getRecipeManager().getRecipeFor(AllRecipeTypes.PRESSING,
                new SingleRecipeInput(stack), level).map(holder ->new Plan(holder.id().identifier(),holder.value(),null)).orElse(null);
    }
    private java.util.List<ItemStack> outputs(Plan plan,ItemStack input){
        int rolls=processingCount(input);var variant=net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(input);
        if(pendingOutputs==null||!pendingInput.equals(variant)||!java.util.Objects.equals(pendingRecipe,plan.id)||pendingRolls!=rolls){
            pendingInput=variant;pendingRecipe=plan.id;pendingRolls=rolls;var results=new java.util.ArrayList<ItemStack>();
            for(int roll=0;roll<rolls;roll++){
                for(var produced:plan.assembly!=null?plan.assembly.roll(level.getRandom()):plan.recipe.results().stream().map(output->output.rollOutput(level.getRandom())).filter(stack->!stack.isEmpty()).toList())appendOutput(results,produced);
                var remainder=input.getItem().getCraftingRemainder();if(remainder!=null)appendOutput(results,remainder.create());
            }
            pendingOutputs=java.util.List.copyOf(results);setChanged();
        }return pendingOutputs;
    }
    private static void appendOutput(java.util.List<ItemStack> results,ItemStack produced){
        var remaining=produced.copy();if(remaining.isEmpty())return;
        for(var existing:results)if(ItemStack.isSameItemSameComponents(existing,remaining)){int moved=Math.min(remaining.getCount(),Math.max(0,existing.getMaxStackSize()-existing.getCount()));if(moved>0){existing.grow(moved);remaining.shrink(moved);}if(remaining.isEmpty())return;}
        while(!remaining.isEmpty())results.add(remaining.split(Math.min(remaining.getCount(),remaining.getMaxStackSize())));
    }
    private void clearPending(){pendingOutputs=null;pendingInput=net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.blank();pendingRecipe=null;}
    /** Called only by the scoped native section-insertion commit; no publication callbacks. */
    void commitWorldProcessingPure(PressingBehaviour cycle){clearPending();cycle.markWorldImpactAppliedPure();}
    boolean ownsWorldProcessingRolls(java.util.List<ItemStack> expected,ItemStack input,int consumed){
        return pendingOutputs==expected&&pendingRolls==consumed&&pendingInput.matches(input);
    }
    public boolean onBeltItemReceived(TransportedItemStack item){
        if(!actualProcessingOwner()||getSpeed()==0)return false;
        if(pressingBehaviour.running)return true;
        if(recipe(item.stack)==null)return false;
        pressingBehaviour.start(PressingBehaviour.Mode.BELT);return true;
    }
    public boolean tryHoldBeltItem(BeltBlockEntity belt,TransportedItemStack item){return actualProcessingOwner()&&getSpeed()!=0&&pressingBehaviour.running;}
    @Override public boolean tryProcessInBasin(boolean simulate){
        if(!actualProcessingOwner())return false;var optional=getBasin();if(optional.isEmpty())return false;
        var found=findBasinRecipe();if(found==null)return false;currentRecipe=found.value();
        if(simulate)return matchBasinRecipe(currentRecipe);
        var samples=optional.get().inventory.inputs.stream().map(slot->slot.stack().copy()).filter(stack->!stack.isEmpty()).toList();
        if(!applyBasinRecipeTransaction())return false;pressingBehaviour.particleItems.addAll(samples);return true;
    }
    @Override public boolean tryProcessOnBelt(TransportedItemStack input,java.util.List<ItemStack> outputList,boolean simulate){
        if(!actualProcessingOwner())return false;var found=recipe(input.stack);if(found==null)return false;
        if(simulate)return true;if(outputList==null)throw new IllegalArgumentException("Real source output destination required");
        outputList.addAll(outputs(found,input.stack).stream().map(ItemStack::copy).toList());return true;
    }
    @Override public boolean tryProcessInWorld(ItemEntity item,boolean simulate){
        if(processingWorldOutput||!actualProcessingOwner()||item.level()!=level||item.isRemoved()||!item.onGround()
            ||!com.simibubi.create.foundation.utility.LoadedEntityAccess.isReady(level,worldPosition)
            ||!com.simibubi.create.foundation.utility.LoadedEntityAccess.isReady(level,item.blockPosition())
            ||((ServerLevel)level).getEntity(item.getUUID())!=item)return false;
        processingWorldOutput=true;
        try{
            var found=recipe(item.getItem());if(found==null)return false;if(simulate)return true;
            var source=PressingWorldOutputRecovery.prepareSource(this,item,processingCount(item.getItem()));
            if(!source.valid())return false;
            var sample=source.observed.copy();var produced=outputs(found,source.actual);
            if(!PressingWorldOutputRecovery.process(source,produced))return false;
            if(item.getItem().isEmpty()&&!item.isRemoved())item.discard();
            if(actualProcessingOwner()){
                setChanged();
                pressingBehaviour.particleItems.add(sample);for(var result:produced)if(!result.isEmpty()){onItemPressed(result);break;}
            }
            return true;
        }finally{processingWorldOutput=false;}
    }
    public void onItemPressed(ItemStack result){
        award(com.simibubi.create.foundation.advancement.AllAdvancements.PRESS);
        if(result.getItem() instanceof net.minecraft.world.item.BlockItem block&&block.getBlock().builtInRegistryHolder().is(
                net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,com.simibubi.create.Create.asResource("tracks"))))tracksCreated+=result.getCount();
        if(tracksCreated>=1000){award(com.simibubi.create.foundation.advancement.AllAdvancements.TRACK_CRAFTING);tracksCreated=0;}
    }

    private boolean tryPress(boolean simulate) {
        if (com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,worldPosition.below(2)) instanceof BeltBlockEntity segment) {
            // Arrival starts belt cycles immediately. Polling is only used for depot/world input.
            if (simulate) return false;
            var controller = segment.getControllerBE();
            if (controller == null || !controller.chainLoaded()) return false;
            var item = controller.getInventory().getTransportedItems().stream()
                    .filter(s -> worldPosition.equals(s.heldBy) && recipe(s.stack) != null).findFirst().orElse(null);
            if (item == null) return false;
            var pressing = recipe(item.stack); if (pressing == null) return false;
            pressingBehaviour.mode=PressingBehaviour.Mode.BELT;
            var outputs = outputs(pressing,item.stack);
            try (Transaction transaction = Transaction.openOuter()) {
                if (!controller.getInventory().replace(item, processingCount(item.stack), outputs, false, transaction)) return false;
                transaction.commit();
            }
            pressingBehaviour.particleItems.add(item.stack.copy());
            for(var result:outputs)if(!result.isEmpty()){onItemPressed(result);break;}
            clearPending();return true;
        }
        if(getBasin().isPresent()){
            pressingBehaviour.mode=PressingBehaviour.Mode.BASIN;return tryProcessInBasin(simulate);
        }
        var depot = BlockEntityBehaviour.get(level, worldPosition.below(2), DepotBehaviour.TYPE);
        if (depot != null) {
            if (!depot.readyForProcessing() || BeltProcessingBehaviour.isBlocked(level, worldPosition.below(2))) return false;
            var input = depot.inventory.inputs.getFirst();
            var recipe = recipe(input.stack());
            if (recipe == null) return false;
            pressingBehaviour.mode=PressingBehaviour.Mode.BELT;
            if (simulate) return true;
            var particleInput=input.stack().copy();
            try (Transaction transaction = Transaction.openOuter()) {
                var results = new java.util.ArrayList<>(outputs(recipe,input.stack()));
                if (!depot.applyRecipeOutputs(processingCount(input.stack()), results, true, transaction)) return false;
                transaction.commit();
            }
            pressingBehaviour.particleItems.add(particleInput);
            if(pendingOutputs!=null)for(var result:pendingOutputs)if(!result.isEmpty()){onItemPressed(result);break;}
            clearPending();return true;
        }
        pressingBehaviour.mode=PressingBehaviour.Mode.WORLD;
        boolean processed=false;
        for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(worldPosition.below()))){
            if(!item.isAlive()||!item.onGround())continue;
            if(tryProcessInWorld(item,simulate)){if(simulate||!canProcessInBulk())return true;processed=true;}
        }
        return processed;
    }

    @Override public void tick() {
        if (level == null) super.tick();
        else tick(level, worldPosition, getBlockState(), this);
    }

    public static void tick(Level level,BlockPos pos,BlockState state,MechanicalPressBlockEntity press){
        try(var frame=com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level,pos,state,press)){
            if(!frame.shouldTick()||press.handleBasinRemoval())return;
            KineticBlockEntity.tick(level,pos,state,press);
        }
    }
    @Override protected void write(ValueOutput output, boolean clientPacket) {
        super.write(output, clientPacket);
        output.putInt("TracksCreated",tracksCreated);
        if(pendingOutputs!=null){output.putInt("PendingRolls",pendingRolls);output.store("PendingInput",net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.CODEC,pendingInput);output.storeNullable("PendingRecipe",net.minecraft.resources.Identifier.CODEC,pendingRecipe);output.store("PendingOutputs",ItemStack.CODEC.listOf(),pendingOutputs);}
    }
    @Override protected void read(ValueInput input, boolean clientPacket) {
        super.read(input, clientPacket);
        tracksCreated=input.getIntOr("TracksCreated",0);
        pendingRolls=Math.max(1,input.getIntOr("PendingRolls",1));pendingInput=input.read("PendingInput",net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.CODEC).orElse(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.blank());pendingRecipe=input.read("PendingRecipe",net.minecraft.resources.Identifier.CODEC).orElse(null);pendingOutputs=input.read("PendingOutputs",ItemStack.CODEC.listOf()).orElse(null);
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("mechanical_press");
        ids.add("compacting");
        ids.add("compacting");
        ids.add("track_crafting_factory");
    }
}
