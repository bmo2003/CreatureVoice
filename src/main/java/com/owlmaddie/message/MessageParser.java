// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
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
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");

    public static ParsedMessage parseMessage(String input) {
        LOGGER.debug("Parsing message: {}", input);
        StringBuilder cleanedMessage = new StringBuilder();
        List<Behavior> behaviors = new ArrayList<>();

        // Integer-argument behaviors (e.g. <FRIENDSHIP 2>) and no-argument behaviors
        Pattern intPattern = Pattern.compile("[<*](FOLLOW|LEAD|FLEE|ATTACK|PROTECT|FRIENDSHIP|UNFOLLOW|UNLEAD|UNPROTECT|UNFLEE|EXPLODE|SET_FIRE|RESUME)[:\\s]*(\\s*[+-]?\\d+)?[>*]", Pattern.CASE_INSENSITIVE);
        // String-argument behaviors (e.g. <ATTACK_NPC Jax>, <GIVE_ITEM emerald 2>, <RECEIVE_ITEM emerald 2>, <RENAME Gronk>)
        // Capture everything between the behavior name and the closing > as the argument
        Pattern strPattern = Pattern.compile("[<*](ATTACK_NPC|GIVE_ITEM|RECEIVE_ITEM|RENAME)\\s+([^>*]+?)[>*]", Pattern.CASE_INSENSITIVE);

        // First pass: extract ATTACK_NPC with string arg (replace with placeholder to avoid
        // interfering with the second pass on the same string)
        Matcher strMatcher = strPattern.matcher(input);
        StringBuilder afterStrPass = new StringBuilder();
        while (strMatcher.find()) {
            String behaviorName = strMatcher.group(1).toUpperCase();
            String targetName = strMatcher.group(2);
            behaviors.add(new Behavior(behaviorName, targetName));
            LOGGER.debug("Found behavior: {} with target: {}", behaviorName, targetName);
            strMatcher.appendReplacement(afterStrPass, "");
        }
        strMatcher.appendTail(afterStrPass);

        // Second pass: extract integer/no-arg behaviors
        Matcher matcher = intPattern.matcher(afterStrPass.toString());
        while (matcher.find()) {
            String behaviorName = matcher.group(1).toUpperCase();
            Integer argument = null;
            if (matcher.group(2) != null && !matcher.group(2).isBlank()) {
                argument = Integer.valueOf(matcher.group(2).trim());
            }
            behaviors.add(new Behavior(behaviorName, argument));
            LOGGER.debug("Found behavior: {} with argument: {}", behaviorName, argument);
            matcher.appendReplacement(cleanedMessage, "");
        }
        matcher.appendTail(cleanedMessage);

        // Get final cleaned string
        String displayMessage = cleanedMessage.toString().trim();

        // Remove all occurrences of "<>" and "**" (if any)
        displayMessage = displayMessage.replaceAll("<>", "").replaceAll("\\*\\*", "").trim();
        LOGGER.debug("Cleaned message: {}", displayMessage);

        return new ParsedMessage(displayMessage, input.trim(), behaviors);
    }
}
