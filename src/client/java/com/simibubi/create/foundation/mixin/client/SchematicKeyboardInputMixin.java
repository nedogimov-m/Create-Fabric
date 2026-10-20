package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.schematics.client.SchematicsClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Full native key event after vanilla key mapping state, with tick polling as the focus fallback. */
@Mixin(KeyboardHandler.class)
public abstract class SchematicKeyboardInputMixin {
    @Inject(method="keyPress",at=@At("RETURN"))
    private void create$schematicMenuKey(long window,int action,KeyEvent key,CallbackInfo callback){
        if(window!=0&&Minecraft.getInstance().getWindow().handle()==window)SchematicsClient.HANDLER.onKeyInput(key,action!=0);
    }
}
