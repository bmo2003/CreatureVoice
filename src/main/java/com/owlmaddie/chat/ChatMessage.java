package com.owlmaddie.chat;

/**
 * The {@code ChatMessage} class represents a single message.
 */
public class ChatMessage {
    public String message;
    public ChatDataManager.ChatSender sender;
    public Long timestamp;

    public ChatMessage(String message, ChatDataManager.ChatSender sender) {
        this.message = message;
        this.sender = sender;
        this.timestamp = System.currentTimeMillis();
    }
}