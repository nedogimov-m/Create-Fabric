package com.simibubi.create.test;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Real InventoryScreen pages, observing the native item resolver used by those visible slots. */
public final class RegisteredItemModelClientTest implements FabricClientGameTest {
    private static final Map<Item, Row> ROWS = new IdentityHashMap<>();
    private static Set<Item> active = Set.of();
    private static final Field LAYERS = field(ItemStackRenderState.class, "layers");
    private static final Field COUNT = field(ItemStackRenderState.class, "activeLayerCount");
    private static final Class<?> LAYER = ItemStackRenderState.LayerRenderState.class;
    private static final Field QUADS = field(LAYER, "quads"), SPECIAL = field(LAYER, "specialRenderer"),
        ARGUMENT = field(LAYER, "argumentForSpecialRendering");

    private static final class Row {
        String id, itemClass, blockClass;
        List<String> customUseHooks;
        boolean consumable, equippable, fuel, nonempty;
        int guiObservations, faces;
        final Set<String> sprites = new TreeSet<>(), specialRenderers = new TreeSet<>(), issues = new TreeSet<>();
        final Set<String> atlasBoundarySamples = new TreeSet<>();
    }

    public static void observe(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context) {
        if (context != ItemDisplayContext.GUI || !active.contains(stack.getItem())) return;
        var row = ROWS.get(stack.getItem());
        row.guiObservations++;
        row.nonempty |= !state.isEmpty();
        try {
            var layers = (Object[]) LAYERS.get(state);
            for (int index = 0; index < COUNT.getInt(state); index++) {
                var layer = layers[index];
                scan(QUADS.get(layer), row, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
                var mesh=((net.fabricmc.fabric.impl.client.renderer.LayerRenderStateExtension)layer).fabric_getMutableMesh();
                if(mesh!=null)mesh.forEach(quad->{
                    row.faces++;
                    var atlas=net.minecraft.client.Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(quad.atlas().getId());
                    var sprite=((net.fabricmc.fabric.api.client.renderer.v1.sprite.FabricTextureAtlas)atlas).spriteFinder().find(quad);
                    if(sprite==null||sprite.contents().name().getPath().equals("missingno")){
                        var missing=atlas.getSprite(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation());
                        boolean samplesMissing=true;
                        for(int vertex=0;vertex<4;vertex++)samplesMissing&=quad.u(vertex)>=missing.getU0()&&quad.u(vertex)<=missing.getU1()&&quad.v(vertex)>=missing.getV0()&&quad.v(vertex)<=missing.getV1();
                        // SpriteFinder returns its missing sprite for unassigned atlas boundary cells too.
                        // The GPU samples the actual UVs; a lookup fallback outside missingno is not a missing material.
                        String sample="atlas="+quad.atlas().getId()+", uv="+quad.u(0)+","+quad.v(0)+", missingno="+missing.getU0()+","+missing.getV0()+".."+missing.getU1()+","+missing.getV1();
                        if(samplesMissing)row.issues.add("Missing mesh texture: "+sample);else row.atlasBoundarySamples.add(sample);
                    }else row.sprites.add(sprite.contents().name().toString());
                    for(int vertex=0;vertex<4;vertex++)if(!Float.isFinite(quad.x(vertex)+quad.y(vertex)+quad.z(vertex)+quad.u(vertex)+quad.v(vertex)))row.issues.add("Nonfinite mesh face/UV");
                });
                var renderer = SPECIAL.get(layer);
                if (renderer != null) {
                    row.specialRenderers.add(renderer.getClass().getName());
                    scan(ARGUMENT.get(layer), row, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
                }
            }
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot inspect native item layers", failure); }
    }

    private static void scan(Object value, Row row, Set<Object> seen, int depth) throws ReflectiveOperationException {
        if (value == null || depth > 8 || !seen.add(value)) return;
        if (value instanceof ItemQuads quads) {
            for (var quad : quads.all()) scan(quad, row, seen, depth + 1);
        } else if (value instanceof BakedQuad quad) {
            row.faces++;
            String sprite = quad.materialInfo().sprite().contents().name().toString();
            row.sprites.add(sprite);
            if (sprite.endsWith(":missingno")) row.issues.add("Missing face texture");
            for (int vertex = 0; vertex < 4; vertex++) {
                var point = quad.position(vertex);
                if (!Float.isFinite(point.x() + point.y() + point.z())) row.issues.add("Nonfinite face position");
            }
        } else if (value instanceof Collection<?> values) {
            for (var child : values) scan(child, row, seen, depth + 1);
        } else if (value instanceof org.joml.Matrix4fc transform) {
            if(!transform.isFinite())row.issues.add("Nonfinite item transform");
        } else if (value instanceof com.simibubi.create.content.equipment.EquipmentItemRenderer.State equipment) {
            scan(equipment.draws, row, seen, depth + 1);
        } else if (value.getClass().isRecord() && value.getClass().getName().startsWith("com.simibubi.create.")) {
            for (var component : value.getClass().getRecordComponents()) {
                var getter = component.getAccessor(); getter.setAccessible(true);
                scan(getter.invoke(value), row, seen, depth + 1);
            }
        }
    }

    @Override public void runTest(ClientGameTestContext context) {
        ROWS.clear(); active = Set.of();
        String inspect=System.getProperty("create.test.inspectItem","");
        try (var world = context.worldBuilder().create()) {
            var items = context.computeOnClient(client -> BuiltInRegistries.ITEM.entrySet().stream()
                .filter(entry -> entry.getKey().identifier().getNamespace().equals("create"))
                .filter(entry -> inspect.isBlank()||entry.getKey().identifier().toString().equals(inspect))
                .sorted(Comparator.comparing(entry -> entry.getKey().identifier().toString()))
                .map(Map.Entry::getValue).toList());
            context.runOnClient(client -> {
                for (var item : items) {
                    var row = new Row(); row.id = BuiltInRegistries.ITEM.getKey(item).toString();
                    row.itemClass = item.getClass().getName();
                    if (item instanceof BlockItem block) row.blockClass = block.getBlock().getClass().getName();
                    var hooks = Set.of("use", "useOn", "interactLivingEntity", "inventoryTick", "releaseUsing",
                        "finishUsingItem", "hurtEnemy", "canAttackBlock", "onUseTick");
                    row.customUseHooks = Arrays.stream(item.getClass().getMethods())
                        .filter(method -> hooks.contains(method.getName()) && method.getDeclaringClass() != Item.class)
                        .map(method -> method.getDeclaringClass().getSimpleName() + "." + method.getName()).distinct().sorted().toList();
                    var stack = new ItemStack(item);
                    row.consumable = stack.has(net.minecraft.core.component.DataComponents.CONSUMABLE);
                    row.equippable = stack.has(net.minecraft.core.component.DataComponents.EQUIPPABLE);
                    row.fuel = stack.has(net.minecraft.core.component.DataComponents.COOKING_FUEL);
                    ROWS.put(item, row);
                }
            });
            world.getServer().runCommand("gamemode survival @p");
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitFor(client -> client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen, 200);
            for (int start = 0; start < items.size(); start += 36) {
                var page = List.copyOf(items.subList(start, Math.min(start + 36, items.size())));
                context.runOnClient(client -> active = Set.copyOf(page));
                world.getServer().runOnServer(ignored -> {
                    var player = world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();
                    for (int slot = 0; slot < 36; slot++)
                        player.getInventory().setItem(slot, slot < page.size() ? new ItemStack(page.get(slot)) : ItemStack.EMPTY);
                    player.containerMenu.broadcastChanges();
                });
                context.waitFor(client -> {
                    for (int slot = 0; slot < page.size(); slot++)
                        if (!client.player.getInventory().getItem(slot).is(page.get(slot)) || ROWS.get(page.get(slot)).guiObservations == 0)
                            return false;
                    return true;
                }, 300);
                context.runOnClient(client -> client.gui.toastManager().clear());
                context.takeScreenshot("create-registry-items-page-" + (start / 36));
            }
            if(!inspect.isBlank()){
                var stack=new ItemStack(items.getFirst());
                context.runOnClient(client->client.gui.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal(inspect)){
                    @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){
                        gui.fill(0,0,width,height,0xFFB0B0B0);gui.pose().pushMatrix();gui.pose().translate(width/2f-80,height/2f-80);gui.pose().scale(10f);gui.item(stack,0,0);gui.pose().popMatrix();
                    }
                }));
                context.waitTicks(4);context.takeScreenshot("create-native-magnified-item-"+inspect.substring(inspect.indexOf(':')+1));
            }
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
            context.waitFor(client -> client.gui.screen() == null, 200);
        } finally {
            active = Set.of();
            try {
                var path = Path.of(System.getProperty("create.test.itemModelReport", "build/reports/registered-item-models.json"));
                Files.createDirectories(path.toAbsolutePath().getParent());
                var rows = ROWS.values().stream().sorted(Comparator.comparing(row -> row.id)).toList();
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(rows) + "\n");
            } catch (Exception failure) { throw new AssertionError("Cannot write actual item inventory", failure); }
        }
        var failures = ROWS.values().stream().filter(row -> !row.nonempty || row.faces==0 || !row.issues.isEmpty())
            .map(row -> row.id + ": " + (row.nonempty ? row.issues : "empty model")).toList();
        if (!failures.isEmpty()) throw new AssertionError("Visible registered item models failed: " + failures);
        System.out.println("CREATE_REGISTERED_ITEM_MODELS_PASS items=" + ROWS.size());
    }

    private static Field field(Class<?> type, String name) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
    }
}
