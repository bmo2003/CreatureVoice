package com.owlmaddie;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;


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

        public EntityChatData(int entityId) {
            this.entityId = entityId;
            this.currentMessage = "";
            this.currentLineNumber = 0;
            this.previousMessages = new ArrayList<>();
            this.characterSheet = "";
            this.status = ChatStatus.NONE;
        }

        // Generate greeting
        public void generateGreeting() {
            this.status = ChatStatus.PENDING;
            ChatGPTRequest.fetchGreetingFromChatGPT().thenAccept(greeting -> {
                if (greeting != null) {
                    this.addMessage(greeting);
                }
            });

            // Broadcast to all players
            ModInit.BroadcastPacketMessage(this);
        }

        // Add a message to the history and update the current message
        public void addMessage(String message) {
            if (!currentMessage.isEmpty()) {
                previousMessages.add(currentMessage);
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
            if (lineNumber == this.getWrappedLines().size()) {
                status = ChatStatus.END;
            }

            // Broadcast to all players
            ModInit.BroadcastPacketMessage(this);
        }
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