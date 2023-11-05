package com.owlmaddie;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;


public class ChatDataManager {
    // Use a static instance to manage our data globally
    private static final ChatDataManager INSTANCE = new ChatDataManager();
    public static int MAX_CHAR_PER_LINE = 22;

    // HashMap to associate unique entity IDs with their chat data
    private HashMap<Integer, EntityChatData> entityChatDataMap;

    // Inner class to hold entity-specific data
    public static class EntityChatData {
        public String currentMessage;
        public int currentLineNumber;
        public List<String> previousMessages;
        public String characterSheet;

        public EntityChatData() {
            this.currentMessage = "";
            this.currentLineNumber = 0;
            this.previousMessages = new ArrayList<>();
            this.characterSheet = "";
        }

        // Generate greeting
        public void generateGreeting() {
            ChatGPTRequest.fetchGreetingFromChatGPT().thenAccept(greeting -> {
                if (greeting != null) {
                    this.addMessage(greeting);
                }
            });
        }

        // Add a message to the history and update the current message
        public void addMessage(String message) {
            if (!currentMessage.isEmpty()) {
                previousMessages.add(currentMessage);
            }
            currentMessage = message;

            // Set line number of displayed text
            this.currentLineNumber = 0;
        }

        // Get wrapped lines
        public List<String> getWrappedLines() {
            return LineWrapper.wrapLines(this.currentMessage, MAX_CHAR_PER_LINE);
        }

        // Update starting line number of displayed text
        public void setLineNumber(Integer lineNumber) {
            // Update displayed starting line # (between 0 and # of lines)
            this.currentLineNumber = Math.min(Math.max(lineNumber, 0), this.getWrappedLines().size());
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