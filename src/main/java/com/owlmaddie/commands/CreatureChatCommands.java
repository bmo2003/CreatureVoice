package com.owlmaddie.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code CreatureChatCommands} class registers custom commands to set new API key, model, and url.
 * Permission level set to 4 (server owner), since this deals with API keys and potential costs.
 */
public class CreatureChatCommands {
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            CommandDispatcher<ServerCommandSource> dispatcher = server.getCommandManager().getDispatcher();
            registerCommands(dispatcher);
        });
    }

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("creaturechat")
                .then(registerSetCommand("key", "API key"))
                .then(registerSetCommand("url", "URL"))
                .then(registerSetCommand("model", "model"))
                .then(registerHelpCommand()));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> registerSetCommand(String settingName, String settingDescription) {
        return CommandManager.literal(settingName)
                .requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("value", StringArgumentType.string())
                                .then(CommandManager.literal("--config")
                                        .then(CommandManager.literal("default")
                                                .executes(context -> setConfig(context.getSource(), settingName, StringArgumentType.getString(context, "value"), false, settingDescription)))
                                        .then(CommandManager.literal("server")
                                                .executes(context -> setConfig(context.getSource(), settingName, StringArgumentType.getString(context, "value"), true, settingDescription)))
                                )
                                .executes(context -> setConfig(context.getSource(), settingName, StringArgumentType.getString(context, "value"), false, settingDescription)) // Default to server if not specified
                        ));
    }

    private static int setConfig(ServerCommandSource source, String settingName, String value, boolean useServerConfig, String settingDescription) {
        ConfigurationHandler configHandler = new ConfigurationHandler(source.getServer());
        ConfigurationHandler.Config config = configHandler.loadConfig();
        switch (settingName) {
            case "key":
                config.setApiKey(value);
                break;
            case "url":
                config.setUrl(value);
                break;
            case "model":
                config.setModel(value);
                break;
        }
        configHandler.saveConfig(config, useServerConfig);

        String playerName = source.getName();
        Text feedbackMessage = Text.literal(settingDescription + " set to: " + value + " in " + (useServerConfig ? "server" : "default") + " configuration by " + playerName);
        source.sendFeedback(() -> feedbackMessage, false);
        LOGGER.info("Command executed: " + feedbackMessage.getLiteralString());
        return 1;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> registerHelpCommand() {
        return CommandManager.literal("help")
                .executes(context -> {
                    String helpMessage = "Usage of CreatureChat Commands:\n"
                            + "/creaturechat key set <key> - Sets the API key.\n"
                            + "/creaturechat url set <url> - Sets the URL.\n"
                            + "/creaturechat model set <model> - Sets the model.\n"
                            + "\n"
                            + "Optional: Append [--config default | server] to any command to specify configuration scope. If --config is not specified, 'default' is assumed'.\n"
                            + "\n"
                            + "Security: Level 4 permission required.";
                    context.getSource().sendFeedback(() -> Text.literal(helpMessage), false);
                    return 1;
                });
    }
}
