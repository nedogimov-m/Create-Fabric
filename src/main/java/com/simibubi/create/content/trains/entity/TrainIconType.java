package com.simibubi.create.content.trains.entity;

import com.simibubi.create.Create;
import io.netty.buffer.ByteBuf;
import java.util.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** Source public train icon registry; actual drawing lives in the native client renderer. */
public final class TrainIconType {
    public static final Map<Identifier,TrainIconType> REGISTRY=new LinkedHashMap<>();
    public static final StreamCodec<ByteBuf,TrainIconType> STREAM_CODEC=Identifier.STREAM_CODEC.map(TrainIconType::byId,TrainIconType::getId);
    public static final int ENGINE=-1,FLIPPED_ENGINE=-2;
    static{var sheet=Create.asResource("textures/gui/assemble.png");register(Create.asResource("traditional"),sheet,2,205);register(Create.asResource("electric"),sheet,2,216);register(Create.asResource("modern"),sheet,2,227);}
    private final Identifier id,sheet;
    private final int x,y;
    public TrainIconType(Identifier id,Identifier sheet,int x,int y){this.id=Objects.requireNonNull(id);this.sheet=Objects.requireNonNull(sheet);this.x=x;this.y=y;}
    public static void register(Identifier id,Identifier sheet,int x,int y){REGISTRY.put(id,new TrainIconType(id,sheet,x,y));}
    public static TrainIconType byId(Identifier id){return REGISTRY.getOrDefault(id,getDefault());}
    public static TrainIconType getDefault(){return REGISTRY.get(Create.asResource("traditional"));}
    public Identifier getId(){return id;}
    public Identifier sheet(){return sheet;}
    public int x(){return x;}
    public int y(){return y;}
    public int getIconWidth(int lengthOrEngine){return lengthOrEngine==ENGINE||lengthOrEngine==FLIPPED_ENGINE||lengthOrEngine>=9?19:lengthOrEngine<3?7:13;}
    public int getIconOffset(int lengthOrEngine){return lengthOrEngine==FLIPPED_ENGINE?0:lengthOrEngine==ENGINE?62:lengthOrEngine<3?34:lengthOrEngine<9?20:42;}
}
