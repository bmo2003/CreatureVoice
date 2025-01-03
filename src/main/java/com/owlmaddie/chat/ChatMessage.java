package com.owlmaddie.chat;

/**
 * The {@code ChatMessage} class represents a single message.
 */
public class ChatMessage {
    public String message;
    public String name;
    public ChatDataManager.ChatSender sender;
    public Long timestamp;

    public ChatMessage(String message, ChatDataManager.ChatSender sender, String playerName) {
        this.message = message;
        this.sender = sender;
        this.name = playerName;
        this.timestamp = System.currentTimeMillis();
    }
}