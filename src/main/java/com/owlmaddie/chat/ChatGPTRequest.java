package com.owlmaddie.chat;

import com.google.gson.Gson;
import com.owlmaddie.commands.ConfigurationHandler;
import com.owlmaddie.json.ChatGPTResponse;
import com.owlmaddie.network.ServerPackets;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * The {@code ChatGPTRequest} class is used to send HTTP requests to our LLM to generate
 * messages.
 */
public class ChatGPTRequest {
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");
    public static String lastErrorMessage;

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

        public ChatGPTRequestPayload(String model, List<ChatGPTRequestMessage> messages, Boolean jsonMode, float temperature, int maxTokens) {
            this.model = model;
            this.messages = messages;
            this.temperature = temperature;
            this.max_tokens = maxTokens;
            if (jsonMode) {
                this.response_format = new ResponseFormat("json_object");
            } else {
                this.response_format = new ResponseFormat("text");
            }
        }
    }

    static class ResponseFormat {
        String type;

        public ResponseFormat(String type) {
            this.type = type;
        }
    }

    // This method should be called in an appropriate context where ResourceManager is available
    public static String loadPromptFromResource(ResourceManager resourceManager, String filePath) {
        Identifier fileIdentifier = new Identifier("creaturechat", filePath);
        try (InputStream inputStream = resourceManager.getResource(fileIdentifier).get().getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            StringBuilder contentBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                contentBuilder.append(line).append("\n");
            }
            return contentBuilder.toString();
        } catch (Exception e) {
            LOGGER.error("Failed to read prompt file", e);
        }
        return null;
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

    public static CompletableFuture<String> fetchMessageFromChatGPT(String systemPrompt, Map<String, String> context, List<ChatDataManager.ChatMessage> messageHistory, Boolean jsonMode) {
        // Get config (api key, url, settings)
        ConfigurationHandler.Config config = new ConfigurationHandler(ServerPackets.serverInstance).loadConfig();

        // Init API & LLM details
        String apiUrl = config.getUrl();
        String apiKey = config.getApiKey();
        String modelName = config.getModel();
        Integer timeout = config.getTimeout() * 1000;
        int maxContextTokens = config.getMaxContextTokens();
        int maxOutputTokens = config.getMaxOutputTokens();
        double percentOfContext = config.getPercentOfContext();

        return CompletableFuture.supplyAsync(() -> {
            try {
                String systemMessage = "";
                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    systemMessage = loadPromptFromResource(ServerPackets.serverInstance.getResourceManager(), "prompts/" + systemPrompt);
                    systemMessage = replacePlaceholders(systemMessage, context);
                }

                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                connection.setDoOutput(true);
                connection.setConnectTimeout(timeout); // 10 seconds connection timeout
                connection.setReadTimeout(timeout); // 10 seconds read timeout

                // Create messages list (for chat history)
                List<ChatGPTRequestMessage> messages = new ArrayList<>();

                // Don't exceed a specific % of total context window (to limit message history in request)
                int remainingContextTokens = (int) ((maxContextTokens - maxOutputTokens) * percentOfContext);
                int usedTokens = estimateTokenSize("system: " + systemMessage);

                // Iterate backwards through the message history
                for (int i = messageHistory.size() - 1; i >= 0; i--) {
                    ChatDataManager.ChatMessage chatMessage = messageHistory.get(i);
                    String senderName = chatMessage.sender.toString().toLowerCase(Locale.ENGLISH);
                    String messageText = replacePlaceholders(chatMessage.message, context);
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
                ChatGPTRequestPayload payload = new ChatGPTRequestPayload(modelName, messages, jsonMode, 1.0f, maxOutputTokens);
                Gson gsonInput = new Gson();
                String jsonInputString = gsonInput.toJson(payload);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Check for error message in response
                if (connection.getResponseCode() >= HttpURLConnection.HTTP_BAD_REQUEST) {
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                        String errorLine;
                        StringBuilder errorResponse = new StringBuilder();
                        while ((errorLine = errorReader.readLine()) != null) {
                            errorResponse.append(errorLine.trim());
                        }
                        LOGGER.error("Error response from API: " + errorResponse);
                        lastErrorMessage = errorResponse.toString();
                    }
                    return null;
                } else {
                    lastErrorMessage = null;
                }

                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }

                    Gson gsonOutput = new Gson();
                    ChatGPTResponse chatGPTResponse = gsonOutput.fromJson(response.toString(), ChatGPTResponse.class);
                    if (chatGPTResponse != null && chatGPTResponse.choices != null && !chatGPTResponse.choices.isEmpty()) {
                        String content = chatGPTResponse.choices.get(0).message.content;
                        LOGGER.info("Generated message: " + content);
                        return content;
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Failed to fetch message from ChatGPT", e);
            }
            return null; // If there was an error or no response, return null
        });
    }
}

