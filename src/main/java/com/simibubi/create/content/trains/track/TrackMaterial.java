package com.simibubi.create.content.trains.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Common physical material registry. Client partial models live in TrackModels. */
public final class TrackMaterial {
    public static final Map<Identifier,TrackMaterial> ALL = new LinkedHashMap<>();
    public static final TrackMaterial ANDESITE = TrackMaterialFactory.make(Create.asResource("andesite"))
        .lang("Andesite").block(() -> () -> AllBlocks.TRACK)
        .particle(Create.asResource("block/palettes/stone_types/polished/andesite_cut_polished")).build();
    /** Unknown material is an error in persistent geometry, never a silent rail replacement. */
    public static final Codec<TrackMaterial> CODEC = Identifier.CODEC.comapFlatMap(id -> {
        var material=ALL.get(id);return material==null?DataResult.error(()->"Unknown track material "+id):DataResult.success(material);
    }, material->material.id);
    public final Identifier id,particle;
    public final String langName;
    public final Supplier<? extends Supplier<? extends TrackBlock>> trackBlock;
    /** Native Ingredient has no EMPTY. Empty means recipe generation was disabled. Suppliers resolve tags after reload. */
    public final Supplier<Optional<Ingredient>> sleeperIngredient,railsIngredient;
    public final TrackType trackType;
    private final TrackType.TrackBlockFactory customFactory;
    public TrackMaterial(Identifier id,String langName,Supplier<? extends Supplier<? extends TrackBlock>> block,Identifier particle,
            Supplier<Optional<Ingredient>> sleepers,Supplier<Optional<Ingredient>> rails,TrackType type,TrackType.TrackBlockFactory customFactory) {
        this.id=Objects.requireNonNull(id);this.langName=Objects.requireNonNull(langName);trackBlock=Objects.requireNonNull(block);
        this.particle=Objects.requireNonNull(particle);sleeperIngredient=sleepers;railsIngredient=rails;trackType=Objects.requireNonNull(type);this.customFactory=customFactory;
        if(ALL.putIfAbsent(id,this)!=null)throw new IllegalArgumentException("Duplicate track material "+id);
    }
    public Supplier<? extends TrackBlock> getBlockSupplier(){return trackBlock.get();}
    public TrackBlock getBlock(){return getBlockSupplier().get();}
    public ItemStack asStack(){return asStack(1);}
    public ItemStack asStack(int count){return new ItemStack(getBlock(),count);}
    public TrackBlock createBlock(BlockBehaviour.Properties properties){return (customFactory==null?trackType.factory:customFactory).create(properties,this);}
    public boolean isFromMod(String id){return this.id.getNamespace().equals(id);}
    public static List<TrackMaterial> allFromMod(String id){return ALL.values().stream().filter(m->m.isFromMod(id)).toList();}
    public static List<Supplier<? extends Block>> allBlocksFromMod(String id){return allFromMod(id).stream().<Supplier<? extends Block>>map(TrackMaterial::getBlockSupplier).toList();}
    public static List<Supplier<? extends Block>> allBlocks(){return ALL.values().stream().<Supplier<? extends Block>>map(TrackMaterial::getBlockSupplier).toList();}
    public String resourceName(){return id.getPath();}
    public static TrackMaterial fromId(Identifier id){return ALL.get(id);}
    /** Legacy 0.5 blank material only; unknown modern ids must remain a load error. */
    public static TrackMaterial deserialize(String name){if(name.isBlank())return ANDESITE;var material=fromId(Identifier.parse(name));if(material==null)throw new IllegalArgumentException("Unknown track material "+name);return material;}
    public static TrackMaterial fromItem(Item item){return item instanceof BlockItem block&&block.getBlock() instanceof ITrackBlock track?track.getMaterial():ANDESITE;}
    public static final class TrackType {
        @FunctionalInterface public interface TrackBlockFactory{TrackBlock create(BlockBehaviour.Properties properties,TrackMaterial material);}
        public static final TrackType STANDARD=new TrackType(Create.asResource("standard"),TrackBlock::new);
        public final Identifier id;protected final TrackBlockFactory factory;
        public TrackType(Identifier id,TrackBlockFactory factory){this.id=Objects.requireNonNull(id);this.factory=Objects.requireNonNull(factory);}
    }
}
