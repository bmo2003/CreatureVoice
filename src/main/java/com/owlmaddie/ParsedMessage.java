package com.owlmaddie;

import java.util.List;

/**
 * The {@code ParsedMessage} class represents a list of behaviors and a cleaned message.
 */
public class ParsedMessage {
    private String cleanedMessage;
    private List<Behavior> behaviors;

    public ParsedMessage(String cleanedMessage, List<Behavior> behaviors) {
        this.cleanedMessage = cleanedMessage;
        this.behaviors = behaviors;
    }

    // Getters
    public String getCleanedMessage() {
        return cleanedMessage;
    }

    public List<Behavior> getBehaviors() {
        return behaviors;
    }
}
