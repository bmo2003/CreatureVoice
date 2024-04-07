package com.owlmaddie.utils;

import java.util.Random;

/**
 * The {@code RandomUtils} class is used to easily generate random Letters or Numbers.
 */
public class RandomUtils {
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
