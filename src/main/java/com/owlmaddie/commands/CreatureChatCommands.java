package com.owlmaddie.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
                .then(registerSetCommand("key", "API Key", StringArgumentType.string()))
                .then(registerSetCommand("url", "URL", StringArgumentType.string()))
                .then(registerSetCommand("model", "Model", StringArgumentType.string()))
                .then(registerSetCommand("timeout", "Timeout (seconds)", IntegerArgumentType.integer()))
                .then(registerHelpCommand()));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> registerSetCommand(String settingName, String settingDescription, ArgumentType<?> valueType) {
        return CommandManager.literal(settingName)
                .requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("value", valueType)
                                .then(CommandManager.literal("--config")
                                        .then(CommandManager.literal("default")
                                                .executes(context -> {
                                                    if (valueType instanceof StringArgumentType)
                                                        return setConfig(context.getSource(), settingName, StringArgumentType.getString(context, "value"), false, settingDescription);
                                                    else if (valueType instanceof IntegerArgumentType)
                                                        return setConfig(context.getSource(), settingName, IntegerArgumentType.getInteger(context, "value"), false, settingDescription);
                                                    return 1;
                                                })
                                        )
                                        .then(CommandManager.literal("server")
                                                .executes(context -> {
                                                    if (valueType instanceof StringArgumentType)
                                                        return setConfig(context.getSource(), settingName, StringArgumentType.getString(context, "value"), true, settingDescription);
                                                    else if (valueType instanceof IntegerArgumentType)
                                                        return setConfig(context.getSource(), settingName, IntegerArgumentType.getInteger(context, "value"), true, settingDescription);
                                                    return 1;
                                                })
                                        )
                                )
                                .executes(context -> {
                                    if (valueType instanceof StringArgumentType)
                                        return setConfig(context.getSource(), settingName, StringArgumentType.getString(context, "value"), false, settingDescription);
                                    else if (valueType instanceof IntegerArgumentType)
                                        return setConfig(context.getSource(), settingName, IntegerArgumentType.getInteger(context, "value"), false, settingDescription);
                                    return 1;
                                })
                        ));
    }

    private static <T> int setConfig(ServerCommandSource source, String settingName, T value, boolean useServerConfig, String settingDescription) {
        ConfigurationHandler configHandler = new ConfigurationHandler(source.getServer());
        ConfigurationHandler.Config config = configHandler.loadConfig();
        try {
            switch (settingName) {
                case "key":
                    config.setApiKey((String) value);
                    break;
                case "url":
                    config.setUrl((String) value);
                    break;
                case "model":
                    config.setModel((String) value);
                    break;
                case "timeout":
                    if (value instanceof Integer) {
                        config.setTimeout((Integer) value);
                    } else {
                        throw new IllegalArgumentException("Invalid type for timeout, must be Integer.");
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown configuration setting: " + settingName);
            }
        } catch (ClassCastException e) {
            Text errorMessage = Text.literal("Invalid type for setting " + settingName).formatted(Formatting.RED);
            source.sendFeedback(() -> errorMessage, false);
            LOGGER.error("Type mismatch during configuration setting for: " + settingName, e);
            return 0;
        } catch (IllegalArgumentException e) {
            Text errorMessage = Text.literal(e.getMessage()).formatted(Formatting.RED);
            source.sendFeedback(() -> errorMessage, false);
            LOGGER.error("Error setting configuration: " + e.getMessage(), e);
            return 0;
        }

        Text feedbackMessage;
        if (configHandler.saveConfig(config, useServerConfig)) {
            // succeeded
            feedbackMessage = Text.literal(settingDescription + " Set Successfully!").formatted(Formatting.GREEN);
            source.sendFeedback(() -> feedbackMessage, false);
            LOGGER.info("Command executed: " + feedbackMessage.getString());
            return 1;
        } else {
            // failed
            feedbackMessage = Text.literal(settingDescription + " Set Failed!").formatted(Formatting.RED);
            source.sendFeedback(() -> feedbackMessage, false);
            LOGGER.info("Command executed: " + feedbackMessage.getString());
            return 0;
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> registerHelpCommand() {
        return CommandManager.literal("help")
                .executes(context -> {
                    String helpMessage = "Usage of CreatureChat Commands:\n"
                            + "/creaturechat key set <key> - Sets the API key\n"
                            + "/creaturechat url set \"<url>\" - Sets the URL\n"
                            + "/creaturechat model set <model> - Sets the model\n"
                            + "/creaturechat timeout set <seconds> - Sets the API timeout\n"
                            + "\n"
                            + "Optional: Append [--config default | server] to any command to specify configuration scope.\n"
                            + "\n"
                            + "Security: Level 4 permission required.";
                    context.getSource().sendFeedback(() -> Text.literal(helpMessage), false);
                    return 1;
                });
    }
}
