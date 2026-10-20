package com.simibubi.create.content.fluids.potion;

import com.mojang.serialization.Codec;
import com.simibubi.create.*;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.component.*;
import net.minecraft.network.codec.*;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.alchemy.PotionContents;
import java.util.Locale;

public final class PotionFluid {
    private PotionFluid(){}
    public static FluidStack of(long amount,PotionContents contents,BottleType bottle){
        var patch=DataComponentPatch.builder().set(AllDataComponents.POTION_FLUID_BOTTLE_TYPE,bottle);
        if(!contents.equals(PotionContents.EMPTY))patch.set(DataComponents.POTION_CONTENTS,contents);
        return new FluidStack(FluidVariant.of(AllFluids.POTION,patch.build()),amount);
    }
    public enum BottleType implements StringRepresentable {
        REGULAR,SPLASH,LINGERING;
        public static final Codec<BottleType> CODEC=StringRepresentable.fromEnum(BottleType::values);
        public static final StreamCodec<io.netty.buffer.ByteBuf,BottleType> STREAM_CODEC=ByteBufCodecs.idMapper(index->values()[Math.clamp(index,0,values().length-1)],Enum::ordinal);
        @Override public String getSerializedName(){return name().toLowerCase(Locale.ROOT);}
    }
}
