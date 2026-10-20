package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.mojang.serialization.Dynamic;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Pure, vanilla-only original3955 ENTITY_TREE conversion. No entity factory,
 * world, manager, replacement UUID, player or paid-material receipt is created.
 * Templates without UUID stay templates; callers own actual world admission.
 */
public final class NeoForgeVanillaEntityImport {
    private record Pair(CompoundTag original, CompoundTag nativeTag, int depth, NeoForgeMigrationScope scope) {}

    public static CompoundTag convert(CompoundTag source, int sourceVersion, HolderLookup.Provider registries) {
        return convert(source,sourceVersion,registries,null);
    }
    public static CompoundTag convert(CompoundTag source,int sourceVersion,HolderLookup.Provider registries,NeoForgeMigrationScope inheritedScope) {
        return convert(source,sourceVersion,registries,inheritedScope,false);
    }
    /** Called only after the complete saved boundary required every genuine original entity UUID/base state. */
    static CompoundTag convertSaved(CompoundTag source,int sourceVersion,HolderLookup.Provider registries) {
        return convert(source,sourceVersion,registries,NeoForgeMigrationScope.entity(uuid(source,"UUID")),true);
    }
    private static CompoundTag convert(CompoundTag source,int sourceVersion,HolderLookup.Provider registries,NeoForgeMigrationScope inheritedScope,boolean actualCompleteSavedTree) {
        if (sourceVersion != NeoForgeSavedDataImport.SOURCE_DATA_VERSION)
            throw invalid("Unsupported original vanilla entity version " + sourceVersion);
        var prepared = actualCompleteSavedTree?NeoForgeEntityItemImport.prepareSaved(source,sourceVersion,registries,inheritedScope)
            :NeoForgeEntityItemImport.prepare(source, sourceVersion, registries,inheritedScope);
        var fixed = DataFixers.getDataFixer().update(References.ENTITY_TREE,
            new Dynamic<Tag>(NbtOps.INSTANCE, prepared), sourceVersion,
            SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
        if (!(fixed instanceof CompoundTag result)) throw invalid("Vanilla ENTITY_TREE DFU returned a noncompound entity");
        return finishAfterVanillaFixer(source, result, sourceVersion, registries,inheritedScope,actualCompleteSavedTree);
    }

    /** Complete this exact original entity subtree after its actual containing DFU has already run. */
    public static CompoundTag finishAfterVanillaFixer(CompoundTag source, CompoundTag actualFixed, int sourceVersion,
            HolderLookup.Provider registries) {
        return finishAfterVanillaFixer(source,actualFixed,sourceVersion,registries,null);
    }
    public static CompoundTag finishAfterVanillaFixer(CompoundTag source,CompoundTag actualFixed,int sourceVersion,
            HolderLookup.Provider registries,NeoForgeMigrationScope inheritedScope) {
        return finishAfterVanillaFixer(source,actualFixed,sourceVersion,registries,inheritedScope,false);
    }
    private static CompoundTag finishAfterVanillaFixer(CompoundTag source,CompoundTag actualFixed,int sourceVersion,
            HolderLookup.Provider registries,NeoForgeMigrationScope inheritedScope,boolean actualCompleteSavedTree) {
        if (sourceVersion != NeoForgeSavedDataImport.SOURCE_DATA_VERSION)
            throw invalid("Unsupported original vanilla entity version " + sourceVersion);
        if(actualCompleteSavedTree)NeoForgeEntityItemImport.validateSaved(source,sourceVersion,registries,inheritedScope);
        else NeoForgeEntityItemImport.validate(source, sourceVersion, registries,inheritedScope);
        var result = actualFixed.copy();
        var pending = new ArrayDeque<Pair>(); pending.add(new Pair(source, result, 0,inheritedScope));
        var identities = new HashSet<UUID>(); int nodes = 0;
        while (!pending.isEmpty()) {
            var pair = pending.removeFirst();
            if (pair.depth > 64 || ++nodes > 1024) throw invalid("Original vanilla entity tree exceeds supported bounds");
            var original = pair.original; var target = pair.nativeTag; var scope=pair.scope;
            validateBase(original);
            var type = Identifier.parse(string(target, "id"));
            if (type.toString().equals("minecraft:player") || !type.getNamespace().equals("minecraft")
                    || !BuiltInRegistries.ENTITY_TYPE.containsKey(type) || !BuiltInRegistries.ENTITY_TYPE.getValue(type).canSerialize())
                throw invalid("Historical vanilla entity has no serializable actual native type: " + type);
            if (original.contains("UUID") != target.contains("UUID")) throw invalid("Vanilla DFU invented/lost an original entity UUID");
            if (original.contains("UUID")) {
                var id = uuid(original, "UUID");
                if (!id.equals(uuid(target, "UUID")) || !identities.add(id))
                    throw invalid("Original entity UUID changed or has two tree owners");
            }
            if (NeoForgeEntityItemImport.isLivingSourceType(Identifier.parse(string(original,"id"))))
                NeoForgeLivingEntityImport.finish(original,target,registries,actualCompleteSavedTree);
            if (string(original,"id").equals("minecraft:text_display") && original.contains("text")) {
                if (!(original.get("text") instanceof StringTag text)) throw invalid("Original text display text must retain flattened source JSON");
                target.put("text",NeoForgeTextComponentImport.flattened(text,sourceVersion,registries,leafScope(scope,"display_text",0)));
                ComponentSerialization.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),target.get("text")).getOrThrow();
            }
            if (original.contains("CustomName")) target.put("CustomName", NeoForgeTextComponentImport.flattened((StringTag) original.get("CustomName"), sourceVersion, registries,leafScope(scope,"customname",0)));
            if (target.contains("CustomName"))
                ComponentSerialization.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), target.get("CustomName")).getOrThrow();
            if (original.contains(NeoForgeEntityProvenance.SOURCE_KEY)) {
                var origin = NeoForgeEntityProvenance.read(compound(target, NeoForgeEntityProvenance.KEY));
                if (!origin.sourceSpawnType().equals(string(original, NeoForgeEntityProvenance.SOURCE_KEY)))
                    throw invalid("Historical DFU changed genuine source spawn provenance");
            } else if (target.contains(NeoForgeEntityProvenance.KEY)) throw invalid("Historical DFU invented source spawn provenance");
            if (original.contains("NeoForgeData") && !compound(original, "NeoForgeData").isEmpty()) {
                var historical = NeoForgeEntityPersistentDataImport.read(compound(target, NeoForgeEntityPersistentDataImport.KEY));
                if (!historical.payload().equals(compound(original, "NeoForgeData"))) throw invalid("Historical DFU lost genuine source core persistent data");
            }
            NeoForgeEntityPayloadImport.finish(original,target,sourceVersion,registries,scope);
            items(original, target, sourceVersion, registries,scope);
            var before = original.contains("Passengers") ? list(original, "Passengers") : new ListTag();
            var after = target.contains("Passengers") ? list(target, "Passengers") : new ListTag();
            if (before.size() != after.size()) throw invalid("Vanilla DFU changed actual source passenger count/order");
            for (int i = 0; i < before.size(); i++) {
                if (!(before.get(i) instanceof CompoundTag child) || !(after.get(i) instanceof CompoundTag migrated))
                    throw invalid("Malformed original/native entity child");
                pending.addLast(new Pair(child, migrated, pair.depth + 1,NeoForgeEntityItemImport.childScope(scope,i,child,actualCompleteSavedTree)));
            }
        }
        return result;
    }

    public static void validateBase(CompoundTag source) {
        for (String key : Set.of("Pos", "Motion")) if (source.contains(key)) vector(list(source, key));
        if (source.contains("Rotation")) {
            var rotation = list(source, "Rotation");
            if (rotation.size() != 2) throw invalid("Original entity rotation requires two source floats");
            for (Tag value : rotation) if (!(value instanceof FloatTag number) || !Float.isFinite(number.floatValue()))
                throw invalid("Original entity rotation must be finite source floats");
        }
        if (source.contains("FallDistance")) floating(source, "FallDistance");
        for (String key : Set.of("Fire", "Air")) if (source.contains(key) && !(source.get(key) instanceof ShortTag))
            throw invalid("Original entity field must retain its source short type: " + key);
        for (String key : Set.of("OnGround", "Invulnerable", "CustomNameVisible", "Silent", "NoGravity", "Glowing", "HasVisualFire"))
            if (source.contains(key)) bool(source, key);
        for (String key : Set.of("PortalCooldown", "TicksFrozen")) if (source.contains(key)) integer(source, key);
        for (String key : Set.of("CustomName", "Team")) if (source.contains(key)) string(source, key);
        if (source.contains("Tags")) {
            var tags = list(source, "Tags"); var unique = new HashSet<String>();
            if (tags.size() > 1024) throw invalid("Original entity scoreboard tags exceed the native limit");
            for (Tag value : tags) if (!(value instanceof StringTag text) || !unique.add(text.value()))
                throw invalid("Original entity scoreboard tag is malformed/duplicated");
        }
    }
    private static void items(CompoundTag original, CompoundTag target, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        for (String field : Set.of("ArmorItems", "HandItems")) if (original.contains(field)) {
            var slots = list(original, field);
            String[] names = field.equals("ArmorItems") ? new String[] {"feet", "legs", "chest", "head"} : new String[] {"mainhand", "offhand"};
            for (int i = 0; i < names.length; i++) equipment((CompoundTag) slots.get(i), target, names[i], version, registries,leafScope(scope,field,i));
        }
        if (original.contains("body_armor_item")) equipment(compound(original, "body_armor_item"), target, "body", version, registries,leafScope(scope,"body_armor_item",0));
        if (original.contains("SaddleItem")) equipment(compound(original, "SaddleItem"), target, "saddle", version, registries,leafScope(scope,"SaddleItem",0));
        for (String key : Set.of("Item", "item", "weapon", "FireworksItem")) if (original.contains(key))
            same(compound(original, key), target, key, version, registries, false,leafScope(scope,key,0));
        for (String key : Set.of("Items", "Inventory")) if (original.contains(key)) {
            var before = list(original, key); var after = list(target, key);
            if (before.size() != after.size()) throw invalid("Historical DFU changed original inventory row order/count");
            for (int i = 0; i < before.size(); i++) {
                var stack = ((CompoundTag) before.get(i)).copy();
                if (!(after.get(i) instanceof CompoundTag row)) throw invalid("Historical DFU lost a real inventory row");
                var nativeStack = row.copy();
                if (key.equals("Items")) {
                    if (!stack.get("Slot").equals(nativeStack.get("Slot"))) throw invalid("Historical DFU changed original3955 inventory slot custody");
                    stack.remove("Slot"); nativeStack.remove("Slot");
                }
                var migrated = sameValue(stack, nativeStack, version, registries, false,leafScope(scope,key,key.equals("Items")?Byte.toUnsignedInt(((net.minecraft.nbt.ByteTag)row.get("Slot")).byteValue()):i));
                if (key.equals("Items")) migrated.put("Slot", row.get("Slot").copy());
                after.set(i, migrated);
            }
        }
        if (original.contains("Offers")) {
            var before = list(compound(original, "Offers"), "Recipes"); var after = list(compound(target, "Offers"), "Recipes");
            if (before.size() != after.size()) throw invalid("Historical DFU changed original merchant offer order/count");
            for (int i = 0; i < before.size(); i++) {
                var offer = (CompoundTag) before.get(i);
                if (!(after.get(i) instanceof CompoundTag nativeOffer)) throw invalid("Historical DFU lost an original merchant offer");
                for (String field : Set.of("buy", "buyB", "sell")) if (offer.contains(field))
                    same(compound(offer, field), nativeOffer, field, version, registries, !field.equals("sell"),leafScope(leafScope(scope,"offers",i),field,0));
            }
            MerchantOffers.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), target.get("Offers")).getOrThrow();
        }
        if (target.contains("equipment"))
            EntityEquipment.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), target.get("equipment")).getOrThrow();
        for (String obsolete : Set.of("ArmorItems", "HandItems", "body_armor_item", "SaddleItem")) if (target.contains(obsolete)) {
            // The native schema can also retain an original equipment field in its
            // remainder after producing the real native equipment above. Both images
            // must independently retain exactly the same genuine source leaf lineage
            // before this otherwise inactive duplicate metadata may be removed.
            if(!original.contains(obsolete)) throw invalid("Historical DFU produced an unowned obsolete equipment field: "+obsolete);
            if(obsolete.equals("ArmorItems")||obsolete.equals("HandItems")){
                var sourceRows=list(original,obsolete);var residualRows=list(target,obsolete);
                if(sourceRows.size()!=residualRows.size())throw invalid("Historical duplicate equipment changed original slot extent: "+obsolete);
                for(int slot=0;slot<sourceRows.size();slot++){
                    if(!(sourceRows.get(slot) instanceof CompoundTag sourceItem)||!(residualRows.get(slot) instanceof CompoundTag residualItem))throw invalid("Malformed historical duplicate equipment row: "+obsolete);
                    checkResidual(sourceItem,residualItem,version,registries,leafScope(scope,obsolete,slot));
                }
            }else checkResidual(compound(original,obsolete),compound(target,obsolete),version,registries,leafScope(scope,obsolete,0));
            target.remove(obsolete);
        }
    }
    private static void checkResidual(CompoundTag original,CompoundTag residual,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        // An untyped DFU remainder may be the exact source-prepared image rather
        // than a fully fixed native item. Either exact preparation or separately
        // proven native lineage is required; a partly changed/extra stack refuses.
        var prepared=NeoForgeItemComponentImport.prepareItemForVanillaFixer(original,version,registries,scope);
        if(!prepared.equals(residual))sameValue(original,residual,version,registries,false,scope);
    }
    private static void equipment(CompoundTag source, CompoundTag target, String slot, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (!target.contains("equipment")) {
            if (!source.isEmpty()) throw invalid("Historical DFU lost original nonempty equipment");
            return;
        }
        same(source, compound(target, "equipment"), slot, version, registries, false,scope);
    }
    private static void same(CompoundTag source, CompoundTag target, String field, int version, HolderLookup.Provider registries, boolean cost, NeoForgeMigrationScope scope) {
        if (!target.contains(field) && source.isEmpty()) return;
        var migrated = sameValue(source, compound(target, field), version, registries, cost,scope);
        // The source empty optional stack represents no physical material. Native
        // entity fields use the nonempty codec and express that state by absence.
        if (source.isEmpty()) target.remove(field); else target.put(field, migrated);
    }
    private static CompoundTag sameValue(CompoundTag source, CompoundTag target, int version, HolderLookup.Provider registries, boolean cost, NeoForgeMigrationScope scope) {
        var before = source.copy(); var after = target.copy();
        int originalCount = source.contains("count") ? integer(source, "count") : 1;
        if (cost) {
            int nativeCount = target.contains("count") ? integer(target, "count") : 1;
            if (nativeCount != originalCount) throw invalid("Historical DFU changed the genuine original cost count");
            before.putInt("count", 1); after.putInt("count", 1);
        }
        // ENTITY_TREE already fixed this exact leaf. The postpass only repairs typed
        // original text/hover and nested item locations, without a second enclosing DFU.
        after = NeoForgeItemComponentImport.finishItemAfterVanillaFixer(before, after, version, registries,scope);
        keys(after, "native typed entity item", "id", "count", "components");
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var stack = ItemStack.OPTIONAL_CODEC.parse(ops, after).getOrThrow();
        var canonical = ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).getOrThrow();
        if (!item(before, version, registries,scope).equals(canonical))
            throw invalid("Native typed entity item did not retain its exact original item/component/count lineage");
        var migrated = ((CompoundTag) canonical).copy();
        if (cost) {
            migrated.putInt("count", originalCount);
            var decoded = ItemCost.CODEC.parse(ops, migrated).getOrThrow();
            if (decoded.count() != originalCount) throw invalid("Native trade codec changed the genuine original cost count");
        }
        return migrated;
    }
    private static NeoForgeMigrationScope leafScope(NeoForgeMigrationScope scope,String field,int index){
        return NeoForgeEntityItemImport.leafScope(scope,field,index);
    }
    private NeoForgeVanillaEntityImport() {}
}
