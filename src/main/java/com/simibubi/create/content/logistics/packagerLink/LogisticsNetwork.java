package com.simibubi.create.content.logistics.packagerLink;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;

public final class LogisticsNetwork {
    public static final Codec<LogisticsNetwork> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("Id").forGetter(n -> n.id),
        RequestPromiseQueue.CODEC.fieldOf("Promises").forGetter(n -> n.panelPromises),
        GlobalPos.CODEC.listOf().fieldOf("Links").forGetter(n -> List.copyOf(n.totalLinks)),
        UUIDUtil.CODEC.optionalFieldOf("Owner").forGetter(n -> Optional.ofNullable(n.owner)),
        Codec.BOOL.optionalFieldOf("Locked", false).forGetter(n -> n.locked)
    ).apply(i, LogisticsNetwork::new));
    public final UUID id;
    public RequestPromiseQueue panelPromises;
    public final Set<GlobalPos> totalLinks = new HashSet<>(), loadedLinks = new HashSet<>();
    public UUID owner;
    public boolean locked;
    public LogisticsNetwork(UUID id) { this(id, new RequestPromiseQueue(() -> {}), List.of(), Optional.empty(), false); }
    private LogisticsNetwork(UUID id, RequestPromiseQueue promises, List<GlobalPos> links, Optional<UUID> owner, boolean locked) {
        this.id=id;panelPromises=promises;totalLinks.addAll(links);this.owner=owner.orElse(null);this.locked=locked;
    }
}
