package com.simibubi.create.test.mixin;

import com.simibubi.create.content.schematics.client.tools.*;
import com.simibubi.create.test.SchematicPublicToolProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={DeployTool.class,MoveTool.class,MoveVerticalTool.class,RotateTool.class,FlipTool.class,PlaceTool.class},remap=false)
abstract class SchematicToolWheelProbeMixin {
    @Inject(method="handleMouseWheel",at=@At("HEAD")) private void createTest$wheel(double delta,CallbackInfoReturnable<Boolean> ci){SchematicPublicToolProbe.observe(this,"handleMouseWheel");}
}
