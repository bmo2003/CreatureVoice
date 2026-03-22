// SPDX-FileCopyrightText: 2025 bmo2003
// SPDX-License-Identifier: GPL-3.0-or-later
package com.owlmaddie.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.owlmaddie.utils.ClientEntityFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Mob;
import org.lwjgl.openal.AL10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TtsManager converts mob speech text to audio using the ElevenLabs Flash TTS API,
 * then plays it back as 3D positional audio via OpenAL so the sound comes from the
 * mob's location in the world with natural distance falloff.
 *
 * Voice assignment: each mob gets a deterministic voice derived from its UUID, so the
 * same mob always sounds the same and nearby mobs have different voices.
 *
 * Threading: HTTP requests run on a dedicated background thread. All OpenAL operations
 * (buffer creation, source positioning, playback) are scheduled on the render thread
 * where Minecraft's OpenAL context is current. A tick() method polled each client tick
 * cleans up finished or interrupted sources.
 */
public class TtsManager {
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturevoice");

    private static String apiKey = "";

    // Ordered list of ElevenLabs voice IDs fetched on world join
    private static final List<String> voiceIds = new ArrayList<>();

    // Background thread for ElevenLabs HTTP requests — single thread so fetchVoices()
    // always completes before the first speak() runs, since both share the same queue.
    private static final ExecutorService ttsThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CreatureVoice-TTS");
        t.setDaemon(true);
        return t;
    });

    // Incremented each time speak() is called. The render-thread callback checks this
    // before starting playback; if it has moved on, the audio is discarded.
    private static final AtomicInteger speakGeneration = new AtomicInteger(0);

    // OpenAL handles for the currently active 3D audio source.
    // Written on the render thread, read on the render thread in tick().
    // Zero means no active source.
    private static int activeAlSource = 0;
    private static int activeAlBuffer = 0;
    private static int activeAlGeneration = -1;
    private static UUID activeAlMobId = null;

    // Sample rate matching ElevenLabs pcm_24000 output format
    private static final int SAMPLE_RATE = 24000;

    // Voice audio fades linearly from full volume at VOICE_REF_DISTANCE to silence
    // at VOICE_MAX_DISTANCE. We control gain manually so it actually reaches zero
    // (OpenAL's built-in inverse model asymptotes and never fully silences).
    private static final float VOICE_REF_DISTANCE = 3.0f;   // full volume within 3 blocks
    private static final float VOICE_MAX_DISTANCE = 32.0f;  // silent at 32 blocks

    /** Store the ElevenLabs API key. Called from ClientInit on world join. */
    public static void setApiKey(String key) {
        apiKey = key != null ? key.trim() : "";
    }

    /**
     * Fetch the full list of available ElevenLabs voices and cache their IDs.
     * Submitted to the TTS thread so any queued speak() calls wait until this completes.
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
     * The same UUID always maps to the same index, so the same mob always has the same voice.
     */
    private static String getVoiceId(UUID mobId) {
        synchronized (voiceIds) {
            if (voiceIds.isEmpty()) return null;
            long hash = mobId.getLeastSignificantBits() ^ mobId.getMostSignificantBits();
            int index = (int) (Math.abs(hash) % voiceIds.size());
            return voiceIds.get(index);
        }
    }

    /**
     * Strip behavior tags and asterisk emotes from text before sending to ElevenLabs.
     * Tags like <LEAD> or <FRIENDSHIP 2> are for game logic, not speech.
     */
    private static String cleanForSpeech(String text) {
        text = text.replaceAll("<[^>]+>", "");                     // remove <TAG> tokens
        text = text.replaceAll("\\[EN:[^\\]]*\\]?", "");           // remove [EN: subtitle] tags (TTS speaks native language only, closing ] optional)
        text = text.replaceAll("\\*[^*]*\\s[^*]*\\*\\s*", "");    // multi-word *emote* → remove
        text = text.replaceAll("\\*([^*\\s]+)\\*", "$1");         // single-word *emphasis* → unwrap
        return text.trim();
    }

    /**
     * Convert mob speech text to audio and play it at the mob's 3D world position.
     * Interrupts any currently playing mob speech immediately.
     * Safe to call from any thread — network and OpenAL work is dispatched internally.
     *
     * @param mobId   the mob's UUID, used to pick a consistent voice and locate it in the world
     * @param rawText the LLM response text (may contain behavior tags)
     */
    public static void speak(UUID mobId, String rawText) {
        if (apiKey.isEmpty()) {
            LOGGER.warn("No ElevenLabs API key set — TTS disabled");
            return;
        }

        // Advance the generation counter. The render-thread tick() will stop the currently
        // playing source on the next frame, and any in-flight API response will be discarded.
        int myGeneration = speakGeneration.incrementAndGet();

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

                // Discard if a newer speak() arrived while the API call was in flight
                if (speakGeneration.get() != myGeneration) return;

                if (pcmData != null && pcmData.length > 0) {
                    LOGGER.info("TTS received {} bytes for mob {}", pcmData.length, mobId);
                    schedulePlayback(pcmData, mobId, myGeneration);
                }
            } catch (Exception e) {
                LOGGER.error("TTS error for mob {}: {}", mobId, e.getMessage());
            }
        });
    }

    /** POST text to ElevenLabs and return raw PCM bytes (24 kHz 16-bit mono LE), or null on failure. */
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

    /**
     * Wraps the raw PCM bytes in a direct ByteBuffer and schedules OpenAL playback
     * on the render thread where Minecraft's OpenAL context is current.
     */
    private static void schedulePlayback(byte[] pcmData, UUID mobId, int generation) {
        // Allocate a direct buffer — OpenAL requires native memory, not heap memory.
        // ByteOrder.LITTLE_ENDIAN matches the ElevenLabs pcm_24000 output format.
        ByteBuffer pcmBuffer = ByteBuffer.allocateDirect(pcmData.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        pcmBuffer.put(pcmData).flip();

        Minecraft.getInstance().execute(() -> {
            // Double-check generation on the render thread; another speak() may have
            // arrived between the TTS thread queuing this and the render thread running it.
            if (generation != speakGeneration.get()) return;

            // Look up the mob's current position in the world
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            Mob mob = ClientEntityFinder.getEntityByUUID(mc.level, mobId);
            if (mob == null) return;  // mob left render range, skip

            // Position the audio near the mob's mouth (60% up its bounding box)
            float x = (float) mob.getX();
            float y = (float) (mob.getY() + mob.getBbHeight() * 0.6);
            float z = (float) mob.getZ();

            // Stop and clean up any previous source before starting the new one
            stopActiveSource();

            // Upload PCM data to an OpenAL buffer
            int alBuf = AL10.alGenBuffers();
            AL10.alBufferData(alBuf, AL10.AL_FORMAT_MONO16, pcmBuffer, SAMPLE_RATE);

            // Create a positional source and attach the buffer
            int alSrc = AL10.alGenSources();
            AL10.alSourcei(alSrc, AL10.AL_BUFFER, alBuf);

            // Set the 3D position — OpenAL uses this for directional panning (left/right ear)
            // relative to the listener, which Minecraft keeps at the camera position.
            AL10.alSource3f(alSrc, AL10.AL_POSITION, x, y, z);

            // Disable OpenAL's built-in distance attenuation — we set AL_GAIN manually
            // each tick so the volume fades linearly to true zero rather than asymptoting.
            AL10.alSourcef(alSrc, AL10.AL_ROLLOFF_FACTOR, 0.0f);

            AL10.alSourcePlay(alSrc);

            activeAlSource = alSrc;
            activeAlBuffer = alBuf;
            activeAlGeneration = generation;
            activeAlMobId = mobId;
        });
    }

    /**
     * Called every client tick from ClientInit. Updates volume and position for the active
     * source, and cleans up sources that have finished or been superseded.
     * Must be called on the render thread (where the OpenAL context is current).
     */
    public static void tick() {
        if (activeAlSource == 0) return;

        // If a newer speak() arrived, stop the current source immediately
        if (activeAlGeneration != speakGeneration.get()) {
            stopActiveSource();
            return;
        }

        // If the source finished playing naturally, clean it up
        if (AL10.alGetSourcei(activeAlSource, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
            stopActiveSource();
            return;
        }

        // Update source position and gain each tick so panning and volume stay accurate
        // as both the player and the mob move around.
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || activeAlMobId == null) return;

        Mob mob = ClientEntityFinder.getEntityByUUID(mc.level, activeAlMobId);
        if (mob == null) return;

        // Keep the source glued to the mob as it moves
        float x = (float) mob.getX();
        float y = (float) (mob.getY() + mob.getBbHeight() * 0.6);
        float z = (float) mob.getZ();
        AL10.alSource3f(activeAlSource, AL10.AL_POSITION, x, y, z);

        // Linear gain: 1.0 within VOICE_REF_DISTANCE, fades to 0.0 at VOICE_MAX_DISTANCE
        double dist = mc.player.distanceTo(mob);
        float gain;
        if (dist <= VOICE_REF_DISTANCE) {
            gain = 1.0f;
        } else if (dist >= VOICE_MAX_DISTANCE) {
            gain = 0.0f;
        } else {
            gain = 1.0f - (float) (dist - VOICE_REF_DISTANCE)
                    / (VOICE_MAX_DISTANCE - VOICE_REF_DISTANCE);
        }
        AL10.alSourcef(activeAlSource, AL10.AL_GAIN, gain);
    }

    /** Stop and delete the active OpenAL source and buffer. Must be on the render thread. */
    private static void stopActiveSource() {
        if (activeAlSource != 0) {
            AL10.alSourceStop(activeAlSource);
            AL10.alDeleteSources(activeAlSource);
            activeAlSource = 0;
        }
        if (activeAlBuffer != 0) {
            AL10.alDeleteBuffers(activeAlBuffer);
            activeAlBuffer = 0;
        }
        activeAlGeneration = -1;
        activeAlMobId = null;
    }
}
