package com.owlmaddie.chat;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The {@code ChatDataSaverScheduler} class is used to start the auto save Runnable task and schedule it.
 */
public class ChatDataSaverScheduler {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void startAutoSaveTask(MinecraftServer server, long interval, TimeUnit timeUnit) {
        ChatDataAutoSaver saverTask = new ChatDataAutoSaver(server);
        scheduler.scheduleAtFixedRate(saverTask, 1, interval, timeUnit);
    }

    public void stopAutoSaveTask() {
        scheduler.shutdown();
    }
}
