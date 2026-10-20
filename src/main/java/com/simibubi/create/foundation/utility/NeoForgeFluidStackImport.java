package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;

/** Genuine original FluidStack fields; original integer mB become exact native long droplets. */
public final class NeoForgeFluidStackImport {
    public record Fluid(FluidVariant variant, long amount) {
        public CompoundTag slot(HolderLookup.Provider registries) {
            var tag = new CompoundTag();
            tag.put("variant", FluidVariant.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), variant).getOrThrow());
            tag.putLong("amount", amount); return tag;
        }
    }
    /** Actual NeoForge21.1.219 FluidTank.writeToNBT envelope, distinct from a FluidStack. */
    public static Fluid tank(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        keys(source, "FluidTank envelope", "Fluid");
        return convert(source.isEmpty() ? new CompoundTag() : compound(source, "Fluid"), version, registries, scope);
    }
    public static Fluid convert(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (version != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unknown original FluidStack version");
        keys(source, "FluidStack", "id", "amount", "components");
        if (source.isEmpty()) return new Fluid(FluidVariant.blank(), 0);
        String original = string(source, "id");
        Identifier id = original.equals("neoforge:milk") ? Create.asResource("milk") : Identifier.parse(original);
        if (!BuiltInRegistries.FLUID.containsKey(id)) throw invalid("Unknown original fluid " + original);
        int millibuckets = integer(source, "amount");
        if (millibuckets <= 0) throw invalid("Nonpositive original fluid amount");
        var patch = source.contains("components") ? DataComponentPatch.CODEC.parse(
            registries.createSerializationContext(NbtOps.INSTANCE), NeoForgeItemComponentImport.patch(compound(source, "components"), version, registries, scope)).getOrThrow() : DataComponentPatch.EMPTY;
        var variant = FluidVariant.of(BuiltInRegistries.FLUID.getValue(id), patch);
        if (variant.isBlank()) throw invalid("Original nonempty fluid became blank");
        return new Fluid(variant, Math.multiplyExact((long) millibuckets, 81));
    }
    private NeoForgeFluidStackImport() {}
    public static Fluid tank(CompoundTag source, int version, HolderLookup.Provider registries) { return tank(source, version, registries, null); }
    public static Fluid convert(CompoundTag source, int version, HolderLookup.Provider registries) { return convert(source, version, registries, null); }
    private static NeoForgeMigrationScope child(NeoForgeMigrationScope scope, String role, int index) { return scope == null ? null : scope.item(role, index); }

}
