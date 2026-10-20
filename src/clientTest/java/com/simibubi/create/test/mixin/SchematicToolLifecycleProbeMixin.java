package com.simibubi.create.test.mixin;

import com.simibubi.create.content.schematics.client.tools.SchematicToolBase;
import com.simibubi.create.test.SchematicPublicToolProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=SchematicToolBase.class,remap=false)
abstract class SchematicToolLifecycleProbeMixin {
    @Inject(method="init",at=@At("RETURN")) private void createTest$init(CallbackInfo ci){SchematicPublicToolProbe.observe(this,"init");}
    @Inject(method="updateSelection",at=@At("RETURN")) private void createTest$selection(CallbackInfo ci){SchematicPublicToolProbe.observe(this,"updateSelection");}
    @Inject(method="extractOnSchematic",at=@At("HEAD")) private void createTest$geometry(CallbackInfo ci){SchematicPublicToolProbe.observe(this,"extractOnSchematic");}
}
