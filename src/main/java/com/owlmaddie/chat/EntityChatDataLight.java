package com.owlmaddie.chat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The {@code EntityChatDataLight} class represents the current displayed message, and no
 * previous messages or player message history. This is primarily used to broadcast the
 * currently displayed messages to players as they connect to the server.
 */
public class EntityChatDataLight {
    public String entityId;
    public String currentMessage;
    public int currentLineNumber;
    public ChatDataManager.ChatStatus status;
    public ChatDataManager.ChatSender sender;
    public Map<String, PlayerData> players;

    // Constructor to initialize the light version from the full version
    public EntityChatDataLight(EntityChatData fullData, UUID playerId) {
        this.entityId = fullData.entityId;
        this.currentMessage = fullData.currentMessage;
        this.currentLineNumber = fullData.currentLineNumber;
        this.status = fullData.status;
        this.sender = fullData.sender;

        // Initialize the players map and add only the current player's data
        this.players = new HashMap<>();
        String playerIdStr = playerId.toString();
        PlayerData playerData = fullData.getPlayerData(playerId);
        this.players.put(playerIdStr, playerData);
    }
}