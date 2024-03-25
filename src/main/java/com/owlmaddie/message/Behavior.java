package com.owlmaddie.message;

/**
 * The {@code Behavior} class represents a single behavior with an optional integer argument.
 * This class is used to model behaviors extracted from a parsed message, where each
 * behavior might have an associated argument that further defines the behavior.
 *
 * For example: "<FOLLOW>", "<FRIENDSHIP 3>", "<UNFOLLOW>"
 */
public class Behavior {
    private String name;
    private Integer argument;

    public Behavior(String name, Integer argument) {
        this.name = name;
        this.argument = argument;
    }

    // Getters
    public String getName() {
        return name;
    }

    public Integer getArgument() {
        return argument;
    }

    @Override
    public String toString() {
        if (argument != null) {
            return name + ": " + argument;
        } else {
            return name;
        }
    }
}
