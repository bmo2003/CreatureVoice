// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.network.ClientPackets;
import com.owlmaddie.particle.CreatureParticleFactory;
import com.owlmaddie.particle.LeadParticleFactory;
import com.owlmaddie.particle.Particles;
import com.owlmaddie.ui.BubbleRenderer;
import com.owlmaddie.ui.ClickHandler;
import com.owlmaddie.ui.InventoryKeyHandler;
import com.owlmaddie.ui.PlayerMessageManager;
import com.owlmaddie.utils.TickDelta;
import com.owlmaddie.inventory.ModMenus;
import com.owlmaddie.inventory.MobInventoryScreen;
import com.owlmaddie.voice.TtsManager;
import com.owlmaddie.voice.VoiceInputManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The {@code ClientInit} class initializes this mod in the client and defines all hooks into the
 * render pipeline to draw chat bubbles, text, and entity icons.
 */
public class ClientInit implements ClientModInitializer {
    private static long tickCounter = 0;

    // Hold V (or the player's rebound key) to speak to the mob your crosshair is on
    public static final KeyMapping TALK_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "key.creaturevoice.talk",
                    GLFW.GLFW_KEY_V,
                    "key.categories.creaturevoice"
            )
    );

    // Tracks whether V was held last tick so we can detect press and release edges
    private static boolean talkKeyHeldLastTick = false;

    @Override
    public void onInitializeClient() {
        // Register particle factories
        ParticleFactoryRegistry.getInstance().register(Particles.HEART_SMALL_PARTICLE,   CreatureParticleFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.HEART_BIG_PARTICLE,     CreatureParticleFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.FIRE_SMALL_PARTICLE,    CreatureParticleFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.FIRE_BIG_PARTICLE,      CreatureParticleFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.ATTACK_PARTICLE,        CreatureParticleFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.FLEE_PARTICLE,          CreatureParticleFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.FOLLOW_FRIEND_PARTICLE, CreatureParticleFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.FOLLOW_ENEMY_PARTICLE,  CreatureParticleFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.PROTECT_PARTICLE,       CreatureParticleFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.LEAD_FRIEND_PARTICLE,   CreatureParticleFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.LEAD_ENEMY_PARTICLE,    CreatureParticleFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.LEAD_PARTICLE,          LeadParticleFactory::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;
            PlayerMessageManager.tickUpdate();

            // Voice input: detect hold-to-talk key press and release edges
            if (client.player == null || client.level == null) return;

            boolean talkKeyHeldNow = TALK_KEY.isDown();

            if (talkKeyHeldNow && !talkKeyHeldLastTick) {
                // Key just pressed — find the mob the player's crosshair is on
                HitResult hit = client.hitResult;
                if (hit instanceof EntityHitResult entityHit
                        && entityHit.getEntity() instanceof Mob targetMob) {

                    VoiceInputManager.startRecording(targetMob, transcript -> {
                        // Transcript arrives on the main thread — send it to the server
                        // exactly as if the player had typed it in the old ChatScreen
                        ClientPackets.sendChat(targetMob, transcript);
                    });
                }
            } else if (!talkKeyHeldNow && talkKeyHeldLastTick) {
                // Key just released — stop capture and submit to Deepgram
                VoiceInputManager.stopRecording();
            }

            talkKeyHeldLastTick = talkKeyHeldNow;
        });

        // Register events
        ClickHandler.register();
        InventoryKeyHandler.register();
        ClientPackets.register();
        MenuScreens.register(ModMenus.MOB_INVENTORY, MobInventoryScreen::new);

        // Register an event callback to render text bubbles
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ctx -> {
            float delta = TickDelta.get(ctx);
            BubbleRenderer.drawTextAboveEntities(ctx, tickCounter, delta);
        });

        // Load API keys and fetch ElevenLabs voices when joining a world
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            loadApiKeys();
            TtsManager.fetchVoices();
        });

        // Register an event callback for when the client disconnects from a server or changes worlds
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ChatDataManager.getClientInstance().clearData();
        });
    }

    /**
     * Reads voice API keys from creaturechat.json and passes them to the voice managers.
     * Checks the run root first, then the saves folder as a fallback.
     */
    private static void loadApiKeys() {
        Path[] candidates = {
                Paths.get("creaturechat.json"),
                Paths.get("saves", "creaturechat.json")
        };
        for (Path path : candidates) {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();

                    if (obj.has("deepgramApiKey")) {
                        VoiceInputManager.setApiKey(obj.get("deepgramApiKey").getAsString());
                        VoiceInputManager.LOGGER.info("Deepgram API key loaded from {}", path);
                    } else {
                        VoiceInputManager.LOGGER.warn("deepgramApiKey not found in {} — voice input disabled", path);
                    }

                    if (obj.has("elevenLabsApiKey")) {
                        TtsManager.setApiKey(obj.get("elevenLabsApiKey").getAsString());
                        TtsManager.LOGGER.info("ElevenLabs API key loaded from {}", path);
                    } else {
                        TtsManager.LOGGER.warn("elevenLabsApiKey not found in {} — TTS disabled", path);
                    }

                    return;
                } catch (Exception e) {
                    VoiceInputManager.LOGGER.warn("Failed to read config at {}: {}", path, e.getMessage());
                }
            }
        }
        VoiceInputManager.LOGGER.warn("creaturechat.json not found — voice input and TTS disabled");
    }
}
