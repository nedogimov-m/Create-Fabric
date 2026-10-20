package com.simibubi.create.content.kinetics.deployer;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.ContraptionAssemblyGraph;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.schematics.*;
import com.simibubi.create.content.schematics.cannon.LaunchedItem;
import com.simibubi.create.content.schematics.cannon.SchematicPayment;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Original moving schematic-filter path, with original paid material retained until real publication. */
public final class DeployerSchematicPrinter {
    private static final String JOB="SchematicPrintJob";
    private static final Map<MovementContext,State> STATES=new WeakHashMap<>();
    private record Cached(ItemStack blueprint,SchematicStructure structure){}
    private record Claim(PrintJob pending,CompoundTag encoded){}
    private record SourceAccess(MovementContext context,DeployerFakePlayer player,State state,
        ServerLevel world,com.simibubi.create.content.contraptions.Contraption structure,
        com.simibubi.create.content.contraptions.AbstractContraptionEntity owner,
        BlockPos localPos,BlockState actorState,CompoundTag actorData,CompoundTag blockData){
        static SourceAccess capture(MovementContext context,DeployerFakePlayer player,State state){
            if(state.stopping||!live(context,player))return null;
            return new SourceAccess(context,player,state,(ServerLevel)context.world,context.contraption,context.contraption.entity,context.localPos,context.state,context.data,context.blockEntityData);
        }
        boolean matches(PrintJob expected){return !state.stopping&&state.pending==expected&&STATES.get(context)==state
            &&context.world==world&&context.contraption==structure&&structure.entity==owner
            &&Objects.equals(context.localPos,localPos)&&context.state==actorState&&context.data==actorData&&context.blockEntityData==blockData
            &&context.position!=null&&!context.disabled&&live(context,player);}
    }
    private record TargetSnapshot(BlockPos pos,BlockState state,net.minecraft.world.level.block.entity.BlockEntity entity){
        static TargetSnapshot capture(SourceAccess access,BlockPos pos){return LoadedLevelAccess.isLoaded(access.world,pos)?new TargetSnapshot(pos.immutable(),LoadedLevelAccess.blockState(access.world,pos),LoadedLevelAccess.blockEntity(access.world,pos)):null;}
        boolean unchanged(SourceAccess access){return LoadedLevelAccess.isLoaded(access.world,pos)&&LoadedLevelAccess.blockState(access.world,pos)==state&&LoadedLevelAccess.blockEntity(access.world,pos)==entity;}
    }
    private record Attempt(SourceAccess source,TargetSnapshot target,PrintJob job,BlockState baseAtStart){
        boolean ready(){
            if(!source.matches(job)||!LoadedLevelAccess.isLoaded(source.world,target.pos)||!source.world.mayInteract(source.player,target.pos))return false;
            if(job.acceptedBase()==null)return target.unchanged(source)&&target.state.canBeReplaced();
            return LoadedLevelAccess.blockState(source.world,target.pos)==job.acceptedBase()&&(baseAtStart==null||target.unchanged(source));
        }
        boolean authorize(){
            if(!ready())return false;
            var receiver=LoadedLevelAccess.blockEntity(source.world,target.pos);
            var hit=new BlockHitResult(Vec3.atCenterOf(target.pos),Direction.UP,target.pos,false);
            var permission=UseBlockCallback.EVENT.invoker().interact(source.player,source.world,InteractionHand.MAIN_HAND,hit);
            return permission!=InteractionResult.FAIL&&!permission.consumesAction()&&ready()&&LoadedLevelAccess.blockEntity(source.world,target.pos)==receiver;
        }
    }

    private static final class State extends SnapshotParticipant<Claim>{
        final java.lang.ref.WeakReference<MovementContext> context;
        Cached cached;
        PrintJob pending;
        boolean executing,stopping;
        State(MovementContext context){
            this.context=new java.lang.ref.WeakReference<>(context);
            context.data.getCompound(JOB).ifPresent(saved->pending=PrintJob.decode(context,saved));
        }
        void claim(PrintJob job,Transaction tx){updateSnapshots(tx);pending=job;write(Objects.requireNonNull(context.get()));}
        @Override protected Claim createSnapshot(){return new Claim(pending,Objects.requireNonNull(context.get()).data.getCompound(JOB).map(CompoundTag::copy).orElse(null));}
        @Override protected void readSnapshot(Claim claim){pending=claim.pending;var actual=Objects.requireNonNull(context.get());if(claim.encoded==null)actual.data.remove(JOB);else actual.data.put(JOB,claim.encoded);}
    }
    private static final class PrintJob extends LaunchedItem.ForBlockState {
        private Attempt attempt;
        BlockState acceptedBase(){return publishedState;}
        boolean updateBound(Attempt current){if(attempt!=null)return false;attempt=current;try{return update(current.source.world);}finally{attempt=null;}}
        PrintJob(BlockPos target,ItemStack item,BlockState state,CompoundTag data){super(target,target,item,state,data);ticksRemaining=0;}
        static PrintJob decode(MovementContext context,CompoundTag saved){
            var job=new PrintJob(BlockPos.ZERO,ItemStack.EMPTY,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),null);
            job.readNBT(saved,context.world.registryAccess(),context.world.registryAccess().lookupOrThrow(Registries.BLOCK));return job;
        }
        @Override protected boolean place(net.minecraft.world.level.Level level){
            if(attempt==null||!attempt.ready()||!retainsPublishedBase(level))return false;
            var inherited=publication(state,level);
            if(!attempt.ready())return false;
            var baseRequirement=ItemRequirement.of(state,null);
            var plan=prepareHandoff(baseRequirement);
            if(!plan.ready()||!attempt.ready())return false;
            var publication=new SchematicPlacement.Publication(){
                private PreparedHandoff base=plan;
                @Override public boolean mayPublish(BlockState actual){return attempt!=null&&attempt.authorize();}
                @Override public void basePrepared(BlockState actual){DeployerSchematicPublication.prepared(level,target,actual);}
                @Override public void baseAccepted(BlockState actual){if(publishedState==null){base.apply();publishedState=actual;publicationChanged();}}
                @Override public void dataAccepted(){inherited.dataAccepted();}
                @Override public void partialDataAccepted(net.minecraft.world.level.block.entity.BlockEntity actual){inherited.partialDataAccepted(actual);if(publishedState==null)base=prepareHandoff(baseRequirement);}
            };
            try(var scope=DeployerSchematicPublication.prepare(level,target,publication)){return SchematicPlacement.placeBlock(level,state,target,stack,data,publication);}
        }
        boolean canAttempt(net.minecraft.world.level.Level level){return LoadedLevelAccess.isLoaded(level,target)&&(publishedState==null?LoadedLevelAccess.blockState(level,target).canBeReplaced():LoadedLevelAccess.blockState(level,target)==publishedState);}
        boolean spentBaseWasRemoved(net.minecraft.world.level.Level level){return publishedState!=null&&paidOriginals().isEmpty()&&LoadedLevelAccess.isLoaded(level,target)&&LoadedLevelAccess.blockState(level,target)!=publishedState;}
        void retireTo(DeployerFakePlayer player){
            var originals=paidOriginals();
            // Both representations change without calling native code: no restored preview or item admission.
            handoffAll();player.movingOverflow.addAll(originals);
        }
    }
    private static State state(MovementContext context){return STATES.computeIfAbsent(context,State::new);}
    public static boolean hasPending(MovementContext context){var state=STATES.get(context);return state!=null?state.pending!=null:context.data.contains(JOB);}
    /** Shared graph and direct restoration preflights must call this before stopping actors. */
    public static boolean prepareDisassembly(MovementContext context){var state=STATES.get(context);return state==null||!state.executing;}
    private static boolean live(MovementContext context,DeployerFakePlayer player){
        if(!(context.world instanceof ServerLevel level)||!level.getServer().isSameThread()||player.level()!=level||player.isRemoved()||context.temporaryData!=player||context.contraption==null)return false;
        var structure=context.contraption;var owner=structure.entity;
        return owner!=null&&owner.level()==level&&owner.getContraption()==structure&&owner.hasOwnedStructure()&&level.getEntity(owner.getUUID())==owner
            &&LoadedEntityAccess.isReady(level,owner.blockPosition())&&!ContraptionAssemblyGraph.isRecovering(owner)&&ContraptionAssemblyGraph.isTransferPermitted(structure)
            &&structure.getActors().stream().anyMatch(actor->actor.right==context&&actor.left.pos().equals(context.localPos));
    }
    /** True selects the source schematic branch even if this particular target is refused. */
    public static boolean activate(MovementContext context,BlockPos target,DeployerFakePlayer player){
        var blueprint=context.getFilterFromBE().item();if(!blueprint.is(AllItems.SCHEMATIC))return false;
        var state=state(context);if(state.executing||state.stopping||!live(context,player))return true;
        var access=SourceAccess.capture(context,player,state);if(access==null)return true;
        var expected=TargetSnapshot.capture(access,target);if(expected==null)return true;
        if(state.pending==null){
            state.executing=true;
            try{prepare(access,expected,blueprint);}
            finally{state.executing=false;if(state.stopping)retire(state,player);write(context);}
            // The same source/target tokens cross real payment onFinalCommit callbacks.
            retry(context,player,access,expected);return true;
        }
        retry(context,player,access,null);return true;
    }
    private static boolean unpaidReady(SourceAccess access,TargetSnapshot target){return access.matches(null)&&target.unchanged(access)&&target.state.canBeReplaced()&&access.world.mayInteract(access.player,target.pos);}
    private static void prepare(SourceAccess access,TargetSnapshot expected,ItemStack blueprint){
        var context=access.context;var state=access.state;var target=expected.pos;
        if(!blueprint.has(AllDataComponents.SCHEMATIC_ANCHOR)||!blueprint.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED,false)||!unpaidReady(access,expected))return;
        SchematicStructure schematic;
        if(state.cached!=null&&ItemStack.matches(state.cached.blueprint,blueprint))schematic=state.cached.structure;
        else{
            try{schematic=SchematicStructure.load(access.world,SchematicItem.loadSchematic(access.world,blueprint),blueprint.get(AllDataComponents.SCHEMATIC_ANCHOR),SchematicItem.getSettings(blueprint),true);}
            catch(java.io.IOException|RuntimeException invalid){return;}
            state.cached=new Cached(blueprint.copy(),schematic);
        }
        if(!unpaidReady(access,expected)||!schematic.bounds().isInside(target.subtract(schematic.anchor())))return;
        var requested=schematic.getBlockState(target);var source=schematic.safeBlockEntity(target);var requirement=ItemRequirement.of(requested,source);
        if(requirement.isInvalid()||requirement.isEmpty()||requested.is(com.simibubi.create.AllBlocks.BELT)||!SchematicWorldAccess.readyForPlacement(access.world,requested,target)||!unpaidReady(access,expected))return;
        var hit=new BlockHitResult(Vec3.atCenterOf(target),Direction.UP,target,false);
        var permission=UseBlockCallback.EVENT.invoker().interact(access.player,access.world,InteractionHand.MAIN_HAND,hit);
        if(permission==InteractionResult.FAIL||permission.consumesAction()||!unpaidReady(access,expected))return;
        var data=SchematicNbt.prepare(access.world,requested,source);
        var item=requirement.getRequiredItems().isEmpty()?ItemStack.EMPTY:requirement.getRequiredItems().getFirst().stack.copy();
        if(!unpaidReady(access,expected))return;
        var job=new PrintJob(target,item,requested,data);
        try(var tx=Transaction.openOuter()){
            var payment=access.structure.hasUniversalCreativeCrate?new SchematicPayment.Result(new SchematicPayment.Receipt(List.of()),ItemStack.EMPTY):SchematicPayment.collect(requirement,List.of(access.structure.getStorage().getAllItems()),tx);
            if(!payment.complete()||!unpaidReady(access,expected))return;
            job.paid(payment.receipt());state.claim(job,tx);
            if(!access.matches(job)||!expected.unchanged(access)||!expected.state.canBeReplaced()||!access.world.mayInteract(access.player,target))return;
            tx.commit();
        }
    }
    public static void retry(MovementContext context,DeployerFakePlayer player){retry(context,player,null,null);}
    private static void retry(MovementContext context,DeployerFakePlayer player,SourceAccess previous,TargetSnapshot expected){
        var state=state(context);if(state.executing||state.stopping||state.pending==null||!live(context,player))return;
        var access=previous==null?SourceAccess.capture(context,player,state):previous;
        var job=state.pending;if(access==null||!access.matches(job))return;
        state.executing=true;
        try{
            if(job.spentBaseWasRemoved(access.world)){state.pending=null;return;}
            var target=expected==null?TargetSnapshot.capture(access,job.target):expected;
            if(target==null||!target.pos.equals(job.target)||!job.canAttempt(access.world))return;
            var attempt=new Attempt(access,target,job,job.acceptedBase());if(!attempt.ready())return;
            if(job.updateBound(attempt)){
                if(job.state.getBlock() instanceof BaseRailBlock||job.state.getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock)player.placedTracks=true;
                state.pending=null;
            }
        }finally{
            state.executing=false;
            if(state.stopping)retire(state,player);
            write(context);
        }
    }
    public static void write(MovementContext context){
        var state=STATES.get(context);if(state==null)return;
        if(state.pending==null)context.data.remove(JOB);else context.data.put(JOB,state.pending.serializeNBT(context.world.registryAccess()));
    }
    private static void retire(State state,DeployerFakePlayer player){
        if(state.pending!=null){state.pending.retireTo(player);state.pending=null;}
        state.cached=null;var context=state.context.get();if(context!=null){context.data.remove(JOB);new DeployerMovementBehaviour().writeExtraData(context);}
    }
    public static void stop(MovementContext context,DeployerFakePlayer player){
        var state=state(context);state.stopping=true;if(!state.executing)retire(state,player);write(context);
    }
    private DeployerSchematicPrinter(){}
}
