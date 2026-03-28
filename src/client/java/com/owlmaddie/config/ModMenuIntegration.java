// SPDX-FileCopyrightText: 2025 bmo2003
// SPDX-License-Identifier: GPL-3.0-or-later
package com.owlmaddie.config;

import com.owlmaddie.commands.ConfigurationHandler;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;

/**
 * ModMenuIntegration registers the CreatureVoice config screen with Mod Menu.
 *
 * Sections:
 *   Voice Input  — Deepgram API key
 *   Voice Output — Chatterbox TTS server URL
 *   Backend      — LLM API key, URL, model
 *   Gameplay     — chat bubbles, whitelist/blacklist, auto-response tuning
 *
 * All changes write to creaturechat.json and hot-reload the voice managers.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ConfigurationHandler.Config config = ClientConfigManager.load();

            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Component.literal("CreatureVoice Settings"))
                    .setSavingRunnable(() -> ClientConfigManager.save(config));

            ConfigEntryBuilder entry = builder.entryBuilder();

            // ── Voice Input ───────────────────────────────────────────────────────
            ConfigCategory voiceInput = builder.getOrCreateCategory(Component.literal("Voice Input"));

            voiceInput.addEntry(entry
                    .startStrField(Component.literal("Deepgram API Key"), config.getDeepgramApiKey())
                    .setDefaultValue("")
                    .setTooltip(Component.literal("Your Deepgram Nova-3 API key for speech-to-text."),
                                Component.literal("Get one at deepgram.com — free tier covers light use."))
                    .setSaveConsumer(config::setDeepgramApiKey)
                    .build());

            // ── Voice Output ──────────────────────────────────────────────────────
            ConfigCategory voiceOutput = builder.getOrCreateCategory(Component.literal("Voice Output"));

            voiceOutput.addEntry(entry
                    .startStrField(Component.literal("Chatterbox Server URL"), config.getChatterboxUrl())
                    .setDefaultValue("http://localhost:4123")
                    .setTooltip(Component.literal("URL of your local Chatterbox TTS server."),
                                Component.literal("Run chatterbox-tts-api locally — free, unlimited, no API key needed."))
                    .setSaveConsumer(config::setChatterboxUrl)
                    .build());

            // ── Backend ───────────────────────────────────────────────────────────
            ConfigCategory backend = builder.getOrCreateCategory(Component.literal("Backend"));

            backend.addEntry(entry
                    .startStrField(Component.literal("LLM API Key"), config.getApiKey())
                    .setDefaultValue("")
                    .setTooltip(Component.literal("Your Anthropic API key (sk-ant-...) or CreatureVoice token-shop key (cc_...)."),
                                Component.literal("Token-shop keys auto-set the URL below."))
                    .setSaveConsumer(config::setApiKey)
                    .build());

            backend.addEntry(entry
                    .startStrField(Component.literal("LLM URL"), config.getUrl())
                    .setDefaultValue("https://api.anthropic.com/v1/chat/completions")
                    .setTooltip(Component.literal("API endpoint. Leave as default for Anthropic direct,"),
                                Component.literal("or set to your token-shop proxy URL."))
                    .setSaveConsumer(config::setUrl)
                    .build());

            backend.addEntry(entry
                    .startStrField(Component.literal("Model"), config.getModel())
                    .setDefaultValue("claude-haiku-4-5-20251001")
                    .setTooltip(Component.literal("Claude model ID for mob responses."),
                                Component.literal("Haiku is fastest and cheapest. Sonnet gives richer replies."))
                    .setSaveConsumer(config::setModel)
                    .build());

            backend.addEntry(entry
                    .startIntField(Component.literal("Request Timeout (seconds)"), config.getTimeout())
                    .setDefaultValue(10)
                    .setMin(5)
                    .setMax(60)
                    .setTooltip(Component.literal("How long to wait for an LLM response before giving up."))
                    .setSaveConsumer(config::setTimeout)
                    .build());

            // ── Gameplay ──────────────────────────────────────────────────────────
            ConfigCategory gameplay = builder.getOrCreateCategory(Component.literal("Gameplay"));

            gameplay.addEntry(entry
                    .startBooleanToggle(Component.literal("Show Chat Bubbles"), config.getChatBubbles())
                    .setDefaultValue(false)
                    .setTooltip(Component.literal("Show speech bubbles above all mobs when they speak."),
                                Component.literal("Off by default — bubbles only appear for foreign-language mobs"),
                                Component.literal("when subtitles are active. Enable this to read every response."))
                    .setSaveConsumer(config::setChatBubbles)
                    .build());

            gameplay.addEntry(entry
                    .startBooleanToggle(Component.literal("Realism Mode"), config.getRealismMode())
                    .setDefaultValue(false)
                    .setTooltip(Component.literal("Only bipedal (two-legged) mobs can speak or be spoken to."),
                                Component.literal("Allowed: zombies, skeletons, villagers, endermen, iron golems, etc."),
                                Component.literal("Excluded: cows, pigs, cats, horses, wolves, and other four-legged animals."))
                    .setSaveConsumer(config::setRealismMode)
                    .build());

            gameplay.addEntry(entry
                    .startStrField(Component.literal("World Story / Context"), config.getStory())
                    .setDefaultValue("")
                    .setTooltip(Component.literal("Optional background lore injected into every mob conversation."),
                                Component.literal("E.g. 'This world is at war. Magic has been outlawed.'"))
                    .setSaveConsumer(config::setStory)
                    .build());

            // Whitelist — opens a full mob picker screen.
            // Saves to disk immediately when Done is clicked in the picker so the
            // player doesn't need to find and click a separate Save button.
            gameplay.addEntry(new MobListButtonEntry(
                    Component.literal("Mob Whitelist"),
                    config.getWhitelist() != null ? config.getWhitelist() : new ArrayList<>(),
                    newList -> { config.setWhitelist(newList); ClientConfigManager.save(config); },
                    () -> Minecraft.getInstance().screen,
                    "Whitelist — only these mobs can speak (empty = all mobs)"));

            // Blacklist — opens a full mob picker screen.
            gameplay.addEntry(new MobListButtonEntry(
                    Component.literal("Mob Blacklist"),
                    config.getBlacklist() != null ? config.getBlacklist() : new ArrayList<>(),
                    newList -> { config.setBlacklist(newList); ClientConfigManager.save(config); },
                    () -> Minecraft.getInstance().screen,
                    "Blacklist — these mobs will never speak (overrides whitelist)"));

            gameplay.addEntry(entry
                    .startIntField(Component.literal("Max Player Auto-Responses"), config.getMaxPlayerAutoResponses())
                    .setDefaultValue(10)
                    .setMin(0)
                    .setMax(50)
                    .setTooltip(Component.literal("How many automatic mob greetings a player triggers before mobs go quiet."))
                    .setSaveConsumer(config::setMaxPlayerAutoResponses)
                    .build());

            gameplay.addEntry(entry
                    .startIntField(Component.literal("Player Auto-Response Cooldown (seconds)"), config.getPlayerAutoCooldownSeconds())
                    .setDefaultValue(3)
                    .setMin(1)
                    .setMax(60)
                    .setTooltip(Component.literal("Minimum seconds between automatic mob greetings triggered by a player."))
                    .setSaveConsumer(config::setPlayerAutoCooldownSeconds)
                    .build());

            gameplay.addEntry(entry
                    .startIntField(Component.literal("Max Entity Auto-Responses"), config.getMaxEntityAutoResponses())
                    .setDefaultValue(3)
                    .setMin(0)
                    .setMax(20)
                    .setTooltip(Component.literal("How many times a single mob will initiate conversation on its own."))
                    .setSaveConsumer(config::setMaxEntityAutoResponses)
                    .build());

            gameplay.addEntry(entry
                    .startIntField(Component.literal("Entity Auto-Response Cooldown (seconds)"), config.getEntityAutoCooldownSeconds())
                    .setDefaultValue(3)
                    .setMin(1)
                    .setMax(60)
                    .setTooltip(Component.literal("Minimum seconds between a single mob's unprompted messages."))
                    .setSaveConsumer(config::setEntityAutoCooldownSeconds)
                    .build());

            return builder.build();
        };
    }
}
