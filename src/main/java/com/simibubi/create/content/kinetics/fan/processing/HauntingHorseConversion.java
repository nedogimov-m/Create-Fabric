package com.simibubi.create.content.kinetics.fan.processing;

import com.mojang.serialization.Codec;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.NativeEntityAcceptance;
import com.simibubi.create.foundation.mixin.accessor.LivingEntityEquipmentAccessor;
import java.util.EnumMap;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Actual source100-step conversion with explicit native admission and real gear/rider custody. */
public final class HauntingHorseConversion {
    public static final AttachmentType<Integer> PROGRESS = AttachmentRegistry.createPersistent(Create.asResource("horse_haunting"), Codec.intRange(0, 100));
    private static final AttachmentType<Boolean> PENDING_BODY_ARMOR = AttachmentRegistry.createPersistent(Create.asResource("haunting_body_armor_ejection"), Codec.BOOL);
    public static void register() {}

    public static void process(ServerLevel level, Horse horse) {
        if (horse.isRemoved() || horse.level() != level) return;
        var target = (AttachmentTarget) horse; int progress = target.getAttachedOrElse(PROGRESS, 0);
        if (progress < 100) {
            if (progress % 10 == 0) level.playSound(null, horse.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.NEUTRAL, 1, 1.5f * progress / 100f);
            target.setAttached(PROGRESS, progress + 1); return;
        }
        convert(level, horse);
    }
    private static void convert(ServerLevel level, Horse horse) {
        // Native Mob.convertTo transfers custody before addFreshEntity and ignores
        // its boolean result. Keep every actual old owner intact until admission.
        var problems = new ProblemReporter.Collector(horse.problemPath());
        var output = TagValueOutput.createWithContext(problems, horse.registryAccess()); horse.saveWithoutId(output);
        if (!problems.isEmpty()) throw new IllegalStateException("Cannot snapshot actual haunted horse: " + problems.getReport());
        var originalSnapshot = output.buildResult(); var data = originalSnapshot.copy(); data.remove("UUID"); data.remove("equipment"); data.remove("Passengers"); data.remove("leash");
        var replacement = EntityTypes.SKELETON_HORSE.create(level, EntitySpawnReason.CONVERSION);
        if (replacement == null) return;
        replacement.load(TagValueInput.create(problems, horse.registryAccess(), data));
        if (!problems.isEmpty()) { replacement.discard(); throw new IllegalStateException("Cannot restore actual haunted horse metadata: " + problems.getReport()); }
        var gear = new EnumMap<EquipmentSlot, ItemStack>(EquipmentSlot.class);
        var snapshots = new EnumMap<EquipmentSlot, ItemStack>(EquipmentSlot.class);
        for (var slot : EquipmentSlot.values()) { var stack = horse.getItemBySlot(slot); gear.put(slot, stack); snapshots.put(slot, stack.copy()); }
        var riders = List.copyOf(horse.getPassengers()); var vehicle = horse.getVehicle(); var leash = horse.getLeashData();
        var oldGear = ((LivingEntityEquipmentAccessor) horse).create$getEquipment();
        var newGear = ((LivingEntityEquipmentAccessor) replacement).create$getEquipment();
        ((AttachmentTarget) replacement).removeAttached(PROGRESS);
        if (!gear.get(EquipmentSlot.BODY).isEmpty()) {
            ((AttachmentTarget) replacement).setAttached(PENDING_BODY_ARMOR, true);
            replacement.setDropChance(EquipmentSlot.BODY, 2);
        }
        boolean accepted = false;
        try (var scope = NativeEntityAcceptance.prepare(replacement, () -> {
            if (horse.isRemoved() || level.getEntity(horse.getUUID()) != horse || horse.getVehicle() != vehicle || horse.getLeashData() != leash) return false;
            var actualRiders = horse.getPassengers(); if (actualRiders.size() != riders.size()) return false;
            for (int i = 0; i < riders.size(); i++) if (actualRiders.get(i) != riders.get(i)) return false;
            for (var slot : EquipmentSlot.values()) if (oldGear.get(slot) != gear.get(slot) || !ItemStack.matches(oldGear.get(slot), snapshots.get(slot))) return false;
            return true;
        }, insertion -> {
            insertion.requireCommitting(replacement);
            for (var slot : EquipmentSlot.values()) { oldGear.set(slot, ItemStack.EMPTY); newGear.set(slot, gear.get(slot)); }
        })) {
            try { scope.add(); } finally { accepted = scope.insertion() != null; }
        } finally {
            if (!accepted) replacement.discard();
            else {
                // The native insertion already committed gear custody. Later
                // onCreated callbacks may consume/remove that new actual owner;
                // such callbacks cannot resurrect the old horse's paid stock.
                if (!replacement.isRemoved()) {
                    // This is continuation of an existing native ride. Preserve
                    // forced custody while suppressing a new ENTITY_MOUNT event
                    // and START_RIDING criterion (the actual native third flag).
                    if (vehicle != null) { horse.stopRiding(); replacement.startRiding(vehicle, true, false); }
                    for (var rider : riders) if (rider.getVehicle() == horse) rider.startRiding(replacement, true, false);
                    horse.setLeashData(null); replacement.setLeashData(leash);
                }
                horse.discard();
            }
        }
        if (accepted && !replacement.isRemoved()) {
            level.playSound(null, replacement.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.NEUTRAL, 1.25f, .65f);
            retryBodyArmor(level, replacement);
        }
    }
    public static void retryBodyArmor(ServerLevel level, Mob mob) {
        if (!(mob instanceof SkeletonHorse) || mob.isRemoved() || !((AttachmentTarget) mob).getAttachedOrElse(PENDING_BODY_ARMOR, false)) return;
        var equipment = ((LivingEntityEquipmentAccessor) mob).create$getEquipment(); var armor = equipment.get(EquipmentSlot.BODY);
        if (armor.isEmpty()) { ((AttachmentTarget) mob).removeAttached(PENDING_BODY_ARMOR); return; }
        var snapshot = armor.copy();
        var dropped = new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), snapshot.copy()); dropped.setDefaultPickUpDelay();
        boolean accepted = false;
        try (var scope = NativeEntityAcceptance.prepare(dropped,
            () -> !mob.isRemoved() && level.getEntity(mob.getUUID()) == mob && equipment.get(EquipmentSlot.BODY) == armor && ItemStack.matches(armor, snapshot),
            insertion -> { insertion.requireCommitting(dropped); equipment.set(EquipmentSlot.BODY, ItemStack.EMPTY); })) {
            try { scope.add(); } finally { accepted = scope.insertion() != null; }
        } finally {
            if (accepted) ((AttachmentTarget) mob).removeAttached(PENDING_BODY_ARMOR);
            else { dropped.setItem(ItemStack.EMPTY); dropped.discard(); }
        }
    }
    private HauntingHorseConversion() {}
}
