package com.simibubi.create.content.schematics.cannon;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.schematics.SchematicPlacement;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.*;

/** Native animated source launch with durable paid original custody until actual placement succeeds. */
public abstract class LaunchedItem {
    public int totalTicks,ticksRemaining;
    public BlockPos target;
    public ItemStack stack;
    private UUID jobId=UUID.randomUUID();
    private List<ItemStack> paidOriginals=List.of();
    private boolean paymentRecorded;
    private boolean completed;
    private long custodyRevision;
    private boolean placementActive,retired;
    private Level refundLevel;
    private BlockPos refundOrigin;
    public long custodyRevision(){return custodyRevision;}
    protected LaunchedItem(){}
    protected LaunchedItem(BlockPos start,BlockPos target,ItemStack stack){this.target=target.immutable();this.stack=stack.copy();totalTicks=ticksRemaining=(int)Math.max(10,Math.sqrt(Math.sqrt(target.distSqr(start)))*4);}
    public UUID jobId(){return jobId;}
    public void paid(SchematicPayment.Receipt receipt){if(retired||paymentRecorded)throw new IllegalStateException("Schematic launch already owns original material custody");paidOriginals=receipt.consumed();onPaid();paymentRecorded=true;}
    protected void onPaid(){}
    public List<ItemStack> paidOriginals(){return paidOriginals.stream().map(ItemStack::copy).toList();}
    public boolean update(Level level){
        if(retired)return false;if(completed)return true;if(ticksRemaining>0){ticksRemaining--;return false;}if(level.isClientSide()||!LoadedLevelAccess.isLoaded(level,target))return false;if(!paymentRecorded)throw new IllegalStateException("Unpaid schematic launch cannot place native content");
        placementActive=true;
        try{if(!place(level))return false;handoffAll();completed=true;return true;}
        finally{placementActive=false;if(retired)refundRetained();}
    }
    /** A native removal callback can run inside placement. Its refund waits for actual publication accounting. */
    public void ownerDestroyed(Level level,BlockPos origin){
        if(retired)return;retired=true;refundLevel=level;refundOrigin=origin.immutable();
        if(!placementActive)refundRetained();
    }
    private void refundRetained(){
        if(refundLevel==null)return;var originals=paidOriginals;paidOriginals=List.of();custodyRevision++;
        for(var original:originals)Block.popResource(refundLevel,refundOrigin,original);
        refundLevel=null;refundOrigin=null;
    }
    protected final void publicationChanged(){custodyRevision++;}
    /** Consume only original material that has actually crossed a native publication boundary. */
    /** Source matching runs before publication; applying this canonical job's plan calls no addon/native code. */
    protected final class PreparedHandoff {
        private final List<ItemStack> source,remaining;
        private final long expectedRevision;
        private final int transferred;
        private boolean applied;
        private PreparedHandoff(List<ItemStack> source,List<ItemStack> remaining,long revision,int transferred){this.source=source;this.remaining=remaining;expectedRevision=revision;this.transferred=transferred;}
        public boolean ready(){return !applied&&paidOriginals==source&&custodyRevision==expectedRevision;}
        /** The executing owner's scope prevents any competing source writer until this actual acceptance. */
        public void apply(){if(applied)return;applied=true;if(transferred>0){paidOriginals=remaining;custodyRevision++;}}
    }
    protected final PreparedHandoff prepareHandoff(com.simibubi.create.content.schematics.requirement.ItemRequirement requirement){
        var source=paidOriginals;long revision=custodyRevision;
        var remaining=source.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));int transferred=0;
        for(var needed:requirement.getRequiredItems()){
            if(needed.usage!=com.simibubi.create.content.schematics.requirement.ItemRequirement.ItemUseType.CONSUME)continue;int amount=needed.stack.getCount();
            for(var original:remaining)if(!original.isEmpty()&&needed.matches(original)){int count=Math.min(amount,original.getCount());original.shrink(count);transferred+=count;amount-=count;if(amount==0)break;}
        }
        return new PreparedHandoff(source,remaining.stream().filter(original->!original.isEmpty()).toList(),revision,transferred);
    }
    protected final void handoff(com.simibubi.create.content.schematics.requirement.ItemRequirement requirement){prepareHandoff(requirement).apply();}
    protected final void handoffAll(){if(!paidOriginals.isEmpty()){paidOriginals=List.of();custodyRevision++;}}
    protected abstract boolean place(Level level);
    public CompoundTag serializeNBT(HolderLookup.Provider registries){return serializeNBT(registries,true);}
    public void write(ValueOutput out,boolean privateData){
        out.putInt("TotalTicks",totalTicks);out.putInt("TicksLeft",ticksRemaining);out.store("Stack",ItemStack.OPTIONAL_CODEC,stack);out.store("Target",BlockPos.CODEC,target);out.store("JobId",UUIDUtil.CODEC,jobId);out.putBoolean("Completed",completed);out.putBoolean("PaymentRecorded",paymentRecorded);out.putBoolean("Retired",retired);if(privateData&&!paidOriginals.isEmpty())out.store("PaidOriginals",ItemStack.OPTIONAL_CODEC.listOf(),paidOriginals);
        if(this instanceof ForBlockState block){if(block.publishedState!=null)out.store("PublishedState",CompoundTag.CODEC,NbtUtils.writeBlockState(block.publishedState));out.store("BlockState",CompoundTag.CODEC,NbtUtils.writeBlockState(block.state));if(block.data!=null){var data=block.data.copy();data.remove("x");data.remove("y");data.remove("z");data.remove("id");out.store("Data",CompoundTag.CODEC,data);}}
        if(this instanceof ForBelt belt){out.putInt("Length",belt.length);out.putBoolean("BeltPublicationStarted",belt.publicationStarted);out.putIntArray("Casing",Arrays.stream(belt.casings).mapToInt(Enum::ordinal).toArray());}
        if(this instanceof ForEntity launched){if(launched.entity!=null){var entityData=out.child("Entity");if(!launched.entity.save(entityData))throw new IllegalArgumentException("Owned native schematic entity refuses durable saving");NbtUtils.addCurrentDataVersion(entityData);}else if(launched.deferredTag!=null)out.store("Entity",CompoundTag.CODEC,launched.deferredTag);}
    }
    public CompoundTag serializeNBT(HolderLookup.Provider registries,boolean privateData){var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,registries);write(out,privateData);return out.buildResult();}
    protected void readNBT(CompoundTag tag,HolderLookup.Provider registries,HolderGetter<Block> blocks){target=tag.read("Target",BlockPos.CODEC).orElseThrow(()->new IllegalArgumentException("Missing actual schematic launch target"));totalTicks=tag.getIntOr("TotalTicks",-1);ticksRemaining=tag.getIntOr("TicksLeft",-1);if(totalTicks<0||ticksRemaining<0||ticksRemaining>totalTicks)throw new IllegalArgumentException("Invalid native launch duration");stack=tag.read("Stack",ItemStack.OPTIONAL_CODEC,registries.createSerializationContext(NbtOps.INSTANCE)).orElse(ItemStack.EMPTY);jobId=tag.read("JobId",UUIDUtil.CODEC).orElseGet(UUID::randomUUID);var data=tag.read("PaidOriginals",ItemStack.OPTIONAL_CODEC.listOf(),registries.createSerializationContext(NbtOps.INSTANCE));if(tag.contains("PaidOriginals")&&data.isEmpty())throw new IllegalArgumentException("Malformed original paid schematic launch custody");paidOriginals=data.orElse(List.of()).stream().filter(item->!item.isEmpty()).map(ItemStack::copy).toList();paymentRecorded=tag.getBooleanOr("PaymentRecorded",true);retired=tag.getBooleanOr("Retired",false);if(retired&&!paidOriginals.isEmpty())throw new IllegalArgumentException("Retired schematic launch still claims original custody");completed=tag.getBooleanOr("Completed",false);if(completed&&!paidOriginals.isEmpty())throw new IllegalArgumentException("Completed native schematic launch still claims paid original custody");}
    public static LaunchedItem fromNBT(CompoundTag tag,HolderLookup.Provider registries,HolderGetter<Block> blocks){LaunchedItem job=tag.contains("Length")?new ForBelt():tag.contains("BlockState")?new ForBlockState():new ForEntity();job.readNBT(tag,registries,blocks);return job;}
    public static class ForBlockState extends LaunchedItem {
        public BlockState state;public CompoundTag data;
        protected BlockState publishedState;
        protected ForBlockState(){}
        public ForBlockState(BlockPos start,BlockPos target,ItemStack stack,BlockState state,CompoundTag data){super(start,target,stack);this.state=state;this.data=data==null?null:data.copy();}
        protected boolean retainsPublishedBase(Level level){return publishedState==null||LoadedLevelAccess.blockState(level,target)==publishedState;}
        protected SchematicPlacement.Publication publication(BlockState materialState,Level level){
            BlockEntity planned=null;if(data!=null&&materialState.getBlock() instanceof net.minecraft.world.level.block.EntityBlock factory){planned=factory.newBlockEntity(target,materialState);if(planned!=null)planned.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),data.copy()));}
            var source=planned;
            return new SchematicPlacement.Publication(){
            @Override public void baseAccepted(BlockState actual){if(publishedState==null){handoff(com.simibubi.create.content.schematics.requirement.ItemRequirement.of(materialState,null));publishedState=actual;publicationChanged();}}
            @Override public void dataAccepted(){handoffAll();}
            @Override public void partialDataAccepted(BlockEntity actual){handoff(com.simibubi.create.content.schematics.SchematicPublication.installedAdditional(materialState,source,actual));}
        };}
        @Override protected boolean place(Level level){return retainsPublishedBase(level)&&SchematicPlacement.placeBlock(level,state,target,stack,data,publication(state,level));}
        @Override protected void readNBT(CompoundTag tag,HolderLookup.Provider registries,HolderGetter<Block> blocks){super.readNBT(tag,registries,blocks);var encoded=tag.getCompound("BlockState").orElseThrow(()->new IllegalArgumentException("Missing actual launch block state"));var id=net.minecraft.resources.Identifier.tryParse(encoded.getStringOr("id",""));if(id==null||!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id))throw new IllegalArgumentException("Paid launch references an unavailable actual block");state=NbtUtils.readBlockState(blocks,encoded);publishedState=tag.getCompound("PublishedState").map(value->{var identity=net.minecraft.resources.Identifier.tryParse(value.getStringOr("id",""));if(identity==null||!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(identity))throw new IllegalArgumentException("Actual published schematic base requires an unavailable block");return NbtUtils.readBlockState(blocks,value);}).orElse(null);data=tag.getCompound("Data").map(CompoundTag::copy).orElse(null);}
    }
    public static final class ForBelt extends ForBlockState {
        public int length;public BeltBlockEntity.CasingType[] casings;
        private boolean publicationStarted;
        private ForBelt(){}
        public ForBelt(BlockPos start,BlockPos target,ItemStack stack,BlockState state,BeltBlockEntity.CasingType[] casings){super(start,target,stack,state,null);this.casings=casings.clone();length=casings.length;validate();var offset=step();if((double)(length-1)*(length-1)*offset.distSqr(BlockPos.ZERO)>=(double)BeltConnectorItem.maxLength()*BeltConnectorItem.maxLength())throw new IllegalArgumentException("New belt launch exceeds configured connector distance");}
        private BlockPos step(){
            if(!state.is(AllBlocks.BELT)||(state.getValue(BeltBlock.PART)!=BeltPart.START&&state.getValue(BeltBlock.PART)!=BeltPart.END))throw new IllegalArgumentException("Actual belt launch requires a native endpoint state");
            return Objects.requireNonNull(BeltBlock.nextSegmentPosition(state,BlockPos.ZERO,state.getValue(BeltBlock.PART)==BeltPart.START));
        }
        private void validate(){
            if(length<2||casings.length!=length||Arrays.stream(casings).anyMatch(Objects::isNull))throw new IllegalArgumentException("Invalid actual source belt launch length/casings");
            var offset=step();
            try{Math.addExact(target.getX(),Math.multiplyExact(offset.getX(),length-1));Math.addExact(target.getY(),Math.multiplyExact(offset.getY(),length-1));Math.addExact(target.getZ(),Math.multiplyExact(offset.getZ(),length-1));}
            catch(ArithmeticException corrupt){throw new IllegalArgumentException("Persisted belt geometry overflows actual coordinates",corrupt);}
        }
        private List<BlockPos> storedChain(){
            validate();var offset=step();var chain=new ArrayList<BlockPos>();
            for(int i=0;i<length;i++)chain.add(target.offset(offset.multiply(i)));
            return chain;
        }
        @Override protected void readNBT(CompoundTag tag,HolderLookup.Provider registries,HolderGetter<Block> blocks){
            super.readNBT(tag,registries,blocks);publicationStarted=tag.getBooleanOr("BeltPublicationStarted",publishedState!=null);length=tag.getIntOr("Length",0);
            var values=tag.getIntArray("Casing").orElse(new int[0]);if(length<2||values.length!=length)throw new IllegalArgumentException("Invalid native belt launch length/casing map");
            casings=new BeltBlockEntity.CasingType[length];for(int i=0;i<length;i++){int ordinal=values[i];if(ordinal<0||ordinal>=BeltBlockEntity.CasingType.values().length)throw new IllegalArgumentException("Invalid native belt casing");casings[i]=BeltBlockEntity.CasingType.values()[ordinal];}validate();
        }
        private record Cell(BlockState state,CompoundTag data){}
        private static boolean writeOwned(Level level,BlockPos pos,BlockState block,Map<BlockPos,BlockState> expected,Map<BlockPos,BlockEntity> owners,Set<BlockPos> published){try{return level.setBlock(pos,block,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);}finally{if(LoadedLevelAccess.blockState(level,pos)==block){expected.put(pos,block);owners.put(pos,com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,pos));published.add(pos);}}}
        private boolean finishPublished(Level level,List<BlockPos> chain){
            // Once any unrolled-back geometry was accepted, retry may finalize existing native
            // owners, but must never manufacture a removed segment or another refundable connector.
            for(int i=0;i<chain.size();i++){
                var pos=chain.get(i);if(!LoadedLevelAccess.isLoaded(level,pos)||!level.isInValidBounds(pos)||!level.getWorldBorder().isWithinBounds(pos))return false;
                var current=LoadedLevelAccess.blockState(level,pos);if(!current.is(AllBlocks.BELT)||!(com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,pos) instanceof BeltBlockEntity))return false;
                if(i==0&&current.getValue(BeltBlock.PART)!=BeltPart.START||i==chain.size()-1&&current.getValue(BeltBlock.PART)!=BeltPart.END)return false;
                if(i>0&&i<chain.size()-1&&current.getValue(BeltBlock.PART)!=BeltPart.MIDDLE&&current.getValue(BeltBlock.PART)!=BeltPart.PULLEY)return false;
                if(i+1<chain.size()&&!chain.get(i+1).equals(BeltBlock.nextSegmentPosition(current,pos,true)))return false;
                if(i>0&&!chain.get(i-1).equals(BeltBlock.nextSegmentPosition(current,pos,false)))return false;
            }
            BeltBlock.initBelt(level,target);
            for(int i=0;i<chain.size();i++){
                if(!(com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,chain.get(i)) instanceof BeltBlockEntity belt)||belt.beltLength!=length||!belt.getController().equals(target))return false;
                if(belt.getCasingType()!=casings[i])belt.setCasingType(casings[i]);
            }
            chain.forEach(pos->level.updateNeighborsAt(pos,AllBlocks.BELT));return true;
        }
        @Override protected boolean place(Level level){
            var chain=storedChain();var end=chain.getLast();
            if(publicationStarted)return finishPublished(level,chain);
            boolean complete=true;BlockPos controller=null;
            for(int i=0;i<length;i++){
                var pos=chain.get(i);if(!LoadedLevelAccess.isLoaded(level,pos)||!level.isInValidBounds(pos)||!level.getWorldBorder().isWithinBounds(pos))return false;
                if(!(com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,pos) instanceof BeltBlockEntity belt)||belt.beltLength!=length||belt.getCasingType()!=casings[i]||!belt.getController().equals(target)&&!belt.getController().equals(end)){complete=false;continue;}
                if(controller==null)controller=belt.getController();else if(!controller.equals(belt.getController()))complete=false;
                if(i+1<length){var current=LoadedLevelAccess.blockState(level,pos);if(!chain.get(i+1).equals(BeltBlock.nextSegmentPosition(current,pos,current.getValue(BeltBlock.PART)!=BeltPart.END)))complete=false;}
            }
            if(complete)return true;
            var axis=state.getValue(BeltBlock.SLOPE)==BeltSlope.SIDEWAYS?Direction.Axis.Y:state.getValue(BeltBlock.HORIZONTAL_FACING).getClockWise().getAxis();
            var shaft=AllBlocks.SHAFT.defaultBlockState().setValue(BlockStateProperties.AXIS,axis);
            if(!LoadedLevelAccess.blockState(level,end).is(AllBlocks.SHAFT)||LoadedLevelAccess.blockState(level,end).getValue(BlockStateProperties.AXIS)!=axis)return false;
            var before=new LinkedHashMap<BlockPos,Cell>();var expected=new LinkedHashMap<BlockPos,BlockState>();var owners=new HashMap<BlockPos,BlockEntity>();var published=new LinkedHashSet<BlockPos>();
            for(var pos:chain){var block=LoadedLevelAccess.blockState(level,pos);var be=com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,pos);
                // Foreign cargo and partial unrelated belts never enter a speculative source chain edit.
                if(!com.simibubi.create.content.schematics.SchematicWorldAccess.readyForPlacement(level,block,pos))return false;
                if(block.getDestroySpeed(level,pos)==-1||block.is(AllBlocks.BELT)||be!=null&&!block.is(AllBlocks.SHAFT))return false;
                if(!pos.equals(target)&&!pos.equals(end)&&!block.canBeReplaced()&&(!block.is(AllBlocks.SHAFT)||block.getValue(BlockStateProperties.AXIS)!=axis))return false;
                before.put(pos,new Cell(block,be==null?null:be.saveWithFullMetadata(level.registryAccess())));expected.put(pos,block);owners.put(pos,be);
            }
            var diff=end.subtract(target);var slope=axis==Direction.Axis.Y?BeltSlope.SIDEWAYS:diff.getY()==0?BeltSlope.HORIZONTAL:diff.getX()==0&&diff.getZ()==0?BeltSlope.VERTICAL:diff.getY()>0?BeltSlope.UPWARD:BeltSlope.DOWNWARD;
            var run=diff.getX()!=0?Direction.Axis.X:diff.getZ()!=0?Direction.Axis.Z:axis==Direction.Axis.X?Direction.Axis.Z:Direction.Axis.X;int sign=diff.getX()==0&&diff.getZ()==0?Integer.signum(diff.getY()):Integer.signum(run.choose(diff.getX(),0,diff.getZ()));var facing=Direction.get(sign>0?Direction.AxisDirection.POSITIVE:Direction.AxisDirection.NEGATIVE,run);
            try{
                BeltBlock.editChain(level,()->{
                    try{
                        if(!writeOwned(level,target,shaft,expected,owners,published)&&LoadedLevelAccess.blockState(level,target)!=shaft)throw new IllegalStateException("Actual source belt shaft refused");expected.put(target,shaft);
                        if(!BeltConnectorItem.canConnectStored(level,target,end,chain))throw new IllegalStateException("Actual source belt endpoint/axis/speed validation refused");
                        for(int i=0;i<length;i++){
                            var pos=chain.get(i);var block=AllBlocks.BELT.defaultBlockState().setValue(BeltBlock.HORIZONTAL_FACING,facing).setValue(BeltBlock.SLOPE,slope).setValue(BeltBlock.PART,i==0?BeltPart.START:i==length-1?BeltPart.END:before.get(pos).state.is(AllBlocks.SHAFT)?BeltPart.PULLEY:BeltPart.MIDDLE).setValue(BeltBlock.WATERLOGGED,before.get(pos).state.getFluidState().is(net.minecraft.tags.FluidTags.WATER));
                            if(!writeOwned(level,pos,block,expected,owners,published)&&LoadedLevelAccess.blockState(level,pos)!=block)throw new IllegalStateException("Actual source belt segment refused at "+pos);expected.put(pos,block);
                            if(LoadedLevelAccess.blockState(level,pos)!=block||!(com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,pos) instanceof BeltBlockEntity))throw new IllegalStateException("Actual source belt segment owner refused at "+pos);
                        }
                    }catch(RuntimeException failure){
                        for(var pos:List.copyOf(before.keySet()).reversed()){
                            var actual=LoadedLevelAccess.blockState(level,pos);if(actual!=expected.get(pos)||com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,pos)!=owners.get(pos))throw new IllegalStateException("External native owner changed during source belt rollback at "+pos,failure);
                            var old=before.get(pos);if(actual!=old.state&&!level.setBlock(pos,old.state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE)&&LoadedLevelAccess.blockState(level,pos)!=old.state)throw new IllegalStateException("Original source belt footprint refuses restoration at "+pos,failure);
                            if(old.data!=null){var restored=BlockEntity.loadStatic(pos,old.state,old.data,level.registryAccess());if(restored==null)throw new IllegalStateException("Actual original shaft BE cannot be restored",failure);level.setBlockEntity(restored);}
                            published.remove(pos);
                        }
                        throw failure;
                    }
                });
            }catch(RuntimeException failure){
                if(!published.isEmpty()){
                    publicationStarted=true;
                    if(published.contains(target))handoff(com.simibubi.create.content.schematics.requirement.ItemRequirement.of(shaft,null));
                    if(published.stream().anyMatch(pos->expected.get(pos).is(AllBlocks.BELT)))handoff(new com.simibubi.create.content.schematics.requirement.ItemRequirement(com.simibubi.create.content.schematics.requirement.ItemRequirement.ItemUseType.CONSUME,AllItems.BELT_CONNECTOR));
                    publicationChanged();
                }
                Create.LOGGER.error("Actual schematic belt creation refused; only unaccepted originals remain reserved",failure);return false;
            }
            // Every real segment is present before its one source shaft receipt crosses into world custody.
            handoffAll();publicationStarted=true;publishedState=LoadedLevelAccess.blockState(level,target);publicationChanged();
            if(!finishPublished(level,chain))return false;level.playSound(null,target,net.minecraft.sounds.SoundEvents.WOOL_PLACE,net.minecraft.sounds.SoundSource.BLOCKS,.5f,1);return true;
        }
    }

    public static final class ForEntity extends LaunchedItem {
        public Entity entity;private CompoundTag deferredTag;
        private ForEntity(){}
        public ForEntity(BlockPos start,BlockPos target,ItemStack stack,Entity entity){super(start,target,stack);this.entity=Objects.requireNonNull(entity);}
        @Override protected void onPaid(){if(!entity.entityTags().contains(marker())&&!entity.addTag(marker()))throw new IllegalArgumentException("Actual native entity has no room for its durable schematic owner marker");}
        private String marker(){return "create_schematic_job:"+jobId();}
        @Override protected boolean place(Level level){
            if(!(level instanceof ServerLevel server)||!level.getWorldBorder().isWithinBounds(target)||!level.isInValidBounds(target))return false;
            if(entity==null){if(deferredTag==null)throw new IllegalStateException("Owned native schematic entity launch lacks original payload");entity=EntityType.create(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),deferredTag),level,new EntitySpawnRequest(EntitySpawnReason.STRUCTURE,false)).orElseThrow(()->new IllegalArgumentException("Owned native schematic entity could not be restored"));entity.addTag(marker());}
            if(entity.getType().onlyOpCanSetNbt())return false;var existing=server.getEntity(entity.getUUID());if(existing!=null)return existing.getType()==entity.getType()&&existing.entityTags().contains(marker());
            try{return server.addFreshEntity(entity);}finally{var published=server.getEntity(entity.getUUID());if(published!=null&&published.getType()==entity.getType()&&published.entityTags().contains(marker()))handoffAll();}
        }
        @Override protected void readNBT(CompoundTag tag,HolderLookup.Provider registries,HolderGetter<Block> blocks){super.readNBT(tag,registries,blocks);entity=null;deferredTag=tag.getCompound("Entity").map(CompoundTag::copy).orElseThrow(()->new IllegalArgumentException("Missing actual native launched entity data"));}
    }
}
