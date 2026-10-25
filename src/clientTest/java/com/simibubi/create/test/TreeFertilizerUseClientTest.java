package com.simibubi.create.test;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Native right-click planting and fertilizing with finite original sapling/fertilizer stock. */
public final class TreeFertilizerUseClientTest implements FabricClientGameTest {
    private record Result(String plant, String plantedState, int logs, int roots, int leaves,
                          int fertilizerLeft, int returnedSaplings, String finalState) {}
    @Override public void runTest(ClientGameTestContext context) {
        var results = new ArrayList<Result>();
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            int index = 0;
            for (var plant : List.of(Blocks.OAK_SAPLING, Blocks.MANGROVE_PROPAGULE, Blocks.MANGROVE_PROPAGULE,
                    Blocks.MANGROVE_PROPAGULE, Blocks.BIRCH_SAPLING, Blocks.CHERRY_SAPLING)) {
                var pos = new BlockPos(64 + index++ * 32, 5, 64);
                var min = pos.offset(-12, -1, -12); var max = pos.offset(12, -1, 12);
                server.runCommand("gamemode spectator @p");
                server.runCommand("tp @p " + (pos.getX()+.5) + " 12 " + (pos.getZ()+3.5));
                context.waitFor(client -> client.level.isLoaded(min) && client.level.isLoaded(max), 400);
                NativeScreenshotReadiness.beforeFloor(context, world, min, max, pos, pos.south(3));
                server.runCommand("fill " + min.getX() + " 5 " + min.getZ() + " " + max.getX() + " 45 " + max.getZ() + " minecraft:air");
                server.runCommand("fill " + min.getX() + " 4 " + min.getZ() + " " + max.getX() + " 4 " + max.getZ() + " minecraft:podzol");
                server.runCommand("clear @p");
                server.runOnServer(ignored -> {
                    var player = world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();
                    player.getInventory().setItem(0, new ItemStack(plant.asItem()));
                    player.containerMenu.broadcastChanges();
                });
                server.runCommand("gamemode survival @p");
                server.runCommand("tp @p " + (pos.getX()+.5) + " 5 " + (pos.getZ()+3.5));
                context.getInput().pressKey(InputConstants.KEY_2); context.getInput().pressKey(InputConstants.KEY_1);
                context.waitFor(client -> client.player.onGround() && client.player.getMainHandItem().is(plant.asItem()), 250);
                look(context, Vec3.atBottomCenterOf(pos).add(0,-.01,0));
                context.waitFor(client -> client.hitResult instanceof BlockHitResult hit
                    && hit.getBlockPos().equals(pos.below()) && hit.getDirection() == Direction.UP, 200);
                context.getInput().pressKey(options -> options.keyUse);
                context.waitFor(client -> client.level.getBlockState(pos).is(plant)
                    && client.player.getMainHandItem().isEmpty(), 200);
                var acknowledged = new java.util.concurrent.atomic.AtomicBoolean();
                for (int tick=0;tick<200&&!acknowledged.get();tick++) {
                    context.waitTicks(1);
                    server.runOnServer(ignored -> {
                        var level=world.getConnection().getServerLevel();
                        var player=level.getServer().getPlayerList().getPlayers().getFirst();
                        acknowledged.set(level.getBlockState(pos).is(plant)&&player.getMainHandItem().isEmpty());
                    });
                }
                if (!acknowledged.get()) throw new AssertionError("Server did not acknowledge native finite-stock planting: "+plant);
                var planted = context.computeOnClient(client -> client.level.getBlockState(pos).toString());
                server.runOnServer(ignored -> {
                    var player = world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();
                    player.getInventory().setItem(0, new ItemStack(AllItems.TREE_FERTILIZER,3));
                    player.containerMenu.broadcastChanges();
                });
                context.waitFor(client -> client.player.getMainHandItem().is(AllItems.TREE_FERTILIZER)
                    && client.player.getMainHandItem().getCount() == 3, 200);
                var target = context.computeOnClient(client -> client.level.getBlockState(pos)
                    .getShape(client.level,pos).bounds().move(pos).getCenter());
                look(context, target);
                context.waitFor(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos), 200);
                context.getInput().pressKey(options -> options.keyUse);
                context.waitFor(client -> client.player.getMainHandItem().getCount() == 2, 200);
                context.waitTicks(3);
                var result = new AtomicReference<Result>();
                server.runOnServer(ignored -> {
                    var level = world.getConnection().getServerLevel();
                    var player = level.getServer().getPlayerList().getPlayers().getFirst();
                    int logs=0,roots=0,leaves=0;
                    for (var cell : BlockPos.betweenClosed(pos.offset(-10,-3,-10),pos.offset(10,64,10))) {
                        var state=level.getBlockState(cell);
                        if (state.is(net.minecraft.tags.BlockTags.LOGS)) logs++;
                        if (state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MUDDY_MANGROVE_ROOTS)) roots++;
                        if (state.is(net.minecraft.tags.BlockTags.LEAVES)) leaves++;
                    }
                    int saplings=player.getInventory().countItem(plant.asItem());
                    saplings += level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(12),
                        entity->entity.getItem().is(plant.asItem())).stream().mapToInt(entity->entity.getItem().getCount()).sum();
                    result.set(new Result(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(plant).toString(),
                        planted,logs,roots,leaves,player.getMainHandItem().getCount(),saplings,level.getBlockState(pos).toString()));
                });
                results.add(result.get());
                System.out.println("TREE_FERTILIZER_PHYSICAL_USE " + result.get());
                context.takeScreenshot("tree-fertilizer-physical-" + index);
            }
        } finally {
            try {
                var file=Path.of(System.getProperty("create.test.fertilizerReport","build/reports/fertilizer-physical-use.json"));
                Files.createDirectories(file.toAbsolutePath().getParent());
                Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(results)+"\n");
            } catch(Exception failure) {throw new AssertionError("Cannot persist actual fertilizer use",failure);}
        }
        var failed=results.stream().filter(result->result.logs==0||result.returnedSaplings!=0).toList();
        if(!failed.isEmpty())throw new AssertionError("Physical fertilizer use failed: "+failed);
    }
    private static void look(ClientGameTestContext context, Vec3 target) {
        var angle=context.computeOnClient(client->{
            var delta=target.subtract(client.player.getEyePosition());
            return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),
                (float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        });
        context.getInput().lookAt(angle.x,angle.y);
    }
}
