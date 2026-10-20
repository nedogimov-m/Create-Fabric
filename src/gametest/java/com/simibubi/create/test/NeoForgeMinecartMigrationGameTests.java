package com.simibubi.create.test;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.minecart.CouplingHandler;
import com.simibubi.create.content.contraptions.minecart.capability.MinecartController;
import com.simibubi.create.content.contraptions.mounted.NativeCartKind;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Actual native entity load/save/attachment owners and genuine source serializer shapes; execution UNRUN. */
public final class NeoForgeMinecartMigrationGameTests {
    private static final BlockPos P = new BlockPos(3, 2, 3);
    private static CompoundTag save(AbstractMinecart cart) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, cart.registryAccess());
        cart.saveWithoutId(output); return output.buildResult();
    }
    private static AbstractMinecart empty(GameTestHelper h, BlockPos relative) {
        return NativeCartKind.RIDEABLE.createEmpty(h.getLevel(), Vec3.atLowerCornerOf(h.absolutePos(relative)).add(.5, 0, .5));
    }
    private static CompoundTag controller(String type) { var result = new CompoundTag(); result.putString("Type", type); return result; }
    private static CompoundTag link(UUID main, UUID connected, float length, boolean structural) {
        var result = new CompoundTag(); result.store("Main", UUIDUtil.CODEC, main); result.store("Connected", UUIDUtil.CODEC, connected);
        result.putFloat("Length", length); result.putBoolean("Contraption", structural); return result;
    }
    private static ListTag vector(double x, double y, double z) {
        var result = new ListTag(); result.add(DoubleTag.valueOf(x)); result.add(DoubleTag.valueOf(y)); result.add(DoubleTag.valueOf(z)); return result;
    }
    private static CompoundTag stalled() {
        var result = new CompoundTag(); result.put("Pos", vector(1.25, 64.5, -7.75)); result.put("Motion", vector(.125, -.25, .375));
        result.putFloat("Yaw", 31.5f); result.putFloat("Pitch", -12.25f); return result;
    }
    private static CompoundTag oldSave(AbstractMinecart cart, Tag source) {
        var result = save(cart); var attachments = new CompoundTag(); attachments.put("create:minecart_controller", source.copy());
        result.put("neoforge:attachments", attachments); return result;
    }
    private static AbstractMinecart load(GameTestHelper h, CompoundTag saved) {
        var result = empty(h, P); result.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), saved));
        h.assertTrue(((AttachmentTarget) result).getAttached(MinecartController.ATTACHMENT) != null,
            "Actual Entity.load imported a real controller before native entity admission");
        h.assertTrue(h.getLevel().addFreshEntity(result), "Actual migrated native cart admitted"); return result;
    }
    private record Pair(AbstractMinecart first, AbstractMinecart second) {}
    private static Pair pair(GameTestHelper h) {
        var first = empty(h, P); var second = empty(h, P.east(3));
        var a = controller("NORMAL"); a.put("MainCoupling", link(first.getUUID(), second.getUUID(), 3, false));
        var b = controller("NORMAL"); b.put("ConnectedCoupling", link(first.getUUID(), second.getUUID(), 3, false));
        return new Pair(load(h, oldSave(first, a)), load(h, oldSave(second, b)));
    }

    @GameTest public void genuineOldAttachmentRetainsEndpointStallAndUnreceiptedTopology(GameTestHelper h) {
        var original = empty(h, P); var peer = UUID.randomUUID(); var source = controller("NORMAL");
        source.put("StallData", stalled()); source.put("MainCoupling", link(original.getUUID(), peer, 3.5f, false));
        var saved = oldSave(original, source); var untouched = saved.copy(); var actual = load(h, saved); var owner = MinecartController.get(actual);
        var outgoing = owner.inspectLink(true);
        h.assertTrue(owner.cart() == actual && owner.isStalled() && outgoing != null && outgoing.main().equals(actual.getUUID())
                && outgoing.connected().equals(peer) && outgoing.length() == 3.5f && outgoing.legacyUnreceipted()
                && !outgoing.creative() && outgoing.actualReceipt().isEmpty(),
            "The genuine source UUIDs/length/stall reach their actual native owner without fabricated paid or creative material");
        h.assertTrue(owner.serializeNBT(actual.registryAccess()).getCompoundOrEmpty("StallData").equals(source.getCompoundOrEmpty("StallData"))
                && untouched.equals(saved), "All original stall vectors/angles survive; the actual source input remains unchanged");
        owner.setStalledExternally(false);
        h.assertTrue(actual.getDeltaMovement().equals(new Vec3(.125, -.25, .375)), "Releasing the actual source stall restores original saved motion");
        h.succeed();
    }

    @GameTest public void legacyPairSurvivesNativeSaveReloadAndDecouplesWithoutInventedRefund(GameTestHelper h) {
        var pair = pair(h); var a = save(pair.first); var b = save(pair.second);
        pair.first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); pair.second.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var first = load(h, a); var second = load(h, b); var owner = MinecartController.get(first); var other = MinecartController.get(second);
        h.assertTrue(owner.inspectLink(true).legacyUnreceipted() && other.inspectLink(false).legacyUnreceipted()
                && owner.getCoupledCart(true).equals(second.getUUID()), "Native persistent attachments retain source provenance and reciprocal original topology");
        h.assertTrue(CouplingHandler.detachOrdinaryLinks(owner) && !owner.isLeadingCoupling() && !other.isConnectedToCoupling()
                && !owner.hasPendingRefunds() && !other.hasPendingRefunds(),
            "A real source link can be removed after native reload without generating an item the source never recorded");
        h.succeed();
    }

    @GameTest public void realSurvivalPaymentReversesLegacyChainAndKeepsItsSourceProvenance(GameTestHelper h) {
        var pair = pair(h); var third = empty(h, P.west(3)); h.assertTrue(h.getLevel().addFreshEntity(third), "Actual independent third cart admitted");
        var receipt = new ItemStack(AllItems.MINECART_COUPLING);
        try (var joined = NativeSurvivalTestPlayer.join(h, Vec3.atCenterOf(h.absolutePos(P)).add(0, 2, 2))) {
            var player = joined.player(); player.setItemInHand(InteractionHand.MAIN_HAND, receipt);
            h.assertTrue(CouplingHandler.tryToCoupleCarts(player, h.getLevel(), pair.first.getId(), third.getId()),
                "Genuine registered survival payment extends a real migrated chain after topology reversal");
            var old = MinecartController.get(pair.second).inspectLink(true); var paid = MinecartController.get(pair.first).inspectLink(true);
            h.assertTrue(old != null && old.connected().equals(pair.first.getUUID()) && old.legacyUnreceipted() && old.actualReceipt().isEmpty()
                    && !old.creative() && paid != null && !paid.legacyUnreceipted() && paid.actualReceipt() == receipt
                    && player.getMainHandItem().isEmpty(), "Original source edge stays explicitly unreceipted; only the genuinely new edge owns the original paid item");
        }
        h.succeed();
    }

    @GameTest public void actualNativeAttachmentWinsBeforeMalformedLegacyIsRead(GameTestHelper h) {
        var original = empty(h, P); h.assertTrue(h.getLevel().addFreshEntity(original), "Actual native cart admitted");
        MinecartController.get(original).setStalledExternally(true); var saved = save(original);
        var old = new CompoundTag(); old.put("create:minecart_controller", StringTag.valueOf("not a source controller compound")); saved.put("neoforge:attachments", old);
        original.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); var restored = load(h, saved); var owner = MinecartController.get(restored);
        h.assertTrue(!owner.isEmpty() && owner.isStalled() && owner.cart() == restored,
            "Fabric loaded the genuine native owner before the migration hook; malformed old data cannot replace it");
        h.succeed();
    }

    @GameTest public void malformedSavedNativeOwnerCannotFallBackToOtherwiseValidLegacy(GameTestHelper h) {
        var actual = empty(h, P); var saved = oldSave(actual, controller("NORMAL"));
        var nativeAttachments = new CompoundTag(); nativeAttachments.put("create:minecart_controller", StringTag.valueOf("invalid native owner"));
        saved.put("fabric:attachments", nativeAttachments); var original = saved.copy(); boolean rejected = false;
        try { actual.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), saved)); }
        catch (RuntimeException expected) { rejected = true; }
        h.assertTrue(rejected && ((AttachmentTarget) actual).getAttached(MinecartController.ATTACHMENT) == null && saved.equals(original),
            "A real saved native owner that failed its codec cannot be replaced by even a valid old source attachment");
        h.succeed();
    }

    @GameTest public void sourceEmptyAndNormalControllersRemainDistinctAcrossNativeReload(GameTestHelper h) {
        var first = load(h, oldSave(empty(h, P), controller("EMPTY")));
        var second = load(h, oldSave(empty(h, P.east(3)), controller("EMPTY")));
        var normal = load(h, oldSave(empty(h, P.west(3)), controller("NORMAL")));
        var a = MinecartController.get(first); var b = MinecartController.get(second);
        h.assertTrue(a.isEmpty() && b.isEmpty() && a != b && a != MinecartController.EMPTY && a.cart() == first && b.cart() == second
                && !MinecartController.get(normal).isEmpty(), "Two actual source EMPTY owners remain per-entity and cannot mutate the shared sentinel or become NORMAL");
        a.tick(); b.tick(); var saved = save(first); first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        h.assertTrue(MinecartController.get(load(h, saved)).isEmpty(), "Actual source EMPTY survives native persistent attachment serialization");
        h.succeed();
    }

    @GameTest public void unknownMalformedAndForeignEndpointSourceFailBeforeNativeOwnership(GameTestHelper h) {
        var unknown = controller("FUTURE_VERSION"); var versioned = controller("NORMAL"); versioned.putInt("FormatVersion", 7);
        var wrongEndpoint = controller("NORMAL"); wrongEndpoint.put("MainCoupling", link(UUID.randomUUID(), UUID.randomUUID(), 3, false));
        var nonfinite = controller("NORMAL"); nonfinite.put("StallData", stalled()); nonfinite.getCompoundOrEmpty("StallData").putFloat("Yaw", Float.NaN);
        for (Tag source : List.of(unknown, versioned, wrongEndpoint, nonfinite, StringTag.valueOf("malformed source"))) {
            var actual = empty(h, P); var saved = oldSave(actual, source); var before = saved.copy(); boolean rejected = false;
            try { actual.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), saved)); }
            catch (RuntimeException expected) { rejected = true; }
            h.assertTrue(rejected && ((AttachmentTarget) actual).getAttached(MinecartController.ATTACHMENT) == null
                    && h.getLevel().getEntity(actual.getUUID()) == null && saved.equals(before),
                "Unknown/malformed source or foreign endpoint is explicit failure before native attachment/entity ownership and source mutation");
        }
        h.succeed();
    }

    @GameTest public void inconsistentReciprocalLegacyLinksCannotBeRetiredOrConvertedIntoPaidItems(GameTestHelper h) {
        var first = empty(h, P); var second = empty(h, P.east(3)); var a = controller("NORMAL"); var b = controller("NORMAL");
        a.put("MainCoupling", link(first.getUUID(), second.getUUID(), 3, false));
        b.put("ConnectedCoupling", link(first.getUUID(), second.getUUID(), 4, false));
        var actual = load(h, oldSave(first, a)); var other = load(h, oldSave(second, b));
        var owner = MinecartController.get(actual); var peer = MinecartController.get(other);
        h.assertTrue(!CouplingHandler.detachOrdinaryLinks(owner) && owner.isLeadingCoupling() && peer.isConnectedToCoupling()
                && owner.getCouplingLength(true) == 3 && peer.getCouplingLength(false) == 4 && !owner.hasPendingRefunds() && !peer.hasPendingRefunds(),
            "A genuine source topology mismatch remains intact for explicit repair and cannot produce paid custody");
        h.succeed();
    }
}
