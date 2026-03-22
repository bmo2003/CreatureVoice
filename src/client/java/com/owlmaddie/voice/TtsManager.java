// SPDX-FileCopyrightText: 2025 bmo2003
// SPDX-License-Identifier: GPL-3.0-or-later
package com.owlmaddie.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TtsManager converts mob speech text to audio using the ElevenLabs Flash TTS API.
 *
 * Each mob gets a deterministic voice assigned from the full list of available ElevenLabs
 * voices, derived by hashing the mob's UUID. The same mob always gets the same voice, and
 * the large pool of voices minimises collisions between nearby mobs.
 *
 * Audio is returned as raw 16 kHz mono 16-bit PCM and played via javax.sound.sampled on
 * a dedicated background thread so the game thread is never blocked.
 *
 * Phase 4 will replace the basic playback here with OpenAL 3D positional audio so the
 * sound comes from the mob's in-world position.
 */
public class TtsManager {
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturevoice");

    private static String apiKey = "";

    // Ordered list of ElevenLabs voice IDs fetched on world join
    private static final List<String> voiceIds = new ArrayList<>();

    // PCM format matching ElevenLabs pcm_24000 output: 24 kHz, 16-bit, mono, signed, little-endian
    private static final AudioFormat PLAYBACK_FORMAT = new AudioFormat(24000f, 16, 1, true, false);

    // Single daemon thread for all TTS network calls and audio playback.
    // Using one thread means fetchVoices() always finishes before the first speak() runs,
    // since both are submitted to the same queue.
    private static final ExecutorService ttsThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CreatureVoice-TTS");
        t.setDaemon(true);
        return t;
    });

    // Increments each time speak() is called. A TTS task discards its audio if the
    // generation has moved on by the time it finishes the API call — i.e. the player
    // already said something new while this request was in flight.
    private static final AtomicInteger speakGeneration = new AtomicInteger(0);

    // The currently playing audio line — closed immediately when a new speak() arrives
    // so the mob stops mid-sentence and starts the new response without delay.
    private static volatile SourceDataLine activeLine = null;

    /** Store the ElevenLabs API key. Called from ClientInit on world join. */
    public static void setApiKey(String key) {
        apiKey = key != null ? key.trim() : "";
    }

    /**
     * Fetch the full list of available ElevenLabs voices and cache their IDs.
     * Runs on the TTS thread so any queued speak() calls wait until this is done.
     */
    public static void fetchVoices() {
        if (apiKey.isEmpty()) return;
        ttsThread.submit(() -> {
            try {
                URL url = new URL("https://api.elevenlabs.io/v1/voices");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("xi-api-key", apiKey);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int status = conn.getResponseCode();
                if (status != 200) {
                    LOGGER.error("Failed to fetch ElevenLabs voices: HTTP {}", status);
                    return;
                }

                String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonArray voices = JsonParser.parseString(body)
                        .getAsJsonObject()
                        .getAsJsonArray("voices");

                synchronized (voiceIds) {
                    voiceIds.clear();
                    for (JsonElement v : voices) {
                        voiceIds.add(v.getAsJsonObject().get("voice_id").getAsString());
                    }
                }
                LOGGER.info("Loaded {} ElevenLabs voices", voiceIds.size());

            } catch (Exception e) {
                LOGGER.error("Failed to fetch ElevenLabs voices: {}", e.getMessage());
            }
        });
    }

    /**
     * Deterministically pick a voice for a mob by hashing its UUID.
     * The same UUID always maps to the same index in the voice list.
     */
    private static String getVoiceId(UUID mobId) {
        synchronized (voiceIds) {
            if (voiceIds.isEmpty()) return null;
            // XOR the two 64-bit halves for better bit distribution than hashCode()
            long hash = mobId.getLeastSignificantBits() ^ mobId.getMostSignificantBits();
            int index = (int) (Math.abs(hash) % voiceIds.size());
            return voiceIds.get(index);
        }
    }

    /**
     * Strip behavior tags and asterisk emotes from text before sending to ElevenLabs.
     * The LLM sometimes embeds tags like <LEAD> or <FRIENDSHIP 2> in the response text.
     */
    private static String cleanForSpeech(String text) {
        text = text.replaceAll("<[^>]+>", "");                          // remove <TAG> and <TAG value>
        text = text.replaceAll("\\*[^*]*\\s[^*]*\\*\\s*", "");         // multi-word *emote actions* → remove
        text = text.replaceAll("\\*([^*\\s]+)\\*", "$1");              // single-word *emphasis* → unwrap
        return text.trim();
    }

    /**
     * Convert mob speech text to audio and play it.
     * If a mob is already speaking, it is interrupted immediately.
     * Safe to call from the main game thread — all work happens on the TTS background thread.
     *
     * @param mobId   the mob's UUID, used to deterministically choose a voice
     * @param rawText the LLM response text (may contain behavior tags)
     */
    public static void speak(UUID mobId, String rawText) {
        if (apiKey.isEmpty()) {
            LOGGER.warn("No ElevenLabs API key set — TTS disabled");
            return;
        }

        // Advance the generation counter and stop any currently playing audio immediately
        int myGeneration = speakGeneration.incrementAndGet();
        SourceDataLine lineToStop = activeLine;
        if (lineToStop != null) {
            lineToStop.close(); // stops playback and unblocks the write loop in playPcm()
        }

        ttsThread.submit(() -> {
            String voiceId = getVoiceId(mobId);
            if (voiceId == null) {
                LOGGER.warn("No voices loaded — skipping TTS for {}", mobId);
                return;
            }

            String text = cleanForSpeech(rawText);
            if (text.isBlank()) return;

            try {
                byte[] pcmData = requestTts(voiceId, text);

                // Discard the audio if a newer speak() arrived while the API call was in flight
                if (speakGeneration.get() != myGeneration) return;

                if (pcmData != null && pcmData.length > 0) {
                    LOGGER.info("TTS received {} bytes for mob {}", pcmData.length, mobId);
                    playPcm(pcmData);
                }
            } catch (Exception e) {
                LOGGER.error("TTS error for mob {}: {}", mobId, e.getMessage());
            }
        });
    }

    /** POST text to ElevenLabs and return raw PCM bytes, or null on failure. */
    private static byte[] requestTts(String voiceId, String text) throws IOException {
        URL url = new URL("https://api.elevenlabs.io/v1/text-to-speech/" + voiceId
                + "?output_format=pcm_24000");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("xi-api-key", apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("model_id", "eleven_flash_v2_5");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        if (status != 200) {
            InputStream err = conn.getErrorStream();
            String errBody = err != null ? new String(err.readAllBytes(), StandardCharsets.UTF_8) : "(no body)";
            LOGGER.error("ElevenLabs HTTP {}: {}", status, errBody);
            return null;
        }

        return conn.getInputStream().readAllBytes();
    }

    /** Play raw 24 kHz mono signed 16-bit little-endian PCM via javax.sound.sampled. */
    private static void playPcm(byte[] pcmData) {
        try {
            // frame length = total bytes / bytes per frame (2 bytes for 16-bit mono)
            AudioInputStream audioStream = new AudioInputStream(
                    new ByteArrayInputStream(pcmData),
                    PLAYBACK_FORMAT,
                    pcmData.length / 2L);

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, PLAYBACK_FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                LOGGER.error("Audio output line not supported on this system");
                return;
            }

            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            activeLine = line;
            try {
                line.open(PLAYBACK_FORMAT);
                line.start();

                byte[] buf = new byte[4096];
                int bytesRead;
                // line.write() returns 0 if the line was closed mid-playback (interrupted)
                while ((bytesRead = audioStream.read(buf, 0, buf.length)) != -1) {
                    if (!line.isOpen()) break;
                    line.write(buf, 0, bytesRead);
                }
                if (line.isOpen()) line.drain();
            } finally {
                activeLine = null;
                if (line.isOpen()) line.close();
            }
        } catch (Exception e) {
            // A closed-line exception is expected when interrupted — only log real errors
            if (activeLine != null) {
                LOGGER.error("Audio playback error: {}", e.getMessage());
            }
        }
    }
}
