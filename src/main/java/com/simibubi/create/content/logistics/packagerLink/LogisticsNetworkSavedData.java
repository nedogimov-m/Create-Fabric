package com.simibubi.create.content.logistics.packagerLink;

import com.mojang.serialization.Codec;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.*;

public final class LogisticsNetworkSavedData extends SavedData {
    public static final Codec<LogisticsNetworkSavedData> CODEC = LogisticsNetwork.CODEC.listOf()
        .fieldOf("LogisticsNetworks").codec().xmap(LogisticsNetworkSavedData::new, data -> List.copyOf(data.networks.values()));
    public static final SavedDataType<LogisticsNetworkSavedData> TYPE = new SavedDataType<>(
        Create.asResource("logistics"), LogisticsNetworkSavedData::new, CODEC, null);
    private final Map<UUID,LogisticsNetwork> networks = new HashMap<>();
    public LogisticsNetworkSavedData() {}
    private LogisticsNetworkSavedData(List<LogisticsNetwork> values) { for(var network:values)if(networks.putIfAbsent(network.id,network)!=null)throw new IllegalArgumentException("Duplicate persistent logistics network UUID");bindCallbacks(); }
    public Map<UUID,LogisticsNetwork> getLogisticsNetworks() { return networks; }
    public void bindCallbacks() { networks.values().forEach(network -> network.panelPromises.setOnChanged(this::setDirty)); }
    public static LogisticsNetworkSavedData load(MinecraftServer server) { return NeoForgeSavedDataImport.load(server,TYPE,"create_logistics.dat",NeoForgeLogisticsImport.VERSION,NeoForgeLogisticsImport::convert); }
}
