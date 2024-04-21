package com.owlmaddie.ui;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.chat.LineWrapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@code PlayerMessage} class provides a player message object, which keeps track of how
 * many ticks to remain visible, and the message to display. Similar to an EntityChatData, but
 * much simpler.
 */
public class PlayerMessage {
    public String currentMessage;
    public int currentLineNumber;
    public AtomicInteger tickCountdown;

    public PlayerMessage(String messageText, int ticks) {
        this.currentMessage = messageText;
        this.currentLineNumber = 0;
        this.tickCountdown = new AtomicInteger(ticks);
    }

    public List<String> getWrappedLines() {
        return LineWrapper.wrapLines(this.currentMessage, ChatDataManager.MAX_CHAR_PER_LINE);
    }

    public boolean isEndOfMessage() {
        int totalLines = this.getWrappedLines().size();
        // Check if the current line number plus DISPLAY_NUM_LINES covers or exceeds the total number of lines
        return currentLineNumber + ChatDataManager.DISPLAY_NUM_LINES >= totalLines;
    }
}