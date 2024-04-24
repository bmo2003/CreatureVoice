package com.owlmaddie.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The {@code Randomizer} class provides easy functions for generating a variety of different random numbers
 * and phrases used by this mod.
 */
public class Randomizer {
    public enum RandomType { NO_RESPONSE, ERROR, ADJECTIVE, FREQUENCY }
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
    private static List<String> characterAdjectives = Arrays.asList(
            "mystical", "fiery", "ancient", "cursed", "ethereal",
            "clumsy", "stealthy", "legendary", "toxic", "enigmatic",
            "frosty", "celestial", "rambunctious", "shadowy", "golden",
            "invisible", "screaming", "radiant", "savage", "whimsical",
            "turbulent", "crystalline", "ominous", "jubilant", "arcane",
            "rugged", "luminous", "venomous", "timeworn", "zephyr",
            "humorous", "silly", "goofy", "irate", "furious",
            "wrathful", "nefarious", "sinister", "malevolent", "tricky",
            "sly", "roguish", "deceitful", "untruthful", "duplicitous",
            "noble", "dignified", "righteous", "defensive", "guardian",
            "shielding", "amiable", "congenial", "affable", "wicked",
            "maleficent", "heinous"
    );
    private static List<String> frequencyTerms = Arrays.asList(
            "always", "frequently", "usually", "often", "sometimes",
            "occasionally", "rarely", "seldom", "almost never", "never"
    );

    // Get random no response message
    public static String getRandomMessage(RandomType messageType) {
        Random random = new Random();
        List<String> messages = null;
        if (messageType.equals(RandomType.ERROR)) {
            messages = errorResponseMessages;
        } else if (messageType.equals(RandomType.NO_RESPONSE)) {
            messages = noResponseMessages;
        } else if (messageType.equals(RandomType.ADJECTIVE)) {
            messages = characterAdjectives;
        } else if (messageType.equals(RandomType.FREQUENCY)) {
            messages = frequencyTerms;
        }

        int index = random.nextInt(messages.size());
        return messages.get(index).trim();
    }

    public static String RandomLetter() {
        // Return random letter between 'A' and 'Z'
        int randomNumber = RandomNumber(26);
        return String.valueOf((char) ('A' + randomNumber));
    }

    public static int RandomNumber(int max) {
        // Generate a random integer between 0 and max (inclusive)
        Random random = new Random();
        return random.nextInt(max);
    }
}
