package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

/**
 * Strict item preflight at the actual typed vanilla 1.21.1 ENTITY_TREE leaves.
 * Never searches arbitrary compounds for item-shaped data and never loads an entity.
 */
public final class NeoForgeEntityItemImport {
    private static final Set<String> SOURCE_TYPES = Set.of(
        "allay",
        "area_effect_cloud",
        "armadillo",
        "armor_stand",
        "arrow",
        "axolotl",
        "bat",
        "bee",
        "blaze",
        "block_display",
        "boat",
        "bogged",
        "breeze",
        "breeze_wind_charge",
        "camel",
        "cat",
        "cave_spider",
        "chest_boat",
        "chest_minecart",
        "chicken",
        "cod",
        "command_block_minecart",
        "cow",
        "creeper",
        "dolphin",
        "donkey",
        "dragon_fireball",
        "drowned",
        "egg",
        "elder_guardian",
        "end_crystal",
        "ender_dragon",
        "ender_pearl",
        "enderman",
        "endermite",
        "evoker",
        "evoker_fangs",
        "experience_bottle",
        "experience_orb",
        "eye_of_ender",
        "falling_block",
        "firework_rocket",
        "fox",
        "frog",
        "furnace_minecart",
        "ghast",
        "giant",
        "glow_item_frame",
        "glow_squid",
        "goat",
        "guardian",
        "hoglin",
        "hopper_minecart",
        "horse",
        "husk",
        "illusioner",
        "interaction",
        "iron_golem",
        "item",
        "item_display",
        "item_frame",
        "ominous_item_spawner",
        "fireball",
        "leash_knot",
        "lightning_bolt",
        "llama",
        "llama_spit",
        "magma_cube",
        "marker",
        "minecart",
        "mooshroom",
        "mule",
        "ocelot",
        "painting",
        "panda",
        "parrot",
        "phantom",
        "pig",
        "piglin",
        "piglin_brute",
        "pillager",
        "polar_bear",
        "potion",
        "pufferfish",
        "rabbit",
        "ravager",
        "salmon",
        "sheep",
        "shulker",
        "shulker_bullet",
        "silverfish",
        "skeleton",
        "skeleton_horse",
        "slime",
        "small_fireball",
        "sniffer",
        "snow_golem",
        "snowball",
        "spawner_minecart",
        "spectral_arrow",
        "spider",
        "squid",
        "stray",
        "strider",
        "tadpole",
        "text_display",
        "tnt",
        "tnt_minecart",
        "trader_llama",
        "trident",
        "tropical_fish",
        "turtle",
        "vex",
        "villager",
        "vindicator",
        "wandering_trader",
        "warden",
        "wind_charge",
        "witch",
        "wither",
        "wither_skeleton",
        "wither_skull",
        "wolf",
        "zoglin",
        "zombie",
        "zombie_horse",
        "zombie_villager",
        "zombified_piglin",
        "fishing_bobber");
    private static final Set<String> SOURCE_MOBS = Set.of(
        "allay",
        "armadillo",
        "axolotl",
        "bat",
        "bee",
        "blaze",
        "bogged",
        "breeze",
        "camel",
        "cat",
        "cave_spider",
        "chicken",
        "cod",
        "cow",
        "creeper",
        "dolphin",
        "donkey",
        "drowned",
        "elder_guardian",
        "ender_dragon",
        "enderman",
        "endermite",
        "evoker",
        "fox",
        "frog",
        "ghast",
        "giant",
        "glow_squid",
        "goat",
        "guardian",
        "hoglin",
        "horse",
        "husk",
        "illusioner",
        "iron_golem",
        "llama",
        "magma_cube",
        "mooshroom",
        "mule",
        "ocelot",
        "panda",
        "parrot",
        "phantom",
        "pig",
        "piglin",
        "piglin_brute",
        "pillager",
        "polar_bear",
        "pufferfish",
        "rabbit",
        "ravager",
        "salmon",
        "sheep",
        "shulker",
        "silverfish",
        "skeleton",
        "skeleton_horse",
        "slime",
        "sniffer",
        "snow_golem",
        "spider",
        "squid",
        "stray",
        "strider",
        "tadpole",
        "trader_llama",
        "tropical_fish",
        "turtle",
        "vex",
        "villager",
        "vindicator",
        "wandering_trader",
        "warden",
        "witch",
        "wither",
        "wither_skeleton",
        "wolf",
        "zoglin",
        "zombie",
        "zombie_horse",
        "zombie_villager",
        "zombified_piglin");
    private static final Set<String> HORSES = Set.of("camel", "donkey", "horse", "llama", "mule", "skeleton_horse", "trader_llama", "zombie_horse");
    private static final Set<String> CHESTED_HORSES = Set.of("donkey", "llama", "mule", "trader_llama");
    private static final Set<String> ITEM_UPPER = Set.of("item", "item_frame", "glow_item_frame", "eye_of_ender", "fireball", "small_fireball", "snowball", "egg", "ender_pearl", "experience_bottle", "potion");
    private static final Set<String> ITEM_LOWER = Set.of("item_display", "ominous_item_spawner", "arrow", "spectral_arrow", "trident");
    private static final Set<String> CONTAINERS = Set.of("chest_boat", "chest_minecart", "hopper_minecart");
    private record Visit(CompoundTag tag, int depth, NeoForgeMigrationScope scope) {}

    static boolean isSupportedSourceType(Identifier type) { return type.getNamespace().equals("minecraft") && SOURCE_TYPES.contains(type.getPath()); }
    static boolean isLivingSourceType(Identifier type) { return isSupportedSourceType(type) && (SOURCE_MOBS.contains(type.getPath()) || type.getPath().equals("armor_stand")); }

    public static void validate(CompoundTag source, int sourceVersion, HolderLookup.Provider registries) {
        validate(source, sourceVersion, registries, null);
    }
    public static void validate(CompoundTag source, int sourceVersion, HolderLookup.Provider registries, NeoForgeMigrationScope inheritedScope) {
        validate(source,sourceVersion,registries,inheritedScope,false);
    }
    private static void validate(CompoundTag source,int sourceVersion,HolderLookup.Provider registries,NeoForgeMigrationScope inheritedScope,boolean actualCompleteSavedTree) {
        if (sourceVersion != NeoForgeSavedDataImport.SOURCE_DATA_VERSION)
            throw invalid("Unsupported original entity item data version " + sourceVersion);
        var pending = new ArrayDeque<Visit>(); pending.add(new Visit(source, 0, inheritedScope)); int nodes = 0;
        while (!pending.isEmpty()) {
            var visit = pending.removeFirst();
            if (visit.depth > 64 || ++nodes > 1024) throw invalid("Original entity tree exceeds supported bounds");
            var entity = visit.tag; var scope = visit.scope; var identifier = Identifier.parse(string(entity, "id"));
            if (!identifier.getNamespace().equals("minecraft") || !SOURCE_TYPES.contains(identifier.getPath()))
                throw invalid("Original custom/unknown/player entity needs its complete genuine schema bridge: " + identifier);
            String type = identifier.getPath();
            NeoForgeVanillaEntitySchema.check(type, entity);
            NeoForgeEntityProvenance.validateSource(entity);
            if (entity.contains(NeoForgeEntityProvenance.SOURCE_KEY) && !SOURCE_MOBS.contains(type))
                throw invalid("Original spawn provenance has no genuine source Mob owner");
            for (String field : Set.of("neoforge:attachments", "ForgeCaps", "ForgeData")) if (entity.contains(field)) {
                if (!compound(entity, field).isEmpty()) throw invalid("Original entity has a separate unconverted extension owner: " + field);
            }
            NeoForgeEntityPayloadImport.validate(entity,sourceVersion,registries,scope);
            if (entity.contains("Lifespan") && (!type.equals("item") || integer(entity, "Lifespan") != 6000))
                throw invalid("Original nonstandard NeoForge item lifespan requires a genuine native expiry bridge");
            NeoForgeEntityPersistentDataImport.validateSource(entity, sourceVersion, registries);
            if (isLivingSourceType(identifier)) NeoForgeLivingEntityImport.validate(entity,registries);
            if (type.equals("text_display") && entity.contains("text")) string(entity,"text");
            equipment(entity, type, sourceVersion, registries, scope);
            single(entity, "SaddleItem", HORSES.contains(type), sourceVersion, registries, scope);
            single(entity, "Item", ITEM_UPPER.contains(type), sourceVersion, registries, scope);
            if (type.equals("item") && (!entity.contains("Item") || compound(entity, "Item").isEmpty()))
                throw invalid("Original ItemEntity cannot be admitted with an absent/empty material stack");
            single(entity, "item", ITEM_LOWER.contains(type), sourceVersion, registries, scope);
            single(entity, "weapon", type.equals("arrow") || type.equals("spectral_arrow") || type.equals("trident"), sourceVersion, registries, scope);
            single(entity, "FireworksItem", type.equals("firework_rocket"), sourceVersion, registries, scope);
            if (entity.contains("Items")) {
                if (CHESTED_HORSES.contains(type)) {
                    if (!bool(entity, "ChestedHorse")) throw invalid("Original unchested horse cannot own a chest inventory");
                    int columns = 5;
                    if (type.equals("llama") || type.equals("trader_llama")) {
                        columns = integer(entity, "Strength");
                        if (columns < 1 || columns > 5) throw invalid("Original llama inventory strength is outside its real bounds");
                    }
                    sparse(entity, "Items", 3 * columns, sourceVersion, registries, scope);
                } else {
                    if (!CONTAINERS.contains(type)) throw invalid("Original Items field has no actual vanilla container owner: " + type);
                    if (entity.contains("LootTable")) throw invalid("Original entity cannot own both materialized items and a deferred loot owner");
                    sparse(entity, "Items", type.equals("hopper_minecart") ? 5 : 27, sourceVersion, registries, scope);
                }
            }
            if (entity.contains("Inventory")) {
                int capacity = switch (type) {
                    case "allay" -> 1;
                    case "pillager" -> 5;
                    case "piglin", "villager", "wandering_trader" -> 8;
                    default -> throw invalid("Original Inventory field has no actual vanilla carrier owner: " + type);
                };
                var inventory = list(entity, "Inventory");
                // SimpleContainer.createTag emits nonempty compounds without Slot. Do not
                // reinterpret it as an ItemStackHandler or silently merge/drop excess rows.
                if (inventory.size() > capacity) throw invalid("Original carried inventory exceeds its actual capacity");
                for (int i=0;i<inventory.size();i++) physical(inventory.get(i), false, sourceVersion, registries, leafScope(scope,"Inventory",i));
            }
            if (entity.contains("Offers")) {
                if (!type.equals("villager") && !type.equals("wandering_trader"))
                    throw invalid("Original trades have no actual merchant owner");
                offers(compound(entity, "Offers"), sourceVersion, registries, scope);
            }
            for (String obsolete : Set.of("ArmorItem", "DecorItem", "EnderItems", "equipment"))
                if (entity.contains(obsolete)) throw invalid("Original source3955 entity has an unsupported item schema " + obsolete);
            if (entity.contains("Passengers")) {
                var children=list(entity,"Passengers");
                for(int i=0;i<children.size();i++) {
                    if (!(children.get(i) instanceof CompoundTag child)) throw invalid("Malformed original entity passenger");
                    pending.addLast(new Visit(child, visit.depth + 1, childScope(scope,i,child,actualCompleteSavedTree)));
                }
            }
        }
    }

    /**
     * Copies the original typed tree, changing only Create component branches at
     * actual item leaves. Vanilla item/entity fields keep their original3955
     * schema for exactly one later whole ENTITY_TREE historical DFU.
     */
    public static CompoundTag prepare(CompoundTag source, int version, HolderLookup.Provider registries) {
        return prepare(source, version, registries, null);
    }
    public static CompoundTag prepare(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope inheritedScope) {
        return prepare(source,version,registries,inheritedScope,false);
    }
    static CompoundTag prepareSaved(CompoundTag source,int version,HolderLookup.Provider registries,NeoForgeMigrationScope actualSavedRoot) {
        return prepare(source,version,registries,actualSavedRoot,true);
    }
    private static CompoundTag prepare(CompoundTag source,int version,HolderLookup.Provider registries,NeoForgeMigrationScope inheritedScope,boolean actualCompleteSavedTree) {
        validate(source, version, registries, inheritedScope,actualCompleteSavedTree);
        var result = source.copy();
        var pending = new ArrayDeque<Visit>(); pending.add(new Visit(result,0,inheritedScope));
        while (!pending.isEmpty()) {
            var visit = pending.removeFirst(); var entity = visit.tag; var scope = visit.scope;
            NeoForgeEntityProvenance.prepareSource(entity, entity);
            // Actual original ItemEntity default and actual native ItemEntity.tick
            // both expire at age6000. Nonstandard old overrides were refused above.
            if (entity.contains("Lifespan")) entity.remove("Lifespan");
            NeoForgeEntityPersistentDataImport.prepareSource(entity, version, registries);
            NeoForgeEntityPayloadImport.prepare(entity,entity,version,registries,scope);
            if (entity.contains("neoforge:attachments") && compound(entity, "neoforge:attachments").isEmpty()) entity.remove("neoforge:attachments");
            for (String key : Set.of("ArmorItems", "HandItems", "Inventory")) if (entity.contains(key)) {
                var stacks = list(entity, key);
                for (int i = 0; i < stacks.size(); i++) stacks.set(i, prepareStack((CompoundTag) stacks.get(i), version, registries, leafScope(scope,key,i)));
            }
            for (String key : Set.of("body_armor_item", "SaddleItem", "Item", "item", "weapon", "FireworksItem"))
                if (entity.contains(key)) entity.put(key, prepareStack(compound(entity, key), version, registries, leafScope(scope,key,0)));
            if (entity.contains("Items")) {
                var stacks = list(entity, "Items");
                for (int i = 0; i < stacks.size(); i++) {
                    var entry = (CompoundTag) stacks.get(i); var stack = entry.copy(); var slot = stack.get("Slot"); stack.remove("Slot");
                    var prepared = prepareStack(stack, version, registries, leafScope(scope,"Items",Byte.toUnsignedInt(((ByteTag)slot).byteValue()))); prepared.put("Slot", slot.copy()); stacks.set(i, prepared);
                }
            }
            if (entity.contains("Offers")) for (int offerIndex=0;offerIndex<list(compound(entity,"Offers"),"Recipes").size();offerIndex++) {
                var offer = (CompoundTag) list(compound(entity,"Offers"),"Recipes").get(offerIndex);
                var offerScope=leafScope(scope,"offers",offerIndex);
                offer.put("sell", prepareStack(compound(offer, "sell"), version, registries,leafScope(offerScope,"sell",0)));
                for (String key : Set.of("buy", "buyB")) if (offer.contains(key)) {
                    var original = compound(offer, key); var probe = original.copy(); probe.putInt("count", 1);
                    var prepared = prepareStack(probe, version, registries,leafScope(offerScope,key,0));
                    if (original.contains("count")) prepared.putInt("count", integer(original, "count")); else prepared.remove("count");
                    offer.put(key, prepared);
                }
            }
            if (entity.contains("Passengers")) {
                var children=list(entity,"Passengers");for(int i=0;i<children.size();i++)pending.addLast(new Visit((CompoundTag)children.get(i),visit.depth+1,childScope(scope,i,(CompoundTag)children.get(i),actualCompleteSavedTree)));
            }
        }
        return result;
    }
    private static CompoundTag prepareStack(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        return NeoForgeItemComponentImport.prepareItemForVanillaFixer(source, version, registries, scope);
    }

    private static void equipment(CompoundTag entity, String type, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        boolean mob = SOURCE_MOBS.contains(type);
        for (String key : Set.of("ArmorItems", "HandItems")) if (entity.contains(key)) {
            if (!mob && !type.equals("armor_stand")) throw invalid("Original equipment has no actual living owner: " + type);
            var slots = list(entity, key);
            if (slots.size() != (key.equals("ArmorItems") ? 4 : 2)) throw invalid("Original equipment slot count disagrees with its real source schema");
            for (int i=0;i<slots.size();i++) physical(slots.get(i), true, version, registries,leafScope(scope,key,i));
        }
        single(entity, "body_armor_item", mob, version, registries,scope);
    }
    private static void single(CompoundTag owner, String field, boolean actualOwner, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (!owner.contains(field)) return;
        if (!actualOwner) throw invalid("Original item field has no typed vanilla owner: " + field);
        physical(compound(owner, field), true, version, registries,leafScope(scope,field,0));
    }
    private static void physical(Tag value, boolean allowEmpty, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (!(value instanceof CompoundTag stack)) throw invalid("Malformed original entity item stack");
        var converted = item(stack, version, registries,scope);
        if (!allowEmpty && converted.isEmpty()) throw invalid("Sparse original entity inventory/trade cannot contain an empty material row");
    }
    private static void sparse(CompoundTag owner, String field, int capacity, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        var occupied = new HashSet<Integer>();
        for (Tag value : list(owner, field)) {
            if (!(value instanceof CompoundTag entry)) throw invalid("Malformed original sparse entity inventory");
            keys(entry, "vanilla sparse entity inventory", "Slot", "id", "count", "components");
            if (!(entry.get("Slot") instanceof ByteTag slotTag)) throw invalid("Original vanilla inventory Slot must be an unsigned byte");
            int slot = Byte.toUnsignedInt(slotTag.byteValue());
            if (slot >= capacity || !occupied.add(slot)) throw invalid("Original entity inventory slot is duplicate/outside its actual container");
            var stack = entry.copy(); stack.remove("Slot");
            physical(stack, false, version, registries,leafScope(scope,field,slot));
        }
    }
    private static void offers(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        keys(source, "original merchant offers", "Recipes");
        var recipes=list(source,"Recipes");
        for (int i=0;i<recipes.size();i++) {
            var offerScope=leafScope(scope,"offers",i);
            if (!(recipes.get(i) instanceof CompoundTag offer)) throw invalid("Malformed original merchant offer");
            keys(offer, "original merchant offer", "buy", "buyB", "sell", "uses", "maxUses", "rewardExp", "specialPrice", "demand", "priceMultiplier", "xp");
            cost(compound(offer, "buy"), version, registries,leafScope(offerScope,"buy",0));
            if (offer.contains("buyB")) cost(compound(offer, "buyB"), version, registries,leafScope(offerScope,"buyB",0));
            physical(compound(offer, "sell"), false, version, registries,leafScope(offerScope,"sell",0));
            for (String field : Set.of("uses", "maxUses", "specialPrice", "demand", "xp")) if (offer.contains(field)) integer(offer, field);
            if (offer.contains("rewardExp")) bool(offer, "rewardExp");
            if (offer.contains("priceMultiplier")) floating(offer, "priceMultiplier");
        }
    }
    private static void cost(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        keys(source, "original merchant ItemCost", "id", "count", "components");
        string(source, "id");
        if (source.contains("count") && integer(source, "count") <= 0) throw invalid("Original merchant cost must be positive");
        if (source.contains("components")) {
            var predicate = compound(source, "components");
            for (String key : predicate.keySet()) if (key.startsWith("!"))
                throw invalid("Original merchant DataComponentPredicate cannot remove a component");
        }
        // A cost is a predicate, not a physical stack. Its genuine source codec has
        // an arbitrary positive count. Validate its typed components at count1,
        // leaving the actual original trade count untouched for ENTITY_TREE DFU.
        var componentProbe = source.copy(); componentProbe.putInt("count", 1);
        physical(componentProbe, false, version, registries,scope);
    }
    static void validateSaved(CompoundTag source,int version,HolderLookup.Provider registries,NeoForgeMigrationScope actualSavedRoot) {
        validate(source,version,registries,actualSavedRoot,true);
    }
    static NeoForgeMigrationScope childScope(NeoForgeMigrationScope inherited,int actualIndex,CompoundTag originalChild,boolean actualCompleteSavedTree) {
        return actualCompleteSavedTree?NeoForgeMigrationScope.entity(uuid(originalChild,"UUID")):leafScope(inherited,"passenger",actualIndex);
    }
    static NeoForgeMigrationScope leafScope(NeoForgeMigrationScope scope, String field, int actualIndex) {
        return scope == null ? null : scope.item(field.toLowerCase(java.util.Locale.ROOT), actualIndex);
    }
    private NeoForgeEntityItemImport() {}
}
