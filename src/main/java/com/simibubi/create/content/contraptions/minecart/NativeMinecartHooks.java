package com.simibubi.create.content.contraptions.minecart;

import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.minecart.capability.*;
import com.simibubi.create.content.contraptions.mounted.CartAssemblerBlock;
import com.simibubi.create.content.redstone.rail.ControllerRailBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.Blocks;

/** Actual inspected native Old/New behavior entry points; no removed Forge rail callbacks are assumed. */
public final class NativeMinecartHooks {
    public static void preTick(AbstractMinecart cart){
        var controller=MinecartController.get(cart);if(controller.isEmpty())return;controller.tick();
        if(cart.level().isClientSide()||cart.isRideable()||controller.ownsPackedReceipt())return;
        var pos=cart.getCurrentBlockPosOrRailBelow();if(!LoadedLevelAccess.isLoaded(cart.level(),pos)||!LoadedLevelAccess.blockState(cart.level(),pos).is(Blocks.ACTIVATOR_RAIL))return;
        // The pinned controller tests actual activator rail identity without a POWERED predicate.
        if(cart.getFirstPassenger() instanceof OrientedContraptionEntity root&&root.disassemble()&&cart.getHurtTime()==0){cart.setHurtDir(-cart.getHurtDir());cart.setHurtTime(10);cart.setDamage(50);}
    }
    public static boolean railPass(AbstractMinecart cart,BlockPos actualRail){
        if(cart.level().isClientSide()||!LoadedLevelAccess.isLoaded(cart.level(),actualRail))return false;
        var state=LoadedLevelAccess.blockState(cart.level(),actualRail);
        if(state.getBlock() instanceof ControllerRailBlock controller){controller.onMinecartPass(state,cart.level(),actualRail,cart);return true;}
        if(state.getBlock() instanceof CartAssemblerBlock assembler)assembler.onMinecartPass(state,cart.level(),actualRail,cart);
        return false;
    }
    /** New behavior's real per-iteration computed speed, before its caller installs native motion. */
    public static net.minecraft.world.phys.Vec3 railSpeed(AbstractMinecart cart,BlockPos actualRail,net.minecraft.world.phys.Vec3 computed){
        if(cart.level().isClientSide()||!LoadedLevelAccess.isLoaded(cart.level(),actualRail))return computed;
        var state=LoadedLevelAccess.blockState(cart.level(),actualRail);
        if(state.getBlock() instanceof ControllerRailBlock controller){cart.setDeltaMovement(computed);controller.onMinecartPass(state,cart.level(),actualRail,cart);return cart.getDeltaMovement();}
        if(state.getBlock() instanceof CartAssemblerBlock assembler)assembler.onMinecartPass(state,cart.level(),actualRail,cart);
        return computed;
    }
    private NativeMinecartHooks(){}
}
