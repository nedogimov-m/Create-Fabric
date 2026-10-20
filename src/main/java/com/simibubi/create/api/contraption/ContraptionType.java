package com.simibubi.create.api.contraption;

import com.simibubi.create.AllContraptionTypes;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.contraptions.Contraption;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

public final class ContraptionType {
    public final Supplier<? extends Contraption> factory;
    public ContraptionType(Supplier<? extends Contraption> factory){this.factory=factory;}
    public Holder<ContraptionType> holder(){return CreateBuiltInRegistries.CONTRAPTION_TYPE.wrapAsHolder(this);}
    public boolean is(TagKey<ContraptionType> tag){return holder().is(tag);}
    public static Contraption fromType(String name){var legacy=AllContraptionTypes.BY_LEGACY_NAME.get(name);if(legacy!=null)return legacy.factory.get();var id=Identifier.tryParse(name);var type=id==null?null:CreateBuiltInRegistries.CONTRAPTION_TYPE.getValue(id);return type==null?null:type.factory.get();}
}
