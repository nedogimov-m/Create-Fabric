package com.simibubi.create.content.schematics.cannon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.schematics.*;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import io.netty.buffer.ByteBuf;
import java.util.*;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.*;

/** Actual source cannon, native material transactions and persisted paid launches. */
public final class SchematicannonBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements Container,ExtendedMenuProvider<com.simibubi.create.content.schematics.table.SchematicTableMenu.Opening> {
    public static java.util.function.Consumer<SchematicannonBlockEntity> CLIENT_TICK;
    public static final int NEIGHBOUR_CHECKING=100,MAX_ANCHOR_DISTANCE=256;
    public enum State {STOPPED,PAUSED,RUNNING}
    public record SchematicannonOptions(int replaceMode,boolean skipMissing,boolean replaceBlockEntities){
        public SchematicannonOptions{if(replaceMode<0||replaceMode>3)throw new IllegalArgumentException("Invalid source cannon replacement mode");}
        public static final Codec<SchematicannonOptions> CODEC=RecordCodecBuilder.create(i->i.group(Codec.intRange(0,3).fieldOf("replace_mode").forGetter(SchematicannonOptions::replaceMode),Codec.BOOL.fieldOf("skip_missing").forGetter(SchematicannonOptions::skipMissing),Codec.BOOL.fieldOf("replace_block_entities").forGetter(SchematicannonOptions::replaceBlockEntities)).apply(i,SchematicannonOptions::new));
        public static final StreamCodec<ByteBuf,SchematicannonOptions> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.INT,SchematicannonOptions::replaceMode,ByteBufCodecs.BOOL,SchematicannonOptions::skipMissing,ByteBufCodecs.BOOL,SchematicannonOptions::replaceBlockEntities,SchematicannonOptions::new);
    }
    public final SchematicannonInventory inventory=new SchematicannonInventory(this);
    public final SchematicPrinter printer=new SchematicPrinter();
    public final List<LaunchedItem> flyingBlocks=new ArrayList<>();
    public final Set<Storage<ItemVariant>> attachedInventories=new LinkedHashSet<>();
    public final MaterialChecklist checklist=new MaterialChecklist();
    public ItemStack missingItem;
    public boolean positionNotLoaded,hasCreativeCrate,dontUpdateChecklist;
    public int remainingFuel,neighbourCheckCooldown,blocksPlaced,blocksToPlace,replaceMode=2;
    public boolean skipMissing,replaceBlockEntities;
    public float bookPrintingProgress,schematicProgress,defaultYaw;
    public String statusMsg="idle";
    public State state=State.STOPPED;
    public BlockPos previousTarget;
    private int printerCooldown;
    private boolean hasTarget,sourceFinished;
    private ItemStack activeBlueprint=ItemStack.EMPTY;
    private final Set<UUID> reportedFailedJobs=new HashSet<>();
    private record LaunchSnapshot(List<LaunchedItem> jobs,int fuel,int placed,int cooldown,boolean hasTarget,State state,String status,ItemStack missing){}
    private final SnapshotParticipant<LaunchSnapshot> paymentOwner=new SnapshotParticipant<>(){
        @Override protected LaunchSnapshot createSnapshot(){return new LaunchSnapshot(List.copyOf(flyingBlocks),remainingFuel,blocksPlaced,printerCooldown,hasTarget,state,statusMsg,missingItem==null?null:missingItem.copy());}
        @Override protected void readSnapshot(LaunchSnapshot saved){flyingBlocks.clear();flyingBlocks.addAll(saved.jobs);remainingFuel=saved.fuel;blocksPlaced=saved.placed;printerCooldown=saved.cooldown;hasTarget=saved.hasTarget;state=saved.state;statusMsg=saved.status;missingItem=saved.missing;}
        @Override protected void onFinalCommit(){notifyUpdate();}
    };
    public SchematicannonBlockEntity(BlockPos pos,BlockState block){super(AllBlockEntityTypes.SCHEMATICANNON,pos,block);}
    public boolean canPlayerUse(Player player){return level!=null&&player.level()==level&&!isRemoved()&&!player.isSpectator()&&player.mayBuild()&&player.isWithinBlockInteractionRange(worldPosition,1)&&level.mayInteract(player,worldPosition)&&(level.isClientSide()||isActualOwner());}
    private boolean isActualOwner(){return level!=null&&!level.isClientSide()&&!isRemoved()&&com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,worldPosition)==this;}
    public void notifyUpdate(){setChanged();if(isActualOwner())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    public void setState(State next){state=Objects.requireNonNull(next);if(next==State.STOPPED){printer.resetSchematic();activeBlueprint=ItemStack.EMPTY;hasTarget=false;sourceFinished=false;missingItem=null;positionNotLoaded=false;blocksPlaced=blocksToPlace=0;schematicProgress=0;statusMsg="idle";}else if(!printer.isLoaded()&&!inventory.getItem(0).isEmpty())initializePrinter(inventory.getItem(0));notifyUpdate();}
    public static void tick(Level level,BlockPos pos,BlockState block,SchematicannonBlockEntity cannon){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, block, cannon)) {
            if (!createTick.shouldTick()) return;

        if(level.isClientSide()){if(CLIENT_TICK!=null)CLIENT_TICK.accept(cannon);return;}if(!cannon.isActualOwner())return;
        if(--cannon.neighbourCheckCooldown<=0){cannon.findInventories();cannon.neighbourCheckCooldown=NEIGHBOUR_CHECKING;}
        boolean changed=false;for(var launch:List.copyOf(cannon.flyingBlocks)){if(!cannon.isActualOwner())break;int before=launch.ticksRemaining;long custodyBefore=launch.custodyRevision();try{if(launch.update(level)){cannon.flyingBlocks.remove(launch);cannon.reportedFailedJobs.remove(launch.jobId());changed=true;}else if(before!=launch.ticksRemaining||custodyBefore!=launch.custodyRevision())changed=true;}catch(RuntimeException failure){if(cannon.reportedFailedJobs.add(launch.jobId()))Create.LOGGER.error("Actual paid schematic launch remains retained after placement failure at {}",launch.target,failure);cannon.state=State.STOPPED;cannon.statusMsg="schematicErrored";changed=true;}}
        if(!cannon.isActualOwner())return;
        cannon.previousTarget=cannon.printer.getCurrentTarget();
        if(!cannon.activeBlueprint.isEmpty()&&!ItemStack.matches(cannon.activeBlueprint,cannon.inventory.getItem(0))){cannon.printer.resetSchematic();cannon.activeBlueprint=ItemStack.EMPTY;cannon.hasTarget=false;cannon.sourceFinished=false;cannon.missingItem=null;cannon.state=State.STOPPED;cannon.statusMsg="idle";changed=true;}
        if(!cannon.inventory.getItem(0).isEmpty()&&!cannon.printer.isLoaded())cannon.initializePrinter(cannon.inventory.getItem(0));
        cannon.tickPaperPrinter();
        if(cannon.printerCooldown>0)cannon.printerCooldown--;
        else if(cannon.state!=State.STOPPED&&!cannon.sourceFinished)try{for(int skipped=0;skipped<1000&&cannon.tickPrinter();skipped++){}}catch(RuntimeException failure){cannon.state=State.STOPPED;cannon.statusMsg="schematicErrored";Create.LOGGER.error("Actual schematic cannon retains source and paid launches after printer failure at {}",pos,failure);changed=true;}
        if(cannon.sourceFinished&&cannon.flyingBlocks.isEmpty())cannon.finishedPrinting();
        cannon.schematicProgress=cannon.blocksToPlace==0?0:Math.clamp(cannon.blocksPlaced/(float)cannon.blocksToPlace,0,1);
        if(changed)cannon.notifyUpdate();
    
        }
    }
    public void findInventories(){attachedInventories.clear();hasCreativeCrate=false;if(!isActualOwner())return;for(var side:Direction.values()){var pos=worldPosition.relative(side);if(!LoadedLevelAccess.isLoaded(level,pos))continue;if(LoadedLevelAccess.blockState(level,pos).is(AllBlocks.CREATIVE_CRATE))hasCreativeCrate=true;var storage=ItemStorage.SIDED.find(level,pos,side.getOpposite());if(storage!=null)attachedInventories.add(storage);}}
    private void initializePrinter(ItemStack blueprint){
        boolean continuing=!activeBlueprint.isEmpty()&&state!=State.STOPPED;var oldStatus=statusMsg;boolean finished=sourceFinished;
        if(!blueprint.is(AllItems.SCHEMATIC)||!blueprint.has(AllDataComponents.SCHEMATIC_ANCHOR)){state=State.STOPPED;statusMsg="schematicInvalid";notifyUpdate();return;}
        if(!blueprint.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED,false)){state=State.STOPPED;statusMsg="schematicNotPlaced";notifyUpdate();return;}
        printer.loadSchematic(blueprint,level,true);if(printer.isErrored()){state=State.STOPPED;statusMsg="schematicErrored";notifyUpdate();return;}
        if(printer.isWorldEmpty()){state=State.STOPPED;statusMsg="schematicExpired";notifyUpdate();return;}
        if(!printer.getAnchor().closerThan(worldPosition,MAX_ANCHOR_DISTANCE)){printer.resetSchematic();state=State.STOPPED;statusMsg="targetOutsideRange";notifyUpdate();return;}
        activeBlueprint=blueprint.copy();hasTarget=printer.getCurrentTarget()!=null&&hasTarget;sourceFinished=finished;if(state==State.STOPPED)state=State.PAUSED;statusMsg=continuing?oldStatus:"ready";updateChecklist();notifyUpdate();
    }
    /** True only when a source block was deliberately skipped and this tick may scan again. */
    private boolean tickPrinter(){
        if(!printer.isLoaded()||printer.isErrored())return false;
        if(state==State.PAUSED&&missingItem==null&&!positionNotLoaded&&!statusMsg.equals("noGunpowder"))return false;
        if(!hasTarget){if(!printer.advanceCurrentPos()){sourceFinished=true;statusMsg="finishing";notifyUpdate();return false;}hasTarget=true;}
        var target=printer.getCurrentTarget();if(target==null)return false;
        if(!LoadedLevelAccess.isLoaded(level,target)||!SchematicWorldAccess.readyForPlacement(level,printer.getBlockReader().getBlockState(target),target)){positionNotLoaded=true;state=State.PAUSED;statusMsg="targetNotLoaded";notifyUpdate();return false;}if(positionNotLoaded){positionNotLoaded=false;state=State.RUNNING;}
        var requirement=printer.getCurrentRequirement();if(requirement.isInvalid()||!printer.shouldPlaceCurrent(level,this::shouldPlace)){hasTarget=false;missingItem=null;statusMsg="searching";notifyUpdate();return true;}
        if(!refillFuelIfPossible()){state=State.PAUSED;statusMsg="noGunpowder";notifyUpdate();return false;}
        final LaunchedItem[] prepared={null};printer.handleCurrentTarget((pos,block,be)->prepared[0]=prepareBlockOrBelt(pos,requirement,block,be),(pos,entity)->prepared[0]=new LaunchedItem.ForEntity(worldPosition,pos,icon(requirement),entity));
        if(prepared[0]==null){hasTarget=false;statusMsg="searching";notifyUpdate();return true;}
        try(var transaction=Transaction.openOuter()){
            var payment=hasCreativeCrate?new SchematicPayment.Result(new SchematicPayment.Receipt(List.of()),ItemStack.EMPTY):SchematicPayment.collect(requirement,attachedInventories,transaction);
            if(!payment.complete()){missingItem=payment.missing().copy();if(skipMissing){hasTarget=false;missingItem=null;state=State.RUNNING;statusMsg="skipping";notifyUpdate();return true;}state=State.PAUSED;statusMsg="missingBlock";notifyUpdate();return false;}
            if(!isActualOwner())return false;paymentOwner.updateSnapshots(transaction);prepared[0].paid(payment.receipt());flyingBlocks.add(prepared[0]);if(!hasCreativeCrate)remainingFuel--;if(!(prepared[0] instanceof LaunchedItem.ForBlockState block)||!block.state.isAir())blocksPlaced=Math.incrementExact(blocksPlaced);printerCooldown=SchematicConfig.get().schematicannonDelay();hasTarget=false;missingItem=null;state=State.RUNNING;statusMsg=prepared[0] instanceof LaunchedItem.ForBlockState block&&block.state.isAir()?"clearing":"placing";transaction.commit();
        }
        if(isActualOwner())level.playSound(null,worldPosition,AllSoundEvents.SCHEMATICANNON_LAUNCH_BLOCK,SoundSource.BLOCKS,1,1);return false;
    }
    private static ItemStack icon(ItemRequirement requirement){return requirement.getRequiredItems().isEmpty()?ItemStack.EMPTY:requirement.getRequiredItems().getFirst().stack.copy();}
    private LaunchedItem prepareBlockOrBelt(BlockPos pos,ItemRequirement requirement,BlockState requested,BlockEntity be){
        var state=requested;if(state.is(AllBlocks.BELT)){state=stripBeltIfNotLast(state);if(state.isAir())return null;if(state.is(AllBlocks.BELT)&&be instanceof BeltBlockEntity belt){int length=belt.beltLength;if(length<2)throw new IllegalArgumentException("Actual source schematic belt has no complete native length");var casings=new BeltBlockEntity.CasingType[length];Arrays.fill(casings,BeltBlockEntity.CasingType.NONE);var current=pos;for(int i=0;i<length;i++){var member=printer.getBlockReader().getBlockEntity(current);var memberState=printer.getBlockReader().getBlockState(current);if(!(member instanceof BeltBlockEntity segment)||!memberState.is(AllBlocks.BELT))throw new IllegalArgumentException("Actual source schematic belt is incomplete");casings[i]=segment.getCasingType();current=BeltBlock.nextSegmentPosition(memberState,current,state.getValue(BeltBlock.PART)!=BeltPart.END);}return new LaunchedItem.ForBelt(worldPosition,pos,new ItemStack(AllItems.BELT_CONNECTOR),state,casings);}}
        return new LaunchedItem.ForBlockState(worldPosition,pos,icon(requirement),state,SchematicNbt.prepare(level,state,be));
    }
    public static BlockState stripBeltIfNotLast(BlockState state){var part=state.getValue(BeltBlock.PART);if(part==BeltPart.MIDDLE)return Blocks.AIR.defaultBlockState();var facing=state.getValue(BeltBlock.HORIZONTAL_FACING);var slope=state.getValue(BeltBlock.SLOPE);boolean last=switch(slope){case DOWNWARD->part==BeltPart.START;case UPWARD->part==BeltPart.END;default->facing.getAxisDirection()==Direction.AxisDirection.POSITIVE&&part==BeltPart.END||facing.getAxisDirection()==Direction.AxisDirection.NEGATIVE&&part==BeltPart.START;};return last?state:AllBlocks.SHAFT.defaultBlockState().setValue(BlockStateProperties.AXIS,slope==BeltSlope.SIDEWAYS?Direction.Axis.Y:facing.getClockWise().getAxis());}
    private boolean refillFuelIfPossible(){
        if(hasCreativeCrate){remainingFuel=0;return true;}int yield=SchematicConfig.get().schematicannonShotsPerGunpowder();if(remainingFuel>yield)remainingFuel=yield;if(remainingFuel>0)return true;
        try(var transaction=Transaction.openOuter()){
            var internal=inventory.getItem(4);boolean paid=!internal.isEmpty()&&internal.is(Items.GUNPOWDER)&&inventory.extractSlot(4,ItemVariant.of(internal),1,transaction)==1;
            if(!paid)paid=SchematicPayment.collect(new ItemRequirement(ItemRequirement.ItemUseType.CONSUME,Items.GUNPOWDER),attachedInventories,transaction).complete();if(!paid||!isActualOwner())return false;paymentOwner.updateSnapshots(transaction);remainingFuel=yield;transaction.commit();return true;
        }
    }
    public int getShotsPerGunpowder(){return hasCreativeCrate?0:SchematicConfig.get().schematicannonShotsPerGunpowder();}
    public boolean shouldPlace(BlockPos pos,BlockState state,BlockEntity be,BlockState replaced,BlockState other,boolean normal){if(pos.closerThan(worldPosition,2)||!replaceBlockEntities&&(replaced.hasBlockEntity()||other!=null&&other.hasBlockEntity())||shouldIgnoreBlockState(state,be))return false;boolean air=state.isAir();return replaceMode==3||replaceMode==2&&!air||replaceMode==1&&!air&&(normal||!replaced.isRedstoneConductor(level,pos)&&(other==null||!other.isRedstoneConductor(level,pos)))||replaceMode==0&&!air&&!replaced.isRedstoneConductor(level,pos)&&(other==null||!other.isRedstoneConductor(level,pos));}
    public boolean shouldIgnoreBlockState(BlockState state,BlockEntity be){if(state.is(Blocks.STRUCTURE_VOID))return true;var requirement=ItemRequirement.of(state,be);if(requirement.isEmpty()||requirement.isInvalid())return false;return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)&&state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER||state.hasProperty(BlockStateProperties.BED_PART)&&state.getValue(BlockStateProperties.BED_PART)==BedPart.HEAD||state.getBlock() instanceof net.minecraft.world.level.block.piston.PistonHeadBlock||state.is(AllBlocks.BELT)&&state.getValue(BeltBlock.PART)==BeltPart.MIDDLE;}
    public void updateChecklist(){checklist.required.clear();checklist.damageRequired.clear();checklist.gathered.clear();checklist.blocksNotLoaded=false;blocksToPlace=printer.markAllBlockRequirements(checklist,level,this::shouldPlace);printer.markAllEntityRequirements(checklist);findInventories();for(var inventory:attachedInventories)for(var view:inventory)if(!view.isResourceBlank()&&view.getAmount()>0)checklist.collect(view.getResource().toStack((int)Math.min(Integer.MAX_VALUE,view.getAmount())));if(hasCreativeCrate)for(var item:checklist.required.keySet())checklist.gathered.put(item,checklist.getRequiredAmount(item));blocksToPlace=Math.addExact(blocksPlaced,blocksToPlace);}
    private void tickPaperPrinter(){var input=inventory.getItem(2);if(input.isEmpty()||!printer.isLoaded()||printer.isErrored()){bookPrintingProgress=0;dontUpdateChecklist=false;return;}if(!dontUpdateChecklist)updateChecklist();var output=input.is(AllBlocks.CLIPBOARD.asItem())?checklist.createWrittenClipboard():checklist.createWrittenBook();var existing=inventory.getItem(3);if(!existing.isEmpty()&&(!ItemStack.isSameItemSameComponents(existing,output)||existing.getCount()>=existing.getMaxStackSize())){bookPrintingProgress=0;return;}bookPrintingProgress+=.05f;if(bookPrintingProgress<1){notifyUpdate();return;}inventory.removeItem(2,1);output.setCount(existing.getCount()+1);inventory.setItem(3,output);bookPrintingProgress=0;dontUpdateChecklist=true;notifyUpdate();}
    private void finishedPrinting(){if(!sourceFinished||!flyingBlocks.isEmpty())return;var output=inventory.getItem(1);if(!output.isEmpty()&&(!output.is(AllItems.EMPTY_SCHEMATIC)||output.getCount()>=output.getMaxStackSize())){statusMsg="outputFull";notifyUpdate();return;}if(!activeBlueprint.isEmpty()&&ItemStack.matches(activeBlueprint,inventory.getItem(0))){inventory.removeItem(0,1);if(output.isEmpty())inventory.setItem(1,new ItemStack(AllItems.EMPTY_SCHEMATIC));else{output.grow(1);inventory.setChanged();}}printer.sendBlockUpdates(level);printer.resetSchematic();activeBlueprint=ItemStack.EMPTY;hasTarget=false;sourceFinished=false;state=State.STOPPED;statusMsg="finished";missingItem=null;level.playSound(null,worldPosition,AllSoundEvents.SCHEMATICANNON_FINISH,SoundSource.BLOCKS,1,1);notifyUpdate();}
    private void writeCannonData(ValueOutput out,boolean privateData){if(privateData)inventory.write(out);out.putString("Status",statusMsg);out.putFloat("Progress",schematicProgress);out.putFloat("PaperProgress",bookPrintingProgress);out.putInt("RemainingFuel",remainingFuel);out.putString("State",state.name());out.putInt("AmountPlaced",blocksPlaced);out.putInt("AmountToPlace",blocksToPlace);if(missingItem!=null&&!missingItem.isEmpty())out.store("MissingItem",ItemStack.CODEC,missingItem);out.store("Options",SchematicannonOptions.CODEC,new SchematicannonOptions(replaceMode,skipMissing,replaceBlockEntities));var printerTag=new CompoundTag();printer.write(printerTag);out.store("Printer",CompoundTag.CODEC,printerTag);var launches=out.childrenList("FlyingBlocks");for(var launch:flyingBlocks)launch.write(launches.addChild(),privateData);out.putFloat("DefaultYaw",defaultYaw);out.putInt("PrinterCooldown",printerCooldown);out.putBoolean("HasTarget",hasTarget);out.putBoolean("SourceFinished",sourceFinished);if(privateData&&!activeBlueprint.isEmpty())out.store("ActiveBlueprint",ItemStack.CODEC,activeBlueprint);}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);writeCannonData(out,!clientPacket);}
    @Override protected void read(ValueInput in, boolean clientPacket){if(!clientPacket)inventory.read(in);statusMsg=in.getStringOr("Status","idle");schematicProgress=Math.clamp(in.getFloatOr("Progress",0),0,1);bookPrintingProgress=Math.clamp(in.getFloatOr("PaperProgress",0),0,1);remainingFuel=in.getIntOr("RemainingFuel",0);if(remainingFuel<0)throw new IllegalArgumentException("Invalid native cannon fuel");state=State.valueOf(in.getStringOr("State","STOPPED"));blocksPlaced=in.getIntOr("AmountPlaced",0);blocksToPlace=in.getIntOr("AmountToPlace",0);if(blocksPlaced<0||blocksToPlace<0)throw new IllegalArgumentException("Invalid native cannon source progress");missingItem=in.read("MissingItem",ItemStack.CODEC).orElse(null);var options=in.read("Options",SchematicannonOptions.CODEC).orElse(new SchematicannonOptions(2,false,false));replaceMode=options.replaceMode;skipMissing=options.skipMissing;replaceBlockEntities=options.replaceBlockEntities;printer.resetSchematic();in.read("Printer",CompoundTag.CODEC).ifPresent(tag->printer.fromTag(tag,level!=null&&level.isClientSide()));flyingBlocks.clear();for(var tag:in.read("FlyingBlocks",CompoundTag.CODEC.listOf()).orElse(List.of()))flyingBlocks.add(LaunchedItem.fromNBT(tag,in.lookup(),in.lookup().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK)));defaultYaw=in.getFloatOr("DefaultYaw",0);printerCooldown=Math.max(0,in.getIntOr("PrinterCooldown",0));hasTarget=in.getBooleanOr("HasTarget",missingItem!=null);sourceFinished=in.getBooleanOr("SourceFinished",false);activeBlueprint=in.read("ActiveBlueprint",ItemStack.CODEC).orElse(inventory.getItem(0).copy());
        super.read(in, clientPacket);
    }
    @Override protected void applyImplicitComponents(DataComponentGetter components){super.applyImplicitComponents(components);var options=components.getOrDefault(AllDataComponents.SCHEMATICANNON_OPTIONS,new SchematicannonOptions(2,true,false));replaceMode=options.replaceMode;skipMissing=options.skipMissing;replaceBlockEntities=options.replaceBlockEntities;}
    @Override protected void collectImplicitComponents(DataComponentMap.Builder components){super.collectImplicitComponents(components);components.set(AllDataComponents.SCHEMATICANNON_OPTIONS,new SchematicannonOptions(replaceMode,skipMissing,replaceBlockEntities));}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null&&!level.isClientSide()){for(int slot=0;slot<inventory.getContainerSize();slot++){var original=inventory.removeItemNoUpdate(slot);if(!original.isEmpty())Block.popResource(level,worldPosition,original);}for(var launch:List.copyOf(flyingBlocks))launch.ownerDestroyed(level,worldPosition);flyingBlocks.clear();}super.preRemoveSideEffects(pos,state);}
    @Override public Component getDisplayName(){return Component.translatable("create.gui.schematicannon.title");}
    @Override public com.simibubi.create.content.schematics.table.SchematicTableMenu.Opening getScreenOpeningData(ServerPlayer player){return new com.simibubi.create.content.schematics.table.SchematicTableMenu.Opening(worldPosition,getUpdateTag(player.registryAccess()),SchematicConfig.get().maxSchematicPacketSize(),SchematicConfig.get().maxTotalSchematicSize()*1000L);}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return new SchematicannonMenu(id,inventory,getScreenOpeningData((ServerPlayer)player));}
    @Override public int getContainerSize(){return inventory.getContainerSize();}@Override public boolean isEmpty(){return inventory.isEmpty();}@Override public ItemStack getItem(int slot){return inventory.getItem(slot);}@Override public ItemStack removeItem(int slot,int count){return inventory.removeItem(slot,count);}@Override public ItemStack removeItemNoUpdate(int slot){return inventory.removeItemNoUpdate(slot);}@Override public void setItem(int slot,ItemStack stack){inventory.setItem(slot,stack);}@Override public boolean canPlaceItem(int slot,ItemStack stack){return inventory.canPlaceItem(slot,stack);}@Override public boolean stillValid(Player player){return canPlayerUse(player);}@Override public void clearContent(){inventory.clearContent();}
}
