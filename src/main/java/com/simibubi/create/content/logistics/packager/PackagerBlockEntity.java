package com.simibubi.create.content.logistics.packager;

import com.simibubi.create.*;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.funnel.FunnelTransfer;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.*;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import org.apache.commons.lang3.mutable.MutableInt;

public class PackagerBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements Clearable,BlockEntityBehaviour.Provider {
    public final com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner sourceAdvancements=new com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner(this,"packager");
    public static final int CYCLE=20;
    public boolean redstonePowered,animationInward=true;
    public int buttonCooldown,animationTicks;
    public String signBasedAddress="";
    public ItemStack heldBox=ItemStack.EMPTY,previouslyUnwrapped=ItemStack.EMPTY;
    public final List<BigItemStack> queuedExitingPackages=new LinkedList<>();
    public final PackagerItemHandler inventory=new PackagerItemHandler(this);
    public final VersionedInventoryTrackerBehaviour inventoryTracker=new VersionedInventoryTrackerBehaviour(this);
    private InventorySummary availableItems;
    private boolean activationQueued;
    long storageVersion;
    private static final ThreadLocal<Set<PackagerBlockEntity>> UNPACKING=ThreadLocal.withInitial(()->Collections.newSetFromMap(new IdentityHashMap<>()));
    private record Contents(ItemStack held,ItemStack inserted,List<BigItemStack> queued,int ticks,boolean inward){}
    private final ContentsSnapshot contents=new ContentsSnapshot();
    private final class ContentsSnapshot extends SnapshotParticipant<Contents>{
        @Override protected Contents createSnapshot(){return new Contents(heldBox.copy(),previouslyUnwrapped.copy(),copyQueue(),animationTicks,animationInward);}
        @Override protected void readSnapshot(Contents state){heldBox=state.held.copy();previouslyUnwrapped=state.inserted.copy();queuedExitingPackages.clear();state.queued.forEach(entry->queuedExitingPackages.add(new BigItemStack(entry.stack.copy(),entry.count)));animationTicks=state.ticks;animationInward=state.inward;}
        @Override protected void onFinalCommit(){storageVersion++;inventoryTracker.reset();invalidateLinkedNetworks();notifyUpdate();}
        void snapshot(TransactionContext transaction){updateSnapshots(transaction);}
    }
    public PackagerBlockEntity(BlockPos pos,BlockState state){this(AllBlockEntityTypes.PACKAGER,pos,state);}
    protected PackagerBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);redstonePowered=state.getValue(PackagerBlock.POWERED);}
    private List<BigItemStack> copyQueue(){return queuedExitingPackages.stream().map(entry->new BigItemStack(entry.stack.copy(),entry.count)).toList();}
    public void snapshot(TransactionContext transaction){contents.snapshot(transaction);}
    public BlockPos targetPos(){return worldPosition.relative(getBlockState().getValue(PackagerBlock.FACING).getOpposite());}
    public Storage<ItemVariant> targetInventory(){if(level==null||!level.isLoaded(targetPos()))return null;return FunnelTransfer.find(level,targetPos(),getBlockState().getValue(PackagerBlock.FACING));}
    public void targetChanged(){inventoryTracker.reset();invalidateLinkedNetworks();}
    public void notifyUpdate(){setChanged();if(level!=null&&!level.isClientSide()){level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());}}
    public InventorySummary getAvailableItems(){
        var storage=targetInventory();boolean transactional=Transaction.getLifecycle()!=Transaction.Lifecycle.NONE;if(!transactional&&availableItems!=null&&inventoryTracker.stillWaiting(storage))return availableItems;
        var summary=new InventorySummary();var kind=com.simibubi.create.foundation.item.WorldBlockEntityStorage.forTypeInspection(storage);if(kind instanceof com.simibubi.create.content.logistics.crate.BottomlessItemHandler bottomless){if(!bottomless.isResourceBlank())summary.add(bottomless.getResource().toStack(),BigItemStack.INF);if(!transactional){inventoryTracker.awaitNewVersion(storage);availableItems=summary;}return summary;}if(storage!=null&&!(kind instanceof PackagerItemHandler))for(var view:storage)if(!view.isResourceBlank())summary.add(view.getResource().toStack(),(int)Math.min(BigItemStack.INF,view.getAmount()));
        if(transactional)return summary;
        inventoryTracker.awaitNewVersion(storage);submitNewArrivals(availableItems,summary);return availableItems=summary;
    }
    public IdentifiedInventory getIdentifiedInventory(){var storage=targetInventory();if(storage==null)return null;return new IdentifiedInventory(com.simibubi.create.api.packager.InventoryIdentifier.get(level,new net.createmod.catnip.math.BlockFace(targetPos(),getBlockState().getValue(PackagerBlock.FACING))),storage);}
    public boolean isTargetingSameInventory(IdentifiedInventory inventory){
        if(inventory==null||inventory.handler()==null)return false;var storage=targetInventory();if(storage==null)return false;
        if(inventory.identifier()!=null)return inventory.identifier().contains(new net.createmod.catnip.math.BlockFace(targetPos(),getBlockState().getValue(PackagerBlock.FACING)));
        if(storage==inventory.handler())return true;
        var underlying=Collections.newSetFromMap(new IdentityHashMap<net.fabricmc.fabric.api.transfer.v1.storage.StorageView<ItemVariant>,Boolean>());for(var view:storage)underlying.add(view.getUnderlyingView());for(var view:inventory.handler())if(underlying.contains(view.getUnderlyingView()))return true;return false;
    }
    public void triggerStockCheck(){inventoryTracker.reset();getAvailableItems();invalidateLinkedNetworks();}
    private List<PackagerLinkBlockEntity> attachedLinks(){var links=new ArrayList<PackagerLinkBlockEntity>();if(level==null)return links;for(var side:Direction.values()){var pos=worldPosition.relative(side);var state=LoadedLevelAccess.blockState(level,pos);if(state.is(AllBlocks.STOCK_LINK)&&PackagerLinkBlock.getConnectedDirection(state)==side&&LoadedLevelAccess.blockEntity(level,pos) instanceof PackagerLinkBlockEntity link)links.add(link);}return links;}
    private void invalidateLinkedNetworks(){if(level!=null&&!level.isClientSide())for(var link:attachedLinks())GlobalLogisticsManager.get(level).invalidate(link.behaviour.freqId);}
    private void submitNewArrivals(InventorySummary before,InventorySummary after){
        if(before==null||after.isEmpty()||level==null||level.isClientSide())return;var queues=new HashSet<RequestPromiseQueue>();for(var link:attachedLinks()){var queue=GlobalLogisticsManager.get(level).getQueuedPromises(link.behaviour.freqId);if(queue!=null)queues.add(queue);}
        for(var side:Direction.values()){var pos=worldPosition.relative(side);var state=LoadedLevelAccess.blockState(level,pos);if(state.is(AllBlocks.FACTORY_GAUGE)&&com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.connectedDirection(state)==side&&LoadedLevelAccess.blockEntity(level,pos) instanceof com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity gauge){gauge.refreshRestocker();if(gauge.restocker)for(var panel:gauge.panels.values())if(panel.active)queues.add(panel.restockerPromises);}}
        for(var entry:after.getStacks()){int arrived=entry.count-before.getCountOf(entry.stack);if(arrived>0)for(var queue:queues)queue.itemEnteredSystem(entry.stack,arrived);}
    }
    public void recheckIfLinksPresent(){if(level==null||level.isClientSide()||!getBlockState().hasProperty(PackagerBlock.LINKED))return;boolean linked=!attachedLinks().isEmpty();if(linked!=getBlockState().getValue(PackagerBlock.LINKED))LoadedLevelAccess.setBlockQuiet(level,worldPosition,getBlockState().setValue(PackagerBlock.LINKED,linked));}
    public void flashLink(){var links=attachedLinks();if(!links.isEmpty())links.getFirst().flash();}
    public boolean isTooBusyFor(LogisticallyLinkedBehaviour.RequestType type){return queuedExitingPackages.size()>=switch(type){case PLAYER->50;case REDSTONE->20;case RESTOCK->10;};}
    public boolean redstoneModeActive(){return !getBlockState().getOptionalValue(PackagerBlock.LINKED).orElse(false);}
    public void activate(){redstonePowered=true;setChanged();recheckIfLinksPresent();if(!redstoneModeActive())return;if(Transaction.getLifecycle()!=Transaction.Lifecycle.NONE){activationQueued=true;return;}updateSignAddress();attemptToSend(null);if(buttonCooldown<=0)buttonCooldown=40;}
    public void lazyTick(){if(level==null||level.isClientSide())return;recheckIfLinksPresent();if(!redstonePowered)return;redstonePowered=getBlockState().getValue(PackagerBlock.POWERED);if(redstoneModeActive()){updateSignAddress();attemptToSend(null);}setChanged();}
    public static void tick(Level level,BlockPos pos,BlockState state,PackagerBlockEntity packager){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, packager)) {
            if (!createTick.shouldTick()) return;

        packager.tick();
        }
    }
    public void tick(){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, worldPosition, getBlockState(), this)) {
            if (!createTick.shouldTick()) return;

        if(!level.isClientSide()&&activationQueued){activationQueued=false;activate();}
        if(buttonCooldown>0)buttonCooldown--;
        
        if(animationTicks==0){
            previouslyUnwrapped=ItemStack.EMPTY;
            if(!level.isClientSide()&&!queuedExitingPackages.isEmpty()&&heldBox.isEmpty())try(var tx=Transaction.openOuter()){snapshot(tx);var entry=queuedExitingPackages.getFirst();heldBox=entry.stack.copy();if(--entry.count<=0)queuedExitingPackages.removeFirst();animationInward=false;animationTicks=CYCLE;tx.commit();}
            return;
        }
        if(level.isClientSide()){
            if(animationTicks==CYCLE-(animationInward?5:1))level.playLocalSound(worldPosition,AllSoundEvents.PACKAGER,SoundSource.BLOCKS,1,1,true);
            if(animationTicks==(animationInward?1:5))level.playLocalSound(worldPosition,SoundEvents.IRON_TRAPDOOR_CLOSE,SoundSource.BLOCKS,.25f,.75f,true);
        }
        animationTicks--;setChanged();if(animationTicks==0&&!level.isClientSide()){storageVersion++;notifyUpdate();}
    
        }
    }
    public boolean unwrapBox(ItemStack box,boolean simulate){try(var tx=Transaction.openOuter()){boolean accepted=unwrapBox(box,tx);if(accepted&&!simulate)tx.commit();return accepted;}}
    public boolean unwrapBox(ItemStack box,TransactionContext transaction){
        if(level==null||level.isClientSide()||animationTicks>0||!PackageItem.isPackage(box))return false;
        var items=new ArrayList<>(PackageItem.getContents(box).stream().filter(stack->!stack.isEmpty()).map(ItemStack::copy).toList());if(items.isEmpty())return true;
        var target=targetPos();if(!level.isLoaded(target))return false;var state=LoadedLevelAccess.blockState(level,target);var handler=UnpackingHandler.REGISTRY.get(state);if(handler==null)handler=UnpackingHandler.DEFAULT;
        var active=UNPACKING.get();if(!active.add(this))return false;
        try(var tx=transaction.openNested()){
            if(!handler.unpack(level,target,state,getBlockState().getValue(PackagerBlock.FACING),items,PackageItem.getOrderContext(box),tx))return false;
            received(box,tx);tx.commit();return true;
        }finally{active.remove(this);if(active.isEmpty())UNPACKING.remove();}
    }
    protected void received(ItemStack box,TransactionContext transaction){snapshot(transaction);previouslyUnwrapped=box.copy();animationInward=true;animationTicks=CYCLE;}
    public void attemptToSend(List<PackagingRequest> requests){
        if(level==null||level.isClientSide()||requests==null&&(!heldBox.isEmpty()||animationTicks!=0||buttonCooldown>0)||requests!=null&&requests.isEmpty())return;
        var source=targetInventory();if(source==null||source instanceof PackagerItemHandler)return;
        try(var tx=Transaction.openOuter()){if(packageFrom(source,requests,tx))tx.commit();}
    }
    private boolean packageFrom(Storage<ItemVariant> source,List<PackagingRequest> requests,TransactionContext transaction){
        return packageFrom(source,requests,transaction,false);
    }
    /** Private request copies may advance inside a larger purchase transaction. */
    public boolean fulfillRequests(List<PackagingRequest> requests,TransactionContext transaction){
        var source=targetInventory();if(source==null||source instanceof PackagerItemHandler)return false;
        for(int i=0;i<100&&!requests.isEmpty();i++)if(!packageFrom(source,requests,transaction,true))return false;
        return requests.isEmpty();
    }
    private boolean packageFrom(Storage<ItemVariant> source,List<PackagingRequest> requests,TransactionContext transaction,boolean immediate){
        var slots=NonNullList.withSize(PackageItem.SLOTS,ItemStack.EMPTY);boolean any=false;var forwarded=ItemStack.EMPTY;
        var counters=new IdentityHashMap<MutableInt,MutableInt>();var originals=new IdentityHashMap<PackagingRequest,PackagingRequest>();var working=new ArrayList<PackagingRequest>();
        if(requests!=null)for(var request:requests){var copy=new PackagingRequest(request.item(),counters.computeIfAbsent(request.count(),value->new MutableInt(value.intValue())),request.address(),request.linkIndex(),request.finalLink(),counters.computeIfAbsent(request.packageCounter(),value->new MutableInt(value.intValue())),request.orderId(),request.context());working.add(copy);originals.put(copy,request);}
        PackagingRequest current=working.isEmpty()?null:working.getFirst();String address=current==null?null:current.address();int order=current==null?0:current.orderId(),link=current==null?0:current.linkIndex(),fragment=current==null?0:current.packageCounter().getAndIncrement();boolean lastLink=current!=null&&current.finalLink().booleanValue(),lastFragment=false;PackageOrderWithCrafts context=current==null?null:current.context();
        outer:for(int pass=0;pass<PackageItem.SLOTS;pass++){
            boolean repeat=true;while(repeat){repeat=false;
                var candidates=new ArrayList<StorageView<ItemVariant>>();source.forEach(candidates::add);
                for(var view:candidates){
                    if(view.isResourceBlank()||view.getAmount()==0)continue;var variant=view.getResource();if(current!=null&&!com.simibubi.create.foundation.item.ItemSampleEquality.same(variant.toStack(),current.item()))continue;
                    int limit=current==null?64:Math.min(64,current.getCount());if(limit<=0)continue;
                    ItemStack extracted;try(var preview=transaction.openNested()){long amount=source.extract(variant,limit,preview);if(amount==0)continue;extracted=variant.toStack((int)amount);}
                    boolean bulky=!extracted.getItem().canFitInsideContainerItems();if(bulky&&any)continue;
                    int transferred=insertInto(slots,extracted);if(transferred==0)continue;if(source.extract(variant,transferred,transaction)!=transferred)return false;any=true;
                    if(PackageItem.isPackage(extracted))forwarded=extracted.copyWithCount(1);
                    if(current==null){if(bulky)break outer;continue;}
                    current.subtract(transferred);if(!current.isEmpty()){if(bulky)break outer;continue;}
                    lastFragment=true;working.removeFirst();if(working.isEmpty())break outer;int previous=current.packageCounter().intValue();current=working.getFirst();if(!address.equals(current.address())||order!=current.orderId())break outer;
                    current.packageCounter().setValue(previous);lastFragment=false;repeat=true;if(current.context()!=null)context=current.context();if(bulky)break outer;break;
                }
            }
        }
        if(!any){if(!immediate&&requests!=null&&current!=null){working.removeFirst();transaction.addOuterCloseCallback(result->{if(result.wasCommitted())requests.removeFirst();});return true;}return false;}
        var created=forwarded.isEmpty()?PackageItem.containingSlots(slots):forwarded;PackageItem.clearAddress(created);if(address!=null)PackageItem.addAddress(created,address);else if(!signBasedAddress.isBlank())PackageItem.addAddress(created,signBasedAddress);
        if(requests!=null){PackageItem.setOrder(created,order,link,lastLink,fragment,lastFragment,context);var retained=working.stream().map(originals::get).toList();Runnable advance=()->{counters.forEach((original,copy)->original.setValue(copy.intValue()));requests.clear();requests.addAll(retained);};if(immediate)advance.run();else transaction.addOuterCloseCallback(result->{if(result.wasCommitted())advance.run();});}
        output(created,transaction);return true;
    }
    public static int insertInto(List<ItemStack> slots,ItemStack stack){
        int remaining=stack.getCount();for(var slot:slots)if(!slot.isEmpty()&&ItemStack.isSameItemSameComponents(slot,stack)){int moved=Math.min(remaining,Math.max(0,Math.min(64,slot.getMaxStackSize())-slot.getCount()));slot.grow(moved);remaining-=moved;if(remaining==0)return stack.getCount();}
        for(int slot=0;slot<slots.size()&&remaining>0;slot++)if(slots.get(slot).isEmpty()){int moved=Math.min(remaining,Math.min(64,stack.getMaxStackSize()));slots.set(slot,stack.copyWithCount(moved));remaining-=moved;}return stack.getCount()-remaining;
    }
    protected void output(ItemStack box,TransactionContext transaction){snapshot(transaction);if(!heldBox.isEmpty()||animationTicks!=0){queuedExitingPackages.add(new BigItemStack(box.copy(),1));return;}heldBox=box.copy();animationInward=false;animationTicks=CYCLE;transaction.addOuterCloseCallback(result->{if(result.wasCommitted())sourceAdvancements.award("packager");});}
    public void updateSignAddress(){signBasedAddress="";for(var side:Direction.values()){var address=getSign(side);if(address!=null&&!address.isBlank())signBasedAddress=address;}}
    protected String getSign(Direction side){if(!(LoadedLevelAccess.blockEntity(level,worldPosition.relative(side)) instanceof SignBlockEntity sign))return null;for(var front:new SignTextSlot[]{SignTextSlot.FRONT,SignTextSlot.BACK}){var address=new StringJoiner(" ");for(var line:sign.getText(front).getMessages(false)){var text=line.getString();if(!text.isBlank())address.add(text.trim());}if(address.length()>0)return address.toString();}return null;}
    public float getTrayOffset(float partial){float cycle=animationInward?animationTicks-partial:animationTicks-5-partial;float progress=Math.clamp(cycle/(CYCLE-5)*2-1,-1,1);progress=1-progress*progress;return progress*progress;}
    public ItemStack getRenderedBox(){return animationInward?animationTicks<=CYCLE/2?ItemStack.EMPTY:previouslyUnwrapped:animationTicks>=CYCLE/2?ItemStack.EMPTY:heldBox;}
    @Override public void clearContent(){try(var tx=Transaction.openOuter()){snapshot(tx);heldBox=ItemStack.EMPTY;previouslyUnwrapped=ItemStack.EMPTY;queuedExitingPackages.clear();animationTicks=0;tx.commit();}}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null&&!level.isClientSide()){drop(heldBox);for(var entry:queuedExitingPackages)for(int i=0;i<entry.count;i++)drop(entry.stack);heldBox=ItemStack.EMPTY;queuedExitingPackages.clear();}super.preRemoveSideEffects(pos,state);}
    private void drop(ItemStack stack){if(!stack.isEmpty())level.addFreshEntity(new ItemEntity(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,stack.copy()));}
    private void writeVisuals(ValueOutput out){out.putBoolean("Active",redstonePowered);out.putBoolean("AnimationInward",animationInward);out.putInt("AnimationTicks",animationTicks);out.putInt("ButtonCooldown",buttonCooldown);out.putString("SignAddress",signBasedAddress);out.store("HeldBox",ItemStack.OPTIONAL_CODEC,heldBox);out.store("InsertedBox",ItemStack.OPTIONAL_CODEC,previouslyUnwrapped);}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);if(clientPacket){writeVisuals(out);return;}writeVisuals(out);out.putBoolean("PendingActivation",activationQueued);out.store("QueuedExitingPackages",BigItemStack.CODEC.listOf(),queuedExitingPackages);if(availableItems!=null)out.store("LastSummary",InventorySummary.CODEC,availableItems);}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);activationQueued=in.getBooleanOr("PendingActivation",false);redstonePowered=in.getBooleanOr("Active",false);animationInward=in.getBooleanOr("AnimationInward",true);animationTicks=Math.clamp(in.getIntOr("AnimationTicks",0),0,CYCLE);buttonCooldown=Math.clamp(in.getIntOr("ButtonCooldown",0),0,40);signBasedAddress=in.getStringOr("SignAddress","");heldBox=in.read("HeldBox",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);previouslyUnwrapped=in.read("InsertedBox",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);queuedExitingPackages.clear();queuedExitingPackages.addAll(in.read("QueuedExitingPackages",BigItemStack.CODEC.listOf()).orElse(List.of()));availableItems=in.read("LastSummary",InventorySummary.CODEC).orElse(null);inventoryTracker.reset();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (inventoryTracker != null) behaviours.add(inventoryTracker);
    }
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return sourceAdvancements;}
}
