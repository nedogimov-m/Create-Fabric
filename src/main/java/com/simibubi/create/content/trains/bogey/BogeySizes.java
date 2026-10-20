package com.simibubi.create.content.trains.bogey;

import com.simibubi.create.Create;
import java.util.*;
import net.minecraft.resources.Identifier;

/** Common size registry. Wheel radius is expressed in world blocks, as in the pinned source. */
public final class BogeySizes {
    private static final Map<Identifier,BogeySize> SIZES=new LinkedHashMap<>();
    private static final List<BogeySize> INCREASING=new ArrayList<>(),DECREASING=new ArrayList<>();
    private static final Map<Identifier,BogeySize> VIEW=Collections.unmodifiableMap(SIZES);
    private static final List<BogeySize> INC_VIEW=Collections.unmodifiableList(INCREASING),DEC_VIEW=Collections.unmodifiableList(DECREASING);
    public static final BogeySize SMALL=new BogeySize(Create.asResource("small"),6.5f/16),LARGE=new BogeySize(Create.asResource("large"),12.5f/16);
    static {register(SMALL);register(LARGE);}
    public static void register(BogeySize size){
        Objects.requireNonNull(size);if(SIZES.putIfAbsent(size.id(),size)!=null)throw new IllegalArgumentException("Duplicate bogey size "+size.id());
        INCREASING.add(size);DECREASING.add(size);INCREASING.sort(Comparator.comparing(BogeySize::wheelRadius));DECREASING.sort(Comparator.comparing(BogeySize::wheelRadius).reversed());
    }
    public static Map<Identifier,BogeySize> all(){return VIEW;}
    public static List<BogeySize> allSortedIncreasing(){return INC_VIEW;}
    public static List<BogeySize> allSortedDecreasing(){return DEC_VIEW;}
    public record BogeySize(Identifier id,float wheelRadius){
        public BogeySize {Objects.requireNonNull(id);if(!Float.isFinite(wheelRadius)||wheelRadius<=0)throw new IllegalArgumentException("Invalid bogey wheel radius");}
        public BogeySize nextBySize(){int index=INCREASING.indexOf(this);if(index<0)throw new IllegalStateException("Unregistered bogey size "+id);return INCREASING.get((index+1)%INCREASING.size());}
    }
    private BogeySizes(){}
}
