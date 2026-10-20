package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.factoryBoard.*;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.createmod.ponder.api.level.PonderLevel;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** Actual virtual connection, sole parcel ownership, independent clock, transfer and replay. UNRUN. */
public final class PonderVirtualConveyorOwnershipTest implements FabricClientGameTest {
    private static final BlockPos FIRST = new BlockPos(2, 4, 2);
    private static final BlockPos SECOND = FIRST.east(6);

    @Override public void runTest(ClientGameTestContext context) {
        try (var joined = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var host = client.level;
                require(host != null, "Actual native client world required");
                var hostFirst = host.getBlockState(FIRST);
                var hostSecond = host.getBlockState(SECOND);
                var hostFirstOwner = host.getBlockEntity(FIRST);
                var hostSecondOwner = host.getBlockEntity(SECOND);
                var scene = new PonderLevel(BlockPos.ZERO, host);
                scene.setBlock(FIRST, AllBlocks.CHAIN_CONVEYOR.defaultBlockState(), 0);
                scene.setBlock(SECOND, AllBlocks.CHAIN_CONVEYOR.defaultBlockState(), 0);
                var first = (ChainConveyorBlockEntity) scene.getBlockEntity(FIRST);
                var second = (ChainConveyorBlockEntity) scene.getBlockEntity(SECOND);
                var offset = SECOND.subtract(FIRST);
                require(first != null && second != null && first.isVirtual() && second.isVirtual()
                    && first.setVirtualConnection(offset, true) && first.isConnectedTo(second),
                    "Actual virtual endpoints own one symmetric canonical connection identity");
                first.setSpeed(128); second.setSpeed(128);
                var parcel = new ChainConveyorPackage(45, PackageStyles.getDefaultBox());
                require(first.addLoopingPackage(parcel), "One canonical local looping owner accepts the actual package");
                require(!first.moveLoopingPackageToConnection(parcel, offset.above(1))
                    && first.getLoopingPackages().size() == 1 && first.getLoopingPackages().getFirst() == parcel
                    && parcel.chainPosition == 45,
                    "A refused native transfer retains the same parcel, position and sole owner");
                require(first.moveLoopingPackageToConnection(parcel, offset)
                    && first.getLoopingPackages().isEmpty() && first.packageCount() == 1
                    && first.getTravellingPackages().get(offset).getFirst() == parcel,
                    "The source departure moves the original parcel between canonical lists without a copy");
                long hostTime = host.getGameTime();
                tick(scene, first, second);
                float previous = parcel.chainPosition;
                tick(scene, first, second);
                require(host.getGameTime() == hostTime && scene.virtualGameTime() == 2
                    && parcel.chainPosition > previous,
                    "Actual virtual transport uses scene time even while the native host time is fixed");
                scene.createBackup();
                scene.restore();
                var restoredFirst = (ChainConveyorBlockEntity) scene.getBlockEntity(FIRST);
                var restoredSecond = (ChainConveyorBlockEntity) scene.getBlockEntity(SECOND);
                require(restoredFirst != first && first.isRemoved() && scene.virtualGameTime() == 0
                    && !first.setVirtualConnection(offset, false)
                    && restoredFirst.isConnectedTo(restoredSecond),
                    "Replay replaces owners and time; stale connection callbacks cannot affect fresh state");
                for (int i = 0; i < 64 && restoredSecond.getLoopingPackages().isEmpty(); i++)
                    tick(scene, restoredFirst, restoredSecond);
                require(restoredFirst.packageCount() == 0 && restoredSecond.packageCount() == 1
                    && restoredSecond.getLoopingPackages().size() == 1,
                    "The genuine native travel path hands exactly one parcel to the actual local destination");
                require(host.getBlockState(FIRST) == hostFirst && host.getBlockState(SECOND) == hostSecond
                    && host.getBlockEntity(FIRST) == hostFirstOwner && host.getBlockEntity(SECOND) == hostSecondOwner,
                    "Connection, failed transfer, source clock, handoff and replay preserve host blocks and owners");
                actualFactoryTemplateReferences(host);
            });
        }
    }

    private record ReferenceKey(BlockPos owner, String slot, String field) { }
    private record ConnectionValue(FactoryPanelPosition from, int amount, int arrowBending) {
        ConnectionValue(FactoryPanelConnection connection) { this(connection.from, connection.amount, connection.arrowBendMode); }
    }
    private record FactoryReferences(Map<BlockPos, BlockEntity> owners,
        Map<ReferenceKey, List<FactoryPanelPosition>> positions,
        Map<ReferenceKey, List<ConnectionValue>> connections, List<Integer> counts) { }

    private static FactoryReferences factoryReferences(PonderLevel scene) {
        var owners = new HashMap<BlockPos, BlockEntity>();
        var positions = new HashMap<ReferenceKey, List<FactoryPanelPosition>>();
        var connections = new HashMap<ReferenceKey, List<ConnectionValue>>();
        int targeting = 0, targetedBy = 0, targetedByLinks = 0, linkedGauges = 0;
        var ops = scene.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (var owner : scene.getBlockEntities()) {
            var data = owner.saveWithFullMetadata(scene.registryAccess());
            if (owner instanceof FactoryPanelBlockEntity panel) {
                require(panel.isVirtual() && panel.getLevel() == scene, "Actual template factory panel belongs to its virtual world");
                owners.put(owner.getBlockPos(), owner);
                for (var slot : PanelSlot.values()) {
                    var behaviour = panel.panels.get(slot);
                    if (!behaviour.active) continue;
                    var saved = data.getCompound(slot.getSerializedName()).orElseThrow();
                    var targets = FactoryPanelPosition.CODEC.listOf().parse(ops, saved.get("Targeting")).getOrThrow();
                    require(targets.equals(List.copyOf(behaviour.targeting)), "Native saved Targeting is the actual panel's typed reference list");
                    positions.put(new ReferenceKey(owner.getBlockPos(), slot.getSerializedName(), "Targeting"), targets);
                    targeting += targets.size();
                    for (var field : List.of("TargetedBy", "TargetedByLinks")) {
                        var decoded = FactoryPanelConnection.CODEC.listOf().parse(ops, saved.get(field)).getOrThrow();
                        var values = decoded.stream().map(ConnectionValue::new).toList();
                        var actual = field.equals("TargetedBy") ? behaviour.targetedBy.values() : behaviour.targetedByLinks.values();
                        require(values.equals(actual.stream().map(ConnectionValue::new).toList()), "Native saved " + field + " retains real typed position/slot/amount/bend values");
                        connections.put(new ReferenceKey(owner.getBlockPos(), slot.getSerializedName(), field), values);
                        if (field.equals("TargetedBy")) targetedBy += values.size(); else targetedByLinks += values.size();
                    }
                }
            }
            var support = BlockEntityBehaviour.get(owner, FactoryPanelSupportBehaviour.TYPE);
            if (support != null) {
                owners.put(owner.getBlockPos(), owner);
                var linked = FactoryPanelPosition.CODEC.listOf().parse(ops, data.get("LinkedGauges")).getOrThrow();
                require(linked.equals(support.getLinkedPanels()), "Native saved LinkedGauges retains actual support references");
                positions.put(new ReferenceKey(owner.getBlockPos(), "root", "LinkedGauges"), linked);
                linkedGauges += linked.size();
            }
        }
        return new FactoryReferences(Map.copyOf(owners), Map.copyOf(positions), Map.copyOf(connections),
            List.of(targeting, targetedBy, targetedByLinks, linkedGauges));
    }

    private static void actualFactoryTemplateReferences(net.minecraft.client.multiplayer.ClientLevel host) {
        var templates = List.of("factory_gauge_recipe", "factory_gauge_crafting", "factory_gauge_links");
        var expected = List.of(List.of(13, 13, 0, 0), List.of(2, 2, 0, 0), List.of(0, 0, 2, 2));
        int[] totals = new int[4];
        for (int i = 0; i < templates.size(); i++) {
            var scene = new PonderLevel(BlockPos.ZERO, host);
            var template = PonderSceneRegistry.loadSchematic(Identifier.parse("create:high_logistics/" + templates.get(i)));
            require(scene.place(template, BlockPos.ZERO, new StructurePlaceSettings()), "Real pinned factory template places through the native Ponder registry boundary");
            var saved = factoryReferences(scene);
            require(saved.counts.equals(expected.get(i)), "Actual SAVED native references match pinned template census: " + templates.get(i) + " " + saved.counts);
            for (int field = 0; field < 4; field++) totals[field] += saved.counts.get(field);
            if (i == 0)
                require(saved.connections.get(new ReferenceKey(new BlockPos(2, 2, 3), "top_left", "TargetedBy")).contains(
                    new ConnectionValue(new FactoryPanelPosition(new BlockPos(50, -40, 25), PanelSlot.BOTTOM_RIGHT), 1, -1)),
                    "Pinned recipe reference retains absolute negative Y, exact slot, amount and automatic-bend sentinel -1");
            if (i == 1)
                require(saved.positions.get(new ReferenceKey(new BlockPos(2, 2, 4), "top_left", "Targeting")).equals(
                    List.of(new FactoryPanelPosition(new BlockPos(54, -41, 54), PanelSlot.BOTTOM_LEFT))),
                    "Pinned crafting Targeting retains the actual absolute position and slot");
            if (i == 2) {
                require(saved.connections.get(new ReferenceKey(new BlockPos(2, 3, 1), "top_left", "TargetedByLinks")).contains(
                    new ConnectionValue(new FactoryPanelPosition(new BlockPos(63, -42, 36), PanelSlot.TOP_RIGHT), 1, 3)),
                    "Pinned support connection retains its actual slot and arrow bend");
                for (var owner : List.of(new BlockPos(1, 2, 1), new BlockPos(3, 2, 1)))
                    require(saved.positions.get(new ReferenceKey(owner, "root", "LinkedGauges")).equals(
                        List.of(new FactoryPanelPosition(new BlockPos(62, -41, 36), PanelSlot.TOP_LEFT))),
                        "Both actual display-link and redstone-link owners preserve their native LinkedGauges reference");
            }
            scene.createBackup(); scene.restore();
            var restored = factoryReferences(scene);
            require(restored.counts.equals(saved.counts) && restored.positions.equals(saved.positions)
                && restored.connections.equals(saved.connections) && restored.owners.keySet().equals(saved.owners.keySet()),
                "Real native backup/restore preserves every saved factory reference and typed value");
            saved.owners.forEach((pos, old) -> require(old.isRemoved() && restored.owners.get(pos) != old
                && restored.owners.get(pos).getLevel() == scene, "Factory replay retires old owners and binds the same references to fresh local owners"));
        }
        require(java.util.Arrays.equals(totals, new int[] {15, 15, 2, 2}), "Three real pinned templates retain all 34 original native saved factory references");
    }

    private static void tick(PonderLevel scene, ChainConveyorBlockEntity first, ChainConveyorBlockEntity second) {
        scene.tick();
        ChainConveyorBlockEntity.tick(scene, FIRST, first.getBlockState(), first);
        ChainConveyorBlockEntity.tick(scene, SECOND, second.getBlockState(), second);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
