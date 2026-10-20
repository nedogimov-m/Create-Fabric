package com.simibubi.create.test.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import com.simibubi.create.test.CopycatNativeProbe;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;

@Mixin(CopycatModel.class)
public abstract class CopycatNativeModelProbeMixin {
    @Shadow @Final protected SpriteFinder finder;
    @WrapMethod(method="emitQuads")
    private void create$observeActualNativeGeometry(QuadEmitter out,BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cull,Operation<Void> original){
        var scope=CopycatNativeProbe.submission(level,pos);if(scope==null){original.call(out,level,pos,state,random,cull);return;}
        out.pushTransform(quad->{CopycatNativeProbe.quad(scope,quad,finder);return true;});
        try{original.call(out,level,pos,state,random,cull);}finally{out.popTransform();CopycatNativeProbe.finish(scope);}
    }
}
