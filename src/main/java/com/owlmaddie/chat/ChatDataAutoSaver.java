// SPDX-License-Identifier: GPL-3.0-or-later | CreatureChat™ © owlmaddie LLC
// Code: GPLv3 | Assets: CC BY-NC 4.0; See LICENSE.md & LICENSE-ASSETS.md.
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
