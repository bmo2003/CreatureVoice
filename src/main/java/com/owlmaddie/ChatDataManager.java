package com.owlmaddie;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ChatDataManager {
    // Use a static instance to manage our data globally
    private static final ChatDataManager SERVER_INSTANCE = new ChatDataManager();
    private static final ChatDataManager CLIENT_INSTANCE = new ChatDataManager();
    public static int MAX_CHAR_PER_LINE = 22;

    public enum ChatStatus {
        NONE,       // No chat status yet
        PENDING,    // Chat is pending (e.g., awaiting response or processing)
        DISPLAY,    // Chat is currently being displayed
        END         // Chat has ended or been dismissed
    }

    public enum ChatSender {
        NONE,      // A blank chat message
        USER,      // A user chat message
        ASSISTANT  // A GPT generated message
    }

    // HashMap to associate unique entity IDs with their chat data
    private HashMap<Integer, EntityChatData> entityChatDataMap;

    // Inner class to hold entity-specific data
    public static class EntityChatData {
        public int entityId;
        public String currentMessage;
        public int currentLineNumber;
        public ChatStatus status;
        public List<String> previousMessages;
        public String characterSheet;
        public ChatSender sender;

        public EntityChatData(int entityId) {
            this.entityId = entityId;
            this.currentMessage = "";
            this.currentLineNumber = 0;
            this.previousMessages = new ArrayList<>();
            this.characterSheet = "";
            this.status = ChatStatus.NONE;
            this.sender = ChatSender.NONE;
        }

        // Generate greeting
        public void generateMessage(ServerPlayerEntity player, String user_message) {
            this.status = ChatStatus.PENDING;
            // Add USER Message
            //this.addMessage(user_message, ChatSender.USER);

            // Add PLAYER context information
            Map<String, String> contextData = new HashMap<>();
            contextData.put("message", user_message);
            contextData.put("player_name", player.getDisplayName().getString());
            contextData.put("player_health", String.valueOf(player.getHealth()));
            contextData.put("player_hunger", String.valueOf(player.getHungerManager().getFoodLevel()));
            contextData.put("player_held_item", String.valueOf(player.getMainHandStack().getItem().toString()));
            contextData.put("player_biome", player.getWorld().getBiome(player.getBlockPos()).getKey().get().getValue().getPath());

            ItemStack feetArmor = player.getInventory().armor.get(0);
            ItemStack legsArmor = player.getInventory().armor.get(1);
            ItemStack chestArmor = player.getInventory().armor.get(2);
            ItemStack headArmor = player.getInventory().armor.get(3);
            contextData.put("player_armor_head", headArmor.getItem().toString());
            contextData.put("player_armor_chest", chestArmor.getItem().toString());
            contextData.put("player_armor_legs", legsArmor.getItem().toString());
            contextData.put("player_armor_feet", feetArmor.getItem().toString());

            // Get World time (as 24 hour value)
            int hours = (int) ((player.getWorld().getTimeOfDay() / 1000 + 6) % 24); // Minecraft day starts at 6 AM
            int minutes = (int) (((player.getWorld().getTimeOfDay() % 1000) / 1000.0) * 60);
            contextData.put("world_time", String.format("%02d:%02d", hours, minutes));

            // Get Entity details
            Entity entity = player.getServerWorld().getEntityById(entityId);
            if (entity.getCustomName() == null) {
                contextData.put("entity_name", "Un-named");
            } else {
                contextData.put("entity_name", entity.getCustomName().toString());
            }
            contextData.put("entity_type", entity.getType().getName().getString().toString());

            // fetch HTTP response from ChatGPT
            ChatGPTRequest.fetchMessageFromChatGPT("chat", contextData).thenAccept(output_message -> {
                if (output_message != null) {
                    // Add ASSISTANT message
                    this.addMessage(output_message, ChatSender.ASSISTANT);
                }
            });

            // Broadcast to all players
            ModInit.BroadcastPacketMessage(this);
        }

        // Add a message to the history and update the current message
        public void addMessage(String message, ChatSender sender) {
            if (!currentMessage.isEmpty()) {
                previousMessages.add(sender.toString() + ": " + currentMessage);
            }
            currentMessage = message;

            // Set line number of displayed text
            currentLineNumber = 0;
            status = ChatStatus.DISPLAY;

            // Broadcast to all players
            ModInit.BroadcastPacketMessage(this);
        }

        // Get wrapped lines
        public List<String> getWrappedLines() {
            return LineWrapper.wrapLines(this.currentMessage, MAX_CHAR_PER_LINE);
        }

        // Update starting line number of displayed text
        public void setLineNumber(Integer lineNumber) {
            // Update displayed starting line # (between 0 and # of lines)
            currentLineNumber = Math.min(Math.max(lineNumber, 0), this.getWrappedLines().size());
            if (currentLineNumber >= this.getWrappedLines().size()) {
                status = ChatStatus.END;
            }

            // Broadcast to all players
            ModInit.BroadcastPacketMessage(this);
        }
    }

    public void clearData() {
        // Clear the chat data for the previous session
        entityChatDataMap.clear();
    }

    private ChatDataManager() {
        entityChatDataMap = new HashMap<>();
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
    public EntityChatData getOrCreateChatData(int entityId) {
        return entityChatDataMap.computeIfAbsent(entityId, k -> new EntityChatData(entityId));
    }
}