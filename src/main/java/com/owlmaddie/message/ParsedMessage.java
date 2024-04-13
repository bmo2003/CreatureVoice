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
    private static List<String> noResponseMessages = Arrays.asList(
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
    private static List<String> errorResponseMessages = Arrays.asList(
            "Seems like my words got lost in the End. Check out https://discord.gg/m9dvPFmN3e for clues!",
            "Oops! My speech bubble popped. Need help? Visit https://discord.gg/m9dvPFmN3e.",
            "I might've eaten a bad Command Block. Help me out at https://discord.gg/m9dvPFmN3e!",
            "My words are on strike. More info? https://discord.gg/m9dvPFmN3e.",
            "I think a Creeper blew up my script. Instructions? https://discord.gg/m9dvPFmN3e.",
            "BRB, asking a villager for directions to https://discord.gg/m9dvPFmN3e.",
            "It’s not you, it’s my API key. Let's regroup at https://discord.gg/m9dvPFmN3e.",
            "I tried to speak, but it was a critical miss. Help at https://discord.gg/m9dvPFmN3e.",
            "Words are hard. Come chat at https://discord.gg/m9dvPFmN3e.",
            "I must've left my responses in my other pants. See https://discord.gg/m9dvPFmN3e.",
            "Shh... I’m hiding from an invalid API key. Join the hunt at https://discord.gg/m9dvPFmN3e.",
            "I’d tell you, but then I’d have to respawn. Meet me at https://discord.gg/m9dvPFmN3e.",
            "Error 404: Response not found. Maybe it’s at https://discord.gg/m9dvPFmN3e?",
            "I'm speechless, literally. Let's troubleshoot at https://discord.gg/m9dvPFmN3e.",
            "Looks like my connection got lost in the Nether. Can you help? https://discord.gg/m9dvPFmN3e.",
            "I forgot what I was saying, but https://discord.gg/m9dvPFmN3e remembers.",
            "Are my words mining without a pickaxe? Dig up some help at https://discord.gg/m9dvPFmN3e.",
            "Sorry, my parrot ate the response. Teach it better at https://discord.gg/m9dvPFmN3e.",
            "My magic mirror says: 'Better answers found at https://discord.gg/m9dvPFmN3e.'",
            "This message is temporarily out of order. Order yours at https://discord.gg/m9dvPFmN3e."
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
    public static String getRandomNoResponseMessage() {
        Random random = new Random();
        int index = random.nextInt(noResponseMessages.size());
        return noResponseMessages.get(index).trim();
    }

    // Get random error message
    public static String getRandomErrorMessage() {
        Random random = new Random();
        int index = random.nextInt(errorResponseMessages.size());
        return errorResponseMessages.get(index).trim();
    }

    // Get original message
    public String getOriginalMessage() {
        return originalMessage.trim();
    }

    public List<Behavior> getBehaviors() {
        return behaviors;
    }
}
