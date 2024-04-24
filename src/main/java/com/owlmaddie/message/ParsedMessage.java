package com.owlmaddie.message;

import java.util.List;

/**
 * The {@code ParsedMessage} class represents a list of behaviors and a cleaned message.
 */
public class ParsedMessage {
    private String cleanedMessage;
    private String originalMessage;
    private List<Behavior> behaviors;

    public ParsedMessage(String cleanedMessage, String originalMessage, List<Behavior> behaviors) {
        this.cleanedMessage = cleanedMessage;
        this.originalMessage = originalMessage;
        this.behaviors = behaviors;
    }

    // Get cleaned message (no behaviors)
    public String getCleanedMessage() {
        return cleanedMessage.trim();
    }

    // Get original message
    public String getOriginalMessage() {
        return originalMessage.trim();
    }

    public List<Behavior> getBehaviors() {
        return behaviors;
    }
}
