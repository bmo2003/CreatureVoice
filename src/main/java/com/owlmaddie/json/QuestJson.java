package com.owlmaddie.json;

import java.util.List;

public class QuestJson {
    Story story;
    List<Character> characters;

    public static class Story {
        String background;
        String clue;
    }

    public static class Character {
        String name;
        int age;
        String personality;
        String greeting;
        String entity_type_key;
        Quest quest;
        String choice_question;
        List<Choice> choices;
    }

    public static class Quest {
        List<QuestItem> quest_items;
        List<DropItem> drop_items;
    }

    public static class QuestItem {
        String key;
        int quantity;
    }

    public static class DropItem {
        String key;
        int quantity;
    }

    public static class Choice {
        String choice;
        String clue;
    }
}
