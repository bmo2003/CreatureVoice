// SPDX-FileCopyrightText: 2025 bmo2003
// SPDX-License-Identifier: GPL-3.0-or-later
package com.owlmaddie.voice;

import com.google.gson.JsonObject;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TtsManager converts mob speech text to audio using a local Chatterbox TTS server,
 * then plays it back as 3D positional audio via OpenAL so the sound comes from the
 * mob's location in the world with natural distance falloff.
 *
 * Speech queue: only ONE mob speaks at a time. When multiple NPCs respond (overhear
 * reactions, mob-to-mob chat), their TTS requests fire immediately in the background
 * so the audio data is ready, but actual OpenAL playback is queued. The next mob's
 * audio starts only after the current speaker finishes. This prevents the cacophony
 * of multiple NPCs talking over each other simultaneously.
 *
 * Voice assignment: each mob gets a deterministic voice from 28 pre-built Chatterbox
 * voices, derived from its UUID so the same mob always sounds the same.
 *
 * Chatterbox runs locally — no cloud API, no usage limits, no API key needed.
 * Install: pip install chatterbox-tts, then run chatterbox-tts-api or similar server.
 */
public class TtsManager {
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturevoice");

    // Chatterbox server URL (default: localhost:4123 for chatterbox-tts-api)
    private static String serverUrl = "http://localhost:4123";

    // 28 pre-built Chatterbox voice names from devnen/Chatterbox-TTS-Server
    // Each mob gets a deterministic voice by hashing its UUID against this list
    private static final String[] VOICES = {
        "Abigail", "Adrian", "Alexander", "Alice", "Austin", "Axel",
        "Connor", "Cora", "Elena", "Eli", "Emily", "Everett",
        "Gabriel", "Gianna", "Henry", "Ian", "Jade", "Jeremiah",
        "Jordan", "Julian", "Layla", "Leonardo", "Michael", "Miles",
        "Olivia", "Ryan", "Taylor", "Thomas"
    };

    // Background thread for Chatterbox HTTP requests — single thread so TTS requests
    // are serialized (one at a time), preventing the server from being overwhelmed.
    private static final ExecutorService ttsThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CreatureVoice-TTS");
        t.setDaemon(true);
        return t;
    });

    // Per-mob generation counters. Incrementing a mob's counter cancels only that
    // mob's in-flight or playing audio, leaving other mobs' audio untouched.
    private static final Map<UUID, AtomicInteger> mobGenerations = new ConcurrentHashMap<>();

    // Currently playing OpenAL source (only ONE at a time). Accessed on render thread only.
    private static MobSource currentSpeaker = null;
    private static UUID currentSpeakerId = null;

    // Queue of ready-to-play audio waiting for the current speaker to finish.
    // Thread-safe because TTS thread adds entries and render thread consumes them.
    private static final ConcurrentLinkedQueue<QueuedAudio> playbackQueue = new ConcurrentLinkedQueue<>();

    /** Holds PCM audio data ready for OpenAL playback, waiting in the speech queue. */
    private static class QueuedAudio {
        final UUID mobId;
        final byte[] pcmData;
        final int sampleRate;
        final int generation;
        QueuedAudio(UUID mobId, byte[] pcmData, int sampleRate, int generation) {
            this.mobId = mobId;
            this.pcmData = pcmData;
            this.sampleRate = sampleRate;
            this.generation = generation;
        }
    }

    /** Bundles the OpenAL handles and generation stamp for the currently playing mob. */
    private static class MobSource {
        final int alSource;
        final int alBuffer;
        final int generation;
        MobSource(int src, int buf, int gen) { alSource = src; alBuffer = buf; generation = gen; }
    }

    // Voice audio fades linearly from full volume at VOICE_REF_DISTANCE to silence
    // at VOICE_MAX_DISTANCE. We control gain manually so it actually reaches zero
    // (OpenAL's built-in inverse model asymptotes and never fully silences).
    private static final float VOICE_REF_DISTANCE = 3.0f;   // full volume within 3 blocks
    private static final float VOICE_MAX_DISTANCE = 32.0f;  // silent at 32 blocks

    /** Set the Chatterbox server URL. Called from ClientInit on world join. */
    public static void setServerUrl(String url) {
        if (url != null && !url.isBlank()) {
            // Strip trailing slash for consistency
            serverUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
        LOGGER.info("Chatterbox TTS server set to: {}", serverUrl);
    }

    /**
     * Deterministically pick a voice for a mob by hashing its UUID.
     * The same UUID always maps to the same voice name, so the same mob always
     * sounds the same and nearby mobs have different voices.
     */
    private static String getVoiceName(UUID mobId) {
        long hash = mobId.getLeastSignificantBits() ^ mobId.getMostSignificantBits();
        int index = (int) (Math.abs(hash) % VOICES.length);
        return VOICES[index];
    }

    /**
     * Strip behavior tags and asterisk emotes from text before sending to TTS.
     * Tags like <LEAD> or <FRIENDSHIP 2> are for game logic, not speech.
     */
    private static String cleanForSpeech(String text) {
        text = text.replaceAll("<[^>]+>", "");                     // remove <TAG> tokens
        text = text.replaceAll("\\[EN:[^\\]]*\\]?", "");           // remove [EN: subtitle] tags
        text = text.replaceAll("\\([^)]*\\)", "");                 // remove (parenthetical actions)
        text = text.replaceAll("\\*[^*]*\\s[^*]*\\*\\s*", "");    // multi-word *emote* → remove
        text = text.replaceAll("\\*([^*\\s]+)\\*", "$1");         // single-word *emphasis* → unwrap
        return text.trim();
    }

    /**
     * Convert mob speech text to audio and queue it for playback. The TTS HTTP request
     * fires immediately so the audio data is ready, but actual OpenAL playback waits
     * until no other mob is currently speaking.
     *
     * If this mob already has audio playing or queued, the old audio is cancelled so
     * only the newest message is heard.
     *
     * @param mobId   the mob's UUID, used to pick a consistent voice and locate it in the world
     * @param rawText the LLM response text (may contain behavior tags)
     */
    public static void speak(UUID mobId, String rawText) {
        // Advance only this mob's generation counter. Any in-flight request for this
        // mob will be discarded, but other mobs' audio continues uninterrupted.
        AtomicInteger genCounter = mobGenerations.computeIfAbsent(mobId, k -> new AtomicInteger(0));
        int myGeneration = genCounter.incrementAndGet();

        ttsThread.submit(() -> {
            String voiceName = getVoiceName(mobId);

            String text = cleanForSpeech(rawText);
            if (text.isBlank()) return;

            try {
                byte[] wavData = requestTts(voiceName, text);

                // Discard if a newer speak() for this same mob arrived while we were waiting
                AtomicInteger counter = mobGenerations.get(mobId);
                if (counter == null || counter.get() != myGeneration) return;

                if (wavData != null && wavData.length > 44) {
                    // Parse WAV header to extract raw PCM data and sample rate
                    WavInfo wav = parseWav(wavData);
                    if (wav != null) {
                        LOGGER.info("TTS received {} PCM bytes for mob {} (voice={}, {}Hz) — queuing",
                                wav.pcmData.length, mobId, voiceName, wav.sampleRate);
                        playbackQueue.add(new QueuedAudio(mobId, wav.pcmData, wav.sampleRate, myGeneration));
                    }
                }
            } catch (Exception e) {
                LOGGER.error("TTS error for mob {} (voice={}): {}", mobId, voiceName, e.getMessage());
            }
        });
    }

    /**
     * POST text to the Chatterbox server using the OpenAI-compatible speech endpoint.
     * Returns raw WAV bytes, or null on failure.
     */
    private static byte[] requestTts(String voiceName, String text) throws IOException {
        URL url = new URL(serverUrl + "/v1/audio/speech");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(60000); // local GPU inference can take a few seconds

        JsonObject body = new JsonObject();
        body.addProperty("input", text);
        body.addProperty("voice", voiceName);
        body.addProperty("response_format", "wav");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        if (status != 200) {
            InputStream err = conn.getErrorStream();
            String errBody = err != null ? new String(err.readAllBytes(), StandardCharsets.UTF_8) : "(no body)";
            LOGGER.error("Chatterbox HTTP {}: {}", status, errBody);
            return null;
        }

        return conn.getInputStream().readAllBytes();
    }

    /** Parsed WAV file info: raw PCM data, sample rate, and bits per sample. */
    private static class WavInfo {
        final byte[] pcmData;
        final int sampleRate;
        final int bitsPerSample;
        WavInfo(byte[] pcm, int rate, int bits) { pcmData = pcm; sampleRate = rate; bitsPerSample = bits; }
    }

    /**
     * Parse a WAV file to extract the raw PCM data, sample rate, and bit depth.
     * Handles standard RIFF WAV format. If the audio is 32-bit float, it gets
     * converted to 16-bit signed integer for OpenAL compatibility.
     */
    private static WavInfo parseWav(byte[] wavData) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(wavData).order(ByteOrder.LITTLE_ENDIAN);

            // RIFF header: "RIFF" + size + "WAVE"
            if (buf.remaining() < 12) return null;
            buf.position(0);
            int riff = buf.getInt(); // "RIFF"
            buf.getInt(); // file size
            int wave = buf.getInt(); // "WAVE"

            int sampleRate = 24000;
            int bitsPerSample = 16;
            int audioFormat = 1; // 1=PCM, 3=IEEE float

            // Find "fmt " and "data" chunks
            byte[] pcmData = null;
            while (buf.remaining() >= 8) {
                int chunkId = buf.getInt();
                int chunkSize = buf.getInt();

                if (chunkId == 0x20746D66) { // "fmt " in little-endian
                    int startPos = buf.position();
                    audioFormat = buf.getShort() & 0xFFFF;
                    int channels = buf.getShort() & 0xFFFF; // should be 1 (mono)
                    sampleRate = buf.getInt();
                    buf.getInt(); // byte rate
                    buf.getShort(); // block align
                    bitsPerSample = buf.getShort() & 0xFFFF;
                    buf.position(startPos + chunkSize); // skip any extra fmt bytes
                } else if (chunkId == 0x61746164) { // "data" in little-endian
                    pcmData = new byte[chunkSize];
                    buf.get(pcmData);
                } else {
                    // Skip unknown chunk
                    buf.position(buf.position() + chunkSize);
                }
            }

            if (pcmData == null) return null;

            // If 32-bit float (format 3), convert to 16-bit signed integer for OpenAL
            if (audioFormat == 3 && bitsPerSample == 32) {
                pcmData = float32ToInt16(pcmData);
                bitsPerSample = 16;
            }

            return new WavInfo(pcmData, sampleRate, bitsPerSample);
        } catch (Exception e) {
            LOGGER.error("Failed to parse WAV: {}", e.getMessage());
            return null;
        }
    }

    /** Convert 32-bit float PCM samples to 16-bit signed integer PCM. */
    private static byte[] float32ToInt16(byte[] floatData) {
        int numSamples = floatData.length / 4;
        byte[] int16Data = new byte[numSamples * 2];
        ByteBuffer floatBuf = ByteBuffer.wrap(floatData).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer int16Buf = ByteBuffer.wrap(int16Data).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < numSamples; i++) {
            float sample = floatBuf.getFloat();
            // Clamp to [-1.0, 1.0] then scale to 16-bit range
            sample = Math.max(-1.0f, Math.min(1.0f, sample));
            int16Buf.putShort((short) (sample * 32767.0f));
        }
        return int16Data;
    }

    /**
     * Called every client tick from ClientInit. Manages the speech queue:
     * - If no mob is currently speaking and the queue has entries, start the next one
     * - If a mob is speaking, update its 3D position and volume
     * - Clean up finished audio and advance to the next queued speaker
     * Must be called on the render thread (where the OpenAL context is current).
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            // World unloaded — clean up everything
            if (currentSpeaker != null) {
                cleanupSource(currentSpeaker);
                currentSpeaker = null;
                currentSpeakerId = null;
            }
            playbackQueue.clear();
            return;
        }

        // If someone is currently speaking, check if they're done
        if (currentSpeaker != null) {
            // If a newer speak() arrived for this mob, stop immediately
            AtomicInteger counter = mobGenerations.get(currentSpeakerId);
            boolean superseded = (counter == null || counter.get() != currentSpeaker.generation);

            // Check if playback finished naturally
            boolean finished = AL10.alGetSourcei(currentSpeaker.alSource, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING;

            // Check if the mob left render range
            Mob mob = ClientEntityFinder.getEntityByUUID(mc.level, currentSpeakerId);
            boolean gone = (mob == null);

            if (superseded || finished || gone) {
                cleanupSource(currentSpeaker);
                currentSpeaker = null;
                currentSpeakerId = null;
            } else {
                // Update 3D position and volume for the current speaker
                float x = (float) mob.getX();
                float y = (float) (mob.getY() + mob.getBbHeight() * 0.6);
                float z = (float) mob.getZ();
                AL10.alSource3f(currentSpeaker.alSource, AL10.AL_POSITION, x, y, z);

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
                AL10.alSourcef(currentSpeaker.alSource, AL10.AL_GAIN, gain);
            }
        }

        // If nobody is speaking, try to start the next queued audio
        if (currentSpeaker == null) {
            startNextInQueue(mc);
        }
    }

    /**
     * Pulls the next valid entry from the playback queue and starts OpenAL playback.
     * Skips entries whose generation has been superseded (mob spoke again while queued).
     */
    private static void startNextInQueue(Minecraft mc) {
        while (!playbackQueue.isEmpty()) {
            QueuedAudio next = playbackQueue.poll();
            if (next == null) break;

            // Skip if this mob's generation was superseded while waiting in the queue
            AtomicInteger counter = mobGenerations.get(next.mobId);
            if (counter == null || counter.get() != next.generation) continue;

            // Skip if the mob left render range
            Mob mob = ClientEntityFinder.getEntityByUUID(mc.level, next.mobId);
            if (mob == null) continue;

            // Start playback
            ByteBuffer pcmBuffer = ByteBuffer.allocateDirect(next.pcmData.length)
                    .order(ByteOrder.LITTLE_ENDIAN);
            pcmBuffer.put(next.pcmData).flip();

            float x = (float) mob.getX();
            float y = (float) (mob.getY() + mob.getBbHeight() * 0.6);
            float z = (float) mob.getZ();

            int alBuf = AL10.alGenBuffers();
            AL10.alBufferData(alBuf, AL10.AL_FORMAT_MONO16, pcmBuffer, next.sampleRate);

            int alSrc = AL10.alGenSources();
            AL10.alSourcei(alSrc, AL10.AL_BUFFER, alBuf);
            AL10.alSource3f(alSrc, AL10.AL_POSITION, x, y, z);
            AL10.alSourcef(alSrc, AL10.AL_ROLLOFF_FACTOR, 0.0f);
            AL10.alSourcePlay(alSrc);

            currentSpeaker = new MobSource(alSrc, alBuf, next.generation);
            currentSpeakerId = next.mobId;
            LOGGER.info("TTS playing for mob {} (voice={})", next.mobId, getVoiceName(next.mobId));
            return; // started one — wait for it to finish before starting another
        }
    }

    /** Free the OpenAL source and buffer for a MobSource. Must be on the render thread. */
    private static void cleanupSource(MobSource src) {
        AL10.alSourceStop(src.alSource);
        AL10.alDeleteSources(src.alSource);
        AL10.alDeleteBuffers(src.alBuffer);
    }
}
