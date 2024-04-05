package com.owlmaddie.message;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The {@code ParsedMessage} class represents a list of behaviors and a cleaned message.
 */
public class ParsedMessage {
    private String cleanedMessage;
    private String originalMessage;
    private List<Behavior> behaviors;
    private List<String> noResponseMessages = Arrays.asList(
            "<no response>",
            "<silence>",
            "<stares>",
            "<blinks>",
            "<looks away>",
            "<sighs>",
            "<shrugs>",
            "<taps foot>",
            "<yawns>",
            "<examines nails>",
            "<whistles softly>",
            "<shifts uncomfortably>",
            "<glances around>",
            "<pretends not to hear>",
            "<hums quietly>",
            "<fiddles with something>",
            "<gazes into the distance>",
            "<smirks>",
            "<raises an eyebrow>",
            "<clears throat>",
            "<peers over your shoulder>",
            "<fakes a smile>",
            "<checks the time>",
            "<doodles in the air>",
            "<mutters under breath>",
            "<adjusts an imaginary tie>",
            "<counts imaginary stars>",
            "<plays with a nonexistent pet>"
    );

    public ParsedMessage(String cleanedMessage, String originalMessage, List<Behavior> behaviors) {
        this.cleanedMessage = cleanedMessage;
        this.originalMessage = originalMessage;
        this.behaviors = behaviors;
    }

    // Get cleaned message (no behaviors)
    public String getCleanedMessage() {
        return cleanedMessage.trim();
    }

    // Get random no response message
    public String getRandomNoResponseMessage() {
        Random random = new Random();
        int index = random.nextInt(noResponseMessages.size());
        return noResponseMessages.get(index).trim();
    }

    // Get original message
    public String getOriginalMessage() {
        return originalMessage.trim();
    }

    public List<Behavior> getBehaviors() {
        return behaviors;
    }
}
