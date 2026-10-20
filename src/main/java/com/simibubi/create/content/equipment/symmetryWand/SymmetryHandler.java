package com.simibubi.create.content.equipment.symmetryWand;

import com.simibubi.create.content.equipment.symmetryWand.mirror.EmptyMirror;
import java.util.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/** Native successful placements and AFTER breaks, retaining the real pre-break state. */
public final class SymmetryHandler {
    public static final class Capture implements AutoCloseable {
        private final Capture previous;private final BlockPlaceContext original;private BlockPlaceContext actual;private BlockState state;private final boolean internal;private final List<ItemStack> owners;
        private Capture(BlockPlaceContext context){previous=CAPTURE.get();original=context;internal=SymmetryPlacement.internal(context);owners=SymmetryPlacement.placementOwners(context);CAPTURE.set(this);}
        @Override public void close(){if(previous==null)CAPTURE.remove();else CAPTURE.set(previous);}
    }
    private record Source(ServerPlayer player,BlockPos pos,BlockState state,boolean breaking,List<ItemStack> owners){Source(ServerPlayer player,BlockPos pos,BlockState state,boolean breaking){this(player,pos,state,breaking,List.of());}}
    private static final ThreadLocal<List<ItemStack>> APPLYING=new ThreadLocal<>();
    static List<ItemStack> currentOwners(){return APPLYING.get();}
    private static boolean suppressed(List<ItemStack> owners,ItemStack wand){return owners.stream().anyMatch(owner->owner==wand);}
    private static final ThreadLocal<Capture> CAPTURE=new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<Source>> QUEUE=new ThreadLocal<>();
    private static final ThreadLocal<CustomCapture> CUSTOM=new ThreadLocal<>();
    private static final ThreadLocal<ItemUse> ITEM_USE=new ThreadLocal<>();
    private static final ThreadLocal<ItemUse> SUSPENDED_USE=new ThreadLocal<>();
    private static final ThreadLocal<NativeUse> NATIVE_USE=new ThreadLocal<>();
    /** Exact native ItemStack.useOn scope, including non-BlockItem source transformations. */
    public static final class ItemUse implements AutoCloseable {
        private final ItemUse previous;private final UseOnContext context;private final ServerPlayer player;private final boolean eligible;
        private BlockPos firstPos;private boolean completed;
        private ItemUse(UseOnContext context){previous=ITEM_USE.get();this.context=context;player=context.getPlayer() instanceof ServerPlayer actual&&SymmetryWandNetworking.actual(actual)&&context.getLevel()==actual.level()?actual:null;eligible=!(context.getItemInHand().getItem() instanceof net.minecraft.world.item.BucketItem);ITEM_USE.set(this);}
        @Override public void close(){if(previous==null)ITEM_USE.remove();else ITEM_USE.set(previous);}
    }
    /** Native GameMode publishes returned hand transformations after ItemStack.useOn returns. */
    public static final class NativeUse implements AutoCloseable {
        private final NativeUse previous;private final ServerPlayer player;private final Level level;private final List<ItemUse> pending=new ArrayList<>();private boolean completed;
        private NativeUse(ServerPlayer player,Level level){previous=NATIVE_USE.get();this.player=player;this.level=level;NATIVE_USE.set(this);}
        @Override public void close(){if(previous==null)NATIVE_USE.remove();else NATIVE_USE.set(previous);}
    }
    public static ItemUse captureItemUse(UseOnContext context){return new ItemUse(context);}
    public static NativeUse captureNativeUse(ServerPlayer player,Level level){return new NativeUse(player,level);}
    private static boolean coveredByItemUse(ServerPlayer player,Level level){var use=ITEM_USE.get();return use!=null&&!use.completed&&use!=SUSPENDED_USE.get()&&use.player==player&&use.context.getLevel()==level;}
    private static void publishedByItemUse(LevelChunk chunk,BlockPos pos,BlockState state,BlockState previous){
        var use=ITEM_USE.get();if(use==null||use.completed||use==SUSPENDED_USE.get()||!use.eligible||use.player==null||use.firstPos!=null||use.context.getLevel()!=chunk.getLevel()||use.player.level()!=chunk.getLevel()||state==previous)return;
        use.firstPos=pos.immutable();
    }
    public static void completedItemUse(ItemUse use,InteractionResult result){
        use.completed=true;if(!result.consumesAction()||!use.eligible||use.player==null||use.firstPos==null)return;
        var nativeUse=NATIVE_USE.get();if(nativeUse!=null&&!nativeUse.completed&&nativeUse.player==use.player&&nativeUse.level==use.context.getLevel()){if(nativeUse.pending.size()<64)nativeUse.pending.add(use);return;}
        publishItemUse(use);
    }
    public static void completedNativeUse(NativeUse nativeUse){nativeUse.completed=true;for(var use:nativeUse.pending)publishItemUse(use);}
    private static void publishItemUse(ItemUse use){
        if(!SymmetryWandNetworking.actual(use.player)||use.player.level()!=use.context.getLevel())return;
        // EntityPlaceEvent samples the final state at its first accepted snapshot position,
        // even when later source writes changed its block type or left it as air.
        var current=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(use.player.level(),use.firstPos);
        var previous=SUSPENDED_USE.get();var active=ITEM_USE.get();if(active==null)SUSPENDED_USE.remove();else SUSPENDED_USE.set(active);
        try{enqueue(new Source(use.player,use.firstPos,current,false));}finally{if(previous==null)SUSPENDED_USE.remove();else SUSPENDED_USE.set(previous);}
    }
    /** One exact source custom-use invocation; acceptance comes only from the actual section write. */
    public static final class CustomCapture implements AutoCloseable {
        private final CustomCapture previous;private final UseOnContext context;private final ServerPlayer player;
        private final BlockPos pos;private final BlockState before;private final boolean firstSnapshot;private BlockPos acceptedPos;private BlockState accepted;
        private CustomCapture(UseOnContext context,boolean firstSnapshot){this.previous=CUSTOM.get();this.context=context;this.player=context.getPlayer() instanceof ServerPlayer actual&&SymmetryWandNetworking.actual(actual)&&context.getLevel()==actual.level()?actual:null;this.pos=context.getClickedPos().immutable();this.before=player==null?null:com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(player.level(),pos);this.firstSnapshot=firstSnapshot;CUSTOM.set(this);}
        @Override public void close(){if(previous==null)CUSTOM.remove();else CUSTOM.set(previous);}
    }
    public static CustomCapture captureCustomUse(UseOnContext context){return new CustomCapture(context,false);}
    /** Pinned EntityMultiPlaceEvent exposes only the first actually changed snapshot to symmetry. */
    public static CustomCapture captureMultiUse(UseOnContext context){return new CustomCapture(context,true);}
    public static void publishedByCustomNative(LevelChunk chunk,BlockPos pos,BlockState state,BlockState previous){
        publishedByItemUse(chunk,pos,state,previous);
        var capture=CUSTOM.get();if(capture==null||capture.player==null||capture.accepted!=null||capture.context.getLevel()!=chunk.getLevel()||capture.player.level()!=chunk.getLevel()||previous==state)return;
        if(!capture.firstSnapshot&&(!capture.pos.equals(pos)||previous!=capture.before||!state.is(com.simibubi.create.AllBlocks.CART_ASSEMBLER)))return;
        capture.acceptedPos=pos.immutable();capture.accepted=state;
    }
    public static void completedCustomUse(CustomCapture capture,boolean result){
        if(!result||capture.player==null||capture.accepted==null||capture.player.level()!=capture.context.getLevel()||coveredByItemUse(capture.player,capture.context.getLevel()))return;
        var current=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(capture.player.level(),capture.acceptedPos);
        if(current.getBlock()!=capture.accepted.getBlock())return;
        enqueue(new Source(capture.player,capture.acceptedPos,current,false));
    }
    static void completedStatePlacement(ServerPlayer player,BlockPos pos,BlockState expected,List<ItemStack> owners){
        if(owners==null||!SymmetryWandNetworking.actual(player))return;
        var current=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(player.level(),pos);
        if(current.getBlock()==expected.getBlock())enqueue(new Source(player,pos.immutable(),current,false,owners));
    }
    public static Capture capture(BlockPlaceContext context){return new Capture(context);}
    public static void placedByNative(BlockPlaceContext context,BlockState state,boolean accepted){var capture=CAPTURE.get();if(capture!=null&&accepted){capture.actual=context;capture.state=state;}}
    public static void completed(Capture capture,InteractionResult result){
        if(capture.internal&&capture.owners==null||!result.consumesAction()||capture.actual==null||capture.state==null||!(capture.actual.getPlayer() instanceof ServerPlayer player)||capture.actual.getLevel()!=player.level())return;
        if(!capture.internal&&coveredByItemUse(player,capture.actual.getLevel()))return;
        var pos=capture.actual.getClickedPos();var actual=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(player.level(),pos);if(actual.getBlock()!=capture.state.getBlock())return;
        enqueue(new Source(player,pos.immutable(),actual,false,capture.internal?capture.owners:List.of()));
    }
    private static void enqueue(Source event){
        var pending=QUEUE.get();if(pending!=null){if(pending.size()<64)pending.add(event);return;}
        pending=new ArrayDeque<>();pending.add(event);QUEUE.set(pending);
        try{drain(pending);}finally{QUEUE.remove();}
    }
    private static void drain(ArrayDeque<Source> pending){int events=0;while(!pending.isEmpty()&&events++<64){var next=pending.remove();if(!SymmetryWandNetworking.actual(next.player))continue;for(int slot=0;slot<Inventory.getSelectionSize();slot++){var wand=next.player.getInventory().getItem(slot);if(!(wand.getItem() instanceof SymmetryWandItem))continue;perform(next.player,wand,next.pos,next.state,next.breaking,next.owners);}}}
    private static void scoped(Runnable action){if(QUEUE.get()!=null){action.run();return;}var queue=new ArrayDeque<Source>();QUEUE.set(queue);try{action.run();drain(queue);}finally{QUEUE.remove();}}
    public static void apply(ServerPlayer player,ItemStack wand,BlockPos source,BlockState state){scoped(()->perform(player,wand,source,state,false,List.of()));}
    public static void remove(ServerPlayer player,ItemStack wand,BlockPos source,BlockState state){scoped(()->perform(player,wand,source,state,true,List.of()));}
    private static void perform(ServerPlayer player,ItemStack wand,BlockPos source,BlockState state,boolean breaking,List<ItemStack> owners){
        if(suppressed(owners,wand)||!SymmetryWandNetworking.actual(player)||!SymmetryWandItem.isEnabled(wand))return;var mirror=SymmetryWandItem.getMirror(wand);if(mirror instanceof EmptyMirror||mirror.getPosition().distanceTo(Vec3.atLowerCornerOf(source))>SymmetryWandConfig.range())return;
        var chain=new ArrayList<ItemStack>(owners);chain.add(wand);var previous=APPLYING.get();APPLYING.set(List.copyOf(chain));
        try{
        var transformed=mirror.process(source,state);var targets=new ArrayList<BlockPos>();targets.add(source.immutable());
        for(var target:transformed.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ))).toList()){
            if(target.getKey().equals(source))continue;
            if(breaking?SymmetryPlacement.destroy(player,target.getKey(),state):SymmetryPlacement.place(player,target.getKey(),target.getValue()))targets.add(target.getKey().immutable());
        }
        if(targets.size()>1){var packet=new SymmetryEffectPacket(BlockPos.containing(mirror.getPosition()),targets);var recipients=new HashSet<>(PlayerLookup.tracking(player));recipients.add(player);for(var recipient:recipients)ServerPlayNetworking.send(recipient,packet);}
        }finally{if(previous==null)APPLYING.remove();else APPLYING.set(previous);}
    }
    public static void register(){PlayerBlockBreakEvents.AFTER.register((level,player,pos,state,entity)->{if(player instanceof ServerPlayer server&&!SymmetryPlacement.internalBreak(server,pos))enqueue(new Source(server,pos.immutable(),state,true));});}
    private SymmetryHandler(){}
}
