package net.createmod.ponder.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.Create;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

/** Ordinary-player Ponder input and the real resource/template path, without fixture registrations. */
public final class PonderInteractionClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var errors = new ArrayList<String>();
        var logger = (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
        var appender = new AbstractAppender("ponder-datafix-audit", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY) {
            @Override public void append(LogEvent event) {
                if (event.getLevel().isMoreSpecificThan(Level.ERROR) && event.getMessage().getFormattedMessage().contains("Not a list")) {
                    errors.add(event.getMessage().getFormattedMessage());
                    new Throwable("PONDER_DFU_LOGGING_STACK " + event.getMessage().getFormattedMessage()).printStackTrace();
                }
            }
        };
        appender.start(); logger.addAppender(appender);
        boolean controllerAtStartup = context.computeOnClient(client -> {
            try { return Class.forName("net.fabricmc.fabric.impl.attachment.AttachmentRegistryImpl")
                .getMethod("get", Identifier.class).invoke(null, Create.asResource("minecart_controller")) != null; }
            catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot inspect actual bootstrap attachment registry",failure); }
        });
        Create.LOGGER.info("NATIVE_CART_REGISTERED_AT_STARTUP {}", controllerAtStartup);
        context.runOnClient(client -> { for (int i=0;i<9;i++) client.getHotbarManager().get(i); });
        net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world = null;
        boolean snapshot = Boolean.getBoolean("create.test.userSnapshot");
        try {
            if (snapshot) world = new net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl(context,
                context.computeOnClient(client -> client.gameDirectory.toPath().resolve("saves/UserWorldSnapshot"))).open();
            else world = context.worldBuilder().create();
            context.waitFor(client -> client.level != null && client.player != null, 400);
            verifyNativeCartRoundTrip(context);
            verifyFilledHotbar(context);
            var ui = context.computeOnClient(client -> {
                long start = System.nanoTime();
                var screen = PonderUI.of(Create.asResource("cogwheel"));
                ScreenOpener.transitionTo(screen);
                Create.LOGGER.info("PONDER_OPEN_COLD ms={}", (System.nanoTime()-start)/1_000_000);
                return screen;
            });
            context.runOnClient(client -> {
                long start = System.nanoTime();
                var repeated = PonderUI.of(Create.asResource("cogwheel"));
                require(repeated.getActiveScene() != ui.getActiveScene(), "Reopening must retain independent mutable scene owners");
                Create.LOGGER.info("PONDER_OPEN_REPEATED ms={}", (System.nanoTime()-start)/1_000_000);
            });
            context.runOnClient(client -> {
                require(!PonderIndex.editingModeActive(), "This case must use ordinary player mode");
                long start = System.nanoTime();
                var templates = PonderIndex.getSceneAccess().getRegisteredEntries().stream()
                    .map(entry -> entry.getValue().getSchematicLocation()).distinct().toList();
                for (var template : templates) PonderSceneRegistry.loadSchematic(template);
                Create.LOGGER.info("PONDER_TEMPLATE_LOAD count={} ms={} errors={}", templates.size(), (System.nanoTime()-start)/1_000_000, errors.size());
            });
            context.waitTicks(25);
            context.getInput().pressKey(InputConstants.KEY_SPACE);
            context.takeScreenshot("ponder-ordinary-before-drag");
            var before = context.computeOnClient(client -> ui.getActiveScene().getTransform().apply(new com.mojang.blaze3d.vertex.PoseStack(), 0).last().pose().toString());
            float[] authored = context.computeOnClient(client -> new float[]{ui.getActiveScene().getTransform().xRotation.getValue(), ui.getActiveScene().getTransform().yRotation.getValue()});
            float[] center = context.computeOnClient(client -> new float[]{client.getWindow().getScreenWidth()*.52f, client.getWindow().getScreenHeight()*.40f});
            context.getInput().setCursorPos(center[0], center[1]);
            context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
            try {
                for (int i=1;i<=10;i++) { context.getInput().setCursorPos(center[0]+i*10,center[1]+i*3); context.waitTicks(1); }
            } finally { context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT); }
            var after = context.computeOnClient(client -> ui.getActiveScene().getTransform().apply(new com.mojang.blaze3d.vertex.PoseStack(), 0).last().pose().toString());
            context.runOnClient(client -> require(authored[0] == ui.getActiveScene().getTransform().xRotation.getValue()
                && authored[1] == ui.getActiveScene().getTransform().yRotation.getValue(), "Mouse rotation must not overwrite the lesson's authored camera state"));
            context.getInput().setCursorPos(center[0],center[1]);
            context.waitTicks(2);
            context.runOnClient(client -> require(after.equals(ui.getActiveScene().getTransform().apply(new com.mojang.blaze3d.vertex.PoseStack(), 0).last().pose().toString()),
                "Released mouse movement must not keep rotating a paused scene"));
            context.takeScreenshot("ponder-ordinary-after-drag");
            Create.LOGGER.info("PONDER_ORDINARY_DRAG changed={} datafixErrors={}", !before.equals(after), errors);
            require(!before.equals(after), "An ordinary player's physical drag must rotate the visible Ponder scene");
            require(errors.isEmpty(), "Ponder resource loading must not log data-fixer errors: " + errors);
            require(controllerAtStartup, "Persistent cart attachments must register before the first world/entity load");
            var fps = new ArrayList<Integer>();
            for (int i=0;i<4;i++) { context.waitTicks(20); fps.add(context.computeOnClient(client -> client.getFps())); }
            Create.LOGGER.info("PONDER_STEADY_FPS samples={} cap=60 window=960x540", fps);
            verifyTemplateCache(context);
        } finally {
            if (world != null) world.close();
            logger.removeAppender(appender); appender.stop();
        }
    }
    private static void verifyFilledHotbar(ClientGameTestContext context) {
        context.runOnClient(client -> {
            try {
            var original = net.minecraft.nbt.NbtIo.read(client.gameDirectory.toPath().resolve("hotbar.nbt"));
            var sample = original == null ? new net.minecraft.nbt.CompoundTag() : original.copy();
            sample.putInt("DataVersion",3837);
            for (int i=0;i<9;i++) {
                if (sample.getListOrEmpty(Integer.toString(i)).size()==9) continue;
                var row = new net.minecraft.nbt.ListTag();
                for (int slot=0;slot<9;slot++) row.add(new net.minecraft.nbt.CompoundTag());
                sample.put(Integer.toString(i),row);
            }
            var item = new net.minecraft.nbt.CompoundTag();
            item.putString("id", "minecraft:diamond"); item.putInt("count", 3);
            var components = new net.minecraft.nbt.CompoundTag();
            components.putString("minecraft:custom_name", "{\"text\":\"Старый набор\"}");
            item.put("components", components);
            var slots = sample.getListOrEmpty("0"); slots.set(0,item);
            var directory = client.gameDirectory.toPath().resolve("hotbar-preservation-test");
            java.nio.file.Files.createDirectories(directory);
            net.minecraft.nbt.NbtIo.write(sample,directory.resolve("hotbar.nbt"));
            var manager = new net.minecraft.client.HotbarManager(directory,net.minecraft.util.datafix.DataFixers.getDataFixer());
            var stacks = manager.get(0).load(client.level.registryAccess());
            require(stacks.getFirst().is(net.minecraft.world.item.Items.DIAMOND) && stacks.getFirst().getCount()==3
                && stacks.getFirst().getHoverName().getString().equals("Старый набор"), "Legacy hotbar conversion must preserve item count and Unicode custom names");
            manager.save();
            var restored = new net.minecraft.client.HotbarManager(directory,net.minecraft.util.datafix.DataFixers.getDataFixer()).get(0).load(client.level.registryAccess());
            require(net.minecraft.world.item.ItemStack.matches(stacks.getFirst(),restored.getFirst()), "Native hotbar save/reload must retain the exact converted stack");
            Create.LOGGER.info("HOTBAR_LEGACY_PRESERVATION_PASS panels=9 slots=81 version=3837 itemCount=3");
            } catch (java.io.IOException failure) { throw new AssertionError("Cannot exercise native hotbar persistence",failure); }
        });
    }
    private static void verifyTemplateCache(ClientGameTestContext context) {
        var opens = new java.util.concurrent.atomic.AtomicInteger();
        var source = context.computeOnClient(client -> PonderIndex.getSceneAccess().getRegisteredEntries().iterator().next().getValue().getSchematicLocation());
        var resources = context.computeOnClient(client -> (net.minecraft.server.packs.resources.ResourceManager) java.lang.reflect.Proxy.newProxyInstance(
            net.minecraft.server.packs.resources.ResourceManager.class.getClassLoader(),new Class[]{net.minecraft.server.packs.resources.ResourceManager.class},
            (proxy,method,args) -> {
                if (method.getName().equals("getResource")) opens.incrementAndGet();
                try { return method.invoke(client.getResourceManager(),args); }
                catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
            }));
        context.runOnClient(client -> {
            var first = PonderSceneRegistry.loadSchematic(resources,source);
            var second = PonderSceneRegistry.loadSchematic(resources,source);
            require(first != second && opens.get()==1,"Reopening must reuse decoded resource data and return independent mutable templates");
        });
        var reload = context.computeOnClient(client -> client.reloadResourcePacks());
        context.waitFor(client -> reload.isDone(),1200); reload.join();
        context.runOnClient(client -> {
            PonderSceneRegistry.loadSchematic(resources,source);
            require(opens.get()==2,"Actual resource reload must invalidate cached Ponder template data");
            Create.LOGGER.info("PONDER_TEMPLATE_CACHE_PASS firstOpen=1 repeatOpen=0 afterReloadOpen=1");
        });
    }
    private static void verifyNativeCartRoundTrip(ClientGameTestContext context) {
        var future = context.computeOnClient(client -> {
            var server = client.getSingleplayerServer();
            return java.util.concurrent.CompletableFuture.runAsync(() -> {
                var level = server.overworld();
                var original = net.minecraft.world.entity.EntityTypes.CHEST_MINECART.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
                original.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,3));
                com.simibubi.create.content.contraptions.minecart.capability.MinecartController.get(original);
                var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess());
                original.saveWithoutId(output);
                var saved = output.buildResult();
                require(saved.getCompoundOrEmpty("fabric:attachments").contains("create:minecart_controller"), "Native cart save must contain its actual persistent controller");
                var restored = net.minecraft.world.entity.EntityTypes.CHEST_MINECART.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
                restored.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),saved));
                require(restored.getItem(0).getCount()==3 && restored.getItem(0).is(net.minecraft.world.item.Items.DIAMOND)
                    && com.simibubi.create.content.contraptions.minecart.capability.MinecartController.get(restored).cart()==restored,
                    "Native cart reload must preserve exact cargo and controller ownership");
                require(level.addFreshEntity(restored), "Restored native cart must be admitted to the actual world");
                restored.discard();
                Create.LOGGER.info("NATIVE_CART_SAVE_RELOAD_PASS cargo=3");
            },server);
        });
        context.waitFor(client -> future.isDone(),200); future.join();
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
