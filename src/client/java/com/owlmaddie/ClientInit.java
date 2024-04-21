package com.owlmaddie;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.ui.BubbleRenderer;
import com.owlmaddie.ui.ClickHandler;
import com.owlmaddie.ui.PlayerMessageManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * The {@code ClientInit} class initializes this mod in the client and defines all hooks into the
 * render pipeline to draw chat bubbles, text, and entity icons.
 */
public class ClientInit implements ClientModInitializer {
    private static long tickCounter = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;
            PlayerMessageManager.tickUpdate();
        });

        ClickHandler.register();

        // Register an event callback to render text bubbles
        WorldRenderEvents.LAST.register((context) -> {
            BubbleRenderer.drawTextAboveEntities(context, tickCounter, context.tickDelta());
        });

        // Register an event callback for when the client disconnects from a server or changes worlds
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // Clear or reset the ChatDataManager
            ChatDataManager.getClientInstance().clearData();
        });
    }
}
