package com.simibubi.create.content.kinetics.fan.processing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.*;
import java.util.ArrayList;
import java.util.List;

/** Registered strategies with real Fabric inventory commits and opaque unknown progress IDs. */
public final class FanProcessing {
    /** Existing port API/field identity remains available for its four builtins. */
    public enum Type implements net.minecraft.util.StringRepresentable,FanProcessingType {
        SPLASHING,HAUNTING,SMOKING,BLASTING;
        public static final Codec<Type> CODEC=net.minecraft.util.StringRepresentable.fromEnum(Type::values);
        @Override public String getSerializedName(){return name().toLowerCase(java.util.Locale.ROOT);}
        private final TagKey<Block> blocks=TagKey.create(net.minecraft.core.registries.Registries.BLOCK,Create.asResource("fan_processing_catalysts/"+getSerializedName()));
        private final TagKey<net.minecraft.world.level.material.Fluid> fluids=TagKey.create(net.minecraft.core.registries.Registries.FLUID,Create.asResource("fan_processing_catalysts/"+getSerializedName()));
        public FanProcessingType registeredType(){return switch(this){case SPLASHING->AllFanProcessingTypes.SPLASHING;case HAUNTING->AllFanProcessingTypes.HAUNTING;case SMOKING->AllFanProcessingTypes.SMOKING;case BLASTING->AllFanProcessingTypes.BLASTING;};}
        boolean isValidBuiltinAt(Level level,BlockPos pos){
            var fluid=level.getFluidState(pos);var state=level.getBlockState(pos);
            if(fluid.is(fluids))return true;if(!state.is(blocks))return false;
            if((this==HAUNTING||this==SMOKING)&&state.is(BlockTags.CAMPFIRES)&&state.hasProperty(CampfireBlock.LIT)&&!state.getValue(CampfireBlock.LIT))return false;
            if(state.hasProperty(com.simibubi.create.content.processing.burner.LitBlazeBurnerBlock.FLAME_TYPE)){
                var flame=state.getValue(com.simibubi.create.content.processing.burner.LitBlazeBurnerBlock.FLAME_TYPE);
                if(this==HAUNTING&&flame!=com.simibubi.create.content.processing.burner.LitBlazeBurnerBlock.FlameType.SOUL||this==SMOKING&&flame!=com.simibubi.create.content.processing.burner.LitBlazeBurnerBlock.FlameType.REGULAR)return false;
            }
            if(state.hasProperty(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL)){
                var heat=state.getValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL);
                if(this==BLASTING&&heat.ordinal()<com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.FADING.ordinal())return false;
                if(this==SMOKING&&heat!=com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SMOULDERING)return false;
            }return true;
        }
        @Override public boolean isValidAt(Level level,BlockPos pos){return registeredType().isValidAt(level,pos);}
        @Override public int getPriority(){return registeredType().getPriority();}
        @Override public boolean canProcess(ItemStack stack,Level level){return registeredType().canProcess(stack,level);}
        @Override public List<ItemStack> process(ItemStack stack,Level level){return registeredType().process(stack,level);}
        @Override public void spawnProcessingParticles(Level level,net.minecraft.world.phys.Vec3 pos){registeredType().spawnProcessingParticles(level,pos);}
        @Override public void morphAirFlow(AirFlowParticleAccess access,net.minecraft.util.RandomSource random){registeredType().morphAirFlow(access,random);}
        @Override public void affectEntity(net.minecraft.world.entity.Entity entity,Level level){registeredType().affectEntity(entity,level);}
        /** Legacy enum-only lookup. New addon dispatch uses FanProcessingType.getAt. */
        public static Type at(Level level,BlockPos pos){var type=canonical(FanProcessingType.getAt(level,pos));return type instanceof Type builtin?builtin:null;}
    }
    public static FanProcessingType canonical(FanProcessingType type){
        if(type==null||type instanceof Type)return type;
        if(type==AllFanProcessingTypes.SPLASHING)return Type.SPLASHING;if(type==AllFanProcessingTypes.HAUNTING)return Type.HAUNTING;
        if(type==AllFanProcessingTypes.SMOKING)return Type.SMOKING;if(type==AllFanProcessingTypes.BLASTING)return Type.BLASTING;
        return type;
    }
    public static String id(FanProcessingType type){
        var actual=type instanceof Type legacy?legacy.registeredType():type;
        var key=com.simibubi.create.api.registry.CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(actual);
        if(key==null)throw new IllegalArgumentException("Unregistered FanProcessingType "+type);
        return key.toString();
    }
    public static String stableTypeName(String name){
        var known=AllFanProcessingTypes.parseLegacy(name);if(known!=null)return id(known);
        return net.minecraft.resources.Identifier.parse(name).toString();
    }
    public static FanProcessingType resolve(String name){return canonical(AllFanProcessingTypes.parseLegacy(name));}
    private static boolean sameType(String name,FanProcessingType type){return resolve(name)==canonical(type);}
    /** Unknown IDs keep their timer and unpaid cached outputs; no fallback type consumes them. */
    public static boolean resolveTransportedType(TransportedItemStack item){
        if(item.unresolvedFanProcessingType!=null){
            var known=resolve(item.unresolvedFanProcessingType);if(known==null)return false;
            item.processedBy=known;item.unresolvedFanProcessingType=null;
        }
        item.processedBy=canonical(item.processedBy);return true;
    }
    public record Progress(String type, ItemVariant input, int count, int time, long lastTick) {
        public static final Codec<Progress> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("Type").forGetter(Progress::type), ItemVariant.CODEC.fieldOf("Input").forGetter(Progress::input),
                Codec.intRange(1, 99).fieldOf("Count").forGetter(Progress::count),
                Codec.INT.fieldOf("Time").forGetter(Progress::time),
                Codec.LONG.fieldOf("LastTick").forGetter(Progress::lastTick)
        ).apply(i, Progress::new));
    }
    public static final AttachmentType<Progress> PROGRESS = AttachmentRegistry.createPersistent(Create.asResource("fan_processing"), Progress.CODEC);
    public static void register() {}


    private static <R extends Recipe<SingleRecipeInput>> R find(Level level,RecipeType<R> type,ItemStack stack,boolean automated){
        var input=new SingleRecipeInput(stack);
        var found=level instanceof ServerLevel server?server.getServer().getRecipeManager().getRecipeFor(type,input,level):level.recipeAccess().getSynchronizedRecipes().getFirstMatch(type,input,level);
        return found.filter(holder->!automated||AllRecipeTypes.CAN_BE_AUTOMATED.test(holder)).map(RecipeHolder::value).orElse(null);
    }
    private static Recipe<?> recipe(Level level,ItemStack stack,Type type){
        return switch(type){
            case SPLASHING->find(level,AllRecipeTypes.SPLASHING,stack,false);
            case HAUNTING->find(level,AllRecipeTypes.HAUNTING,stack,false);
            case SMOKING->find(level,RecipeType.SMOKING,stack,true);
            case BLASTING->{var smelting=find(level,RecipeType.SMELTING,stack,true);yield smelting!=null?smelting:find(level,RecipeType.BLASTING,stack,true);}
        };
    }
    static boolean builtinCanProcess(ItemStack stack,Level level,Type type){return !stack.isEmpty()&&(recipe(level,stack,type)!=null||type==Type.BLASTING&&stack.canBeHurtBy(level.damageSources().lava()));}
    static List<ItemStack> builtinProcess(ItemStack stack,Level level,Type type){
        if(!(level instanceof ServerLevel server))return null;
        var recipe=recipe(level,stack,type);if(recipe==null&&type!=Type.BLASTING)return null;
        return results(server,stack,type,recipe);
    }
    private record InputSnapshot(ItemVariant variant,int count){
        static InputSnapshot capture(ItemStack stack){return new InputSnapshot(ItemVariant.of(stack),stack.getCount());}
        boolean matches(ItemStack stack){return !stack.isEmpty()&&variant.matches(stack)&&count==stack.getCount();}
    }
    // Synchronous addon/native callbacks may call the same public processing API again.
    private static final ThreadLocal<java.util.Set<Object>> IN_FLIGHT=ThreadLocal.withInitial(()->java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    private static boolean currentEntity(ItemEntity entity,ServerLevel level,InputSnapshot expected){
        return !entity.isRemoved()&&entity.level()==level&&level.getEntity(entity.getUUID())==entity&&expected.matches(entity.getItem());
    }
    private static void invalidateEntityInput(ItemEntity entity,FanProcessingType type,InputSnapshot expected){
        var progress=entity.getAttached(PROGRESS);
        if(progress!=null&&sameType(progress.type(),type)&&progress.input().equals(expected.variant())&&progress.count()==expected.count())entity.removeAttached(PROGRESS);
        var pending=entity.getAttached(PENDING);
        if(pending!=null&&sameType(pending.type(),type)&&pending.input().equals(expected.variant())&&pending.count()==expected.count())entity.removeAttached(PENDING);
    }
    private static boolean currentDepot(DepotBehaviour depot,TransportedItemStack item,InputSnapshot expected){
        if(!depot.isActiveWorldOwner())return false;
        var actual=depot.getHeldItem();return actual==item&&expected.matches(actual.stack);
    }
    private static boolean currentBelt(BeltBlockEntity segment,BeltBlockEntity controller,TransportedItemStack item,InputSnapshot expected){
        var level=segment.getLevel();
        return level!=null&&!segment.isRemoved()&&!controller.isRemoved()
            &&com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,segment.getBlockPos())==segment
            &&com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,controller.getBlockPos())==controller
            &&segment.getControllerBE()==controller&&controller.chainLoaded()&&controller.getInventory().byId(item.id)==item
            &&item.beltPosition>=segment.index&&item.beltPosition<segment.index+1&&expected.matches(item.stack);
    }
    private static void invalidateTransportedInput(TransportedItemStack item,Level level){item.clearFanProcessingData();item.lastProcessingTick=level.getGameTime();}
    private static int duration(ItemStack stack){return com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.fanProcessingTime.get()*((stack.getCount()-1)/16+1);}
    private static boolean ready(AttachmentTarget target,ServerLevel level,ItemStack stack,FanProcessingType type){
        if(stack.isEmpty())return false;id(type);
        var progress=target.getAttached(PROGRESS);var variant=ItemVariant.of(stack);
        if(progress!=null&&resolve(progress.type())==null)return false;
        if(progress==null||!sameType(progress.type(),type)||!progress.input().equals(variant)||progress.count()!=stack.getCount()){
            var expected=InputSnapshot.capture(stack);
            if(!type.canProcess(stack.copy(),level)||!expected.matches(stack))return false;
            progress=new Progress(id(type),expected.variant(),expected.count(),duration(stack),level.getGameTime());target.setAttached(PROGRESS,progress);return progress.time()==0;
        }
        if(progress.time()==-1)return false;if(progress.time()==0)return true;if(progress.lastTick()==level.getGameTime())return false;
        progress=new Progress(id(type),variant,stack.getCount(),progress.time()-1,level.getGameTime());target.setAttached(PROGRESS,progress);return progress.time()==0;
    }
    private static List<ItemStack> snapshotResults(List<ItemStack> results){
        if(results==null)return null;
        var copy=new ArrayList<ItemStack>();for(var stack:results){if(stack==null)throw new IllegalArgumentException("Null fan result stack");addResult(copy,stack.copy());}return copy;
    }
    public static boolean canProcess(ItemEntity entity,FanProcessingType type){
        if(entity.isRemoved()||entity.getItem().isEmpty())return false;
        var progress=entity.getAttached(PROGRESS);if(progress!=null&&resolve(progress.type())==null)return false;
        if(progress!=null&&sameType(progress.type(),type)&&progress.input().matches(entity.getItem())&&progress.count()==entity.getItem().getCount())return progress.time()>=0;
        return type.canProcess(entity.getItem().copy(),entity.level());
    }
    public static boolean applyProcessing(ItemEntity entity,FanProcessingType type){return processEntity(entity,type,()->{});}
    public static void process(ItemEntity entity,FanProcessingType type){process(entity,type,()->{});}
    public static void process(ItemEntity entity,FanProcessingType type,Runnable completed){processEntity(entity,type,completed);}
    private static boolean processEntity(ItemEntity entity,FanProcessingType requested,Runnable completed){
        var active=IN_FLIGHT.get();if(!active.add(entity))return false;
        try{return processCurrentEntity(entity,requested,completed);}finally{active.remove(entity);if(active.isEmpty())IN_FLIGHT.remove();}
    }
    private static boolean processCurrentEntity(ItemEntity entity,FanProcessingType requested,Runnable completed){
        if(!(entity.level() instanceof ServerLevel level)||entity.isRemoved())return false;
        var type=canonical(requested);var expected=InputSnapshot.capture(entity.getItem());
        if(!currentEntity(entity,level,expected))return false;
        boolean ready=ready(entity,level,entity.getItem(),type);
        if(!currentEntity(entity,level,expected)){invalidateEntityInput(entity,type,expected);return false;}
        if(!ready)return false;
        var cached=entity.getAttached(PENDING);
        if(cached==null||!sameType(cached.type(),type)||!cached.input().equals(expected.variant())||cached.count()!=expected.count()){
            var rolled=snapshotResults(type.process(entity.getItem().copy(),level));
            if(!currentEntity(entity,level,expected)){invalidateEntityInput(entity,type,expected);return false;}
            if(rolled==null){entity.setAttached(PROGRESS,new Progress(id(type),expected.variant(),expected.count(),-1,level.getGameTime()));return false;}
            cached=new Pending(id(type),expected.variant(),expected.count(),rolled);entity.setAttached(PENDING,cached);
        }
        var output=cached.results();var admitted=new ArrayList<ItemEntity>();
        for(int i=1;i<output.size();i++){
            if(!currentEntity(entity,level,expected)){admitted.forEach(ItemEntity::discard);invalidateEntityInput(entity,type,expected);return false;}
            var extra=new ItemEntity(level,entity.getX(),entity.getY(),entity.getZ(),output.get(i).copy());
            extra.setDeltaMovement(entity.getDeltaMovement());extra.setAttached(PROGRESS,new Progress(id(type),ItemVariant.of(extra.getItem()),extra.getItem().getCount(),-1,level.getGameTime()));
            boolean accepted=level.addFreshEntity(extra);
            if(accepted)admitted.add(extra);
            boolean current=currentEntity(entity,level,expected);
            if(!accepted||!current){admitted.forEach(ItemEntity::discard);if(!current)invalidateEntityInput(entity,type,expected);return false;}
        }
        // Native admission callbacks above may legitimately transfer original input elsewhere.
        if(!currentEntity(entity,level,expected)){admitted.forEach(ItemEntity::discard);invalidateEntityInput(entity,type,expected);return false;}
        entity.removeAttached(PROGRESS);entity.removeAttached(PENDING);
        if(output.isEmpty()){entity.discard();completed.run();return false;}
        entity.setItem(output.getFirst().copy());entity.setAttached(PROGRESS,new Progress(id(type),ItemVariant.of(entity.getItem()),entity.getItem().getCount(),-1,level.getGameTime()));
        completed.run();return true;
    }
    public static TransportedResult applyProcessing(TransportedItemStack item,Level level,FanProcessingType requested){
        var active=IN_FLIGHT.get();if(!active.add(item))return TransportedResult.doNothing();
        try{return prepareTransportedProcessing(item,level,requested);}finally{active.remove(item);if(active.isEmpty())IN_FLIGHT.remove();}
    }
    private static TransportedResult prepareTransportedProcessing(TransportedItemStack item,Level level,FanProcessingType requested){
        if(item.stack.isEmpty()||!resolveTransportedType(item))return TransportedResult.doNothing();
        var type=canonical(requested);id(type);
        if(item.pendingFanResults!=null&&(!item.pendingFanInput.matches(item.stack)||item.pendingFanCount!=item.stack.getCount()))item.clearFanProcessingData();
        if(item.processedBy!=type){
            item.clearFanProcessingData();item.processedBy=type;var expected=InputSnapshot.capture(item.stack);
            boolean allowed=type.canProcess(item.stack.copy(),level);
            if(!expected.matches(item.stack)){invalidateTransportedInput(item,level);return TransportedResult.doNothing();}
            item.processingTime=allowed?duration(item.stack)+1:-1;return TransportedResult.doNothing();
        }
        if(item.pendingFanResults==null){
            if(item.processingTime==-1)return TransportedResult.doNothing();if(item.processingTime-->0)return TransportedResult.doNothing();
            var expected=InputSnapshot.capture(item.stack);var rolled=snapshotResults(type.process(item.stack.copy(),level));
            if(!expected.matches(item.stack)){invalidateTransportedInput(item,level);return TransportedResult.doNothing();}
            if(rolled==null)return TransportedResult.doNothing();
            item.pendingFanResults=rolled;item.pendingFanInput=expected.variant();item.pendingFanCount=expected.count();
        }
        var converted=new ArrayList<TransportedItemStack>();
        for(var result:item.pendingFanResults){var copy=item.copy();copy.stack=result.copy();copy.processingTime=-1;copy.pendingFanResults=null;copy.pendingFanInput=ItemVariant.blank();copy.pendingFanCount=0;converted.add(copy);}
        return TransportedResult.convertTo(converted);
    }
    public static void process(DepotBlockEntity depot,FanProcessingType type){process(depot.depotBehaviour,type);}
    public static void process(DepotBehaviour depot,FanProcessingType type){process(depot,type,()->{});}
    public static void process(DepotBehaviour depot,FanProcessingType requested,Runnable completed){
        if(!(depot.getWorld() instanceof ServerLevel level)||!depot.isActiveWorldOwner())return;
        var item=depot.getHeldItem();if(item==null||item.lastProcessingTick==level.getGameTime())return;item.lastProcessingTick=level.getGameTime();
        var owner=depot.blockEntity;var type=canonical(requested);var legacy=owner.getAttached(PROGRESS);
        if(item.processedBy==null&&item.unresolvedFanProcessingType==null&&legacy!=null){
            var resolved=resolve(legacy.type());if(resolved==null)return;
            if(resolved==type&&legacy.input().matches(item.stack)&&legacy.count()==item.stack.getCount()){
                item.processedBy=type;item.processingTime=legacy.time();var pending=owner.getAttached(PENDING);
                if(pending!=null&&sameType(pending.type(),type)&&pending.input().matches(item.stack)&&pending.count()==item.stack.getCount()){
                    item.pendingFanResults=pending.results().stream().map(ItemStack::copy).toList();item.pendingFanInput=pending.input();item.pendingFanCount=pending.count();
                }
            }
        }
        var expected=InputSnapshot.capture(item.stack);var result=applyProcessing(item,level,type);item.lastProcessingTick=level.getGameTime();
        if(!currentDepot(depot,item,expected)){invalidateTransportedInput(item,level);return;}
        owner.setChanged();if(result.doesNothing())return;
        boolean changed=!result.didntChangeFrom(item.stack);
        try(var tx=Transaction.openNested(Transaction.getCurrentUnsafe())){
            if(!currentDepot(depot,item,expected)){invalidateTransportedInput(item,level);return;}
            depot.applyResult(result,tx);
            tx.addOuterCloseCallback(closed->{if(closed.wasCommitted()){owner.removeAttached(PROGRESS);owner.removeAttached(PENDING);if(changed)completed.run();}});tx.commit();
        }
    }
    public static void process(BeltBlockEntity segment,FanProcessingType type){process(segment,type,()->{});}
    public static void process(BeltBlockEntity segment,FanProcessingType type,Runnable completed){
        var controller=segment.getControllerBE();if(controller==null||!controller.chainLoaded()||!(segment.getLevel() instanceof ServerLevel level))return;
        var inventory=controller.getInventory();
        for(var item:inventory.getTransportedItems()){
            if(item.beltPosition<segment.index||item.beltPosition>=segment.index+1||item.stack.isEmpty()||item.lastProcessingTick==level.getGameTime())continue;
            var expected=InputSnapshot.capture(item.stack);if(!currentBelt(segment,controller,item,expected))continue;
            item.lastProcessingTick=level.getGameTime();var result=applyProcessing(item,level,type);item.lastProcessingTick=level.getGameTime();
            if(!currentBelt(segment,controller,item,expected)){invalidateTransportedInput(item,level);continue;}
            inventory.changed();if(result.doesNothing())continue;
            boolean changed=!result.didntChangeFrom(item.stack);var outputs=result.getOutputs().stream().map(output->output.stack).toList();
            try(var tx=Transaction.openNested(Transaction.getCurrentUnsafe())){
                if(!currentBelt(segment,controller,item,expected)){invalidateTransportedInput(item,level);continue;}
                if(!inventory.replace(item,expected.count(),outputs,true,tx))continue;
                tx.addOuterCloseCallback(closed->{if(closed.wasCommitted()&&changed)completed.run();});tx.commit();
            }
        }
    }
    // Preserve existing port call descriptors while registered addon types use the strategy overloads.
    public static void process(ItemEntity item,Type type){process(item,(FanProcessingType)type);}
    public static void process(ItemEntity item,Type type,Runnable completed){process(item,(FanProcessingType)type,completed);}
    public static void process(DepotBlockEntity depot,Type type){process(depot,(FanProcessingType)type);}
    public static void process(DepotBehaviour depot,Type type){process(depot,(FanProcessingType)type);}
    public static void process(DepotBehaviour depot,Type type,Runnable completed){process(depot,(FanProcessingType)type,completed);}
    public static void process(BeltBlockEntity belt,Type type){process(belt,(FanProcessingType)type);}
    public static void process(BeltBlockEntity belt,Type type,Runnable completed){process(belt,(FanProcessingType)type,completed);}
    public record Pending(String type,ItemVariant input,int count,List<ItemStack> results){
        public Pending{results=results.stream().map(ItemStack::copy).toList();}
        public Pending(FanProcessingType type,ItemVariant input,int count,List<ItemStack> results){this(id(type),input,count,results);}
        public static final Codec<Pending> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("Type").forGetter(Pending::type),ItemVariant.CODEC.fieldOf("Input").forGetter(Pending::input),Codec.intRange(1,99).fieldOf("Count").forGetter(Pending::count),ItemStack.CODEC.listOf().fieldOf("Results").forGetter(Pending::results)
        ).apply(i,Pending::new));
    }
    private static final AttachmentType<Pending> PENDING=AttachmentRegistry.createPersistent(Create.asResource("fan_pending_results"),Pending.CODEC);
    private static List<ItemStack> results(ServerLevel level, ItemStack stack, Type type, Recipe<?> recipe) {
        var output = new ArrayList<ItemStack>();
        if (recipe == null) return output;
        if (type == Type.BLASTING) {
            var smoking = recipe(level, stack, Type.SMOKING);
            if (smoking instanceof AbstractCookingRecipe cooking && recipe instanceof AbstractCookingRecipe blasting
                    && ItemStack.isSameItem(cooking.assemble(new SingleRecipeInput(stack)), blasting.assemble(new SingleRecipeInput(stack)))) return output;
        }
        for (int i = 0; i < stack.getCount(); i++) {
            if (recipe instanceof FanProcessingRecipe fan) {
                for (var result : fan.results()) addResult(output, result.rollOutput(level.getRandom()));
            } else if (recipe instanceof AbstractCookingRecipe cooking) addResult(output, cooking.assemble(new SingleRecipeInput(stack)));
            var remainder = stack.getItem().getCraftingRemainder();
            if (remainder != null) addResult(output, remainder.create());
        }
        return output;
    }
    private static void addResult(List<ItemStack> outputs, ItemStack stack) {
        if (stack.isEmpty()) return;
        for (var existing : outputs) {
            if (!ItemStack.isSameItemSameComponents(existing, stack)) continue;
            int moved = Math.min(existing.getMaxStackSize() - existing.getCount(), stack.getCount());
            existing.grow(moved); stack.shrink(moved);
            if (stack.isEmpty()) return;
        }
        while (!stack.isEmpty()) {
            int count = Math.min(stack.getCount(), stack.getMaxStackSize());
            outputs.add(stack.copyWithCount(count)); stack.shrink(count);
        }
    }
    private FanProcessing(){}
}
