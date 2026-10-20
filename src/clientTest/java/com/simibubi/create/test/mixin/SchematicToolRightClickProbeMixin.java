package com.simibubi.create.test.mixin;

import com.simibubi.create.content.schematics.client.tools.*;
import com.simibubi.create.test.SchematicPublicToolProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={DeployTool.class,FlipTool.class,PlaceTool.class},remap=false)
abstract class SchematicToolRightClickProbeMixin {
    @Inject(method="handleRightClick",at=@At("HEAD")) private void createTest$rightClick(CallbackInfoReturnable<Boolean> ci){SchematicPublicToolProbe.observe(this,"handleRightClick");}
}
