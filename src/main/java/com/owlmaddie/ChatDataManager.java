package com.owlmaddie;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;


public class ChatDataManager {
    // Use a static instance to manage our data globally
    private static final ChatDataManager INSTANCE = new ChatDataManager();

    // HashMap to associate unique entity IDs with their chat data
    private HashMap<Integer, EntityChatData> entityChatDataMap;

    // Inner class to hold entity-specific data
    public static class EntityChatData {
        public String currentMessage;
        public List<String> previousMessages;
        public String characterSheet;

        public EntityChatData() {
            this.currentMessage = "";
            this.previousMessages = new ArrayList<>();
            this.characterSheet = "";
        }

        // Generate greeting
        public void generateGreeting() {
            ChatGPTRequest.fetchGreetingFromChatGPT().thenAccept(greeting -> {
                if (greeting != null) {
                    this.currentMessage = greeting;
                }
            });
        }

        // Add a message to the history and update the current message
        public void addMessage(String message) {
            if (!currentMessage.isEmpty()) {
                previousMessages.add(currentMessage);
            }
            currentMessage = message;
        }
    }

    private ChatDataManager() {
        entityChatDataMap = new HashMap<>();
    }

    // Method to get the global instance of the data manager
    public static ChatDataManager getInstance() {
        return INSTANCE;
    }

    // Retrieve chat data for a specific entity, or create it if it doesn't exist
    public EntityChatData getOrCreateChatData(int entityId) {
        return entityChatDataMap.computeIfAbsent(entityId, k -> new EntityChatData());
    }
}