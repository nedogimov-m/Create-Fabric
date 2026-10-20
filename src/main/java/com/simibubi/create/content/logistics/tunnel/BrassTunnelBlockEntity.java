package com.simibubi.create.content.logistics.tunnel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.funnel.*;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.*;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

public final class BrassTunnelBlockEntity extends BeltTunnelBlockEntity implements BlockEntityBehaviour.Provider,IHaveGoggleInformation,net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity {
    public record RenderData(boolean distributing,boolean left,boolean right){}
    @Override public Object getRenderData(){return new RenderData(hasDistributionBehaviour(),connectedLeft,connectedRight);}
	public enum SelectionMode implements INamedIconOptions {
		SPLIT(AllIcons.I_TUNNEL_SPLIT),
		FORCED_SPLIT(AllIcons.I_TUNNEL_FORCED_SPLIT),
		ROUND_ROBIN(AllIcons.I_TUNNEL_ROUND_ROBIN),
		FORCED_ROUND_ROBIN(AllIcons.I_TUNNEL_FORCED_ROUND_ROBIN),
		PREFER_NEAREST(AllIcons.I_TUNNEL_PREFER_NEAREST),
		RANDOMIZE(AllIcons.I_TUNNEL_RANDOMIZE),
		SYNCHRONIZE(AllIcons.I_TUNNEL_SYNCHRONIZE),

		;

		private final String translationKey;
		private final AllIcons icon;

		SelectionMode(AllIcons icon) {
			this.icon = icon;
			this.translationKey = "create.tunnel.selection_mode." + name().toLowerCase(Locale.ROOT);
		}

        public String translationKey(){return getTranslationKey();}

		@Override
		public AllIcons getIcon() {
			return icon;
		}

		@Override
		public String getTranslationKey() {
			return translationKey;
		}
	}

    private record Target(BlockPos pos,Direction face){
        static final Codec<Target> CODEC=RecordCodecBuilder.create(instance->instance.group(BlockPos.CODEC.fieldOf("Pos").forGetter(Target::pos),Direction.CODEC.fieldOf("Face").forGetter(Target::face)).apply(instance,Target::new));
    }
    private record Queue(ItemStack stack,Direction entered,int progress,boolean fresh,int previous){}
    private ItemStack stackToDistribute=ItemStack.EMPTY;
    private Direction stackEnteredFrom;
    public int distributionProgress=-1,distributionDistanceLeft,distributionDistanceRight,previousOutputIndex;
    private boolean connectedLeft,connectedRight,newItemArrived,syncedOutputActive;
    protected ScrollOptionBehaviour<SelectionMode> selectionMode;
    private final List<Target> filteredTargets=new ArrayList<>(),targets=new ArrayList<>();
    public final SidedFilteringBehaviour filtering=new SidedFilteringBehaviour(this,sides::contains);
    private final SnapshotParticipant<Queue> contents=new SnapshotParticipant<>(){
        @Override protected Queue createSnapshot(){return new Queue(stackToDistribute.copy(),stackEnteredFrom,distributionProgress,newItemArrived,previousOutputIndex);}
        @Override protected void readSnapshot(Queue snapshot){stackToDistribute=snapshot.stack.copy();stackEnteredFrom=snapshot.entered;distributionProgress=snapshot.progress;newItemArrived=snapshot.fresh;previousOutputIndex=snapshot.previous;}
        @Override protected void onFinalCommit(){changed();}
    };
    public BrassTunnelBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.BRASS_TUNNEL,pos,state);}
    public ItemStack getStackToDistribute(){return stackToDistribute.copy();}
    public Direction getStackEnteredFrom(){return stackEnteredFrom;}
    public SelectionMode getSelectionMode(){return selectionMode.get();}
    public boolean isConnected(boolean left){return left?connectedLeft:connectedRight;}
    public boolean hasDistributionBehaviour(){if(flaps.isEmpty())return false;if(connectedLeft||connectedRight)return true;var axis=getBlockState().getValue(BeltTunnelBlock.HORIZONTAL_AXIS);return flaps.keySet().stream().anyMatch(side->side.getAxis()!=axis);}
    public boolean canTakeItems(){return stackToDistribute.isEmpty()&&!syncedOutputActive;}
    public boolean canInsert(Direction side,ItemStack stack){return filtering.test(side,stack)&&(!hasDistributionBehaviour()||canTakeItems());}
    public long accept(ItemStack stack,Direction from,TransactionContext tx){
        if(stack.isEmpty()||!canTakeItems()||from!=null&&!filtering.test(from,stack))return 0;
        int amount=Math.min(stack.getCount(),stack.getMaxStackSize());contents.updateSnapshots(tx);stackToDistribute=stack.copyWithCount(amount);stackEnteredFrom=from;distributionProgress=-1;newItemArrived=true;
        if(from!=null)tx.addOuterCloseCallback(result->{if(result.wasCommitted())flap(from,true);});return amount;
    }
    @Override public Storage<ItemVariant> storage(Direction side){return new Storage<>(){
        @Override public long insert(ItemVariant variant,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(variant,amount);if(amount==0)return 0;if(!hasDistributionBehaviour()){var below=BrassTunnelBlockEntity.super.storage(side);return below==null?0:below.insert(variant,amount,tx);}return accept(variant.toStack((int)Math.min(amount,variant.toStack().getMaxStackSize())),null,tx);}
        @Override public long extract(ItemVariant variant,long amount,TransactionContext tx){var below=BrassTunnelBlockEntity.super.storage(side);return below==null?0:below.extract(variant,amount,tx);}
        @Override public Iterator<StorageView<ItemVariant>> iterator(){var below=BrassTunnelBlockEntity.super.storage(side);return below==null?Collections.emptyIterator():below.iterator();}
    };}
    private Direction adjacentDirection(boolean left){var base=Direction.get(Direction.AxisDirection.POSITIVE,getBlockState().getValue(BeltTunnelBlock.HORIZONTAL_AXIS));return left?base.getCounterClockWise():base.getClockWise();}
    public BrassTunnelBlockEntity getAdjacent(boolean left){
        if(level==null)return null;var pos=worldPosition.relative(adjacentDirection(left));var state=LoadedLevelAccess.blockState(level,pos);
        return state.getBlock() instanceof BeltTunnelBlock block&&block.brass&&state.getValue(BeltTunnelBlock.HORIZONTAL_AXIS)==getBlockState().getValue(BeltTunnelBlock.HORIZONTAL_AXIS)&&LoadedLevelAccess.blockEntity(level,pos) instanceof BrassTunnelBlockEntity adjacent&&!adjacent.isRemoved()?adjacent:null;
    }
    private boolean connection(boolean left){var pos=worldPosition.relative(adjacentDirection(left));if(!level.isLoaded(pos))return isConnected(left);var adjacent=getAdjacent(left);return !flaps.isEmpty()&&adjacent!=null&&!adjacent.flaps.isEmpty();}
    @Override public void updateTunnelConnections(){
        super.updateTunnelConnections();if(level==null)return;boolean left=connection(true),right=connection(false);boolean changed=left!=connectedLeft||right!=connectedRight;connectedLeft=left;connectedRight=right;filtering.updateFilterPresence();if(changed){
            changed();
            if(!level.isClientSide())for(boolean side:new boolean[]{true,false})if(isConnected(side)){var adjacent=getAdjacent(side);if(adjacent!=null){adjacent.updateTunnelConnections();adjacent.setSelectionMode(getSelectionMode());}}
        }
    }
    /** Returns null while a previously connected member is unloaded. */
    private List<BrassTunnelBlockEntity> group(){
        var result=new ArrayList<BrassTunnelBlockEntity>();var seen=new HashSet<BlockPos>();result.add(this);seen.add(worldPosition);
        for(boolean left:new boolean[]{true,false}){var current=this;while(current.isConnected(left)){var position=current.worldPosition.relative(current.adjacentDirection(left));if(!level.isLoaded(position))return null;var next=current.getAdjacent(left);if(next==null)break;if(!seen.add(next.worldPosition))break;result.add(next);current=next;}}
        return result;
    }
    public boolean setSelectionMode(SelectionMode mode){
        var members=group();if(members==null)return false;
        for(var member:members){member.selectionMode.setValue(mode.ordinal());member.distributionProgress=-1;member.syncedOutputActive=false;member.changed();}
        return true;
    }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours){
        super.addBehaviours(behaviours);
        selectionMode=new ScrollOptionBehaviour<>(SelectionMode.class,
            com.simibubi.create.foundation.utility.CreateLang.translateDirect("logistics.when_multiple_outputs_available"),this,new BrassTunnelModeSlot());
        selectionMode.onlyActiveWhen(this::hasDistributionBehaviour);
        selectionMode.withCallback(setting->{
            distributionProgress=-1;syncedOutputActive=false;
            if(level==null)return;
            for(boolean side:new boolean[]{true,false}) if(isConnected(side)){
                var adjacent=getAdjacent(side);
                if(adjacent!=null)adjacent.selectionMode.setValue(setting);
            }
        });
        behaviours.add(selectionMode);
    }
    public List<ItemStack> grabAllStacksOfGroup(boolean simulate){var members=group();if(members==null)return List.of();var stacks=new ArrayList<ItemStack>();for(var member:members)if(!member.stackToDistribute.isEmpty()){stacks.add(member.stackToDistribute.copy());if(!simulate){member.stackToDistribute=ItemStack.EMPTY;member.stackEnteredFrom=null;member.distributionProgress=-1;member.changed();}}return stacks;}
    public boolean testFlapFilter(Direction side,ItemStack stack){var own=filtering.get(side);if(own!=null)return own.test(stack);var adjacent=BlockEntityBehaviour.get(level,worldPosition.relative(side),FilteringBehaviour.TYPE);return adjacent==null||adjacent.test(stack);}
    public boolean flapFilterEmpty(Direction side){var own=filtering.get(side);if(own!=null)return own.getFilter().isEmpty();var adjacent=BlockEntityBehaviour.get(level,worldPosition.relative(side),FilteringBehaviour.TYPE);return adjacent==null||adjacent.getFilter().isEmpty();}
    public void tickDistribution(){
        if(distributionProgress>0){distributionProgress--;setChanged();}
        var below=belt();if(below==null||below.getSpeed()==0||stackToDistribute.isEmpty()&&!syncedOutputActive)return;
        if(distributionProgress==-1){
            filteredTargets.clear();targets.clear();distributionDistanceLeft=distributionDistanceRight=0;
            var members=group();if(members==null)return;
            if(getSelectionMode()==SelectionMode.SYNCHRONIZE){boolean allEmpty=members.stream().allMatch(t->t.stackToDistribute.isEmpty()),allFull=members.stream().allMatch(t->!t.stackToDistribute.isEmpty());if(allEmpty||allFull)for(var member:members)member.syncedOutputActive=!allEmpty;if(!syncedOutputActive)return;}
            if(stackToDistribute.isEmpty())return;
            for(var member:members)gatherOutputs(member,member==this);
            if(filteredTargets.isEmpty()&&targets.isEmpty())return;
            if(newItemArrived){newItemArrived=false;distributionProgress=2;}else{distributionProgress=com.simibubi.create.infrastructure.config.AllConfigs.server().logistics.brassTunnelTimer.get();changed();return;}
        }
        if(distributionProgress!=0||group()==null)return;
        distribute(filteredTargets);if(!stackToDistribute.isEmpty())distribute(targets);distributionProgress=-1;setChanged();
    }
    private void gatherOutputs(BrassTunnelBlockEntity tunnel,boolean own){
        var below=tunnel.belt();if(below==null)return;var movement=below.getMovementFacing();
        for(boolean sidePass:new boolean[]{true,false}){
            if(!own&&sidePass)continue;
            for(var direction:new Direction[]{Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST}){
                if(own&&sidePass==(direction.getAxis()==movement.getAxis())||direction==movement.getOpposite()||direction==movement&&below.getSpeed()==0||!tunnel.sides.contains(direction))continue;
                if(tunnel==this&&direction==stackEnteredFrom)continue;
                var offset=tunnel.worldPosition.below().relative(direction);if(!level.isLoaded(offset))continue;
                var potential=LoadedLevelAccess.blockState(level,offset.above());if(potential.getBlock() instanceof BeltFunnelBlock&&potential.getValue(BeltFunnelBlock.SHAPE)==BeltFunnelBlock.Shape.PULLING&&potential.getValue(BeltFunnelBlock.HORIZONTAL_FACING)==direction)continue;
                var input=BlockEntityBehaviour.get(level,offset,DirectBeltInputBehaviour.TYPE);
                if(input!=null?!input.canInsertFromSide(direction):direction!=movement||LoadedLevelAccess.blockState(level,offset).isFaceSturdy(level,offset,direction.getOpposite()))continue;
                var target=new Target(tunnel.worldPosition,direction);if(preview(target,stackToDistribute)<0)continue;
                (tunnel.flapFilterEmpty(direction)?targets:filteredTargets).add(target);
                int distance=tunnel.worldPosition.getX()+tunnel.worldPosition.getZ()-worldPosition.getX()-worldPosition.getZ();if(distance<0)distributionDistanceLeft=Math.max(distributionDistanceLeft,-distance);else distributionDistanceRight=Math.max(distributionDistanceRight,distance);
            }
        }
    }
    private long preview(Target target,ItemStack stack){try(var tx=Transaction.openOuter()){return insertIntoTunnel(target,stack,tx);}}
    /** Negative means the output is invalid; zero means it is valid but occupied. */
    private long insertIntoTunnel(Target target,ItemStack stack,TransactionContext tx){
        if(!(LoadedLevelAccess.blockEntity(level,target.pos) instanceof BrassTunnelBlockEntity tunnel)||!tunnel.testFlapFilter(target.face,stack))return -1;
        var below=tunnel.belt();var offset=target.pos.below().relative(target.face);if(below==null||!level.isLoaded(offset))return -1;
        var input=BlockEntityBehaviour.get(level,offset,DirectBeltInputBehaviour.TYPE);
        if(input!=null){if(!input.canInsertFromSide(target.face))return -1;long inserted=input.insert(stack,target.face,tx);if(inserted>0)tx.addOuterCloseCallback(result->{if(result.wasCommitted())tunnel.flap(target.face,false);});return inserted;}
        if(target.face!=below.getMovementFacing()||LoadedLevelAccess.blockState(level,offset).isFaceSturdy(level,offset,target.face.getOpposite()))return -1;
        tx.addOuterCloseCallback(result->{if(!result.wasCommitted())return;var controller=below.getControllerBE();if(controller==null)return;float speed=below.getDirectionAwareBeltMovementSpeed();var at=BeltHelper.getVectorForOffset(controller,below.index+(speed>0?1:0)).add(0,6f/16,0);double movement=Math.max(Math.abs(speed),.125);var item=new ItemEntity(level,at.x,at.y,at.z,stack.copy(),target.face.getStepX()*movement,.125,target.face.getStepZ()*movement);item.setDefaultPickUpDelay();level.addFreshEntity(item);tunnel.flap(target.face,true);});return stack.getCount();
    }
    private void distribute(List<Target> saved){
        var valid=new ArrayList<Target>();for(var target:saved)if(!(target.pos.equals(worldPosition)&&target.face==stackEnteredFrom)&&LoadedLevelAccess.blockEntity(level,target.pos) instanceof BrassTunnelBlockEntity)valid.add(target);
        int amount=valid.size();if(amount==0||stackToDistribute.isEmpty())return;
        var distributed=new LinkedHashMap<Target,ItemStack>();var full=new HashSet<Target>();
        int start=Math.floorMod(previousOutputIndex,amount);var mode=getSelectionMode();boolean force=mode==SelectionMode.FORCED_ROUND_ROBIN||mode==SelectionMode.FORCED_SPLIT,split=mode==SelectionMode.FORCED_SPLIT||mode==SelectionMode.SPLIT,robin=mode==SelectionMode.FORCED_ROUND_ROBIN||mode==SelectionMode.ROUND_ROBIN;
        if(mode==SelectionMode.RANDOMIZE)start=level.getRandom().nextInt(amount);if(mode==SelectionMode.PREFER_NEAREST||mode==SelectionMode.SYNCHRONIZE)start=0;
        var remaining=stackToDistribute.copy();
        for(boolean again:new boolean[]{true,false}){
            ItemStack cycle=null;int outputs=amount,leftovers=0;
            for(boolean simulate:new boolean[]{true,false}){
                if(outputs==0)break;leftovers=0;int index=start,size=remaining.getCount(),part=size/outputs,extra=size%outputs,visited=0;cycle=remaining.copy();if(!(force||split)&&simulate)continue;
                while(visited<amount){
                    var target=valid.get(index);index=(index+1)%amount;visited++;
                    if(full.contains(target)){if(split&&simulate)outputs--;continue;}
                    int count=split?part+(extra>0?1:0):size;if(count==0)break;var batch=cycle.copyWithCount(count);int planned=distributed.containsKey(target)?distributed.get(target).getCount():0;batch.grow(planned);
                    long accepted=preview(target,batch);int remainder=accepted<0?batch.getCount():batch.getCount()-(int)accepted;
                    if(accepted<0||remainder==(planned>0?count+1:count)){
                        if(force)return;if(split&&simulate)outputs--;if(!simulate)full.add(target);if(robin)break;continue;
                    }else if(remainder!=0&&!simulate)full.add(target);
                    if(!simulate){batch.shrink(remainder);distributed.put(target,batch);}
                    leftovers+=remainder;cycle.shrink(count);if(cycle.isEmpty())break;extra--;if(!split)break;
                }
            }
            if(cycle==null)return;remaining.setCount(cycle.getCount()+leftovers);if(leftovers==0&&again||!split)break;
        }
        // Commit actual output amounts and decrement the owned queue in the same transaction.
        try(var tx=Transaction.openOuter()){
            int moved=0;for(var entry:distributed.entrySet()){long accepted=insertIntoTunnel(entry.getKey(),entry.getValue(),tx);if(accepted>0)moved+=(int)accepted;}
            if(moved>stackToDistribute.getCount())throw new IllegalStateException("Tunnel distribution exceeded its queue");contents.updateSnapshots(tx);stackToDistribute.shrink(moved);if(stackToDistribute.isEmpty())stackEnteredFrom=null;previousOutputIndex=(previousOutputIndex+1)%amount;tx.commit();
        }
    }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null&&!level.isClientSide()){Block.popResource(level,worldPosition,stackToDistribute);stackToDistribute=ItemStack.EMPTY;stackEnteredFrom=null;}super.preRemoveSideEffects(pos,state);}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putBoolean("ConnectedLeft",connectedLeft);out.putBoolean("ConnectedRight",connectedRight);out.putBoolean("SyncedOutput",syncedOutputActive);out.store("StackToDistribute",ItemStack.OPTIONAL_CODEC,stackToDistribute);if(stackEnteredFrom!=null)out.store("StackEnteredFrom",Direction.CODEC,stackEnteredFrom);out.putInt("DistributionProgress",distributionProgress);out.putInt("PreviousIndex",previousOutputIndex);out.putInt("DistanceLeft",distributionDistanceLeft);out.putInt("DistanceRight",distributionDistanceRight);out.putBoolean("NewItemArrived",newItemArrived);out.store("FilteredTargets",Target.CODEC.listOf(),filteredTargets);out.store("Targets",Target.CODEC.listOf(),targets);}
    @Override protected void read(ValueInput in, boolean clientPacket){var previous=getRenderData();connectedLeft=in.getBooleanOr("ConnectedLeft",false);connectedRight=in.getBooleanOr("ConnectedRight",false);syncedOutputActive=in.getBooleanOr("SyncedOutput",false);stackToDistribute=in.read("StackToDistribute",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);stackEnteredFrom=in.read("StackEnteredFrom",Direction.CODEC).orElse(null);distributionProgress=Math.clamp(in.getIntOr("DistributionProgress",-1),-1,10);previousOutputIndex=Math.max(0,in.getIntOr("PreviousIndex",0));distributionDistanceLeft=in.getIntOr("DistanceLeft",0);distributionDistanceRight=in.getIntOr("DistanceRight",0);newItemArrived=in.getBooleanOr("NewItemArrived",false);filteredTargets.clear();filteredTargets.addAll(in.read("FilteredTargets",Target.CODEC.listOf()).orElse(List.of()));targets.clear();targets.addAll(in.read("Targets",Target.CODEC.listOf()).orElse(List.of()));if(level!=null&&level.isClientSide()&&!previous.equals(getRenderData()))level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_ALL);
        super.read(in, clientPacket);
    }
    @Override public boolean addToGoggleTooltip(List<Component> tooltip,boolean sneaking){var stacks=grabAllStacksOfGroup(true);if(stacks.isEmpty())return false;tooltip.add(Component.translatable("create.tooltip.brass_tunnel.contains"));for(var stack:stacks)tooltip.add(Component.translatable("create.tooltip.brass_tunnel.contains_entry",stack.getHoverName(),stack.getCount()));tooltip.add(Component.translatable("create.tooltip.brass_tunnel.retrieve"));return true;}
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (filtering != null) behaviours.add(filtering);
    }
}
