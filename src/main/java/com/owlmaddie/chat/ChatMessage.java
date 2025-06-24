// SPDX-License-Identifier: GPL-3.0-or-later | CreatureChat™ © owlmaddie LLC
// Code: GPLv3 | Assets: CC BY-NC 4.0; See LICENSE.md & LICENSE-ASSETS.md.
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