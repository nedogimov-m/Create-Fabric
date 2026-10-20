// Ponder1.0.82 command semantics, MIT; native26.3 permission sets and typed S2C.
package net.createmod.ponder.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.server.level.ServerPlayer;

public final class PonderCommand {
    static ArgumentBuilder<CommandSourceStack,?> register(){
        return Commands.literal("ponder").requires(Commands.hasPermission(Commands.LEVEL_ALL))
            .executes(ctx->openScene("ponder:tags",List.of(ctx.getSource().getPlayerOrException())))
            .then(Commands.literal("reload").executes(ctx->{ServerPlayNetworking.send(ctx.getSource().getPlayerOrException(),PonderActionPayload.reload());return Command.SINGLE_SUCCESS;}))
            .then(Commands.literal("index").executes(ctx->openScene("ponder:index",List.of(ctx.getSource().getPlayerOrException()))))
            .then(Commands.literal("tags").executes(ctx->openScene("ponder:tags",List.of(ctx.getSource().getPlayerOrException()))))
            .then(Commands.argument("scene",IdentifierArgument.id())
                .executes(ctx->openScene(IdentifierArgument.getId(ctx,"scene").toString(),List.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets",EntityArgument.players()).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(ctx->openScene(IdentifierArgument.getId(ctx,"scene").toString(),EntityArgument.getPlayers(ctx,"targets")))));
    }
    private static int openScene(String scene,Collection<? extends ServerPlayer> players){
        var action=PonderActionPayload.open(scene);
        for(var player:players){if(player instanceof FakePlayer)continue;ServerPlayNetworking.send(player,action);}
        return Command.SINGLE_SUCCESS;
    }
    private PonderCommand(){}
}
