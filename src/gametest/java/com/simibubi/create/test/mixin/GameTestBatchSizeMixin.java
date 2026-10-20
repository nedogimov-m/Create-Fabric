package com.simibubi.create.test.mixin;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only limits the native partition width; the original selected registry collection is retained. */
@Mixin(value = GameTestBatchFactory.class, remap = false)
public abstract class GameTestBatchSizeMixin {
    @Unique private static int create$batchSize(int original) {
        String requested = System.getProperty("create.test.nativeBatchSize");
        if (requested == null) return original;
        int size = Integer.parseInt(requested);
        if (size < 1 || size > original) throw new IllegalArgumentException("Native test batch size must be between1 and" + original);
        return size;
    }
    @ModifyArg(method = "lambda$divideIntoBatches$1", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Lists;partition(Ljava/util/List;I)Ljava/util/List;"), index = 1, remap = false)
    private static int create$initialWidth(int original) { return create$batchSize(original); }

    @ModifyArg(method = "fromGameTestInfo()Lnet/minecraft/gametest/framework/GameTestRunner$GameTestBatcher;", at = @At(value = "INVOKE", target = "Lnet/minecraft/gametest/framework/GameTestBatchFactory;fromGameTestInfo(I)Lnet/minecraft/gametest/framework/GameTestRunner$GameTestBatcher;"), index = 0, remap = false)
    private static int create$rerunWidth(int original) { return create$batchSize(original); }

    @Inject(method = "divideIntoBatches", at = @At("RETURN"), remap = false)
    private static void create$record(Collection<?> original, GameTestBatchFactory.TestDecorator decorator, MinecraftServer server,
            CallbackInfoReturnable<List<GameTestBatch>> ci) {
        if (System.getProperty("create.test.nativeBatchSize") == null) return;
        int requested = create$batchSize(50);
        var originalIds = new ArrayList<String>();
        for (Object value : original) originalIds.add(((Holder<?>) value).unwrapKey().orElseThrow().identifier().toString());
        var constructedIds = new ArrayList<String>();
        var dimensions = new LinkedHashMap<String, Integer>();
        var sizes = new ArrayList<Integer>(); int maximum = 0;
        for (GameTestBatch batch : ci.getReturnValue()) {
            int size = batch.gameTestInfos().size(); sizes.add(size); maximum = Math.max(maximum, size);
            dimensions.merge(batch.dimension().identifier().toString(), size, Integer::sum);
            for (GameTestInfo info : batch.gameTestInfos()) constructedIds.add(info.id().toString());
        }
        var expected = new ArrayList<>(originalIds); var actual = new ArrayList<>(constructedIds);
        Collections.sort(expected); Collections.sort(actual);
        if (!expected.equals(actual) || maximum > requested || sizes.stream().anyMatch(size -> size < 1))
            throw new AssertionError("Native partition changed the registry collection or exceeded the requested width");
        String destination = System.getProperty("create.test.nativeBatchReport");
        if (destination == null) return;
        var report = new LinkedHashMap<String, Object>();
        report.put("schemaVersion", 1); report.put("status", "NATIVE_BATCH_CONSTRUCTION_ONLY");
        report.put("requestedBatchSize", requested); report.put("actualMaximumBatchSize", maximum); report.put("actualBatchSizes", sizes);
        report.put("actualBatchCount", sizes.size()); report.put("originalNativeCases", originalIds.size()); report.put("constructedNativeCases", constructedIds.size());
        report.put("originalNativeIDs", originalIds); report.put("constructedNativeIDs", constructedIds); report.put("dimensionCases", dimensions);
        report.put("exactOriginalCollectionPreserved", true); report.put("testExecutionOrPassClaim", false);
        try {
            Path target = Path.of(destination); Files.createDirectories(target.getParent());
            Path pending = target.resolveSibling(target.getFileName() + ".pending");
            Files.writeString(pending, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
            try { Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unavailable) { Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception failure) { throw new AssertionError("Cannot persist actual native batch construction evidence", failure); }
    }
}
