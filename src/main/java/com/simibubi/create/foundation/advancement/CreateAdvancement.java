package com.simibubi.create.foundation.advancement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import com.simibubi.create.Create;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.advancements.triggers.ItemUsedOnLocationTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/** Original Create advancement DSL/runtime, using the native 26.3 advancement and criterion API. */
public class CreateAdvancement {
    static final Identifier BACKGROUND = Create.asResource("textures/gui/advancements.png");
    static final String LANG = "advancement." + Create.ID + ".";
    static final String SECRET_SUFFIX = "\n\u00A77(Hidden Advancement)";
    private final Advancement.Builder mcBuilder = Advancement.Builder.advancement();
    private SimpleCreateTrigger builtinTrigger;
    private CreateAdvancement parent;
    private final Builder createBuilder = new Builder();
    AdvancementHolder datagenResult;
    private final String id;
    private String title;
    private String description;

    public CreateAdvancement(String id, UnaryOperator<Builder> configure) {
        this.id = id;
        configure.apply(createBuilder);
        if (!createBuilder.externalTrigger) {
            builtinTrigger = AllTriggers.addSimple(id + "_builtin");
            mcBuilder.addCriterion("0", builtinTrigger.createCriterion(builtinTrigger.instance()));
        }
        if (createBuilder.type == TaskType.SECRET)
            description += SECRET_SUFFIX;
        AllAdvancements.ENTRIES.add(this);
    }

    public String id() { return id; }
    private String titleKey() { return LANG + id; }
    private String descriptionKey() { return titleKey() + ".desc"; }

    public boolean isAlreadyAwardedTo(Player player) {
        if (!(player instanceof ServerPlayer actual))
            return true;
        var advancement = actual.level().getServer().getAdvancements().get(Create.asResource(id));
        return advancement == null || actual.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    public void awardTo(Player player) {
        if (!(player instanceof ServerPlayer actual))
            return;
        if (builtinTrigger == null)
            throw new UnsupportedOperationException("Advancement " + id + " uses external Triggers, it cannot be awarded directly");
        builtinTrigger.trigger(actual);
    }

    void save(Consumer<AdvancementHolder> consumer, HolderLookup.Provider registries) {
        if (parent != null)
            mcBuilder.parent(parent.datagenResult);
        for (var criterion : createBuilder.deferredCriteria)
            mcBuilder.addCriterion(criterion.key(), criterion.factory().apply(registries));
        var icon = ItemStackTemplate.fromNonEmptyStack(createBuilder.iconFactory.apply(registries));
        var title = Component.translatable(titleKey());
        var description = Component.translatable(descriptionKey()).withStyle(style -> style.withColor(0xDBA213));
        var type = createBuilder.type;
        if (id.equals("root"))
            mcBuilder.rootDisplay(icon, title, description, BACKGROUND, type.advancementType, type.toast, type.announce, type.hide);
        else
            mcBuilder.display(icon, title, description, type.advancementType, type.toast, type.announce, type.hide);
        datagenResult = mcBuilder.build(Create.asResource(id));
        consumer.accept(datagenResult);
    }

    void provideLang(BiConsumer<String, String> consumer) {
        consumer.accept(titleKey(), title);
        consumer.accept(descriptionKey(), description);
    }

    public enum TaskType {
        SILENT(AdvancementType.TASK, false, false, false),
        NORMAL(AdvancementType.TASK, true, false, false),
        NOISY(AdvancementType.TASK, true, true, false),
        EXPERT(AdvancementType.GOAL, true, true, false),
        SECRET(AdvancementType.GOAL, true, true, true);
        final AdvancementType advancementType;
        final boolean toast, announce, hide;
        TaskType(AdvancementType advancementType, boolean toast, boolean announce, boolean hide) {
            this.advancementType = advancementType;
            this.toast = toast;
            this.announce = announce;
            this.hide = hide;
        }
    }

    public class Builder {
        private record DeferredCriterion(String key, Function<HolderLookup.Provider, Criterion<?>> factory) {}
        private final List<DeferredCriterion> deferredCriteria = new ArrayList<>();
        private TaskType type = TaskType.NORMAL;
        private boolean externalTrigger;
        private int keyIndex;
        private Function<HolderLookup.Provider, ItemStack> iconFactory;
        public Builder special(TaskType type) { this.type = type; return this; }
        public Builder after(CreateAdvancement other) { parent = other; return this; }
        // Mod initialization precedes native item-component binding; construct icons during save.
        public Builder icon(ItemLike item) { return icon(registries -> new ItemStack(item)); }
        public Builder icon(ItemStack stack) { return icon(registries -> stack); }
        public Builder icon(Function<HolderLookup.Provider, ItemStack> factory) { iconFactory = factory; return this; }
        public Builder title(String title) { CreateAdvancement.this.title = title; return this; }
        public Builder description(String description) { CreateAdvancement.this.description = description; return this; }
        public Builder whenBlockPlaced(Block block) {
            return externalTrigger(registries -> ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(registries.lookupOrThrow(Registries.BLOCK), block));
        }
        public Builder whenIconCollected() {
            var factory = java.util.Objects.requireNonNull(iconFactory, "Set the advancement icon before its criterion");
            return externalTrigger(registries -> InventoryChangeTrigger.TriggerInstance.hasItems(factory.apply(registries).getItem()));
        }
        public Builder whenItemCollected(ItemLike item) {
            return externalTrigger(InventoryChangeTrigger.TriggerInstance.hasItems(item));
        }
        public Builder whenItemCollected(TagKey<Item> tag) {
            return externalTrigger(registries -> InventoryChangeTrigger.TriggerInstance.hasItems(
                ItemPredicate.Builder.item().of(registries.lookupOrThrow(Registries.ITEM), tag).build()));
        }
        public Builder awardedForFree() {
            return externalTrigger(InventoryChangeTrigger.TriggerInstance.hasItems(new ItemLike[] {}));
        }
        public Builder externalTrigger(Criterion<?> criterion) {
            mcBuilder.addCriterion(String.valueOf(keyIndex++), criterion);
            externalTrigger = true;
            return this;
        }
        private Builder externalTrigger(Function<HolderLookup.Provider, Criterion<?>> factory) {
            deferredCriteria.add(new DeferredCriterion(String.valueOf(keyIndex++), factory));
            externalTrigger = true;
            return this;
        }
    }
}
