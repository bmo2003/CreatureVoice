// SPDX-FileCopyrightText: 2025 bmo2003
// SPDX-License-Identifier: GPL-3.0-or-later
package com.owlmaddie.voice;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * VoiceInputManager handles microphone capture and speech-to-text conversion
 * via the Deepgram Nova-3 REST API.
 *
 * Audio is captured on a dedicated background thread so the main game thread
 * is never blocked. The player holds a key, audio is buffered, and on key
 * release the buffer is sent to Deepgram. The resulting transcript is then
 * delivered back to the main thread via the provided callback.
 *
 * Phase 2 uses the Deepgram REST (pre-recorded) endpoint for simplicity.
 * Streaming WebSocket will replace this in a later phase.
 */
public class VoiceInputManager {
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturevoice");

    // 16kHz mono 16-bit PCM — the format Deepgram Nova-3 expects for raw audio
    private static final AudioFormat CAPTURE_FORMAT = new AudioFormat(
            16000f, // sample rate Hz
            16,     // bit depth
            1,      // mono
            true,   // signed
            false   // little-endian
    );

    // Volatile so changes are visible immediately across threads
    private static volatile boolean isRecording = false;
    private static volatile TargetDataLine micLine = null;

    // Single daemon thread dedicated to mic capture — never touches the game thread
    private static final ExecutorService captureThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CreatureVoice-Capture");
        t.setDaemon(true);
        return t;
    });

    private static String deepgramApiKey = "";
    private static Mob currentTargetMob = null;
    private static Consumer<String> pendingTranscriptCallback = null;

    /** Called at startup to pass the Deepgram key from config into this manager. */
    public static void setApiKey(String key) {
        deepgramApiKey = key != null ? key.trim() : "";
    }

    /** Returns true while the microphone is actively recording. */
    public static boolean isRecording() {
        return isRecording;
    }

    /** Returns the mob currently being spoken to, or null if not recording. */
    public static Mob getTargetMob() {
        return currentTargetMob;
    }

    /**
     * Starts microphone capture aimed at the given mob.
     * The onTranscript callback is called on the main game thread once Deepgram
     * returns a transcript. Safe to call from the main game thread.
     */
    public static void startRecording(Mob targetMob, Consumer<String> onTranscript) {
        if (isRecording) return;

        if (deepgramApiKey.isEmpty()) {
            LOGGER.warn("No Deepgram API key set — voice input is disabled. Add 'deepgramApiKey' to creaturechat.json.");
            return;
        }

        currentTargetMob = targetMob;
        pendingTranscriptCallback = onTranscript;
        isRecording = true;

        captureThread.submit(() -> {
            try {
                DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, CAPTURE_FORMAT);
                if (!AudioSystem.isLineSupported(lineInfo)) {
                    LOGGER.error("Microphone input not supported on this system");
                    isRecording = false;
                    return;
                }

                micLine = (TargetDataLine) AudioSystem.getLine(lineInfo);
                micLine.open(CAPTURE_FORMAT);
                micLine.start();
                LOGGER.info("Microphone recording started");

                // Read from the mic in small chunks until stopRecording() clears the flag
                ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[1024];
                while (isRecording) {
                    int bytesRead = micLine.read(chunk, 0, chunk.length);
                    if (bytesRead > 0) {
                        audioBuffer.write(chunk, 0, bytesRead);
                    }
                }

                micLine.stop();
                micLine.close();
                micLine = null;
                LOGGER.info("Recording stopped — sending {} bytes to Deepgram", audioBuffer.size());

                byte[] rawAudio = audioBuffer.toByteArray();
                if (rawAudio.length == 0) return;

                String transcript = sendToDeepgram(rawAudio);
                if (transcript != null && !transcript.isBlank()) {
                    LOGGER.info("Transcript: {}", transcript);
                    // Deliver back to the main game thread
                    final Consumer<String> callback = pendingTranscriptCallback;
                    Minecraft.getInstance().execute(() -> {
                        if (callback != null) callback.accept(transcript);
                    });
                } else {
                    LOGGER.warn("Deepgram returned an empty transcript");
                }

            } catch (Exception e) {
                LOGGER.error("Voice capture error: {}", e.getMessage());
                isRecording = false;
            }
        });
    }

    /**
     * Signals the capture thread to stop recording and submit audio to Deepgram.
     * Safe to call from the main game thread.
     */
    public static void stopRecording() {
        isRecording = false;
    }

    /**
     * Cancels an in-progress recording without submitting anything to Deepgram.
     * Used when the player moves out of range mid-recording.
     */
    public static void cancelRecording() {
        pendingTranscriptCallback = null;
        isRecording = false;
        currentTargetMob = null;
    }

    /**
     * POSTs raw 16kHz mono PCM audio to the Deepgram Nova-3 REST endpoint
     * and returns the first transcript string, or null on any failure.
     */
    private static String sendToDeepgram(byte[] rawAudio) {
        try {
            // smart_format=true cleans up punctuation and casing automatically
            String urlStr = "https://api.deepgram.com/v1/listen"
                    + "?model=nova-3"
                    + "&encoding=linear16"
                    + "&sample_rate=16000"
                    + "&channels=1"
                    + "&smart_format=true";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Token " + deepgramApiKey);
            conn.setRequestProperty("Content-Type", "audio/raw");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(rawAudio);
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                InputStream err = conn.getErrorStream();
                String body = err != null
                        ? new String(err.readAllBytes(), StandardCharsets.UTF_8)
                        : "(no error body)";
                LOGGER.error("Deepgram HTTP {}: {}", status, body);
                return null;
            }

            String responseBody = new String(
                    conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return parseTranscript(responseBody);

        } catch (Exception e) {
            LOGGER.error("Failed to reach Deepgram: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Pulls the first transcript string out of the Deepgram JSON response.
     * Deepgram returns: {"results":{"channels":[{"alternatives":[{"transcript":"..."}]}]}}
     * Gson is already on the classpath but simple string extraction is sufficient here.
     */
    private static String parseTranscript(String json) {
        String marker = "\"transcript\":\"";
        int start = json.indexOf(marker);
        if (start == -1) return null;
        start += marker.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }
}
