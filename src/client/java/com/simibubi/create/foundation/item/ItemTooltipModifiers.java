package com.simibubi.create.foundation.item;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyTooltip;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.catnip.lang.LangNumberFormat;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** The source default modifier factory and ClientEvents chain over the actual native item registry. */
public final class ItemTooltipModifiers {
    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        registerSourceDescriptionKeys();
        // Native registration replaces Registrate; attach the same source factory to actual Create items.
        for (var item : BuiltInRegistries.ITEM) {
            if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Create.ID)) continue;
            TooltipModifier.REGISTRY.register(item,
                new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                    .andThen(TooltipModifier.mapNull(KineticStats.create(item))));
        }
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Create.asResource("item_descriptions"),
            (ResourceManagerReloadListener) manager -> {
                ItemDescription.invalidateCaches();
                LangNumberFormat.numberFormat.update();
                TooltipModifier.REGISTRY.invalidate();
            });
        ItemTooltipCallback.EVENT.register((stack, context, flags, tooltip) -> {
            var player = Minecraft.getInstance().player;
            if (player == null || !AllConfigs.client().tooltips.get()) return;
            var nativeContext = new TooltipModifier.Context(stack, context, flags, player, tooltip);
            var modifier = TooltipModifier.REGISTRY.get(stack.getItem());
            if (modifier != null && modifier != TooltipModifier.EMPTY) modifier.modify(nativeContext);
            SequencedAssemblyTooltip.addToTooltip(nativeContext);
        });
    }

    private static void registerSourceDescriptionKeys() {
        ItemDescription.useKey(AllItems.CARDBOARD_HELMET, "item.create.cardboard_armor");
        ItemDescription.useKey(AllItems.CARDBOARD_CHESTPLATE, "item.create.cardboard_armor");
        ItemDescription.useKey(AllItems.CARDBOARD_LEGGINGS, "item.create.cardboard_armor");
        ItemDescription.useKey(AllItems.CARDBOARD_BOOTS, "item.create.cardboard_armor");
        ItemDescription.referKey(AllItems.RED_SAND_PAPER, () -> AllItems.SAND_PAPER);
        ItemDescription.referKey(AllBlocks.MYSTERIOUS_CUCKOO_CLOCK, () -> AllBlocks.CUCKOO_CLOCK);
        AllBlocks.POSTBOXES.values().forEach(block -> ItemDescription.useKey(block, "block.create.package_postbox"));
        AllBlocks.TOOLBOXES.values().forEach(block -> ItemDescription.useKey(block, "block.create.toolbox"));
        AllBlocks.SEATS.values().forEach(block -> ItemDescription.useKey(block, "block.create.seat"));
        for (var block : AllBlocks.tableCloths()) ItemDescription.useKey(block, "block.create.table_cloth");
    }

    private ItemTooltipModifiers() {}
}
