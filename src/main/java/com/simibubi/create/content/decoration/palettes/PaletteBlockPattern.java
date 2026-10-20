package com.simibubi.create.content.decoration.palettes;
import net.minecraft.world.level.block.*;
import java.util.function.Function;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
/** Source six patterns; generation metadata lives in the pinned native resource closure. */
public class PaletteBlockPattern {
    public static final PaletteBlockPattern CUT=new PaletteBlockPattern("cut",PatternNameType.PREFIX,Block::new,PaletteBlockPartial.ALL_PARTIALS),POLISHED=new PaletteBlockPattern("polished_cut",PatternNameType.PREFIX,Block::new,PaletteBlockPartial.FOR_POLISHED),BRICKS=new PaletteBlockPattern("cut_bricks",PatternNameType.WRAP,Block::new,PaletteBlockPartial.ALL_PARTIALS),SMALL_BRICKS=new PaletteBlockPattern("small_bricks",PatternNameType.WRAP,Block::new,PaletteBlockPartial.ALL_PARTIALS),LAYERED=new PaletteBlockPattern("layered",PatternNameType.PREFIX,Block::new),PILLAR=new PaletteBlockPattern("pillar",PatternNameType.SUFFIX,ConnectedPillarBlock::new);
    public static final PaletteBlockPattern[] VANILLA_RANGE={CUT,POLISHED,BRICKS,SMALL_BRICKS,LAYERED,PILLAR},STANDARD_RANGE={CUT,POLISHED,BRICKS,SMALL_BRICKS,LAYERED,PILLAR};
    private final String id;private final PatternNameType nameType;private final Function<Properties,? extends Block> factory;private final PaletteBlockPartial<?>[] partials;
    protected PaletteBlockPattern(String id,PatternNameType nameType,Function<Properties,? extends Block> factory,PaletteBlockPartial<?>...partials){this.id=id;this.nameType=nameType;this.factory=factory;this.partials=partials;}
    public String createName(String variant){if(nameType==PatternNameType.WRAP){var parts=id.split("_");return parts[0]+"_"+variant+"_"+parts[1];}return nameType==PatternNameType.SUFFIX?variant+"_"+id:id+"_"+variant;}
    public PaletteBlockPartial<?>[] getPartials(){return partials;}
    public Function<Properties,? extends Block> getBlockFactory(){return factory;}
    public String id(){return id;}
    public boolean isTranslucent(){return false;}
    public enum PatternNameType {PREFIX,SUFFIX,WRAP}
}
