package com.owlmaddie.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code MessageParser} class parses out behaviors that are included in messages, and outputs
 * a {@code ParsedMessage} result, which separates the cleaned message and the included behaviors.
 */
public class MessageParser {
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");

    public static ParsedMessage parseMessage(String input) {
        LOGGER.info("Parsing message: {}", input);  // Log the input string
        StringBuilder cleanedMessage = new StringBuilder();
        List<Behavior> behaviors = new ArrayList<>();
        Pattern pattern = Pattern.compile("<(\\w+)(?:\\s+(-?\\d+))?>");
        Matcher matcher = pattern.matcher(input);

        while (matcher.find()) {
            String behaviorName = matcher.group(1);
            Integer argument = null;
            if (matcher.group(2) != null) {
                argument = Integer.valueOf(matcher.group(2));
            }
            behaviors.add(new Behavior(behaviorName, argument));
            LOGGER.info("Found behavior: {} with argument: {}", behaviorName, argument);  // Log each found behavior

            matcher.appendReplacement(cleanedMessage, "");
        }
        matcher.appendTail(cleanedMessage);
        LOGGER.info("Cleaned message: {}", cleanedMessage.toString());  // Log the cleaned message

        return new ParsedMessage(cleanedMessage.toString().trim(), behaviors);
    }
}
