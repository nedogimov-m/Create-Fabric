package com.simibubi.create.content.contraptions.mounted;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.contraptions.StabilizedSubContraptions;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import com.simibubi.create.foundation.utility.NeoForgePackedValueSemantics;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The genuine old item stored a structure and initial direction, never a saved cart.
 * This new native plan value has no original cart UUID, paid receipt or default cargo snapshot.
 */
public record LegacyPackedCartPlan(UUID nativePlanId, CompoundTag structure, Direction initial, String originalValue) {
    public LegacyPackedCartPlan(UUID nativePlanId,CompoundTag structure,Direction initial){this(nativePlanId,structure,initial,null);}
    public static final int SCHEMA = 2;
    public LegacyPackedCartPlan {
        Objects.requireNonNull(nativePlanId); Objects.requireNonNull(initial); structure = structure.copy();
        if (!string(structure,"Type").equals("create:mounted")) throw invalid("A legacy packed plan requires its actual mounted structure");
        if (!list(structure,"SubContraptions").isEmpty() || !list(structure,"Passengers").isEmpty())
            throw invalid("An original item cannot invent bodies for its saved external entity references");
        if (structure.contains("GraphRestorationUpdates") || structure.getBooleanOr("GraphRecovering",false))
            throw invalid("A legacy item cannot own an unresolved native graph recovery");
    }
    @Override public CompoundTag structure() { return structure.copy(); }
    public CompoundTag encode() {
        var result = new CompoundTag(); result.putInt("CartItemSchema",SCHEMA);
        result.putInt("OriginalDataVersion",NeoForgeSavedDataImport.SOURCE_DATA_VERSION);
        result.store("NativePlanId",UUIDUtil.CODEC,nativePlanId);
        result.put("Contraption",structure.copy()); result.putString("InitialOrientation",initial.name());
        if(originalValue!=null)NeoForgePackedValueSemantics.bind(result,originalValue);
        return result;
    }
    public static boolean isPlan(ItemStack actual) {
        var tag = actual.get(AllDataComponents.MINECRAFT_CONTRAPTION_DATA);
        return tag != null && tag.getIntOr("CartItemSchema",0) == SCHEMA;
    }
    public static LegacyPackedCartPlan parse(ItemStack actual) {
        var data = actual.get(AllDataComponents.MINECRAFT_CONTRAPTION_DATA);
        if (data == null) throw invalid("Missing actual packed plan value");
        keys(data,"legacy packed cart plan","CartItemSchema","OriginalDataVersion","NativePlanId","Contraption","InitialOrientation",NeoForgePackedValueSemantics.SOURCE_VALUE,NeoForgePackedValueSemantics.NATIVE_IMAGE);
        if (integer(data,"CartItemSchema") != SCHEMA || integer(data,"OriginalDataVersion") != NeoForgeSavedDataImport.SOURCE_DATA_VERSION)
            throw invalid("Unknown native legacy-packed plan schema/original version");
        actualKind(actual);
        return new LegacyPackedCartPlan(uuid(data,"NativePlanId"),compound(data,"Contraption"),Direction.valueOf(string(data,"InitialOrientation")),NeoForgePackedValueSemantics.validOriginalValue(data));
    }
    public static NativeCartKind actualKind(ItemStack actual) {
        for (var kind : NativeCartKind.values()) if (actual.getItem() == kind.item()) return kind;
        throw invalid("A packed plan requires the actual containing rideable/chest/furnace contraption item");
    }
    /**
     * Only an actual admitted primary already owning this exact item may supply a new cart state.
     * Native placement created that cart; no historical/default chest or furnace state is synthesized.
     */
    public PackedCartForest realize(ItemStack exact, AbstractMinecart actualPrimary) {
        if (!(actualPrimary.level() instanceof net.minecraft.server.level.ServerLevel level)
                || level.getEntity(actualPrimary.getUUID()) != actualPrimary || actualPrimary.isRemoved())
            throw invalid("A real admitted native primary is required to realize an original packed plan");
        var controller = com.simibubi.create.content.contraptions.minecart.capability.CapabilityMinecartController.getIfPresent(level,actualPrimary.getUUID());
        var expected = parse(exact);
        if (controller == null || !controller.ownsPackedReceipt(exact) || !nativePlanId.equals(expected.nativePlanId)
                || !structure.equals(expected.structure) || initial != expected.initial
                || actualKind(exact) != NativeCartKind.of(actualPrimary) || !NativeCartState.cargoIsActuallyEmpty(actualPrimary))
            throw invalid("The actual accepted primary must own this exact original item plan and native cart kind");
        // Root yaw/pitch are chosen by actual placement below, as in the original addContraptionToMinecart.
        var node = new PackedCartForest.Node(-1,structure,initial,initial.getAxis().isHorizontal()?initial.toYRot():Direction.SOUTH.toYRot(),0,null);
        var base = new PackedCartForest.Base(NativeCartKind.of(actualPrimary),NativeCartState.capture(actualPrimary),Vec3.ZERO);
        return new PackedCartForest(nativePlanId,List.of(node),List.of(base),0);
    }
}
