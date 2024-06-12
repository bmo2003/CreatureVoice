package com.owlmaddie.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.owlmaddie.chat.ChatDataManager;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code EntityTestData} class is a test representation of our regular EntityChatData class. This allows us
 * to simulate loading entity JSON data, adding new messages, and sending HTTP requests for the testing module. It
 * is not possible to use the original class, due to Minecraft and Fabric imports and dependencies.
 */
public class EntityTestData {
    public String entityId;
    public String playerId;
    public String currentMessage;
    public int currentLineNumber;
    public ChatDataManager.ChatStatus status;
    public List<ChatDataManager.ChatMessage> previousMessages;
    public String characterSheet;
    public ChatDataManager.ChatSender sender;
    public int friendship; // -3 to 3 (0 = neutral)
    public int auto_generated;

    public EntityTestData(String entityId, String playerId) {
        this.entityId = entityId;
        this.playerId = playerId;
        this.currentMessage = "";
        this.currentLineNumber = 0;
        this.previousMessages = new ArrayList<>();
        this.characterSheet = "";
        this.status = ChatDataManager.ChatStatus.NONE;
        this.sender = ChatDataManager.ChatSender.USER;
        this.friendship = 0;
        this.auto_generated = 0;
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

    // Add a message to the history and update the current message
    public void addMessage(String message, ChatDataManager.ChatSender messageSender) {
        // Truncate message (prevent crazy long messages... just in case)
        String truncatedMessage = message.substring(0, Math.min(message.length(), ChatDataManager.MAX_CHAR_IN_USER_MESSAGE));

        // Add message to history
        previousMessages.add(new ChatDataManager.ChatMessage(truncatedMessage, messageSender));

        // Set new message and reset line number of displayed text
        currentMessage = truncatedMessage;
        currentLineNumber = 0;
        if (messageSender == ChatDataManager.ChatSender.ASSISTANT) {
            // Show new generated message
            status = ChatDataManager.ChatStatus.DISPLAY;
        } else if (messageSender == ChatDataManager.ChatSender.USER) {
            // Show pending icon
            status = ChatDataManager.ChatStatus.PENDING;
        }
        sender = messageSender;
    }

    public Map<String, String> getPlayerContext(Path worldPath, Path playerPath, Path entityPath) {
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, String>>() {}.getType();
        Map<String, String> contextData = new HashMap<>();

        try {
            // Load world context
            String worldContent = Files.readString(worldPath);
            Map<String, String> worldContext = gson.fromJson(worldContent, mapType);
            contextData.putAll(worldContext);

            // Load player context
            String playerContent = Files.readString(playerPath);
            Map<String, String> playerContext = gson.fromJson(playerContent, mapType);
            contextData.putAll(playerContext);

            // Load entity context
            String entityContent = Files.readString(entityPath);
            Map<String, String> entityContext = gson.fromJson(entityContent, mapType);
            contextData.putAll(entityContext);

            // Read character sheet info
            contextData.put("entity_name", getCharacterProp("Name"));
            contextData.put("entity_friendship", String.valueOf(this.friendship));
            contextData.put("entity_personality", getCharacterProp("Personality"));
            contextData.put("entity_speaking_style", getCharacterProp("Speaking Style / Tone"));
            contextData.put("entity_likes", getCharacterProp("Likes"));
            contextData.put("entity_dislikes", getCharacterProp("Dislikes"));
            contextData.put("entity_age", getCharacterProp("Age"));
            contextData.put("entity_alignment", getCharacterProp("Alignment"));
            contextData.put("entity_class", getCharacterProp("Class"));
            contextData.put("entity_skills", getCharacterProp("Skills"));
            contextData.put("entity_background", getCharacterProp("Background"));

        } catch (IOException e) {
            e.printStackTrace();
        }

        return contextData;
    }
}