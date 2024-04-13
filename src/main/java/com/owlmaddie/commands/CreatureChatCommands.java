package com.owlmaddie.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * The {@code CreatureChatCommands} class registers custom commands to set new API key, model, and url.
 * Permission level set to 4 (server owner), since this deals with API keys and potential costs.
 */
public class CreatureChatCommands {

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            CommandDispatcher<ServerCommandSource> dispatcher = server.getCommandManager().getDispatcher();
            registerBaseCommand(dispatcher);
        });
    }

    private static void registerBaseCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("creaturechat")
                .then(registerSetKeyCommand())
                .then(registerSetUrlCommand())
                .then(registerSetModelCommand())
                .then(registerHelpCommand()));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> registerSetKeyCommand() {
        return CommandManager.literal("key").then(CommandManager.literal("set").then(CommandManager.argument("key", StringArgumentType.word())
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    String key = StringArgumentType.getString(context, "key");
                    context.getSource().sendFeedback(() -> Text.literal("API key set to: " + key), false);
                    return 1;
                })));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> registerSetUrlCommand() {
        return CommandManager.literal("url").then(CommandManager.literal("set").then(CommandManager.argument("url", StringArgumentType.string())
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    String url = StringArgumentType.getString(context, "url");
                    context.getSource().sendFeedback(() -> Text.literal("URL set to: " + url), false);
                    return 1;
                })));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> registerSetModelCommand() {
        return CommandManager.literal("model").then(CommandManager.literal("set").then(CommandManager.argument("model", StringArgumentType.word())
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    String model = StringArgumentType.getString(context, "model");
                    context.getSource().sendFeedback(() -> Text.literal("Model set to: " + model), false);
                    return 1;
                })));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> registerHelpCommand() {
        return CommandManager.literal("help")
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    context.getSource().sendFeedback(() -> Text.literal("Usage:\n/creaturechat key set <key>\n/creaturechat url set <url>\n/creaturechat model set <model>"), false);
                    return 1;
                });
    }
}
