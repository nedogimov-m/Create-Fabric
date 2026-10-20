package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.NeoForgePlayerVehicleContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.storage.PlayerDataStorage;

/** Exact native filename owner, outside the reader's corrupt-file fallback and the save catch. */
public final class NeoForgePlayerStorageImport {
    private record Owner(MinecraftServer server, Path directory) {}
    private static final Map<PlayerDataStorage, Owner> OWNERS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<PlayerDataStorage, Refusal> FAILURES = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final ThreadLocal<Read> ACTIVE = new ThreadLocal<>();
    static {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            synchronized (OWNERS) {
                OWNERS.entrySet().removeIf(entry -> {
                    if (entry.getValue().server != server) return false;
                    FAILURES.remove(entry.getKey()); return true;
                });
            }
        });
    }
    private static final class Read {
        final PlayerDataStorage storage;
        final NameAndId fileOwner;
        final Owner owner;
        final Map<Path, String> attempts = new LinkedHashMap<>();
        Path winner;
        Read(PlayerDataStorage storage, NameAndId fileOwner, Owner owner) {
            this.storage = storage; this.fileOwner = fileOwner; this.owner = owner;
        }
    }
    public static void bind(PlayerDataStorage storage, MinecraftServer server, File actualPlayerDirectory) {
        var owner = new Owner(server, actualPlayerDirectory.toPath().toAbsolutePath().normalize());
        synchronized (OWNERS) {
            var previous = OWNERS.get(storage);
            if (previous != null && !previous.equals(owner)) throw invalid("Native player storage changed its actual server/directory owner");
            OWNERS.put(storage, owner);
        }
    }
    public static Optional<CompoundTag> load(PlayerDataStorage storage, NameAndId fileOwner,
            Supplier<Optional<CompoundTag>> nativeLoad) {
        var failed = FAILURES.get(storage);
        if (failed != null) throw failed;
        var previous = ACTIVE.get();
        if (previous != null && previous.storage == storage) throw invalid("Reentrant native player-file load");
        var read = new Read(storage, fileOwner, OWNERS.get(storage));
        ACTIVE.set(read);
        try { return nativeLoad.get(); }
        finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }
    public static Optional<CompoundTag> raw(PlayerDataStorage storage, NameAndId actualFileOwner, String suffix,
            Supplier<Optional<CompoundTag>> nativeRead) {
        var read = ACTIVE.get();
        if (read == null || read.storage != storage || !read.fileOwner.id().equals(actualFileOwner.id()))
            return nativeRead.get();
        if (!suffix.equals(".dat") && !suffix.equals(".dat_old")) throw invalid("Unknown native player-file suffix");
        Path path = read.owner == null ? null : read.owner.directory.resolve(actualFileOwner.id() + suffix);
        String before = null;
        if (path != null) {
            try { before = fingerprint(path); }
            catch (RuntimeException ordinaryReadFailure) { /* Preserve the native unreadable-file fallback. */ }
        }
        var result = nativeRead.get();
        if (path != null) read.attempts.put(path, before);
        if (result.isPresent() && original(result.get())) {
            try {
                if (read.owner == null) throw invalid("Original player file has no actual native server/directory context");
                if (before == null || !before.equals(fingerprint(path)) || !readExact(path).equals(result.get()))
                    throw invalid("Original player file changed during the actual native read");
                read.winner = path;
            } catch (RuntimeException error) { throw refuse(read, result.get(), error); }
        }
        return result;
    }
    /** Returns null for the ordinary native mapper. Original owners bypass exactly that one mapper. */
    public static CompoundTag convert(PlayerDataStorage storage, CompoundTag actualRaw) {
        if (!original(actualRaw)) return null;
        var read = ACTIVE.get();
        if (read == null || read.storage != storage) throw invalid("Original player mapper lacks its actual native filename witness");
        try {
            if (read.owner == null || read.winner == null) throw invalid("Original player mapper lacks its actual native raw-file witness");
            checkCurrent(read, actualRaw);
            if (!uuid(actualRaw, "UUID").equals(read.fileOwner.id())) throw invalid("Original player UUID disagrees with the actual loaded filename");
            Identifier dimension = Identifier.parse(string(actualRaw, "Dimension"));
            Supplier<CompoundTag> prepare = () -> {
                checkCurrent(read, actualRaw);
                var level = read.owner.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
                if (level == null) throw invalid("Original player dimension has no actual loaded native level");
                var result = NeoForgePlayerDataImport.convert(actualRaw, read.fileOwner.id(), level);
                checkCurrent(read, actualRaw);
                return result;
            };
            // Global railway witnesses and actual level ownership are observed on the real server thread.
            return read.owner.server.isSameThread() ? prepare.get() : read.owner.server.submit(prepare).join();
        } catch (RuntimeException error) { throw refuse(read, actualRaw, error); }
    }
    private static void checkCurrent(Read read, CompoundTag actualRaw) {
        var failed = FAILURES.get(read.storage);
        if (failed != null) throw failed;
        for (var attempt : read.attempts.entrySet())
            if (!attempt.getValue().equals(fingerprint(attempt.getKey()))) throw invalid("Native player-file winner changed before publication");
        if (!readExact(read.winner).equals(actualRaw)) throw invalid("Native player-file payload changed before publication");
    }
    public static boolean saveAllowed(PlayerDataStorage storage) { return !FAILURES.containsKey(storage); }
    public static Refusal failure(PlayerDataStorage storage) { return FAILURES.get(storage); }
    public static PlayerDataStorage actualStorage(MinecraftServer server) {
        synchronized (OWNERS) {
            PlayerDataStorage result = null;
            for (var entry : OWNERS.entrySet()) if (entry.getValue().server == server) {
                if (result != null && result != entry.getKey()) throw invalid("Ambiguous actual native player storage");
                result = entry.getKey();
            }
            if (result == null) throw invalid("No actual native player storage is bound to this server");
            return result;
        }
    }
    private static boolean original(CompoundTag data) {
        return data.get("DataVersion") instanceof IntTag version && version.intValue() == 3955;
    }
    /** Region-first references use only current raw original files in this actual native storage. */
    public static NeoForgePlayerVehicleContext vehicleContext(MinecraftServer server, Identifier actualDimension) {
        Owner owner = null;
        synchronized (OWNERS) {
            for (var entry : OWNERS.entrySet()) if (entry.getValue().server == server) {
                if (FAILURES.containsKey(entry.getKey())) throw FAILURES.get(entry.getKey());
                if (owner != null && !owner.equals(entry.getValue())) throw invalid("Ambiguous actual player storage for source vehicle context");
                owner = entry.getValue();
            }
        }
        var players = new ListTag();
        if (owner == null) return NeoForgePlayerVehicleContext.fromOriginalPlayers(players, actualDimension);
        try (var files = Files.list(owner.directory)) {
            var ids = new java.util.TreeSet<UUID>();
            for (var path : files.toList()) {
                var name = path.getFileName().toString();
                String stem = name.endsWith(".dat") ? name.substring(0, name.length()-4)
                    : name.endsWith(".dat_old") ? name.substring(0, name.length()-8) : "";
                try { var id = UUID.fromString(stem); if (id.toString().equals(stem)) ids.add(id); }
                catch (IllegalArgumentException ignored) {}
            }
            for (var id : ids) {
                Path primary = owner.directory.resolve(id + ".dat"), backup = owner.directory.resolve(id + ".dat_old");
                String primaryBefore = fingerprint(primary), backupBefore = fingerprint(backup);
                CompoundTag source;
                try { source = readExact(primary); }
                catch (RuntimeException unreadablePrimary) {
                    try { source = readExact(backup); }
                    catch (RuntimeException unreadableBackup) { continue; }
                }
                if (!primaryBefore.equals(fingerprint(primary)) || !backupBefore.equals(fingerprint(backup)))
                    throw invalid("Original player reference file changed during native region preparation");
                if (!original(source) || !source.contains("RootVehicle") || !string(source,"Dimension").equals(actualDimension.toString())) continue;
                if (!uuid(source,"UUID").equals(id)) throw invalid("Original player reference UUID disagrees with actual raw filename");
                players.add(source);
            }
        } catch (IOException error) { throw new IllegalStateException("Cannot read actual native player storage", error); }
        return NeoForgePlayerVehicleContext.fromOriginalPlayers(players, actualDimension);
    }
    /** A current native filename/winner witness; it never transfers material out of PlayerData. */
    public static final class RegionVehicleFile {
        private final PlayerDataStorage storage;
        private final Owner owner;
        private final UUID fileOwner;
        private final Path primary, backup, winner;
        private final String primaryFingerprint, backupFingerprint;
        private final CompoundTag source;
        private final boolean selected;
        private RegionVehicleFile(PlayerDataStorage storage, Owner owner, UUID fileOwner, Path primary,
                Path backup, Path winner, String primaryFingerprint, String backupFingerprint,
                CompoundTag source, boolean selected) {
            this.storage=storage;this.owner=owner;this.fileOwner=fileOwner;this.primary=primary;
            this.backup=backup;this.winner=winner;this.primaryFingerprint=primaryFingerprint;
            this.backupFingerprint=backupFingerprint;this.source=source.copy();this.selected=selected;
        }
        public UUID player() { return fileOwner; }
        public Identifier dimension() { return Identifier.parse(string(source,"Dimension")); }
        public CompoundTag originalPlayer() { return source.copy(); }
        public CompoundTag originalRoot() { return compound(compound(source,"RootVehicle"),"Entity").copy(); }
        public boolean selectedOriginal() { return selected; }
        /** Re-run on the real server thread immediately before actual entity batch publication. */
        public void requireCurrent() {
            if (!owner.server.isSameThread()) throw invalid("Original player region custody must be checked on the actual server thread");
            requireRawCurrent();
            if (owner.server.getPlayerList().getPlayer(fileOwner)!=null) throw invalid("An actual online player supersedes its old RootVehicle region snapshot");
        }
        /** Disk/owner-only preparation is safe on the actual native entity-deserializer worker. */
        public void requireRawCurrent() {
            if (!owner.equals(OWNERS.get(storage))) throw invalid("Original player region custody changed its native storage/directory owner");
            var failure=FAILURES.get(storage);if(failure!=null)throw failure;
            if (!selected) throw invalid("A newer native/primary player file supersedes the historical RootVehicle region copy");
            if (!primaryFingerprint.equals(fingerprint(primary)) || !backupFingerprint.equals(fingerprint(backup))
                    || !readExact(winner).equals(source)) throw invalid("Original player RootVehicle filename/winner/payload changed before actual region admission");
        }
    }
    /** Current .dat wins even when .dat_old contains an original vehicle. Backups can only diagnose supersession. */
    public static java.util.List<RegionVehicleFile> regionVehicleFiles(MinecraftServer server) {
        var storage=actualStorage(server);var owner=OWNERS.get(storage);
        var failed=FAILURES.get(storage);if(failed!=null)throw failed;
        var result=new java.util.ArrayList<RegionVehicleFile>();
        try(var files=Files.list(owner.directory)) {
            var ids=new java.util.TreeSet<UUID>();
            for(var path:files.toList()) {
                var name=path.getFileName().toString();String stem=name.endsWith(".dat")?name.substring(0,name.length()-4):name.endsWith(".dat_old")?name.substring(0,name.length()-8):"";
                try{var id=UUID.fromString(stem);if(id.toString().equals(stem))ids.add(id);}catch(IllegalArgumentException ignored){}
            }
            for(var id:ids) {
                var primary=owner.directory.resolve(id+".dat");var backup=owner.directory.resolve(id+".dat_old");
                var primaryBefore=fingerprint(primary);var backupBefore=fingerprint(backup);
                var current=readIfReadable(primary);var old=readIfReadable(backup);var selected=current!=null?current:old;
                if(!primaryBefore.equals(fingerprint(primary))||!backupBefore.equals(fingerprint(backup)))throw invalid("Actual player vehicle source changed during region custody preparation");
                if(vehicleIn(selected)) {
                    if(!uuid(selected,"UUID").equals(id))throw invalid("Original region vehicle player UUID differs from its actual filename");
                    result.add(new RegionVehicleFile(storage,owner,id,primary,backup,current!=null?primary:backup,primaryBefore,backupBefore,selected,original(selected)));
                }
                // Never revive old material after a successful newer .dat read, even if that .dat has no RootVehicle.
                if(current!=null&&originalVehicleIn(old)
                        &&(!vehicleIn(selected)||!uuid(compound(compound(selected,"RootVehicle"),"Entity"),"UUID").equals(uuid(compound(compound(old,"RootVehicle"),"Entity"),"UUID")))) {
                    if(!uuid(old,"UUID").equals(id))throw invalid("Historical region vehicle player UUID differs from its actual filename");
                    result.add(new RegionVehicleFile(storage,owner,id,primary,backup,backup,primaryBefore,backupBefore,old,false));
                }
            }
        }catch(IOException error){throw new IllegalStateException("Cannot witness actual native player files for region custody",error);}
        return java.util.List.copyOf(result);
    }
    private static boolean originalVehicleIn(CompoundTag source) {
        return source!=null&&original(source)&&vehicleIn(source);
    }
    private static boolean vehicleIn(CompoundTag source) {
        return source!=null&&source.contains("RootVehicle");
    }
    private static CompoundTag readIfReadable(Path path) {
        try{return readExact(path);}catch(RuntimeException ordinaryUnreadableFile){return null;}
    }
    private static CompoundTag readExact(Path path) {
        try { return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()); }
        catch (IOException error) { throw new IllegalStateException("Cannot read actual player file " + path, error); }
    }
    private static String fingerprint(Path path) {
        if (!Files.exists(path)) return "absent";
        try (var stream = Files.newInputStream(path)) {
            var digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[8192]; int read;
            while ((read = stream.read(buffer)) != -1) digest.update(buffer, 0, read);
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception error) { throw new IllegalStateException("Cannot witness actual player file " + path, error); }
    }
    private static Refusal refuse(Read read, CompoundTag original, RuntimeException cause) {
        synchronized (FAILURES) {
            var existing = FAILURES.get(read.storage);
            if (existing != null) return existing;
            var refusal = new Refusal(read.fileOwner.id(), cause); FAILURES.put(read.storage, refusal);
            if (read.owner != null) {
                try {
                    Path archive = read.owner.directory.resolve("create-import-refusals"); Files.createDirectories(archive);
                    NbtIo.writeCompressed(original.copy(), archive.resolve(read.fileOwner.id()+"-"+refusal.reportId+".original.nbt"));
                } catch (Exception archiveError) { refusal.addSuppressed(archiveError); }
                Create.LOGGER.error("Original player import refused for actual file {}; native player saves are fenced and source remains authoritative", read.winner, refusal);
                read.owner.server.halt(false);
            }
            return refusal;
        }
    }
    public static final class Refusal extends RuntimeException {
        public final UUID reportId = UUID.randomUUID();
        private Refusal(UUID actualFileOwner, Throwable cause) { super("Original Create player import refused for actual filename owner " + actualFileOwner, cause); }
    }
    private NeoForgePlayerStorageImport() {}
}
