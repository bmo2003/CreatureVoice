// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.chat;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.owlmaddie.commands.ConfigurationHandler;
import com.owlmaddie.json.ChatGPTResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

/**
 * The {@code ChatGPTRequest} class is used to send HTTP requests to our LLM to generate
 * messages.
 */
public class ChatGPTRequest {
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");
    private static final Gson GSON = new Gson();
    public static String lastErrorMessage;
    public static int lastErrorCode = 0;

    // Global limit on simultaneous LLM HTTP calls. Without this, witness reactions,
    // initiative events, and SPEAK_TO all fire at once and the API returns HTTP errors.
    // 3 permits means at most 3 calls run in parallel; extras wait their turn.
    private static final Semaphore LLM_CONCURRENCY = new Semaphore(3);

    static class ChatGPTRequestMessage {
        String role;
        String content;

        public ChatGPTRequestMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    static class ChatGPTRequestPayload {
        String model;
        List<ChatGPTRequestMessage> messages;
        ResponseFormat response_format;
        float temperature;
        int max_tokens;
        boolean stream;

        public ChatGPTRequestPayload(String model, List<ChatGPTRequestMessage> messages, Boolean jsonMode, float temperature, int maxTokens) {
            this.model = model;
            this.messages = messages;
            this.temperature = temperature;
            this.max_tokens = maxTokens;
            this.stream = false;
            if (jsonMode) {
                this.response_format = new ResponseFormat("json_object");
            } else {
                this.response_format = null;
            }
        }
    }

    static class ResponseFormat {
        String type;

        public ResponseFormat(String type) {
            this.type = type;
        }
    }

    // ── Gemini native REST API types ──────────────────────────────────────────
    // Used when apiUrl contains "generativelanguage.googleapis.com".
    // Sends thinkingBudget=0 to disable dynamic thinking so thinking tokens
    // are never billed as output tokens.

    static class GeminiPart {
        String text;
    }

    static class GeminiContent {
        String role;
        List<GeminiPart> parts;
    }

    static class GeminiThinkingConfig {
        int thinkingBudget;
    }

    static class GeminiGenerationConfig {
        int maxOutputTokens;
        float temperature;
        GeminiThinkingConfig thinkingConfig;
    }

    static class GeminiSystemInstruction {
        List<GeminiPart> parts = new ArrayList<>();
    }

    static class GeminiRequestPayload {
        GeminiSystemInstruction system_instruction;
        List<GeminiContent> contents = new ArrayList<>();
        GeminiGenerationConfig generationConfig;
    }

    static class GeminiCandidate {
        GeminiContent content;
    }

    // Token-usage fields from Gemini's usageMetadata block.
    // thoughtsTokenCount is only present when thinkingBudget > 0; absence = 0 thinking tokens.
    static class GeminiUsageMetadata {
        int promptTokenCount;
        int candidatesTokenCount;
        int thoughtsTokenCount;
        int totalTokenCount;
    }

    static class GeminiNativeResponse {
        List<GeminiCandidate> candidates;
        GeminiUsageMetadata   usageMetadata;
    }

    public static String removeQuotes(String str) {
        if (str != null && str.length() > 1 && str.startsWith("\"") && str.endsWith("\"")) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }

    // Class to represent the error response structure
    public static class ErrorResponse {
        Error error;

        static class Error {
            String message;
            String type;
            String code;
        }
    }

    public static String parseAndLogErrorResponse(String errorResponse) {
        try {
            ErrorResponse response = GSON.fromJson(errorResponse, ErrorResponse.class);

            if (response != null && response.error != null) {
                LOGGER.error("Error Message: " + response.error.message);
                LOGGER.error("Error Type: " + response.error.type);
                LOGGER.error("Error Code: " + response.error.code);
                return response.error.message != null ? response.error.message : "Unknown error";
            } else {
                // Some gateways return {"message":"Internal server error"} or similar
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = GSON.fromJson(errorResponse, Map.class);
                    Object msg = (m != null) ? m.get("message") : null;
                    if (msg instanceof String && !((String) msg).isEmpty()) {
                        LOGGER.error("Gateway error message: " + msg);
                        return (String) msg;
                    }
                } catch (Exception ignore) {
                    // fall through to generic handling below
                }
                LOGGER.error("Unknown error response: " + errorResponse);
                return "Unknown error";
            }
        } catch (JsonSyntaxException e) {
            LOGGER.warn("Failed to parse error response as JSON, falling back to plain text");
            LOGGER.error("Error response: " + errorResponse);
        } catch (Exception e) {
            LOGGER.error("Failed to parse error response", e);
        }
        return removeQuotes(errorResponse);
    }

    // Function to replace placeholders in the template
    public static String replacePlaceholders(String template, Map<String, String> replacements) {
        String result = template;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replaceAll(Pattern.quote("{{" + entry.getKey() + "}}"), entry.getValue());
        }
        return result.replace("\"", "") ;
    }

    // Function to roughly estimate # of OpenAI tokens in String
    private static int estimateTokenSize(String text) {
        return (int) Math.round(text.length() / 3.5);
    }

    private static String sanitizeApiKey(String message, String apiKey) {
        if (message == null || apiKey == null || apiKey.isEmpty()) {
            return message;
        }
        return message.replace(apiKey, "**********");
    }

    /**
     * Overload that lets the caller specify a token limit different from the one in config.
     * Used for character sheet generation, which needs ~500 tokens instead of the 60-token
     * chat limit. Normal chat calls use the no-override version below.
     */
    public static CompletableFuture<String> fetchMessageFromChatGPT(ConfigurationHandler.Config config, String systemPrompt, Map<String, String> contextData, List<ChatMessage> messageHistory, Boolean jsonMode, int maxTokensOverride) {
        // Init API & LLM details
        String apiUrl = config.getUrl();
        String apiKey = config.getApiKey();
        String modelName = config.getModel();
        Integer timeout = config.getTimeout() * 1000;
        int maxContextTokens = config.getMaxContextTokens();
        int maxOutputTokens = maxTokensOverride;
        double percentOfContext = config.getPercentOfContext();

        return CompletableFuture.supplyAsync(() -> {
            // Wait for a concurrency permit before making the HTTP call.
            // This prevents the API from seeing a burst of simultaneous requests
            // (e.g. witness reactions + SPEAK_TO + initiative all firing at once).
            LLM_CONCURRENCY.acquireUninterruptibly();
            lastErrorCode = 0;
            HttpURLConnection connection = null;
            try {
                // Replace placeholders
                String systemMessage = replacePlaceholders(systemPrompt, contextData);

                // ── Native Gemini branch ──────────────────────────────────────────────────
                // When the configured URL points at generativelanguage.googleapis.com we switch
                // to the native Gemini REST format.  The OpenAI-compat shim does not allow
                // disabling dynamic thinking, so we would be billed for thinking tokens as
                // output tokens.  The native format lets us set thinkingBudget=0.
                if (apiUrl.contains("generativelanguage.googleapis.com")) {
                    // Build the same trimmed message history the OpenAI path would use
                    List<ChatGPTRequestMessage> rawMessages = new ArrayList<>();
                    int remainingContextTokens =
                            (int) ((maxContextTokens - maxOutputTokens) * percentOfContext);
                    int usedTokens = estimateTokenSize("system: " + systemMessage);

                    for (int i = messageHistory.size() - 1; i >= 0; i--) {
                        ChatMessage chatMessage = messageHistory.get(i);
                        String senderName = chatMessage.sender.toString().toLowerCase(Locale.ENGLISH);
                        String messageText = replacePlaceholders(chatMessage.message, contextData);
                        int messageTokens = estimateTokenSize(senderName + ": " + messageText);
                        if (usedTokens + messageTokens > remainingContextTokens) break;
                        rawMessages.add(new ChatGPTRequestMessage(senderName, messageText));
                        usedTokens += messageTokens;
                    }
                    Collections.reverse(rawMessages);

                    // Build system_instruction
                    GeminiSystemInstruction sysInst = new GeminiSystemInstruction();
                    GeminiPart sysPart = new GeminiPart();
                    sysPart.text = systemMessage;
                    sysInst.parts.add(sysPart);

                    // Build generationConfig with thinkingBudget=0
                    GeminiThinkingConfig thinkingCfg = new GeminiThinkingConfig();
                    thinkingCfg.thinkingBudget = 0;
                    GeminiGenerationConfig genCfg = new GeminiGenerationConfig();
                    genCfg.maxOutputTokens = maxOutputTokens;
                    genCfg.temperature = 0.7f;
                    genCfg.thinkingConfig = thinkingCfg;

                    // Convert roles: "assistant" → "model" for Gemini, ensure alternating turns
                    GeminiRequestPayload geminiPayload = new GeminiRequestPayload();
                    geminiPayload.system_instruction = sysInst;
                    geminiPayload.generationConfig   = genCfg;

                    String lastGeminiRole = null;
                    for (ChatGPTRequestMessage msg : rawMessages) {
                        String geminiRole = "assistant".equals(msg.role) ? "model" : "user";
                        if (geminiRole.equals(lastGeminiRole)) continue; // skip consecutive same role
                        GeminiContent gc = new GeminiContent();
                        gc.role = geminiRole;
                        GeminiPart gp = new GeminiPart();
                        gp.text = msg.content;
                        gc.parts = new ArrayList<>();
                        gc.parts.add(gp);
                        geminiPayload.contents.add(gc);
                        lastGeminiRole = geminiRole;
                    }
                    // Gemini requires contents to start with a "user" turn
                    if (!geminiPayload.contents.isEmpty()
                            && !"user".equals(geminiPayload.contents.get(0).role)) {
                        geminiPayload.contents.remove(0);
                    }
                    if (geminiPayload.contents.isEmpty()) {
                        GeminiContent fallback = new GeminiContent();
                        fallback.role = "user";
                        GeminiPart fp = new GeminiPart();
                        fp.text = "Hello";
                        fallback.parts = new ArrayList<>();
                        fallback.parts.add(fp);
                        geminiPayload.contents.add(fallback);
                    }

                    // Native endpoint: always /v1beta/models/{model}:generateContent
                    String nativeUrl = "https://generativelanguage.googleapis.com/v1beta/models/"
                            + modelName + ":generateContent";

                    URL geminiUrl = new URL(nativeUrl);
                    connection = (HttpURLConnection) geminiUrl.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("x-goog-api-key", apiKey); // no Bearer prefix
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("Accept-Encoding", "gzip");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(timeout);
                    connection.setReadTimeout(timeout);

                    Gson gsonGemini = new Gson();
                    byte[] geminiInput = gsonGemini.toJson(geminiPayload).getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(geminiInput.length);
                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(geminiInput);
                    }

                    int statusCode = connection.getResponseCode();
                    if (statusCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                        lastErrorCode = statusCode;
                        InputStream errStream = connection.getErrorStream();
                        if (errStream == null) {
                            try { errStream = connection.getInputStream(); } catch (Exception ex) {
                                lastErrorMessage = sanitizeApiKey("HTTP " + statusCode + ": " + ex.getMessage(), apiKey);
                                return null;
                            }
                        }
                        if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
                            errStream = new GZIPInputStream(errStream);
                        }
                        try (BufferedReader er = new BufferedReader(
                                new InputStreamReader(errStream, StandardCharsets.UTF_8))) {
                            StringBuilder eb = new StringBuilder();
                            String el;
                            while ((el = er.readLine()) != null) eb.append(el.trim());
                            String cleanErr = parseAndLogErrorResponse(eb.toString());
                            String finalMsg = "HTTP " + statusCode
                                    + (cleanErr != null && !cleanErr.isEmpty() ? ": " + cleanErr : "");
                            LOGGER.error(finalMsg);
                            lastErrorMessage = sanitizeApiKey(finalMsg, apiKey);
                        }
                        return null;
                    } else {
                        lastErrorMessage = null;
                        lastErrorCode = 0;
                    }

                    InputStream geminiIn = connection.getInputStream();
                    if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
                        geminiIn = new GZIPInputStream(geminiIn);
                    }
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(geminiIn, StandardCharsets.UTF_8))) {
                        StringBuilder resp = new StringBuilder();
                        String rl;
                        while ((rl = br.readLine()) != null) resp.append(rl.trim());
                        GeminiNativeResponse geminiResp =
                                GSON.fromJson(resp.toString(), GeminiNativeResponse.class);
                        if (geminiResp != null && geminiResp.candidates != null
                                && !geminiResp.candidates.isEmpty()) {
                            // Log token usage so we can verify thinkingBudget=0 is working.
                            // thoughtsTokenCount should always be 0 when thinkingBudget=0.
                            if (geminiResp.usageMetadata != null) {
                                GeminiUsageMetadata u = geminiResp.usageMetadata;
                                double inputCost  = u.promptTokenCount     * 0.10 / 1_000_000.0;
                                double outputCost = u.candidatesTokenCount * 0.40 / 1_000_000.0;
                                LOGGER.info(
                                    "Gemini usage: input={}tok output={}tok thinking={}tok total={}tok | cost ~${} (input) + ~${} (output)",
                                    u.promptTokenCount, u.candidatesTokenCount,
                                    u.thoughtsTokenCount, u.totalTokenCount,
                                    String.format("%.6f", inputCost),
                                    String.format("%.6f", outputCost));
                            }
                            GeminiCandidate cand = geminiResp.candidates.get(0);
                            if (cand.content != null && cand.content.parts != null
                                    && !cand.content.parts.isEmpty()
                                    && cand.content.parts.get(0).text != null) {
                                return cand.content.parts.get(0).text;
                            }
                        }
                        LOGGER.warn("Gemini native: empty or unparseable response — raw: {}",
                                resp.length() > 200 ? resp.substring(0, 200) + "..." : resp.toString());
                        lastErrorMessage = "Failed to parse Gemini native response";
                        return null;
                    }
                    // End of native Gemini branch — falls through to return null if something missed
                }
                // ── End native Gemini branch ──────────────────────────────────────────────

                URL url = new URL(apiUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                connection.setRequestProperty("Connection", "keep-alive");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Accept-Encoding", "gzip");
                connection.setDoOutput(true);
                connection.setConnectTimeout(timeout);
                connection.setReadTimeout(timeout);

                // Create messages list (for chat history)
                List<ChatGPTRequestMessage> messages = new ArrayList<>();

                // Don't exceed a specific % of total context window (to limit message history in request)
                int remainingContextTokens = (int) ((maxContextTokens - maxOutputTokens) * percentOfContext);
                int usedTokens = estimateTokenSize("system: " + systemMessage);

                // Iterate backwards through the message history
                for (int i = messageHistory.size() - 1; i >= 0; i--) {
                    ChatMessage chatMessage = messageHistory.get(i);
                    String senderName = chatMessage.sender.toString().toLowerCase(Locale.ENGLISH);
                    String messageText = replacePlaceholders(chatMessage.message, contextData);
                    int messageTokens = estimateTokenSize(senderName + ": " + messageText);

                    if (usedTokens + messageTokens > remainingContextTokens) {
                        break;  // If adding this message would exceed the token limit, stop adding more messages
                    }

                    // Add the message to the temporary list
                    messages.add(new ChatGPTRequestMessage(senderName, messageText));
                    usedTokens += messageTokens;
                }

                // Add system message
                messages.add(new ChatGPTRequestMessage("system", systemMessage));

                // Reverse the list to restore chronological order
                // This is needed since we build the list in reverse order for token restricting above
                Collections.reverse(messages);

                // Convert JSON to String
                ChatGPTRequestPayload payload = new ChatGPTRequestPayload(
                        modelName, messages, jsonMode, 0.7f, maxOutputTokens);

                Gson gsonInput = new Gson();
                String jsonInputString = gsonInput.toJson(payload);

                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(input.length);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(input);
                }

                // Check for error message in response
                int statusCode = connection.getResponseCode();
                if (statusCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                    lastErrorCode = statusCode;
                    final String reason = connection.getResponseMessage() != null ? connection.getResponseMessage() : "";

                    // Try to capture helpful IDs for tracing through AWS and OpenAI
                    final String awsRequestId    = connection.getHeaderField("x-amzn-RequestId");
                    final String awsErrorType    = connection.getHeaderField("x-amzn-ErrorType");
                    final String openaiRequestId = connection.getHeaderField("x-request-id");

                    // Log AWS headers only for debugging so they don't bloat user-facing messages
                    if (awsRequestId != null) LOGGER.debug("AWS Request ID: {}", awsRequestId);
                    if (awsErrorType != null) LOGGER.debug("AWS Error Type: {}", awsErrorType);
                    if (openaiRequestId != null) LOGGER.debug("OpenAI Request ID: {}", openaiRequestId);

                    InputStream errStream = connection.getErrorStream();
                    if (errStream == null) {
                        try {
                            errStream = connection.getInputStream();
                        } catch (Exception ex) {
                            LOGGER.error("Failed to obtain error stream", ex);
                            String msg = reason != null ? reason : ("HTTP error " + statusCode);
                            StringBuilder base = new StringBuilder();
                            base.append("HTTP ").append(statusCode);
                            if (msg != null && !msg.isEmpty()) base.append(" ").append(msg);

                            lastErrorMessage = sanitizeApiKey(base + ": " + ex.getMessage(), apiKey);
                            return null;
                        }
                    }
                    if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
                        errStream = new GZIPInputStream(errStream);
                    }
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(errStream, StandardCharsets.UTF_8))) {
                        String line;
                        StringBuilder errorResponse = new StringBuilder();
                        while ((line = errorReader.readLine()) != null) {
                            errorResponse.append(line.trim());
                        }

                        // Try known shapes first
                        String cleanError = parseAndLogErrorResponse(errorResponse.toString());

                        // Build a richer message (status + reason + IDs + short body preview)
                        StringBuilder sb = new StringBuilder();
                        sb.append("HTTP ").append(statusCode);
                        if (!reason.isEmpty()) sb.append(" ").append(reason);

                        if (cleanError != null && !cleanError.isEmpty() && !"Unknown error".equals(cleanError)) {
                            sb.append(": ").append(cleanError);
                        } else if (errorResponse.length() > 0) {
                            String bodyPreview = errorResponse.length() > 300
                                    ? errorResponse.substring(0, 300) + "..."
                                    : errorResponse.toString();
                            sb.append(": ").append(bodyPreview);
                        }

                        String finalMsg = sb.toString();
                        LOGGER.error(finalMsg);
                        lastErrorMessage = sanitizeApiKey(finalMsg, apiKey);
                    } catch (Exception e) {
                        LOGGER.error("Failed to read error response", e);
                        lastErrorMessage = sanitizeApiKey("Failed to read error response: " + e.getMessage(), apiKey);
                    }
                    return null;
                } else {
                    lastErrorMessage = null;
                    lastErrorCode = 0;
                }

                InputStream inStream = connection.getInputStream();
                if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
                    inStream = new GZIPInputStream(inStream);
                }
                try (BufferedReader br = new BufferedReader(new InputStreamReader(inStream, StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }

                    ChatGPTResponse chatGPTResponse = GSON.fromJson(response.toString(), ChatGPTResponse.class);
                    if (chatGPTResponse != null && chatGPTResponse.choices != null && !chatGPTResponse.choices.isEmpty()) {
                        String content = chatGPTResponse.choices.get(0).message.content;
                        if (content != null) {
                            return content;
                        }
                        // content was null — likely a safety filter block or empty stop from the LLM
                        LOGGER.warn("LLM returned null content (possible safety filter or empty stop)");
                    }
                    lastErrorMessage = "Failed to parse response";
                    return null;
                }
            } catch (SocketException | SocketTimeoutException ce) {
                LOGGER.warn("Connection failed", ce);
                lastErrorMessage = "No Internet or Blocked Request: " + ce.getMessage();
                lastErrorCode = -1;
                return null;
            } catch (Exception e) {
                LOGGER.error("Failed to request message", e);
                lastErrorMessage = sanitizeApiKey("Failed to request message: " + e.getMessage(), apiKey);
                lastErrorCode = 0;
                return null;
            } finally {
                // Always release the concurrency permit so the next queued call can proceed
                LLM_CONCURRENCY.release();
            }
        });
    }

    /** Standard chat call — uses the token limit from config (e.g. 60 for single-sentence replies). */
    public static CompletableFuture<String> fetchMessageFromChatGPT(ConfigurationHandler.Config config, String systemPrompt, Map<String, String> contextData, List<ChatMessage> messageHistory, Boolean jsonMode) {
        return fetchMessageFromChatGPT(config, systemPrompt, contextData, messageHistory, jsonMode, config.getMaxOutputTokens());
    }
}

