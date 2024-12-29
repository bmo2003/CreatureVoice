package com.owlmaddie.ui;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.chat.EntityChatData;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@code PlayerMessage} class provides a player message object, which keeps track of how
 * many ticks to remain visible, and the message to display. Similar to an EntityChatData, but
 * much simpler.
 */
public class PlayerMessage extends EntityChatData {
    public AtomicInteger tickCountdown;

    public PlayerMessage(String playerId, String playerName, String messageText, int ticks) {
        super(playerId, playerName);
        this.currentMessage = messageText;
        this.currentLineNumber = 0;
        this.tickCountdown = new AtomicInteger(ticks);
        this.status = ChatDataManager.ChatStatus.DISPLAY;
    }
}