package com.owlmaddie.chat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.owlmaddie.controls.SpeedControls;
import com.owlmaddie.goals.*;
import com.owlmaddie.items.RarityItemCollector;
import com.owlmaddie.json.QuestJson;
import com.owlmaddie.message.Behavior;
import com.owlmaddie.message.MessageParser;
import com.owlmaddie.message.ParsedMessage;
import com.owlmaddie.network.ServerPackets;
import com.owlmaddie.utils.LivingEntityInterface;
import com.owlmaddie.utils.ServerEntityFinder;
import com.owlmaddie.utils.Randomizer;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Rarity;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The {@code ChatDataManager} class manages chat data for all entities. This class also helps
 * generate new messages, set entity goals, and other useful chat-related functions.
 */
public class ChatDataManager {
    // Use a static instance to manage our data globally
    private static final ChatDataManager SERVER_INSTANCE = new ChatDataManager(true);
    private static final ChatDataManager CLIENT_INSTANCE = new ChatDataManager(false);
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");
    public static int MAX_CHAR_PER_LINE = 20;
    public static int DISPLAY_NUM_LINES = 3;
    public static int MAX_CHAR_IN_USER_MESSAGE = 512;
    public static int TICKS_TO_DISPLAY_USER_MESSAGE = 70;
    public QuestJson quest = null;
    private static final Gson GSON = new Gson();

    public enum ChatStatus {
        NONE,       // No chat status yet
        PENDING,    // Chat is pending (e.g., awaiting response or processing)
        DISPLAY,    // Chat is currently being displayed
        HIDDEN,     // Chat is currently hidden
    }

    public enum ChatSender {
        USER,      // A user chat message
        ASSISTANT  // A GPT generated message
    }

    // HashMap to associate unique entity IDs with their chat data
    public HashMap<String, EntityChatData> entityChatDataMap;

    public static class ChatMessage {
        public String message;
        public ChatSender sender;

        public ChatMessage(String message, ChatSender sender) {
            this.message = message;
            this.sender = sender;
        }
    }

    // Inner class to hold entity-specific data
    public static class EntityChatData {
        public String entityId;
        public String playerId;
        public String currentMessage;
        public int currentLineNumber;
        public ChatStatus status;
        public List<ChatMessage> previousMessages;
        public String characterSheet;
        public ChatSender sender;
        public int friendship; // -3 to 3 (0 = neutral)
        public boolean auto_generated;

        public EntityChatData(String entityId, String playerId) {
            this.entityId = entityId;
            this.playerId = playerId;
            this.currentMessage = "";
            this.currentLineNumber = 0;
            this.previousMessages = new ArrayList<>();
            this.characterSheet = "";
            this.status = ChatStatus.NONE;
            this.sender = ChatSender.USER;
            this.friendship = 0;
            this.auto_generated = false;
        }

        // Light version with no 'previousMessages' attribute
        public class EntityChatDataLight {
            public String entityId;
            public String currentMessage;
            public int currentLineNumber;
            public ChatStatus status;
            public ChatSender sender;
            public int friendship;
        }

        // Generate light version of chat data (no previous messages)
        public EntityChatDataLight toLightVersion() {
            EntityChatDataLight light = new EntityChatDataLight();
            light.entityId = this.entityId;
            light.currentMessage = this.currentMessage;
            light.currentLineNumber = this.currentLineNumber;
            light.status = this.status;
            light.sender = this.sender;
            light.friendship = this.friendship;
            return light;
        }

        public String getCharacterProp(String propertyName) {
            // Create a case-insensitive regex pattern to match the property name and capture its value
            Pattern pattern = Pattern.compile("-?\\s*" + Pattern.quote(propertyName) + ":\\s*(.+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(characterSheet);

            if (matcher.find()) {
                // Return the captured value, trimmed of any excess whitespace
                return matcher.group(1).trim().replace("\"", "");
            }

            return "N/A";
        }

        // Generate context object
        public Map<String, String> getPlayerContext(ServerPlayerEntity player) {
            // Add PLAYER context information
            Map<String, String> contextData = new HashMap<>();
            contextData.put("player_name", player.getDisplayName().getString());
            contextData.put("player_health", player.getHealth() + "/" + player.getMaxHealth());
            contextData.put("player_hunger", String.valueOf(player.getHungerManager().getFoodLevel()));
            contextData.put("player_held_item", String.valueOf(player.getMainHandStack().getItem().toString()));
            contextData.put("player_biome", player.getWorld().getBiome(player.getBlockPos()).getKey().get().getValue().getPath());
            contextData.put("player_is_creative", player.isCreative() ? "yes" : "no");
            contextData.put("player_is_swimming", player.isSwimming() ? "yes" : "no");
            contextData.put("player_is_on_ground", player.isOnGround() ? "yes" : "no");

            ItemStack feetArmor = player.getInventory().armor.get(0);
            ItemStack legsArmor = player.getInventory().armor.get(1);
            ItemStack chestArmor = player.getInventory().armor.get(2);
            ItemStack headArmor = player.getInventory().armor.get(3);
            contextData.put("player_armor_head", headArmor.getItem().toString());
            contextData.put("player_armor_chest", chestArmor.getItem().toString());
            contextData.put("player_armor_legs", legsArmor.getItem().toString());
            contextData.put("player_armor_feet", feetArmor.getItem().toString());

            // Get active player effects
            String effectsString = player.getActiveStatusEffects().entrySet().stream()
                    .map(entry -> entry.getKey().getTranslationKey() + " x" + (entry.getValue().getAmplifier() + 1))
                    .collect(Collectors.joining(", "));
            contextData.put("player_active_effects", effectsString);

            // Get World time (as 24 hour value)
            int hours = (int) ((player.getWorld().getTimeOfDay() / 1000 + 6) % 24); // Minecraft day starts at 6 AM
            int minutes = (int) (((player.getWorld().getTimeOfDay() % 1000) / 1000.0) * 60);
            contextData.put("world_time", String.format("%02d:%02d", hours, minutes));
            contextData.put("world_is_raining", player.getWorld().isRaining() ? "yes" : "no");
            contextData.put("world_is_thundering", player.getWorld().isThundering() ? "yes" : "no");
            contextData.put("world_difficulty", player.getWorld().getDifficulty().getName());
            contextData.put("world_is_hardcore", player.getWorld().getLevelProperties().isHardcore() ? "yes" : "no");

            // Get moon phase
            String moonPhaseDescription = switch (player.getWorld().getMoonPhase()) {
                case 0 -> "Full Moon";
                case 1 -> "Waning Gibbous";
                case 2 -> "Last Quarter";
                case 3 -> "Waning Crescent";
                case 4 -> "New Moon";
                case 5 -> "Waxing Crescent";
                case 6 -> "First Quarter";
                case 7 -> "Waxing Gibbous";
                default -> "Unknown";
            };
            contextData.put("world_moon_phase", moonPhaseDescription);

            // Get Entity details
            MobEntity entity = ServerEntityFinder.getEntityByUUID(player.getServerWorld(), UUID.fromString(entityId));
            if (entity.getCustomName() == null) {
                contextData.put("entity_name", "");
            } else {
                contextData.put("entity_name", entity.getCustomName().getLiteralString());
            }
            contextData.put("entity_type", entity.getType().getName().getString());
            contextData.put("entity_health", entity.getHealth() + "/" + entity.getMaxHealth());
            contextData.put("entity_personality", getCharacterProp("Personality"));
            contextData.put("entity_speaking_style", getCharacterProp("Speaking Style / Tone"));
            contextData.put("entity_likes", getCharacterProp("Likes"));
            contextData.put("entity_dislikes", getCharacterProp("Dislikes"));
            contextData.put("entity_age", getCharacterProp("Age"));
            contextData.put("entity_alignment", getCharacterProp("Alignment"));
            contextData.put("entity_friendship", String.valueOf(friendship));

            return contextData;
        }

        // Generate greeting
        public void generateMessage(ServerPlayerEntity player, String systemPrompt, String userMessage, boolean is_auto_message) {
            this.status = ChatStatus.PENDING;
            this.auto_generated = is_auto_message;
            // Add USER Message
            if (systemPrompt == "system-character") {
                // Add message without playerId (so it does not display)
                this.addMessage(userMessage, ChatSender.USER, "");
            } else if (systemPrompt == "system-chat") {
                this.addMessage(userMessage, ChatSender.USER, player.getUuidAsString());
            }

            // Add PLAYER context information
            Map<String, String> contextData = getPlayerContext(player);

            // fetch HTTP response from ChatGPT
            ChatGPTRequest.fetchMessageFromChatGPT(systemPrompt, contextData, previousMessages, false).thenAccept(output_message -> {
                if (output_message != null && systemPrompt == "system-character") {
                    // Character Sheet: Remove system-character message from previous messages
                    previousMessages.clear();

                    // Add NEW CHARACTER sheet & greeting
                    this.characterSheet = output_message;
                    String shortGreeting = getCharacterProp("short greeting");
                    this.addMessage(shortGreeting.replace("\n", " "), ChatSender.ASSISTANT, player.getUuidAsString());

                } else if (output_message != null && systemPrompt == "system-chat") {
                    // Chat Message: Parse message for behaviors
                    ParsedMessage result = MessageParser.parseMessage(output_message.replace("\n", " "));

                    // Apply behaviors (if any)
                    MobEntity entity = ServerEntityFinder.getEntityByUUID(player.getServerWorld(), UUID.fromString(entityId));
                    for (Behavior behavior : result.getBehaviors()) {
                        LOGGER.info("Behavior: " + behavior.getName() + (behavior.getArgument() != null ?
                                    ", Argument: " + behavior.getArgument() : ""));

                        // Determine entity's default speed
                        // Some Entities (i.e. Axolotl) set this incorrectly... so adjusting in the SpeedControls class
                        float entitySpeed = SpeedControls.getMaxSpeed(entity);
                        float entitySpeedFast = MathHelper.clamp(entitySpeed * 1.3F, 0.5f, 1.3f);

                        // Apply behaviors to entity
                        if (behavior.getName().equals("FOLLOW")) {
                            FollowPlayerGoal followGoal = new FollowPlayerGoal(player, entity, entitySpeed);
                            EntityBehaviorManager.removeGoal(entity, FleePlayerGoal.class);
                            EntityBehaviorManager.removeGoal(entity, AttackPlayerGoal.class);
                            EntityBehaviorManager.addGoal(entity, followGoal, GoalPriority.FOLLOW_PLAYER);

                        } else if (behavior.getName().equals("UNFOLLOW")) {
                            EntityBehaviorManager.removeGoal(entity, FollowPlayerGoal.class);

                        } else if (behavior.getName().equals("FLEE")) {
                            float fleeDistance = 400F; // 20 blocks squared
                            FleePlayerGoal fleeGoal = new FleePlayerGoal(player, entity, entitySpeedFast, fleeDistance);
                            EntityBehaviorManager.removeGoal(entity, TalkPlayerGoal.class);
                            EntityBehaviorManager.removeGoal(entity, FollowPlayerGoal.class);
                            EntityBehaviorManager.removeGoal(entity, AttackPlayerGoal.class);
                            EntityBehaviorManager.addGoal(entity, fleeGoal, GoalPriority.FLEE_PLAYER);

                        } else if (behavior.getName().equals("ATTACK")) {
                            AttackPlayerGoal attackGoal = new AttackPlayerGoal(player, entity, entitySpeedFast);
                            EntityBehaviorManager.removeGoal(entity, TalkPlayerGoal.class);
                            EntityBehaviorManager.removeGoal(entity, FollowPlayerGoal.class);
                            EntityBehaviorManager.removeGoal(entity, FleePlayerGoal.class);
                            EntityBehaviorManager.addGoal(entity, attackGoal, GoalPriority.ATTACK_PLAYER);

                        } else if (behavior.getName().equals("FRIENDSHIP")) {
                            int new_friendship = Math.max(-3, Math.min(3, behavior.getArgument()));
                            if (new_friendship > 0) {
                                // positive friendship (apply friend goal)
                                ((LivingEntityInterface)entity).setCanTargetPlayers(false);
                            } else if (new_friendship <= 0) {
                                // negative friendship (remove friend goal)
                                ((LivingEntityInterface)entity).setCanTargetPlayers(true);
                            }
                            // Does friendship improve?
                            if (new_friendship > this.friendship) {
                                // Stop any attack/flee if friendship improves
                                EntityBehaviorManager.removeGoal(entity, FleePlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, AttackPlayerGoal.class);
                            }
                            this.friendship = new_friendship;
                        }
                    }

                    // Add ASSISTANT message to history
                    this.addMessage(result.getOriginalMessage(), ChatSender.ASSISTANT, player.getUuidAsString());

                    // Get cleaned message (i.e. no <BEHAVIOR> strings)
                    String cleanedMessage = result.getCleanedMessage();
                    if (cleanedMessage.isEmpty()) {
                        cleanedMessage = Randomizer.getRandomMessage(Randomizer.RandomType.NO_RESPONSE);
                    }
                    // Update the current message to a 'cleaned version'
                    this.currentMessage = cleanedMessage;

                } else {
                    // Error / No Chat Message (Failure)
                    String randomErrorMessage = Randomizer.getRandomMessage(Randomizer.RandomType.ERROR);
                    this.addMessage(randomErrorMessage, ChatSender.ASSISTANT, player.getUuidAsString());

                    // Clear history (if no character sheet was generated)
                    if (characterSheet.isEmpty()) {
                        previousMessages.clear();
                    }
                }

                // Broadcast to all players
                ServerPackets.BroadcastPacketMessage(this);
            });
        }

        // Add a message to the history and update the current message
        public void addMessage(String message, ChatSender messageSender, String playerId) {
            // Truncate message (prevent crazy long messages... just in case)
            String truncatedMessage = message.substring(0, Math.min(message.length(), MAX_CHAR_IN_USER_MESSAGE));

            // Add message to history
            previousMessages.add(new ChatMessage(truncatedMessage, messageSender));

            // Set new message and reset line number of displayed text
            currentMessage = truncatedMessage;
            currentLineNumber = 0;
            if (messageSender == ChatSender.ASSISTANT) {
                // Show new generated message
                status = ChatStatus.DISPLAY;
            } else if (messageSender == ChatSender.USER) {
                // Show pending icon
                status = ChatStatus.PENDING;
            }
            sender = messageSender;
            this.playerId = playerId;

            // Broadcast to all players
            ServerPackets.BroadcastPacketMessage(this);
        }

        // Get wrapped lines
        public List<String> getWrappedLines() {
            return LineWrapper.wrapLines(this.currentMessage, MAX_CHAR_PER_LINE);
        }

        public boolean isEndOfMessage() {
            int totalLines = this.getWrappedLines().size();
            // Check if the current line number plus DISPLAY_NUM_LINES covers or exceeds the total number of lines
            return currentLineNumber + DISPLAY_NUM_LINES >= totalLines;
        }

        public void setLineNumber(Integer lineNumber) {
            int totalLines = this.getWrappedLines().size();
            // Ensure the lineNumber is within the valid range
            currentLineNumber = Math.min(Math.max(lineNumber, 0), totalLines);

            // Broadcast to all players
            ServerPackets.BroadcastPacketMessage(this);
        }

        public void setStatus(ChatStatus new_status) {
            status = new_status;

            // Broadcast to all players
            ServerPackets.BroadcastPacketMessage(this);
        }
    }

    public void clearData() {
        // Clear the chat data for the previous session
        entityChatDataMap.clear();
    }

    private ChatDataManager(Boolean server_only) {
        // Constructor
        entityChatDataMap = new HashMap<>();

        if (server_only) {
            // Generate initial quest
            // TODO: Complete the quest flow
            //generateQuest();
        }
    }

    // Method to get the global instance of the server data manager
    public static ChatDataManager getServerInstance() {
        return SERVER_INSTANCE;
    }

    // Method to get the global instance of the client data manager (synced from server)
    public static ChatDataManager getClientInstance() {
        return CLIENT_INSTANCE;
    }

    // Retrieve chat data for a specific entity, or create it if it doesn't exist
    public EntityChatData getOrCreateChatData(String entityId) {
        return entityChatDataMap.computeIfAbsent(entityId, k -> new EntityChatData(entityId, ""));
    }

    // Generate quest data for this server session
    public void generateQuest() {
        // Get items needed for Quest prompt
        List<String> commonItems = RarityItemCollector.getItemsByRarity(Rarity.COMMON, 5);
        List<String> uncommonItems = RarityItemCollector.getItemsByRarity(Rarity.UNCOMMON, 5);
        List<String> rareItems = RarityItemCollector.getItemsByRarity(Rarity.RARE, 5);

        // Get entities needed for Quest prompt
        List<String> commonEntities = RarityItemCollector.getEntitiesByRarity(Rarity.COMMON, 5);
        List<String> uncommonEntities = RarityItemCollector.getEntitiesByRarity(Rarity.UNCOMMON, 5);
        List<String> rareEntities = RarityItemCollector.getEntitiesByRarity(Rarity.RARE, 5);

        // Add context information for prompt
        Map<String, String> contextData = new HashMap<>();
        contextData.put("items_common", String.join("\n", commonItems));
        contextData.put("items_uncommon", String.join("\n", uncommonItems));
        contextData.put("items_rare", String.join("\n", rareItems));
        contextData.put("entities_common", String.join("\n", commonEntities));
        contextData.put("entities_uncommon", String.join("\n", uncommonEntities));
        contextData.put("entities_rare", String.join("\n", rareEntities));

        // Add message
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("Generate me a new fantasy story with ONLY the 1st character in the story", ChatSender.USER));

        // Generate Quest: fetch HTTP response from ChatGPT
        ChatGPTRequest.fetchMessageFromChatGPT("system-quest", contextData, messages, true).thenAccept(output_message -> {
            // New Quest
            Gson gson = new Gson();
            quest = gson.fromJson(output_message, QuestJson.class);
        });
    }

    // Save chat data to file
    public String GetLightChatData() {
        try {
            // Create "light" version of entire chat data HashMap
            HashMap<String, EntityChatData.EntityChatDataLight> lightVersionMap = new HashMap<>();
            this.entityChatDataMap.forEach((id, entityChatData) -> lightVersionMap.put(id, entityChatData.toLightVersion()));

            // Convert light chat data to JSON string
            return GSON.toJson(lightVersionMap).toString();
        } catch (Exception e) {
            // Handle exceptions
            return "";
        }
    }

    // Save chat data to file
    public void saveChatData(MinecraftServer server) {
        File saveFile = new File(server.getSavePath(WorldSavePath.ROOT).toFile(), "chatdata.json");
        File tempFile = new File(saveFile.getAbsolutePath() + ".tmp");
        LOGGER.info("Saving chat data to " + saveFile.getAbsolutePath());

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
            GSON.toJson(this.entityChatDataMap, writer);
            if (saveFile.exists()) {
                saveFile.delete();
            }
            if (!tempFile.renameTo(saveFile)) {
                throw new IOException("Failed to rename temporary chat data file to " + saveFile.getName());
            }
        } catch (Exception e) {
            LOGGER.error("Error saving chat data", e);
        }
    }

    // Load chat data from file
    public void loadChatData(MinecraftServer server) {
        File loadFile = new File(server.getSavePath(WorldSavePath.ROOT).toFile(), "chatdata.json");
        LOGGER.info("Loading chat data from " + loadFile.getAbsolutePath());

        if (loadFile.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(loadFile), StandardCharsets.UTF_8)) {
                Type type = new TypeToken<HashMap<String, EntityChatData>>(){}.getType();
                this.entityChatDataMap = GSON.fromJson(reader, type);
            } catch (Exception e) {
                LOGGER.error("Error loading chat data", e);
                this.entityChatDataMap = new HashMap<>();
            }
        } else {
            // Init empty chat data
            this.entityChatDataMap = new HashMap<>();
        }
    }
}