package com.owlmaddie.message;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code MessageParser} class parses out behaviors that are included in messages, and outputs
 * a {@code ParsedMessage} result, which separates the cleaned message and the included behaviors.
 */
public class MessageParser {

    public static ParsedMessage parseMessage(String input) {
        StringBuilder cleanedMessage = new StringBuilder();
        List<Behavior> behaviors = new ArrayList<>();
        Pattern pattern = Pattern.compile("<(\\w+)(?:\\s+(-?\\d+))?>");
        Matcher matcher = pattern.matcher(input);

        while (matcher.find()) {
            // Extract and store behaviors
            String behaviorName = matcher.group(1);
            Integer argument = null;
            if (matcher.group(2) != null) {
                argument = Integer.valueOf(matcher.group(2));
            }
            behaviors.add(new Behavior(behaviorName, argument));

            // Remove the matched pattern from the original string
            matcher.appendReplacement(cleanedMessage, "");
        }
        matcher.appendTail(cleanedMessage);

        // Create and return the ParseResult object
        return new ParsedMessage(cleanedMessage.toString().trim(), behaviors);
    }
}
