package com.owlmaddie.ui;

import com.owlmaddie.chat.ChatDataManager;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code PlayerMessageManager} class keeps track of currently visible player messages. These are temporary,
 * and only stored when they need to be rendered.
 */
public class PlayerMessageManager {
    private static final ConcurrentHashMap<UUID, PlayerMessage> messages = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> openChatUIs = new ConcurrentHashMap<>();

    public static void addMessage(UUID playerUUID, String playerName, String messageText, int ticks) {
        messages.put(playerUUID, new PlayerMessage(playerUUID.toString(), playerName, messageText, ticks));
    }

    public static PlayerMessage getMessage(UUID playerId) {
        return messages.get(playerId);
    }

    public static void tickUpdate() {
        messages.forEach((uuid, playerMessage) -> {
            if (playerMessage.tickCountdown.decrementAndGet() <= 0) {
                // Move to next line or remove the message
                nextLineOrRemove(uuid, playerMessage);
            }
        });
    }

    private static void nextLineOrRemove(UUID uuid, PlayerMessage playerMessage) {
        // Logic to move to the next line or remove the message
        if (!playerMessage.isEndOfMessage()) {
            // Check if more lines are available
            playerMessage.currentLineNumber += ChatDataManager.DISPLAY_NUM_LINES;
            playerMessage.tickCountdown.set(ChatDataManager.TICKS_TO_DISPLAY_USER_MESSAGE);
        } else {
            messages.remove(uuid);
        }
    }

    // Methods for managing open chat UIs
    public static void openChatUI(UUID playerId) {
        openChatUIs.put(playerId, Boolean.TRUE);
    }

    public static boolean isChatUIOpen(UUID playerId) {
        return openChatUIs.getOrDefault(playerId, Boolean.FALSE);
    }

    public static void closeChatUI(UUID playerId) {
        openChatUIs.remove(playerId);
    }
}

