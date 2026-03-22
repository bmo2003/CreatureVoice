// SPDX-FileCopyrightText: 2025 bmo2003
// SPDX-License-Identifier: GPL-3.0-or-later
package com.owlmaddie.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.owlmaddie.commands.ConfigurationHandler;
import com.owlmaddie.voice.TtsManager;
import com.owlmaddie.voice.VoiceInputManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ClientConfigManager reads and writes creaturechat.json for the in-game config screen.
 * It preserves all existing fields (server-side settings, story, whitelist etc.) when
 * saving — only the fields changed in the config screen are updated.
 * After saving it immediately re-applies the voice API keys to the running managers
 * so changes take effect without a game restart.
 */
public class ClientConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("creaturevoice");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Same search order as ClientInit.loadApiKeys() — run root first, saves folder fallback
    private static final Path[] SEARCH_PATHS = {
            Paths.get("creaturechat.json"),
            Paths.get("saves", "creaturechat.json")
    };

    /**
     * Loads the full config from whichever creaturechat.json file exists.
     * Returns a default Config if no file is found.
     */
    public static ConfigurationHandler.Config load() {
        for (Path path : SEARCH_PATHS) {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    ConfigurationHandler.Config config = GSON.fromJson(reader, ConfigurationHandler.Config.class);
                    if (config != null) return config;
                } catch (Exception e) {
                    LOGGER.warn("Failed to read config at {}: {}", path, e.getMessage());
                }
            }
        }
        return new ConfigurationHandler.Config();
    }

    /**
     * Writes the config back to creaturechat.json (run root), then immediately
     * re-applies the voice API keys to the running managers.
     */
    public static void save(ConfigurationHandler.Config config) {
        // Always save to the run root path
        Path savePath = SEARCH_PATHS[0];
        try (Writer writer = Files.newBufferedWriter(savePath)) {
            GSON.toJson(config, writer);
            LOGGER.info("Config saved to {}", savePath);
        } catch (Exception e) {
            LOGGER.error("Failed to save config to {}: {}", savePath, e.getMessage());
        }

        // Re-apply voice keys immediately so the player doesn't need to restart
        VoiceInputManager.setApiKey(config.getDeepgramApiKey());
        TtsManager.setApiKey(config.getElevenLabsApiKey());
    }
}
