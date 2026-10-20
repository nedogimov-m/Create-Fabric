// Ponder1.0.82 command surface, MIT; native26.3 Fabric registration.
package net.createmod.ponder.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;

public final class PonderCommands {
    private static boolean registered;
    public static void register(){
        if(registered)return;registered=true;PonderActionPayload.register();
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->register(dispatcher));
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){dispatcher.getRoot().addChild(PonderCommand.register().build());}
    private PonderCommands(){}
}
