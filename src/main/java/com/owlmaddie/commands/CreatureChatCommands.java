package com.owlmaddie.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.owlmaddie.network.ServerPackets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
                .then(registerStoryCommand())
                .then(registerWhitelistCommand())
                .then(registerBlacklistCommand())
                .then(registerChatBubbleCommand())
                .then(registerHelpCommand()));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> registerSetCommand(String settingName, String settingDescription, ArgumentType<?> valueType) {
        return CommandManager.literal(settingName)
                .requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("value", valueType)
                                .then(addConfigArgs((context, useServerConfig) -> {
                                    if (valueType instanceof StringArgumentType)
                                        return setConfig(context.getSource(), settingName, StringArgumentType.getString(context, "value"), useServerConfig, settingDescription);
                                    else if (valueType instanceof IntegerArgumentType)
                                        return setConfig(context.getSource(), settingName, IntegerArgumentType.getInteger(context, "value"), useServerConfig, settingDescription);
                                    return 1;
                                }))
                                .executes(context -> {
                                    if (valueType instanceof StringArgumentType)
                                        return setConfig(context.getSource(), settingName, StringArgumentType.getString(context, "value"), false, settingDescription);
                                    else if (valueType instanceof IntegerArgumentType)
                                        return setConfig(context.getSource(), settingName, IntegerArgumentType.getInteger(context, "value"), false, settingDescription);
                                    return 1;
                                })
                        ));
    }

    private static List<Identifier> getLivingEntityIds() {
        List<Identifier> livingEntityIds = Registries.ENTITY_TYPE.getIds().stream()
                .filter(id -> {
                    EntityType<?> entityType = Registries.ENTITY_TYPE.get(id);
                    return entityType != null && (entityType.getSpawnGroup() != SpawnGroup.MISC  || isIncludedEntity(entityType));
                })
                .collect(Collectors.toList());
        return livingEntityIds;
    }

    private static boolean isIncludedEntity(EntityType<?> entityType) {
        return entityType == EntityType.VILLAGER
                || entityType == EntityType.IRON_GOLEM
                || entityType == EntityType.SNOW_GOLEM;
    }

    private static List<String> getLivingEntityTypeNames() {
        return getLivingEntityIds().stream()
                .map(Identifier::toString)
                .collect(Collectors.toList());
    }
    private static LiteralArgumentBuilder<ServerCommandSource> registerChatBubbleCommand() {
        return CommandManager.literal("chatbubble")
                .requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.literal("set")
                        .then(CommandManager.literal("on")
                                .then(addConfigArgs((context, useServerConfig) -> setChatBubbleEnabled(context, true, useServerConfig)))
                                .executes(context -> setChatBubbleEnabled(context, true, false)))
                        .then(CommandManager.literal("off")
                                .then(addConfigArgs((context, useServerConfig) -> setChatBubbleEnabled(context, false, useServerConfig)))
                                .executes(context -> setChatBubbleEnabled(context, false, false))));
    }

    private static int setChatBubbleEnabled(CommandContext<ServerCommandSource> context, boolean enabled, boolean useServerConfig) {
        ServerCommandSource source = context.getSource();
        ConfigurationHandler configHandler = new ConfigurationHandler(source.getServer());
        ConfigurationHandler.Config config = configHandler.loadConfig();

        config.setChatBubbles(enabled);

        if (configHandler.saveConfig(config, useServerConfig)) {
            Text feedbackMessage = Text.literal("Player chat bubbles have been " + (enabled ? "enabled" : "disabled") + ".").formatted(Formatting.GREEN);
            source.sendFeedback(() -> feedbackMessage, true);
            return 1;
        } else {
            Text feedbackMessage = Text.literal("Failed to update player chat bubble setting.").formatted(Formatting.RED);
            source.sendFeedback(() -> feedbackMessage, false);
            return 0;
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> registerWhitelistCommand() {
        return CommandManager.literal("whitelist")
                .requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.argument("entityType", IdentifierArgumentType.identifier())
                        .suggests((context, builder) -> CommandSource.suggestIdentifiers(getLivingEntityIds(), builder))
                        .then(addConfigArgs((context, useServerConfig) -> modifyList(context, "whitelist", IdentifierArgumentType.getIdentifier(context, "entityType").toString(), useServerConfig)))
                        .executes(context -> modifyList(context, "whitelist", IdentifierArgumentType.getIdentifier(context, "entityType").toString(), false)))
                .then(CommandManager.literal("all")
                        .then(addConfigArgs((context, useServerConfig) -> modifyList(context, "whitelist", "all", useServerConfig)))
                        .executes(context -> modifyList(context, "whitelist", "all", false)))
                .then(CommandManager.literal("clear")
                        .then(addConfigArgs((context, useServerConfig) -> modifyList(context, "whitelist", "clear", useServerConfig)))
                        .executes(context -> modifyList(context, "whitelist", "clear", false)));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> registerBlacklistCommand() {
        return CommandManager.literal("blacklist")
                .requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.argument("entityType", IdentifierArgumentType.identifier())
                        .suggests((context, builder) -> CommandSource.suggestIdentifiers(getLivingEntityIds(), builder))
                        .then(addConfigArgs((context, useServerConfig) -> modifyList(context, "blacklist", IdentifierArgumentType.getIdentifier(context, "entityType").toString(), useServerConfig)))
                        .executes(context -> modifyList(context, "blacklist", IdentifierArgumentType.getIdentifier(context, "entityType").toString(), false)))
                .then(CommandManager.literal("all")
                        .then(addConfigArgs((context, useServerConfig) -> modifyList(context, "blacklist", "all", useServerConfig)))
                        .executes(context -> modifyList(context, "blacklist", "all", false)))
                .then(CommandManager.literal("clear")
                        .then(addConfigArgs((context, useServerConfig) -> modifyList(context, "blacklist", "clear", useServerConfig)))
                        .executes(context -> modifyList(context, "blacklist", "clear", false)));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> registerHelpCommand() {
        return CommandManager.literal("help")
                .executes(context -> {
                    String helpMessage = "Usage of CreatureChat Commands:\n"
                            + "/creaturechat key set <key> - Sets the API key\n"
                            + "/creaturechat url set \"<url>\" - Sets the URL\n"
                            + "/creaturechat model set <model> - Sets the model\n"
                            + "/creaturechat timeout set <seconds> - Sets the API timeout\n"
                            + "/creaturechat story set \"<story>\" - Sets a custom story\n"
                            + "/creaturechat chatbubbles set <on | off> - Show player chat bubbles\n"
                            + "/creaturechat whitelist <entityType | all | clear> - Show chat bubbles\n"
                            + "/creaturechat blacklist <entityType | all | clear> - Hide chat bubbles\n"
                            + "\n"
                            + "Optional: Append [--config default | server] to any command to specify configuration scope.\n"
                            + "\n"
                            + "Security: Level 4 permission required.";
                    context.getSource().sendFeedback(() -> Text.literal(helpMessage), false);
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> registerStoryCommand() {
        return CommandManager.literal("story")
                .requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("value", StringArgumentType.string())
                                .then(addConfigArgs((context, useServerConfig) -> {
                                    String story = StringArgumentType.getString(context, "value");
                                    ConfigurationHandler.Config config = new ConfigurationHandler(context.getSource().getServer()).loadConfig();
                                    config.setStory(story);
                                    if (new ConfigurationHandler(context.getSource().getServer()).saveConfig(config, useServerConfig)) {
                                        context.getSource().sendFeedback(() -> Text.literal("Story set successfully: " + story).formatted(Formatting.GREEN), true);
                                        return 1;
                                    } else {
                                        context.getSource().sendFeedback(() -> Text.literal("Failed to set story!").formatted(Formatting.RED), false);
                                        return 0;
                                    }
                                }))))
                .then(CommandManager.literal("clear")
                        .then(addConfigArgs((context, useServerConfig) -> {
                            ConfigurationHandler.Config config = new ConfigurationHandler(context.getSource().getServer()).loadConfig();
                            config.setStory("");
                            if (new ConfigurationHandler(context.getSource().getServer()).saveConfig(config, useServerConfig)) {
                                context.getSource().sendFeedback(() -> Text.literal("Story cleared successfully!").formatted(Formatting.GREEN), true);
                                return 1;
                            } else {
                                context.getSource().sendFeedback(() -> Text.literal("Failed to clear story!").formatted(Formatting.RED), false);
                                return 0;
                            }
                        })))
                .then(CommandManager.literal("display")
                .executes(context -> {
                    ConfigurationHandler.Config config = new ConfigurationHandler(context.getSource().getServer()).loadConfig();
                    String story = config.getStory();
                    if (story == null || story.isEmpty()) {
                        context.getSource().sendFeedback(() -> Text.literal("No story is currently set.").formatted(Formatting.RED), false);
                        return 0;
                    } else {
                        context.getSource().sendFeedback(() -> Text.literal("Current story: " + story).formatted(Formatting.AQUA), false);
                        return 1;
                    }
                }));
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
            feedbackMessage = Text.literal(settingDescription + " Set Successfully!").formatted(Formatting.GREEN);
            source.sendFeedback(() -> feedbackMessage, false);
            LOGGER.info("Command executed: " + feedbackMessage.getString());
            return 1;
        } else {
            feedbackMessage = Text.literal(settingDescription + " Set Failed!").formatted(Formatting.RED);
            source.sendFeedback(() -> feedbackMessage, false);
            LOGGER.info("Command executed: " + feedbackMessage.getString());
            return 0;
        }
    }

    private static int modifyList(CommandContext<ServerCommandSource> context, String listName, String action, boolean useServerConfig) {
        ServerCommandSource source = context.getSource();
        ConfigurationHandler configHandler = new ConfigurationHandler(source.getServer());
        ConfigurationHandler.Config config = configHandler.loadConfig();
        List<String> entityTypes = getLivingEntityTypeNames();

        try {
            if ("all".equals(action)) {
                if ("whitelist".equals(listName)) {
                    config.setWhitelist(entityTypes);
                    config.setBlacklist(new ArrayList<>()); // Clear blacklist
                } else if ("blacklist".equals(listName)) {
                    config.setBlacklist(entityTypes);
                    config.setWhitelist(new ArrayList<>()); // Clear whitelist
                }
            } else if ("clear".equals(action)) {
                if ("whitelist".equals(listName)) {
                    config.setWhitelist(new ArrayList<>());
                } else if ("blacklist".equals(listName)) {
                    config.setBlacklist(new ArrayList<>());
                }
            } else {
                if (!entityTypes.contains(action)) {
                    throw new IllegalArgumentException("Invalid entity type: " + action);
                }
                if ("whitelist".equals(listName)) {
                    List<String> whitelist = new ArrayList<>(config.getWhitelist());
                    if (!whitelist.contains(action)) {
                        whitelist.add(action);
                        config.setWhitelist(whitelist);
                    }
                    // Remove from blacklist if present
                    List<String> blacklist = new ArrayList<>(config.getBlacklist());
                    blacklist.remove(action);
                    config.setBlacklist(blacklist);
                } else if ("blacklist".equals(listName)) {
                    List<String> blacklist = new ArrayList<>(config.getBlacklist());
                    if (!blacklist.contains(action)) {
                        blacklist.add(action);
                        config.setBlacklist(blacklist);
                    }
                    // Remove from whitelist if present
                    List<String> whitelist = new ArrayList<>(config.getWhitelist());
                    whitelist.remove(action);
                    config.setWhitelist(whitelist);
                }
            }
        } catch (IllegalArgumentException e) {
            Text errorMessage = Text.literal(e.getMessage()).formatted(Formatting.RED);
            source.sendFeedback(() -> errorMessage, false);
            LOGGER.error("Error modifying list: " + e.getMessage(), e);
            return 0;
        }

        if (configHandler.saveConfig(config, useServerConfig)) {
            Text feedbackMessage = Text.literal("Successfully updated " + listName + " with " + action).formatted(Formatting.GREEN);
            source.sendFeedback(() -> feedbackMessage, false);

            // Send whitelist / blacklist to all players
            ServerPackets.send_whitelist_blacklist(null);
            return 1;
        } else {
            Text feedbackMessage = Text.literal("Failed to update " + listName).formatted(Formatting.RED);
            source.sendFeedback(() -> feedbackMessage, false);
            return 0;
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> addConfigArgs(CommandExecutor executor) {
        return CommandManager.literal("--config")
                .then(CommandManager.literal("default").executes(context -> executor.run(context, false)))
                .then(CommandManager.literal("server").executes(context -> executor.run(context, true)))
                .executes(context -> executor.run(context, false));
    }

    @FunctionalInterface
    private interface CommandExecutor {
        int run(CommandContext<ServerCommandSource> context, boolean useServerConfig) throws CommandSyntaxException;
    }
}
