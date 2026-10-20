package com.simibubi.create.content.decoration.slidingDoor;

import com.simibubi.create.Create;
import java.util.*;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;

/** Folding halves are two-block-tall native baked models, reloaded with the resource pack. */
public final class SlidingDoorModels {
    public record Halves(ExtraModelKey<BlockStateModel> left,ExtraModelKey<BlockStateModel> right){}
    private static final Map<String,Halves> FOLDING=new LinkedHashMap<>();
    static {for(var name:List.of("andesite_door","copper_door"))FOLDING.put(name,new Halves(key(name+"/fold_left"),key(name+"/fold_right")));}
    private static ExtraModelKey<BlockStateModel> key(String path){return ExtraModelKey.create(()->"create:block/"+path);}
    public static Halves get(String name){return FOLDING.get(name);}
    public static void register(){ModelLoadingPlugin.register(context->FOLDING.forEach((name,halves)->{context.addModel(halves.left,SimpleUnbakedExtraModel.blockStateModel(Create.asResource("block/"+name+"/fold_left")));context.addModel(halves.right,SimpleUnbakedExtraModel.blockStateModel(Create.asResource("block/"+name+"/fold_right")));}));}
    private SlidingDoorModels(){}
}
