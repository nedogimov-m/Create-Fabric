package com.simibubi.create.content.contraptions;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.simibubi.create.content.contraptions.mounted.LegacyPackedCartPlan;
import com.simibubi.create.content.contraptions.mounted.CartAssemblerBlockEntity.CartMovementMode;
import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Source3955 Create structures are converted before any native entity factory or actual publication. */
public final class NeoForgeContraptionEntityImport {
    /** Actual original component body, with the actual containing source item/slot scope. */
    public static CompoundTag packed(CompoundTag originalComponentBody,int version,HolderLookup.Provider registries,
            NeoForgeMigrationScope actualContainingItemScope) {
        if (version != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unknown original packed contraption version");
        if (actualContainingItemScope == null) throw invalid("Original packed body requires its genuine containing source item scope");
        keys(originalComponentBody,"original packed mounted structure","Type","Blocks","CapturedMultiblocks","Actors","DisabledActors","Interactors",
            "Superglue","Seats","Passengers","SubContraptions","Anchor","Stalled","BottomlessSupply","BoundsFront","RotationMode",
            "InitialOrientation","items","fluids");
        if (!string(originalComponentBody,"Type").equals("create:mounted")) throw invalid("Original packed item is not an actual mounted structure");
        var initial = Direction.valueOf(string(originalComponentBody,"InitialOrientation"));
        var rotation = CartMovementMode.valueOf(string(originalComponentBody,"RotationMode"));
        if (!list(originalComponentBody,"SubContraptions").isEmpty() || !list(originalComponentBody,"Passengers").isEmpty())
            throw invalid("Original packed item only names external passengers/child UUIDs and contains no genuine saved bodies for them");
        var converted = NeoForgeContraptionGeometryImport.convert(originalComponentBody,version,registries,actualContainingItemScope).tag();
        converted.remove("InitialOrientation"); converted.putString("RotationMode",rotation.name());
        return new LegacyPackedCartPlan(actualContainingItemScope.nativeValueIdentity("packed_body",0),converted,initial,com.simibubi.create.foundation.utility.NeoForgePackedValueSemantics.originalValue(originalComponentBody)).encode();
    }
    private NeoForgeContraptionEntityImport() {}
}
