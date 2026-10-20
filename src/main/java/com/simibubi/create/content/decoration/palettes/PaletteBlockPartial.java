package com.simibubi.create.content.decoration.palettes;
import java.util.function.Supplier;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
/** Source partial constructors and IDs with native Block/Item ownership in place of Registrate builders. */
public abstract class PaletteBlockPartial<B extends Block> {
    public static final PaletteBlockPartial<StairBlock> STAIR=new PaletteBlockPartial<>("stairs"){@Override protected StairBlock createBlock(Supplier<? extends Block> block,Properties properties){return new StairBlock(block.get().defaultBlockState(),properties);}};
    public static final PaletteBlockPartial<SlabBlock> SLAB=new PaletteBlockPartial<>("slab"){@Override protected SlabBlock createBlock(Supplier<? extends Block> block,Properties properties){return new SlabBlock(properties);}@Override protected boolean canRecycle(){return false;}};
    public static final PaletteBlockPartial<SlabBlock> UNIQUE_SLAB=new PaletteBlockPartial<>("slab"){@Override protected SlabBlock createBlock(Supplier<? extends Block> block,Properties properties){return new SlabBlock(properties);}@Override protected boolean canRecycle(){return false;}};
    public static final PaletteBlockPartial<WallBlock> WALL=new PaletteBlockPartial<>("wall"){@Override protected WallBlock createBlock(Supplier<? extends Block> block,Properties properties){return new WallBlock(properties.forceSolidOn());}};
    public static final PaletteBlockPartial<?>[] ALL_PARTIALS={STAIR,SLAB,WALL},FOR_POLISHED={STAIR,UNIQUE_SLAB,WALL};
    private final String name;protected PaletteBlockPartial(String name){this.name=name;}
    protected abstract B createBlock(Supplier<? extends Block> block,Properties properties);
    protected boolean canRecycle(){return true;}
    public B create(String variant,PaletteBlockPattern pattern,Block base){String patternName=pattern.createName(variant);if(patternName.endsWith("bricks"))patternName=patternName.substring(0,patternName.length()-1);return PaletteStoneRegistry.register(patternName+"_"+name,Properties.ofFullCopy(base).requiresCorrectToolForDrops(),properties->createBlock(()->base,properties));}
}
