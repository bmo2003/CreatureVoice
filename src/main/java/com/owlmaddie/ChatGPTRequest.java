package com.owlmaddie;

import com.google.gson.Gson;
import com.owlmaddie.json.ChatGPTResponse;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;


public class ChatGPTRequest {
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");

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

        public ChatGPTRequestPayload(String model, List<ChatGPTRequestMessage> messages) {
            this.model = model;
            this.messages = messages;
        }
    }

    // This method should be called in an appropriate context where ResourceManager is available
    public static String loadPromptFromResource(ResourceManager resourceManager, String filePath) {
        Identifier fileIdentifier = new Identifier("mobgpt", filePath);
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

    public static CompletableFuture<String> fetchMessageFromChatGPT(String systemPrompt, Map<String, String> context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get user message
                String userMessage = context.get("message");

                // Load and prepare the system prompt template
                String systemMessage = "";
                if (!systemPrompt.isEmpty()) {
                    // Load prompt from resources
                    systemMessage = loadPromptFromResource(ModInit.serverInstance.getResourceManager(), "prompts/" + systemPrompt);
                }

                // Replace placeholders (if any)
                systemMessage = replacePlaceholders(systemMessage, context);
                userMessage = replacePlaceholders(userMessage, context);

                URL url = new URL("https://api.openai.com/v1/chat/completions");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer REDACTED_API_KEY");
                connection.setDoOutput(true);

                // Build JSON payload for ChatGPT
                List<ChatGPTRequestMessage> messages = new ArrayList<>();
                messages.add(new ChatGPTRequestMessage("system", systemMessage));
                messages.add(new ChatGPTRequestMessage("user", userMessage));

                // Convert JSON to String
                ChatGPTRequestPayload payload = new ChatGPTRequestPayload("gpt-3.5-turbo", messages);
                Gson gsonInput = new Gson();
                String jsonInputString = gsonInput.toJson(payload);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }

                    Gson gsonOutput = new Gson();
                    ChatGPTResponse chatGPTResponse = gsonOutput.fromJson(response.toString(), ChatGPTResponse.class);
                    if (chatGPTResponse != null && chatGPTResponse.choices != null && !chatGPTResponse.choices.isEmpty()) {
                        String content = chatGPTResponse.choices.get(0).message.content;
                        LOGGER.info(content);
                        return content;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to fetch greeting from ChatGPT", e);
            }
            return null; // If there was an error or no response, return null
        });
    }
}

