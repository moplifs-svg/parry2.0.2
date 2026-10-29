package com.parrymod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;

/** /parry <player> and /unparry <player> (operators only). */
@Mod.EventBusSubscriber(modid = ParryMod.MODID)
public class ParryCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("parry")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.players())
                        .executes(ctx -> set(ctx, true))));

        dispatcher.register(Commands.literal("unparry")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.players())
                        .executes(ctx -> set(ctx, false))));
    }

    private static int set(CommandContext<CommandSourceStack> ctx, boolean allow) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "player");
        ParryPermissions permissions = ParryPermissions.get(ctx.getSource().getServer());

        for (ServerPlayer target : targets) {
            permissions.setAllowed(target.getUUID(), allow);
            if (!allow) {
                ParryHandler.clearState(target);
            }
        }

        String names = targets.size() == 1
                ? targets.iterator().next().getName().getString()
                : targets.size() + " players";
        ctx.getSource().sendSuccess(() -> Component.literal(
                (allow ? "Allowed " : "Removed parry from ") + names), true);
        return targets.size();
    }
}
