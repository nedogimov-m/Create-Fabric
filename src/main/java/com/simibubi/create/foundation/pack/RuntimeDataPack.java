package com.simibubi.create.foundation.pack;

import com.simibubi.create.foundation.data.RuntimeDataGenerator;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.*;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.InclusiveRange;
import java.io.*;
import java.util.*;
import java.util.stream.Stream;

/** Low-priority generated server data. World datapacks can replace every generated recipe. */
public final class RuntimeDataPack implements PackResources {
    private final PackLocationInfo location;
    private final Map<Identifier, byte[]> files;
    private RuntimeDataPack(PackLocationInfo location, Map<Identifier, byte[]> files) { this.location = location; this.files = files; }
    public static Pack create() {
        var info = new PackLocationInfo("create:runtime_generated", Component.literal("Create runtime recipes"), PackSource.BUILT_IN, Optional.empty());
        return Pack.readMetaAndCreate(info, new Pack.ResourcesSupplier() {
            @Override public PackMetadataResources openMetadata(PackLocationInfo location) { return new RuntimeDataPack(location, Map.of()); }
            @Override public Stream<PackResources> openResources(PackLocationInfo location, Pack.Metadata metadata) { return Stream.of(new RuntimeDataPack(location, RuntimeDataGenerator.generate())); }
        }, PackType.SERVER_DATA, new PackSelectionConfig(true, Pack.Position.BOTTOM, true));
    }
    @Override public PackLocationInfo location() { return location; }
    @Override public IoSupplier<InputStream> getRootResource(String... paths) { return null; }
    @SuppressWarnings("unchecked")
    @Override public <T> T getMetadataSection(MetadataSectionType<T> type) {
        return type == PackMetadataSection.SERVER_TYPE ? (T) new PackMetadataSection(location.title(), new InclusiveRange<>(SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA))) : null;
    }
    @Override public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
        var bytes = type == PackType.SERVER_DATA ? files.get(id) : null;
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }
    @Override public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {
        if (type != PackType.SERVER_DATA) return;
        files.forEach((id, bytes) -> { if (id.getNamespace().equals(namespace) && (prefix.isEmpty() || id.getPath().startsWith(prefix + "/"))) output.accept(id, () -> new ByteArrayInputStream(bytes)); });
    }
    @Override public Set<String> getNamespaces(PackType type) { return type == PackType.SERVER_DATA ? Set.of("create") : Set.of(); }
    @Override public void close() {}
}
