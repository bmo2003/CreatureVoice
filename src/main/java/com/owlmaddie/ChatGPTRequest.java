package com.owlmaddie;

import com.google.gson.Gson;
import com.owlmaddie.json.ChatGPTResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;


public class ChatGPTRequest {
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");

    public static CompletableFuture<String> fetchGreetingFromChatGPT() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL("https://api.openai.com/v1/chat/completions");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer REDACTED_API_KEY");
                connection.setDoOutput(true);

                String jsonInputString = "{"
                        + "\"model\": \"gpt-3.5-turbo\","
                        + "\"messages\": ["
                        + "{ \"role\": \"system\", \"content\": \"You are a silly Minecraft entity who speaks to the player in short riddles.\" },"
                        + "{ \"role\": \"user\", \"content\": \"Hello!\" }"
                        + "]"
                        + "}";
                LOGGER.info(jsonInputString);

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

                    Gson gson = new Gson();
                    ChatGPTResponse chatGPTResponse = gson.fromJson(response.toString(), ChatGPTResponse.class);
                    if (chatGPTResponse != null && chatGPTResponse.choices != null && !chatGPTResponse.choices.isEmpty()) {
                        String content = chatGPTResponse.choices.get(0).message.content.replace("\n", " ");
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

