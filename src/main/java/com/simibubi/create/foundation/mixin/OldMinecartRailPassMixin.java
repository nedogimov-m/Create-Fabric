package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.contraptions.minecart.NativeMinecartHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OldMinecartBehavior.class)
public abstract class OldMinecartRailPassMixin extends MinecartBehavior {
    @Unique private BlockPos create$actualTraversedRail;
    protected OldMinecartRailPassMixin(AbstractMinecart cart){super(cart);}
    @Inject(method="moveAlongTrack",at=@At("HEAD"))
    private void create$rememberActualRail(ServerLevel world,CallbackInfo ci){create$actualTraversedRail=minecart.getCurrentBlockPosOrRailBelow();}
    @Inject(method="moveAlongTrack",at=@At("RETURN"))
    private void create$sourceRailPass(ServerLevel world,CallbackInfo ci){if(create$actualTraversedRail!=null){var pos=create$actualTraversedRail;create$actualTraversedRail=null;NativeMinecartHooks.railPass(minecart,pos);}}
}
