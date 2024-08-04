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
    private MinecraftServer server = null;

    public void startAutoSaveTask(MinecraftServer server, long interval, TimeUnit timeUnit) {
        this.server = server;
        ChatDataAutoSaver saverTask = new ChatDataAutoSaver(server);
        scheduler.scheduleAtFixedRate(saverTask, 1, interval, timeUnit);
    }

    public void stopAutoSaveTask() {
        scheduler.shutdown();
    }

    // Schedule a task to run after 1 tick (basically immediately)
    public void scheduleTask(Runnable task) {
        scheduler.schedule(() -> server.execute(task), 50, TimeUnit.MILLISECONDS);
    }
}
