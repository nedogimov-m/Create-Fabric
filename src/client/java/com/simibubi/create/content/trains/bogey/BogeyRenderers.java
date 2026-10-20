package com.simibubi.create.content.trains.bogey;

import com.simibubi.create.AllBogeyStyles;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** Client-only size renderer registry. Common style loading never links native client/Flywheel classes. */
public final class BogeyRenderers {
    private record Key(Identifier style,BogeySizes.BogeySize size){}
    private static final Map<Key,BogeyRenderer> RENDERERS=new LinkedHashMap<>();
    public static void register(BogeyStyle style,BogeySizes.BogeySize size,BogeyRenderer renderer){if(!style.validSizes().contains(size))throw new IllegalArgumentException("Unsupported style size");if(RENDERERS.putIfAbsent(new Key(style.id,size),Objects.requireNonNull(renderer))!=null)throw new IllegalArgumentException("Duplicate bogey renderer");}
    public static void registerStandard(){register(AllBogeyStyles.STANDARD,BogeySizes.SMALL,new StandardBogeyRenderer.Small());register(AllBogeyStyles.STANDARD,BogeySizes.LARGE,new StandardBogeyRenderer.Large());}
    public static void extract(BogeyStyle style,BogeySizes.BogeySize size,CompoundTag data,float angle,float partial,BogeyRenderState state,boolean inContraption){
        var renderer=RENDERERS.get(new Key(style.id,size));if(renderer==null)throw new IllegalStateException("No native renderer for bogey style "+style.id+" size "+size.id());
        state.clear();state.wheelAngle=angle;renderer.extract(data.copy(),angle,partial,state,inContraption);
    }
    private BogeyRenderers(){}
}
