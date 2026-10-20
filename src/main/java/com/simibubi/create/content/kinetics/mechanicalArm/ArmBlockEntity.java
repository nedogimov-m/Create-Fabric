package com.simibubi.create.content.kinetics.mechanicalArm;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Original speed-driven transport state machine. Held stock participates in each real transfer. */
public class ArmBlockEntity extends KineticBlockEntity implements TransformableBlockEntity {
    private final List<ArmInteractionPoint> inputs=new ArrayList<>(),outputs=new ArrayList<>();
    private List<ArmInteractionPoint.PointData> pointData=List.of();
    private boolean updateInteractionPoints=true;
    private ItemStack heldItem=ItemStack.EMPTY;
    private Phase phase=Phase.SEARCH_INPUTS;
    private SelectionMode selectionMode=SelectionMode.ROUND_ROBIN;
    private int chasedPointIndex=-1,lastInputIndex=-1,lastOutputIndex=-1;
    private float chasedPointProgress,previousProgress;
    private boolean redstoneLocked,goggles;
    private UUID advancementOwner;
    private UUID placementOwner;
    private long placementNonce,placementDeadline;
    private ArmAngleTarget previousTarget=ArmAngleTarget.NO_TARGET;
    private float previousBaseAngle;
    private final SnapshotParticipant<ItemStack> hand=new SnapshotParticipant<>() {
        @Override protected ItemStack createSnapshot(){return heldItem.copy();}
        @Override protected void readSnapshot(ItemStack before){heldItem=before.copy();}
        @Override protected void onFinalCommit(){changed();}
    };
    public enum Phase {SEARCH_INPUTS,MOVE_TO_INPUT,SEARCH_OUTPUTS,MOVE_TO_OUTPUT,DANCING}
    public enum SelectionMode {
        ROUND_ROBIN,FORCED_ROUND_ROBIN,PREFER_FIRST;
        public String getTranslationKey(){return "create.mechanical_arm.selection_mode."+name().toLowerCase(java.util.Locale.ROOT);}
    }
    public ArmBlockEntity(BlockPos pos,BlockState state){this(AllBlockEntityTypes.MECHANICAL_ARM,pos,state);}
    public ArmBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    public boolean isActiveWorldOwner(){return level!=null&&!isRemoved()&&LoadedLevelAccess.blockEntity(level,worldPosition)==this;}
    public List<ArmInteractionPoint> inputs(){return List.copyOf(inputs);}
    public List<ArmInteractionPoint> outputs(){return List.copyOf(outputs);}
    public List<ArmInteractionPoint.PointData> interactionPoints(){return pointData;}
    public ItemStack getHeldItem(){return heldItem.copy();}
    public Phase getPhase(){return phase;}
    public SelectionMode getSelectionMode(){return selectionMode;}
    public int getTargetPointIndex(){return chasedPointIndex;}
    public float getMovementProgress(){return chasedPointProgress;}
    public boolean isRedstoneLocked(){return redstoneLocked;}
    public boolean hasGoggles(){return goggles;}
    public boolean isOnCeiling(){return getBlockState().getValue(ArmBlock.CEILING);}
    public void setGoggles(){if(!goggles){goggles=true;changed();}}
    public void setSelectionMode(SelectionMode mode){if(selectionMode!=mode){selectionMode=mode;lastInputIndex=lastOutputIndex=-1;changed();}}
    public static int getRange(){return ArmConfig.server();}
    public void changed(){setChanged();if(level!=null&&!level.isClientSide())sync();}
    public void setHeldItem(ItemStack stack,TransactionContext tx){if(!isActiveWorldOwner())throw new IllegalStateException("Removed mechanical arm cannot mutate world-owned hand stock");hand.updateSnapshots(tx);heldItem=stack.copy();}
    public ItemStack takeHeldItem(){if(!isActiveWorldOwner())return ItemStack.EMPTY;var result=heldItem.copy();heldItem=ItemStack.EMPTY;reset(Phase.SEARCH_INPUTS);return result;}
    public boolean configurePoints(List<ArmInteractionPoint.PointData> points){
        if(!isActiveWorldOwner())return false;
        var positions=new HashSet<BlockPos>();
        for(var data:points){
            var target=worldPosition.offset(data.pos());
            if(!target.closerThan(worldPosition,ArmConfig.get(level))||!positions.add(target)||ArmInteractionPoint.fromData(data,level,worldPosition)==null)return false;
        }
        pointData=List.copyOf(points);updateInteractionPoints=true;lastInputIndex=lastOutputIndex=-1;
        reset(heldItem.isEmpty()?Phase.SEARCH_INPUTS:Phase.SEARCH_OUTPUTS);initInteractionPoints();return true;
    }
    public void beginPlacement(ServerPlayer player){
        advancementOwner=player.getUUID();placementOwner=player.getUUID();placementNonce=java.util.concurrent.ThreadLocalRandom.current().nextLong();
        if(placementNonce==0)placementNonce=1;placementDeadline=level.getGameTime()+200;
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new ArmPlacementPacket.ClientBoundRequest(worldPosition,placementNonce));changed();
    }
    boolean acceptPlacement(ServerPlayer player,long nonce,List<ArmInteractionPoint.PointData> points){
        if(nonce==0||nonce!=placementNonce||!player.getUUID().equals(placementOwner)||level.getGameTime()>placementDeadline)return false;
        if(!configurePoints(points))return false;placementNonce=0;placementOwner=null;placementDeadline=0;return true;
    }
    public UUID advancementOwner(){return advancementOwner;}
    public static void tick(Level level,BlockPos pos,BlockState state,ArmBlockEntity arm){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, arm)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level,pos,state,arm);arm.initInteractionPoints();
        arm.previousProgress=arm.chasedPointProgress;
        arm.chasedPointProgress=Math.min(1,arm.chasedPointProgress+Math.min(256,Math.abs(arm.getSpeed()))/1024f);
        if(arm.chasedPointProgress<1){
            if(arm.phase==Phase.MOVE_TO_INPUT){var target=arm.target();if(target!=null)target.keepAlive();}
            return;
        }
        if(level.isClientSide()||arm.updateInteractionPoints)return;
        if(arm.phase==Phase.MOVE_TO_INPUT)arm.collectItem();
        else if(arm.phase==Phase.MOVE_TO_OUTPUT)arm.depositItem();
        else if(arm.phase==Phase.SEARCH_INPUTS||arm.phase==Phase.DANCING)arm.searchForItem();
        if(arm.chasedPointProgress>=.5&&arm.previousProgress<1)arm.lazyTick();
    
        }
    }
    public void lazyTick(){
        if(level==null||level.isClientSide()||chasedPointProgress<.5)return;
        if(phase==Phase.SEARCH_OUTPUTS)searchForDestination();
        else if(phase==Phase.SEARCH_INPUTS||phase==Phase.DANCING){
            boolean music=hasMusic(inputs)||hasMusic(outputs);
            if(music!=(phase==Phase.DANCING)){phase=music?Phase.DANCING:Phase.SEARCH_INPUTS;changed();}
        }
    }
    private boolean hasMusic(List<ArmInteractionPoint> points){
        for(var point:points)if(point instanceof AllArmInteractionPointTypes.JukeboxPoint&&point.isValid()&&LoadedLevelAccess.blockState(level,point.getPos()).getOptionalValue(JukeboxBlock.HAS_RECORD).orElse(false))return true;
        return false;
    }
    public void redstoneUpdate(){
        if(level==null||level.isClientSide())return;
        boolean powered=LoadedLevelAccess.getBestNeighborSignal(level,worldPosition)>0;
        if(powered==redstoneLocked)return;redstoneLocked=powered;changed();
        if(!powered&&phase==Phase.SEARCH_INPUTS&&chasedPointProgress>=1)searchForItem();
    }
    public void initInteractionPoints(){
        if(!updateInteractionPoints||level==null)return;
        // Retain saved definitions until all referenced chunks really exist; never create loading tickets.
        for(var data:pointData)if(!LoadedLevelAccess.isLoaded(level,worldPosition.offset(data.pos())))return;
        inputs.clear();outputs.clear();
        for(var data:pointData){
            if(!worldPosition.offset(data.pos()).closerThan(worldPosition,ArmConfig.get(level)))continue;
            var point=ArmInteractionPoint.fromData(data,level,worldPosition);if(point==null)continue;
            (point.getMode()==ArmInteractionPoint.Mode.TAKE?inputs:outputs).add(point);
        }
        if(lastInputIndex>=inputs.size())lastInputIndex=-1;
        if(lastOutputIndex>=outputs.size())lastOutputIndex=-1;
        updateInteractionPoints=false;
        if(!level.isClientSide()){
            if(outputs.size()>=10)ArmAdvancements.award(this,"arm_many_targets");
            if(outputs.stream().anyMatch(point->point instanceof AllArmInteractionPointTypes.BlazeBurnerPoint))ArmAdvancements.award(this,"arm_blaze_burner");
        }
    }
    private ArmInteractionPoint target(){
        var points=phase==Phase.MOVE_TO_INPUT?inputs:phase==Phase.MOVE_TO_OUTPUT?outputs:List.<ArmInteractionPoint>of();
        return chasedPointIndex>=0&&chasedPointIndex<points.size()?points.get(chasedPointIndex):null;
    }
    private void select(boolean input,int index){
        rememberTarget();phase=input?Phase.MOVE_TO_INPUT:Phase.MOVE_TO_OUTPUT;chasedPointIndex=index;chasedPointProgress=previousProgress=0;
        if(input)lastInputIndex=index;else lastOutputIndex=index;changed();
    }
    private void rememberTarget(){var point=target();previousTarget=point==null?ArmAngleTarget.NO_TARGET:point.getTargetAngles(worldPosition,isOnCeiling());if(point!=null)previousBaseAngle=previousTarget.baseAngle;}
    private void reset(Phase next){rememberTarget();phase=next;chasedPointIndex=-1;chasedPointProgress=previousProgress=0;changed();}
    protected void searchForItem(){
        if(redstoneLocked)return;
        int start=selectionMode==SelectionMode.PREFER_FIRST?0:lastInputIndex+1;
        int end=selectionMode==SelectionMode.FORCED_ROUND_ROBIN?Math.min(lastInputIndex+2,inputs.size()):inputs.size();
        boolean found=false;
        outer:for(int i=start;i<end;i++){
            var point=inputs.get(i);if(!point.isValid())continue;
            for(int slot=0;slot<point.getSlotCount(this);slot++)if(getDistributableAmount(point,slot)>0){select(true,i);found=true;break outer;}
        }
        if(!found&&selectionMode==SelectionMode.ROUND_ROBIN)lastInputIndex=-1;
        if(lastInputIndex==inputs.size()-1)lastInputIndex=-1;
    }
    protected void searchForDestination(){
        if(heldItem.isEmpty()){reset(Phase.SEARCH_INPUTS);return;}
        int start=selectionMode==SelectionMode.PREFER_FIRST?0:lastOutputIndex+1;
        int end=selectionMode==SelectionMode.FORCED_ROUND_ROBIN?Math.min(lastOutputIndex+2,outputs.size()):outputs.size();
        boolean found=false;
        for(int i=start;i<end;i++){
            var point=outputs.get(i);if(!point.isValid())continue;
            try(var tx=Transaction.openOuter()){
                var remainder=point.insertStack(this,heldItem,tx);
                if(ItemStack.matches(remainder,heldItem))continue;
                select(false,i);found=true;break;
            }
        }
        if(!found&&selectionMode==SelectionMode.ROUND_ROBIN)lastOutputIndex=-1;
        if(lastOutputIndex==outputs.size()-1)lastOutputIndex=-1;
    }
    protected int getDistributableAmount(ArmInteractionPoint input,int slot){
        try(var tx=Transaction.openOuter()){
            var stack=input.extract(this,slot,64,tx);if(stack.isEmpty())return 0;
            var remainder=stack.copy();
            for(var output:outputs){if(output.isValid())remainder=output.insertStack(this,remainder,tx);if(remainder.isEmpty())break;}
            return ItemStack.isSameItem(stack,remainder)?stack.getCount()-remainder.getCount():stack.getCount();
        }
    }
    protected void collectItem(){
        var point=target();
        if(point!=null&&point.isValid())for(int slot=0;slot<point.getSlotCount(this);slot++){
            int amount=getDistributableAmount(point,slot);if(amount==0)continue;
            try(var tx=Transaction.openOuter()){
                var item=point.extract(this,slot,amount,tx);if(item.isEmpty())continue;
                setHeldItem(item,tx);tx.commit();
            }
            level.playSound(null,worldPosition,SoundEvents.ITEM_PICKUP,SoundSource.BLOCKS,.125f,.5f+level.getRandom().nextFloat()*.25f);
            reset(Phase.SEARCH_OUTPUTS);return;
        }
        reset(Phase.SEARCH_INPUTS);
    }
    protected void depositItem(){
        var point=target();boolean moved=false;
        if(point!=null&&point.isValid()&&!heldItem.isEmpty())try(var tx=Transaction.openOuter()){
            var before=heldItem.copy();var remainder=point.insertStack(this,before,tx);
            if(!ItemStack.matches(before,remainder)){setHeldItem(remainder,tx);tx.commit();moved=true;}
        }
        if(moved){ArmAdvancements.award(this,"mechanical_arm");if(point instanceof AllArmInteractionPointTypes.JukeboxPoint&&heldItem.isEmpty())ArmAdvancements.award(this,"musical_arm");}
        reset(heldItem.isEmpty()?Phase.SEARCH_INPUTS:Phase.SEARCH_OUTPUTS);
    }
    /** Source two-stage resting pose and wrapped angular interpolation, consumed during native extraction. */
    public float[] angles(float partialTick){
        float progress=Mth.lerp(partialTick,previousProgress,chasedPointProgress);
        var point=target();var next=point==null?ArmAngleTarget.NO_TARGET:point.getTargetAngles(worldPosition,isOnCeiling());
        float base=previousBaseAngle+Mth.wrapDegrees((next==ArmAngleTarget.NO_TARGET?previousBaseAngle:next.baseAngle)-previousBaseAngle)*progress;
        var before=previousTarget;
        if(progress<.5)next=ArmAngleTarget.NO_TARGET;else before=ArmAngleTarget.NO_TARGET;
        float stage=progress==1?1:(progress%.5f)*2;
        return new float[]{base,Mth.lerp(stage,before.lowerArmAngle,next.lowerArmAngle),Mth.lerp(stage,before.upperArmAngle,next.upperArmAngle),before.headAngle+Mth.wrapDegrees(next.headAngle-before.headAngle)*stage};
    }
    @Override public void transform(BlockEntity blockEntity,StructureTransform transform){pointData=pointData.stream().map(point->point.transform(transform)).toList();updateInteractionPoints=true;changed();}
    @Override public void setLevel(Level level){super.setLevel(level);updateInteractionPoints=true;inputs.forEach(point->point.setLevel(level));outputs.forEach(point->point.setLevel(level));}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null&&!level.isClientSide()){Block.popResource(level,worldPosition,heldItem);heldItem=ItemStack.EMPTY;}super.preRemoveSideEffects(pos,state);}
    @Override public boolean addToTooltip(List<Component> tooltip,boolean sneaking){
        if(super.addToTooltip(tooltip,sneaking))return true;
        if(sneaking||!pointData.isEmpty())return false;
        tooltip.add(com.simibubi.create.content.equipment.goggles.GoggleTooltip.hint("hint.mechanical_arm_no_targets"));return true;
    }
    public void writeInteractionPoints(net.minecraft.nbt.CompoundTag tag){tag.put("InteractionPoints",ArmInteractionPoint.PointData.CODEC.listOf().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,pointData).getOrThrow());}
    @Override protected void write(ValueOutput out, boolean clientPacket){
        super.write(out, clientPacket);out.store("InteractionPoints",ArmInteractionPoint.PointData.CODEC.listOf(),pointData);
        out.putString("Phase",phase.name());out.putString("SelectionMode",selectionMode.name());out.putBoolean("Powered",redstoneLocked);out.putBoolean("Goggles",goggles);
        out.store("HeldItem",ItemStack.OPTIONAL_CODEC,heldItem);out.putInt("TargetPointIndex",chasedPointIndex);out.putFloat("MovementProgress",chasedPointProgress);
        out.putInt("LastInputIndex",lastInputIndex);out.putInt("LastOutputIndex",lastOutputIndex);
    }
    @Override protected void read(ValueInput in, boolean clientPacket){
        var oldData=pointData;var oldPhase=phase;int oldIndex=chasedPointIndex;var oldPoint=target();var oldTarget=oldPoint==null?ArmAngleTarget.NO_TARGET:oldPoint.getTargetAngles(worldPosition,isOnCeiling());super.read(in, clientPacket);
        boolean hasPoints=in.read("InteractionPoints",com.mojang.serialization.Codec.PASSTHROUGH).isPresent();
        var savedPoints=in.read("InteractionPoints",ArmInteractionPoint.PointData.CODEC.listOf()).orElseGet(()->{
            if(hasPoints)throw new IllegalArgumentException("Malformed saved arm interaction points");return List.of();
        });
        var positions=new HashSet<BlockPos>();
        for(var data:savedPoints){
            var absolute=worldPosition.offset(data.pos());
            if((long)worldPosition.getX()+data.pos().getX()!=absolute.getX()||(long)worldPosition.getY()+data.pos().getY()!=absolute.getY()
                    ||(long)worldPosition.getZ()+data.pos().getZ()!=absolute.getZ()||!positions.add(absolute))
                throw new IllegalArgumentException("Duplicate or overflowing saved arm target location");
        }
        pointData=List.copyOf(savedPoints);
        try{phase=Phase.valueOf(in.getStringOr("Phase","SEARCH_INPUTS"));}catch(IllegalArgumentException ignored){phase=Phase.SEARCH_INPUTS;}
        try{selectionMode=SelectionMode.valueOf(in.getStringOr("SelectionMode","ROUND_ROBIN"));}catch(IllegalArgumentException ignored){selectionMode=SelectionMode.ROUND_ROBIN;}
        // Original ScrollOptionBehaviour persists its option ordinal under its label's key.
        if(in.getStringOr("SelectionMode","").isEmpty()){int option=in.getIntOr("ScrollValue",0);if(option>=0&&option<SelectionMode.values().length)selectionMode=SelectionMode.values()[option];}
        redstoneLocked=in.getBooleanOr("Powered",false);goggles=in.getBooleanOr("Goggles",false);heldItem=in.read("HeldItem",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY).copy();
        chasedPointIndex=in.getIntOr("TargetPointIndex",-1);if(chasedPointIndex< -1)throw new IllegalArgumentException("Invalid saved arm target index");float progress=in.getFloatOr("MovementProgress",0);
        chasedPointProgress=Float.isFinite(progress)?Math.clamp(progress,0,1):0;previousProgress=chasedPointProgress;
        lastInputIndex=in.getIntOr("LastInputIndex",-1);lastOutputIndex=in.getIntOr("LastOutputIndex",-1);if(lastInputIndex< -1||lastOutputIndex< -1)throw new IllegalArgumentException("Invalid saved arm selection index");
        
        if(oldPhase!=phase||oldIndex!=chasedPointIndex||!oldData.equals(pointData)){previousTarget=oldTarget;if(oldPoint!=null)previousBaseAngle=oldTarget.baseAngle;}
        if(!oldData.equals(pointData))updateInteractionPoints=true;
        if(oldPhase==phase&&oldIndex==chasedPointIndex&&level!=null&&level.isClientSide()){var current=target();if(current!=null)current.updateCachedState();}
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("arm_blaze_burner");
        ids.add("arm_many_targets");
        ids.add("mechanical_arm");
        ids.add("musical_arm");
    }
    private final com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage canonicalAdvancementOwner=new com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage() {
        @Override public java.util.UUID getPlayerId(){return advancementOwner;}
        @Override public void setPlayerId(java.util.UUID id){advancementOwner=id;}
    };
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return canonicalAdvancementOwner;}
}
