// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.chat;

import net.minecraft.server.MinecraftServer;

/**
 * The {@code ChatDataAutoSaver} class is a Runnable task, which autosaves the server chat data to JSON.
 * It can be scheduled with the {@code ChatDataSaverScheduler} class.
 */
public class ChatDataAutoSaver implements Runnable {
    private final MinecraftServer server;

    public ChatDataAutoSaver(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void run() {
        // Your method to save chat data
        ChatDataManager.getServerInstance().saveChatData(server);
    }
}
