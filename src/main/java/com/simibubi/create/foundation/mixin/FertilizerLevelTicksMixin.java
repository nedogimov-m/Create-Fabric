package com.simibubi.create.foundation.mixin;
import com.simibubi.create.content.equipment.TreeFertilizerSimulation;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Native leaf/root shape updates discard only this exact active dream's scheduled tick effects. */
@Mixin(value=LevelTicks.class,priority=2000)
abstract class FertilizerLevelTicksMixin {
 @Inject(method="schedule",at=@At("HEAD"),cancellable=true)private void create$noDreamSchedule(CallbackInfo ci){if(TreeFertilizerSimulation.ownsTicks(this))ci.cancel();}
 @Inject(method={"hasScheduledTick","willTickThisTick"},at=@At("HEAD"),cancellable=true,require=2)private void create$noDreamTickQuery(CallbackInfoReturnable<Boolean> ci){if(TreeFertilizerSimulation.ownsTicks(this))ci.setReturnValue(false);}
}
