package com.simibubi.create.content.kinetics.deployer;

import com.simibubi.create.*;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.content.equipment.goggles.GoggleTooltip;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltItemProcessor;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

public final class DeployerBlockEntity extends KineticBlockEntity implements BeltItemProcessor {
    private final net.createmod.catnip.animation.LerpedFloat animatedOffset=
        net.createmod.catnip.animation.LerpedFloat.linear().startWithValue(0);
    public void setAnimatedOffset(float offset) { animatedOffset.setValue(offset); }

    public enum State { WAITING,EXPANDING,RETRACTING,DUMPING }
    public enum Mode { PUNCH,USE }
    private State state=State.WAITING;
    private Mode mode=Mode.USE;
    private int timer;
    private float reach=0.75f;
    private boolean redstoneLocked;
    private boolean fistBump;
    private boolean clientHasOverflow;
    public UUID owner;
    ItemStack heldItem=ItemStack.EMPTY;
    public ItemStack filter=ItemStack.EMPTY;
    List<ItemStack> overflowItems=new ArrayList<>();
    public final DeployerItemHandler inventory=new DeployerItemHandler(this);
    private DeployerFakePlayer player;
    private List<DeployerInventory.Entry> retainedInventory=List.of();
    private int selectedSlot;
    private BlockPos breakingPos;
    private float breakingProgress;
    private ItemVariant pendingInput=ItemVariant.blank(),pendingTool=ItemVariant.blank();
    private net.minecraft.resources.Identifier pendingRecipe;
    private List<ItemStack> pendingOutputs;
    public DeployerBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.DEPLOYER,pos,state);}
    public ItemStack getHeldItem(){return heldItem;}
    boolean ownsCartActor(DeployerFakePlayer actual){return player==actual;}
    public List<ItemStack> getOverflowItems(){return overflowItems.stream().map(ItemStack::copy).toList();}
    public boolean hasOverflow(){return level!=null&&level.isClientSide()?clientHasOverflow:!overflowItems.isEmpty();}
    public State getState(){return state;}
    public Mode getMode(){return mode;}
    public int getTimer(){return timer;}
    public boolean isRedstoneLocked(){return redstoneLocked;}
    public void setHeldItem(ItemStack stack){heldItem=stack.copy();inventoryChanged();}
    public boolean accepts(ItemStack stack){return com.simibubi.create.content.logistics.filter.FilterItemStack.of(filter).test(level,stack);}
    public void setFilter(ItemStack sample){filter=sample.copyWithCount(1);inventoryChanged();}
    public void inventoryChanged(){if(player!=null)player.setItemInHand(InteractionHand.MAIN_HAND,heldItem.copy());setChanged();sync();}
    public DeployerFakePlayer getPlayer(){
        if(player==null&&level instanceof ServerLevel server){
            var actual=new DeployerFakePlayer(server,owner);
            DeployerInventory.hydrate(actual,retainedInventory,selectedSlot,heldItem);
            actual.setPos(Vec3.atCenterOf(worldPosition));
            // The real private player is ready before this retained owner relinquishes its slots.
            player=actual;retainedInventory=List.of();
        }return player;
    }
    List<ItemStack> inventoryForRemoval(){
        var items=new ArrayList<ItemStack>();
        var hand=player==null?heldItem:player.getMainHandItem();if(!hand.isEmpty())items.add(hand.copy());
        for(var item:overflowItems)if(!item.isEmpty())items.add(item.copy());
        var entries=player==null?retainedInventory:DeployerInventory.capture(player);
        for(var entry:entries)items.add(entry.item().copy());
        return items;
    }
    void releaseRemovedInventory(List<ItemStack> cleared){
        if(player!=null)DeployerInventory.clear(player);
        heldItem=ItemStack.EMPTY;retainedInventory=List.of();overflowItems=cleared;
    }
    public int getTimerSpeed(){return getSpeed()==0?0:(int)Math.clamp(Math.abs(getSpeed()*2),8,512);}
    public void changeMode(){mode=mode==Mode.USE?Mode.PUNCH:Mode.USE;clearBreaking();setChanged();sync();}
    public void redstoneUpdate(){boolean value=level!=null&&level.hasNeighborSignal(worldPosition);if(value!=redstoneLocked){redstoneLocked=value;setChanged();sync();}}
    public float getHandOffset(float partial){if(isVirtual())return animatedOffset.getValue(partial);float progress=state==State.EXPANDING?1-(timer-partial*getTimerSpeed())/1000f:state==State.RETRACTING?(timer-partial*getTimerSpeed())/1000f:0;if(fistBump&&state==State.EXPANDING)progress*=progress;float handLength=mode==Mode.PUNCH?3f/16:heldItem.isEmpty()?0:4f/16;return Math.min(Math.clamp(progress,0,1)*(reach+handLength),21f/16);}
    public boolean isFistBumping(){return fistBump;}
    private DeployerBlockEntity fistPartner(){for(int distance=2;distance<5;distance++){var pos=worldPosition.relative(facing(),distance);if(!level.isLoaded(pos))return null;if(LoadedLevelAccess.blockEntity(level,pos) instanceof DeployerBlockEntity partner)return partner;}return null;}
    private boolean startFistBump(){var partner=fistPartner();if(partner==null||partner.facing()!=facing().getOpposite()||partner.mode!=Mode.PUNCH||partner.getSpeed()==0)return false;float reach=(float)(Math.sqrt(worldPosition.distSqr(partner.worldPosition))-2)/2;for(var hand:List.of(this,partner)){hand.fistBump=true;hand.reach=reach;hand.state=State.EXPANDING;hand.timer=1000;hand.setChanged();hand.sync();}return true;}
    private void finishFistBump(){var partner=fistPartner();if(partner==null||!partner.fistBump||partner.state!=State.EXPANDING||partner.timer>0)return;fistBump=false;partner.fistBump=false;partner.state=State.RETRACTING;partner.timer=1000;partner.setChanged();partner.sync();com.simibubi.create.foundation.advancement.GameplayAdvancements.awardOwner(level,owner,"fist_bump");level.playSound(null,BlockPos.containing(Vec3.atCenterOf(worldPosition).add(Vec3.atCenterOf(partner.worldPosition)).scale(0.5)),SoundEvents.PLAYER_ATTACK_NODAMAGE,SoundSource.BLOCKS,0.75f,0.75f);}
    private Direction facing(){return getBlockState().getValue(DeployerBlock.FACING);}
    private BlockPos target(){return worldPosition.relative(facing(),2);}
    private boolean processingSurface(){var be=LoadedLevelAccess.blockEntity(level,target());return facing()==Direction.DOWN&&mode==Mode.USE&&(be instanceof BeltBlockEntity||BlockEntityBehaviour.get(be,DepotBehaviour.TYPE)!=null);}
    private void start(){
        state=State.EXPANDING;var direction=Vec3.atLowerCornerOf(facing().getUnitVec3i());var start=Vec3.atCenterOf(worldPosition).add(direction.scale(1.5));var end=Vec3.atCenterOf(worldPosition).add(direction.scale(2.5));
        var hit=level.clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,getPlayer()));reach=(float)(0.5+Math.min(hit.getLocation().distanceTo(start),0.75));timer=1000;setChanged();sync();
    }
    @Override public boolean onBeltItemReceived(TransportedItemStack item){
        if(getSpeed()==0||mode!=Mode.USE||facing()!=Direction.DOWN)return false;if(state!=State.WAITING)return true;if(redstoneLocked)return false;if(heldItem.isEmpty())return true;if(DeployerRecipes.find(this,item.stack,heldItem)==null)return false;start();return true;
    }
    @Override public boolean tryHoldBeltItem(BeltBlockEntity belt,TransportedItemStack item){return tryHoldTransportedItem(item);}
    public boolean tryHoldTransportedItem(TransportedItemStack item){
        if(getSpeed()==0||mode!=Mode.USE||facing()!=Direction.DOWN)return false;if(heldItem.isEmpty())return true;if(DeployerRecipes.find(this,item.stack,heldItem)==null)return false;if(state==State.WAITING){if(redstoneLocked)return false;start();}return true;
    }
    private List<ItemStack> outputs(DeployerRecipes.Match match,ItemStack input){
        var inputVariant=ItemVariant.of(input);var toolVariant=ItemVariant.of(heldItem);
        if(pendingOutputs==null||!pendingInput.equals(inputVariant)||!pendingTool.equals(toolVariant)||!Objects.equals(pendingRecipe,match.id())){pendingInput=inputVariant;pendingTool=toolVariant;pendingRecipe=match.id();pendingOutputs=match.roll(level.getRandom());setChanged();}
        return pendingOutputs;
    }
    private void clearPending(){pendingOutputs=null;pendingInput=ItemVariant.blank();pendingTool=ItemVariant.blank();pendingRecipe=null;}
    private boolean processSurface(){
        var surface=LoadedLevelAccess.blockEntity(level,target());BeltBlockEntity belt=null;TransportedItemStack transported=null;ItemStack input;
        var depot=BlockEntityBehaviour.get(surface,DepotBehaviour.TYPE);
        if(depot!=null){if(!depot.readyForProcessing())return false;input=depot.getHeldItemStack();}
        else if(surface instanceof BeltBlockEntity segment){belt=segment.getControllerBE();if(belt==null||!belt.chainLoaded())return false;transported=belt.getInventory().getTransportedItems().stream().filter(item->worldPosition.equals(item.heldBy)).findFirst().orElse(null);if(transported==null)return false;input=transported.stack;}
        else return false;
        var match=DeployerRecipes.find(this,input,heldItem);if(match==null){clearPending();return false;}var results=outputs(match,input);var toolBefore=heldItem.copy();
        try(var tx=Transaction.openOuter()){
            if(depot!=null){if(!depot.applyRecipeOutputs(1,results,false,tx))return false;}
            else if(!belt.getInventory().replace(transported,1,results,false,tx,false))return false;
            if(!match.keepTool()&&!heldItem.isDamageableItem())inventory.consumeOne(tx);tx.commit();
        }
        if(!match.keepTool()&&toolBefore.isDamageableItem()){var actor=getPlayer();actor.setItemInHand(InteractionHand.MAIN_HAND,heldItem.copy());actor.getMainHandItem().hurtAndBreak(1,actor,InteractionHand.MAIN_HAND);heldItem=actor.getMainHandItem().copy();}
        if(!results.isEmpty()){
            var advancement=ManualApplicationRecipe.casingAdvancement(results.getFirst());
            if(advancement!=null)award(advancement);
        }
        clearPending();inventoryChanged();
        award(AllAdvancements.DEPLOYER);
        level.playSound(null,worldPosition,SoundEvents.ITEM_PICKUP,SoundSource.BLOCKS,0.25f,0.75f);return true;
    }
    private void collectInventory(){
        if(player==null)return;for(int i=0;i<player.getInventory().getContainerSize();i++){if(i==player.getInventory().getSelectedSlot())continue;var item=player.getInventory().getItem(i);if(!item.isEmpty()){overflowItems.add(item.copy());player.getInventory().setItem(i,ItemStack.EMPTY);}}
        heldItem=player.getMainHandItem().copy();if(!heldItem.isEmpty()&&!accepts(heldItem)){overflowItems.add(heldItem);heldItem=ItemStack.EMPTY;player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);}
    }
    private void activate(){
        if(processingSurface()){processSurface();return;}if(!(level instanceof ServerLevel server)||!level.isLoaded(target()))return;
        var actor=getPlayer();actor.setItemInHand(InteractionHand.MAIN_HAND,heldItem.copy());overflowItems.addAll(DeployerDropCapture.run(server,target(),()->DeployerHandler.activate(this,actor)));
        // Original Create awards after the real handler invocation, including accepted no-op cycles.
        award(AllAdvancements.DEPLOYER);
        collectInventory();inventoryChanged();
    }
    public static void tick(Level level,BlockPos pos,BlockState blockState,DeployerBlockEntity deployer){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, blockState, deployer)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level,pos,blockState,deployer);if(deployer.getSpeed()==0)return;
        if(deployer.timer>0){deployer.timer-=deployer.getTimerSpeed();return;}if(level.isClientSide())return;
        if(!level.isLoaded(deployer.target()))return;deployer.getPlayer();
        if(deployer.state==State.WAITING){
            deployer.collectInventory();deployer.redstoneUpdate();if(!deployer.overflowItems.isEmpty()){deployer.timer=deployer.getTimerSpeed()*10;return;}
            if(deployer.processingSurface()){var below=LoadedLevelAccess.blockEntity(level,deployer.target());if(below instanceof BeltBlockEntity)return;var depot=BlockEntityBehaviour.get(below,DepotBehaviour.TYPE);if(depot==null||!depot.readyForProcessing()||BeltProcessingBehaviour.isBlocked(level,deployer.target())||DeployerRecipes.find(deployer,depot.getHeldItemStack(),deployer.heldItem)==null)return;}
            else if(deployer.mode==Mode.USE&&!DeployerHandler.shouldActivate(deployer))return;
            if(deployer.mode==Mode.PUNCH&&!deployer.fistBump&&deployer.startFistBump())return;
            if(deployer.redstoneLocked)return;deployer.start();return;
        }
        if(deployer.state==State.EXPANDING){if(deployer.fistBump)deployer.finishFistBump();deployer.activate();deployer.state=State.RETRACTING;deployer.timer=1000;deployer.setChanged();deployer.sync();return;}
        if(deployer.state==State.RETRACTING){deployer.state=State.WAITING;deployer.timer=500;deployer.setChanged();deployer.sync();}
    
        }
    }
    void clearBreaking(){if(breakingPos!=null&&level!=null&&!level.isClientSide()&&player!=null)level.destroyBlockProgress(player.getId(),breakingPos,-1);breakingPos=null;breakingProgress=0;}
    BlockPos breakingPos(){return breakingPos;}
    float breakingProgress(){return breakingProgress;}
    void breaking(BlockPos pos,float progress){breakingPos=pos;breakingProgress=progress;setChanged();}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null&&!level.isClientSide()){DeployerOutputRecovery.captureRemoved(this);clearBreaking();}super.preRemoveSideEffects(pos,state);}
    @Override public void invalidate(){clearBreaking();if(player!=null){heldItem=player.getMainHandItem().copy();selectedSlot=player.getInventory().getSelectedSlot();retainedInventory=DeployerInventory.capture(player);player.discard();player=null;}super.invalidate();}
    @Override public boolean addToGoggleTooltip(List<Component> tooltip,boolean sneaking){tooltip.add(GoggleTooltip.translate("tooltip.deployer.header"));tooltip.add(GoggleTooltip.translate("tooltip.deployer."+(mode==Mode.USE?"using":"punching")));if(!heldItem.isEmpty())tooltip.add(GoggleTooltip.translate("tooltip.deployer.contains",heldItem.getHoverName(),heldItem.getCount()));super.addToGoggleTooltip(tooltip,sneaking);return true;}
    @Override public boolean addToTooltip(List<Component> tooltip,boolean sneaking){if(super.addToTooltip(tooltip,sneaking))return true;if(getSpeed()!=0&&hasOverflow()){tooltip.add(GoggleTooltip.hint("hint.full_deployer"));return true;}return false;}
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=super.getUpdateTag(registries);tag.putBoolean("HasOverflow",hasOverflow());for(var key:new String[]{"Inventory","PlayerInventory","SelectedSlot","Overflow","PendingInput","PendingTool","PendingRecipe","PendingOutputs","BreakingPos","BreakingProgress","Owner"})tag.remove(key);return tag;}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putString("State",state.name());out.putString("Mode",mode.name());out.putInt("Timer",timer);out.putFloat("Reach",reach);out.putBoolean("RedstoneLocked",redstoneLocked);out.putBoolean("Fistbump",fistBump);out.store("HeldItem",ItemStack.OPTIONAL_CODEC,player==null?heldItem:player.getMainHandItem());out.putInt("SelectedSlot",player==null?selectedSlot:player.getInventory().getSelectedSlot());out.store("PlayerInventory",DeployerInventory.CODEC,player==null?retainedInventory:DeployerInventory.capture(player));out.store("Filter",ItemStack.OPTIONAL_CODEC,filter);out.store("Overflow",ItemStack.CODEC.listOf(),overflowItems.stream().filter(item->!item.isEmpty()).toList());if(pendingOutputs!=null){out.store("PendingInput",ItemVariant.CODEC,pendingInput);out.store("PendingTool",ItemVariant.CODEC,pendingTool);out.storeNullable("PendingRecipe",net.minecraft.resources.Identifier.CODEC,pendingRecipe);out.store("PendingOutputs",ItemStack.CODEC.listOf(),pendingOutputs);}if(breakingPos!=null){out.store("BreakingPos",BlockPos.CODEC,breakingPos);out.putFloat("BreakingProgress",breakingProgress);}}
    @Override protected void read(ValueInput in, boolean clientPacket){try{state=State.valueOf(in.getStringOr("State","WAITING"));}catch(IllegalArgumentException ignored){state=State.WAITING;}try{mode=Mode.valueOf(in.getStringOr("Mode","USE"));}catch(IllegalArgumentException ignored){mode=Mode.USE;}timer=in.getIntOr("Timer",0);float loadedReach=in.getFloatOr("Reach",0.75f);reach=Float.isFinite(loadedReach)?Math.clamp(loadedReach,0,1.25f):0.75f;redstoneLocked=in.getBooleanOr("RedstoneLocked",in.getBooleanOr("Powered",false));fistBump=in.getBooleanOr("Fistbump",false);heldItem=in.read("HeldItem",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);filter=in.read("Filter",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);overflowItems.clear();overflowItems.addAll(in.read("Overflow",ItemStack.CODEC.listOf()).orElse(List.of()));pendingOutputs=in.read("PendingOutputs",ItemStack.CODEC.listOf()).orElse(null);pendingInput=in.read("PendingInput",ItemVariant.CODEC).orElse(ItemVariant.blank());pendingTool=in.read("PendingTool",ItemVariant.CODEC).orElse(ItemVariant.blank());pendingRecipe=in.read("PendingRecipe",net.minecraft.resources.Identifier.CODEC).orElse(null);breakingPos=in.read("BreakingPos",BlockPos.CODEC).orElse(null);breakingProgress=in.getFloatOr("BreakingProgress",0);
        clientHasOverflow=in.getBooleanOr("HasOverflow",false);
        if(!clientPacket){
            selectedSlot=in.getIntOr("SelectedSlot",0);
            var retained=new ArrayList<>(in.read("PlayerInventory",DeployerInventory.CODEC).orElse(List.of()));
            // Historical native saves accepted the original sparse list. Its real hand slot is
            // authoritative, as in source Inventory.load followed by getMainHandItem.
            var legacy=in.childrenListOrEmpty("Inventory");boolean hasLegacy=in.childrenList("Inventory").isPresent();var legacyHand=ItemStack.EMPTY;var legacySlots=new HashSet<Integer>();
            for(var slot:legacy){
                var item=slot.read(ItemStack.MAP_CODEC).orElseThrow();
                int actual=DeployerInventory.originalSlot(slot.getByteOr("Slot",(byte)-1)&255);if(!legacySlots.add(actual))throw new IllegalArgumentException("Duplicate original Deployer slot");
                if(actual==0)legacyHand=item;else if(!item.isEmpty())retained.add(new DeployerInventory.Entry(actual,item));
            }
            if(hasLegacy){if(in.read("PlayerInventory",DeployerInventory.CODEC).isPresent())throw new IllegalArgumentException("Two saved Deployer inventory representations");selectedSlot=0;heldItem=legacyHand;}
            retainedInventory=DeployerInventory.checked(retained,selectedSlot);
            if(player!=null){player.discard();player=null;}
        }
    
        super.read(in, clientPacket);
        if(clientPacket&&level!=null&&level.isClientSide())in.read("Particle",ItemStack.OPTIONAL_CODEC).ifPresent(particle->{
            var direction=net.minecraft.world.phys.Vec3.atLowerCornerOf(facing().getUnitVec3i());
            com.simibubi.create.content.equipment.sandPaper.SandPaperItem.spawnParticles(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition).add(direction.scale(reach+1)),particle,level);
        });
    }
    private final com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour beltProcessing = com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.createNative(this);
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (beltProcessing != null) behaviours.add(beltProcessing);
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("train_casing_00");
        ids.add("andesite_casing");
        ids.add("brass_casing");
        ids.add("copper_casing");
        ids.add("fist_bump");
        ids.add("deployer");
        ids.add("self_deploying");
    }
    private final com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage canonicalAdvancementOwner=new com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage() {
        @Override public java.util.UUID getPlayerId(){return owner;}
        @Override public void setPlayerId(java.util.UUID id){owner=id;}
        @Override public boolean canClear(net.minecraft.world.entity.player.Player player){return false;}
    };
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return canonicalAdvancementOwner;}
}
