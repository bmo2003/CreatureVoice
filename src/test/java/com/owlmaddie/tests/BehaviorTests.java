package com.owlmaddie.tests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.chat.ChatGPTRequest;
import com.owlmaddie.commands.ConfigurationHandler;
import com.owlmaddie.message.MessageParser;
import com.owlmaddie.message.ParsedMessage;
import com.owlmaddie.utils.EntityTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@code BehaviorTests} class tests a variety of LLM prompts and expected outputs from specific characters
 * and personality types. For example, an aggressive character will attack, a nervous character will flee, etc...
 */
public class BehaviorTests {
    static String PROMPT_PATH = "src/main/resources/data/creaturechat/prompts/";
    static String RESOURCE_PATH = "src/test/resources/data/creaturechat/";
    static String API_KEY = "";
    static String API_URL = "";
    String NO_API_KEY = "No API_KEY environment variable has been set.";

    ConfigurationHandler.Config config = null;
    String systemChatContents = null;

    List<String> followMessages = Arrays.asList(
            "Please follow me",
            "Come with me please",
            "Quickly, please come this way");
    List<String> attackMessages = Arrays.asList(
            "<attacked you directly with Stone Axe>",
            "<attacked you indirectly with Arrow>",
            "DIEEE!");
    List<String> friendshipUpMessages = Arrays.asList(
            "Hi friend! I am so happy to see you again!",
            "How is my best friend doing?",
            "<gives 1 golden apple>");
    List<String> friendshipDownMessages = Arrays.asList(
            "<attacked you directly with Stone Axe>",
            "You suck so much! I hate you",
            "DIEEE!");

    static Path systemChatPath = Paths.get(PROMPT_PATH, "system-chat");
    static Path bravePath = Paths.get(RESOURCE_PATH, "chatdata", "brave-archer.json");
    static Path nervousPath = Paths.get(RESOURCE_PATH, "chatdata", "nervous-rogue.json");
    static Path entityPigPath = Paths.get(RESOURCE_PATH, "entities", "pig.json");
    static Path playerPath = Paths.get(RESOURCE_PATH, "players", "player.json");
    static Path worldPath = Paths.get(RESOURCE_PATH, "worlds", "world.json");

    Logger LOGGER = LoggerFactory.getLogger("creaturechat");
    Gson gson = new GsonBuilder().create();

    @BeforeEach
    public void setup() {
        // Get API key from env var
        API_KEY = System.getenv("API_KEY");
        API_URL = System.getenv("API_URL");

        // Config
        config = new ConfigurationHandler.Config();
        if (API_KEY != null && !API_KEY.isEmpty()) {
            config.setApiKey(API_KEY);
        }
        if (API_URL != null && !API_URL.isEmpty()) {
            config.setUrl(API_URL);
        }
        // Verify API key is set correctly
        assertNotNull(API_KEY, NO_API_KEY);

        // Load system chat prompt
        systemChatContents = readFileContents(systemChatPath);
    }

    @Test
    public void followBrave() {
        for (String message : followMessages) {
            testPromptForBehavior(bravePath, message, "FOLLOW");
        }
    }

    @Test
    public void followNervous() {
        for (String message : followMessages) {
            testPromptForBehavior(nervousPath, message, "FOLLOW");
        }
    }

    @Test
    public void attackBrave() {
        for (String message : attackMessages) {
            testPromptForBehavior(bravePath, message, "ATTACK");
        }
    }

    @Test
    public void attackNervous() {
        for (String message : attackMessages) {
            testPromptForBehavior(nervousPath, message, "FLEE");
        }
    }

    @Test
    public void friendshipUpNervous() {
        for (String message : friendshipUpMessages) {
            ParsedMessage result = testPromptForBehavior(nervousPath, message, "FRIENDSHIP");
            assertTrue(result.getBehaviors().stream().anyMatch(b -> "FRIENDSHIP".equals(b.getName()) && b.getArgument() > 0));
        }
    }

    @Test
    public void friendshipDownNervous() {
        for (String message : friendshipDownMessages) {
            ParsedMessage result = testPromptForBehavior(nervousPath, message, "FRIENDSHIP");
            assertTrue(result.getBehaviors().stream().anyMatch(b -> "FRIENDSHIP".equals(b.getName()) && b.getArgument() < 0));
        }
    }

    public ParsedMessage testPromptForBehavior(Path chatDataPath, String message, String behavior) {
        LOGGER.info("Testing '" + chatDataPath.getFileName() + "' with '" + message + "' and expecting behavior: " + behavior);

        try {
            // Load entity chat data
            String chatDataPathContents = readFileContents(chatDataPath);
            EntityTestData entityTestData = gson.fromJson(chatDataPathContents, EntityTestData.class);

            // Load context
            Map<String, String> contextData = entityTestData.getPlayerContext(worldPath, playerPath, entityPigPath);
            assertNotNull(contextData);

            // Add test message
            entityTestData.addMessage(message, ChatDataManager.ChatSender.USER);

            // Get prompt
            Path promptPath = Paths.get(PROMPT_PATH, "system-chat");
            String promptText = Files.readString(promptPath);
            assertNotNull(promptText);

            // fetch HTTP response from ChatGPT
            CompletableFuture<String> future = ChatGPTRequest.fetchMessageFromChatGPT(config, promptText, contextData, entityTestData.previousMessages, false);

            try {
                String outputMessage = future.get(60 * 60, TimeUnit.SECONDS);
                assertNotNull(outputMessage);

                // Chat Message: Check for behavior
                ParsedMessage result = MessageParser.parseMessage(outputMessage.replace("\n", " "));
                assertTrue(result.getBehaviors().stream().anyMatch(b -> behavior.equals(b.getName())));
                return result;

            } catch (TimeoutException e) {
                fail("The asynchronous operation timed out.");
            } catch (Exception e) {
                fail("The asynchronous operation failed: " + e.getMessage());
            }

        } catch (IOException e) {
            e.printStackTrace();
            fail("Failed to read the file: " + e.getMessage());
        }
        LOGGER.info("");
        return null;
    }

    public String readFileContents(Path filePath) {
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

}
