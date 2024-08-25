package com.owlmaddie.chat;

import java.util.ArrayList;
import java.util.List;

public class PlayerData {
    public List<ChatMessage> messages;
    public int friendship;

    public PlayerData() {
        this.messages = new ArrayList<>();
        this.friendship = 0;
    }
}