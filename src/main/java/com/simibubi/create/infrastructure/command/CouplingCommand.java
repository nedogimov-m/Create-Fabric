package com.simibubi.create.infrastructure.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.exceptions.*;
import com.simibubi.create.content.contraptions.minecart.CouplingHandler;
import com.simibubi.create.content.contraptions.minecart.capability.MinecartController;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

/** Source operator surface using native permission checks and real durable cart/receipt topology. */
public final class CouplingCommand {
    private static final SimpleCommandExceptionType ONLY_CARTS=new SimpleCommandExceptionType(Component.literal("Only Minecarts can be coupled"));
    private static final SimpleCommandExceptionType SAME_DIMENSION=new SimpleCommandExceptionType(Component.literal("Minecarts have to be in the same Dimension"));
    private static final DynamicCommandExceptionType TWO_CARTS=new DynamicCommandExceptionType(count->Component.literal("Your selector targeted "+count+" entities. You can only couple 2 Minecarts at a time."));
    public static void register(){CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->dispatcher.register(Commands.literal("create").then(tree())));}
    public static ArgumentBuilder<CommandSourceStack,?> tree(){
        return Commands.literal("coupling").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("add")
                .then(Commands.argument("cart1",EntityArgument.entity()).then(Commands.argument("cart2",EntityArgument.entity()).executes(ctx->add(ctx.getSource(),EntityArgument.getEntity(ctx,"cart1"),EntityArgument.getEntity(ctx,"cart2")))))
                .then(Commands.argument("carts",EntityArgument.entities()).executes(ctx->{var values=List.copyOf(EntityArgument.getEntities(ctx,"carts"));if(values.size()!=2)throw TWO_CARTS.create(values.size());return add(ctx.getSource(),values.get(0),values.get(1));})))
            .then(Commands.literal("remove").then(Commands.argument("cart1",EntityArgument.entity()).then(Commands.argument("cart2",EntityArgument.entity()).executes(ctx->{var first=cart(EntityArgument.getEntity(ctx,"cart1"));var second=cart(EntityArgument.getEntity(ctx,"cart2"));sameDimension(first,second);return removed(ctx.getSource(),CouplingHandler.detachByCommand(ctx.getSource(),MinecartController.get(first),second.getUUID()));}))))
            .then(Commands.literal("removeAll").then(Commands.argument("cart",EntityArgument.entity()).executes(ctx->removed(ctx.getSource(),CouplingHandler.detachByCommand(ctx.getSource(),MinecartController.get(cart(EntityArgument.getEntity(ctx,"cart"))),null)))));
    }
    private static AbstractMinecart cart(Entity actual)throws CommandSyntaxException{if(actual instanceof AbstractMinecart cart)return cart;throw ONLY_CARTS.create();}
    private static void sameDimension(AbstractMinecart first,AbstractMinecart second)throws CommandSyntaxException{if(first.level()!=second.level())throw SAME_DIMENSION.create();}
    private static int add(CommandSourceStack source,Entity first,Entity second)throws CommandSyntaxException{var a=cart(first);var b=cart(second);sameDimension(a,b);return CouplingHandler.tryToCoupleCartsByCommand(source,a,b)?1:0;}
    private static int removed(CommandSourceStack source,boolean removed){source.sendSuccess(()->Component.literal(removed?"Removed Minecart Couplings":"The specified Carts have no removable loaded Couplings"),true);return removed?1:0;}
    private CouplingCommand(){}
}
