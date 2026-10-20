package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.mojang.serialization.Codec;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.legacy.ChromaticCompoundItem;
import com.simibubi.create.content.legacy.NoGravMagicalDohickyItem;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;

/** Known core source persistent data; active state goes to its real native owner. */
public final class NeoForgeEntityPersistentDataImport {
    public static final String KEY = "CreateNeoForgePersistentData";
    private static final Set<String> FIELDS = Set.of("CreateNetheriteDivingBits", "CreateFireImmune", "HeavyBoots", "LavaGrounded", "VisualBacktankAir", "ContraptionDismountLocation", "ContraptionMountLocation", "ContraptionGrounded", "TrainHat", "LastOverrideLimbSwingUpdate", "OverrideLimbSwing", "JustCreated", "PlayEffects", "BypassCrushingWheel", "CreateHaunting", "CreateData");
    public record Historical(Identifier originalType, CompoundTag payload, boolean pendingFan) {
        public Historical { payload = payload.copy(); validatePayload(originalType, payload); if (pendingFan && !hasFan(payload)) throw invalid("Pending source fan import has no genuine source processing state"); }
        @Override public CompoundTag payload() { return payload.copy(); }
    }
    public static void validateSource(CompoundTag source, int version, HolderLookup.Provider registries) {
        if (version != 3955 || source.contains(KEY)) throw invalid("Original persistent data cannot claim a new native schema/version");
        if (!source.contains("NeoForgeData")) return;
        var type = Identifier.parse(string(source, "id")); var data = compound(source, "NeoForgeData"); validatePayload(type, data);
        if (data.contains("JustCreated") || data.contains("PlayEffects")) {
            var stack = ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), item(compound(source, "Item"), version, registries)).getOrThrow();
            if (!(stack.getItem() instanceof ChromaticCompoundItem) && !(stack.getItem() instanceof NoGravMagicalDohickyItem))
                throw invalid("Original legacy item effects have no actual matching native Create item");
        }
    }
    private static void validatePayload(Identifier type, CompoundTag data) {
        if (!NeoForgeEntityItemImport.isSupportedSourceType(type)) throw invalid("Persistent data needs its actual supported nonplayer source entity scope");
        if (!NeoForgeEntityItemImport.isLivingSourceType(type))
            for (String key : Set.of("CreateNetheriteDivingBits", "CreateFireImmune", "HeavyBoots", "LavaGrounded", "VisualBacktankAir", "TrainHat", "ContraptionDismountLocation", "ContraptionMountLocation"))
                if (data.contains(key)) throw invalid("Original living persistent metadata has a different source owner");
        for (String key : data.keySet()) if (!FIELDS.contains(key)) throw invalid("Unknown original core/addon entity persistent field " + key);
        for (String key : Set.of("CreateFireImmune", "HeavyBoots", "LavaGrounded", "ContraptionGrounded", "TrainHat", "JustCreated", "PlayEffects")) if (data.contains(key)) bool(data, key);
        if (data.contains("CreateNetheriteDivingBits")) {
            if (!(data.get("CreateNetheriteDivingBits") instanceof ByteTag bits) || (Byte.toUnsignedInt(bits.byteValue()) & ~15) != 0) throw invalid("Invalid original four-slot diving mask");
        }
        for (String key : Set.of("VisualBacktankAir", "LastOverrideLimbSwingUpdate")) if (data.contains(key) && integer(data, key) < 0) throw invalid("Invalid original persistent counter " + key);
        if (data.contains("OverrideLimbSwing")) floating(data, "OverrideLimbSwing");
        for (String key : Set.of("ContraptionDismountLocation", "ContraptionMountLocation")) if (data.contains(key)) vector(list(data, key));
        if (data.contains("BypassCrushingWheel")) { requireItem(type); pos(compound(data, "BypassCrushingWheel")); }
        if (data.contains("JustCreated") || data.contains("PlayEffects")) requireItem(type);
        if (data.contains("CreateHaunting")) {
            if (!type.getPath().equals("horse") || integer(data, "CreateHaunting") < 0 || integer(data, "CreateHaunting") > 100) throw invalid("Original horse haunting has no actual horse/progress schema");
        }
        if (data.contains("CreateData")) {
            requireItem(type); var create = compound(data, "CreateData"); keys(create, "entity fan processing data", "Processing");
            if (create.contains("Processing")) { var progress = compound(create, "Processing"); keys(progress, "entity fan progress", "Type", "Time"); fanType(string(progress, "Type")); if (integer(progress, "Time") < -1) throw invalid("Invalid original fan processing timer"); }
        }
    }
    private static void requireItem(Identifier type) { if (!type.getPath().equals("item")) throw invalid("Original item processing/effects field has a different genuine source entity owner"); }
    private static boolean hasFan(CompoundTag data) { return data.contains("CreateData") && compound(data, "CreateData").contains("Processing"); }
    private static String fanType(String original){return FanProcessing.stableTypeName(original);}
    public static void prepareSource(CompoundTag source, int version, HolderLookup.Provider registries) {
        validateSource(source, version, registries);
        if (!source.contains("NeoForgeData")) return;
        var data = compound(source, "NeoForgeData");
        if (!data.isEmpty()) {
            source.put(KEY, write(new Historical(Identifier.parse(string(source, "id")), data, hasFan(data))));
            var attachments = new CompoundTag();
            if (data.contains("TrainHat")) attachments.put("create:train_hat", Codec.BOOL.encodeStart(NbtOps.INSTANCE, true).getOrThrow());
            if (data.contains("BypassCrushingWheel")) attachments.put("create:bypass_crushing_wheel", net.minecraft.core.BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, pos(compound(data, "BypassCrushingWheel"))).getOrThrow());
            if (data.contains("CreateHaunting")) attachments.put("create:horse_haunting", Codec.intRange(0, 100).encodeStart(NbtOps.INSTANCE, integer(data, "CreateHaunting")).getOrThrow());
            for (String field : Set.of("ContraptionMountLocation", "ContraptionDismountLocation")) if (data.contains(field)) {
                var values = list(data, field); var vector = new Vec3(values.getDouble(0).orElseThrow(), values.getDouble(1).orElseThrow(), values.getDouble(2).orElseThrow());
                attachments.put(field.equals("ContraptionMountLocation") ? "create:contraption_mount_location" : "create:historical_contraption_dismount_location", Vec3.CODEC.encodeStart(NbtOps.INSTANCE, vector).getOrThrow());
            }
            if (!attachments.isEmpty()) source.put("fabric:attachments", attachments);
            var effects = new CompoundTag(); for (String key : Set.of("JustCreated", "PlayEffects")) if (data.contains(key)) effects.put(key, data.get(key).copy());
            if (!effects.isEmpty()) source.put("CreateLegacy", effects);
        }
        source.remove("NeoForgeData");
    }
    public static Historical read(CompoundTag tag) {
        keys(tag, "native source persistent data provenance", "SchemaVersion", "SourceDataVersion", "SourceEntityType", "Data", "PendingFan");
        if (integer(tag, "SchemaVersion") != 1 || integer(tag, "SourceDataVersion") != 3955) throw invalid("Unknown native source persistent data version");
        return new Historical(Identifier.parse(string(tag, "SourceEntityType")), compound(tag, "Data"), bool(tag, "PendingFan"));
    }
    public static CompoundTag write(Historical data) {
        var tag = new CompoundTag(); tag.putInt("SchemaVersion", 1); tag.putInt("SourceDataVersion", 3955); tag.putString("SourceEntityType", data.originalType().toString()); tag.put("Data", data.payload()); tag.putBoolean("PendingFan", data.pendingFan()); return tag;
    }
    public static Historical admitPendingFan(Entity actual, ValueInput input, Historical source) {
        if (!source.pendingFan()) return source;
        if (!(actual instanceof ItemEntity item) || item.getItem().isEmpty()) throw invalid("Pending fan state has no actual nonempty native ItemEntity owner");
        var target = (AttachmentTarget) actual;
        if (target.getAttached(FanProcessing.PROGRESS) == null) {
            var nativeRoot = input.read("fabric:attachments", Codec.PASSTHROUGH);
            if (nativeRoot.isPresent()) {
                var raw = nativeRoot.get().convert(NbtOps.INSTANCE).getValue();
                if (!(raw instanceof CompoundTag attachments)) throw invalid("Malformed native attachment root beside pending fan source");
                if (attachments.contains("create:fan_processing")) throw invalid("Present native fan progress failed decoding; original source cannot replace it");
            }
            var progress = compound(compound(source.payload(), "CreateData"), "Processing");
            var proposal = new FanProcessing.Progress(fanType(string(progress, "Type")), ItemVariant.of(item.getItem()), item.getItem().getCount(), integer(progress, "Time"), actual.level().getGameTime());
            if (target.getAttached(FanProcessing.PROGRESS) == null) target.setAttached(FanProcessing.PROGRESS, proposal);
        }
        return new Historical(source.originalType(), source.payload(), false);
    }
    private NeoForgeEntityPersistentDataImport() {}
}
