package com.owlmaddie.chat;

/**
 * The {@code EntityChatDataLight} class represents the current displayed message, and no
 * previous messages or player message history. This is primarily used to broadcast the
 * currently displayed messages to players as they connect to the server.
 */
public class EntityChatDataLight {
    public String entityId;
    public String currentMessage;
    public int currentLineNumber;
    public ChatDataManager.ChatStatus status;
    public ChatDataManager.ChatSender sender;
    public int friendship;
}