package com.simibubi.create.foundation.utility;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * Actual containing source-owner coordinates. These produce NEW native transport-entry identities;
 * they are never source entity UUIDs, paid-material receipts or proof that an owner was published.
 */
public final class NeoForgeMigrationScope {
    private final String path;
    private NeoForgeMigrationScope(String path) { this.path = path; }
    public static NeoForgeMigrationScope train(UUID actualSourceTrain) {
        return new NeoForgeMigrationScope("train/" + Objects.requireNonNull(actualSourceTrain));
    }
    public static NeoForgeMigrationScope train(UUID actualSourceTrain, int originalCarriageIndex) {
        Objects.requireNonNull(actualSourceTrain);
        if (originalCarriageIndex < 0) throw new IllegalArgumentException("Negative original carriage index");
        return new NeoForgeMigrationScope("train/" + actualSourceTrain + "/carriage/" + originalCarriageIndex);
    }
    public static NeoForgeMigrationScope logisticsNetwork(UUID actualSourceNetwork) {
        return new NeoForgeMigrationScope("logistics_network/" + Objects.requireNonNull(actualSourceNetwork));
    }
    public static NeoForgeMigrationScope entity(UUID actualSourceEntity) {
        return new NeoForgeMigrationScope("entity/" + Objects.requireNonNull(actualSourceEntity));
    }
    public static NeoForgeMigrationScope worldBlock(Identifier actualDimension, BlockPos actualAbsolutePos) {
        var dimension = Objects.requireNonNull(actualDimension).toString();
        return new NeoForgeMigrationScope("world/" + dimension.length() + ":" + dimension).block(actualAbsolutePos);
    }
    public NeoForgeMigrationScope block(BlockPos actualLocalPos) {
        Objects.requireNonNull(actualLocalPos);
        return new NeoForgeMigrationScope(path + "/block/" + actualLocalPos.getX() + "/" + actualLocalPos.getY() + "/" + actualLocalPos.getZ());
    }
    /** Actual typed original item location under this genuine containing source owner. */
    public NeoForgeMigrationScope item(String typedRole, int originalEntryIndex) {
        if (typedRole == null || !typedRole.matches("[a-z][a-z0-9_]*") || originalEntryIndex < 0)
            throw new IllegalArgumentException("A typed original item role and nonnegative entry order are required");
        return new NeoForgeMigrationScope(path + "/item/" + typedRole.length() + ":" + typedRole + "/" + originalEntryIndex);
    }
    /** NEW native value identity; never an original entity UUID or historical paid-material receipt. */
    public UUID nativeValueIdentity(String typedRole, int originalEntryIndex) {
        return nativeIdentity("value", typedRole, originalEntryIndex);
    }
    public UUID nativeTransportIdentity(String typedRole, int originalEntryIndex) {
        return nativeIdentity("transport", typedRole, originalEntryIndex);
    }
    /** NEW native pending-operation identity. Original launched icons do not prove paid materials. */
    public UUID nativeJobIdentity(String typedRole, int originalEntryIndex) {
        return nativeIdentity("job", typedRole, originalEntryIndex);
    }
    private UUID nativeIdentity(String kind, String typedRole, int originalEntryIndex) {
        if (typedRole == null || !typedRole.matches("[a-z][a-z0-9_]*") || originalEntryIndex < 0)
            throw new IllegalArgumentException("A typed original transport role and nonnegative entry order are required");
        return UUID.nameUUIDFromBytes(("create:neoforge_native_" + kind + "/v1/" + path + "/" + typedRole + "/" + originalEntryIndex)
            .getBytes(StandardCharsets.UTF_8));
    }
}
