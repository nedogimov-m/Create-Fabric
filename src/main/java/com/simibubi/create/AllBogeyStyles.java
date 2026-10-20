package com.simibubi.create;

import com.simibubi.create.content.trains.bogey.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Real standard style registry; renderer providers are registered separately on the native client. */
public final class AllBogeyStyles {
    private static final Map<Identifier,BogeyStyle> STYLES=new LinkedHashMap<>();
    private static final Map<Identifier,Map<Identifier,BogeyStyle>> GROUPS=new LinkedHashMap<>();
    public static final Map<Identifier,BogeyStyle> BOGEY_STYLES=Collections.unmodifiableMap(STYLES);
    public static final Identifier STANDARD_CYCLE_GROUP=Create.asResource("standard");
    public static final BogeyStyle STANDARD=new BogeyStyle.Builder(Create.asResource("standard"),STANDARD_CYCLE_GROUP)
            .displayName(Component.translatable("create.bogey.style.standard"))
            .size(BogeySizes.SMALL,()->AllBlocks.SMALL_BOGEY).size(BogeySizes.LARGE,()->AllBlocks.LARGE_BOGEY).build();
    public static void register(BogeyStyle style){if(STYLES.putIfAbsent(style.id,style)!=null)throw new IllegalArgumentException("Duplicate bogey style "+style.id);GROUPS.computeIfAbsent(style.cycleGroup,key->new LinkedHashMap<>()).put(style.id,style);}
    public static Map<Identifier,BogeyStyle> getCycleGroup(Identifier group){var styles=GROUPS.get(group);return styles==null?Map.of():Collections.unmodifiableMap(styles);}
    private AllBogeyStyles(){}
}
