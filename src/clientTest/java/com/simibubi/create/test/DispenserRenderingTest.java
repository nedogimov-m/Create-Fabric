package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Native Vulkan moving dispenser/dropper, real effects, inventory input and saved ownership. */
final class DispenserRenderingTest {
    private static final BlockPos P = new BlockPos(6319, 10, 5);
    private static final BlockPos DISPENSER = BlockPos.ZERO.east(), BARREL = BlockPos.ZERO.east(2),
        DROPPER = BlockPos.ZERO.east(3);
    private static final class Sounds implements SoundEventListener {
        private final Map<Identifier, Integer> counts = new HashMap<>();
        @Override public void onPlaySound(SoundInstance sound, WeighedSoundEvents event, float range) {
            counts.merge(sound.getIdentifier(), 1, Integer::sum);
        }
        int launches() { return counts.getOrDefault(SoundEvents.DISPENSER_LAUNCH.location(), 0); }
        int drops() { return counts.getOrDefault(SoundEvents.DISPENSER_DISPENSE.location(), 0); }
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var sounds = new Sounds();
        context.runOnClient(client -> client.getSoundManager().addListener(sounds));
        try { scene(context, world, sounds); }
        finally { context.runOnClient(client -> client.getSoundManager().removeListener(sounds)); }
    }

    private static void scene(ClientGameTestContext context, TestSingleplayerContext world, Sounds sounds) {
        var server = world.getServer();
        server.runCommand("gamemode spectator @p"); server.runCommand("tp @p 6320.5 12 13.5");
        context.waitFor(client -> Math.abs(client.player.getX() - 6320.5) < .1 && client.level.isLoaded(P.east(3)), 400);
        context.waitFor(client -> NativeScreenshotReadiness.skyReady(client, new BlockPos(6316, 4, 7), new BlockPos(6324, 4, 7)), 300);
        server.runCommand("fill 6308 3 -4 6330 3 18 minecraft:smooth_quartz");
        block(world, P, "minecraft:stone");
        block(world, P.east(), "minecraft:dispenser[facing=south]");
        block(world, P.east(2), "minecraft:barrel[facing=south]");
        block(world, P.east(3), "minecraft:dropper[facing=south]");
        var id = new AtomicReference<UUID>(); var projectileId = new AtomicReference<UUID>();
        var dropId = new AtomicReference<UUID>();
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel();
            ((DispenserBlockEntity) level.getBlockEntity(P.east())).setItem(0, named(Items.ARROW, 1));
            ((BarrelBlockEntity) level.getBlockEntity(P.east(2))).setItem(0, named(Items.ARROW, 9));
            ((DispenserBlockEntity) level.getBlockEntity(P.east(3))).setItem(4, named(Items.DIAMOND, 2));
            level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(P, P.east(3))));
        });
        waitServer(context, world, level -> {
            if (!SuperGlueEntity.isGlued(level, P, Direction.EAST, null)) return false;
            try { LoadedEntityAccess.requireGlueQueriesReady(level, P, new HashSet<>()); return true; }
            catch (AssemblyException pending) { return false; }
        }, 300, "Dispenser fixture native entity data did not become ready");
        NativeContraptionFixtures.awaitCaptureReady(context,world,300,P,P.east(3));
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var structure = new BearingContraption(false, Direction.UP);
            try {
                if (!structure.assemble(level, P.below()) || structure.getBlocks().size() != 4
                    || !structure.tryRemoveBlocksFromWorld(level, BlockPos.ZERO))
                    throw new AssertionError("Dispenser carrier must capture all four blocks");
            } catch (AssemblyException failure) { throw new AssertionError(failure); }
            var owner = new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION, level);
            owner.setContraption(structure); owner.setPos(Vec3.atLowerCornerOf(P));
            structure.setActorsActive(ItemStack.EMPTY, false); level.addFreshEntity(owner); owner.tickActors();
            id.set(owner.getUUID());
        });
        context.waitFor(client -> find(client.level, id.get()) instanceof ControlledContraptionEntity owner
            && owner.getContraption() != null && owner.getContraption().getBlocks().size() == 4, 300);
        look(context, Vec3.atCenterOf(P.east(2)));
        var beforeLaunch = context.computeOnClient(client -> sounds.launches());
        var beforeDrop = context.computeOnClient(client -> sounds.drops());
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var owner = owner(level, id.get());
            owner.setAngle(45); owner.tickActors();
            if (!level.getEntitiesOfClass(Projectile.class, new AABB(P).inflate(10)).isEmpty())
                throw new AssertionError("Disabled moving dispenser fired");
            owner.getContraption().setActorsActive(ItemStack.EMPTY, true);
            owner.setAngle(30); owner.tickActors();
            owner.getContraption().setActorsActive(ItemStack.EMPTY, false);
            var storage = owner.getContraption().getStorage().getAllItemStorages();
            if (storage.get(DISPENSER).getStackInSlot(0).getCount() != 9
                || storage.get(DROPPER).getStackInSlot(4).getCount() != 1
                || !storage.get(BARREL).getStackInSlot(0).isEmpty())
                throw new AssertionError("Source top-off or sample ownership incorrect");
            var projectiles = level.getEntitiesOfClass(Projectile.class, new AABB(P).inflate(10));
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(P).inflate(10));
            if (projectiles.size() != 1 || projectiles.getFirst().getType() != EntityTypes.ARROW
                || drops.size() != 1 || !drops.getFirst().getItem().is(Items.DIAMOND)
                || !drops.getFirst().getItem().has(DataComponents.CUSTOM_NAME))
                throw new AssertionError("Exactly one native arrow and named dropped item required");
            projectileId.set(projectiles.getFirst().getUUID()); dropId.set(drops.getFirst().getUUID());
            for (var entity : new Entity[]{projectiles.getFirst(), drops.getFirst()}) {
                entity.setNoGravity(true); entity.setDeltaMovement(Vec3.ZERO);
            }
        });
        context.waitFor(client -> find(client.level, projectileId.get()) != null && find(client.level, dropId.get()) != null
            && sounds.launches() == beforeLaunch + 1 && sounds.drops() == beforeDrop + 1
            && find(client.level,id.get()) instanceof ControlledContraptionEntity owner
            && Math.abs(owner.getAngle(1)-30)<.01, 300);
        context.runOnClient(client -> {
            var owner = (ControlledContraptionEntity) find(client.level, id.get());
            var state = ((ContraptionEntityRenderer) client.getEntityRenderDispatcher().getRenderer(owner))
                .createRenderState(owner, .5f);
            if (state.blocks.size() != 4 || Math.abs(owner.getAngle(1) - 30) > .01)
                throw new AssertionError("Native moving dispenser/dropper extraction: blocks="+state.blocks.size()
                    +", angle="+owner.getAngle(1)+", captured="+owner.getContraption().getBlocks().size());
        });
        context.waitTicks(8); screenshot(context, world, "create-vulkan-mounted-dispenser-dropper-one-shot-effects");

        server.runCommand("tp @p 7100 14 7100");
        waitServer(context, world, level -> level.getEntity(id.get()) == null
            && level.getChunkSource().getChunkNow(P.getX() >> 4, 0) == null
            && level.getChunkSource().getChunkNow(P.east(3).getX() >> 4, 0) == null, 800,
            "Dispenser owner and both source chunks failed to unload");
        server.runCommand("tp @p 6320.5 12 13.5");
        waitServer(context, world, level -> level.getEntity(id.get()) instanceof ControlledContraptionEntity
            && level.getEntity(projectileId.get()) != null && level.getEntity(dropId.get()) != null, 500,
            "Saved dispenser owner/effects failed to return with their original UUIDs");
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var owner = owner(level, id.get());
            var storages = owner.getContraption().getStorage().getAllItemStorages();
            if (Math.abs(owner.getAngle(1) - 30) > .01 || !owner.getContraption().getActorAt(DISPENSER).right.disabled
                || storages.get(DISPENSER).getStackInSlot(0).getCount() != 9
                || storages.get(DROPPER).getStackInSlot(4).getCount() != 1
                || !storages.get(DISPENSER).getStackInSlot(0).has(DataComponents.CUSTOM_NAME))
                throw new AssertionError("Real unload changed disabled state, angle or ammunition");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(P).inflate(10));
            if (drops.size() != 1 || drops.getFirst().getItem().getCount() != 1)
                throw new AssertionError("Saved effects duplicated or disappeared");
        });
        context.waitFor(client -> find(client.level, id.get()) instanceof ControlledContraptionEntity owner
            && owner.getContraption() != null, 300);
        var dispenserState = Blocks.DISPENSER.defaultBlockState();
        var oldModel = context.computeOnClient(client -> client.getModelManager().getBlockStateModelSet().get(dispenserState));
        var reload = context.computeOnClient(client -> client.reloadResourcePacks());
        context.waitFor(client -> reload.isDone(), 500); reload.join(); context.waitTicks(3);
        context.runOnClient(client -> {
            if (client.getModelManager().getBlockStateModelSet().get(dispenserState) == oldModel)
                throw new AssertionError("Dispenser retained a model from before resource reload");
        });
        screenshot(context, world, "create-vulkan-mounted-dispenser-two-chunk-save-return-reload");

        server.runOnServer(ignored -> { var owner = owner(world.getConnection().getServerLevel(), id.get());
            owner.setAngle(0); owner.tickActors(); });
        block(world, P.east().south(3).below(2), "minecraft:smooth_quartz");
        server.runCommand("tp @p 6320.5 9 8.5"); server.runCommand("gamemode survival @p");
        context.waitFor(client -> client.player.onGround() && Math.abs(client.player.getZ() - 8.5) < .1
            && Math.abs(((ControlledContraptionEntity) find(client.level, id.get())).getAngle(1)) < .01, 200);
        look(context, Vec3.atCenterOf(P.east())); context.waitTicks(5);
        waitServer(context, world, level -> {
            var owner = owner(level, id.get()); var player = level.getServer().getPlayerList().getPlayers().getFirst();
            var eye = player.getEyePosition(); var hit = owner.rayTrace(eye, eye.add(player.getViewVector(1).scale(5)), 1);
            return hit != null && hit.getBlockPos().equals(DISPENSER);
        }, 150, "Native player ray did not reach moving dispenser");
        context.getInput().pressKey(options -> options.keyUse);
        context.waitFor(client -> client.player.containerMenu instanceof DispenserMenu
            && client.player.containerMenu.getSlot(0).getItem().getCount() == 9
            && client.player.containerMenu.getSlot(0).getItem().has(DataComponents.CUSTOM_NAME), 200);
        screenshot(context, world, "create-vulkan-mounted-dispenser-real-nine-slot-menu");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null, 100);
        server.runOnServer(ignored -> {
            var level = world.getConnection().getServerLevel(); var owner = owner(level, id.get());
            var held = owner.getContraption().getStorage().getAllItemStorages().get(DISPENSER);
            if (!owner.disassemble()) throw new AssertionError("Saved dispenser failed disassembly");
            var dispenser = (DispenserBlockEntity) level.getBlockEntity(P.east());
            var dropper = (DispenserBlockEntity) level.getBlockEntity(P.east(3));
            if (dispenser.getItem(0).getCount() != 9 || !dispenser.getItem(0).has(DataComponents.CUSTOM_NAME)
                || dropper.getItem(4).getCount() != 1 || held.isAvailable()
                || !((BarrelBlockEntity) level.getBlockEntity(P.east(2))).getItem(0).isEmpty())
                throw new AssertionError("Disassembly changed exact ammunition/components or retained a second owner");
        });
        server.runCommand("gamemode spectator @p"); look(context, Vec3.atCenterOf(P.east(2)));
        screenshot(context, world, "create-vulkan-mounted-dispenser-dropper-exact-world-restoration");
    }

    private static ControlledContraptionEntity owner(ServerLevel level, UUID id) {
        return (ControlledContraptionEntity) level.getEntity(id);
    }
    private static Entity find(net.minecraft.client.multiplayer.ClientLevel level, UUID id) {
        if (level != null && id != null) for (var entity : level.entitiesForRendering())
            if (entity.getUUID().equals(id)) return entity;
        return null;
    }
    private static ItemStack named(Item item, int count) {
        var stack = new ItemStack(item, count); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Moving reserve"));
        return stack;
    }
    private static void waitServer(ClientGameTestContext context, TestSingleplayerContext world,
                                   Predicate<ServerLevel> predicate, int ticks, String message) {
        var ready = new AtomicBoolean();
        for (int tick = 0; tick < ticks && !ready.get(); tick++) {
            world.getServer().runOnServer(ignored -> ready.set(predicate.test(world.getConnection().getServerLevel())));
            if (!ready.get()) context.waitTicks(1);
        }
        if (!ready.get()) throw new AssertionError(message);
    }
    private static void block(TestSingleplayerContext world, BlockPos pos, String state) {
        world.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + state);
    }
    private static void screenshot(ClientGameTestContext context, TestSingleplayerContext world, String name) {
        NativeScreenshotReadiness.await(context,world);
        context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false)); context.takeScreenshot(name);
    }
    private static void look(ClientGameTestContext context, Vec3 target) {
        var angle = context.computeOnClient(client -> { var vector = target.subtract(client.player.getEyePosition());
            return new Vec2((float) Math.toDegrees(Math.atan2(-vector.x, vector.z)),
                (float) -Math.toDegrees(Math.atan2(vector.y, Math.hypot(vector.x, vector.z)))); });
        context.getInput().lookAt(angle.x, angle.y);
    }
}
