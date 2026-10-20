package com.simibubi.create.foundation.advancement;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Source advancement behaviour over the concrete BE's one canonical persisted Owner. */
public class AdvancementBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<AdvancementBehaviour> TYPE = new BehaviourType<>();

    /** Native devices expose their existing UUID/Owner here; no second behaviour UUID is kept. */
    public interface OwnerStorage {
        UUID getPlayerId();
        void setPlayerId(UUID id);
        default Collection<String> advancementIds() { return List.of(); }
        default boolean canClear(Player player) { return true; }
        default void initialize() {}
        default void read(ValueInput input) { setPlayerId(input.read("Owner", UUIDUtil.CODEC).orElse(null)); }
        default void write(ValueOutput output) {
            var owner = getPlayerId();
            if (owner != null)
                output.store("Owner", UUIDUtil.CODEC, owner);
        }
    }

    /** Used only when the actual Smart owner has no pre-existing native Owner field. */
    public static final class LocalOwnerStorage implements OwnerStorage {
        private UUID playerId;
        @Override public UUID getPlayerId() { return playerId; }
        @Override public void setPlayerId(UUID id) { playerId = id; }
    }

    private final Set<CreateAdvancement> advancements = new HashSet<>();

    public AdvancementBehaviour(SmartBlockEntity owner, CreateAdvancement... advancements) {
        super(owner);
        add(advancements);
    }

    private OwnerStorage owner() { return blockEntity.advancementOwnerStorage(); }
    public void add(CreateAdvancement... advancements) { Collections.addAll(this.advancements, advancements); }
    public boolean isOwnerPresent() { return owner().getPlayerId() != null; }

    public void setPlayer(UUID id) {
        if (getWorld() == null)
            return;
        var player = getWorld().getPlayerByUUID(id);
        if (player == null || player instanceof FakePlayer)
            return;
        owner().setPlayerId(id);
        removeAwarded();
        blockEntity.setChanged();
    }

    @Override
    public void initialize() {
        super.initialize();
        owner().initialize();
        removeAwarded();
    }

    private void removeAwarded() {
        var player = getPlayer();
        if (player == null)
            return;
        advancements.removeIf(advancement -> advancement.isAlreadyAwardedTo(player));
        if (advancements.isEmpty() && owner().canClear(player) && owner().getPlayerId() != null) {
            owner().setPlayerId(null);
            blockEntity.setChanged();
        }
    }

    public void awardPlayerIfNear(CreateAdvancement advancement, int maxDistance) {
        var player = getPlayer();
        if (player != null && player.distanceToSqr(Vec3.atCenterOf(getPos())) <= (double) maxDistance * maxDistance)
            award(advancement, player);
    }

    public void awardPlayer(CreateAdvancement advancement) {
        var player = getPlayer();
        if (player != null)
            award(advancement, player);
    }

    private void award(CreateAdvancement advancement, Player player) {
        if (advancements.contains(advancement))
            advancement.awardTo(player);
        removeAwarded();
    }

    private Player getPlayer() {
        var id = owner().getPlayerId();
        if (id == null || getWorld() == null)
            return null;
        var player = getWorld().getPlayerByUUID(id);
        return player != null && player.level() == getWorld() && !(player instanceof FakePlayer) ? player : null;
    }

    /** Pure query used by existing native award hooks before clearing their canonical Owner. */
    public static boolean hasPendingFor(SmartBlockEntity owner, UUID playerId) {
        if (playerId == null)
            return false;
        var behaviour = owner.getBehaviour(TYPE);
        if (behaviour == null)
            return false;
        var player = owner.getLevel() == null ? null : owner.getLevel().getPlayerByUUID(playerId);
        return behaviour.advancements.stream().anyMatch(advancement -> player == null || !advancement.isAlreadyAwardedTo(player));
    }

    @Override public void read(ValueInput input, boolean clientPacket) { owner().read(input); }
    @Override public void write(ValueOutput output, boolean clientPacket) { owner().write(output); }
    @Override public BehaviourType<?> getType() { return TYPE; }

    public static void tryAward(BlockGetter level, BlockPos pos, CreateAdvancement advancement) {
        var behaviour = BlockEntityBehaviour.get(level, pos, TYPE);
        if (behaviour != null)
            behaviour.awardPlayer(advancement);
    }

    public static void setPlacedBy(Level level, BlockPos pos, LivingEntity placer) {
        var behaviour = BlockEntityBehaviour.get(level, pos, TYPE);
        if (behaviour != null && placer instanceof ServerPlayer && !(placer instanceof FakePlayer))
            behaviour.setPlayer(placer.getUUID());
    }
}
