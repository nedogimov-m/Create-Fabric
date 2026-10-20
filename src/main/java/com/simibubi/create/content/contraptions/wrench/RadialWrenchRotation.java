package com.simibubi.create.content.contraptions.wrench;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlock;
import com.simibubi.create.content.redstone.DirectedDirectionalBlock;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Pinned Create rotation properties shared by the native client and authoritative server. */
public final class RadialWrenchRotation {
    public record Choice(Property<?> property,String label) {}
    private static final Map<Property<?>,String> PROPERTIES=new LinkedHashMap<>();
    private static final Set<Identifier> BLACKLIST=new HashSet<>();
    static {
        registerRotationProperty(RotatedPillarKineticBlock.AXIS,"Axis");
        registerRotationProperty(DirectionalKineticBlock.FACING,"Facing");
        registerRotationProperty(HorizontalAxisKineticBlock.HORIZONTAL_AXIS,"Axis");
        registerRotationProperty(HorizontalKineticBlock.HORIZONTAL_FACING,"Facing");
        registerRotationProperty(HopperBlock.FACING,"Facing");
        registerRotationProperty(DirectedDirectionalBlock.TARGET,"Target");
        registerRotationProperty(SequencedGearshiftBlock.VERTICAL,"Vertical");
        registerBlacklistedBlock(BuiltInRegistries.BLOCK.getKey(AllBlocks.LARGE_WATER_WHEEL));
        registerBlacklistedBlock(BuiltInRegistries.BLOCK.getKey(AllBlocks.WATER_WHEEL_STRUCTURAL));
    }
    public static void registerRotationProperty(Property<?> property,String label){PROPERTIES.putIfAbsent(Objects.requireNonNull(property),Objects.requireNonNull(label));}
    public static void registerBlacklistedBlock(Identifier id){BLACKLIST.add(Objects.requireNonNull(id));}
    public static List<Choice> propertiesFor(BlockState state){
        if(BLACKLIST.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock())))return List.of();
        return PROPERTIES.entrySet().stream().filter(entry->state.hasProperty(entry.getKey())).map(entry->new Choice(entry.getKey(),entry.getValue())).toList();
    }
    public static Optional<BlockState> select(BlockState state,String propertyName,String serializedValue){
        return propertiesFor(state).stream().filter(choice->choice.property().getName().equals(propertyName)).findFirst().flatMap(choice->selectValue(state,choice.property(),serializedValue));
    }
    private static <T extends Comparable<T>>Optional<BlockState> selectValue(BlockState state,Property<T> property,String value){return property.getValue(value).map(selected->state.setValue(property,selected));}
    public static List<BlockState> states(BlockState state,Property<?> property){return statesFor(state,property);}
    private static <T extends Comparable<T>>List<BlockState> statesFor(BlockState state,Property<T> property){return property.getPossibleValues().stream().map(value->state.setValue(property,value)).toList();}
    public static String value(BlockState state,Property<?> property){return valueFor(state,property);}
    private static <T extends Comparable<T>>String valueFor(BlockState state,Property<T> property){return property.getName(state.getValue(property));}
    private RadialWrenchRotation(){}
}
