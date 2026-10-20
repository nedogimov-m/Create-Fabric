package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.trains.entity.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Original railway RootVehicle resolves existing canonical objects, before vanilla can clone its snapshot. */
@Mixin(ServerPlayer.class)
public abstract class NeoForgeRailwayPlayerVehicleMixin implements NeoForgeRailwayPlayerReference.Holder {
    @Unique private NeoForgeRailwayPlayerReference create$originalRailwayReference;
    @Override public NeoForgeRailwayPlayerReference create$getOriginalRailwayReference(){return create$originalRailwayReference;}
    @Override public void create$setOriginalRailwayReference(NeoForgeRailwayPlayerReference value){create$originalRailwayReference=value;}
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void create$readOriginalRailwayReference(ValueInput input,CallbackInfo callback){
        create$originalRailwayReference=input.read(NeoForgeRailwayPlayerReference.KEY,NeoForgeRailwayPlayerReference.CODEC).orElse(null);
        if(create$originalRailwayReference!=null&&!create$originalRailwayReference.player().equals(((ServerPlayer)(Object)this).getUUID()))throw new IllegalArgumentException("Original railway reference belongs to another actual player file");
    }
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void create$savePendingOriginalRailwayReference(ValueOutput output,CallbackInfo callback){if(create$originalRailwayReference!=null)output.store(NeoForgeRailwayPlayerReference.KEY,NeoForgeRailwayPlayerReference.CODEC,create$originalRailwayReference);}
    @Inject(method="loadAndSpawnParentVehicle",at=@At("HEAD"),cancellable=true)
    private void create$resolveActualCanonicalRailwayBeforeVanillaSnapshot(ValueInput input,CallbackInfo callback){if(NeoForgeRailwayPlayerReference.resumeActualPlayer((ServerPlayer)(Object)this))callback.cancel();}
    @Inject(method="tick",at=@At("TAIL"))
    private void create$retryOnlyActualCurrentPlayerRelationship(CallbackInfo callback){NeoForgeRailwayPlayerReference.resumeActualPlayer((ServerPlayer)(Object)this);}
    @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",at=@At("HEAD"))
    private void create$supersedePendingOriginalRailwayTeleport(net.minecraft.server.level.ServerLevel target,double x,double y,double z,java.util.Set<net.minecraft.world.entity.Relative> relative,float yaw,float pitch,boolean camera,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> callback){NeoForgeRailwayPlayerReference.supersedeForActualAction((ServerPlayer)(Object)this);}
    @Inject(method="teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",at=@At("HEAD"))
    private void create$supersedePendingOriginalRailwayTransition(net.minecraft.world.level.portal.TeleportTransition transition,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<ServerPlayer> callback){NeoForgeRailwayPlayerReference.supersedeForActualAction((ServerPlayer)(Object)this);}
    @Inject(method="teleportTo(DDD)V",at=@At("HEAD"))
    private void create$supersedePendingOriginalRailwaySameLevelTeleport(double x,double y,double z,CallbackInfo callback){NeoForgeRailwayPlayerReference.supersedeForActualAction((ServerPlayer)(Object)this);}
    @Inject(method="teleportRelative(DDD)V",at=@At("HEAD"))
    private void create$supersedePendingOriginalRailwayRelativeTeleport(double x,double y,double z,CallbackInfo callback){NeoForgeRailwayPlayerReference.supersedeForActualAction((ServerPlayer)(Object)this);}
    @Inject(method="saveParentVehicle",at=@At("HEAD"),cancellable=true)
    private void create$doNotDuplicateCanonicalRailwayBodyIntoPlayerFile(ValueOutput output,CallbackInfo callback){if(((ServerPlayer)(Object)this).getRootVehicle() instanceof CarriageContraptionEntity front&&front.isGloballyManaged())callback.cancel();}
}
