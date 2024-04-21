package com.owlmaddie.ui;

import com.owlmaddie.chat.ChatDataManager;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@code PlayerMessage} class provides a player message object, which keeps track of how
 * many ticks to remain visible, and the message to display. Similar to an EntityChatData, but
 * much simpler.
 */
public class PlayerMessage extends ChatDataManager.EntityChatData {
    public AtomicInteger tickCountdown;

    public PlayerMessage(String playerId, String messageText, int ticks) {
        super("", playerId);
        this.currentMessage = messageText;
        this.currentLineNumber = 0;
        this.tickCountdown = new AtomicInteger(ticks);
        this.status = ChatDataManager.ChatStatus.DISPLAY;
    }
}