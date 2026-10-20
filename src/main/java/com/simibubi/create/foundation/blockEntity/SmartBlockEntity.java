package com.simibubi.create.foundation.blockEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.api.schematic.nbt.PartialSafeNBT;
import com.simibubi.create.api.schematic.requirement.SpecialBlockEntityItemRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.advancement.AdvancementBehaviour;
import com.simibubi.create.foundation.advancement.CreateAdvancement;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.IInteractionChecker;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.createmod.ponder.api.VirtualBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Source SmartBlockEntity lifecycle on native Fabric owners and native value serialization. */
public abstract class SmartBlockEntity extends CachedRenderBBBlockEntity
        implements PartialSafeNBT, IInteractionChecker, SpecialBlockEntityItemRequirement,
        VirtualBlockEntity, BlockEntityBehaviour.Provider {
    private final Map<BehaviourType<?>, BlockEntityBehaviour> behaviours = new Reference2ObjectArrayMap<>();
    private final Set<BlockEntityBehaviour> initializedBehaviours =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<BlockEntityBehaviour> unloadedBehaviours =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<BehaviourType<?>> suppressedNativeTypes =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<PendingRead> readsDuringPublication = new ArrayList<>();
    private boolean initialized;
    private boolean initializing;
    private boolean firstNbtRead = true;
    private boolean collectingNativeBehaviours;
    private boolean publishingBehaviours;
    private boolean tickingBehaviours;
    private boolean invalidated;
    private boolean destroyed;
    private boolean chunkUnloaded;
    private boolean virtualMode;
    private boolean clientRedrawPending;
    private int nativeTickDepth;
    private boolean nativeTickPhaseRan;
    private ClientChunkRead clientChunkRead;
    private AdvancementBehaviour.LocalOwnerStorage localAdvancementOwner;
    protected int lazyTickRate;
    protected int lazyTickCounter;

    private record ClientChunkRead(LevelChunk chunk, ValueInput input, BlockState state) {}
    private record PendingRead(ValueInput input, boolean clientPacket, ClientChunkRead chunkRead) {}

    public SmartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
        var initial = new ArrayList<BlockEntityBehaviour>();
        addBehaviours(initial);
        register(initial);
    }

    /** Source constructor hook. Called exactly once, including on addon subclasses. */
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    /** Pure collection of existing native fields after concrete construction; creates no owners. */
    protected void addNativeBehaviours(List<BlockEntityBehaviour> behaviours) {}

    protected void addNativeAdvancementIds(List<String> ids) {}

    protected AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() { return null; }

    public final AdvancementBehaviour.OwnerStorage advancementOwnerStorage() {
        var nativeOwner = nativeAdvancementOwnerStorage();
        if (nativeOwner != null)
            return nativeOwner;
        if (localAdvancementOwner == null)
            localAdvancementOwner = new AdvancementBehaviour.LocalOwnerStorage();
        return localAdvancementOwner;
    }

    /** Loaded path only, after the subclass has read fields required to construct behaviours. */
    public void addBehavioursDeferred(List<BlockEntityBehaviour> behaviours) {}

    private void register(Collection<BlockEntityBehaviour> additions) {
        for (var behaviour : additions) {
            if (behaviour == null)
                continue;
            if (behaviour.blockEntity != this)
                throw new IllegalArgumentException("Behaviour does not belong to this actual block entity");
            behaviours.put(behaviour.getType(), behaviour);
        }
    }

    private void collectNativeBehaviours() {
        if (!firstNbtRead || collectingNativeBehaviours)
            return;
        collectingNativeBehaviours = true;
        try {
            var actual = new ArrayList<BlockEntityBehaviour>();
            addNativeBehaviours(actual);
            // Constructor-created addon behaviours retain their identity and source precedence.
            for (var behaviour : actual)
                if (behaviour != null && !suppressedNativeTypes.contains(behaviour.getType())
                        && !behaviours.containsKey(behaviour.getType()))
                    register(List.of(behaviour));
            var ids = new ArrayList<String>();
            addNativeAdvancementIds(ids);
            var nativeOwner = nativeAdvancementOwnerStorage();
            if (nativeOwner != null)
                ids.addAll(nativeOwner.advancementIds());
            if (!ids.isEmpty() && !suppressedNativeTypes.contains(AdvancementBehaviour.TYPE)) {
                var advancement = (AdvancementBehaviour) behaviours.get(AdvancementBehaviour.TYPE);
                if (advancement == null) {
                    advancement = new AdvancementBehaviour(this);
                    behaviours.put(AdvancementBehaviour.TYPE, advancement);
                }
                for (var id : ids) {
                    var source = AllAdvancements.find(id);
                    if (source == null)
                        throw new IllegalStateException("Missing genuine source advancement " + id);
                    advancement.add(source);
                }
            }
        } finally {
            collectingNativeBehaviours = false;
        }
    }

    private void publishBehaviours(boolean loaded) {
        if (!firstNbtRead)
            return;
        collectNativeBehaviours();
        // Claim before deferred hooks or public callbacks can reenter this same owner.
        firstNbtRead = false;
        publishingBehaviours = true;
        try {
            if (loaded) {
                var deferred = new ArrayList<BlockEntityBehaviour>();
                addBehavioursDeferred(deferred);
                register(deferred);
            }
            BlockEntityBehaviourEvent.EVENT.invoker().onBehaviours(new BlockEntityBehaviourEvent(this, behaviours));
        } finally {
            publishingBehaviours = false;
        }
        // A recursive native load keeps its real input, but cannot read behaviours before the event.
        while (!readsDuringPublication.isEmpty()) {
            var pending = readsDuringPublication.removeFirst();
            readBehaviours(pending.input(), pending.clientPacket(), pending.chunkRead());
        }
    }

    public void initialize() {
        publishBehaviours(false);
        if (publishingBehaviours || !canRunBehaviourLifecycle())
            return;
        initializeRegisteredBehaviours();
        if (canRunBehaviourLifecycle())
            lazyTick();
    }

    private void initializeRegisteredBehaviours() {
        forEachBehaviour(behaviour -> {
            if (canRunBehaviourLifecycle() && initializedBehaviours.add(behaviour))
                behaviour.initialize();
        });
    }

    public void tick() {
        tickBehaviours();
    }

    /** The native wrapper calls this phase, never the concrete owner's product tick. */
    public final void tickBehaviours() {
        if (tickingBehaviours || publishingBehaviours || isRemoved()
                || nativeTickDepth != 0 && nativeTickPhaseRan)
            return;
        if (nativeTickDepth != 0)
            nativeTickPhaseRan = true;
        tickingBehaviours = true;
        try {
            consumeClientRedraw();
            if (!initialized && hasLevel()) {
                if (initializing)
                    return;
                initializing = true;
                try {
                    publishBehaviours(false);
                    if (!canRunBehaviourLifecycle())
                        return;
                    initialize();
                    initialized = true;
                } finally {
                    initializing = false;
                }
            }
            if (!canRunBehaviourLifecycle())
                return;
            if (initialized && hasLevel())
                initializeRegisteredBehaviours();
            if (lazyTickCounter-- <= 0) {
                lazyTickCounter = lazyTickRate;
                lazyTick();
            }
            if (canRunBehaviourLifecycle())
                forEachBehaviour(behaviour -> {
                    if (canRunBehaviourLifecycle())
                        behaviour.tick();
                });
        } finally {
            tickingBehaviours = false;
        }
    }

    /** Nested native/static bridges share one behaviour phase and the same actual owner. */
    public static void tickNative(Level level, BlockPos pos, BlockState state,
            SmartBlockEntity owner, Runnable nativeTick) {
        try (var frame = enterNativeTick(level, pos, state, owner)) {
            if (frame.shouldTick())
                nativeTick.run();
        }
    }

    public static NativeTickScope enterNativeTick(Level level, BlockPos pos, BlockState state,
            SmartBlockEntity owner) {
        return new NativeTickScope(level, pos, state, owner);
    }

    public static final class NativeTickScope implements AutoCloseable {
        private final SmartBlockEntity owner;
        private final boolean outer;
        private boolean entered;
        private boolean shouldTick;

        private NativeTickScope(Level level, BlockPos pos, BlockState state, SmartBlockEntity owner) {
            this.owner = owner;
            if (level == null || owner.publishingBehaviours || owner.tickingBehaviours) {
                outer = false;
                return;
            }
            if (!owner.hasLevel())
                owner.setLevel(level);
            if (!owner.isCurrentTickOwner(level, pos, state)) {
                outer = false;
                return;
            }
            entered = true;
            outer = owner.nativeTickDepth++ == 0;
            if (outer)
                owner.nativeTickPhaseRan = false;
            try {
                owner.tickBehaviours();
                shouldTick = owner.isCurrentTickOwner(level, pos, state);
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        public boolean shouldTick() { return shouldTick; }

        @Override
        public void close() {
            if (!entered)
                return;
            entered = false;
            owner.nativeTickDepth--;
            if (outer)
                owner.nativeTickPhaseRan = false;
        }
    }

    private boolean isCurrentTickOwner(Level world, BlockPos pos, BlockState expectedState) {
        return level == world && !isRemoved() && worldPosition.equals(pos)
            && LoadedLevelAccess.blockEntity(world, pos) == this
            && LoadedLevelAccess.blockState(world, pos) == expectedState
            && getBlockState() == expectedState;
    }

    private boolean canRunBehaviourLifecycle() {
        return !isRemoved() && (level == null || isVirtual()
            || LoadedLevelAccess.blockEntity(level, worldPosition) == this);
    }

    /** Native chunk data is read before a new client chunk enters the world's chunk cache. */
    public final void withClientChunkRead(LevelChunk chunk, ValueInput input, Runnable nativeRead) {
        var previous = clientChunkRead;
        clientChunkRead = new ClientChunkRead(java.util.Objects.requireNonNull(chunk),
            java.util.Objects.requireNonNull(input), getBlockState());
        try {
            withClientRead(nativeRead);
        } finally {
            clientChunkRead = previous;
        }
    }

    private boolean canReadBehaviourData(ValueInput input, boolean clientPacket, ClientChunkRead read) {
        if (read == null)
            return canRunBehaviourLifecycle();
        if (read.input() != input || !clientPacket || level == null || !level.isClientSide()
                || isRemoved() || chunkUnloaded)
            return false;
        var chunk = read.chunk();
        var visible = level.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return (visible == null || visible == chunk)
            && chunk.getLevel() == level && chunk.getBlockEntities().get(worldPosition) == this
            && chunk.getBlockState(worldPosition) == read.state() && getBlockState() == read.state();
    }

    public void lazyTick() {}

    /** Override these modern source hooks, never the native load/save entrypoints. */
    protected void read(ValueInput input, boolean clientPacket) {
        var chunkRead = clientChunkRead;
        if (chunkRead != null && chunkRead.input() != input)
            chunkRead = null;
        // Consume only this input before publication callbacks can enter another native read.
        if (chunkRead != null)
            clientChunkRead = null;
        clientRedrawPending = clientPacket && (clientRedrawPending || input.getBooleanOr("Redraw", false));
        consumeClientRedraw();
        publishBehaviours(true);
        super.loadAdditional(input);
        if (publishingBehaviours) {
            readsDuringPublication.add(new PendingRead(input, clientPacket, chunkRead));
            return;
        }
        readBehaviours(input, clientPacket, chunkRead);
    }

    private void readBehaviours(ValueInput input, boolean clientPacket, ClientChunkRead chunkRead) {
        forEachBehaviour(behaviour -> {
            if (canReadBehaviourData(input, clientPacket, chunkRead))
                behaviour.read(input, clientPacket);
        });
    }

    private void consumeClientRedraw() {
        if (!clientRedrawPending || level == null || !level.isClientSide()
                || LoadedLevelAccess.blockEntity(level, worldPosition) != this)
            return;
        clientRedrawPending = false;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
            net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    protected void write(ValueOutput output, boolean clientPacket) {
        collectNativeBehaviours();
        super.saveAdditional(output);
        forEachBehaviour(behaviour -> behaviour.write(output, clientPacket));
        if (clientPacket && clientRedrawPending) {
            output.putBoolean("Redraw", true);
            clientRedrawPending = false;
        }
    }

    @Override
    protected final void loadAdditional(ValueInput input) {
        read(input, isReadingClientPacket());
    }

    @Override
    protected final void saveAdditional(ValueOutput output) {
        write(output, false);
    }

    @Override
    protected final void writeClient(ValueOutput output) {
        write(output, true);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        collectNativeBehaviours();
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        super.saveAdditional(output);
        tag.merge(output.buildResult());
        forEachBehaviour(behaviour -> {
            if (behaviour.isSafeNBT())
                behaviour.writeSafe(tag, registries);
        });
    }

    public void onChunkUnloaded() {
        chunkUnloaded = true;
    }

    @Override
    public final void setRemoved() {
        if (isRemoved())
            return;
        super.setRemoved();
        try {
            if (!chunkUnloaded)
                remove();
        } finally {
            invalidate();
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        chunkUnloaded = false;
        invalidated = false;
        destroyed = false;
        initialized = false;
        initializedBehaviours.clear();
        unloadedBehaviours.clear();
    }

    public void remove() {}

    public void invalidate() {
        if (invalidated)
            return;
        invalidated = true;
        forEachBehaviour(this::unloadBehaviour);
    }

    private void unloadBehaviour(BlockEntityBehaviour behaviour) {
        if (unloadedBehaviours.add(behaviour))
            behaviour.unload();
    }

    public void destroy() {
        if (destroyed)
            return;
        destroyed = true;
        forEachBehaviour(BlockEntityBehaviour::destroy);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        destroy();
        super.preRemoveSideEffects(pos, state);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends BlockEntityBehaviour> T getBehaviour(BehaviourType<T> type) {
        collectNativeBehaviours();
        return (T) behaviours.get(type);
    }

    public Collection<BlockEntityBehaviour> getAllBehaviours() {
        collectNativeBehaviours();
        return behaviours.values();
    }

    public void forEachBehaviour(Consumer<BlockEntityBehaviour> action) {
        collectNativeBehaviours();
        // Mutating callbacks cannot tick/read an already replaced or removed registry member.
        for (var behaviour : List.copyOf(behaviours.values()))
            if (behaviours.get(behaviour.getType()) == behaviour)
                action.accept(behaviour);
    }

    public void attachBehaviourLate(BlockEntityBehaviour behaviour) {
        behaviour.blockEntity = this;
        suppressedNativeTypes.remove(behaviour.getType());
        unloadedBehaviours.remove(behaviour);
        behaviours.put(behaviour.getType(), behaviour);
        initializedBehaviours.add(behaviour);
        behaviour.initialize();
    }

    /** A native data-driven field replacement preserves any behaviour explicitly replaced by an addon. */
    protected final void replaceNativeBehaviourForRead(BlockEntityBehaviour previous,
            BlockEntityBehaviour replacement) {
        if (replacement.blockEntity != this || previous.getType() != replacement.getType())
            throw new IllegalArgumentException("Invalid actual native behaviour replacement");
        if (behaviours.get(previous.getType()) != previous)
            return;
        behaviours.put(replacement.getType(), replacement);
        initializedBehaviours.remove(previous);
        unloadBehaviour(previous);
    }

    public void removeBehaviour(BehaviourType<?> type) {
        suppressedNativeTypes.add(type);
        var removed = behaviours.remove(type);
        if (removed != null) {
            initializedBehaviours.remove(removed);
            unloadBehaviour(removed);
        }
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state) {
        ItemRequirement required = ItemRequirement.NONE;
        for (var behaviour : List.copyOf(getAllBehaviours()))
            if (behaviours.get(behaviour.getType()) == behaviour)
                required = required.union(behaviour.getRequiredItems());
        return required;
    }

    public void setLazyTickRate(int rate) {
        lazyTickRate = rate;
        lazyTickCounter = rate;
    }

    @Override public void markVirtual() { virtualMode = true; }
    @Override public boolean isVirtual() { return virtualMode; }
    public boolean isChunkUnloaded() { return chunkUnloaded; }

    @Override
    public boolean canPlayerUse(Player player) {
        return level != null && player.level() == level && !isRemoved()
            && LoadedLevelAccess.blockEntity(level, worldPosition) == this
            && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5,
                worldPosition.getZ() + .5) <= 64;
    }

    public void sendToMenu(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(getBlockPos());
        buffer.writeNbt(getUpdateTag(buffer.registryAccess()));
    }

    public void refreshBlockState() {
        if (level != null)
            setBlockState(level.getBlockState(worldPosition));
    }

    public void registerAwardables(List<BlockEntityBehaviour> list, CreateAdvancement... advancements) {
        for (var behaviour : list)
            if (behaviour instanceof AdvancementBehaviour advancement) {
                advancement.add(advancements);
                return;
            }
        list.add(new AdvancementBehaviour(this, advancements));
    }

    public void award(CreateAdvancement advancement) {
        var behaviour = getBehaviour(AdvancementBehaviour.TYPE);
        if (behaviour != null)
            behaviour.awardPlayer(advancement);
    }

    public void awardIfNear(CreateAdvancement advancement, int range) {
        var behaviour = getBehaviour(AdvancementBehaviour.TYPE);
        if (behaviour != null)
            behaviour.awardPlayerIfNear(advancement, range);
    }
}
