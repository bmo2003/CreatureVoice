// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code LineWrapper} class is used to wrap lines of text on the nearest space character
 */
public class LineWrapper {

    public static List<String> wrapLines(String text, int maxWidth) {
        List<String> wrappedLines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            // Check if adding the next word exceeds the line length
            if (currentLine.length() + word.length() + 1 > maxWidth) {
                if (currentLine.length() > 0) {
                    wrappedLines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }
                // If the word itself is longer than maxWidth, split the word
                while (word.length() > maxWidth) {
                    wrappedLines.add(word.substring(0, maxWidth));
                    word = word.substring(maxWidth);
                }
            }
            // Append the word to the line
            if (currentLine.length() > 0) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }
        // Add the last line if there's anything left
        if (currentLine.length() > 0) {
            wrappedLines.add(currentLine.toString());
        }

        return wrappedLines;
    }
}
