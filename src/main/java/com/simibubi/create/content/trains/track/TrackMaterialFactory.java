package com.simibubi.create.content.trains.track;

import com.simibubi.create.Create;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

/** Source material builder, with native lazy tag ingredients and no client classes in common code. */
public final class TrackMaterialFactory {
    private final Identifier id;
    private String langName;private Identifier particle;
    private Supplier<? extends Supplier<? extends TrackBlock>> block;
    private TrackMaterial.TrackType type=TrackMaterial.TrackType.STANDARD;
    private TrackMaterial.TrackType.TrackBlockFactory factory;
    private Supplier<Optional<Ingredient>> sleepers=()->tagIngredient(List.of(Create.asResource("sleepers")));
    private Supplier<Optional<Ingredient>> rails=()->tagIngredient(List.of(Identifier.fromNamespaceAndPath("c","nuggets/iron"),Identifier.fromNamespaceAndPath("c","nuggets/zinc")));
    private TrackMaterialFactory(Identifier id){this.id=id;}
    public static TrackMaterialFactory make(Identifier id){return new TrackMaterialFactory(id);}
    public TrackMaterialFactory lang(String name){langName=name;return this;}
    public TrackMaterialFactory particle(Identifier texture){particle=texture;return this;}
    public TrackMaterialFactory block(Supplier<? extends Supplier<? extends TrackBlock>> block){this.block=block;return this;}
    public TrackMaterialFactory trackType(TrackMaterial.TrackType type){this.type=type;return this;}
    public TrackMaterialFactory customBlockFactory(TrackMaterial.TrackType.TrackBlockFactory factory){this.factory=factory;return this;}
    public TrackMaterialFactory sleeper(Ingredient ingredient){sleepers=()->Optional.of(ingredient);return this;}
    public TrackMaterialFactory sleeper(ItemLike... items){return sleeper(Ingredient.of(items));}
    public TrackMaterialFactory rails(Ingredient ingredient){rails=()->Optional.of(ingredient);return this;}
    public TrackMaterialFactory rails(ItemLike... items){return rails(Ingredient.of(items));}
    public TrackMaterialFactory noRecipeGen(){sleepers=rails=Optional::empty;return this;}
    public TrackMaterial build(){return new TrackMaterial(id,langName,block,particle,sleepers,rails,type,factory);}
    private static Optional<Ingredient> tagIngredient(List<Identifier> tags){
        var holders=tags.stream().flatMap(id->BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM,id)).stream().flatMap(HolderSet::stream)).distinct().toList();
        return holders.isEmpty()?Optional.empty():Optional.of(Ingredient.of(HolderSet.direct(holders)));
    }
}
