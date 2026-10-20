package com.simibubi.create.test;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.createmod.catnip.lang.FontHelper;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Regression for cold glyph uploads from native asynchronous creative search. */
public final class CreativeSearchTooltipClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        String backend = context.computeOnClient(client -> RenderSystem.getDevice().getDeviceInfo().backendName());
        Create.LOGGER.info("CREATE_SEARCH_BACKEND {}", backend);
        // Deliberately cold glyphs, before a world, creative inventory or item hover.
        var cold = CompletableFuture.supplyAsync(() -> {
            require(!Minecraft.getInstance().isSameThread(), "Cold helper must execute on a worker");
            return TooltipHelper.cutStringTextComponent("Cold _glyphs_ Ж Щ 余 弦 旋 ᚠ ᚢ", FontHelper.Palette.STANDARD_CREATE);
        });
        context.waitFor(client -> cold.isDone(), 200);
        require(cold.join().stream().map(Component::getString).anyMatch(text -> text.contains("glyphs Ж Щ 余 弦 旋 ᚠ ᚢ")),
            "Background text must preserve Unicode and remove highlight markers");
        Create.LOGGER.info("CREATE_SEARCH_COLD_GLYPHS_PASS");

        String originalLanguage = context.computeOnClient(client -> client.getLanguageManager().getSelected());
        try (var world = context.worldBuilder().adjustSettings(settings ->
                settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
            context.waitFor(client -> client.player != null && client.level != null, 400);
            verifyIndex(context, "initial");
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitFor(client -> client.gui.screen() instanceof CreativeModeInventoryScreen, 200);
            context.runOnClient(client -> {
                var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.fromNamespaceAndPath("create", "base"));
                require(tab != null && !tab.getDisplayItems().isEmpty(), "Create creative tab must exist and contain real items");
                selectTab((CreativeModeInventoryScreen) client.gui.screen(), tab);
                require(((CreativeModeInventoryScreen) client.gui.screen()).getMenu().items.stream().anyMatch(stack -> stack.is(AllBlocks.COGWHEEL.asItem())),
                    "Actual Create creative screen must display the cogwheel");
                var palettes = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.fromNamespaceAndPath("create", "palettes"));
                require(palettes != null && !palettes.getDisplayItems().isEmpty(), "Create palettes tab must exist and contain real items");
                try {
                    var page = client.gui.screen().getClass().getMethod("getCurrentPage").invoke(client.gui.screen());
                    Create.LOGGER.info("CREATE_SEARCH_TAB_PASS page={} baseItems={} palettesItems={}", page, tab.getDisplayItems().size(), palettes.getDisplayItems().size());
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            context.waitTicks(3);
            context.takeScreenshot("create-creative-tab");
            context.runOnClient(client -> selectTab((CreativeModeInventoryScreen) client.gui.screen(),
                BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.fromNamespaceAndPath("create", "palettes"))));
            context.waitTicks(3);
            context.takeScreenshot("create-palettes-tab");
            context.runOnClient(client -> selectTab((CreativeModeInventoryScreen) client.gui.screen(),
                BuiltInRegistries.CREATIVE_MODE_TAB.getValue(CreativeModeTabs.SEARCH)));
            context.getInput().typeChars("create:cogwheel");
            context.runOnClient(client -> {
                var screen = (CreativeModeInventoryScreen) client.gui.screen();
                require(searchBox(screen).getValue().equals("create:cogwheel"), "Native character input must reach creative search");
                require(screen.getMenu().items.stream().anyMatch(stack -> stack.is(AllBlocks.COGWHEEL.asItem())),
                    "Native typed creative search must find the Create cogwheel");
            });
            context.waitTicks(3);
            context.takeScreenshot("create-creative-search-typed");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitFor(client -> client.gui.screen() == null, 200);

            var reload = new AtomicReference<CompletableFuture<Void>>();
            context.runOnClient(client -> {
                client.getLanguageManager().setSelected("ru_ru");
                client.options.languageCode = "ru_ru";
                reload.set(client.reloadResourcePacks());
            });
            context.waitFor(client -> reload.get().isDone(), 1200);
            reload.get().join();
            verifyIndex(context, "russian-after-resource-reload");
            context.getInput().holdKey(InputConstants.KEY_LSHIFT);
            try { context.runOnClient(client -> {
                require(!new ItemStack(AllBlocks.COGWHEEL).getHoverName().getString().equals("Cogwheel"),
                    "Russian resource reload must change actual translated item names");
                var description = ItemDescription.create(AllBlocks.SCHEMATICANNON.asItem(), FontHelper.Palette.STANDARD_CREATE);
                require(description != null && description.linesOnShift().size() > 2,
                    "Visible description must still be wrapped after background indexing and reload");
                for (var line : description.linesOnShift()) {
                    require(!line.getString().contains("_"), "Visible descriptions must not expose highlight markers");
                }
                var actual = new ItemStack(AllBlocks.SCHEMATICANNON).getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL);
                require(description.linesOnShift().stream().allMatch(expected -> actual.stream().anyMatch(line -> line.getString().equals(expected.getString()))),
                    "Actual shifted tooltip callback must keep render-thread wrapping after worker indexing");
            }); } finally { context.getInput().releaseKey(InputConstants.KEY_LSHIFT); }
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitFor(client -> client.gui.screen() instanceof CreativeModeInventoryScreen, 200);
            context.runOnClient(client -> {
                selectTab((CreativeModeInventoryScreen) client.gui.screen(), BuiltInRegistries.CREATIVE_MODE_TAB.getValue(CreativeModeTabs.SEARCH));
                searchBox((CreativeModeInventoryScreen) client.gui.screen()).setValue("");
            });
            context.getInput().typeChars("шестер");
            context.runOnClient(client -> require(((CreativeModeInventoryScreen) client.gui.screen()).getMenu().items.stream()
                .anyMatch(stack -> stack.is(AllBlocks.COGWHEEL.asItem())), "Native Cyrillic search must find the translated cogwheel"));
            context.waitTicks(3);
            context.takeScreenshot("create-creative-search-russian-after-reload");
            Create.LOGGER.info("CREATE_SEARCH_TYPING_AND_RELOAD_PASS");
        } finally {
            var restore = new AtomicReference<CompletableFuture<Void>>();
            context.runOnClient(client -> {
                client.getLanguageManager().setSelected(originalLanguage);
                client.options.languageCode = originalLanguage;
                restore.set(client.reloadResourcePacks());
            });
            context.waitFor(client -> restore.get().isDone(), 1200);
            restore.get().join();
        }
    }

    private static void verifyIndex(ClientGameTestContext context, String stage) {
        var future = new AtomicReference<CompletableFuture<Integer>>();
        context.runOnClient(client -> {
            ItemDescription.invalidateCaches();
            var stacks = new ArrayList<ItemStack>();
            for (var item : BuiltInRegistries.ITEM) {
                if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("create")) stacks.add(new ItemStack(item));
            }
            var registries = client.level.registryAccess();
            var itemContext = Item.TooltipContext.of(client.level);
            future.set(CompletableFuture.supplyAsync(() -> {
                require(!client.isSameThread(), "Actual tooltip sweep must execute on a worker");
                for (var stack : stacks) stack.getTooltipLines(itemContext, null, TooltipFlag.NORMAL);
                var trees = new SessionSearchTrees();
                trees.updateCreativeTooltips(registries, List.copyOf(stacks));
                require(trees.creativeNameSearch().search("create:cogwheel").stream().anyMatch(stack -> stack.is(AllBlocks.COGWHEEL.asItem())),
                    "Native asynchronous SessionSearchTrees must index Create items");
                return stacks.size();
            }));
        });
        context.waitFor(client -> future.get().isDone(), 600);
        Create.LOGGER.info("CREATE_SEARCH_NATIVE_INDEX_PASS stage={} items={}", stage, future.get().join());
    }

    private static void selectTab(CreativeModeInventoryScreen screen, net.minecraft.world.item.CreativeModeTab tab) {
        try {
            // Fabric's public API also switches pages; vanilla's private selectTab
            // refuses tabs on another page and would make this a false failure.
            var method = screen.getClass().getMethod("setSelectedTab", net.minecraft.world.item.CreativeModeTab.class);
            method.invoke(screen, tab);
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot select native creative tab", failure); }
    }

    private static EditBox searchBox(CreativeModeInventoryScreen screen) {
        try {
            var field = CreativeModeInventoryScreen.class.getDeclaredField("searchBox");
            field.setAccessible(true); return (EditBox) field.get(screen);
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot inspect native creative search field", failure); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
