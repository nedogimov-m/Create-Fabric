package com.simibubi.create.content.logistics.chute;

import com.simibubi.create.*;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.fan.*;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.funnel.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** One owned moving stack; every inventory handoff shares the caller's Fabric transaction. */
public class ChuteBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements IHaveGoggleInformation,Clearable,com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.Provider {
    protected ItemStack item=ItemStack.EMPTY;
    protected float itemPosition,previousPosition,pull,push,bottomPullDistance;
    public final com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour inventoryTracker=new com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour(this);
    private boolean airflowDirty=true,canPickUpItems;
    private long lastAirflowCheck=Long.MIN_VALUE;
    private int entitySearchCooldown;
    private BlockPos surfaceBelow;
    private record Contents(ItemStack item,float position,float previous){}
    private final SnapshotParticipant<Contents> contents=new SnapshotParticipant<>(){
        @Override protected Contents createSnapshot(){return new Contents(item.copy(),itemPosition,previousPosition);}
        @Override protected void readSnapshot(Contents snapshot){item=snapshot.item.copy();itemPosition=snapshot.position;previousPosition=snapshot.previous;}
        private boolean closingCommittedOuter,committedItemChange;
        @Override public void onClose(TransactionContext transaction,TransactionContext.Result result){
            closingCommittedOuter=transaction.nestingDepth()==0&&result.wasCommitted();
            try{super.onClose(transaction,result);}finally{closingCommittedOuter=false;}
        }
        @Override protected void releaseSnapshot(Contents snapshot){
            if(closingCommittedOuter)committedItemChange=!ItemStack.matches(snapshot.item,item);
        }
        @Override protected void onFinalCommit(){
            boolean itemChanged=committedItemChange;committedItemChange=false;
            inventoryTracker.reset();changed();if(itemChanged)awardChute();
        }
    };
    public ChuteBlockEntity(BlockPos pos,BlockState state){this(AllBlockEntityTypes.CHUTE,pos,state);}
    protected ChuteBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    public ItemStack getItem(){return item.copy();}
    public float getItemPosition(float partial){return previousPosition+(itemPosition-previousPosition)*partial;}
    public float getPull(){return pull;}
    public float getPush(){return push;}
    public float getBottomPullDistance(){return bottomPullDistance;}
    public float getItemMotion(){float motion=(push+pull)/64;return (Math.clamp(motion,-20,20)+(motion<=0?-4:0))/20;}
    protected boolean canActivate(){return true;}
    public boolean canAcceptItem(ItemStack stack){return !isRemoved()&&item.isEmpty();}
    protected int getExtractionAmount(){return 16;}
    protected boolean isExact(){return false;}
    public final SingleSlotStorage<ItemVariant> itemHandler=new SingleSlotStorage<>(){
        @Override public boolean isResourceBlank(){return item.isEmpty();}
        @Override public ItemVariant getResource(){return ItemVariant.of(item);}
        @Override public long getAmount(){return item.getCount();}
        @Override public long getCapacity(){return item.isEmpty()?64:item.getMaxStackSize();}
        @Override public long insert(ItemVariant variant,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(variant,amount);return insertAt(variant,amount,getItemMotion()<0?1:0,tx);}
        @Override public long extract(ItemVariant variant,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(variant,amount);if(amount==0||item.isEmpty()||!ItemVariant.of(item).equals(variant))return 0;int moved=(int)Math.min(amount,item.getCount());contents.updateSnapshots(tx);item.shrink(moved);return moved;}
    };
    public long insertAt(ItemVariant variant,long amount,float position,TransactionContext tx){if(amount==0||!canAcceptItem(variant.toStack()))return 0;int moved=(int)Math.min(amount,variant.toStack().getMaxStackSize());contents.updateSnapshots(tx);item=variant.toStack(moved);previousPosition=itemPosition=position;return moved;}
    public void setItem(ItemStack stack){setItem(stack,getItemMotion()<0?1:0);}
    public void setItem(ItemStack stack,float position){inventoryTracker.reset();item=stack.copy();itemPosition=previousPosition=position;changed();awardChute();}
    private void awardChute(){if(level!=null&&!level.isClientSide()&&!isRemoved()&&LoadedLevelAccess.blockEntity(level,worldPosition)==this)award(com.simibubi.create.foundation.advancement.AllAdvancements.CHUTE);}
    public ItemStack takeItem(){var result=item.copy();setItem(ItemStack.EMPTY);return result;}
    public void changed(){setChanged();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    public ChuteBlockEntity getTargetChute(){var facing=AbstractChuteBlock.getChuteFacing(getBlockState());if(level==null||facing==null)return null;var target=worldPosition.below();if(facing.getAxis().isHorizontal())target=target.relative(facing.getOpposite());return LoadedLevelAccess.blockEntity(level,target) instanceof ChuteBlockEntity chute&&!chute.isRemoved()?chute:null;}
    public List<ChuteBlockEntity> getInputChutes(){
        var result=new ArrayList<ChuteBlockEntity>();if(level==null)return result;
        for(var side:Direction.values()){
            if(side==Direction.DOWN)continue;var facing=side.getOpposite();var position=worldPosition.above();if(facing.getAxis().isHorizontal())position=position.relative(facing);
            if(AbstractChuteBlock.getChuteFacing(LoadedLevelAccess.blockState(level,position))==facing&&LoadedLevelAccess.blockEntity(level,position) instanceof ChuteBlockEntity chute&&!chute.isRemoved())result.add(chute);
        }return result;
    }
    public void requestAirflowUpdate(){airflowDirty=true;lastAirflowCheck=Long.MIN_VALUE;}
    /** The graph strictly descends in Y, so two ordered passes reproduce pull sums and push shares. */
    public void refreshAirflow(){
        if(level==null||level.isClientSide()||isRemoved())return;long time=level.getGameTime();if(!airflowDirty&&lastAirflowCheck==time)return;
        var queue=new ArrayDeque<ChuteBlockEntity>();var graph=new LinkedHashMap<BlockPos,ChuteBlockEntity>();queue.add(this);
        while(!queue.isEmpty()){var chute=queue.removeFirst();if(graph.putIfAbsent(chute.worldPosition,chute)!=null)continue;var target=chute.getTargetChute();if(target!=null)queue.addLast(target);queue.addAll(chute.getInputChutes());}
        var ordered=new ArrayList<>(graph.values());ordered.sort(Comparator.comparingInt(chute->-chute.worldPosition.getY()));var old=new IdentityHashMap<ChuteBlockEntity,float[]>();ordered.forEach(chute->old.put(chute,new float[]{chute.pull,chute.push,chute.bottomPullDistance}));
        for(var chute:ordered){var fan=chute.fan(Direction.UP,Direction.DOWN);chute.pull=fan!=null?fan.getSpeed():(float)chute.getInputChutes().stream().mapToDouble(input->input.pull).sum();}
        Collections.reverse(ordered);
        for(var chute:ordered){var fan=chute.fan(Direction.DOWN,Direction.UP);var target=chute.getTargetChute();chute.push=fan!=null?fan.getSpeed():target==null?0:target.push/Math.max(1,target.getInputChutes().size());chute.rebuildSuction();chute.airflowDirty=false;chute.lastAirflowCheck=time;var before=old.get(chute);if(before[0]!=chute.pull||before[1]!=chute.push||before[2]!=chute.bottomPullDistance)chute.changed();}
    }
    private EncasedFanBlockEntity fan(Direction offset,Direction facing){var pos=worldPosition.relative(offset);var state=LoadedLevelAccess.blockState(level,pos);return state.is(AllBlocks.ENCASED_FAN)&&state.getValue(EncasedFanBlock.FACING)==facing&&LoadedLevelAccess.blockEntity(level,pos) instanceof EncasedFanBlockEntity fan&&!fan.isRemoved()?fan:null;}
    private void rebuildSuction(){
        surfaceBelow=null;bottomPullDistance=0;if(getItemMotion()<=0)return;
        float speed=pull-push;float distance=speed>=128?3:speed>=64?2:speed>=32?1:Math.max(0,speed/32);
        if(AbstractChuteBlock.isChute(LoadedLevelAccess.blockState(level,worldPosition.below())))distance=0;
        if(distance>0)distance=EncasedFanBlockEntity.flowLimit(level,worldPosition,Direction.DOWN,distance);
        bottomPullDistance=Math.max(0,distance);
        for(int i=1;i<=bottomPullDistance+1;i++){var pos=worldPosition.below(i);var entity=LoadedLevelAccess.blockEntity(level,pos);if(entity instanceof BeltBlockEntity||entity instanceof DepotBlockEntity){surfaceBelow=pos;break;}}
    }
    public boolean canDirectlyInsertCached(){return canPickUpItems;}
    private boolean canDirectlyInsert(){var state=getBlockState();return AbstractChuteBlock.isOpenChute(state)&&AbstractChuteBlock.getChuteFacing(LoadedLevelAccess.blockState(level,worldPosition.above()))!=Direction.DOWN&&!(getItemMotion()>0&&getInputChutes().isEmpty());}
    public void captureItem(ItemEntity entity){captureItem((net.minecraft.world.entity.Entity)entity);}
    public void captureItem(net.minecraft.world.entity.Entity entity){
        var held=entity instanceof ItemEntity item?item.getItem():entity instanceof com.simibubi.create.content.logistics.box.PackageEntity box?box.getBox():ItemStack.EMPTY;
        if(level==null||level.isClientSide()||!entity.isAlive()||held.isEmpty()||!canDirectlyInsertCached()||!canAcceptItem(held))return;
        if(!com.simibubi.create.content.logistics.box.PackageEntity.centerPackage(entity,net.minecraft.world.phys.Vec3.atBottomCenterOf(worldPosition.above())))return;
        var stack=held.copy();try(var tx=Transaction.openOuter()){long moved=itemHandler.insert(ItemVariant.of(stack),stack.getCount(),tx);if(moved==0)return;tx.commit();stack.shrink((int)moved);if(stack.isEmpty()){entity.discard();if(entity instanceof com.simibubi.create.content.logistics.box.PackageEntity box)com.simibubi.create.foundation.advancement.GameplayAdvancements.award(box.tossedBy.get(),"package_chute_throw");}else if(entity instanceof ItemEntity item)item.setItem(stack);}
    }
    private Storage<ItemVariant> grabCapability(Direction side){var pos=worldPosition.relative(side);var entity=LoadedLevelAccess.blockEntity(level,pos);if(entity instanceof ChuteBlockEntity&&(side!=Direction.DOWN||!(entity instanceof SmartChuteBlockEntity)||getItemMotion()>0))return null;return FunnelTransfer.find(level,pos,side.getOpposite());}
    private void handleInput(Direction side,float start){
        if(!canActivate())return;var source=grabCapability(side);if(source==null||inventoryTracker.stillWaiting(source))return;
        var extracted=ItemStack.EMPTY;
        try(var tx=Transaction.openOuter()){extracted=FunnelTransfer.extract(source,this::canAcceptItem,getExtractionAmount(),isExact(),tx);if(!extracted.isEmpty()&&insertAt(ItemVariant.of(extracted),extracted.getCount(),start,tx)==extracted.getCount())tx.commit();}
        if(extracted.isEmpty())inventoryTracker.awaitNewVersion(source);
    }

    private void searchEntities(){
        if(getItemMotion()<=0||!canActivate())return;var center=Vec3.atCenterOf(worldPosition);var box=new AABB(center.add(0,-bottomPullDistance-.5,0),center.add(0,-.5,0)).inflate(.45);
        for(var entity:level.getEntitiesOfClass(ItemEntity.class,box)){if(!entity.isAlive()||!canAcceptItem(entity.getItem()))continue;var stack=entity.getItem().copy();try(var tx=Transaction.openOuter()){long accepted=insertAt(ItemVariant.of(stack),stack.getCount(),(float)(entity.getBoundingBox().getCenter().y-worldPosition.getY()),tx);if(accepted==0)continue;tx.commit();stack.shrink((int)accepted);if(stack.isEmpty())entity.discard();else entity.setItem(stack);}break;}
    }
    private void extractFromSurface(){
        if(getItemMotion()<=0||surfaceBelow==null||!item.isEmpty()||!canActivate())return;var entity=LoadedLevelAccess.blockEntity(level,surfaceBelow);float at=-(worldPosition.getY()-surfaceBelow.getY()-1);
        if(entity instanceof DepotBlockEntity depot){var slot=depot.inventory.inputs.getFirst();if(slot.isResourceBlank()||!canAcceptItem(slot.stack()))return;try(var tx=Transaction.openOuter()){var variant=slot.getResource();long amount=slot.getAmount();if(insertAt(variant,amount,at,tx)==amount&&slot.extract(variant,amount,tx)==amount)tx.commit();}}
        if(entity instanceof BeltBlockEntity belt){var controller=belt.getControllerBE();if(controller==null||!controller.chainLoaded())return;for(var cargo:controller.getInventory().getTransportedItems()){if(cargo.beltPosition<belt.index||cargo.beltPosition>=belt.index+1||!canAcceptItem(cargo.stack))continue;try(var tx=Transaction.openOuter()){int count=cargo.stack.getCount();if(insertAt(ItemVariant.of(cargo.stack),count,at,tx)==count&&controller.getInventory().extractItem(cargo.id,count,tx)==count)tx.commit();}break;}}
    }
    private boolean insertInto(Storage<ItemVariant> target,boolean simulate){
        if(target==null||item.isEmpty()||inventoryTracker.stillWaiting(target))return false;
        boolean movedAny=false;
        try(var tx=Transaction.openOuter()){long moved=target.insert(ItemVariant.of(item),item.getCount(),tx);movedAny=moved>0;if(movedAny&&!simulate){contents.updateSnapshots(tx);item.shrink((int)moved);tx.commit();}}
        if(!movedAny)inventoryTracker.awaitNewVersion(target);return movedAny;
    }

    private boolean insertInto(ChuteBlockEntity target,float at,boolean simulate){
        if(target==null||!target.canAcceptItem(item)||item.isEmpty())return false;try(var tx=Transaction.openOuter()){int amount=item.getCount();if(target.insertAt(ItemVariant.of(item),amount,at,tx)!=amount)return false;if(!simulate){contents.updateSnapshots(tx);item=ItemStack.EMPTY;tx.commit();}return true;}
    }
    private boolean handleDownwardOutput(boolean simulate){
        if(!canActivate())return false;var direction=AbstractChuteBlock.getChuteFacing(getBlockState());if(direction==null)return false;var cap=grabCapability(Direction.DOWN);
        if(cap!=null){if(level.isClientSide()&&!isVirtual())return false;if(insertInto(cap,simulate))return true;if(direction==Direction.DOWN)return false;}
        var target=getTargetChute();if(target!=null)return insertInto(target,direction==Direction.DOWN?1:.51f,simulate);
        if(direction.getAxis().isHorizontal())return false;var pos=worldPosition.below();if(!level.isLoaded(pos))return false;var state=LoadedLevelAccess.blockState(level,pos);if(AbstractFunnelBlock.getFunnelFacing(state)==Direction.DOWN||state.isFaceSturdy(level,pos,Direction.UP,SupportType.RIGID))return false;
        return eject(false,simulate);
    }
    private boolean handleUpwardOutput(boolean simulate){
        if(!canActivate())return false;if(AbstractChuteBlock.isOpenChute(getBlockState())){var cap=grabCapability(Direction.UP);if(cap!=null){if(level.isClientSide()&&!isVirtual())return false;return insertInto(cap,simulate);}}
        var inputs=getInputChutes();ChuteBlockEntity best=null;for(var candidate:inputs)if(candidate.canAcceptItem(item)&&candidate.getItemMotion()>=0&&(best==null||best.getItemMotion()<candidate.getItemMotion()))best=candidate;
        if(best!=null)return insertInto(best,0,simulate);
        var pos=worldPosition.above();if(!level.isLoaded(pos))return false;var above=LoadedLevelAccess.blockState(level,pos);if(AbstractFunnelBlock.getFunnelFacing(above)==Direction.UP||above.isFaceSturdy(level,pos,Direction.DOWN)||!inputs.isEmpty())return false;
        return eject(true,simulate);
    }
    private boolean eject(boolean upward,boolean simulate){
        if(simulate)return true;try(var tx=Transaction.openOuter()){var stack=item.copy();var at=Vec3.atCenterOf(worldPosition).add(0,upward?.5:-.75,0);var entity=new ItemEntity(level,at.x,at.y,at.z,stack);entity.setDefaultPickUpDelay();entity.setDeltaMovement(0,upward?getItemMotion()*2:-.25,0);contents.updateSnapshots(tx);item=ItemStack.EMPTY;tx.addOuterCloseCallback(result->{if(result.wasCommitted())level.addFreshEntity(entity);});tx.commit();}return true;
    }
    private void spawnParticles(){
        float motion=getItemMotion();if(motion==0||push==0&&pull==0)return;boolean up=motion>0;float speed=Math.abs(motion);
        if(up&&AbstractChuteBlock.isOpenChute(getBlockState())&&LoadedLevelAccess.blockState(level,worldPosition.above()).getCollisionShape(level,worldPosition.above()).isEmpty())spawnAir(1,2,speed,.5f);
        if(AbstractChuteBlock.getChuteFacing(getBlockState())!=Direction.DOWN)return;
        if(AbstractChuteBlock.isTransparentChute(getBlockState()))spawnAir(up?0:1,up?1:0,speed,1);
        if(!up&&LoadedLevelAccess.blockState(level,worldPosition.below()).getCollisionShape(level,worldPosition.below()).isEmpty())spawnAir(0,-1,speed,.5f);
        if(up&&canActivate()&&bottomPullDistance>0){spawnAir(-bottomPullDistance,0,speed,2);spawnAir(-bottomPullDistance,0,speed,2);}
    }
    private void spawnAir(float start,float end,float speed,float drag){var random=level.getRandom();float x=random.nextFloat()*.5f+.25f,z=random.nextFloat()*.5f+.25f;if(random.nextFloat()<2*speed)level.addAlwaysVisibleParticle(new com.simibubi.create.foundation.particle.AirParticleData(drag,speed),worldPosition.getX()+x,worldPosition.getY()+start,worldPosition.getZ()+z,0,end-start,0);}
    public static void tick(Level level,BlockPos pos,BlockState state,ChuteBlockEntity chute){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, chute)) {
            if (!createTick.shouldTick()) return;

        if(!level.isClientSide()){
            if(chute.airflowDirty||level.getGameTime()%com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.fanBlockCheckRate.get()==0)chute.refreshAirflow();
            chute.canPickUpItems=chute.canDirectlyInsert();if(chute.entitySearchCooldown--<=0&&chute.item.isEmpty()){chute.entitySearchCooldown=5;chute.searchEntities();}chute.extractFromSurface();
        }
        if(level.isClientSide())chute.spawnParticles();
        chute.previousPosition=chute.itemPosition;
        if(chute.item.isEmpty()){if(!level.isClientSide()||chute.isVirtual()){float motion=chute.getItemMotion();chute.handleInput(motion<0?Direction.UP:Direction.DOWN,motion<0?1:0);}return;}
        float motion=chute.getItemMotion(),next=chute.itemPosition+motion;
        if(level.isClientSide()&&!chute.isVirtual()){
            if(motion<0&&next<.5f&&!chute.handleDownwardOutput(true)||motion>0&&next>.5f&&!chute.handleUpwardOutput(true))next=.5f;
            chute.itemPosition=motion<0?Math.max(0,next):Math.min(1,next);return;
        }
        if(motion<0&&next<.5f){if(!chute.handleDownwardOutput(true))next=.5f;else if(next<0){chute.handleDownwardOutput(false);next=chute.itemPosition;}}
        if(motion>0&&next>.5f){if(!chute.handleUpwardOutput(true))next=.5f;else if(next>1){chute.handleUpwardOutput(false);next=chute.itemPosition;}}
        chute.itemPosition=next;chute.setChanged();if(level.getGameTime()%5==0&&!chute.item.isEmpty())chute.changed();
    
        }
    }
    @Override public void clearContent(){setItem(ItemStack.EMPTY);}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null&&!level.isClientSide()){Block.popResource(level,worldPosition,item);item=ItemStack.EMPTY;for(var input:getInputChutes())input.requestAirflowUpdate();var target=getTargetChute();if(target!=null)target.requestAirflowUpdate();}super.preRemoveSideEffects(pos,state);}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.store("Item",ItemStack.OPTIONAL_CODEC,item);out.putFloat("ItemPosition",itemPosition);out.putFloat("Pull",pull);out.putFloat("Push",push);out.putFloat("BottomAirFlowDistance",bottomPullDistance);}
    @Override protected void read(ValueInput in, boolean clientPacket){var previous=item;item=in.read("Item",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);float position=in.getFloatOr("ItemPosition",0);itemPosition=previousPosition=Float.isFinite(position)?Math.clamp(position,-16,2):0;pull=finite(in.getFloatOr("Pull",0));push=finite(in.getFloatOr("Push",0));bottomPullDistance=Math.clamp(finite(in.getFloatOr("BottomAirFlowDistance",0)),0,3);airflowDirty=true;
        if(level!=null&&level.isClientSide()&&!item.isEmpty()&&(previous.getCount()!=item.getCount()||!ItemStack.isSameItemSameComponents(previous,item))&&level.getRandom().nextInt(3)==0){var random=level.getRandom();level.addParticle(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM,net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(item)),worldPosition.getX()+random.nextDouble(),worldPosition.getY()+random.nextDouble(),worldPosition.getZ()+random.nextDouble(),0,0,0);}
    
        super.read(in, clientPacket);
    }
    private static float finite(float value){return Float.isFinite(value)?value:0;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public boolean addToGoggleTooltip(List<Component> tooltip,boolean sneaking){tooltip.add(Component.translatable("create.tooltip.chute.header"));if(pull==0&&push==0)tooltip.add(Component.translatable("create.tooltip.chute.no_fans_attached"));if(pull!=0)tooltip.add(Component.translatable("create.tooltip.chute.fans_"+(pull>0?"pull_up":"push_down")));if(push!=0)tooltip.add(Component.translatable("create.tooltip.chute.fans_"+(push>0?"push_up":"pull_down")));tooltip.add(Component.translatable("create.tooltip.chute.items_move_"+(getItemMotion()<0?"down":"up")));if(!item.isEmpty())tooltip.add(Component.translatable("create.tooltip.chute.contains",item.getHoverName(),item.getCount()));return true;}
    private final com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour directBeltInput = com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.createNative(this);
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (inventoryTracker != null) behaviours.add(inventoryTracker);
        if (directBeltInput != null) behaviours.add(directBeltInput);
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        ids.clear();
        ids.add("chute");
    }
}
