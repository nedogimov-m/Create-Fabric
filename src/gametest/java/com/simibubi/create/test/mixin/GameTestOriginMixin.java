package com.simibubi.create.test.mixin;

import com.simibubi.create.Create;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Optional deterministic placement through the real native grid, with ordinary random placement by default. */
@Mixin(GameTestServer.class)
public abstract class GameTestOriginMixin {
    @Unique private static final BlockPos create$testOrigin = create$parseTestOrigin();

    @Unique
    private static BlockPos create$parseTestOrigin() {
        var property = System.getProperty("create.test.origin");
        if (property == null) return null;
        var coordinates = property.split(",", -1);
        if (coordinates.length != 3)
            throw new IllegalArgumentException("create.test.origin must contain exactly three integer coordinates: x,y,z");
        var values = new int[3];
        for (int index = 0; index < coordinates.length; index++) {
            var coordinate = coordinates[index].strip();
            if (!coordinate.matches("[+-]?[0-9]+"))
                throw new IllegalArgumentException("Invalid integer coordinate in create.test.origin: " + property);
            try {
                values[index] = Integer.parseInt(coordinate);
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Coordinate outside integer range in create.test.origin: " + property, invalid);
            }
        }
        if (Math.abs((long) values[0]) > 29_999_984 || Math.abs((long) values[2]) > 29_999_984
                || values[1] < -64 || values[1] >= 320)
            throw new IllegalArgumentException("create.test.origin must be inside native overworld block bounds: " + property);
        return new BlockPos(values[0], values[1], values[2]);
    }

    @ModifyArg(method = "startTests", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/LevelData$RespawnData;of(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/BlockPos;FF)Lnet/minecraft/world/level/storage/LevelData$RespawnData;"), index = 1)
    private BlockPos create$overrideNativeSpawnOrigin(BlockPos randomOrigin) {
        return create$testOrigin == null ? randomOrigin : create$testOrigin;
    }

    @ModifyArg(method = "startTests", at = @At(value = "INVOKE", target = "Lnet/minecraft/gametest/framework/StructureGridSpawner;<init>(Ljava/util/function/Function;IZ)V"), index = 0)
    private Function<ResourceKey<Level>, BlockPos> create$overrideNativeGridOrigin(Function<ResourceKey<Level>, BlockPos> randomOrigins) {
        if (create$testOrigin == null) return randomOrigins;
        Create.LOGGER.info("GameTest origin override active: native grid and spawn use {} (create.test.origin)", create$testOrigin.toShortString());
        return dimension -> create$testOrigin;
    }

    @ModifyArg(method = "startTests", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"), index = 2)
    private Object create$reportActualNativeGridOrigin(Object randomOriginText) {
        return create$testOrigin == null ? randomOriginText : create$testOrigin.toShortString();
    }
}
